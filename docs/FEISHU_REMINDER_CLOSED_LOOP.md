# 飞书提醒闭环接入说明

本文档记录 `matrix-secretary` 当前 dev 分支中飞书提醒闭环的目标、现状、配置方式、验证步骤和后续改造计划。

当前文档只覆盖“飞书文本消息 -> AI 秘书任务 -> 一次性调度 -> 到期回发提醒”的最小闭环，不覆盖复杂审批、卡片消息、群机器人权限治理、多租户鉴权等生产化能力。

## 1. 闭环目标

本阶段要验证的用户场景是：

```text
用户在飞书里发送：1分钟后提醒我喝水
  -> 飞书事件回调到 Java 服务
  -> Java 解析飞书事件并抽取文本消息
  -> Java 调用 Python 语义服务生成 TaskPlan / TaskAction
  -> Java 注入飞书会话上下文
  -> 低风险提醒任务自动确认
  -> Java 调度器等待到期
  -> 到期后执行 reminder Skill
  -> reminder 识别来源为飞书
  -> 通过 ChannelMessageExecutor 回发提醒
```

预期最终效果：飞书里能收到提醒消息，例如“该喝水了”。

当前代码先完成 mock-send 闭环：到期后不会真实调用飞书发送接口，而是在 Java 控制台输出 mock 发送日志。

## 2. 当前代码改造点

### 2.1 飞书配置类

新增：

```text
java-service/src/main/java/com/kailei/demo/channel/feishu/FeishuProperties.java
```

用于读取以下配置：

| 配置项 | 说明 |
|---|---|
| `FEISHU_ENABLED` | 是否启用真实飞书发送，当前真实发送尚未完成 |
| `FEISHU_CLIENT_ID` | 飞书应用 App ID，真实发送阶段使用 |
| `FEISHU_CLIENT_SECRET` | 飞书应用 App Secret，真实发送阶段使用 |
| `FEISHU_VERIFICATION_TOKEN` | 飞书事件订阅 Verification Token |
| `FEISHU_ENCRYPT_KEY` | 飞书事件加密 Key，当前不建议开启 |
| `FEISHU_BASE_URL` | 飞书开放平台地址，默认 `https://open.feishu.cn` |
| `FEISHU_MOCK_SEND_ENABLED` | 是否启用 mock-send，用于本地闭环测试 |

### 2.2 飞书渠道适配器

新增：

```text
java-service/src/main/java/com/kailei/demo/channel/feishu/FeishuChannelAdapter.java
```

当前职责：

1. 实现 `ChannelAdapter`。
2. 注册平台类型为 `ChannelPlatform.FEISHU`。
3. 解析飞书文本消息事件。
4. 抽取 `tenantId`、`conversationId`、`messageId`、`senderId`、`senderName`、`text`。
5. 支持 `Verification Token` 的基础校验。
6. 在 `FEISHU_MOCK_SEND_ENABLED=true` 时打印 mock 发送日志。

当前限制：

1. `sendText` 尚未真实调用飞书发送 API。
2. 暂未实现飞书事件加密解密。
3. token 校验优先服务本地测试，后续需要兼容新版事件结构中的 `header.token`。

### 2.3 飞书事件入口

新增：

```text
java-service/src/main/java/com/kailei/demo/controller/FeishuWebhookController.java
```

接口地址：

```http
POST /api/channels/feishu/events
```

职责：

1. 处理飞书 `url_verification`。
2. 校验事件 token。
3. 调用 `FeishuChannelAdapter#parseIncoming` 解析飞书文本消息。
4. 构造 `PreviewTaskRequest`：

```text
text      = 飞书消息文本
userId    = feishu:{senderId}
sessionId = feishu:{tenantId}:{conversationId}
timezone  = Asia/Shanghai
```

5. 调用 `AiTaskService.preview` 创建任务计划。
6. 通过 `AiTaskService.editAction` 给每个 `TaskAction` 注入飞书上下文：

```json
{
  "platform": "feishu",
  "tenantId": "...",
  "conversationId": "...",
  "receiverId": "...",
  "replyToMessageId": "..."
}
```

7. 对低风险提醒类任务自动确认。

### 2.4 reminder 执行路由调整

修改：

```text
java-service/src/main/java/com/kailei/demo/skill/GenericSkillExecutor.java
```

原逻辑：

```text
reminder -> 创建站内提醒 NotificationEntity
```

新逻辑：

```text
如果 userId 以 feishu: 开头，或 action.args.platform = feishu
  -> 走 ChannelMessageExecutor 回发渠道消息
否则
  -> 保持原站内提醒逻辑
```

这样可以避免破坏原有 Web / 站内提醒流程。

## 3. 本地启动配置

### 3.1 启动 Python 语义服务

```powershell
cd D:\workspace\demo\python-service
.\.venv\Scripts\python.exe -m uvicorn app.main:app --host 127.0.0.1 --port 10001
```

Python 服务地址：

```text
http://127.0.0.1:10001/api/v1/semantic/parse
```

### 3.2 启动 Java 服务

第一轮建议先启用 mock-send，不启用真实飞书发送：

```powershell
$env:PYTHON_SEMANTIC_URL="http://127.0.0.1:10001/api/v1/semantic/parse"
$env:FEISHU_MOCK_SEND_ENABLED="true"
$env:FEISHU_VERIFICATION_TOKEN=""
$env:FEISHU_ENCRYPT_KEY=""
```

如果使用本地 MySQL：

```powershell
$env:MYSQL_URL="jdbc:mysql://127.0.0.1:3306/ai_secretary_demo?createDatabaseIfNotExist=true&useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true"
$env:MYSQL_USERNAME="ai_demo"
$env:MYSQL_PASSWORD="ai_demo_123"
```

启动 Java：

```powershell
cd D:\workspace\demo\java-service
mvn spring-boot:run
```

Java 服务端口：

```text
http://127.0.0.1:10002
```

### 3.3 ngrok 映射

ngrok 需要映射 Java 服务端口 `10002`，不要映射 Python 的 `10001`。

```powershell
ngrok http 10002
```

如果 ngrok 分配的公网地址是：

```text
https://float-manlike-jawless.ngrok-free.dev
```

则飞书事件订阅请求地址应配置为：

```text
https://float-manlike-jawless.ngrok-free.dev/api/channels/feishu/events
```

不要配置为：

```text
https://float-manlike-jawless.ngrok-free.dev/api/feishu/events
```

除非你把 `FeishuWebhookController` 上的 `@RequestMapping` 从 `/api/channels/feishu` 改成 `/api/feishu`。

## 4. 飞书开放平台配置

进入飞书开放平台企业自建应用后台，完成以下配置。

### 4.1 开启机器人能力

在应用能力中开启机器人，使用户可以在飞书里给机器人发送消息。

### 4.2 事件订阅

事件订阅请求地址填写：

```text
https://你的公网域名/api/channels/feishu/events
```

本地测试示例：

```text
https://float-manlike-jawless.ngrok-free.dev/api/channels/feishu/events
```

### 4.3 Verification Token

第一轮 mock 测试建议先不配置 Java 侧 token：

```powershell
$env:FEISHU_VERIFICATION_TOKEN=""
```

这样可以先确认事件能进入 Java 服务。

等 mock 闭环跑通后，再把飞书后台事件订阅页面中的 `Verification Token` 复制到 Java 环境变量：

```powershell
$env:FEISHU_VERIFICATION_TOKEN="飞书后台复制出来的 Verification Token"
```

注意：当前代码主要读取事件体顶层 `token`。如果飞书新版事件结构把 token 放在 `header.token`，需要后续补兼容。

### 4.4 Encrypt Key

第一轮不要开启事件加密：

```powershell
$env:FEISHU_ENCRYPT_KEY=""
```

当前代码尚未实现飞书事件解密。开启加密后，Java 收到的事件体会变成加密载荷，现有解析逻辑无法直接处理。

## 5. 验证步骤

### 5.1 验证飞书 URL Challenge

在飞书事件订阅页面保存请求地址时，飞书会发送 `url_verification` 请求。

Java 接口收到后应返回：

```json
{
  "challenge": "飞书传来的 challenge 值"
}
```

如果验证失败，优先检查：

1. ngrok 是否仍在线。
2. ngrok 是否映射到 `10002`。
3. 请求地址是否是 `/api/channels/feishu/events`。
4. Java 是否已启动。
5. 本地是否有防火墙或代理拦截。

### 5.2 验证消息进入 Java

在飞书里给机器人发送：

```text
1分钟后提醒我喝水
```

Java 控制台应看到类似日志：

```text
Receive Feishu message: sender=..., conversation=..., text=1分钟后提醒我喝水
```

如果没有日志，优先检查：

1. 飞书事件是否订阅了接收消息事件。
2. 机器人是否被添加到会话。
3. 请求地址是否填错。
4. ngrok 控制台是否收到请求。
5. Java Controller 是否被命中。

### 5.3 验证任务自动确认并进入调度

Java 控制台应看到类似日志：

```text
Schedule action [...] 已进入调度队列: cron=..., nextRunAt=...
```

或者在数据库 / 任务查询接口中看到 `TaskPlan.status = SCHEDULED`。

### 5.4 验证到期执行

默认本地调度器会扫描到期任务。到期后，如果 `FEISHU_MOCK_SEND_ENABLED=true`，Java 控制台应看到：

```text
Mock send Feishu text: conversationId=..., receiverId=..., content=...
```

这说明飞书闭环的任务链路已经跑通。

## 6. 常见问题

### 6.1 飞书后台请求地址应该填哪个

当前代码对应：

```text
/api/channels/feishu/events
```

所以公网完整地址应为：

```text
https://你的公网域名/api/channels/feishu/events
```

### 6.2 为什么不是 `/api/feishu/events`

因为当前 Controller 定义的是：

```java
@RequestMapping("/api/channels/feishu")
```

如果希望使用 `/api/feishu/events`，需要改成：

```java
@RequestMapping("/api/feishu")
```

### 6.3 为什么先不要配置 Verification Token

为了先排除事件结构差异带来的干扰。

当前代码优先读取顶层 `token`。飞书新版事件可能使用 `header.token`。如果你提前配置 `FEISHU_VERIFICATION_TOKEN`，可能出现 token 校验失败，但这并不代表事件订阅或 ngrok 有问题。

### 6.4 为什么先不要开启 Encrypt Key

因为当前 Java 还没有实现飞书事件解密。开启后，事件内容无法直接解析成消息文本。

### 6.5 为什么到期后飞书没有真的收到消息

当前版本 `FeishuChannelAdapter#sendText` 仍是 mock-send，没有真实调用飞书发送消息接口。

先看 Java 控制台是否有：

```text
Mock send Feishu text: ...
```

有这条日志说明任务闭环已经通了，只差真实飞书发送 API。

### 6.6 为什么提醒内容可能不是“该喝水了”

当前 Python fallback 对“1分钟后提醒我喝水”这类句子的内容抽取可能会有截断或为空的情况。后续建议在 Python 规则解析器或 Java 侧补提醒内容归一化，保证“喝水”类提醒稳定转成“该喝水了”。

## 7. 下一步真实发送改造

mock-send 跑通后，下一步需要把 `FeishuChannelAdapter#sendText` 替换为真实发送。

建议改造顺序：

1. 增加 `tenant_access_token` 获取与缓存。
2. 根据 `conversationId` 使用 `chat_id` 作为 `receive_id`。
3. 调用飞书消息发送接口发送文本消息。
4. 处理飞书 API 错误码和异常日志。
5. 将发送请求、响应、错误写入 `ai_task_execution_log` 或渠道发送日志。
6. 支持 `FEISHU_ENABLED=true` 且 `FEISHU_MOCK_SEND_ENABLED=false` 的真实发送模式。
7. 兼容 `header.token`。
8. 最后再考虑事件加密解密。

真实发送阶段需要配置：

```powershell
$env:FEISHU_ENABLED="true"
$env:FEISHU_MOCK_SEND_ENABLED="false"
$env:FEISHU_CLIENT_ID="你的飞书 App ID"
$env:FEISHU_CLIENT_SECRET="你的飞书 App Secret"
```

## 8. 当前验收标准

当前 mock-send 闭环验收标准：

1. 飞书 URL Challenge 可以通过。
2. 飞书发送文本后，Java 可以打印接收消息日志。
3. Java 可以创建 `TaskPlan`。
4. 低风险 `reminder` 可以自动确认。
5. `reminder` 可以进入 `SCHEDULED` 状态。
6. 到期后 Java 调度器可以执行该动作。
7. 执行时可以打印 `Mock send Feishu text` 日志。
8. Web / 普通用户原有站内提醒逻辑不受影响。

真实发送阶段验收标准另行补充。
