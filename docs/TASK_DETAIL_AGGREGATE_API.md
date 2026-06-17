# 任务详情聚合接口说明

本次新增任务详情聚合接口，用于前端打开任务详情页时一次性拿到核心数据，减少多接口请求。

## 接口

```http
GET /api/ai-task/{planId}/detail?userId=xxx&recentLogSize=20&recentDispatchSize=20
```

## 返回结构

```java
TaskDetailResponse
```

字段：

| 字段 | 说明 |
|---|---|
| `plan` | 任务计划详情，包含 actions |
| `recentExecutionLogs` | 最近执行日志 |
| `recentDispatchRecords` | 最近调度记录 DTO |
| `dispatchSummary` | 调度状态摘要 |

## dispatchSummary

```java
TaskDispatchSummaryResponse(
    total,
    running,
    succeeded,
    failed
)
```

说明：

| 字段 | 说明 |
|---|---|
| `total` | 当前 plan 下全部 dispatch records 数量 |
| `running` | RUNNING 数量 |
| `succeeded` | SUCCEEDED 数量 |
| `failed` | FAILED 数量 |

## 参数

| 参数 | 默认值 | 说明 |
|---|---:|---|
| `userId` | 空 | 复用任务计划访问控制 |
| `recentLogSize` | 20 | 最近执行日志数量，最大 100 |
| `recentDispatchSize` | 20 | 最近调度记录数量，受 PageResult 最大 size 约束 |

## 数据来源

```text
plan -> AiTaskService.get(planId, userId)
recentExecutionLogs -> TaskExecutionLogRepository.findRecentByPlanId(planId, recentLogSize)
recentDispatchRecords -> TaskDispatchRecordRepository.findByPlanId(planId, page=1, size=recentDispatchSize)
dispatchSummary -> TaskDispatchRecordRepository.summarizeByPlanId(planId)
```

## 访问控制

接口第一步会调用：

```java
aiTaskService.get(planId, userId)
```

因此传入 `userId` 时，沿用任务计划已有用户隔离逻辑。

## 当前边界

1. `recentExecutionLogs` 当前仍直接返回 `TaskExecutionLogEntity`，后续可以继续 DTO 化。
2. `dispatchSummary` 当前只统计 total/running/succeeded/failed，暂未统计 retry exhausted、retry scheduled 等细分状态。
3. 聚合接口默认只拿最近 N 条日志和调度记录，不替代完整分页查询接口。

## 下一步

继续生产化建议：

1. 为 execution log 增加 DTO。
2. 扩展 dispatchSummary，增加 retryScheduled、retryExhausted、successRate。
3. 在详情页展示最近一次 dispatch record 摘要。
4. 增加 Prometheus 指标和管理端统计接口。
