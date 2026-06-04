# Skill Retry Policy 配置说明

本文档记录 Skill 级别重试策略配置。

## 1. 背景

任务中心已经支持失败退避和 FAILED 自动重试。为了避免所有技能使用同一套重试参数，现在 `skill.yml` 可以配置 `retry` 节点。

模型位置：

```text
java-service/src/main/java/com/kailei/demo/skill/SkillRetryPolicy.java
```

Skill 定义：

```text
java-service/src/main/java/com/kailei/demo/skill/SkillDefinition.java
```

加载逻辑：

```text
java-service/src/main/java/com/kailei/demo/skill/SkillCatalog.java
```

## 2. 配置示例

在任意 `skills/*/skill.yml` 中增加：

```yaml
retry:
  maxRetryCount: 3
  initialBackoffSeconds: 60
  maxBackoffSeconds: 1800
  runningTimeoutMinutes: 10
```

字段含义：

```text
maxRetryCount          最大失败重试次数
initialBackoffSeconds  第一次失败后的退避秒数
maxBackoffSeconds      最大退避秒数
runningTimeoutMinutes  RUNNING 执行记录超时时间，预留给后续分 skill 超时恢复使用
```

## 3. 默认值

如果 `skill.yml` 不配置 `retry`，默认值为：

```text
maxRetryCount = 3
initialBackoffSeconds = 60
maxBackoffSeconds = 1800
runningTimeoutMinutes = 10
```

如果配置值为空、负数或 0，会自动回退默认值。

如果 `maxBackoffSeconds < initialBackoffSeconds`，会自动把 `maxBackoffSeconds` 调整为 `initialBackoffSeconds`。

## 4. 当前落地范围

当前已经落地：

```text
1. SkillCatalog 从 skill.yml 读取 retry 配置。
2. SkillDefinition 持有 SkillRetryPolicy。
3. SkillCatalog.publicViews() 输出 retry 配置，方便前端查看。
4. TaskPlanRepository 保存 action 时使用对应 skill 的 retry policy：
   - maxRetryCount
   - initialBackoffSeconds
   - maxBackoffSeconds
```

当前暂未完全落地：

```text
runningTimeoutMinutes 目前已经进入模型，但 TaskActionExecutionRepository 的 RUNNING 超时恢复仍使用全局 10 分钟。
```

## 5. 推荐配置

### 5.1 本地提醒类 skill

```yaml
retry:
  maxRetryCount: 2
  initialBackoffSeconds: 30
  maxBackoffSeconds: 300
  runningTimeoutMinutes: 5
```

说明：

```text
本地提醒失败通常恢复快，重试次数可以少，退避时间可以短。
```

### 5.2 外部 HTTP / Webhook skill

```yaml
retry:
  maxRetryCount: 5
  initialBackoffSeconds: 60
  maxBackoffSeconds: 1800
  runningTimeoutMinutes: 10
```

说明：

```text
外部服务可能短暂不可用，可允许更多重试。
```

### 5.3 高风险操作 skill

```yaml
retry:
  maxRetryCount: 1
  initialBackoffSeconds: 300
  maxBackoffSeconds: 300
  runningTimeoutMinutes: 10
```

说明：

```text
涉及发送消息、下单、转账、删除等高风险操作时，不建议多次自动重试。
```

## 6. 后续建议

后续可以继续完善：

```text
1. TaskActionExecutionRepository 按 skill.runningTimeoutMinutes 恢复 RUNNING 记录。
2. 增加 retryableErrorCodes，只对可重试错误自动重试。
3. 增加 nonRetryableErrorCodes，直接进入 FAILED_FINAL。
4. 在执行记录中保存实际使用的 retry policy 快照。
```
