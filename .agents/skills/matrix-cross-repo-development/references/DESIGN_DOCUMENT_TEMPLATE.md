# <WORK_ITEM_TITLE>

## 1. Background

Describe the current problem, affected users, and why this work is needed now.

## 2. Objective

State the user-visible or system-visible result that must be achieved.

## 3. Non-goals

List work intentionally excluded from this item.

## 4. Shared branch

```text
<branch-name>
```

| Repository | Branch | Planned changes |
|---|---|---|
| `matrix` | `<branch-name>` | |
| `matrix-web` | `<branch-name>` | |
| `matrix-secretary` | `<branch-name>` | |

## 5. Repository responsibilities

### matrix

- Business APIs:
- Validation and permissions:
- Persistence or configuration:

### matrix-secretary

- Semantic parsing:
- TaskPlan/TaskAction orchestration:
- Skill execution, scheduling, retry, and audit:

### matrix-web

- User interaction:
- Preview and confirmation:
- Result and error presentation:

## 6. Impact analysis

### Modules and files

| Repository | Module or path | Impact |
|---|---|---|
| | | |

### Data and configuration

Describe schema, migration, index, configuration, and compatibility effects.

## 7. Cross-repository contract

### Request

```json
{}
```

### Response

```json
{}
```

### Error behavior

| Scenario | Code or status | Caller behavior |
|---|---|---|
| | | |

### Context propagation

Define user, tenant, trace, service identity, timeout, and correlation fields.

## 8. State and execution rules

- Initial state:
- Confirmation rule:
- Success state:
- Failure state:
- Cancellation rule:
- Retry rule:
- Idempotency key:
- Concurrency protection:
- Audit records:

## 9. Ordered implementation plan

1. Documentation gate.
2. `matrix` provider changes.
3. `matrix-secretary` orchestration and Skill changes.
4. `matrix-web` consumer changes.
5. Repository verification.
6. Vertical-flow verification.
7. Outcome documentation.

## 10. Acceptance criteria

- [ ] Shared branch exists in all three repositories.
- [ ] Design document was committed before implementation code.
- [ ] Provider and consumer contracts match.
- [ ] Normal flow passes.
- [ ] Failure and duplicate-operation behavior passes.
- [ ] Required execution and audit records are written.
- [ ] User-facing result is displayed correctly.

## 11. Verification plan

### matrix

```bash
mvn test
mvn clean package
```

### matrix-web

```bash
npm install
npm run build
```

List additional available checks.

### matrix-secretary

```bash
cd java-service
mvn test

cd ../python-service
python -m compileall app
pytest
```

### Vertical flow

Describe test data, steps, expected result, and evidence.

## 12. Risks and rollback

| Risk | Mitigation | Rollback |
|---|---|---|
| | | |

## 13. Implementation outcome

Complete this section after implementation.

### Files changed

| Repository | Files | Result |
|---|---|---|
| | | |

### Verification results

| Repository | Command or check | Result |
|---|---|---|
| | | |

### Skipped checks

List skipped checks and reasons.

### Known limitations and follow-ups

- 

### Pull requests

- `matrix`:
- `matrix-web`:
- `matrix-secretary`:
