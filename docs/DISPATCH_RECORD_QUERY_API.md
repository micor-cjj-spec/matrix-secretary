# Dispatch Record 查询接口说明

本次改造为任务中心增加 dispatch record 查询接口，用于任务详情页展示调度执行状态、重试信息和错误信息。

## 查询计划下所有调度记录

```http
GET /api/ai-task/{planId}/dispatch-records?page=1&size=20&userId=xxx
```

支持过滤参数：

```http
GET /api/ai-task/{planId}/dispatch-records?status=FAILED&startTime=2026-06-17T00:00:00%2B08:00&endTime=2026-06-18T00:00:00%2B08:00&dispatchOwner=local-scheduler
```

返回：

```java
PageResult<TaskDispatchRecordResponse>
```

排序：

```text
trigger_at DESC, created_at DESC
```

## 查询某个 action 的调度记录

```http
GET /api/ai-task/{planId}/actions/{actionId}/dispatch-records?page=1&size=20&userId=xxx
```

同样支持：

```text
status
startTime
endTime
dispatchOwner
page
size
```

返回：

```java
PageResult<TaskDispatchRecordResponse>
```

排序：

```text
trigger_at DESC, created_at DESC
```

## 过滤语义

| 参数 | 说明 |
|---|---|
| `status` | 调度记录状态，自动转大写，例如 `running` 会按 `RUNNING` 查询 |
| `startTime` | 触发时间下界，对应 `trigger_at >= startTime` |
| `endTime` | 触发时间上界，对应 `trigger_at <= endTime` |
| `dispatchOwner` | 调度实例标识，精确匹配 |

`startTime` 和 `endTime` 使用 ISO DateTime，例如：

```text
2026-06-17T00:00:00+08:00
```

URL 中的 `+` 需要编码为 `%2B`。

## DTO 字段

接口不再直接返回数据库 Entity，而是返回 `TaskDispatchRecordResponse`。

| 字段 | 说明 |
|---|---|
| `id` | 调度记录 ID |
| `planId` | 任务计划 ID |
| `actionId` | 任务动作 ID |
| `status` | `RUNNING` / `SUCCEEDED` / `FAILED` |
| `triggerAt` | 本次触发时间 |
| `startedAt` | 开始执行时间 |
| `finishedAt` | 结束执行时间 |
| `dispatchOwner` | 调度实例标识 |
| `retryCount` | 已重试次数 |
| `maxRetryCount` | 最大重试次数 |
| `nextRetryAt` | 下一次允许重试时间 |
| `errorMessage` | 失败原因 |
| `idempotencyKey` | 幂等键，便于排查重复执行 |
| `createdAt` | 创建时间 |
| `updatedAt` | 更新时间 |

## 访问控制

两个接口都会先调用：

```java
aiTaskService.get(planId, userId)
```

因此如果传入 `userId`，会复用任务计划已有的用户隔离逻辑。

## Repository 能力

```java
findByPlanId(planId, status, startTime, endTime, dispatchOwner, page, size)
findByPlanIdAndActionId(planId, actionId, status, startTime, endTime, dispatchOwner, page, size)
```

## 当前边界

1. 当前 `startTime/endTime` 过滤的是 `trigger_at`，不是 `started_at`。
2. 当前没有单独的调度记录详情接口。
3. 当前没有汇总统计，例如成功率、失败次数、重试耗时分布。

## 下一步

继续生产化建议：

1. 在任务详情页展示最近一次 dispatch record 摘要。
2. 增加调度记录详情接口。
3. 增加调度指标接口或 Prometheus 指标。
4. 增加按 `startedAt/finishedAt` 的执行时间范围过滤。
