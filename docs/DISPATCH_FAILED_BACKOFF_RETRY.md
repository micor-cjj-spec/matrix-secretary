# Dispatch FAILED 退避重试说明

本次改造为调度幂等记录增加 FAILED 退避重试能力，避免一次执行失败后同一个 trigger 永久阻塞。

## 新增字段

`ai_task_dispatch_record` 新增：

| 字段 | 说明 |
|---|---|
| `retry_count` | 已经发生的重试次数，初始为 0 |
| `max_retry_count` | 最大重试次数 |
| `next_retry_at` | 下一次允许重试的时间 |

## 状态流转

```text
RUNNING -> SUCCEEDED
RUNNING -> FAILED
FAILED  -> RUNNING   // retry_count < max_retry_count 且 next_retry_at <= now
```

## 启动策略

调度入口现在使用：

```java
tryStartOrRetry(planId, actionId, triggerAt, owner, maxRetryCount)
```

语义：

```text
1. 先尝试插入新的 RUNNING 记录
2. 如果 idempotency_key 唯一键冲突，则尝试重启已有 FAILED 记录
3. 只有满足以下条件才允许 FAILED -> RUNNING：
   - status = FAILED
   - COALESCE(retry_count, 0) < maxRetryCount
   - next_retry_at IS NULL OR next_retry_at <= now
```

重启时会：

```text
status = RUNNING
retry_count = COALESCE(retry_count, 0) + 1
started_at = now
finished_at = null
error_message = null
next_retry_at = null
```

## 失败策略

执行失败后：

```text
status = FAILED
finished_at = now
next_retry_at = now + dispatch-retry-backoff-seconds
error_message = error
```

RUNNING 超时恢复也会写入同样的 `next_retry_at`。

## 配置项

```yaml
ai-secretary:
  local-scheduler:
    dispatch-max-retry-count: 3
    dispatch-retry-backoff-seconds: 60
```

环境变量：

```bash
AI_SECRETARY_DISPATCH_MAX_RETRY_COUNT=3
AI_SECRETARY_DISPATCH_RETRY_BACKOFF_SECONDS=60
```

## 当前边界

1. 当前是固定退避，不是指数退避。
2. `retry_count` 表示重试次数，不包含首次执行。
3. 到达最大重试次数后，记录会保持 FAILED，不再自动拉起。
4. 当前仍只接入本地调度触发路径，不影响手动重试。

## 下一步

继续生产化建议：

1. 支持指数退避，例如 1m / 5m / 15m。
2. 增加调度记录查询接口，让前端可以展示 retry_count、next_retry_at、error_message。
3. 增加 Prometheus 指标：retry count、retry exhausted count、timeout recovered count。
4. 对邮件、飞书、HTTP 回调等外部副作用类 skill 增加业务幂等。