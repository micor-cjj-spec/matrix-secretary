# 调度可靠性改造说明

本文档记录 `feat/reliable-task-scheduling` 分支的第一轮调度可靠性改造。

## 背景

原实现中，本地 `@Scheduled` 或 XXL-JOB 每 10 秒触发一次 `dispatchDueOnceTasks()`，内部会读取全部 `TaskPlan`，再逐个判断 action 是否到期。

这种方式适合 demo，但存在几个问题：

- 任务量上来后会全量扫描计划表。
- 多实例部署时缺少抢占锁，可能重复执行同一个到期任务。
- 执行过程中服务宕机后缺少锁过期和恢复基础。
- 调度查询字段隐藏在 `scheduleJson` 内，不利于建立索引。

## 本次改造内容

### 1. action 表增加调度辅助字段

`TaskActionEntity` 新增字段：

```text
nextFireTime     下次触发时间，调度器按该字段查询到期动作
lockedBy         调度执行锁持有者
lockedAt         调度执行锁获取时间
attemptCount     执行尝试次数
maxRetryCount    最大重试次数
idempotencyKey   幂等键
lastError        最近一次错误信息
```

其中 `status` 和 `nextFireTime` 已加索引注解，后续调度查询应围绕这两个字段优化。

### 2. 不再全量扫描计划

`TaskPlanRepository` 新增：

```text
findDueScheduledActions(now, lockExpiredBefore, limit)
```

查询条件：

```sql
status = 'SCHEDULED'
AND next_fire_time <= now
AND (locked_by IS NULL OR locked_at IS NULL OR locked_at < lockExpiredBefore)
ORDER BY next_fire_time ASC
LIMIT :limit
```

当前批量大小为 100。

### 3. 到期动作执行前先抢锁

`TaskPlanRepository` 新增：

```text
tryLockScheduledAction(actionId, now, lockExpiredBefore, lockOwner)
releaseActionLock(actionId, lockOwner)
```

抢锁通过条件更新实现：只有仍处于 `SCHEDULED`、已经到期、且未被有效锁占用的 action 才能更新成功。

这可以避免：

- 本地调度与 XXL-JOB 同时触发时重复执行同一 action。
- 多实例同时扫描到同一 action 时重复执行。

### 4. 调度主流程调整

`AiTaskService.dispatchDueOnceTasks()` 现在流程为：

```text
计算 now / lockExpiredBefore / lockOwner
  -> 查询到期 action 批次
  -> 对每个 action 尝试抢锁
  -> 抢锁成功后读取所属 plan
  -> 只执行当前到期 action
  -> 保存 plan/action 状态
  -> 如果没有产生状态变化则释放锁
```

锁超时时间当前为 10 分钟。

## 当前仍未完成

这次改造是第一步，不代表调度已经完全生产级。后续还需要继续补：

1. 用增量 update 替代 `TaskPlanRepository.save()` 中的 delete-and-reinsert action。
2. 引入明确的 `RUNNING` / `RETRY_WAITING` / `TIMEOUT` 状态。
3. `attemptCount` 真正递增，并与 `maxRetryCount`、失败退避策略联动。
4. 使用独立状态机服务管理调度状态流转。
5. 为调度抢锁、锁过期、周期任务推进增加自动化测试。
6. 对 `next_fire_time`、`status`、`locked_at` 建复合索引，而不是只依赖字段级索引。

## 验收建议

至少验证以下场景：

1. 一个到期一次性提醒只执行一次。
2. 本地 `@Scheduled` 和 XXL-JOB 同时触发时，不会重复执行同一 action。
3. 两个 Java 实例同时触发调度时，只有一个实例能抢到同一 action。
4. 周期任务执行成功后重新进入 `SCHEDULED`，并推进下一次 `nextFireTime`。
5. 被锁住但超过 10 分钟未完成的 action 可以被后续调度重新抢占。
