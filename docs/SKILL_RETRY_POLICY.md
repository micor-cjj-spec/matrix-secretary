# Skill Retry Policy 配置说明

本文档记录 Skill 级别重试策略配置。

## 1. 背景

任务中心已经支持失败退避、FAILED 自动重试和 RUNNING 执行记录超时恢复。为了避免所有技能使用同一套重试参数，现在 `skill.yml` 可以配置 `retry` 节点。

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
runningTimeoutMinutes  RUNNING 执行记录超时时间
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
5. TaskActionExecutionRepository 使用 runningTimeoutMinutes 判断 RUNNING 执行记录是否超时。
```

### 4.1 单条幂等占位恢复

`tryBeginExecution(...)` 遇到同一个 `idempotencyKey` 已存在 RUNNING 记录时，会使用当前 skill 的：

```text
runningTimeoutMinutes
```

判断是否超时。未超时则返回 RUNNING，超时则先恢复为 FAILED，再尝试重新标记 RUNNING。

### 4.2 批量 RUNNING 恢复

`recoverStaleRunningExecutions(now)` 会批量扫描 RUNNING 执行记录，并根据执行记录中的：

```text
skillName
```

查找对应 skill 的 `runningTimeoutMinutes`。如果找不到 skill，则使用默认策略。

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
1. 增加 retryableErrorCodes，只对可重试错误自动重试。
2. 增加 nonRetryableErrorCodes，直接进入 FAILED_FINAL。
3. 在执行记录中保存实际使用的 retry policy 快照。
4. 对连续 RUNNING 超时的 action 进入 NEEDS_MANUAL_REVIEW。
```
