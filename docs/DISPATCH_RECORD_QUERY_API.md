# Dispatch Record 查询接口说明

本次改造为任务中心增加 dispatch record 查询接口，用于任务详情页展示调度执行状态、重试信息和错误信息。

## 查询计划下所有调度记录

```http
GET /api/ai-task/{planId}/dispatch-records?page=1&size=20&userId=xxx
```

返回：

```java
PageResult<TaskDispatchRecordEntity>
```

排序：

```text
trigger_at DESC, created_at DESC
```

## 查询某个 action 的调度记录

```http
GET /api/ai-task/{planId}/actions/{actionId}/dispatch-records?page=1&size=20&userId=xxx
```

返回：

```java
PageResult<TaskDispatchRecordEntity>
```

排序：

```text
trigger_at DESC, created_at DESC
```

## 可展示字段

前端任务详情页建议展示：

| 字段 | 说明 |
|---|---|
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

## 访问控制

两个接口都会先调用：

```java
aiTaskService.get(planId, userId)
```

因此如果传入 `userId`，会复用任务计划已有的用户隔离逻辑。

## Repository 能力

```java
findByPlanId(planId, page, size)
findByPlanIdAndActionId(planId, actionId, page, size)
```

## 当前边界

1. 当前返回实体对象，后续可以改成更稳定的 DTO，避免直接暴露内部表结构。
2. 当前只支持按 plan/action 查询，暂未支持按状态、时间范围、owner 过滤。
3. 当前没有单独的调度记录详情接口。

## 下一步

继续生产化建议：

1. 增加 DTO，屏蔽内部字段。
2. 支持 `status`、`startTime`、`endTime`、`dispatchOwner` 查询条件。
3. 在任务详情页展示最近一次 dispatch record 摘要。
4. 增加调度指标接口或 Prometheus 指标。
