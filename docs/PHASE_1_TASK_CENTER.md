# 阶段一：任务中心稳定化

本文档记录 `matrix-secretary` 阶段一的目标、边界、已完成内容、接口说明、状态约束、验收标准和长期计划。

文档命名沿用 `docs/AI_SECRETARY_EVOLUTION_PLAN.md` 的大写下划线风格，统一放在 `docs/` 目录下，方便后续每个阶段都形成独立设计文档。

## 1. 阶段定位

阶段一的核心任务是把项目从“能演示的 AI 秘书 Demo”推进到“可管理的任务中心”。

AI 秘书的核心不是聊天，而是稳定地把自然语言转成可追踪的任务计划：

```text
自然语言输入
  -> Python 语义解析
  -> Java 生成 TaskPlan / TaskAction
  -> 用户预览
  -> 用户编辑 / 确认 / 取消
  -> 调度或立即执行
  -> Skill 执行
  -> 执行日志 / 审计
```

阶段一暂时不追求接入更多外部平台，例如真实企业微信、飞书、日历、审批系统。外部执行器可以继续模拟，但任务中心的基础能力必须先稳定。

## 2. 阶段目标

阶段一需要达成以下目标：

1. 用户可以提交自然语言并得到结构化任务预览。
2. 用户可以在确认前编辑 AI 解析结果，修正对象、正文、时间、标题、参数等字段。
3. 用户可以确认任务，确认后由 Java 决定立即执行或进入调度。
4. 用户可以取消未执行任务。
5. 用户可以重试失败动作。
6. 用户可以查询任务详情和执行日志。
7. 写操作必须记录 `operatorUserId`，并做基础 userId 隔离。
8. 每次状态变化必须写入业务执行日志，而不是只依赖应用日志。

## 3. 阶段边界

### 3.1 本阶段要做

- 强化 `TaskPlan` / `TaskAction` 的可管理性。
- 增加确认前编辑能力。
- 收紧写操作的操作人校验。
- 保持高风险动作确认机制。
- 补充阶段文档和 README 接口说明。
- 为后续测试、权限、真实执行器打基础。

### 3.2 本阶段暂不做

- 不接入真实企业微信、飞书、钉钉。
- 不接入真实日历系统。
- 不做完整用户登录体系。
- 不做多租户 RBAC 权限模型。
- 不重构全部调度机制。
- 不把 Python 语义解析迁移到 Java。

这些内容放到后续阶段推进，避免第一阶段改动过大。

## 4. 当前已具备能力

当前任务中心已经具备以下 API：

| 能力 | 接口 |
|---|---|
| 查询 Skill 列表 | `GET /api/skills` |
| 任务预览 | `POST /api/ai-task/preview` |
| 确认前编辑动作 | `PATCH /api/ai-task/{planId}/actions/{actionId}` |
| 任务确认 | `POST /api/ai-task/{planId}/confirm` |
| 任务取消 | `POST /api/ai-task/{planId}/cancel` |
| 失败动作重试 | `POST /api/ai-task/{planId}/actions/{actionId}/retry` |
| 任务详情查询 | `GET /api/ai-task/{planId}` |
| 任务列表查询 | `GET /api/ai-task` |
| 执行日志查询 | `GET /api/ai-task/{planId}/logs` |
| 单动作执行日志查询 | `GET /api/ai-task/{planId}/actions/{actionId}/logs` |

## 5. 本次已完成内容

本次阶段一推进已经完成以下内容：

### 5.1 新增确认前编辑动作接口

新增接口：

```http
PATCH /api/ai-task/{planId}/actions/{actionId}
Content-Type: application/json
```

用途：用户在确认任务前，可以修正 AI 解析出的单个 `TaskAction`。

典型场景：

- AI 把联系人识别错了。
- AI 把提醒内容截断了。
- AI 生成的时间不准确。
- 用户希望把普通优先级改成高优先级。
- 用户希望某个动作即使不是高风险也必须确认。

### 5.2 新增请求模型

新增模型：

```text
java-service/src/main/java/com/kailei/demo/model/EditTaskActionRequest.java
```

该模型承载用户可编辑字段：

```text
title
content
target
schedule
args
priority
requiresConfirmation
```

### 5.3 扩展 TaskAction 编辑方法

在 `TaskAction` 中增加 `withEditableFields(...)`，用于只更新允许编辑的字段，同时保留任务身份和执行路由字段。

保留字段包括：

```text
actionId
actionType
skillName
riskLevel
confidence
sourceSentence
analysisNote
```

原因是这些字段涉及任务身份、Skill 路由、风险判断和 AI 解析依据，不能通过普通编辑接口随意覆盖。

### 5.4 收紧写操作 userId 校验

本阶段对写操作做了基础隔离：

- 如果 `TaskPlan.userId` 为空，保持本地 demo 兼容。
- 如果 `TaskPlan.userId` 不为空，写操作必须显式传入一致的 `operatorUserId`。
- `confirm`、`cancel`、`retryAction`、`editAction` 统一走操作人校验。

生产级版本应继续升级为：

```text
登录态 / JWT -> 后端解析 userId / tenantId -> 所有写操作不再信任请求体里的 userId
```

### 5.5 补充文档

本次同步补充：

- `docs/PHASE_1_TASK_CENTER.md`：阶段一专项文档。
- `docs/AI_SECRETARY_EVOLUTION_PLAN.md`：增加阶段一文档链接和当前进展。
- `README.md`：增加编辑接口、请求示例和状态流转说明。

## 6. 编辑接口说明

### 6.1 请求示例

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

### 6.2 字段规则

- `operatorUserId`：写操作人。若任务已有 `userId`，该字段必须传，并且必须一致。
- `title`：任务标题；为空时保留原值。
- `content`：任务正文、邮件正文、消息正文或提醒内容。
- `target`：目标对象。
- `schedule`：调度信息；如果未传 `cron`，Java 会通过 `CronScheduleService` 尽量补齐。
- `args`：Skill 扩展参数。
- `priority`：优先级。
- `requiresConfirmation`：是否需要确认。

### 6.3 编辑后的状态

编辑后：

```text
TaskPlan.status = WAITING_CONFIRM
TaskAction.status = WAITING_CONFIRM
TaskAction.executionNote = 用户已编辑任务参数，等待确认
```

编辑不会触发执行。用户必须再次调用确认接口，任务才会进入执行或调度。

## 7. 状态约束

编辑动作必须满足：

```text
TaskPlan.status == WAITING_CONFIRM
TaskAction.status == WAITING_CONFIRM
```

不允许编辑以下状态的动作：

```text
CONFIRMED
SCHEDULED
EXECUTED
FAILED
CANCELLED
```

原因：

- 已确认任务已经进入执行决策阶段，不应再静默变更参数。
- 已调度任务可能已经被调度器扫描，不应直接修改执行时间或内容。
- 已执行任务不能被“编辑成未执行”。
- 失败任务应走重试流程。
- 取消任务应重新创建。

## 8. 审计日志要求

编辑动作属于状态变化，必须记录到 `ai_task_execution_log`。

当前记录内容包括：

- `planId`
- `actionId`
- `skillName`
- 编辑前状态
- 编辑后状态
- 编辑前请求载荷
- 编辑后响应载荷
- `operatorUserId`
- `createdAt`

后续应增强为字段级 diff，例如：

```text
content: old -> new
target.name: old -> new
schedule.runAt: old -> new
priority: normal -> high
```

## 9. 验收标准

阶段一至少应能跑通以下正向链路：

```text
POST /api/ai-task/preview
  -> PATCH /api/ai-task/{planId}/actions/{actionId}
  -> GET /api/ai-task/{planId}
  -> POST /api/ai-task/{planId}/confirm
  -> GET /api/ai-task/{planId}/logs
```

阶段一还应覆盖以下异常链路：

- 已确认任务不允许编辑。
- 已调度任务不允许编辑。
- 已执行任务不允许整体取消。
- 非 FAILED 动作不允许重试。
- `operatorUserId` 与 `TaskPlan.userId` 不一致时，不允许写操作。
- 缺少 `operatorUserId` 时，如果任务已有 `userId`，不允许写操作。

## 10. 后续短期计划

阶段一后续还需要继续补强：

1. 增加 Controller / Service 单元测试。
2. 增加编辑接口的非法状态测试。
3. 增加 userId 越权写操作测试。
4. 增加分页查询，避免任务列表全量返回。
5. 增加统一异常处理，避免直接暴露 Java 异常文本。
6. 增加字段级编辑审计 diff。
7. 增加 task/action 级别的状态机服务，避免状态流转分散在业务代码中。

## 11. 长期计划

阶段一完成后，任务中心应继续向生产级能力演进。

### 11.1 权限和身份体系

长期应废弃请求体中的 `userId` / `operatorUserId` 作为可信来源，改为：

```text
登录认证 -> JWT / Session -> 后端解析 userId / tenantId / role -> 服务层统一鉴权
```

需要补充：

- 用户表。
- 租户表。
- 角色权限。
- API Key 与用户绑定。
- 操作审计。

### 11.2 状态机服务

当前状态流转仍分散在 `AiTaskService` 和 `TaskExecutionService` 中。长期应抽象：

```text
TaskStateMachineService
  - canEdit
  - canConfirm
  - canCancel
  - canRetry
  - canSchedule
  - canExecute
```

状态变化必须显式校验，避免后续功能扩展时出现隐式状态跳转。

### 11.3 调度可靠性

当前调度仍是 demo 级。长期需要支持：

- 只扫描到期任务。
- 分页扫描。
- 数据库锁或分布式锁。
- 幂等键。
- 最大重试次数。
- 失败退避。
- 超时恢复。
- 多实例防重复执行。

目标是让调度从“能跑”升级为“可恢复、可扩容、可审计”。

### 11.4 Skill 执行器拆分

当前执行器仍偏集中。长期应拆为：

```text
ReminderSkillExecutor
TodoSkillExecutor
EmailDraftSkillExecutor
EmailSendSkillExecutor
MessageSkillExecutor
CalendarSkillExecutor
HttpWebhookSkillExecutor
PromptSkillExecutor
```

每类 Skill 独立控制参数校验、权限、风险等级、幂等和执行日志。

### 11.5 真实执行器接入

阶段二开始逐步接入真实执行能力：

- Reminder：站内提醒 / Web 通知。
- Todo：本地待办。
- Email：邮件草稿 / 邮件发送。
- Message：飞书 / 企业微信 / 钉钉。
- Calendar：日历事件。
- HTTP Webhook：外部系统集成。

所有高风险写操作仍必须保留人工确认。

### 11.6 语义解析评测体系

长期需要为 Python / LLM 语义解析建立回归测试集：

```text
simple_reminder
multi_task
email_draft
recurring_task
conditional_task
ambiguous_target
```

每次修改 prompt、规则 fallback、后处理逻辑，都必须能验证解析质量没有明显退化。

## 12. 文档维护规则

后续每一阶段都应在 `docs/` 下新增或维护对应阶段文档，命名采用大写下划线风格：

```text
docs/PHASE_1_TASK_CENTER.md
docs/PHASE_2_REAL_EXECUTORS.md
docs/PHASE_3_AUTH_AND_PERMISSION.md
docs/PHASE_4_RELIABLE_SCHEDULING.md
```

每个阶段文档至少包含：

- 阶段定位。
- 阶段目标。
- 本阶段做什么。
- 本阶段不做什么。
- 已完成内容。
- 接口或模型变化。
- 状态或权限约束。
- 验收标准。
- 后续计划。
- 长期计划。
