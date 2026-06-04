# 任务中心平台化设计方案

本文档用于约束 `matrix-secretary` 从“AI 秘书 Demo / 应用”抽象为“任务中心平台”的演进方向。

后续改造的核心目标不是继续堆叠更多渠道、更多 Skill 或更多聊天能力，而是先把任务中心平台能力沉淀稳定，使飞书、Web、Dify、Python 语义解析、邮件、提醒、待办、日历等都成为任务中心外围的入口或执行器。

## 1. 平台定位

`matrix-secretary` 后续应分成两个概念：

```text
AI Secretary = 一个基于任务中心的 AI 秘书应用场景
Task Center  = 真正的平台核心
```

任务中心平台的核心职责是：

```text
把来自不同入口的任务请求，统一转换为可预览、可编辑、可确认、可调度、可执行、可审计、可恢复的任务计划。
```

因此，平台核心不应该依赖具体入口：

- 不依赖飞书。
- 不依赖 Web 页面。
- 不依赖 Dify。
- 不依赖 Python 语义解析。
- 不依赖某个具体 Skill 实现。
- 不依赖某个具体外部系统。

这些能力都应该通过 Adapter、Parser、Executor、Client 等外围组件接入。

## 2. 核心链路

平台主链路统一为：

```text
任务输入
  -> 任务草稿生成
  -> 任务计划预览
  -> 用户编辑 / 确认 / 取消
  -> 调度或立即执行
  -> Skill 执行
  -> 执行日志 / 审计
  -> 失败恢复 / 重试
```

不同入口只负责把请求转换成平台可识别的任务命令。

示例：

```text
飞书消息
  -> ChannelIncomingMessage
  -> PreviewTaskCommand
  -> TaskPlan

Web 表单
  -> PreviewTaskCommand
  -> TaskPlan

Dify Workflow
  -> HTTP API
  -> PreviewTaskCommand
  -> TaskPlan

定时扫描器
  -> DispatchDueTaskCommand
  -> TaskAction 执行
```

平台核心只处理任务，不关心任务最初来自哪里。

## 3. 分层目标架构

建议目标架构分为七层：

```text
1. Entry Layer 入口层
   - Web
   - REST API
   - 飞书
   - Dify
   - 浏览器插件
   - 后续钉钉 / QQ / 企业微信

2. Semantic Layer 语义理解层
   - AiSemanticParser
   - PythonSemanticParser
   - RuleFallbackSemanticParser
   - 后续 SpringAiSemanticParser

3. Task Center Core 任务中心核心层
   - TaskPlan
   - TaskAction
   - TaskSchedule
   - TaskStateMachineService
   - TaskCommandService
   - TaskQueryService

4. Scheduling Layer 调度编排层
   - 一次性任务
   - 周期任务
   - 到期扫描
   - 幂等
   - 锁
   - 重试
   - 超时恢复

5. Skill Layer 技能执行层
   - ReminderSkillExecutor
   - TodoSkillExecutor
   - EmailDraftSkillExecutor
   - EmailSendSkillExecutor
   - MessageSkillExecutor
   - CalendarSkillExecutor
   - HttpWebhookSkillExecutor

6. Channel Layer 渠道层
   - ChannelAdapter
   - FeishuChannelAdapter
   - WebChannelAdapter
   - DingTalkChannelAdapter
   - WeComChannelAdapter

7. Audit / Governance 审计治理层
   - 执行日志
   - 操作日志
   - 风险等级
   - userId / tenantId 隔离
   - API Key / JWT
   - 权限控制
```

核心依赖方向必须保持：

```text
Entry / Channel / Semantic / Skill
  -> Task Center Core
```

禁止反向依赖：

```text
Task Center Core -> Feishu
Task Center Core -> Dify
Task Center Core -> 具体聊天平台
```

## 4. 核心领域模型

### 4.1 TaskPlan

`TaskPlan` 表示一次任务请求生成的整体任务计划。

建议字段：

```text
planId
traceId
tenantId
userId
sessionId
sourceType       // WEB / FEISHU / API / DIFY / SYSTEM
sourceId         // 飞书 messageId / API requestId / 外部事件ID
sourceText
status
riskLevel
warnings
createdAt
updatedAt
confirmedAt
cancelledAt
```

设计原则：

- `TaskPlan` 是任务中心的聚合根。
- 每个可执行动作必须属于某个 `TaskPlan`。
- 不允许绕过 `TaskPlan` 直接执行 Skill。
- `sourceText` 必须保留，方便审计和后续回放。
- `sourceType` 和 `sourceId` 用于渠道追踪和幂等治理。

### 4.2 TaskAction

`TaskAction` 表示任务计划里的一个可执行动作。

建议字段：

```text
actionId
planId
actionType       // reminder / email / todo / message / calendar / http
skillName
title
content
target
schedule
args
priority
riskLevel
requiresConfirmation
confidence
sourceSentence
analysisNote
status
executionNote
createdAt
updatedAt
```

设计原则：

- 一个 `TaskPlan` 可以包含多个 `TaskAction`。
- 高风险 `TaskAction` 必须确认后才能执行。
- `actionType` 描述业务意图，`skillName` 描述具体执行路由。
- `args` 只能作为扩展参数，不能替代核心字段。
- 不允许通过普通编辑接口修改 `actionId`、`actionType`、`skillName`、`riskLevel` 等关键路由字段。

### 4.3 TaskSchedule

`TaskSchedule` 表示任务调度信息。

建议字段：

```text
scheduleType     // immediate / once / recurring
originalText
runAt
cron
timezone
nextRunAt
lastRunAt
triggerCount
```

后续可靠性增强字段：

```text
executionAttempt
maxRetryCount
idempotencyKey
lockedBy
lockedAt
lastExecutionAt
```

设计原则：

- 调度信息必须持久化。
- 不允许只依赖内存定时器。
- 一次性任务执行成功后应进入 `EXECUTED`。
- 周期任务执行成功后应回到 `SCHEDULED`，并推进 `nextRunAt`。
- 调度扫描必须逐步从全量扫描升级为“只扫描到期任务”。

### 4.4 TaskExecutionLog

`TaskExecutionLog` 表示任务状态变化、调度、执行、失败、重试等审计日志。

建议字段：

```text
logId
planId
actionId
eventType        // PREVIEW / EDIT / CONFIRM / SCHEDULE / EXECUTE / RETRY / CANCEL
skillName
beforeStatus
afterStatus
requestPayload
responsePayload
errorMessage
operatorUserId
operatorType     // USER / SYSTEM / SCHEDULER / CHANNEL
traceId
createdAt
```

设计原则：

- 状态变化必须写业务执行日志。
- 不能只依赖应用日志。
- 失败原因必须可追踪。
- 后续编辑动作应支持字段级 diff。
- 日志中的敏感字段需要脱敏。

## 5. 状态机设计

任务中心平台必须显式抽象状态机，避免状态流转散落在业务代码中。

### 5.1 TaskPlan 状态

建议保留或扩展以下状态：

```text
WAITING_CONFIRM  等待确认
CONFIRMED         已确认
SCHEDULED         已调度
EXECUTED          已执行
FAILED            执行失败
CANCELLED         已取消
```

### 5.2 TaskAction 状态

`TaskAction` 状态建议与 `TaskPlan` 保持一致，但语义上以动作执行为准。

### 5.3 核心状态规则

```text
1. 预览后默认进入 WAITING_CONFIRM。
2. 只有 WAITING_CONFIRM 状态允许编辑。
3. 高风险动作不能跳过确认。
4. 已执行动作不能被编辑成未执行。
5. 已执行计划不能整体取消。
6. 只有 FAILED 动作允许 retry。
7. 调度任务确认后进入 SCHEDULED。
8. 一次性调度任务到期执行成功后进入 EXECUTED。
9. 周期调度任务到期执行成功后仍保持 SCHEDULED，并推进 nextRunAt。
10. 全部动作取消时，计划状态为 CANCELLED。
11. 任一动作调度中时，计划状态为 SCHEDULED。
12. 任一动作失败时，计划状态为 FAILED，除非仍有更高优先级状态规则覆盖。
13. 全部动作执行成功时，计划状态为 EXECUTED。
```

### 5.4 TaskStateMachineService

优先新增：

```java
@Service
public class TaskStateMachineService {

    public void ensureCanEdit(TaskPlan plan, TaskAction action) {}

    public void ensureCanConfirm(TaskPlan plan) {}

    public void ensureCanCancel(TaskPlan plan) {}

    public void ensureCanRetry(TaskPlan plan, TaskAction action) {}

    public TaskStatus resolvePlanStatus(List<TaskAction> actions) {}

    public TaskAction transition(
            String planId,
            TaskAction before,
            TaskStatus nextStatus,
            String note,
            String operatorUserId
    ) {}
}
```

第一阶段先不追求复杂状态机框架，先把状态校验和状态计算收敛到一个服务里。

## 6. Command 设计

平台应把不同入口转换为统一命令，而不是让 Controller、ChannelFacade、Scheduler 各自拼业务逻辑。

建议命令模型：

```text
PreviewTaskCommand
EditTaskActionCommand
ConfirmTaskCommand
CancelTaskCommand
RetryTaskActionCommand
DispatchDueTaskCommand
ExecuteActionCommand
```

### 6.1 PreviewTaskCommand

```text
text
timezone
userId
tenantId
sessionId
sourceType
sourceId
operatorUserId
```

### 6.2 EditTaskActionCommand

```text
planId
actionId
operatorUserId
title
content
target
schedule
args
priority
requiresConfirmation
```

### 6.3 ConfirmTaskCommand

```text
planId
operatorUserId
confirmReason
```

### 6.4 CancelTaskCommand

```text
planId
operatorUserId
cancelReason
```

### 6.5 RetryTaskActionCommand

```text
planId
actionId
operatorUserId
retryReason
```

设计原则：

- Controller 负责组装 Command。
- Channel 层负责组装 Command。
- Dify / 外部 API 负责组装 Command。
- 任务中心应用服务统一处理 Command。

## 7. 服务拆分方案

当前 `AiTaskService` 承担了预览、编辑、确认、取消、重试、查询、调度等多种职责。平台化后建议拆成多个应用服务。

目标结构：

```text
TaskPreviewService
  - preview(command)

TaskCommandService
  - editAction(command)
  - confirm(command)
  - cancel(command)
  - retryAction(command)

TaskQueryService
  - get(planId, userId)
  - list(query)
  - listBySession(sessionId, userId)

TaskDispatchService
  - dispatchDueTasks()
  - dispatchAction(plan, action)

TaskExecutionService
  - confirmAction(planId, userId, action, operatorUserId)
  - executeNow(planId, userId, action, operatorUserId)

TaskStateMachineService
  - 状态校验
  - 状态转换
  - 计划状态计算
```

过渡期可以保留 `AiTaskService` 作为兼容门面：

```text
AiTaskService
  -> TaskPreviewService
  -> TaskCommandService
  -> TaskQueryService
  -> TaskDispatchService
```

这样旧 Controller 和现有飞书链路可以先不大改。

## 8. 包结构建议

建议逐步演进为：

```text
com.kailei.demo.taskcenter
  ├── domain
  │   ├── TaskPlan
  │   ├── TaskAction
  │   ├── TaskSchedule
  │   ├── TaskStatus
  │   ├── TaskRiskLevel
  │   └── TaskEventType
  │
  ├── command
  │   ├── PreviewTaskCommand
  │   ├── EditTaskActionCommand
  │   ├── ConfirmTaskCommand
  │   ├── CancelTaskCommand
  │   └── RetryTaskActionCommand
  │
  ├── application
  │   ├── TaskPreviewService
  │   ├── TaskCommandService
  │   ├── TaskQueryService
  │   ├── TaskDispatchService
  │   └── TaskExecutionService
  │
  ├── statemachine
  │   └── TaskStateMachineService
  │
  ├── repository
  │   ├── TaskPlanRepository
  │   └── TaskExecutionLogRepository
  │
  └── api
      └── TaskCenterController
```

外围包：

```text
com.kailei.demo.semantic
  ├── AiSemanticParser
  ├── PythonSemanticParser
  └── RuleFallbackSemanticParser

com.kailei.demo.channel
  ├── core
  ├── model
  └── feishu

com.kailei.demo.skill
  ├── SkillCatalog
  ├── SkillExecutor
  ├── ReminderSkillExecutor
  ├── TodoSkillExecutor
  ├── EmailSkillExecutor
  ├── MessageSkillExecutor
  └── HttpWebhookSkillExecutor
```

迁移时不要求一次性改包名，优先保证职责边界先稳定。

## 9. API 抽象设计

现有 `/api/ai-task/**` 可以保留，作为 AI 秘书应用 API。

同时建议新增平台化 API：

```text
POST   /api/task-center/plans/preview
GET    /api/task-center/plans/{planId}
GET    /api/task-center/plans
PATCH  /api/task-center/plans/{planId}/actions/{actionId}
POST   /api/task-center/plans/{planId}/confirm
POST   /api/task-center/plans/{planId}/cancel
POST   /api/task-center/plans/{planId}/actions/{actionId}/retry
GET    /api/task-center/plans/{planId}/logs
GET    /api/task-center/plans/{planId}/actions/{actionId}/logs
```

兼容策略：

```text
/api/ai-task/**
  -> 兼容旧入口
  -> 内部委托 Task Center Service

/api/task-center/**
  -> 新平台入口
  -> 面向 Web / Dify / 外部系统 / 后续前端
```

API 设计原则：

- 写操作必须有 `operatorUserId`，后续改为从 JWT / API Key 解析。
- 查询接口必须做 userId / tenantId 隔离。
- 错误响应必须统一。
- 高风险动作必须经过确认。
- 外部入口不能绕过任务计划直接执行 Skill。

## 10. 调度可靠性设计

当前阶段可以先保持本地调度器，但平台化后必须逐步增强可靠性。

### 10.1 当前可接受能力

```text
- 支持一次性任务。
- 支持周期任务。
- 支持 nextRunAt。
- 支持 lastRunAt。
- 支持 triggerCount。
- 支持执行日志。
```

### 10.2 后续必须增强

```text
1. 只扫描到期任务，不再全量扫描。
2. 分页扫描。
3. 数据库锁或分布式锁。
4. idempotencyKey。
5. executionAttempt。
6. maxRetryCount。
7. 失败退避。
8. 超时恢复。
9. 多实例防重复执行。
10. 调度器异常恢复。
```

### 10.3 推荐调度查询

后续 repository 应提供：

```java
List<TaskPlan> findDueScheduledPlans(OffsetDateTime now, int limit);
```

或直接扫描 action 级别：

```java
List<TaskActionRecord> findDueScheduledActions(OffsetDateTime now, int limit);
```

不要长期使用：

```java
repository.findAll()
```

再逐个判断是否到期。

## 11. Skill 抽象设计

当前 `GenericSkillExecutor` 可以作为 Demo 期统一执行器，但平台化后应逐步拆分。

目标接口：

```java
public interface SkillExecutor {

    boolean supports(SkillDefinition skill, TaskAction action);

    TaskAction execute(String planId, String userId, SkillDefinition skill, TaskAction action);
}
```

推荐拆分：

```text
ReminderSkillExecutor
TodoSkillExecutor
EmailDraftSkillExecutor
EmailSendSkillExecutor
MessageSkillExecutor
CalendarSkillExecutor
HttpWebhookSkillExecutor
PromptSkillExecutor
NoopSkillExecutor
```

执行器设计原则：

- 每类 Skill 独立控制参数校验。
- 每类 Skill 独立控制风险等级。
- 高风险写操作必须确认。
- HTTP Skill 必须有 URL 白名单、method 白名单、超时、响应长度限制和敏感字段脱敏。
- Email Send 与 Email Draft 后续应拆开。
- Message / Reply 后续应走 ChannelMessageExecutor。

## 12. Channel 抽象设计

Channel 层用于接入飞书、钉钉、QQ、企业微信、Web 等入口。

核心模型：

```text
ChannelPlatform
ChannelIncomingMessage
ChannelOutgoingMessage
ChannelAdapter
ChannelAdapterRegistry
ChannelTaskFacade
ChannelMessageExecutor
ChannelEventLog
```

Channel 层职责：

```text
1. 解析平台事件。
2. 校验平台 token / 签名。
3. 记录事件日志。
4. 做事件幂等。
5. 转换为统一 ChannelIncomingMessage。
6. 调用 Task Center 生成或操作任务。
7. 把任务中心结果发送回渠道。
```

Channel 层禁止：

```text
1. 绕过 TaskPlan 直接执行 Skill。
2. 自己管理任务状态。
3. 自己实现任务重试。
4. 自己持有核心任务状态机。
```

飞书只是第一个 Channel Adapter，不应该污染任务中心核心模型。

## 13. 语义解析抽象设计

语义解析层只负责把自然语言转换成任务草稿，不负责执行。

目标接口：

```java
public interface AiSemanticParser {

    SemanticParseResult parse(SemanticParseRequest request);
}
```

实现：

```text
PythonSemanticParser
SpringAiSemanticParser
RuleFallbackSemanticParser
```

迁移原则：

- 当前 Python 服务继续保留。
- 后续如引入 Spring AI，必须双轨迁移。
- 不允许一次性删除 Python 能力。
- 所有 Parser 输出必须符合统一任务草稿 Schema。
- Parser 不允许直接执行 Skill。

## 14. 身份、权限与隔离

平台化后必须从 demo 级 `userId` 传参逐步升级。

短期：

```text
- 写操作继续要求 operatorUserId。
- 如果 TaskPlan.userId 不为空，operatorUserId 必须一致。
- 查询接口按 userId 过滤。
```

中期：

```text
- API Key 绑定 userId。
- 后端从 API Key 解析 userId。
- 请求体中的 userId 只作为兼容字段，不再可信。
```

长期：

```text
- JWT / Session。
- tenantId。
- role。
- permission。
- 操作审计。
```

权限原则：

```text
1. 用户只能查看自己的任务。
2. 用户只能操作自己的任务。
3. 渠道用户必须映射为平台 userId。
4. 外部系统必须通过 API Key / OAuth / JWT 接入。
5. 高风险操作必须强制确认。
```

## 15. 数据模型后续增强

建议逐步补充以下字段。

### 15.1 TaskPlanEntity

```text
tenantId
sourceType
sourceId
riskLevel
confirmedAt
cancelledAt
```

### 15.2 TaskAction

```text
executionAttempt
maxRetryCount
idempotencyKey
lockedBy
lockedAt
lastExecutionAt
```

### 15.3 ChannelEventLogEntity

```text
unique(platform, eventId)
retryCount
responsePayload
```

### 15.4 TaskExecutionLogEntity

```text
eventType
operatorType
fieldDiff
traceId
```

## 16. 推荐改造顺序

不要一次性大重构。推荐顺序如下：

```text
阶段 1：补平台设计文档
- 新增 TASK_CENTER_PLATFORM_DESIGN.md
- 明确任务中心平台定位

阶段 2：抽状态机
- 新增 TaskStateMachineService
- 收敛 canEdit / canConfirm / canCancel / canRetry
- 收敛 resolvePlanStatus

阶段 3：拆 AiTaskService
- TaskPreviewService
- TaskCommandService
- TaskQueryService
- TaskDispatchService
- AiTaskService 暂时保留为兼容门面

阶段 4：新增 Task Center API
- /api/task-center/**
- /api/ai-task/** 保持兼容

阶段 5：增强调度可靠性
- findDueScheduledTasks
- 分页扫描
- 锁
- 幂等
- retry attempt

阶段 6：拆 Skill Executor
- ReminderSkillExecutor
- TodoSkillExecutor
- EmailSkillExecutor
- MessageSkillExecutor
- HttpWebhookSkillExecutor

阶段 7：完善身份和权限
- API Key 绑定 userId
- tenantId
- JWT / Session
```

## 17. 阶段 1.5：任务中心抽象验收标准

本阶段完成后，至少满足：

```text
1. 有独立任务中心平台设计文档。
2. TaskStateMachineService 承担核心状态校验和计划状态计算。
3. AiTaskService 不再直接散落大量状态判断。
4. preview / edit / confirm / cancel / retry / dispatch 行为不退化。
5. 飞书入口仍能正常生成任务计划。
6. 提醒任务仍能进入调度。
7. 已确认任务不允许编辑。
8. 已执行任务不允许整体取消。
9. 非 FAILED 动作不允许重试。
10. 所有状态变化继续写入执行日志。
```

## 18. 编码约束

后续改造必须遵守：

```text
1. 不绕过 TaskPlan / TaskAction 执行任务。
2. 不让 Channel 层持有任务状态机。
3. 不让 Skill 层反向依赖具体入口。
4. 不让 Parser 直接执行 Skill。
5. 不把外部平台变成核心状态存储。
6. 高风险操作必须确认。
7. 调度任务必须持久化。
8. 状态变化必须记录业务日志。
9. 不提交真实密钥、token、邮箱授权码。
10. 每次大改先更新设计文档或阶段文档。
```

## 19. 近期最小落地任务

从当前代码状态出发，下一步最小落地任务建议为：

```text
P0：新增 TaskStateMachineService
P0：把 AiTaskService 中状态校验迁移进去
P1：把 resolvePlanStatus 迁移进去
P1：补 edit / cancel / retry 的状态机单元测试
P2：新增 TaskCommandService，但暂时由 AiTaskService 委托
P2：新增 /api/task-center/** 兼容 API
P3：调度扫描从 findAll 改为 findDueScheduledPlans
```

优先级原则：

```text
先抽核心，再扩入口。
先稳状态机，再做更多 Skill。
先完善任务中心，再继续接更多平台。
```
