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
| `recentExecutionLogs` | 最近执行日志 DTO |
| `recentDispatchRecords` | 最近调度记录 DTO |
| `dispatchSummary` | 调度状态摘要 |

## recentExecutionLogs

`recentExecutionLogs` 返回 `TaskExecutionLogResponse` 列表。

| 字段 | 说明 |
|---|---|
| `id` | 执行日志 ID |
| `planId` | 任务计划 ID |
| `actionId` | 任务动作 ID |
| `skillName` | 技能名称 |
| `status` | 执行状态 |
| `requestPayload` | 请求载荷 JSON |
| `responsePayload` | 响应载荷 JSON |
| `errorMessage` | 错误信息 |
| `operatorUserId` | 操作用户 ID |
| `createdAt` | 创建时间 |

## dispatchSummary

```java
TaskDispatchSummaryResponse(
    total,
    running,
    succeeded,
    failed,
    retryScheduled,
    retryExhausted,
    successRate,
    latestStatus,
    latestTriggerAt,
    latestFinishedAt
)
```

说明：

| 字段 | 说明 |
|---|---|
| `total` | 当前 plan 下全部 dispatch records 数量 |
| `running` | RUNNING 数量 |
| `succeeded` | SUCCEEDED 数量 |
| `failed` | FAILED 数量 |
| `retryScheduled` | FAILED 且还有重试额度的数量 |
| `retryExhausted` | FAILED 且重试次数已达到上限的数量 |
| `successRate` | 成功率百分比，等于 `succeeded / total * 100` |
| `latestStatus` | 最近一次 dispatch record 状态 |
| `latestTriggerAt` | 最近一次 dispatch record 触发时间 |
| `latestFinishedAt` | 最近一次 dispatch record 结束时间 |

## 参数

| 参数 | 默认值 | 说明 |
|---|---:|---|
| `userId` | 空 | 复用任务计划访问控制 |
| `recentLogSize` | 20 | 最近执行日志数量，最大 100 |
| `recentDispatchSize` | 20 | 最近调度记录数量，受 PageResult 最大 size 约束 |

## 数据来源

```text
plan -> AiTaskService.get(planId, userId)
recentExecutionLogs -> TaskExecutionLogRepository.findRecentByPlanId(planId, recentLogSize) -> TaskExecutionLogResponse
recentDispatchRecords -> TaskDispatchRecordRepository.findByPlanId(planId, page=1, size=recentDispatchSize) -> TaskDispatchRecordResponse
dispatchSummary -> TaskDispatchRecordRepository.summarizeByPlanId(planId)
```

## 访问控制

接口第一步会调用：

```java
aiTaskService.get(planId, userId)
```

因此传入 `userId` 时，沿用任务计划已有用户隔离逻辑。

## 相关日志接口

以下接口也已经返回 DTO：

```http
GET /api/ai-task/{planId}/logs
GET /api/ai-task/{planId}/actions/{actionId}/logs
```

返回：

```java
List<TaskExecutionLogResponse>
```

## 当前边界

1. `requestPayload` 和 `responsePayload` 当前仍以 JSON 字符串返回，后续可以改为结构化 JSON 对象。
2. `retryScheduled` 当前统计 FAILED 且还有重试额度的记录，不区分 `nextRetryAt` 是否已经到期。
3. `latestStatus/latestTriggerAt/latestFinishedAt` 基于 `triggerAt DESC, createdAt DESC` 选取最近记录。
4. 聚合接口默认只拿最近 N 条日志和调度记录，不替代完整分页查询接口。

## 下一步

继续生产化建议：

1. 增加 Prometheus 指标和管理端统计接口。
2. 将 requestPayload/responsePayload 从字符串升级为结构化对象。
3. 增加 retry due / retry pending 细分指标。
4. 增加按 skill/action 维度的调度成功率统计。
