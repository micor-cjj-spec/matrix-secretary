# Task Center 历史数据回填说明

本文档记录任务中心当前启动阶段执行的轻量历史数据回填逻辑。

## 1. 位置

回填逻辑位于：

```text
java-service/src/main/java/com/kailei/demo/config/TaskCenterSchemaInitializer.java
```

启动流程：

```text
1. ensureIndexes()
2. backfillTaskActionScheduleAndIdempotencyFields()
```

## 2. 回填目标

当前回填 `ai_task_action` 的两个字段：

```text
next_run_at_epoch_ms
idempotency_key
```

原因：

```text
1. 老数据可能只有 next_run_at 字符串，没有 next_run_at_epoch_ms。
2. 老数据可能没有 idempotency_key，无法进入执行器幂等链路。
```

## 3. 回填规则

### 3.1 next_run_at_epoch_ms

条件：

```sql
next_run_at_epoch_ms is null
and next_run_at is not null
and next_run_at <> ''
```

回填方式：

```text
OffsetDateTime.parse(next_run_at).toInstant().toEpochMilli()
```

如果 `next_run_at` 无法解析，则保持 `null`，不阻断启动。

### 3.2 idempotency_key

条件：

```sql
idempotency_key is null or idempotency_key = ''
```

回填格式：

```text
planId:actionId:triggerCount
```

如果 `triggerCount` 为空，默认使用 `0`。

## 4. 批次控制

当前配置：

```java
private static final int BACKFILL_BATCH_SIZE = 500;
private static final int BACKFILL_MAX_ROUNDS = 20;
```

也就是说，单次启动最多处理：

```text
500 * 20 = 10000 条 action
```

这样可以避免本地启动时一次性扫太多旧数据。

如果数据量超过 10000 条，可以多次重启，或者后续改成专门 migration job。

## 5. 安全边界

当前回填只更新缺失字段，不覆盖已有值：

```sql
set next_run_at_epoch_ms = case
        when next_run_at_epoch_ms is null then ?
        else next_run_at_epoch_ms
    end,
    idempotency_key = case
        when idempotency_key is null or idempotency_key = '' then ?
        else idempotency_key
    end
```

因此：

```text
1. 已存在 next_run_at_epoch_ms 不会被覆盖。
2. 已存在 idempotency_key 不会被覆盖。
3. 解析失败不会中断启动。
4. 数据库异常只打印 warn，不阻断本地开发。
```

## 6. 验证 SQL

查看仍未回填 epoch 的 action：

```sql
select action_id, plan_id, next_run_at
from ai_task_action
where next_run_at_epoch_ms is null
  and next_run_at is not null
  and next_run_at <> '';
```

查看仍未回填 idempotency_key 的 action：

```sql
select action_id, plan_id, trigger_count
from ai_task_action
where idempotency_key is null
   or idempotency_key = '';
```

查看回填结果：

```sql
select action_id,
       plan_id,
       next_run_at,
       next_run_at_epoch_ms,
       trigger_count,
       idempotency_key
from ai_task_action
order by plan_id, action_id
limit 50;
```

## 7. 后续建议

如果项目进入更正式的环境，建议将启动时回填迁移为：

```text
Flyway / Liquibase / 独立 migration runner
```

并增加：

```text
1. 回填执行批次记录。
2. 回填失败明细表。
3. 对无法解析 next_run_at 的数据输出人工处理清单。
4. 回填完成后移除 next_run_at 字符串兜底查询。
```
