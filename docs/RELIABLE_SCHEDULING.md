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

`TaskPlanRepository.findDueScheduledActions()` selects only due scheduled actions:

```sql
status = 'SCHEDULED'
AND next_fire_time <= now
AND (locked_by IS NULL OR locked_at IS NULL OR locked_at < lockExpiredBefore)
ORDER BY next_fire_time ASC
LIMIT :limit
```

The current batch size is 100.

### 3. Conditional action lock

`TaskPlanRepository.tryLockScheduledAction()` uses a conditional update so that only one worker can acquire the same due action.

`TaskPlanRepository.releaseActionLock()` releases a lock only for the same owner.

### 4. Dispatch flow

`AiTaskService.dispatchDueOnceTasks()` now:

```text
calculate now / lockExpiredBefore / lockOwner
  -> query due actions
  -> try to lock each action
  -> load the owning plan
  -> execute only the locked action
  -> save plan/action state
  -> release the lock if nothing changed
```

The current lock timeout is 10 minutes.

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

`AiTaskService` now routes edit, confirm, cancel, retry, dispatch, and plan-status resolution through the state machine. `TaskExecutionService` validates confirmation and execution through the same service.

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
- `RETRY_WAITING` can be executed by a future retry scheduler.
- Plan status resolution now prioritizes `RUNNING`, then `RETRY_WAITING`, then scheduled/failed/timeout terminal states.

`TaskPlanRepository` also updates execution metadata during action persistence:

- `attemptCount` increments when an action reaches `EXECUTED`, `FAILED`, or `TIMEOUT` from another status.
- `attemptCount` increments for recurring scheduled actions when `nextFireTime` advances.
- `lastError` is populated for `FAILED` and `TIMEOUT` actions.

This is groundwork; retry backoff scheduling is not implemented yet.

## Still pending

1. Persist a visible `RUNNING` transition before executing long-running skills.
2. Connect `attemptCount` to `maxRetryCount` and retry backoff.
3. Add automated tests for lock acquisition, lock expiry, recurring task advancement, same-plan concurrent actions, and status rules.
4. Add a composite index for `status + next_fire_time + locked_at`.
5. Split action persistence further into clearer `savePlan`, `saveAction`, and `updateActionStatus` methods.

## Acceptance checks

1. A due one-time reminder executes once.
2. Local scheduler and XXL-JOB do not run the same action twice.
3. Two Java instances cannot acquire the same due action.
4. A recurring action advances `nextFireTime` after a successful run.
5. An expired lock can be acquired again after 10 minutes.
6. Two due actions under the same plan do not clear each other's locks.
7. Non-confirmable actions cannot be confirmed, and non-failed/non-timeout actions cannot be retried.
8. `FAILED` and `TIMEOUT` actions populate `lastError`.
9. Successful, failed, timed-out, and advanced recurring executions increment `attemptCount`.
