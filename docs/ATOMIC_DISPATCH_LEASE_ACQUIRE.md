# 原子 dispatch lease 抢占说明

本次改造在 action-level due scan 基础上增加原子 lease 抢占，避免多实例调度时多个实例同时执行同一个 action。

## 核心方法

Repository 新增：

```java
public boolean tryAcquireDispatchLease(
        String actionId,
        OffsetDateTime now,
        OffsetDateTime leaseUntil,
        String owner
)
```

底层使用单条条件 UPDATE：

```sql
UPDATE ai_task_action
SET dispatch_locked_until = :leaseUntil,
    dispatch_owner = :owner,
    dispatch_attempt = COALESCE(dispatch_attempt, 0) + 1
WHERE action_id = :actionId
  AND status = 'SCHEDULED'
  AND next_run_at IS NOT NULL
  AND next_run_at <= :now
  AND (
    dispatch_locked_until IS NULL
    OR dispatch_locked_until <= :now
  )
```

只有更新成功 1 行时，当前实例才认为自己抢占成功。

## 调度流程

```text
find due action candidates
  -> tryAcquireDispatchLease(actionId, now, leaseUntil, owner)
  -> acquired=false: skip
  -> acquired=true: load plan and execute action
  -> save plan/actions
```

## 配置项

```yaml
ai-secretary:
  local-scheduler:
    dispatch-page-size: 50
    dispatch-lease-seconds: 60
    dispatch-owner: local-scheduler
```

环境变量：

```bash
AI_SECRETARY_DISPATCH_PAGE_SIZE=50
AI_SECRETARY_DISPATCH_LEASE_SECONDS=60
AI_SECRETARY_DISPATCH_OWNER=local-scheduler
```

## 当前边界

本次只保证执行前抢占 lease，不完整覆盖以下情况：

- `TaskPlanRepository.save(plan)` 当前会删除并重建 plan 下所有 action，因此保存后 lease 字段会被重置。
- 执行过程中如果服务崩溃，lease 会等到 `dispatch_locked_until` 过期后重新进入候选队列。
- 尚未增加外部副作用幂等键，例如邮件发送、飞书消息发送的业务幂等。
- 尚未对失败任务做退避重试。

## 下一步

继续生产化建议：

1. 将 action 保存从“删除重建”改为按 `actionId` upsert，避免 lease 信息被全量重置。
2. 增加 action 执行幂等键：`planId + actionId + triggerAt`。
3. 增加执行失败退避和最大重试次数。
4. 增加 dispatch owner 为真实实例 ID，例如 hostname + pid。
5. 增加 lease 过期恢复日志。