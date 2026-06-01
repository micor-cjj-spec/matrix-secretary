## Change Summary

-

## Motivation

-

## Impact Scope

Check all that apply:

- [ ] Java controller/API
- [ ] Java service/orchestration
- [ ] Repository/persistence
- [ ] Entity/table structure
- [ ] Python semantic parser
- [ ] Skill metadata
- [ ] Skill execution
- [ ] Scheduling
- [ ] Authentication/authorization
- [ ] Secrets/configuration
- [ ] Documentation only

## Core Flow Impact

Does this change touch the core task flow?

```text
Natural language -> semantic parse -> preview -> confirm/edit/cancel -> schedule/execute -> Skill execution -> audit log
```

- [ ] No
- [ ] Yes, explanation:

## State Machine Impact

- [ ] Does not change task status transitions
- [ ] Changes task status transitions and updates `docs/STATE_MACHINE.md`

## Security Checklist

- [ ] No secrets, tokens, passwords, or app passwords are committed
- [ ] High-risk actions still require confirmation
- [ ] userId isolation is preserved or improved
- [ ] External HTTP/write operations remain controlled
- [ ] Logs do not expose sensitive payloads unnecessarily

## Verification

Commands run:

- [ ] `cd java-service && mvn test`
- [ ] `cd java-service && mvn -q -DskipTests compile`
- [ ] `cd python-service && python -m compileall app`
- [ ] `cd python-service && pytest`
- [ ] Manual API smoke test
- [ ] Not run, reason:

## Manual Test Notes

-

## Risk and Rollback

Risk level:

- [ ] Low
- [ ] Medium
- [ ] High

Rollback plan:

-

## AI-Generated Code Disclosure

- [ ] This PR contains AI-assisted changes
- [ ] I reviewed the generated diff manually
- [ ] I verified the result with tests or explained why tests were not run
