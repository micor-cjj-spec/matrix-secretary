# 调度指标摘要接口说明

本次新增任务调度指标摘要接口，用于前端任务中心展示轻量监控卡片，不要求前端直接接入 Prometheus。

## 接口

```http
GET /api/ai-task/dispatch/metrics/summary
```

## 返回结构

```java
TaskDispatchMetricsSummaryResponse
```

字段：

| 字段 | 来源 | 说明 |
|---|---|---|
| `runningCurrent` | DB count | 当前 RUNNING dispatch record 数量 |
| `failedCurrent` | DB count | 当前 FAILED dispatch record 数量 |
| `retryScheduledCurrent` | DB count | 当前 FAILED 且仍有重试额度的记录数量 |
| `retryExhaustedCurrent` | DB count | 当前 FAILED 且已耗尽重试次数的记录数量 |
| `startedTotal` | Micrometer Counter | 当前进程内累计 started 次数 |
| `succeededTotal` | Micrometer Counter | 当前进程内累计 succeeded 次数 |
| `failedTotal` | Micrometer Counter | 当前进程内累计 failed 次数 |
| `retryStartedTotal` | Micrometer Counter | 当前进程内累计 retry started 次数 |
| `timeoutRecoveredTotal` | Micrometer Counter | 当前进程内累计 timeout recovered 次数 |
| `generatedAt` | 应用时间 | 接口生成时间 |

## 返回示例

```json
{
  "runningCurrent": 0,
  "failedCurrent": 2,
  "retryScheduledCurrent": 1,
  "retryExhaustedCurrent": 1,
  "startedTotal": 20.0,
  "succeededTotal": 17.0,
  "failedTotal": 3.0,
  "retryStartedTotal": 2.0,
  "timeoutRecoveredTotal": 1.0,
  "generatedAt": "2026-06-18T09:20:00+08:00"
}
```

## 设计说明

1. `runningCurrent/failedCurrent/retryScheduledCurrent/retryExhaustedCurrent` 直接查询数据库当前存量，适合前端展示当前积压状态。
2. `startedTotal/succeededTotal/failedTotal/retryStartedTotal/timeoutRecoveredTotal` 来自 Micrometer Counter，表示当前应用进程生命周期内的累计值。
3. 如果应用重启，Counter 会从 0 重新累计；长期趋势仍建议以 Prometheus 为准。
4. 该接口是管理端轻量摘要，不替代 `/actuator/prometheus`。

## 路由顺序

接口路径是：

```http
/api/ai-task/dispatch/metrics/summary
```

Controller 中该接口放在：

```http
/api/ai-task/{planId}
```

之前，避免 `dispatch` 被误匹配为 `planId`。

## 当前边界

1. 暂未做权限控制，后续接入 Security 后应限制为管理端或运维角色访问。
2. Counter 是进程内累计值，不代表全局长期累计；多实例部署时需要 Prometheus 聚合。
3. 当前接口不返回 Timer p95/p99，耗时统计仍建议走 Prometheus 查询。

## 下一步

继续生产化建议：

1. 给该接口加管理权限。
2. 增加多实例聚合方案，或前端直接读取 Prometheus 聚合结果。
3. 增加任务中心监控卡片 UI。
4. 增加按时间窗口统计的成功率、失败率。
