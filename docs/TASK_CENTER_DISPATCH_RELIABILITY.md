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
lockedBy
lockedAtEpochMs
executionAttempt
maxRetryCount
idempotencyKey
```

`TaskAction.scheduleJson` 仍然保留，用于兼容完整调度对象；独立列用于调度查询、锁抢占、重试治理、幂等治理和后续索引优化。

同时已新增执行记录表：

```text
ai_task_action_execution
```

用于记录按 `idempotencyKey` 执行过的 action，避免同一个幂等键重复调用外部执行器。

## 2. 当前实现

### 2.1 Repository 查询

`TaskPlanRepository` 提供：

```java
public List<TaskPlan> findDueScheduledPlans(OffsetDateTime now, int limit)
```

当前主查询逻辑：

```text
1. 从 ai_task_action 查询 status = SCHEDULED 的 action。
2. 要求 lockedBy 为空，或者 lockedAtEpochMs 已超时。
3. 要求 executionAttempt < maxRetryCount，或者重试字段为空。
4. 要求 nextRunAtEpochMs 不为空。
5. 要求 nextRunAtEpochMs <= now.toInstant().toEpochMilli()。
6. 按 nextRunAtEpochMs 升序排序。
7. 使用 bounded limit 限制批次数量。
8. 再根据 planId 回查完整 TaskPlan。
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
5. 执行前调用 tryLockAction 抢占 action 锁
6. 抢锁成功后 executeNow
7. 周期任务执行成功后 markTriggered 并继续保持 SCHEDULED
8. finally 中 releaseActionLock
9. 保存 TaskPlan 并更新 Session
```

说明：Repository 已经做第一层到期过滤，`TaskDispatchService` 中的时间判断仍保留，作为兜底保护。

### 2.3 锁机制

`TaskPlanRepository` 提供：

```java
public boolean tryLockAction(String actionId, String lockedBy, OffsetDateTime now)
```

抢锁条件：

```text
actionId 匹配
status = SCHEDULED
lockedBy 为空，或者 lockedAtEpochMs 已超过锁超时时间
executionAttempt < maxRetryCount，或者重试字段为空
```

当前锁超时时间：

```java
private static final long DISPATCH_LOCK_TIMEOUT_MS = 5 * 60 * 1000L;
```

释放锁：

```java
public void releaseActionLock(String actionId, String lockedBy)
```

释放时会校验 `lockedBy`，避免实例 A 释放实例 B 的锁。

### 2.4 重试计数

当前默认最大重试次数：

```java
private static final int DEFAULT_MAX_RETRY_COUNT = 3;
```

保存 `TaskPlan` 时会先读取旧 action：

```text
1. action 第一次保存时，maxRetryCount 默认 3，executionAttempt 默认 0。
2. action 从非 FAILED 变成 FAILED 时，executionAttempt + 1。
3. action 变成 EXECUTED 时，executionAttempt 重置为 0。
4. action 保持 FAILED 时，不重复累加。
```

调度查询和抢锁都会排除达到最大重试次数的 action。

### 2.5 幂等键

`TaskActionEntity` 持久化：

```java
private String idempotencyKey;
```

当前生成规则：

```text
1. 如果旧 action 已存在 idempotencyKey，沿用旧值。
2. 如果 action.args.idempotencyKey 已存在，优先使用请求传入值。
3. 否则使用 planId + actionId + triggerCount 生成。
```

保存 action 时，会把 `idempotencyKey` 同步写入 `args.idempotencyKey`。读取 action 时，也会把实体字段中的 `idempotencyKey` 回填到 `TaskAction.args`。

### 2.6 执行记录表与原子占位

`TaskActionExecutionRepository` 提供：

```java
tryBeginExecution(planId, userId, skill, action, operatorUserId)
findExecutedByIdempotencyKey(idempotencyKey)
record(planId, userId, skill, before, after, operatorUserId)
```

当前执行记录状态：

```text
RUNNING
EXECUTED
FAILED
```

`tryBeginExecution` 当前规则：

```text
1. 从 TaskAction.args 读取 idempotencyKey。
2. 如果没有 idempotencyKey，则直接放行执行。
3. 如果已存在同 key 且状态为 EXECUTED，返回 EXECUTED，调用方跳过真实执行。
4. 如果已存在同 key 且状态为 RUNNING，返回 RUNNING，调用方跳过本次重复触发。
5. 如果不存在同 key，则使用 idempotencyKey 的 SHA-256 生成确定性执行记录 ID。
6. 先插入 RUNNING 执行记录，插入成功才允许调用 GenericSkillExecutor。
7. 如果并发插入发生主键冲突，则回查已有记录并按 EXECUTED / RUNNING 分支处理。
8. 执行完成后 record(...) 将 RUNNING 更新为 EXECUTED 或 FAILED。
```

`TaskExecutionService.executeNow()` 当前执行规则：

```text
1. 获取 SkillDefinition。
2. 调用 tryBeginExecution 抢占 idempotencyKey。
3. ACQUIRED：调用 GenericSkillExecutor 真正执行，并记录结果。
4. EXECUTED：直接返回 EXECUTED，不调用 GenericSkillExecutor。
5. RUNNING：直接返回 SCHEDULED，不调用 GenericSkillExecutor。
6. 继续写 TaskExecutionLog。
```

这一步已经把幂等从“执行前查询”推进到“执行前原子占位 + 执行后更新”。

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

### 3.3 给 action 状态、调度时间和锁字段增加索引

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

`TaskActionEntity.lockedBy` 和 `lockedAtEpochMs` 已增加索引，用于过滤未加锁或锁超时的 action。

`TaskActionEntity.idempotencyKey` 已增加索引，用于后续执行器按幂等键查询执行记录。

`TaskActionExecutionEntity.idempotencyKey` 已增加索引，用于执行前查重；执行记录 ID 由 `idempotencyKey` 的 SHA-256 派生，提供第一层并发占位保护。

`nextRunAt` 字符串字段仍保留索引，用于短期兼容旧数据兜底查询。

### 3.4 同步调度与治理字段

保存 `TaskActionEntity` 时会从 `TaskSchedule`、旧 action 和 args 同步：

```text
scheduleType      = schedule.scheduleType
runAt             = schedule.runAt
nextRunAt         = schedule.effectiveRunAt()
nextRunAtEpochMs  = OffsetDateTime.parse(schedule.effectiveRunAt()).toInstant().toEpochMilli()
lastRunAt         = schedule.lastRunAt
triggerCount      = schedule.triggerCount
lockedBy          = null
lockedAtEpochMs   = null
executionAttempt  = 根据旧状态和新状态计算
maxRetryCount     = 旧值或默认 3
idempotencyKey    = 旧值 / args.idempotencyKey / planId:actionId:triggerCount
```

`nextRunAt` 统一使用 `schedule.effectiveRunAt()`，即优先取 `nextRunAt`，没有时回退 `runAt`。

## 4. 当前限制

当前版本仍存在以下限制：

```text
1. 还没有 status + nextRunAtEpochMs + lockedBy 的组合索引。
2. 历史数据如果没有 nextRunAtEpochMs，仍依赖 nextRunAt 字符串兜底。
3. 当前 save(plan) 会重建 action 行；虽然已保留 executionAttempt / maxRetryCount / idempotencyKey，但后续仍应改成 action 级别更新。
4. executionAttempt 当前只在状态变为 FAILED 时累加；如果未来做“FAILED 自动重新调度”，需要进一步扩展退避策略。
5. 执行记录 ID 已经按 idempotencyKey 确定性生成，但 idempotencyKey 字段本身还没有数据库唯一约束。
6. RUNNING 执行记录目前没有超时恢复逻辑，执行器进程异常退出时需要后续恢复任务处理。
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

### 5.2 增加组合索引与唯一约束

当历史数据回填完成后，推荐组合索引：

```text
idx_status_next_run_epoch(status, next_run_at_epoch_ms)
idx_status_lock_next_run(status, locked_by, locked_at_epoch_ms, next_run_at_epoch_ms)
idx_plan_id_status(plan_id, status)
```

执行记录表推荐唯一约束：

```text
uk_idempotency_key(idempotency_key)
```

当前阶段通过确定性执行记录 ID 先提供并发占位保护，后续仍建议增加唯一约束。

### 5.3 改为 action 级别保存

当前 `TaskPlanRepository.save(plan)` 会删除并重插 action 行。后续调度执行建议逐步改成：

```text
1. action 级别状态更新。
2. action 级别 schedule 字段更新。
3. plan 状态单独刷新。
4. 避免重建 action 行影响锁字段和执行统计字段。
```

### 5.4 增加失败退避

后续如果允许失败任务自动重新调度，建议增加：

```text
nextRetryAtEpochMs
retryBackoffSeconds
lastErrorCode
lastErrorMessage
```

避免失败任务高频重复执行。

### 5.5 增加 RUNNING 超时恢复

后续需要增加恢复任务：

```text
1. 扫描 ai_task_action_execution 中长时间 RUNNING 的记录。
2. 根据业务策略标记 FAILED 或重新放行。
3. 结合 action lockedAtEpochMs 释放卡住的执行锁。
```

## 6. 验收建议

本阶段本地验证：

```text
1. 创建一个立即执行任务，确认不被调度扫描重复执行。
2. 创建一个 once reminder，确认到期前不会执行，到期后执行一次。
3. 创建一个 recurring reminder，确认执行后仍保持 SCHEDULED，并推进 nextRunAt 和 nextRunAtEpochMs。
4. 数据库中存在大量 EXECUTED / CANCELLED plan 时，调度扫描只查询 SCHEDULED 且 nextRunAtEpochMs 已到期的 action 对应计划。
5. 手动设置某个 action 的 lockedBy，确认未超时前不会被扫描或执行。
6. 手动设置 lockedAtEpochMs 为 5 分钟以前，确认可以被重新抢锁执行。
7. 模拟 action 执行失败，确认 executionAttempt 增加。
8. 将 executionAttempt 设置为 maxRetryCount，确认不再被调度扫描或抢锁。
9. 查询 action 返回结果，确认 args.idempotencyKey 存在。
10. 重复执行同一个 idempotencyKey 的 action，确认第二次不会调用真实执行器。
11. 并发触发同一个 idempotencyKey，确认只有一个请求能插入 RUNNING 执行记录并进入真实执行。
```

## 7. 当前结论

本阶段完成的是调度可靠性的第八步：

```text
从执行前查重，升级为执行前原子占位 + 执行后记录。
```

真正的生产级调度还需要继续完成：

```text
历史数据回填、组合索引、唯一约束、action 级别保存、失败退避、RUNNING 超时恢复。
```
