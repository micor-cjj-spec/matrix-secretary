# 阶段二：多平台渠道接入

## 1. 阶段定位

阶段二的目标不是把 `matrix-secretary` 改造成某个平台的聊天机器人，而是在现有任务中心外侧增加统一 Channel Adapter 层。飞书作为第一个真实渠道接入，后续钉钉、QQ、企业微信等平台只新增对应 Adapter，不改任务中心主链路。

## 2. 设计目标

1. 支持飞书消息进入 AI 秘书。
2. 支持飞书内生成任务预览文本。
3. 支持用户通过文本指令确认 / 取消任务。
4. 支持消息类 Skill 通过 ChannelMessageExecutor 发送到真实渠道。
5. 抽象统一 Channel 模型，为钉钉、QQ、企业微信预留扩展点。
6. 事件接入必须可审计、可幂等，避免平台重试造成重复任务。

## 3. 阶段边界

### 3.1 本阶段要做

- `ChannelPlatform`
- `ChannelIncomingMessage`
- `ChannelOutgoingMessage`
- `ChannelAdapter`
- `ChannelAdapterRegistry`
- `ChannelTaskFacade`
- `ChannelMessageExecutor`
- `ChannelEventLogEntity`
- `FeishuProperties`
- `FeishuTokenService`
- `FeishuMessageClient`
- `FeishuChannelAdapter`
- `FeishuEventController`

### 3.2 本阶段暂不做

- 完整登录体系 / RBAC。
- 联系人别名系统，例如“王总”“李雷”自动映射平台用户。
- 飞书卡片按钮编辑。
- 飞书日历、审批、文档等深度 API。
- 钉钉、QQ 的真实接入。
- 多租户后台管理。

这些内容后续分阶段推进。

## 4. 总体架构

```text
飞书 / 钉钉 / QQ / 企业微信
  -> Channel Adapter
  -> ChannelIncomingMessage
  -> ChannelTaskFacade
  -> AiTaskService.preview()
  -> Python 语义解析
  -> TaskPlan / TaskAction
  -> 任务预览返回渠道
  -> 用户确认 / 取消
  -> AiTaskService.confirm()
  -> 调度或立即执行
  -> ChannelMessageExecutor
  -> ChannelOutgoingMessage
  -> 平台消息发送
```

## 5. 核心模型设计

### 5.1 ChannelPlatform

```text
FEISHU
DINGTALK
QQ
WEB
```

用于统一标识消息来源和出站渠道。

### 5.2 ChannelIncomingMessage

统一入站消息模型：

```text
platform
tenantId
conversationId
messageId
senderId
senderName
text
mentionedBot
raw
```

其中：

- `secretaryUserId()` 生成内部 userId，例如 `feishu:ou_xxx`。
- `secretarySessionId()` 生成内部 sessionId，例如 `feishu:tenant:chat`。

### 5.3 ChannelOutgoingMessage

统一出站消息模型：

```text
platform
tenantId
conversationId
receiverId
replyToMessageId
messageType
content
extra
```

## 6. 飞书接入设计

### 6.1 事件入口

```http
POST /api/feishu/events
```

职责：

1. 处理飞书 URL challenge。
2. 校验 verification token。
3. 记录事件日志。
4. 通过 `FeishuChannelAdapter` 解析文本消息。
5. 交给 `ChannelTaskFacade` 进入任务中心。

### 6.2 飞书 token

`FeishuTokenService` 负责获取和缓存 `tenant_access_token`，过期前提前刷新。

### 6.3 飞书消息发送

`FeishuMessageClient` 负责向飞书发送文本消息。

第一版支持：

- 按 `chat_id` 发送。
- 按 `open_id` 发送。

## 7. 与任务中心的关系

飞书消息最终转换为：

```java
new PreviewTaskRequest(text, "Asia/Shanghai", userId, sessionId)
```

后续仍走现有主链路：

```text
preview -> edit -> confirm -> schedule/execute -> execution log
```

Channel 层不得绕开 `AiTaskService` 直接执行任务。

## 8. 确认方式

第一版使用文本确认：

```text
确认 plan-xxxx
取消 plan-xxxx
```

后续可以升级为飞书交互卡片：

```text
[确认执行] [取消] [编辑]
```

## 9. 事件幂等与审计

新增表：

```text
ai_channel_event_log
```

用于记录：

- platform
- tenantId
- eventId
- messageId
- conversationId
- senderId
- status
- rawPayload
- errorMessage
- createdAt
- processedAt

平台重试时，通过 `platform + eventId` 判断是否重复。

## 10. 配置项

```yaml
feishu:
  enabled: ${FEISHU_ENABLED:false}
  client-id: ${FEISHU_CLIENT_ID:}
  client-secret: ${FEISHU_CLIENT_SECRET:}
  verification-token: ${FEISHU_VERIFICATION_TOKEN:}
  encrypt-key: ${FEISHU_ENCRYPT_KEY:}
  base-url: ${FEISHU_BASE_URL:https://open.feishu.cn}
```

注意：

- 不要把真实凭据提交到仓库。
- 本地测试通过环境变量注入。
- 若启用飞书加密事件，后续需要补充解密逻辑。

## 11. 验收标准

阶段二第一版至少跑通：

```text
飞书私聊 / 群聊 @机器人
  -> 用户输入自然语言
  -> /api/feishu/events 收到事件
  -> 生成 TaskPlan
  -> 机器人返回任务预览文本
  -> 用户回复 确认 plan-xxxx
  -> 任务进入确认 / 调度 / 执行
  -> 执行日志可查
```

## 12. 后续计划

1. 飞书卡片按钮确认。
2. 飞书卡片编辑任务内容、目标、时间。
3. 联系人系统。
4. 群聊权限控制。
5. 钉钉 ChannelAdapter。
6. QQ ChannelAdapter。
7. 企业微信 ChannelAdapter。
