# Action-level 到期调度扫描说明

本次改造将本地调度器从“扫描任务计划，再遍历全部动作”升级为“直接查询到期动作”。

## 调度查询路径

旧路径：

```text
TaskPlan page -> TaskPlan.tasks -> in-memory due check
```

新路径：

```text
ai_task_action where status = SCHEDULED and next_run_at <= now
  -> load plan by planId
  -> execute matched actionId only
  -> save plan and actions
```

## 为什么只查第一页

到期动作执行后，动作状态或 `next_run_at` 会发生变化，该动作会从本轮 due 查询结果中移出。

因此如果使用 offset 分页：

```text
page 1 -> 执行并更新 -> page 2
```

数据库结果集会发生收缩，`page 2` 可能跳过一部分原本应该处理的动作。

当前策略是：

```text
每个 scheduler tick 只查询第一页 due actions
每次最多处理 dispatch-page-size 条
下一轮 tick 继续处理剩余 due actions
```

这样单次调度有明确上限，同时避免 offset 结果集漂移导致漏扫。

## 当前配置

```yaml
ai-secretary:
  local-scheduler:
    dispatch-page-size: 50
```

环境变量：

```bash
AI_SECRETARY_DISPATCH_PAGE_SIZE=50
```

## 当前边界

本次只完成 action-level due scan，不包含以下生产级能力：

- 多实例抢占锁
- dispatch lease
- 执行幂等键
- 超时恢复
- 失败退避
- orphan action 自动清理

## 下一步

继续生产化调度应增加：

1. `dispatch_locked_until` / `dispatch_owner` 字段。
2. 原子抢占 due action。
3. 幂等键：`planId + actionId + triggerAt`。
4. 执行完成后释放或推进下一次调度。
5. orphan action 清理任务。