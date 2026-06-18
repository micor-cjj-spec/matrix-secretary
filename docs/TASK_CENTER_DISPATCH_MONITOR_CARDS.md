# 任务中心调度监控卡片前端对接设计

本设计用于任务中心首页或任务中心运营页展示调度健康状态。当前仓库暂未包含独立 Vue/Vite 前端工程，因此本次先沉淀前端对接方案、接口类型、刷新策略和页面布局草案。

## 目标

前端任务中心需要快速回答四类问题：

1. 当前有没有调度卡住。
2. 当前失败和重试积压是否异常。
3. 最近调度成功/失败趋势是否健康。
4. 用户点进任务详情时，能否继续下钻到 dispatch records 和 execution logs。

## 后端接口

### 调度指标摘要

```http
GET /api/ai-task/dispatch/metrics/summary
```

返回：

```ts
export interface TaskDispatchMetricsSummary {
  runningCurrent: number
  failedCurrent: number
  retryScheduledCurrent: number
  retryExhaustedCurrent: number
  startedTotal: number
  succeededTotal: number
  failedTotal: number
  retryStartedTotal: number
  timeoutRecoveredTotal: number
  generatedAt: string
}
```

字段来源：

| 字段 | 来源 | 前端用途 |
|---|---|---|
| `runningCurrent` | DB 当前 count | 判断是否有 RUNNING 卡住风险 |
| `failedCurrent` | DB 当前 count | 失败积压卡片 |
| `retryScheduledCurrent` | DB 当前 count | 等待重试卡片 |
| `retryExhaustedCurrent` | DB 当前 count | 需要人工处理卡片 |
| `startedTotal` | 当前进程 Counter | 调度启动累计值 |
| `succeededTotal` | 当前进程 Counter | 成功累计值 |
| `failedTotal` | 当前进程 Counter | 失败累计值 |
| `retryStartedTotal` | 当前进程 Counter | 自动重试拉起累计值 |
| `timeoutRecoveredTotal` | 当前进程 Counter | RUNNING 超时恢复累计值 |
| `generatedAt` | 应用时间 | 展示数据刷新时间 |

> 注意：Counter 是当前 Java 进程内累计值，应用重启后会从 0 开始；长期趋势仍以 Prometheus/Grafana 为准。

### 全局 Dispatch records 查询

```http
GET /api/ai-task/dispatch-records?status=FAILED&page=1&size=20
GET /api/ai-task/dispatch-records?status=RUNNING&sortField=startedAt&sortDirection=ASC&page=1&size=20
GET /api/ai-task/dispatch-records?status=FAILED&timeField=finishedAt&startTime=2026-06-18T00:00:00%2B08:00&sortField=finishedAt&sortDirection=DESC&page=1&size=20
GET /api/ai-task/dispatch-records?retryExhausted=true&page=1&size=20
GET /api/ai-task/dispatch-records?retryDue=true&sortField=nextRetryAt&sortDirection=ASC&page=1&size=20
GET /api/ai-task/dispatch-records?planId={planId}&actionId={actionId}&page=1&size=20
```

用于任务中心监控页展示失败列表、RUNNING 列表、重试耗尽列表、已到期重试列表，也可复用到任务详情页和 action 详情页。

### 任务详情聚合

```http
GET /api/ai-task/{planId}/detail?userId=xxx&recentLogSize=20&recentDispatchSize=20
```

用于用户从监控卡片下钻到任务详情。

### 兼容的 Plan 维度 Dispatch records 查询

```http
GET /api/ai-task/{planId}/dispatch-records?status=FAILED&page=1&size=20
```

旧页面仍可使用；新页面建议优先使用统一全局接口并传入 `planId/actionId`。

## 页面布局草案

### Row 1：调度健康总览

| 卡片 | 指标 | 展示规则 |
|---|---|---|
| 当前 RUNNING | `runningCurrent` | `0` 正常；`>0` 黄色提醒；持续高位需要告警 |
| 当前 FAILED | `failedCurrent` | `0` 正常；`>0` 黄色；`>20` 红色 |
| 等待重试 | `retryScheduledCurrent` | 显示自动恢复能力是否在工作 |
| 重试耗尽 | `retryExhaustedCurrent` | `>0` 红色，提示人工介入 |

### Row 2：累计趋势摘要

| 卡片 | 指标 | 展示规则 |
|---|---|---|
| 启动累计 | `startedTotal` | 当前进程累计启动次数 |
| 成功累计 | `succeededTotal` | 当前进程累计成功次数 |
| 失败累计 | `failedTotal` | 当前进程累计失败次数 |
| 超时恢复累计 | `timeoutRecoveredTotal` | 当前进程 RUNNING 超时恢复次数 |

### Row 3：派生指标

前端可基于摘要字段计算：

```ts
const terminalTotal = summary.succeededTotal + summary.failedTotal
const successRate = terminalTotal <= 0 ? 0 : summary.succeededTotal / terminalTotal
const failureRate = terminalTotal <= 0 ? 0 : summary.failedTotal / terminalTotal
const backlogTotal = summary.runningCurrent + summary.failedCurrent
```

建议展示：

| 派生指标 | 公式 | 含义 |
|---|---|---|
| 成功率 | `succeededTotal / (succeededTotal + failedTotal)` | 当前进程生命周期调度成功率 |
| 失败率 | `failedTotal / (succeededTotal + failedTotal)` | 当前进程生命周期调度失败率 |
| 总积压 | `runningCurrent + failedCurrent` | 当前需要关注的调度记录数量 |

## 状态颜色建议

| 指标 | 正常 | 警告 | 严重 |
|---|---:|---:|---:|
| `runningCurrent` | `0` | `1~10` | `>10` |
| `failedCurrent` | `0` | `1~20` | `>20` |
| `retryScheduledCurrent` | 任意值展示为信息态 | - | - |
| `retryExhaustedCurrent` | `0` | - | `>0` |
| `timeoutRecoveredTotal` | `0` | `>0` | 持续增加 |

## Vue 对接草案

### API Client

```ts
import axios from 'axios'

export interface TaskDispatchMetricsSummary {
  runningCurrent: number
  failedCurrent: number
  retryScheduledCurrent: number
  retryExhaustedCurrent: number
  startedTotal: number
  succeededTotal: number
  failedTotal: number
  retryStartedTotal: number
  timeoutRecoveredTotal: number
  generatedAt: string
}

export type DispatchRecordTimeField = 'triggerAt' | 'startedAt' | 'finishedAt'
export type DispatchRecordSortField = 'triggerAt' | 'startedAt' | 'finishedAt' | 'createdAt' | 'nextRetryAt'
export type SortDirection = 'ASC' | 'DESC'

export interface DispatchRecordQuery {
  planId?: string
  actionId?: string
  status?: 'RUNNING' | 'SUCCEEDED' | 'FAILED'
  timeField?: DispatchRecordTimeField
  startTime?: string
  endTime?: string
  retryExhausted?: boolean
  retryDue?: boolean
  sortField?: DispatchRecordSortField
  sortDirection?: SortDirection
  page?: number
  size?: number
}

export async function fetchDispatchMetricsSummary() {
  const { data } = await axios.get<TaskDispatchMetricsSummary>('/api/ai-task/dispatch/metrics/summary')
  return data
}

export async function fetchDispatchRecords(query: DispatchRecordQuery) {
  const { data } = await axios.get('/api/ai-task/dispatch-records', { params: query })
  return data
}
```

### Composable

```ts
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { fetchDispatchMetricsSummary, type TaskDispatchMetricsSummary } from './taskDispatchApi'

export function useDispatchMetricsSummary(refreshIntervalMs = 15000) {
  const data = ref<TaskDispatchMetricsSummary | null>(null)
  const loading = ref(false)
  const error = ref<string | null>(null)
  let timer: number | undefined

  const terminalTotal = computed(() => {
    if (!data.value) return 0
    return data.value.succeededTotal + data.value.failedTotal
  })

  const successRate = computed(() => {
    if (!data.value || terminalTotal.value <= 0) return 0
    return data.value.succeededTotal / terminalTotal.value
  })

  const backlogTotal = computed(() => {
    if (!data.value) return 0
    return data.value.runningCurrent + data.value.failedCurrent
  })

  async function refresh() {
    loading.value = true
    error.value = null
    try {
      data.value = await fetchDispatchMetricsSummary()
    } catch (e) {
      error.value = e instanceof Error ? e.message : '调度指标加载失败'
    } finally {
      loading.value = false
    }
  }

  onMounted(() => {
    refresh()
    timer = window.setInterval(refresh, refreshIntervalMs)
  })

  onUnmounted(() => {
    if (timer) window.clearInterval(timer)
  })

  return {
    data,
    loading,
    error,
    successRate,
    backlogTotal,
    refresh
  }
}
```

## 卡片组件草案

```vue
<template>
  <section class="dispatch-monitor-panel">
    <MetricCard title="当前 RUNNING" :value="summary?.runningCurrent ?? 0" />
    <MetricCard title="当前 FAILED" :value="summary?.failedCurrent ?? 0" danger-when-positive />
    <MetricCard title="等待重试" :value="summary?.retryScheduledCurrent ?? 0" />
    <MetricCard title="重试耗尽" :value="summary?.retryExhaustedCurrent ?? 0" danger-when-positive />
  </section>
</template>
```

## 交互建议

1. 卡片每 15 秒刷新一次，避免过高频率触发 DB count 查询。
2. 点击 `FAILED` 卡片跳转到失败调度记录列表。
3. 点击 `重试耗尽` 卡片跳转到需要人工处理的失败记录列表。
4. 如果接口失败，卡片保留上一次数据，同时显示弱提示。
5. 页面顶部展示 `generatedAt`，说明数据生成时间。

## 下钻路径

| 用户点击 | 跳转建议 |
|---|---|
| RUNNING 当前值 | `/api/ai-task/dispatch-records?status=RUNNING&page=1&size=20` |
| 最早卡住 RUNNING | `/api/ai-task/dispatch-records?status=RUNNING&sortField=startedAt&sortDirection=ASC&page=1&size=20` |
| 最近开始执行的 RUNNING | `/api/ai-task/dispatch-records?status=RUNNING&timeField=startedAt&startTime=...&sortField=startedAt&sortDirection=DESC&page=1&size=20` |
| FAILED 当前值 | `/api/ai-task/dispatch-records?status=FAILED&page=1&size=20` |
| 最近完成的 FAILED | `/api/ai-task/dispatch-records?status=FAILED&timeField=finishedAt&startTime=...&sortField=finishedAt&sortDirection=DESC&page=1&size=20` |
| 等待重试 | `/api/ai-task/dispatch-records?retryExhausted=false&sortField=nextRetryAt&sortDirection=ASC&page=1&size=20` |
| 已到期重试 | `/api/ai-task/dispatch-records?retryDue=true&sortField=nextRetryAt&sortDirection=ASC&page=1&size=20` |
| 未到期重试 | `/api/ai-task/dispatch-records?retryDue=false&sortField=nextRetryAt&sortDirection=ASC&page=1&size=20` |
| 重试耗尽 | `/api/ai-task/dispatch-records?retryExhausted=true&page=1&size=20` |
| 某条 dispatch record | 任务详情页 `/api/ai-task/{planId}/detail` |
| 某个 plan 详情列表 | `/api/ai-task/dispatch-records?planId={planId}&page=1&size=20` |
| 某个 action 详情列表 | `/api/ai-task/dispatch-records?planId={planId}&actionId={actionId}&page=1&size=20` |

## 当前边界

1. 后端摘要接口和全局 dispatch records 接口暂未内置权限控制，正式接入前端管理页时应加管理端鉴权。
2. 多实例部署时，Counter 是单实例当前进程值；全局累计趋势应从 Prometheus 获取。
3. 当前接口不返回耗时 p95/p99，耗时类面板仍建议读取 Prometheus。
4. 当前仓库没有前端工程，本设计是接口对接草案，后续接入实际 Vue 项目时再落地组件。

## 后续建议

1. 增加真正的前端工程实现：`DispatchMonitorCards.vue`。
2. 增加失败调度记录列表页。
3. 增加从监控卡片到任务详情页的路由跳转。
4. 管理端对接 Prometheus 查询接口，展示 p95/p99 和时间序列趋势。
