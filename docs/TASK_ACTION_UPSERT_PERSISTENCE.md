# TaskAction upsert 持久化说明

本次改造将 `TaskPlanRepository.save(plan)` 中 action 的保存方式从“删除重建”调整为按 `actionId` upsert。

## 背景

旧逻辑：

```java
taskActionMapper.delete(eq(planId));
for (...) {
    taskActionMapper.insert(toActionEntity(...));
}
```

问题：

- 每次保存 plan 都会删除并重建所有 action。
- `dispatch_locked_until`、`dispatch_owner`、`dispatch_attempt` 等运行时字段会被重置。
- 后续如果继续增加执行统计、失败退避、幂等信息，也会被这种全量重建破坏。

## 新逻辑

保存 action 时分三步：

```text
1. 查询当前 plan 下已有 action
2. 对本次 plan.tasks() 中的 action 按 actionId upsert
   - 已存在：保留 dispatch lease 字段后 updateById
   - 不存在：insert
3. 删除数据库中存在、但本次 plan.tasks() 已不存在的 stale action
```

## 保留字段

更新已有 action 时会保留：

```java
nextEntity.setDispatchLockedUntil(existing.getDispatchLockedUntil());
nextEntity.setDispatchOwner(existing.getDispatchOwner());
nextEntity.setDispatchAttempt(existing.getDispatchAttempt());
```

这意味着执行前抢占到的 lease 不会因为保存 plan 被直接清空。

## 删除规则

如果数据库中有旧 action，但当前 plan 的 action 列表里已经没有对应 `actionId`，会删除这些 stale action。

等价语义：

```sql
DELETE FROM ai_task_action
WHERE plan_id = :planId
AND action_id NOT IN (:currentActionIds)
```

如果当前 action 列表为空，则删除该 plan 下全部 action。

## 当前边界

本次只保护 dispatch lease 字段，不代表完整执行历史已经独立化。后续如果加入更多运行时字段，需要继续扩展保留逻辑，或者把运行时状态拆到独立表。

## 下一步

建议继续做：

1. 将执行幂等信息拆到 `ai_task_action_execution` 或 `ai_task_dispatch_record`。
2. 增加 `idempotency_key = planId + actionId + triggerAt`。
3. 对外部副作用类 skill 使用幂等键，例如邮件、飞书、HTTP 回调。
4. 增加失败退避字段和最大重试次数。
