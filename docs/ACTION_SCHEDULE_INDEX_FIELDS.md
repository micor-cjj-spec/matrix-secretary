# 任务动作调度索引字段说明

本次改造为 `ai_task_action` 增加调度查询需要的冗余字段，避免后续调度器只能读取 `schedule_json` 后在内存中解析到期时间。

## 新增字段

| 字段 | 类型 | 说明 |
|---|---|---|
| `status` | varchar(32) | 已加索引，用于过滤 `SCHEDULED` 动作 |
| `next_run_at` | datetime/offset datetime | 已加索引，用于查找到期动作 |
| `last_run_at` | datetime/offset datetime | 记录上一次触发时间 |
| `trigger_count` | int | 记录累计触发次数 |

> 说明：`status` 字段此前已存在，本次补充索引。

## 回填规则

保存 `TaskActionEntity` 时，Repository 从 `TaskAction.schedule()` 派生调度列：

```java
entity.setNextRunAt(parseOffsetDateTime(schedule == null ? null : schedule.effectiveRunAt()));
entity.setLastRunAt(parseOffsetDateTime(schedule == null ? null : schedule.lastRunAt()));
entity.setTriggerCount(schedule == null ? 0 : schedule.triggerCount());
```

其中 `effectiveRunAt()` 优先取 `nextRunAt`，没有时取 `runAt`。

## 新增 Repository 查询

```java
public PageResult<TaskActionEntity> findDueScheduledActions(OffsetDateTime now, long page, long size)
```

查询语义：

```sql
status = 'SCHEDULED'
AND next_run_at IS NOT NULL
AND next_run_at <= :now
ORDER BY next_run_at ASC, sort_order ASC
```

## 当前边界

本次只把调度查询所需字段补齐，并预留到期动作查询方法；调度器仍未切换到 action-level due scan。

下一步应继续做：

1. 调度器从 `findPage(plan)` 切到 `findDueScheduledActions(now, page, size)`。
2. 根据 action 的 `planId` 加载所属 plan。
3. 只执行命中的 action，而不是遍历整个 plan 的全部 action。
4. 增加 dispatch lease / lock 字段，避免多实例重复执行。
5. 增加幂等键：`planId + actionId + triggerAt`。
