# Task Center 调度可靠性说明

本文档记录任务中心调度扫描的当前实现、已完成优化和后续可靠性改造方向。

## 1. 当前目标

调度模块的目标是：

```text
只扫描已经到期的调度任务动作，避免每次调度都全量扫描所有 TaskPlan。
```

当前项目已经把核心调度字段从 `scheduleJson` 中同步拆出到 `ai_task_action` 表的独立列中：

```text
scheduleType
runAt
nextRunAt
nextRunAtEpochMs
lastRunAt
triggerCount
```

`TaskAction.scheduleJson` 仍然保留，用于兼容完整调度对象；独立列用于调度查询和后续索引优化。

## 2. 当前实现

### 2.1 Repository 查询

`TaskPlanRepository` 提供：

```java
public List<TaskPlan> findDueScheduledPlans(OffsetDateTime now, int limit)
```

当前主查询逻辑：

```text
1. 从 ai_task_action 查询 status = SCHEDULED 的 action。
2. 要求 nextRunAtEpochMs 不为空。
3. 要求 nextRunAtEpochMs <= now.toInstant().toEpochMilli()。
4. 按 nextRunAtEpochMs 升序排序。
5. 使用 bounded limit 限制批次数量。
6. 再根据 planId 回查完整 TaskPlan。
```

为了兼容历史数据，当前保留一段兜底查询：

```text
当 nextRunAtEpochMs 为空时，使用 nextRunAt 字符串做短期兜底过滤。
```

等历史数据通过任务重新保存或迁移脚本完成回填后，可以删除该兜底分支。

### 2.2 Dispatch 扫描

`TaskDispatchService` 当前入口：

```java
public void dispatchDueOnceTasks()
```

内部默认委托：

```java
public void dispatchDueOnceTasks(int limit)
```

默认批次：

```java
private static final int DEFAULT_DISPATCH_LIMIT = 100;
```

扫描逻辑：

```text
1. OffsetDateTime now = OffsetDateTime.now()
2. taskPlanRepository.findDueScheduledPlans(now, limit)
3. 遍历 plan.tasks()
4. 对 SCHEDULED 且 schedule 不为空的 action 做最终兜底判断
5. 到期后 executeNow
6. 周期任务执行成功后 markTriggered 并继续保持 SCHEDULED
7. 保存 TaskPlan 并更新 Session
```

说明：Repository 已经做第一层到期过滤，`TaskDispatchService` 中的时间判断仍保留，作为兜底保护。

## 3. 已完成优化

### 3.1 不再全量扫描 TaskPlan

旧逻辑：

```java
taskPlanRepository.findAll()
```

新逻辑：

```java
taskPlanRepository.findDueScheduledPlans(now, limit)
```

收益：

```text
调度扫描从“扫描所有计划”收敛为“扫描到期的 SCHEDULED action 对应计划”。
```

### 3.2 增加批次上限

`TaskPlanRepository` 中增加：

```java
private static final int MAX_DISPATCH_QUERY_LIMIT = 500;
```

调用方传入 limit 时会被限制在合理范围内。

### 3.3 给 action 状态和调度时间增加索引

`TaskActionEntity.status` 已增加索引：

```java
@Index
@ColumnType(value = MysqlTypeConstant.VARCHAR, length = 32)
@ColumnComment("动作状态")
private String status;
```

`TaskActionEntity.nextRunAtEpochMs` 已增加索引：

```java
@Index
@ColumnComment("下一次执行时间epoch毫秒")
private Long nextRunAtEpochMs;
```

`nextRunAt` 字符串字段仍保留索引，用于短期兼容旧数据兜底查询。

### 3.4 同步调度字段

保存 `TaskActionEntity` 时会从 `TaskSchedule` 同步：

```text
scheduleType      = schedule.scheduleType
runAt             = schedule.runAt
nextRunAt         = schedule.effectiveRunAt()
nextRunAtEpochMs  = OffsetDateTime.parse(schedule.effectiveRunAt()).toInstant().toEpochMilli()
lastRunAt         = schedule.lastRunAt
triggerCount      = schedule.triggerCount
```

`nextRunAt` 统一使用 `schedule.effectiveRunAt()`，即优先取 `nextRunAt`，没有时回退 `runAt`。

## 4. 当前限制

当前版本仍存在以下限制：

```text
1. 多实例下仍可能重复扫描同一批 SCHEDULED action。
2. action 执行过程没有 lockedBy / lockedAt。
3. 没有 executionAttempt / maxRetryCount。
4. 没有 idempotencyKey。
5. 还没有 status + nextRunAtEpochMs 的组合索引。
6. 历史数据如果没有 nextRunAtEpochMs，仍依赖 nextRunAt 字符串兜底。
```

## 5. 后续推荐演进

### 5.1 历史数据回填

建议后续提供一次性迁移脚本：

```sql
-- 伪代码：真实 SQL 需要根据数据库版本和 JSON 格式调整
update ai_task_action
set next_run_at_epoch_ms = parse_epoch_ms(next_run_at)
where next_run_at_epoch_ms is null
  and next_run_at is not null;
```

也可以用 Java migration runner 读取 `nextRunAt` 后调用 `OffsetDateTime.parse(...).toInstant().toEpochMilli()` 回填。

### 5.2 增加组合索引

当历史数据回填完成后，推荐组合索引：

```text
idx_status_next_run_epoch(status, next_run_at_epoch_ms)
idx_plan_id_status(plan_id, status)
```

当前阶段先使用单列索引，避免一次性改动过大。

### 5.3 增加锁机制

多实例部署前，需要增加抢占机制：

```text
1. 查询到期 action。
2. update action set lockedBy=?, lockedAt=? where actionId=? and status='SCHEDULED' and lockedBy is null。
3. update 成功的实例才允许执行。
4. 执行完成后清理锁或推进状态。
5. 超时锁由恢复任务释放。
```

### 5.4 增加幂等键

后续每次执行 action 应生成或持久化：

```text
idempotencyKey = planId + actionId + triggerCount
```

外部执行器，例如消息、邮件、HTTP webhook，应基于幂等键避免重复发送。

## 6. 验收建议

本阶段本地验证：

```text
1. 创建一个立即执行任务，确认不被调度扫描重复执行。
2. 创建一个 once reminder，确认到期前不会执行，到期后执行一次。
3. 创建一个 recurring reminder，确认执行后仍保持 SCHEDULED，并推进 nextRunAt 和 nextRunAtEpochMs。
4. 数据库中存在大量 EXECUTED / CANCELLED plan 时，调度扫描只查询 SCHEDULED 且 nextRunAtEpochMs 已到期的 action 对应计划。
```

## 7. 当前结论

本阶段完成的是调度可靠性的第三步：

```text
从字符串 nextRunAt 到期过滤，升级为 epoch milliseconds 数字过滤。
```

真正的生产级调度还需要继续完成：

```text
历史数据回填、组合索引、锁、重试次数、幂等键、超时恢复。
```
