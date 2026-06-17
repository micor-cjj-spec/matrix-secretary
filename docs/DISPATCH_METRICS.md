# 调度指标说明

本次为本地调度链路增加 Micrometer 指标，并通过 Actuator Prometheus endpoint 暴露。

## 暴露端点

```http
GET /actuator/prometheus
```

配置：

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus
  endpoint:
    prometheus:
      enabled: true
  metrics:
    tags:
      application: ${spring.application.name}
```

依赖：

```xml
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

## 新增指标

| 指标 | 说明 |
|---|---|
| `task_dispatch_started_total` | dispatch record 成功进入 RUNNING 的次数 |
| `task_dispatch_succeeded_total` | dispatch record 标记为 SUCCEEDED 的次数 |
| `task_dispatch_failed_total` | dispatch record 标记为 FAILED 的次数 |
| `task_dispatch_retry_started_total` | FAILED dispatch record 被重新拉起为 RUNNING 的次数 |
| `task_dispatch_timeout_recovered_total` | RUNNING 超时恢复为 FAILED 的记录数 |

## 打点位置

| 指标 | 打点位置 |
|---|---|
| `task_dispatch_started_total` | `tryStartOrRetry(...)` 成功后 |
| `task_dispatch_retry_started_total` | 已存在 dispatch record 且 `tryStartOrRetry(...)` 成功后 |
| `task_dispatch_succeeded_total` | `markSucceeded(...)` 成功后 |
| `task_dispatch_failed_total` | `markFailed(...)` 成功后，以及 RUNNING 超时恢复后 |
| `task_dispatch_timeout_recovered_total` | `recoverTimedOutDispatchRecords(...)` 恢复成功后 |

## 当前边界

1. 当前指标是全局 Counter，没有按 skill、actionType、owner 做标签，避免早期标签基数失控。
2. `task_dispatch_retry_started_total` 通过“已有 dispatch record 且成功重新进入 RUNNING”判断，表示 FAILED 重试拉起，不包含首次启动。
3. `task_dispatch_failed_total` 包含执行失败和 RUNNING 超时恢复两类失败。
4. 当前还没有 Gauge，例如 RUNNING 当前存量、FAILED 当前存量，这些可以后续从 Repository summary 或定时采样补充。

## 下一步

继续生产化建议：

1. 增加当前存量 Gauge：running / failed / retryScheduled / retryExhausted。
2. 对关键指标增加有限标签，例如 `owner` 或 `result`，但不要直接把 `planId/actionId` 放入标签。
3. 增加 Timer，统计 dispatch 执行耗时。
4. 在管理端或 Grafana 面板展示调度成功率、失败趋势、超时恢复趋势。
