# Task Center 失败退避说明

本文档记录任务中心当前失败重试退避字段、调度过滤规则和自动重试入口。

## 1. 当前定位

当前实现已经完成失败自动重试的基础闭环：

```text
1. 失败时记录 nextRetryAtEpochMs / retryBackoffSeconds / lastErrorMessage。
2. 调度查询和抢锁尊重 nextRetryAtEpochMs。
3. 调度入口会把到期且未超过最大重试次数的 FAILED action 重新标记为 SCHEDULED。
4. 重新标记为 SCHEDULED 后，复用原有到期调度、锁、幂等、执行记录链路。
```

也就是说：

```text
失败 action 不会高频反复执行，只会在退避到期后重新进入调度队列。
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
即使某个失败 action 被重新标记为 SCHEDULED，只要还没到 nextRetryAtEpochMs，就不会被扫描或抢锁执行。
```

## 5. FAILED 自动重试入口

`TaskPlanRepository` 提供：

```java
public List<TaskPlan> rescheduleRetryableFailedPlans(OffsetDateTime now, int limit)
```

筛选条件：

```text
status = FAILED
nextRetryAtEpochMs is not null
nextRetryAtEpochMs <= nowEpochMs
executionAttempt < maxRetryCount，或者重试字段为空
```

更新动作：

```text
1. status 改为 SCHEDULED。
2. executionNote 改为“失败重试退避已到期，重新进入调度队列”。
3. 清空 lockedBy / lockedAtEpochMs。
4. 返回受影响 action 所属的 TaskPlan。
```

`TaskDispatchService.dispatchDueOnceTasks(...)` 当前顺序：

```text
1. recoverStaleRunningExecutions(now)
2. rescheduleRetryableFailedPlans(now, limit)
3. findDueScheduledPlans(now, limit)
4. dispatchIfDue(...)
```

这表示失败自动重试会复用原有调度执行路径，而不是另开一条执行链路。

## 6. 索引

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

## 7. 当前限制

```text
1. 还没有分 skill 的退避策略。
2. lastErrorMessage 只保存最近一次错误，不保存完整错误历史。
3. 当前执行失败计数仍基于 TaskPlanRepository.save(plan) 的状态变化。
4. 不可重试错误还没有区分，后续需要 FAILED_FINAL 或 NEEDS_MANUAL_REVIEW。
```

## 8. 后续建议

后续可以增加：

```text
1. skill-level retry policy：不同 skill 配置不同 maxRetryCount、初始退避和最大退避。
2. lastErrorCode / lastErrorType：区分参数错误、外部系统超时、权限错误。
3. permanent failure：不可重试错误直接进入 FAILED_FINAL 或 NEEDS_MANUAL_REVIEW。
4. retry audit：记录每次自动重试恢复动作。
```

## 9. 验证建议

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

手动把失败 action 设置为可重试：

```sql
update ai_task_action
set next_retry_at_epoch_ms = unix_timestamp(now(3)) * 1000 - 1000
where action_id = '<action_id>';
```

下次调度后确认状态恢复：

```sql
select action_id,
       status,
       execution_note,
       next_retry_at_epoch_ms
from ai_task_action
where action_id = '<action_id>';
```

确认索引：

```sql
show index from ai_task_action;
```

重点查看：

```text
idx_task_action_status_retry_next_run
```
