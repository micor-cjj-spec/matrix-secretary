# AI Secretary 架构演进与改造规则

本文档用于约束 `matrix-secretary` 后续演进方向。项目不应被改造成普通聊天机器人，而应演进为一个可确认、可调度、可审计、可扩展 Skill 的 AI 秘书任务执行系统。

## 1. 核心定位

AI 秘书的核心价值不是“会聊天”，而是：

> 将用户自然语言输入转化为可预览、可确认、可调度、可执行、可追踪、可恢复的任务计划。

系统主链路应保持为：

```text
用户自然语言
  -> AI 理解与任务拆分
  -> 任务计划预览
  -> 用户确认 / 编辑 / 取消
  -> 调度或立即执行
  -> Skill 执行
  -> 执行日志 / 审计 / 失败恢复
```

## 2. 架构边界

### 2.1 Java 服务定位

Java 服务是核心任务中台，必须沉淀以下能力：

- 任务计划模型：`TaskPlan`、`TaskAction`、`TaskSchedule`。
- 任务状态机：等待确认、已确认、已调度、已执行、失败、取消。
- 人工确认：高风险操作必须经过确认。
- 任务编排：立即执行、一次性调度、周期调度。
- Skill 执行：内置 Skill、HTTP Skill、Prompt Skill、Noop Skill。
- 审计日志：执行前后状态、请求载荷、响应载荷、错误信息、操作人。
- 后续扩展：幂等、重试、取消、权限、联系人、长期记忆。

Java 服务不得退化为一个简单的模型转发层。

### 2.2 Python 服务定位

Python 服务当前作为 AI 语义解析服务，负责：

- 自然语言任务拆分。
- 意图识别。
- 时间归一化。
- cron 表达式生成。
- LLM 调用失败时的规则 fallback。

中期可以通过统一接口逐步引入 Java 侧 AI 能力，例如 Spring AI，但迁移必须走双轨，不允许一次性删除 Python 能力。

建议抽象：

```text
AiSemanticParser
  ├── PythonSemanticParser
  ├── SpringAiSemanticParser
  └── RuleFallbackParser
```

### 2.3 Dify / n8n / MCP 等外部平台定位

这些平台只能作为辅助层，不应替代 Java 核心任务中台。

推荐用法：

- Dify：作为聊天入口、Workflow/Chatflow 原型、Prompt 调试、调用 Java API 的外部编排层。
- n8n：作为外部 SaaS 自动化连接器，通过 HTTP Skill 或 Webhook 被 Java 调用。
- MCP：作为后续统一工具生态的接入方式。

禁止用法：

- 不要让 Dify/n8n 管理核心任务状态。
- 不要让 Dify/n8n 直接承担高风险操作确认。
- 不要把执行日志、幂等、重试、权限体系外包给低代码平台。

## 3. 分层目标架构

```text
入口层
  - Web 页面
  - REST API
  - Dify Chatflow
  - 飞书 / 企业微信 / QQ / 浏览器插件

AI 理解层
  - LLM 结构化输出
  - 任务拆分
  - 意图识别
  - 风险判断
  - 时间归一化

任务计划层
  - planId / actionId
  - 任务预览
  - 用户编辑
  - 用户确认
  - 任务取消

任务编排层
  - 状态机
  - 调度
  - 幂等
  - 重试
  - 超时恢复
  - 失败人工介入

Skill 执行层
  - 邮件
  - 消息
  - 提醒
  - 待办
  - 日历
  - HTTP Webhook
  - Prompt 生成
  - MCP Tool

审计与记忆层
  - 执行日志
  - 用户偏好
  - 联系人
  - 历史任务
  - 权限
  - 安全审计
```

## 4. 改造原则

### 4.1 Plan Before Execute

任何 AI 生成的任务不得直接执行，必须先形成任务计划。

最低要求：

- 生成 `planId`。
- 生成一个或多个 `actionId`。
- 保存原始输入 `sourceText`。
- 保存 AI 解析出的 warnings。
- 每个 action 保留 `sourceSentence` 和 `analysisNote`。

### 4.2 Human In The Loop

以下操作必须确认：

- 发送邮件。
- 发送消息。
- 回复消息。
- 调用外部 HTTP 写操作。
- 删除、取消、覆盖类操作。
- 涉及合同、财务、预算、付款、审批等敏感业务。
- Skill 标记为 `requiresConfirmation=true`。
- Skill 风险等级为 `HIGH`。

提醒、待办等中低风险任务可以允许自动确认，但必须可配置。

### 4.3 一切执行必须可审计

每次执行或状态变化都必须记录：

- `planId`
- `actionId`
- `skillName`
- 执行前状态
- 执行后状态
- 请求参数
- 响应结果
- 错误信息
- 操作人
- 创建时间

日志不能只写应用日志，必须写入业务执行日志表。

### 4.4 高风险 Skill 默认保守

```text
LOW：只读查询、文本生成、草稿生成。
MEDIUM：提醒、待办、站内通知、低影响调度。
HIGH：发送邮件、发送消息、外部写接口、自动回复、业务系统写操作。
CRITICAL：付款、审批、合同提交、删除数据、权限变更。
```

`HIGH` 和 `CRITICAL` 必须要求人工确认。

### 4.5 调度任务必须可恢复

调度系统不得只依赖内存状态。每个调度任务必须持久化：

- `scheduleType`
- `cron`
- `runAt`
- `nextRunAt`
- `lastRunAt`
- `triggerCount`
- action 当前状态

后续必须补充：

- `executionAttempt`
- `maxRetryCount`
- `idempotencyKey`
- `lastExecutionAt`
- `lockedBy`
- `lockedAt`

### 4.6 HTTP Skill 必须受控

HTTP Skill 是高风险扩展点，必须逐步增加：

- URL 白名单。
- method 白名单。
- 内网地址保护。
- 请求超时。
- 重试策略。
- 敏感字段脱敏。
- 响应体长度限制。
- 写操作默认确认。

禁止让模型直接生成任意 URL 并执行。

## 5. 阶段路线图

### 阶段一：任务中心稳定化

目标：从 Demo 变成可管理的任务中心。

阶段文档：[`PHASE_1_TASK_CENTER.md`](PHASE_1_TASK_CENTER.md)

优先任务：

1. 任务预览结果可编辑。
2. 增加任务取消接口。
3. 增加 action 重试接口。
4. 增加执行日志查询接口。
5. 增加 userId 维度数据隔离。
6. 增加 API Key 或 JWT 鉴权。
7. 将硬编码配置改为环境变量。
8. 增加 OpenAPI 文档，方便外部平台接入。

当前阶段一进展：

- 已支持预览后编辑单个 `TaskAction`。
- 编辑动作限制在 `WAITING_CONFIRM` 状态，避免修改已确认、已调度、已执行任务。
- 写操作会校验 `operatorUserId` 与 `TaskPlan.userId`，保留 demo 兼容逻辑。
- 编辑、取消、重试、执行等状态变化需要记录业务执行日志。

验收标准：

- 用户可以查看任务详情。
- 用户可以查看执行日志。
- 用户可以取消未执行任务。
- 用户可以重试失败任务。
- 用户可以在确认前编辑任务动作。
- 任务查询不应跨用户泄露。

### 阶段二：真实执行器

目标：从模拟执行升级为真实办事。

优先 Skill：

1. Reminder：站内提醒 / Web 通知。
2. Email：邮件草稿 / 邮件发送。
3. Todo：本地待办。
4. Message：飞书 / 企业微信 / 钉钉消息。
5. Calendar：日历事件。
6. HTTP Webhook：外部系统接入。
