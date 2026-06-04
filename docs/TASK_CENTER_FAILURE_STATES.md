# Task Center 失败状态分类说明

本文档记录任务中心当前失败状态的分类和自动重试边界。

## 1. 新增状态

`TaskStatus` 当前新增两个非普通失败状态：

```text
FAILED_FINAL
NEEDS_MANUAL_REVIEW
```

含义：

```text
FAILED_FINAL          不可重试失败，通常是系统配置、执行类型不支持等问题。
NEEDS_MANUAL_REVIEW   需要人工介入，通常是参数缺失、用户身份缺失、权限或业务信息不完整。
```

## 2. 与 FAILED 的区别

```text
FAILED                可重试失败，会进入退避和自动重试链路。
FAILED_FINAL          终态失败，不进入自动重试。
NEEDS_MANUAL_REVIEW   人工介入状态，不进入自动重试。
```

当前自动重试方法：

```java
TaskPlanRepository.rescheduleRetryableFailedPlans(...)
```

只筛选：

```text
status = FAILED
```

因此 `FAILED_FINAL` 和 `NEEDS_MANUAL_REVIEW` 会自然排除在自动重试之外。

## 3. 状态机规则

`TaskStateMachineService.resolvePlanStatus(...)` 当前优先级：

```text
1. 全部 CANCELLED -> CANCELLED
2. 任意 NEEDS_MANUAL_REVIEW -> NEEDS_MANUAL_REVIEW
3. 任意 SCHEDULED -> SCHEDULED
4. 任意 FAILED -> FAILED
5. 任意 FAILED_FINAL -> FAILED_FINAL
6. 全部 EXECUTED -> EXECUTED
7. 其他 -> CONFIRMED
```

这样可以保证：

```text
1. 有人工介入动作时，计划整体优先显示 NEEDS_MANUAL_REVIEW。
2. 仍有可调度动作时，计划保持 SCHEDULED。
3. 普通失败优先于最终失败，便于先处理可自动恢复问题。
```

## 4. 执行器分类规则

`GenericSkillExecutor` 当前分类：

### 4.1 NEEDS_MANUAL_REVIEW

以下问题进入人工介入：

```text
1. SkillArgumentValidator 参数校验失败。
2. 创建站内通知缺少 userId。
3. 创建邮件草稿缺少 userId。
4. 渠道消息参数错误 IllegalArgumentException。
5. HTTP Skill 参数错误 IllegalArgumentException。
```

这些问题通常不会因为等待几分钟自动恢复，需要用户、开发者或上游解析修正。

### 4.2 FAILED_FINAL

以下问题进入最终失败：

```text
1. 不支持的 Skill execution.type。
2. 不支持的 builtin executor。
3. HTTP Skill 缺少 url。
```

这些属于配置或代码能力问题，不应自动重试。

### 4.3 FAILED

以下问题仍进入普通失败并参与退避重试：

```text
1. 渠道消息发送异常。
2. 邮件沙箱发送失败。
3. HTTP 外部调用异常。
```

这些问题可能是网络、外部系统、临时服务异常，适合退避重试。

## 5. 后续建议

后续可以进一步引入：

```text
1. retryableErrorCodes：只对明确可重试错误自动重试。
2. nonRetryableErrorCodes：直接进入 FAILED_FINAL 或 NEEDS_MANUAL_REVIEW。
3. lastErrorCode / lastErrorType：替代纯文本错误判断。
4. 人工处理入口：支持把 NEEDS_MANUAL_REVIEW 修复后重新置为 SCHEDULED。
5. 审计日志：记录为什么从 FAILED 变成 FAILED_FINAL 或 NEEDS_MANUAL_REVIEW。
```

## 6. 验证建议

参数缺失类验证：

```text
构造一个缺少必填 args 的 action，确认状态变为 NEEDS_MANUAL_REVIEW。
```

配置错误类验证：

```text
构造一个 execution.type 不支持的 skill，确认状态变为 FAILED_FINAL。
```

外部系统异常验证：

```text
构造一个 HTTP 调用失败的 skill，确认状态仍为 FAILED，并生成 nextRetryAtEpochMs。
```
