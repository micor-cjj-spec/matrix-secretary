# 调度指标说明

本地调度链路使用 Micrometer 指标，并通过 Actuator Prometheus endpoint 暴露。

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
    distribution:
      percentiles-histogram:
        task.dispatch.execution.duration: true
      percentiles:
        task.dispatch.execution.duration: 0.95,0.99
```

依赖：

```xml
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

## Counter 指标

| 指标 | 说明 |
|---|---|
| `task_dispatch_started_total` | dispatch record 成功进入 RUNNING 的次数 |
| `task_dispatch_succeeded_total` | dispatch record 标记为 SUCCEEDED 的次数 |
| `task_dispatch_failed_total` | dispatch record 标记为 FAILED 的次数 |
| `task_dispatch_retry_started_total` | FAILED dispatch record 被重新拉起为 RUNNING 的次数 |
| `task_dispatch_timeout_recovered_total` | RUNNING 超时恢复为 FAILED 的记录数 |

## Gauge 指标

| 指标 | 说明 |
|---|---|
| `task_dispatch_running_current` | 当前 RUNNING dispatch record 数量 |
| `task_dispatch_failed_current` | 当前 FAILED dispatch record 数量 |
| `task_dispatch_retry_scheduled_current` | 当前 FAILED 且仍有重试额度的记录数量 |
| `task_dispatch_retry_exhausted_current` | 当前 FAILED 且已耗尽重试次数的记录数量 |

Gauge 每次 scrape 时读取数据库当前值，不依赖进程内缓存。

## Timer 指标

| 指标 | 标签 | 说明 |
|---|---|---|
| `task_dispatch_execution_duration` | `result=succeeded` | dispatch 成功完成耗时 |
| `task_dispatch_execution_duration` | `result=failed` | dispatch 失败或异常耗时 |
| `task_dispatch_execution_duration` | `result=unknown` | dispatch 已进入 RUNNING，但未明确更新成成功或失败时的耗时 |

在 Prometheus 中，Micrometer Timer 通常会暴露为：

```text
task_dispatch_execution_duration_seconds_count{result="succeeded"}
task_dispatch_execution_duration_seconds_sum{result="succeeded"}
task_dispatch_execution_duration_seconds_max{result="succeeded"}
task_dispatch_execution_duration_seconds_bucket{result="succeeded",le="..."}
```

Histogram 已开启后，可以查询 p95/p99：

```promql
histogram_quantile(
  0.95,
  sum(rate(task_dispatch_execution_duration_seconds_bucket[10m])) by (le, result)
)
```

## 打点位置

| 指标 | 打点位置 |
|---|---|
| `task_dispatch_started_total` | `tryStartOrRetry(...)` 成功后 |
| `task_dispatch_retry_started_total` | 已存在 dispatch record 且 `tryStartOrRetry(...)` 成功后 |
| `task_dispatch_succeeded_total` | `markSucceeded(...)` 成功后 |
| `task_dispatch_failed_total` | `markFailed(...)` 成功后，以及 RUNNING 超时恢复后 |
| `task_dispatch_timeout_recovered_total` | `recoverTimedOutDispatchRecords(...)` 恢复成功后 |
| `task_dispatch_execution_duration{result="succeeded"}` | dispatch 成功标记 SUCCEEDED 后 |
| `task_dispatch_execution_duration{result="failed"}` | dispatch 失败、异常或标记 FAILED 后 |
| `task_dispatch_execution_duration{result="unknown"}` | dispatch 已启动但未明确落入成功/失败分支时 |

## 当前边界

1. Counter/Gauge 仍是全局指标，没有按 skill、actionType、owner 做标签，避免早期标签基数失控。
2. Timer 只增加了低基数 `result` 标签，不包含 `planId/actionId`。
3. `task_dispatch_retry_started_total` 通过“已有 dispatch record 且成功重新进入 RUNNING”判断，表示 FAILED 重试拉起，不包含首次启动。
4. `task_dispatch_failed_total` 包含执行失败和 RUNNING 超时恢复两类失败。
5. Gauge 会触发数据库 count 查询，适合低频 Prometheus scrape，不建议高频压测式拉取。
6. Timer 只覆盖调度入口，不覆盖手动立即执行或用户手动 retry action。

## 下一步

继续生产化建议：

1. 增加按 skill/actionType 维度的执行耗时统计，注意控制标签基数。
2. 在管理端或 Grafana 面板展示调度成功率、失败趋势、超时恢复趋势。
3. 增加告警规则，例如 FAILED 当前存量持续升高、RUNNING 当前存量长时间不下降。
4. 后续如需按 owner 拆分指标，先限制 owner 枚举，避免标签基数失控。
