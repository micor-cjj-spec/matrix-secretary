# 调度监控告警与 Grafana 面板草案

本文件基于当前调度指标设计 Prometheus 告警规则和 Grafana 面板草案。对应规则模板见：

```text
docs/monitoring/dispatch-alert-rules.yml
```

## 指标来源

当前服务通过 Actuator 暴露 Prometheus 指标：

```http
GET /actuator/prometheus
```

调度相关指标：

| 类型 | 指标 |
|---|---|
| Counter | `task_dispatch_started_total` |
| Counter | `task_dispatch_succeeded_total` |
| Counter | `task_dispatch_failed_total` |
| Counter | `task_dispatch_retry_started_total` |
| Counter | `task_dispatch_timeout_recovered_total` |
| Gauge | `task_dispatch_running_current` |
| Gauge | `task_dispatch_failed_current` |
| Gauge | `task_dispatch_retry_scheduled_current` |
| Gauge | `task_dispatch_retry_exhausted_current` |
| Timer | `task_dispatch_execution_duration` |

> 注意：Micrometer Timer 在 Prometheus 中会展开为 `_seconds_count`、`_seconds_sum`、`_seconds_max`，若后续启用 histogram，还会有 `_seconds_bucket`。

## 告警规则

### 1. FAILED 存量过高

```promql
task_dispatch_failed_current > 20
```

建议：

```text
for: 10m
severity: warning
```

排查方向：

1. 查看任务详情聚合接口的 `dispatchSummary.failed`。
2. 查询 `/dispatch-records?status=FAILED`。
3. 查看 `errorMessage` 和 execution logs。
4. 检查外部 skill 依赖，例如邮件、飞书、HTTP 回调。

### 2. RUNNING 长时间不下降

```promql
task_dispatch_running_current > 0
```

建议：

```text
for: 10m
severity: warning
```

排查方向：

1. 检查本地调度器是否仍在运行。
2. 检查 RUNNING 超时恢复是否生效。
3. 检查数据库连接和 `markSucceeded/markFailed` 更新是否异常。
4. 检查是否有长耗时 skill。

### 3. 重试耗尽

```promql
task_dispatch_retry_exhausted_current > 0
```

建议：

```text
for: 5m
severity: critical
```

含义：任务已经失败且 `retryCount >= maxRetryCount`，不会再自动拉起。

### 4. 发生 RUNNING 超时恢复

```promql
increase(task_dispatch_timeout_recovered_total[15m]) > 0
```

建议：

```text
for: 0m
severity: warning
```

含义：曾有任务从 RUNNING 超时恢复到 FAILED。重点关注 JVM 崩溃、服务重启、数据库异常、执行卡死。

### 5. 失败率过高

```promql
(
  sum(rate(task_dispatch_failed_total[10m]))
  /
  clamp_min(sum(rate(task_dispatch_succeeded_total[10m])) + sum(rate(task_dispatch_failed_total[10m])), 1)
) > 0.2
```

建议：

```text
for: 10m
severity: warning
```

说明：最近 10 分钟失败率超过 20%。

### 6. 执行耗时 p95 过高

如果 Timer 开启 histogram，可使用：

```promql
histogram_quantile(
  0.95,
  sum(rate(task_dispatch_execution_duration_seconds_bucket[10m])) by (le)
) > 30
```

如果暂未开启 histogram，可先用平均耗时：

```promql
rate(task_dispatch_execution_duration_seconds_sum[10m])
/
clamp_min(rate(task_dispatch_execution_duration_seconds_count[10m]), 1)
```

## Grafana 面板草案

### Row 1：调度总览

| Panel | 类型 | PromQL |
|---|---|---|
| Started rate | Time series | `sum(rate(task_dispatch_started_total[5m]))` |
| Succeeded rate | Time series | `sum(rate(task_dispatch_succeeded_total[5m]))` |
| Failed rate | Time series | `sum(rate(task_dispatch_failed_total[5m]))` |
| Retry started rate | Time series | `sum(rate(task_dispatch_retry_started_total[5m]))` |

### Row 2：当前积压

| Panel | 类型 | PromQL |
|---|---|---|
| RUNNING current | Stat | `task_dispatch_running_current` |
| FAILED current | Stat | `task_dispatch_failed_current` |
| Retry scheduled current | Stat | `task_dispatch_retry_scheduled_current` |
| Retry exhausted current | Stat | `task_dispatch_retry_exhausted_current` |

### Row 3：成功率与失败率

| Panel | 类型 | PromQL |
|---|---|---|
| Success rate | Time series | `sum(rate(task_dispatch_succeeded_total[10m])) / clamp_min(sum(rate(task_dispatch_succeeded_total[10m])) + sum(rate(task_dispatch_failed_total[10m])), 1)` |
| Failure rate | Time series | `sum(rate(task_dispatch_failed_total[10m])) / clamp_min(sum(rate(task_dispatch_succeeded_total[10m])) + sum(rate(task_dispatch_failed_total[10m])), 1)` |
| Timeout recovery trend | Time series | `increase(task_dispatch_timeout_recovered_total[15m])` |

### Row 4：执行耗时

| Panel | 类型 | PromQL |
|---|---|---|
| Avg duration | Time series | `rate(task_dispatch_execution_duration_seconds_sum[10m]) / clamp_min(rate(task_dispatch_execution_duration_seconds_count[10m]), 1)` |
| Max duration | Time series | `task_dispatch_execution_duration_seconds_max` |
| P95 duration | Time series | `histogram_quantile(0.95, sum(rate(task_dispatch_execution_duration_seconds_bucket[10m])) by (le))` |

> 如果没有 `_bucket` 指标，说明 Timer histogram 暂未启用，P95 面板先隐藏或改用平均耗时。

## 建议阈值

| 指标 | 初始阈值 | 说明 |
|---|---:|---|
| FAILED current | `> 20 for 10m` | 早期先用保守值，避免误报 |
| RUNNING current | `> 0 for 10m` | 当前调度通常应该较快结束 |
| Retry exhausted | `> 0 for 5m` | 需要人工介入 |
| Timeout recovered | `increase > 0 in 15m` | 任意发生都值得关注 |
| Failure rate | `> 20% for 10m` | 需要结合实际业务量调整 |
| Avg duration | `> 10s for 10m` | 根据 skill 类型调整 |
| P95 duration | `> 30s for 10m` | 长耗时 skill 可单独放宽 |

## 当前边界

1. 当前调度指标没有 `skillName/actionType/owner` 标签，无法直接按技能维度拆分。
2. Gauge 每次 scrape 会执行数据库 count 查询，建议 Prometheus scrape interval 不低于 15s。
3. p95 依赖 Timer histogram；如果未开启 histogram，Grafana 中先使用平均耗时和 max。
4. 当前指标覆盖本地调度路径，不覆盖手动立即执行路径。

## 后续建议

1. 给 Timer 增加有限标签，例如 `result=success|failed`。
2. 开启 percentile histogram 支持 p95/p99。
3. 增加 skill/actionType 维度指标，但必须限制枚举值，避免标签基数失控。
4. 将告警规则加入部署目录，由环境级 Prometheus 加载。
