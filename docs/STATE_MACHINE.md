# Task State Machine

This document defines the task status model for `matrix-secretary`. AI-generated changes must preserve these transitions unless this document is intentionally updated first.

## 1. Core Statuses

```text
WAITING_CONFIRM
CONFIRMED
SCHEDULED
EXECUTED
FAILED
CANCELLED
```

## 2. Status Meaning

### WAITING_CONFIRM

The task plan has been generated from natural language and persisted, but the user has not confirmed execution yet.

Expected after:

- `/api/ai-task/preview`

Allowed transitions:

```text
WAITING_CONFIRM -> CONFIRMED
WAITING_CONFIRM -> SCHEDULED
WAITING_CONFIRM -> EXECUTED
WAITING_CONFIRM -> FAILED
WAITING_CONFIRM -> CANCELLED
```

Notes:

- High-risk actions must not skip this state.
- Preview must not directly execute high-risk Skills.

### CONFIRMED

The user or operator has confirmed the task, but at least one action remains neither scheduled, executed, failed, nor cancelled in a terminal way.

Allowed transitions:

```text
CONFIRMED -> SCHEDULED
CONFIRMED -> EXECUTED
CONFIRMED -> FAILED
CONFIRMED -> CANCELLED
```

### SCHEDULED

The action is waiting for `runAt` or `nextRunAt`.

Expected for:

- one-time scheduled tasks
- recurring scheduled tasks

Allowed transitions:

```text
SCHEDULED -> EXECUTED
SCHEDULED -> FAILED
SCHEDULED -> CANCELLED
SCHEDULED -> SCHEDULED   # recurring action after successful trigger and nextRunAt advance
```

Rules:

- Scheduled tasks must be persisted.
- `scheduleType`, `cron`, `runAt`, `nextRunAt`, `lastRunAt`, and `triggerCount` must be retained when available.
- Recurring actions should advance `nextRunAt` after successful execution.
- Future production scheduling should avoid full-table scans and use `nextRunAt <= now` queries with locking.

### EXECUTED

The action has completed successfully.

Allowed transitions:

```text
EXECUTED -> no normal transition
```

Rules:

- Executed actions cannot be globally cancelled.
- Re-running an executed action requires a new explicit task or a future idempotent replay mechanism.

### FAILED

The action failed during validation, execution, scheduling, or external integration.

Allowed transitions:

```text
FAILED -> EXECUTED       # retry succeeded
FAILED -> SCHEDULED      # retry rescheduled recurring/scheduled action
FAILED -> FAILED         # retry failed again
FAILED -> CANCELLED      # user cancels unrecoverable work
```

Rules:

- Only failed actions should be retried through the retry endpoint.
- Retry attempts should eventually include attempt counts, retry limits, idempotency keys, and backoff.

### CANCELLED

The user or operator cancelled the task or action before successful execution.

Allowed transitions:

```text
CANCELLED -> no normal transition
```

Rules:

- A plan containing executed actions must not be cancelled globally.
- Cancellation must write an execution or state-change log.

## 3. Plan Status Resolution

A plan status is derived from its action statuses.

Recommended precedence:

```text
all actions CANCELLED -> CANCELLED
any action SCHEDULED -> SCHEDULED
any action FAILED    -> FAILED
all actions EXECUTED -> EXECUTED
otherwise            -> CONFIRMED
```

This matches the current service-level behavior and should remain stable unless intentionally changed.

## 4. High-Risk Action Rule

High-risk actions include:

- sending email
- sending message
- replying to message
- HTTP write operation
- external business system write operation
- delete, cancel, overwrite, submit, approve, pay, or permission-changing operation

Rules:

```text
High-risk action -> requiresConfirmation=true -> WAITING_CONFIRM before execution
```

The model is not trusted as the final authority on risk. Java must enforce risk rules from Skill metadata.

## 5. Audit Logging Rule

Every meaningful transition must be auditable.

Execution logs should capture:

- planId
- actionId
- skillName
- status before or request payload
- status after or response payload
- error message
- operatorUserId
- createdAt

Application logs alone are not enough.

## 6. Scheduling Rule

Scheduled actions must not depend only on memory.

Persist at minimum:

- scheduleType
- cron
- runAt
- nextRunAt
- lastRunAt
- triggerCount
- action status

Future reliability fields:

- executionAttempt
- maxRetryCount
- idempotencyKey
- lastExecutionAt
- lockedBy
- lockedAt

## 7. Forbidden Transitions

These transitions are forbidden unless a future design explicitly documents a safe workflow:

```text
WAITING_CONFIRM -> direct real email send without confirmation
WAITING_CONFIRM -> direct external HTTP write without confirmation
EXECUTED -> CANCELLED
EXECUTED -> RETRY through normal retry endpoint
CANCELLED -> EXECUTED
FAILED -> infinite retry without limit
SCHEDULED -> execution without audit log
```

## 8. Change Protocol

Before changing state machine behavior:

1. Update this document.
2. Explain the reason for the new transition or rule.
3. Add or update tests.
4. Verify existing preview, confirm, cancel, retry, dispatch, and log APIs remain coherent.
