# 调度执行幂等接入说明

本次改造将 `ai_task_dispatch_record` 接入本地调度执行入口。

## 执行流程

当前调度执行顺序：

```text
find due action candidates
  -> tryAcquireDispatchLease(actionId, now, leaseUntil, owner)
  -> build idempotencyKey(planId, actionId, triggerAt)
  -> hasSucceeded(idempotencyKey) = true: skip
  -> tryStart(planId, actionId, triggerAt, owner)
  -> tryStart = false: skip
  -> load plan and execute matched action
  -> status = FAILED: markFailed(idempotencyKey, note)
  -> otherwise: markSucceeded(idempotencyKey)
```

## 幂等键

调度触发场景使用：

```text
planId:actionId:triggerAt
```

其中 `triggerAt` 来自 `TaskActionEntity.nextRunAt`。

## 状态流转

```text
RUNNING -> SUCCEEDED
RUNNING -> FAILED
```

## 接入位置

幂等记录接入在 `AiTaskService` 的本地调度入口：

```java
private void dispatchDueAction(TaskActionEntity dueAction,
                               OffsetDateTime now,
                               long leaseSeconds,
                               String owner)
```

这样只影响调度触发，不影响用户手动重试、立即执行等操作。

## 当前边界

本次接入仍有几个边界：

1. `idempotency_key` 当前还不是数据库唯一约束，并发下仍依赖 dispatch lease 降低重复概率。
2. `tryStart(...)` 当前遇到任何已有记录都会返回 false，因此同一个 trigger 的 FAILED 记录不会自动再次执行。
3. 如果执行过程中 JVM 崩溃，记录可能停留在 RUNNING，需要后续补超时恢复策略。
4. 目前只接入本地调度入口，其他直接执行入口没有使用 dispatch record。

## 下一步

继续生产化建议：

1. 给 `idempotency_key` 增加唯一约束。
2. 将 `tryStart(...)` 改为基于唯一键冲突的原子插入。
3. 增加 RUNNING 超时恢复。
4. 给 FAILED 增加退避重试策略。
5. 对邮件、飞书、HTTP 回调等外部副作用类 skill 进一步做业务幂等。
