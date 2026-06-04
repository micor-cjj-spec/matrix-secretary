# Task Center 调度可靠性说明

本文档记录任务中心调度扫描的当前实现、已完成优化和后续可靠性改造方向。

## 1. 当前目标

调度模块的目标是：

```text
只扫描需要调度的任务动作，避免每次调度都全量扫描所有 TaskPlan。
```

当前项目中，调度信息仍存储在 `TaskAction.scheduleJson` 中，因此第一阶段不直接在 SQL 层解析 `runAt / nextRunAt`，而是先做到：

```text
1. Repository 层只取存在 SCHEDULED action 的 planId。
2. Dispatch 层继续在 Java 内判断 runAt / nextRunAt 是否到期。
3. 每次扫描使用 limit 控制批次大小。
```

## 2. 当前实现

### 2.1 Repository 查询

`TaskPlanRepository` 提供：

```java
public List<TaskPlan> findDueScheduledPlans(OffsetDateTime now, int limit)
```

当前实现边界：

```text
- 先从 ai_task_action 查询 status = SCHEDULED 的 action。
- 按 planId 分组。
- 使用 bounded limit 限制批次数量。
- 再根据 planId 回查完整 TaskPlan。
```

虽然方法名是 `findDueScheduledPlans`，但当前版本还没有在 SQL 层过滤真实到期时间。

原因：

```text
runAt / nextRunAt 目前仍在 scheduleJson 内。
```

因此，真正到期判断仍在 `TaskDispatchService` 中完成。

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
4. 对 SCHEDULED 且 schedule 不为空的 action 判断 effectiveRunAt
5. 到期后 executeNow
6. 周期任务执行成功后 markTriggered 并继续保持 SCHEDULED
7. 保存 TaskPlan 并更新 Session
```

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
调度扫描从“扫描所有计划”收敛为“扫描存在 SCHEDULED action 的计划”。
```

### 3.2 增加批次上限

`TaskPlanRepository` 中增加：

```java
private static final int MAX_DISPATCH_QUERY_LIMIT = 500;
```

调用方传入 limit 时会被限制在合理范围内。

### 3.3 给 action 状态增加索引

`TaskActionEntity.status` 已增加：

```java
@Index
@ColumnType(value = MysqlTypeConstant.VARCHAR, length = 32)
@ColumnComment("动作状态")
private String status;
```

当前 `planId` 已有索引，`status` 也有索引，能支撑第一阶段的 scheduled action 查询。

## 4. 当前限制

当前版本仍存在以下限制：

```text
1. SQL 层不能直接判断 runAt / nextRunAt 是否到期。
2. 多实例下仍可能重复扫描同一批 SCHEDULED action。
3. action 执行过程没有 lockedBy / lockedAt。
4. 没有 executionAttempt / maxRetryCount。
5. 没有 idempotencyKey。
6. scheduleJson 作为 JSON 字段，不利于高效调度查询。
```

## 5. 后续推荐演进

### 5.1 拆出调度列

后续建议在 `TaskActionEntity` 增加：

```text
scheduleType
runAt
nextRunAt
lastRunAt
triggerCount
lockedBy
lockedAt
executionAttempt
maxRetryCount
idempotencyKey
```

这样可以把查询升级为：

```sql
where status = 'SCHEDULED'
  and next_run_at <= now()
order by next_run_at asc
limit ?
```

### 5.2 增加组合索引

当 `nextRunAt` 独立成列后，推荐组合索引：

```text
idx_status_next_run_at(status, next_run_at)
idx_plan_id_status(plan_id, status)
```

当前阶段由于尚未拆出 `nextRunAt` 列，暂时只增加 `status` 单列索引。

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
3. 创建一个 recurring reminder，确认执行后仍保持 SCHEDULED，并推进 nextRunAt。
4. 数据库中存在大量 EXECUTED / CANCELLED plan 时，调度扫描只查询 SCHEDULED action 对应计划。
```

## 7. 当前结论

本阶段完成的是调度可靠性的第一步：

```text
从全量扫描 TaskPlan，升级为有界扫描 SCHEDULED action 对应的 TaskPlan。
```

真正的生产级调度还需要继续完成：

```text
到期时间列化、组合索引、锁、重试次数、幂等键、超时恢复。
```
