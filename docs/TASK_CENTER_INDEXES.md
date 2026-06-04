# Task Center 索引与约束说明

本文档记录任务中心当前由 `TaskCenterSchemaInitializer` 自动创建的索引与约束。

## 1. 背景

AutoTable 已经负责实体建表和普通单列索引，但任务中心调度、锁、幂等执行对索引要求更明确，因此增加 `TaskCenterSchemaInitializer` 在应用启动后补关键索引。

初始化器位置：

```text
java-service/src/main/java/com/kailei/demo/config/TaskCenterSchemaInitializer.java
```

初始化方式：

```text
1. 查询 information_schema.statistics。
2. 如果索引不存在，则执行 alter table 创建索引。
3. 如果创建失败，只打印 warn，不阻断本地开发启动。
```

## 2. 执行幂等唯一索引

```sql
alter table ai_task_action_execution
add unique key uk_task_action_execution_idempotency_key (idempotency_key);
```

用途：

```text
保证同一个 idempotencyKey 在执行记录表中只存在一条记录。
```

配合机制：

```text
1. TaskAction.args.idempotencyKey 提供幂等键。
2. TaskActionExecutionRepository 使用 idempotencyKey 的 SHA-256 生成确定性执行记录 ID。
3. 执行前先插入 RUNNING 占位记录。
4. 唯一索引进一步避免极端并发下同 key 重复插入。
```

注意：

```text
如果历史数据里已经存在重复 idempotencyKey，唯一索引创建会失败。
此时应用不会启动失败，但日志会打印 warn。
需要先清理重复历史数据，再重启应用。
```

## 3. 到期调度查询索引

```sql
alter table ai_task_action
add index idx_task_action_status_next_run_epoch (status, next_run_at_epoch_ms);
```

对应查询：

```text
status = 'SCHEDULED'
next_run_at_epoch_ms <= nowEpochMs
order by next_run_at_epoch_ms asc
limit ?
```

用途：

```text
支撑到期任务扫描，避免调度器扫描大量非 SCHEDULED 或未到期 action。
```

## 4. 锁过滤 + 到期调度索引

```sql
alter table ai_task_action
add index idx_task_action_status_lock_next_run
(status, locked_by, locked_at_epoch_ms, next_run_at_epoch_ms);
```

对应查询条件：

```text
status = 'SCHEDULED'
locked_by is null or locked_at_epoch_ms < expiredBefore
next_run_at_epoch_ms <= nowEpochMs
```

用途：

```text
支撑多实例调度下的锁过滤和锁超时恢复扫描。
```

说明：

```text
当前查询中包含 OR 条件，MySQL 优化器未必总能完全利用组合索引。
后续如果任务量很大，可以考虑增加 lock_status 或 available_at_epoch_ms 派生字段，进一步简化查询条件。
```

## 5. 计划 + 状态索引

```sql
alter table ai_task_action
add index idx_task_action_plan_status (plan_id, status);
```

用途：

```text
支撑按 planId 查询 action、按 planId + status 做状态统计或局部更新。
```

## 6. 当前已由实体注解创建的单列索引

`TaskActionEntity` 中已有多个 `@Index` 单列索引，包括：

```text
planId
scheduleType
nextRunAt
nextRunAtEpochMs
lockedBy
lockedAtEpochMs
idempotencyKey
status
```

这些单列索引可用于基础查询；组合索引用于更贴近调度热点路径。

## 7. 验证方式

启动 Java 服务后，可在 MySQL 中执行：

```sql
show index from ai_task_action;
show index from ai_task_action_execution;
```

建议重点确认：

```text
idx_task_action_status_next_run_epoch
idx_task_action_status_lock_next_run
idx_task_action_plan_status
uk_task_action_execution_idempotency_key
```

## 8. 后续建议

后续如果引入正式 migration 工具，建议将这些启动时 DDL 迁移到：

```text
Flyway / Liquibase / 独立 SQL migration
```

原因：

```text
1. 生产环境更容易审计 DDL。
2. 可以控制 DDL 执行窗口。
3. 可以先清理历史重复数据，再创建唯一约束。
4. 可以按环境逐步灰度上线。
```
