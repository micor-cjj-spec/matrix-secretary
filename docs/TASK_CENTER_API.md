# Task Center API 说明

本文档记录任务中心平台入口 `/api/task-center/**` 的接口规范。

`/api/ai-task/**` 保留为 AI 秘书应用兼容入口；`/api/task-center/**` 是平台化入口，直接面向 Web、Dify、外部系统、后续前端或其他自动化调用方。

## 1. API 分层

当前项目同时保留两套入口：

```text
/api/ai-task/**
  -> AI 秘书兼容入口
  -> 由 AiTaskService 门面委托 Task Center 四大服务

/api/task-center/**
  -> 任务中心平台入口
  -> 由 TaskCenterController 直接调用 TaskPreviewService / TaskCommandService / TaskQueryService
```

平台化后的核心服务：

```text
TaskPreviewService      -> preview
TaskCommandService      -> edit / confirm / cancel / retry / manual-resolve / reopen-final-failure
TaskQueryService        -> get / list / session query
TaskDispatchService     -> due task dispatch
TaskStateMachineService -> 状态规则
```

## 2. 新旧 API 对照

| 能力 | AI 秘书兼容入口 | 任务中心平台入口 |
|---|---|---|
| 任务预览 | `POST /api/ai-task/preview` | `POST /api/task-center/plans/preview` |
| 查询单个计划 | `GET /api/ai-task/{planId}` | `GET /api/task-center/plans/{planId}` |
| 查询计划列表 | `GET /api/ai-task` | `GET /api/task-center/plans` |
| 编辑动作 | `PATCH /api/ai-task/{planId}/actions/{actionId}` | `PATCH /api/task-center/plans/{planId}/actions/{actionId}` |
| 确认计划 | `POST /api/ai-task/{planId}/confirm` | `POST /api/task-center/plans/{planId}/confirm` |
| 取消计划 | `POST /api/ai-task/{planId}/cancel` | `POST /api/task-center/plans/{planId}/cancel` |
| 重试动作 | `POST /api/ai-task/{planId}/actions/{actionId}/retry` | `POST /api/task-center/plans/{planId}/actions/{actionId}/retry` |
| 人工处理动作 | `POST /api/ai-task/{planId}/actions/{actionId}/manual-resolve` | `POST /api/task-center/plans/{planId}/actions/{actionId}/manual-resolve` |
| 重新打开最终失败 | `POST /api/ai-task/{planId}/actions/{actionId}/reopen-final-failure` | `POST /api/task-center/plans/{planId}/actions/{actionId}/reopen-final-failure` |
| 查询计划日志 | `GET /api/ai-task/{planId}/logs` | `GET /api/task-center/plans/{planId}/logs` |
| 查询动作日志 | `GET /api/ai-task/{planId}/actions/{actionId}/logs` | `GET /api/task-center/plans/{planId}/actions/{actionId}/logs` |
| 查询会话 | `GET /api/ai-task/sessions/{sessionId}` | `GET /api/task-center/sessions/{sessionId}` |
| 查询会话计划 | `GET /api/ai-task/sessions/{sessionId}/plans` | `GET /api/task-center/sessions/{sessionId}/plans` |

## 3. 任务预览

```http
POST /api/task-center/plans/preview
Content-Type: application/json
```

```json
{
  "text": "1分钟后提醒我喝水",
  "timezone": "Asia/Shanghai",
  "userId": "demo-user",
  "sessionId": "demo-session"
}
```

返回：

```json
{
  "planId": "plan-xxxx",
  "traceId": "trace-xxxx",
  "sessionId": "demo-session",
  "sourceText": "1分钟后提醒我喝水",
  "userId": "demo-user",
  "status": "WAITING_CONFIRM",
  "tasks": []
}
```

说明：

- 预览只生成 `TaskPlan` 和 `TaskAction`。
- 预览阶段默认进入 `WAITING_CONFIRM`。
- 是否自动确认由上层入口策略决定，例如飞书 `ChannelTaskFacade` 可对低风险 reminder 自动确认。

## 4. 查询任务

```http
GET /api/task-center/plans/{planId}?userId=demo-user
```

```http
GET /api/task-center/plans?userId=demo-user
```

权限规则：

```text
如果 TaskPlan.userId 不为空，并且请求传入 userId，则二者必须一致。
```

## 5. 编辑动作

```http
PATCH /api/task-center/plans/{planId}/actions/{actionId}
Content-Type: application/json
```

```json
{
  "operatorUserId": "demo-user",
  "title": "提醒喝水",
  "content": "该喝水了",
  "priority": "P1",
  "requiresConfirmation": false
}
```

编辑规则：

```text
1. 只有 WAITING_CONFIRM 状态的 TaskPlan 允许编辑。
2. 只有 WAITING_CONFIRM 状态的 TaskAction 允许编辑。
3. operatorUserId 必须与 TaskPlan.userId 一致。
4. 未传字段保留原值。
5. 编辑会写 TaskExecutionLog。
```

## 6. 确认计划

```http
POST /api/task-center/plans/{planId}/confirm
Content-Type: application/json
```

```json
{
  "operatorUserId": "demo-user"
}
```

确认规则：

```text
1. WAITING_CONFIRM 状态下会确认每个 action。
2. 立即任务可能直接 EXECUTED。
3. 调度任务会进入 SCHEDULED。
4. 非 WAITING_CONFIRM 状态重复确认时，不重复执行。
```

## 7. 取消计划

```http
POST /api/task-center/plans/{planId}/cancel
Content-Type: application/json
```

```json
{
  "operatorUserId": "demo-user",
  "reason": "用户主动取消"
}
```

取消规则：

```text
1. 如果计划中存在 EXECUTED action，不允许整体取消。
2. 未执行 action 会被标记为 CANCELLED。
3. 取消会写 TaskExecutionLog。
4. CANCELLED 计划再次取消时直接返回当前状态。
```

## 8. 重试动作

```http
POST /api/task-center/plans/{planId}/actions/{actionId}/retry
Content-Type: application/json
```

```json
{
  "operatorUserId": "demo-user"
}
```

重试规则：

```text
1. 只有 FAILED action 允许重试。
2. 重试会调用 TaskExecutionService.executeNow。
3. 重试结束后会重新计算 TaskPlan 状态。
4. FAILED_FINAL 不允许普通 retry，必须走 reopen-final-failure。
```

## 9. 人工处理动作

```http
POST /api/task-center/plans/{planId}/actions/{actionId}/manual-resolve
Content-Type: application/json
```

```json
{
  "operatorUserId": "demo-user",
  "title": "修正后的动作标题",
  "content": "修正后的动作内容",
  "target": {
    "targetType": "user",
    "name": "张三",
    "address": null
  },
  "schedule": null,
  "args": {
    "platform": "feishu"
  },
  "priority": "P1",
  "requiresConfirmation": false,
  "executeNow": true,
  "note": "补齐缺失参数后立即执行"
}
```

人工处理规则：

```text
1. 只有 NEEDS_MANUAL_REVIEW action 允许人工处理。
2. 可在请求中修复 title / content / target / schedule / args / priority / requiresConfirmation。
3. executeNow=true 时会立即调用 TaskExecutionService.executeNow。
4. executeNow=false 且 schedule 有效时会重新进入 SCHEDULED。
5. 处理过程会写 TaskExecutionLog，并重新计算 TaskPlan 状态。
```

## 10. 重新打开最终失败

```http
POST /api/task-center/plans/{planId}/actions/{actionId}/reopen-final-failure
Content-Type: application/json
```

```json
{
  "operatorUserId": "admin-user",
  "args": {
    "url": "http://127.0.0.1:10002/api/mock/skill-endpoint"
  },
  "executeNow": false,
  "note": "管理员修复 Skill 配置后重新进入调度队列"
}
```

重新打开规则：

```text
1. 只有 FAILED_FINAL action 允许重新打开。
2. 典型用途是管理员修复配置后，让不可重试失败重新进入可执行链路。
3. 可在请求中修复 title / content / target / schedule / args / priority / requiresConfirmation。
4. executeNow=true 时会立即执行；否则如果 schedule 有效，会重新进入 SCHEDULED。
5. 非 FAILED_FINAL action 调用该接口应返回 BAD_REQUEST。
6. 操作会写 TaskExecutionLog，并重新计算 TaskPlan 状态。
```

## 11. 日志查询

```http
GET /api/task-center/plans/{planId}/logs?userId=demo-user
```

```http
GET /api/task-center/plans/{planId}/actions/{actionId}/logs?userId=demo-user
```

日志查询前会先通过 `TaskQueryService.get(planId, userId)` 校验访问权限。

## 12. 会话查询

```http
GET /api/task-center/sessions/{sessionId}?userId=demo-user
```

```http
GET /api/task-center/sessions/{sessionId}/plans?userId=demo-user
```

## 13. 状态规则摘要

```text
WAITING_CONFIRM      -> 可编辑 / 可确认 / 可取消
CONFIRMED            -> 已确认，等待执行服务处理
SCHEDULED            -> 等待调度触发
EXECUTED             -> 已执行，不能整体取消
FAILED               -> 可普通 retry
NEEDS_MANUAL_REVIEW  -> 参数或上下文需要人工修复，只能 manual-resolve
FAILED_FINAL         -> 配置类或不可重试失败，只能 reopen-final-failure
CANCELLED            -> 已取消
```

计划状态由 `TaskStateMachineService.resolvePlanStatus()` 根据 action 状态统一计算。

`ConfirmTaskResponse.executionSummary` 当前包含：

```text
executed
scheduled
failed
failedFinal
manualReview
cancelled
```

## 14. 本地验证建议

启动 Java 服务后，可在 Swagger 中查看新接口：

```text
http://127.0.0.1:10002/swagger-ui/index.html
```

也可以直接访问 OpenAPI JSON：

```text
http://127.0.0.1:10002/v3/api-docs
```

建议优先验证：

```text
1. POST /api/task-center/plans/preview
2. PATCH /api/task-center/plans/{planId}/actions/{actionId}
3. POST /api/task-center/plans/{planId}/confirm
4. POST /api/task-center/plans/{planId}/actions/{actionId}/retry
5. POST /api/task-center/plans/{planId}/actions/{actionId}/manual-resolve
6. POST /api/task-center/plans/{planId}/actions/{actionId}/reopen-final-failure
7. GET  /api/task-center/plans/{planId}/logs
8. GET  /api/task-center/plans/{planId}/actions/{actionId}/logs
```

异常链路建议验证：

```text
1. 非 FAILED action 调 retry，应返回 BAD_REQUEST。
2. 非 NEEDS_MANUAL_REVIEW action 调 manual-resolve，应返回 BAD_REQUEST。
3. 非 FAILED_FINAL action 调 reopen-final-failure，应返回 BAD_REQUEST。
4. operatorUserId 与 TaskPlan.userId 不一致，应返回 BAD_REQUEST。
```
