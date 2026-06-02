# 阶段一：任务中心稳定化

本文档记录阶段一的改造目标、已完成能力、接口约束和后续检查项。阶段一的目标不是扩展更多外部平台，而是先把 AI 秘书的任务中心做稳：任务可预览、可编辑、可确认、可取消、可重试、可查询日志。

## 1. 阶段目标

阶段一聚焦 Java 主控服务的任务计划层和任务编排层：

```text
自然语言输入
  -> Python 语义解析
  -> Java 生成 TaskPlan / TaskAction
  -> 用户预览
  -> 用户编辑 / 确认 / 取消
  -> 调度或立即执行
  -> 执行日志
```

阶段一不优先处理真实外部系统接入，例如真实企业微信、真实日历、真实审批系统。外部执行器应在任务中心稳定后逐步替换。

## 2. 当前已具备能力

- 任务预览：`POST /api/ai-task/preview`
- 任务确认：`POST /api/ai-task/{planId}/confirm`
- 任务取消：`POST /api/ai-task/{planId}/cancel`
- 失败动作重试：`POST /api/ai-task/{planId}/actions/{actionId}/retry`
- 任务详情查询：`GET /api/ai-task/{planId}`
- 任务列表查询：`GET /api/ai-task`
- 执行日志查询：`GET /api/ai-task/{planId}/logs`
- 单动作执行日志查询：`GET /api/ai-task/{planId}/actions/{actionId}/logs`
- 预览后编辑动作：`PATCH /api/ai-task/{planId}/actions/{actionId}`

## 3. 本阶段新增：预览后编辑动作

用户确认之前，可以编辑 AI 解析出来的单个 `TaskAction`，用于修正模型解析不准的标题、正文、目标对象、时间、参数和确认策略。

### 3.1 接口

```http
PATCH /api/ai-task/{planId}/actions/{actionId}
Content-Type: application/json
```

### 3.2 请求示例

```json
{
  "operatorUserId": "demo-user",
  "title": "提醒事项: 王总",
  "content": "确认项目方案已经更新，并提醒对方查看最新版",
  "target": {
    "targetType": "user",
    "name": "王总",
    "address": null
  },
  "schedule": {
    "scheduleType": "once",
    "originalText": "明天下午三点",
    "runAt": "2026-06-03T15:00:00+08:00",
    "cron": null,
    "timezone": "Asia/Shanghai",
    "nextRunAt": null,
    "lastRunAt": null,
    "triggerCount": 0
  },
  "args": {
    "source": "manual_edit"
  },
  "priority": "high",
  "requiresConfirmation": true
}
```

说明：

- `schedule.cron` 可以不传，Java 会通过 `CronScheduleService` 尽量补齐。
- 只传需要修改的字段即可；未传字段保留原值。
- 编辑后动作仍保持 `WAITING_CONFIRM`，必须再次由用户确认后才会进入执行或调度。

### 3.3 允许编辑的字段

当前接口允许编辑：

- `title`
- `content`
- `target`
- `schedule`
- `args`
- `priority`
- `requiresConfirmation`

当前接口不允许编辑：

- `actionId`
- `actionType`
- `skillName`
- `riskLevel`
- `confidence`
- `sourceSentence`
- `analysisNote`
- `status`

原因：这些字段涉及任务身份、Skill 路由、风险判断和解析依据。如果未来需要编辑，应新增更严格的接口，而不是通过普通编辑接口直接覆盖。

## 4. 状态约束

编辑动作必须满足：

```text
TaskPlan.status == WAITING_CONFIRM
TaskAction.status == WAITING_CONFIRM
```

不允许编辑已经确认、已经调度、已经执行、失败或取消的动作。若需要修改这些动作，应走取消、重试或新建任务流程。

## 5. 操作人约束

阶段一仍然是 demo 级 userId 隔离，但已收紧写操作校验：

- 如果 `TaskPlan.userId` 为空，保持本地 demo 兼容。
- 如果 `TaskPlan.userId` 不为空，`operatorUserId` 必须与 `TaskPlan.userId` 一致。
- `confirm`、`cancel`、`retryAction`、`editAction` 都应使用同一套操作人校验。

生产级版本应改为从登录态/JWT 中解析 `userId`，禁止客户端任意传入 `userId` 或 `operatorUserId`。

## 6. 审计日志

编辑动作属于任务状态变化，必须写入 `ai_task_execution_log`。当前编辑后会记录：

- `planId`
- `actionId`
- 编辑前状态
- 编辑后状态
- 编辑前内容和时间信息
- 编辑后执行说明
- `operatorUserId`

后续需要补充更细粒度的 diff，例如字段级变更记录：`content: old -> new`、`target: old -> new`。

## 7. 后续检查项

阶段一后续还需要继续补强：

1. 增加 Controller / Service 单元测试。
2. 增加编辑接口的非法状态测试。
3. 增加 userId 越权写操作测试。
4. 增加分页查询，避免任务列表全量返回。
5. 增加统一异常处理，避免直接暴露 Java 异常文本。
6. 增加字段级编辑审计 diff。
7. 将 `userId` / `operatorUserId` 从请求参数迁移到登录态。

## 8. 验收标准

阶段一完成后，至少应能跑通以下链路：

```text
POST /preview
  -> PATCH /actions/{actionId}
  -> GET /{planId}
  -> POST /confirm
  -> GET /{planId}/logs
```

对于非法链路，应返回明确错误：

- 已确认任务不允许编辑。
- 已执行任务不允许整体取消。
- 非 FAILED 动作不允许重试。
- 操作人不匹配时不允许写操作。
