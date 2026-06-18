# Reliable scheduling changes

This document records the first reliability pass on scheduled task dispatch in `feat/reliable-scheduling-clean`.

## Background

The previous local scheduler or XXL-JOB handler triggered `dispatchDueOnceTasks()` every 10 seconds. The old implementation scanned all task plans and then checked each action to decide whether it was due.

That was acceptable for a demo, but it had several issues:

- It could scan too many plans as data grows.
- Multiple Java instances could pick the same due action.
- A crashed worker could leave no clear recovery path.
- Scheduling query fields lived only inside `scheduleJson`, which is not suitable for indexed due-task queries.
- The old plan save flow deleted all actions under a plan and inserted them again, which could clear locks held by another scheduler worker.
- State rules were scattered across service methods, making future statuses harder to add safely.

## Changes

### 1. Scheduling columns on task actions

`TaskActionEntity` now has these scheduling helper fields:

```text
nextFireTime
lockedBy
lockedAt
attemptCount
maxRetryCount
idempotencyKey
lastError
```

`status` and `nextFireTime` have index annotations. A later pass should replace field-level indexes with a composite scheduling index.

### 2. Due-action query

`TaskPlanRepository.findDueScheduledActions()` selects due runnable actions:

```sql
status IN ('SCHEDULED', 'RETRY_WAITING')
AND next_fire_time <= now
AND (locked_by IS NULL OR locked_at IS NULL OR locked_at < lockExpiredBefore)
ORDER BY next_fire_time ASC
LIMIT :limit
```

The current batch size is 100.

### 3. Conditional action lock

`TaskPlanRepository.tryLockScheduledAction()` uses a conditional update so that only one worker can acquire the same due action. It now supports both `SCHEDULED` and `RETRY_WAITING` actions.

`TaskPlanRepository.releaseActionLock()` releases a lock only for the same owner.

### 4. Dispatch flow

`AiTaskService.dispatchDueOnceTasks()` now:

```text
calculate now / lockExpiredBefore / runningExpiredBefore
  -> recover timed-out RUNNING actions
  -> query due SCHEDULED / RETRY_WAITING actions
  -> try to lock each action
  -> load the owning plan
  -> execute only the locked action
  -> save plan/action state
  -> release the lock if nothing changed
```

The current scheduler lock timeout is 10 minutes. The current running execution timeout is 30 minutes.

### 5. Incremental action persistence

`TaskPlanRepository.save()` no longer deletes all actions and inserts them again. It now performs action-level upsert by `actionId`:

```text
save the plan row
  -> load existing actions under the plan
  -> update existing actions by actionId
  -> insert new actions
  -> delete actions that no longer exist in the plan
```

For unchanged actions, existing lock fields are preserved. If action status or `nextFireTime` changes, the lock is cleared.

### 6. State machine extraction

`TaskStateMachineService` centralizes plan/action state rules:

```text
canEditPlan / requireEditablePlan
canEditAction / requireEditableAction
canConfirmPlan / canConfirmAction / requireConfirmableAction
canCancelPlan / requireCancellablePlan
canRetryAction / requireRetryableAction
canDispatchScheduledAction
canExecuteAction / requireExecutableAction
resolvePlanStatus
```

`AiTaskService` now routes edit, confirm, cancel, retry, dispatch, timeout recovery, and plan-status resolution through the state machine. `TaskExecutionService` validates confirmation and execution through the same service.

### 7. Retry and timeout status groundwork

`TaskStatus` now includes:

```text
RUNNING
RETRY_WAITING
TIMEOUT
```

The state machine understands these statuses:

- `RUNNING` blocks cancellation and execution.
- `FAILED` and `TIMEOUT` actions can be retried.
- `RETRY_WAITING` can be dispatched when its `nextFireTime` is due.
- Plan status resolution now prioritizes `RUNNING`, then `RETRY_WAITING`, then scheduled/failed/timeout terminal states.

`TaskPlanRepository` also updates execution metadata during action persistence:

- `attemptCount` increments when an action reaches `EXECUTED`, `FAILED`, or `TIMEOUT` from another status.
- `attemptCount` increments for recurring scheduled actions when `nextFireTime` advances.
- `lastError` is populated for `FAILED`, `TIMEOUT`, and `RETRY_WAITING` actions.

### 8. Action-level status update methods

`TaskPlanRepository` now exposes action-level status update methods:

```text
markActionRunning(action, note)
markActionResult(action)
markActionTimeoutIfStillRunning(action, timeoutAt, runningExpiredBefore)
updateActionStatus(actionId, status, executionNote, schedule, releaseLock)
```

`TaskExecutionService.executeNow()` now persists a visible `RUNNING` state before invoking the skill executor, then persists the final result state after execution. Unexpected runtime exceptions are converted to `FAILED`, persisted, and logged.

Entering `RUNNING` writes `lockedBy=runtime-executor` and a fresh `lockedAt`, so timeout recovery uses the actual execution start time.

### 9. Retry backoff scheduling

When an execution result is `FAILED` or `TIMEOUT`, `TaskPlanRepository.markActionResult()` now checks `attemptCount` against `maxRetryCount`.

If more retries are available, the persisted action becomes `RETRY_WAITING` instead of final `FAILED` or `TIMEOUT`:

```text
FAILED/TIMEOUT
  -> attemptCount + 1
  -> if attemptCount < maxRetryCount
  -> RETRY_WAITING
  -> nextFireTime = now + exponential backoff
```

The current backoff is exponential by attempt number and capped at 30 minutes:

```text
1st retry: 1 minute
2nd retry: 2 minutes
3rd retry: 4 minutes
...
max: 30 minutes
```

`RETRY_WAITING` actions are picked up by the same due-action query and lock path as normal scheduled actions.

### 10. Running timeout recovery

`AiTaskService.dispatchDueOnceTasks()` now runs timeout recovery before normal due-action dispatch.

`TaskPlanRepository.findTimedOutRunningActions()` selects actions stuck in `RUNNING` whose `lockedAt` is missing or older than the configured running timeout. Each recovered action is passed through `markActionTimeoutIfStillRunning()`, which turns it into `TIMEOUT` and then reuses the same retry-backoff logic as normal failures.

Timeout recovery uses a guarded conditional update: the database row must still be `RUNNING`, and `lockedAt` must still be older than the timeout threshold. If the execution thread completes first and writes `EXECUTED` or `FAILED`, recovery is skipped and the plan is not overwritten.

This means a worker crash during execution no longer leaves an action permanently stuck in `RUNNING`, while a race with a just-completed execution is protected.

## Still pending

1. Add automated tests for lock acquisition, lock expiry, recurring task advancement, same-plan concurrent actions, action-level status updates, state rules, retry backoff, and running timeout recovery.
2. Add a composite index for `status + next_fire_time + locked_at`.
3. Split plan persistence further into clearer `savePlan`, `saveAction`, and query/update ports.
4. Make scheduler lock timeout, running timeout, batch size, retry backoff, and max retry count externally configurable.
5. Add UI/API fields that expose `attemptCount`, `maxRetryCount`, `lastError`, and `nextFireTime` to the frontend.

## Acceptance checks

1. A due one-time reminder executes once.
2. Local scheduler and XXL-JOB do not run the same action twice.
3. Two Java instances cannot acquire the same due action.
4. A recurring action advances `nextFireTime` after a successful run.
5. An expired lock can be acquired again after 10 minutes.
6. Two due actions under the same plan do not clear each other's locks.
7. Non-confirmable actions cannot be confirmed, and non-failed/non-timeout actions cannot be retried.
8. `FAILED`, `TIMEOUT`, and `RETRY_WAITING` actions populate `lastError`.
9. Successful, failed, timed-out, and advanced recurring executions increment `attemptCount`.
10. Immediate execution, manual retry, and scheduled dispatch all persist `RUNNING` before the final action result.
11. Failed actions with retries remaining move to `RETRY_WAITING` with a future `nextFireTime`.
12. Due `RETRY_WAITING` actions are scanned, locked, and executed by the same scheduler path.
13. Stale `RUNNING` actions are converted to `TIMEOUT` and then either retried or finalized based on retry limits.
14. Timeout recovery does not overwrite a concurrently completed action.
