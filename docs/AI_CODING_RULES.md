# AI Coding Rules

This document defines how AI-assisted coding should be used in `matrix-secretary`. Its purpose is to prevent vibe coding from gradually breaking architecture, security, maintainability, and task reliability.

## 1. Coding Mode

AI-generated changes must follow this order:

```text
Understand -> Plan -> Change -> Verify -> Summarize
```

Do not jump directly from a vague requirement to code changes.

For non-trivial requirements, first produce:

- requirement interpretation
- impacted modules
- impacted data model or API
- risk assessment
- minimal implementation plan
- verification plan

## 2. Project Non-Negotiables

`matrix-secretary` is a task execution system. The following rules are mandatory:

1. Natural language parsing does not equal execution.
2. Every executable action must belong to a `TaskPlan` and `TaskAction`.
3. High-risk actions must require confirmation.
4. Scheduled tasks must be persisted and recoverable.
5. Execution results must be written to business audit logs.
6. Secrets must never be committed.
7. External automation platforms must not own the core state machine.

## 3. Layering Rules

### Controller

Controllers should:

- validate API-level input
- call application services
- return response DTOs or domain responses

Controllers should not:

- send emails directly
- call Python directly except through service/client abstractions
- manipulate task status directly
- access MyBatis mappers directly
- contain large business branches

### Service

Services own business orchestration, including:

- preview
- confirm
- cancel
- retry
- dispatch
- status transition
- execution coordination

### Repository

Repositories own persistence and mapping between entities and domain models.

Repositories should not contain business policy decisions such as whether an email requires confirmation.

### Skill Layer

Skill metadata belongs in `skill.yml`.

Skill execution should be delegated to executor classes. Avoid adding more unrelated responsibilities to a single generic executor.

### Python Service

Python owns semantic parsing only. It must not directly execute Skills.

## 4. State Machine Rules

Before changing task statuses or transition logic, update or verify `docs/STATE_MACHINE.md`.

Important constraints:

- `WAITING_CONFIRM` is the only normal entry state after preview.
- High-risk actions cannot skip confirmation.
- Executed actions cannot be globally cancelled.
- Only failed actions should be retried.
- Recurring actions should return to `SCHEDULED` after successful execution and advance `nextRunAt`.

## 5. Security Rules

Never commit:

- API keys
- mail passwords
- app passwords
- database passwords for real environments
- OAuth tokens
- bearer tokens
- private URLs containing credentials
- screenshots containing secrets

Configuration files should use environment variables. Defaults must be safe for local development.

Unsafe example:

```yaml
password: real-password
send-enabled: true
```

Safer example:

```yaml
password: ${MAIL_PASSWORD:}
send-enabled: ${EMAIL_SEND_ENABLED:false}
```

## 6. Email Safety Rules

Email is a high-risk Skill.

Rules:

- Sending email must require confirmation.
- Development mode should default to disabled or sandbox mode.
- Sandbox mode must send only to a configured test recipient or the sender itself.
- Real recipient sending must be explicitly enabled.
- Draft creation and real sending should be separated where possible.

Preferred future split:

```text
draft_email: create or update an email draft, lower risk, target optional
send_email: send to a real recipient, high risk, target required, confirmation required
```

## 7. HTTP Skill Safety Rules

HTTP Skills are high-risk until governed.

Before adding production HTTP Skill execution, implement or verify:

- URL allowlist
- method allowlist
- internal network protection
- request timeout
- response size limit
- sensitive field redaction
- retry policy
- confirmation for write operations

The model must not generate arbitrary URLs and execute them directly.

## 8. Testing Expectations

Core flows must have tests before the project grows further.

High-priority Java test targets:

- preview creates `WAITING_CONFIRM` plan
- send email action requires confirmation
- missing required Skill arguments fail safely
- confirm writes execution logs
- cancel refuses already executed plans
- retry only allows failed actions
- userId isolation prevents cross-user reads
- recurring schedule advances `nextRunAt`

High-priority Python test targets:

- send email parsing
- reminder parsing
- recurring schedule parsing
- multi-task splitting
- dynamic target normalization
- fallback behavior when LLM is unavailable

## 9. Change Size Rules

Prefer small changes.

Good examples:

- only remove hardcoded mail credentials
- only add email draft query API
- only split `EmailSkillExecutor`
- only add `draft_email` Skill
- only add scheduler locking fields

Bad examples:

- refactor the whole project
- add full user system, calendar, email, messaging, and front-end in one change
- rewrite parser, state machine, and Skill execution together

## 10. Review Checklist

Before merging AI-generated code, check:

- Does this bypass `TaskPlan` or `TaskAction`?
- Does this bypass confirmation for high-risk work?
- Does this introduce or expose secrets?
- Does this weaken userId isolation?
- Does this skip audit logging?
- Does this add unbounded retries or unbounded scheduling scans?
- Does this make `GenericSkillExecutor` more bloated?
- Does this have at least minimal verification?

## 11. Rollback Rule

Every feature change should be small enough to revert with one commit.

If a change cannot be safely reverted, it is too large and should be split.
