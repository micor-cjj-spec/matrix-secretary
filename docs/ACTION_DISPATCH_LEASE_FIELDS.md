# Action dispatch lease 字段说明

本次改造为 `ai_task_action` 增加 dispatch lease 相关字段，用于后续实现多实例调度抢占。

## 新增字段

| 字段 | 说明 |
|---|---|
| `dispatch_locked_until` | 调度锁过期时间，已加索引 |
| `dispatch_owner` | 当前锁持有者，例如实例 ID |
| `dispatch_attempt` | 调度抢占次数 |

## 候选查询语义

Repository 新增：

```java
public PageResult<TaskActionEntity> findDispatchLeaseCandidates(OffsetDateTime now, long page, long size)
```

查询语义：

```sql
status = 'SCHEDULED'
AND next_run_at IS NOT NULL
AND next_run_at <= :now
AND (
  dispatch_locked_until IS NULL
  OR dispatch_locked_until <= :now
)
ORDER BY next_run_at ASC, sort_order ASC
```

`findDueScheduledActions(...)` 当前委托到 `findDispatchLeaseCandidates(...)`，因此本地调度器已经不会主动读取仍在 lease 期内的 action。

## 保存规则

当前 `TaskPlanRepository.save(plan)` 会删除并重建 plan 下全部 action。因此保存 action 时会初始化 lease 字段：

```java
entity.setDispatchLockedUntil(null);
entity.setDispatchOwner(null);
entity.setDispatchAttempt(0);
```

这一步只提供字段和候选查询，不做原子抢占。

## 当前边界

本次还没有实现真正的多实例安全，因为还缺少原子更新：

```sql
UPDATE ai_task_action
SET dispatch_locked_until = :leaseUntil,
    dispatch_owner = :owner,
    dispatch_attempt = dispatch_attempt + 1
WHERE action_id = :actionId
  AND status = 'SCHEDULED'
  AND next_run_at <= :now
  AND (
    dispatch_locked_until IS NULL
    OR dispatch_locked_until <= :now
  )
```

只有这个更新返回 1 行时，当前实例才算抢占成功。

## 下一步

继续生产化调度应做：

1. 增加原子抢占方法 `tryAcquireDispatchLease(...)`。
2. 调度器执行前先抢占 lease。
3. 执行成功后保存 action 并清理 lease。
4. 执行失败时记录失败原因，必要时保留 lease 到期后重试。
5. 增加幂等键，避免外部副作用重复发生。
