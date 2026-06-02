# matrix-secretary Agent Instructions

This repository is an AI secretary task execution system, not a generic chatbot. AI-generated code must preserve the task planning, confirmation, scheduling, execution, and audit boundaries described here.

## Project Positioning

The core value of this project is to convert natural language into task plans that are:

- previewable
- confirmable
- schedulable
- executable
- traceable
- recoverable

Main flow:

```text
User natural language
  -> semantic parse
  -> task plan preview
  -> user confirm / edit / cancel
  -> schedule or execute
  -> Skill execution
  -> execution log / audit / recovery
```

## Service Boundaries

### Java service

Java is the core task orchestration service. It owns:

- API entry points
- `TaskPlan` and `TaskAction`
- task status transitions
- user confirmation
- scheduling orchestration
- Skill execution
- persistence
- audit logs
- userId isolation
- security checks

Java must not degrade into a thin model-forwarding layer.

### Python service

Python is the semantic parsing service. It owns:

- natural language task splitting
- intent recognition
- target/content/time extraction
- cron generation
- LLM fallback behavior

Python must not directly write task tables, send emails, call external write APIs, or bypass Java orchestration.

## Hard Rules

- Do not bypass `TaskPlan` or `TaskAction` when executing work.
- Do not execute high-risk actions before user confirmation.
- Do not put API keys, mail passwords, tokens, or other secrets in code, YAML defaults, docs, tests, or screenshots.
- Do not let Python directly execute business actions.
- Do not let Dify, n8n, MCP, or other external platforms own the core task state machine.
- Every execution or status change must be auditable through business logs, not only application logs.
- New Skills must be registered through the Skill system and executed through the Skill execution layer.
- HTTP Skills must be treated as high-risk unless explicitly proven safe.
- Do not perform broad rewrites without first producing an impact analysis and migration plan.

## Required Workflow for AI Code Changes

Before changing code:

1. Identify the impacted modules, classes, tables, APIs, and tests.
2. Explain whether the change touches the task state machine, Skill execution, scheduling, security, or persistence.
3. Prefer the smallest compatible change.
4. Preserve existing public API behavior unless the request explicitly requires a breaking change.

After changing code:

1. Summarize the files changed.
2. State the verification commands that were run.
3. State any tests that were skipped and why.
4. Mention risks and rollback notes.

## Required Checks

Use the strongest available checks for the changed area.

Java service:

```bash
cd java-service
mvn test
```

If tests are not available yet, at least run:

```bash
cd java-service
mvn -q -DskipTests compile
```

Python service:

```bash
cd python-service
python -m compileall app
```

When Python tests exist, also run:

```bash
cd python-service
pytest
```

## Dangerous Areas

Changes in these areas require extra care and a clear explanation:

- `java-service/src/main/resources/application.yml`
- `java-service/src/main/java/com/kailei/demo/model/TaskStatus.java`
- `java-service/src/main/java/com/kailei/demo/service/AiTaskService.java`
- `java-service/src/main/java/com/kailei/demo/service/TaskExecutionService.java`
- `java-service/src/main/java/com/kailei/demo/skill/GenericSkillExecutor.java`
- `java-service/src/main/java/com/kailei/demo/repository/TaskPlanRepository.java`
- `java-service/src/main/java/com/kailei/demo/service/EmailSandboxService.java`
- `java-service/src/main/resources/db/`
- `python-service/app/llm_parser.py`
- `python-service/app/parser.py`
- `python-service/app/postprocess.py`

## Preferred Design Direction

- Keep Controllers thin.
- Keep orchestration in Services.
- Keep persistence in Repositories.
- Keep Skill metadata in `skill.yml` files.
- Prefer dedicated Skill executors over growing a large switch-based generic executor.
- Prefer explicit status transitions over implicit side effects.
- Prefer environment variables over hardcoded configuration.
- Prefer tests around core flows before large feature expansion.

## Current Priority Guardrails

The project should prioritize:

1. removing hardcoded secrets and unsafe defaults
2. strengthening userId isolation
3. adding tests for preview, confirm, execute, logs, cancel, retry, and scheduling
4. splitting high-risk Skills such as email into draft and send modes
5. adding reliable scheduling protections such as nextRunAt queries, locking, retry limits, and idempotency keys
