# Task Center 失败退避说明

本文档记录任务中心当前失败重试退避字段和调度过滤规则。

## 1. 当前定位

当前实现是保守版本：

```text
失败退避字段已经持久化，调度查询和抢锁会尊重 nextRetryAtEpochMs。
```

但当前尚未把 `FAILED` action 自动重新调度为 `SCHEDULED`。

也就是说：

```text
1. 当前不会因为失败自动高频反复执行。
2. 后续如果启用 FAILED 自动重试，已有字段可以直接承接退避策略。
```

## 2. 新增字段

`ai_task_action` 新增：

```text
nextRetryAtEpochMs
retryBackoffSeconds
lastErrorMessage
```

含义：

```text
nextRetryAtEpochMs   下一次允许重试的时间，epoch milliseconds
retryBackoffSeconds  当前退避秒数
lastErrorMessage     最近一次失败错误信息
```

## 3. 退避策略

默认值：

```java
DEFAULT_RETRY_BACKOFF_SECONDS = 60;
MAX_RETRY_BACKOFF_SECONDS = 30 * 60;
```

规则：

```text
1. action 第一次失败：退避 60 秒。
2. 第二次失败：退避 120 秒。
3. 第三次失败：退避 240 秒。
4. 后续继续翻倍，但最大不超过 30 分钟。
5. action 执行成功后，nextRetryAtEpochMs 清空，retryBackoffSeconds 重置为默认值。
```

## 4. 调度过滤规则

`TaskPlanRepository.baseDueActionQuery(nowEpochMs)` 当前会过滤：

```text
nextRetryAtEpochMs is null
or nextRetryAtEpochMs <= nowEpochMs
```

`tryLockAction(...)` 抢锁时也会再次校验同样条件。

作用：

```text
即使后续某个失败 action 被重新标记为 SCHEDULED，只要还没到 nextRetryAtEpochMs，就不会被扫描或抢锁执行。
```

## 5. 索引

`TaskCenterSchemaInitializer` 会启动时尝试创建：

```sql
alter table ai_task_action
add index idx_task_action_status_retry_next_run
(status, next_retry_at_epoch_ms, next_run_at_epoch_ms);
```

用途：

```text
支撑按状态、重试时间、到期时间扫描可执行 action。
```

## 6. 当前限制

```text
1. FAILED action 尚未自动转回 SCHEDULED。
2. 还没有分 skill 的退避策略。
3. lastErrorMessage 只保存最近一次错误，不保存完整错误历史。
4. 当前执行失败计数仍基于 TaskPlanRepository.save(plan) 的状态变化。
```

## 7. 后续建议

后续可以增加：

```text
1. failed action retry job：扫描 FAILED 且 nextRetryAtEpochMs 到期的 action，重新转为 SCHEDULED。
2. skill-level retry policy：不同 skill 配置不同 maxRetryCount、初始退避和最大退避。
3. lastErrorCode / lastErrorType：区分参数错误、外部系统超时、权限错误。
4. permanent failure：不可重试错误直接进入 FAILED_FINAL 或 NEEDS_MANUAL_REVIEW。
```

## 8. 验证建议

模拟失败 action 后检查：

```sql
select action_id,
       status,
       execution_attempt,
       max_retry_count,
       next_retry_at_epoch_ms,
       retry_backoff_seconds,
       last_error_message
from ai_task_action
where status = 'FAILED'
order by action_id desc
limit 20;
```

确认索引：

```sql
show index from ai_task_action;
```

重点查看：

```text
idx_task_action_status_retry_next_run
```
