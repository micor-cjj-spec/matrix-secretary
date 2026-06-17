# Dispatch RUNNING 超时恢复说明

本次改造为 `ai_task_dispatch_record` 增加 RUNNING 超时恢复能力，避免调度执行过程中服务崩溃导致记录永久停留在 RUNNING。

## 背景

当前调度执行链路中：

```text
tryStart -> RUNNING
execute action
markSucceeded / markFailed
```

如果 JVM 在 `tryStart` 成功后、`markSucceeded/markFailed` 前崩溃，记录会一直停留在 `RUNNING`，同一个 `idempotency_key` 后续会因为唯一键无法重新 `tryStart`。

## 恢复策略

调度器每个 tick 执行前先运行恢复逻辑：

```text
now - dispatch-running-timeout-seconds = timeoutBefore
find RUNNING records where started_at <= timeoutBefore
mark as FAILED with timeout message
then scan due actions
```

## Repository 方法

```java
findTimedOutRunningRecords(timeoutBefore, batchSize)
markTimedOutAsFailed(id, errorMessage)
markTimedOutRunningRecordsAsFailed(timeoutBefore, batchSize, errorMessage)
```

## Service 方法

```java
recoverTimedOutDispatchRecords(runningTimeoutSeconds, recoveryBatchSize)
```

## 配置项

```yaml
ai-secretary:
  local-scheduler:
    dispatch-running-timeout-seconds: 300
    dispatch-recovery-batch-size: 50
```

环境变量：

```bash
AI_SECRETARY_DISPATCH_RUNNING_TIMEOUT_SECONDS=300
AI_SECRETARY_DISPATCH_RECOVERY_BATCH_SIZE=50
```

## 当前边界

1. 超时记录会被标记为 `FAILED`，不会立即自动重试同一个 trigger。
2. 后续需要结合 FAILED 退避重试策略，决定是否允许重新执行。
3. 恢复逻辑复用本地调度器 tick，没有单独的恢复线程。
4. 当前每个 tick 最多恢复 `dispatch-recovery-batch-size` 条，避免一次性更新过多历史记录。

## 下一步

继续生产化建议：

1. 增加 FAILED 退避重试策略。
2. 增加最大重试次数。
3. 增加调度恢复指标，例如 recovered count。
4. 暴露 dispatch record 查询接口，方便在任务详情页查看 RUNNING/SUCCEEDED/FAILED 历史。
