# 调度扫描分页改造说明

本次改造将本地调度器从一次性全量加载任务计划，调整为按页扫描任务计划。

## 背景

原逻辑：

```java
repository.findAll().forEach(plan -> ...)
```

问题：

- 任务计划数量增长后，会一次性加载所有 `TaskPlan`。
- 每个 `TaskPlan` 还会继续加载对应 `TaskAction`，内存压力会被放大。
- 后续接入多实例、锁、幂等之前，应先让扫描过程具备批处理边界。

## 改造内容

### 调度器配置

```yaml
ai-secretary:
  local-scheduler:
    enabled: true
    dispatch-page-size: 50
```

也可以通过环境变量覆盖：

```bash
AI_SECRETARY_DISPATCH_PAGE_SIZE=50
```

### 服务层行为

`AiTaskService` 新增：

```java
public void dispatchDueOnceTasks(Long pageSize)
```

扫描逻辑：

1. 归一化 `pageSize`。
2. 从第 1 页开始读取任务计划。
3. 每页逐个 plan 检查 due action。
4. 只有 action 状态发生变化时才保存 plan。
5. 直到当前页为空，或已经扫描到最后一页。

## 当前边界

本次只解决“全量加载”问题，不改变调度判定语义：

- 仍然扫描所有 plan。
- 仍然在内存中判断 action 是否 `SCHEDULED`。
- 仍然读取 action 的 `scheduleJson` 并解析 `effectiveRunAt`。
- 尚未增加数据库锁和幂等键。
- 尚未支持多实例并发安全。

## 下一步

生产级调度应继续推进：

1. 在 `ai_task_action` 增加可索引字段：`next_run_at`、`status`。
2. Repository 增加到期动作查询：`status = SCHEDULED AND next_run_at <= now`。
3. 引入 dispatch lock 或 lease 字段，避免多实例重复执行。
4. 增加幂等键：`planId + actionId + triggerAt`。
5. 增加最大重试次数、失败退避、超时恢复。
