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

当前查询逻辑：

```text
1. 从 ai_task_action 查询 status = SCHEDULED 的 action。
2. 要求 nextRunAt 不为空。
3. 要求 nextRunAt <= now。
4. 按 nextRunAt 升序排序。
5. 按 planId 分组。
6. 使用 bounded limit 限制批次数量。
7. 再根据 planId 回查完整 TaskPlan。
```

当前版本已经不再依赖全量扫描，也不再只靠 Java 侧判断所有 scheduled action 是否到期。

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

`TaskActionEntity.nextRunAt` 已增加索引：

```java
@Index
@ColumnType(value = MysqlTypeConstant.VARCHAR, length = 64)
@ColumnComment("下一次执行时间")
private String nextRunAt;
```

当前 `planId` 已有索引，`status` 和 `nextRunAt` 也有索引，能支撑当前阶段的到期调度查询。

### 3.4 同步调度字段

保存 `TaskActionEntity` 时会从 `TaskSchedule` 同步：

```text
scheduleType = schedule.scheduleType
runAt        = schedule.runAt
nextRunAt    = schedule.effectiveRunAt()
lastRunAt    = schedule.lastRunAt
triggerCount = schedule.triggerCount
```

`nextRunAt` 统一使用 `schedule.effectiveRunAt()`，即优先取 `nextRunAt`，没有时回退 `runAt`。

## 4. 当前限制

当前版本仍存在以下限制：

```text
1. nextRunAt 当前使用字符串存储，不是数据库 datetime 类型。
2. 多实例下仍可能重复扫描同一批 SCHEDULED action。
3. action 执行过程没有 lockedBy / lockedAt。
4. 没有 executionAttempt / maxRetryCount。
5. 没有 idempotencyKey。
6. 还没有 status + nextRunAt 的组合索引。
```

## 5. 后续推荐演进

### 5.1 时间字段类型升级

后续建议把 `runAt / nextRunAt / lastRunAt` 从字符串升级为数据库时间类型，或者统一存储 epoch milliseconds。

推荐优先级：

```text
epoch milliseconds > datetime + timezone 额外字段 > varchar ISO 字符串
```

原因：

```text
ISO 字符串在不同时区 offset 下做字典序比较存在边界风险。
```

当前版本建议尽量保持同一 timezone 生成调度时间。

### 5.2 增加组合索引

当时间字段类型稳定后，推荐组合索引：

```text
idx_status_next_run_at(status, next_run_at)
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
3. 创建一个 recurring reminder，确认执行后仍保持 SCHEDULED，并推进 nextRunAt。
4. 数据库中存在大量 EXECUTED / CANCELLED plan 时，调度扫描只查询 SCHEDULED 且 nextRunAt 已到期的 action 对应计划。
```

## 7. 当前结论

本阶段完成的是调度可靠性的第二步：

```text
从有界扫描 SCHEDULED action，升级为有界扫描 SCHEDULED 且 nextRunAt 已到期的 action。
```

真正的生产级调度还需要继续完成：

```text
时间字段类型升级、组合索引、锁、重试次数、幂等键、超时恢复。
```
