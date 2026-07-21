---
name: matrix-cross-repo-development
description: Coordinate Matrix feature, fix, refactor, and integration work across matrix, matrix-web, and matrix-secretary. Enforce identical branch names, a committed docs-first design gate, repository ownership boundaries, implementation verification, outcome recording, and linked pull requests.
---

# Matrix Cross-Repository Development

Use this Skill whenever a work item affects, may affect, or must be tracked across:

- `micor-cjj-spec/matrix`
- `micor-cjj-spec/matrix-web`
- `micor-cjj-spec/matrix-secretary`

Read `docs/CROSS_REPO_DEVELOPMENT_SKILL.md` before performing write actions. That document is the project-level source of truth for this workflow.

## Non-negotiable invariants

1. All three repositories must use the exact same work-item branch name.
2. Create or verify the branch in all three repositories even when only one repository needs code changes.
3. A design document under `docs/` must be committed before implementation code is created or modified.
4. The first documentation commit must not contain implementation code.
5. If implementation needs to diverge from the document, update and commit the document first.
6. Preserve repository boundaries:
   - `matrix` owns business data, business validation, authentication, gateway, and financial APIs.
   - `matrix-web` owns browser interaction, task preview, confirmation, and result presentation.
   - `matrix-secretary` owns semantic parsing, TaskPlan/TaskAction orchestration, scheduling, Skill routing, execution logs, and recovery.
7. Python semantic parsing must not directly perform Matrix business writes.
8. Matrix write actions executed by the Agent must pass through the Java orchestration and Skill execution path.
9. Prefer one complete vertical flow over disconnected scaffolding in several modules.
10. Do not merge pull requests unless the user explicitly requests merging.

## Required inputs

Resolve the following from the request and repository state:

- work-item title
- work-item type: `feature`, `fix`, `refactor`, or `docs`
- branch slug
- repositories with implementation changes
- owning repository for the main design document
- expected user-visible or system-visible outcome

If the user does not supply a branch name, generate:

```text
<type>/<lowercase-hyphenated-slug>
```

Use the same generated value in all three repositories.

## Phase 1: Inspect the baseline

Before writing:

1. Read the relevant `AGENTS.md`, `README.md`, and related files in `docs/`.
2. Inspect current interfaces, modules, routes, data models, tests, and recent related pull requests.
3. Identify whether the work touches:
   - public API compatibility
   - database schema
   - task state transitions
   - Skill execution
   - scheduling, locks, retries, or idempotency
   - authentication, user isolation, or tenant isolation
   - deployment configuration
4. Record assumptions. Do not treat remembered project state as current repository state.

## Phase 2: Establish the shared branch

Create or verify the identical branch in all repositories:

```text
micor-cjj-spec/matrix
micor-cjj-spec/matrix-web
micor-cjj-spec/matrix-secretary
```

Default base is each repository's `main` unless the user explicitly supplies another base.

Do not continue to implementation when any repository has a conflicting or unavailable branch.

Report the branch gate in this format:

```text
Branch: <branch-name>
matrix: created | existing | blocked
matrix-web: created | existing | blocked
matrix-secretary: created | existing | blocked
```

## Phase 3: Produce the design document

Select the main documentation repository:

- Agent, scheduling, orchestration, or cross-repository integration: `matrix-secretary/docs/`
- backend-only business design: `matrix/docs/`
- frontend-only interaction design: `matrix-web/docs/`

For cross-repository work, default to `matrix-secretary/docs/`.

The document must include:

1. background and objective
2. non-goals and scope boundary
3. repository responsibility matrix
4. impacted modules, pages, classes, tables, and interfaces
5. request and response contracts
6. identity, tenant, trace, and service-call context
7. state transitions and confirmation requirements
8. idempotency, retry, concurrency, and audit behavior
9. database and configuration changes
10. ordered implementation plan
11. acceptance criteria
12. verification plan
13. risks and rollback

Commit the document separately with a message such as:

```text
docs: describe <work-item>
```

After the commit, verify that the document exists on the shared branch. Only then open the implementation gate.

## Phase 4: Validate the design gate

Do not generate implementation code until all applicable questions are answered:

- Is each repository's responsibility explicit?
- Are cross-repository request and response fields defined?
- Are caller identity and tenant context defined?
- Are write-operation confirmation and authorization rules defined?
- Are idempotency and retries defined where required?
- Are success, failure, and partial-failure results defined?
- Are acceptance checks executable?
- Is rollback possible and documented?

If any material answer is missing, update the document first.

## Phase 5: Implement in dependency order

Default order:

1. `matrix`
   - business endpoint
   - validation and permissions
   - data model and persistence
   - service-to-service contract
2. `matrix-secretary`
   - Skill metadata and executor
   - TaskPlan/TaskAction orchestration
   - confirmation, scheduling, idempotency, retry, and audit
3. `matrix-web`
   - API client
   - preview and confirmation interaction
   - loading, success, failure, and final-state presentation

A repository may have no implementation changes, but its same-named branch must remain available for traceability.

Keep changes small and compatible. Do not perform broad rewrites without an impact analysis and migration plan in the design document.

## Phase 6: Verify each repository

Use the strongest available checks for changed areas.

### matrix

```bash
mvn test
mvn clean package
```

Module-level checks are acceptable during iteration. State clearly when full verification was not run.

### matrix-web

```bash
npm install
npm run build
```

Also run available lint, unit-test, type-check, and end-to-end commands.

### matrix-secretary Java

```bash
cd java-service
mvn test
```

At minimum when tests cannot run:

```bash
cd java-service
mvn -q -DskipTests compile
```

### matrix-secretary Python

```bash
cd python-service
python -m compileall app
```

Run `pytest` when Python tests exist.

Never report a skipped check as passed. Record the command, result, and reason for any skip.

## Phase 7: Verify the vertical flow

For cross-repository work, test the complete path rather than isolated endpoints only.

Cover as applicable:

- normal success flow
- invalid input
- unauthenticated or unauthorized access
- tenant mismatch
- downstream timeout or unavailable service
- duplicate confirmation or duplicate execution
- high-risk action without confirmation
- scheduling concurrency and retry
- frontend error and final-state display
- execution and audit records

## Phase 8: Record implementation outcomes

Update the main design document or add an implementation document under `docs/`.

Record:

- files changed by repository
- final contracts and configuration
- database changes
- commands run and their outcomes
- checks skipped and why
- known limitations
- risks and rollback steps
- remaining follow-up work

Documentation must describe actual implementation, not the intended plan only.

## Phase 9: Create linked pull requests

When requested, create one pull request per repository that has commits.

Requirements:

- same work-item title
- same branch name
- descriptions reference the related repository pull requests
- dependency and merge order are explicit
- provider and consumer changes are identified
- verification results are included
- no automatic merge without explicit user instruction

Repositories with an intentionally empty tracking branch do not require an empty pull request unless the user requests one.

## Completion report

Return a concise report with:

```text
Work item:
Shared branch:
Design document and commit:

matrix:
- changes
- verification

matrix-secretary:
- changes
- verification

matrix-web:
- changes
- verification

Vertical-flow result:
Known limitations:
Risks and rollback:
Pull requests:
```

Do not claim completion when the docs gate, branch gate, required verification, or vertical-flow acceptance has not been satisfied.
