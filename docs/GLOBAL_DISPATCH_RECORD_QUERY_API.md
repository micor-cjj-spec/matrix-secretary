# 全局调度记录查询接口说明

全局 dispatch records 查询接口用于任务中心监控页展示失败、RUNNING、等待重试、已到期重试和重试耗尽列表。前端不需要先知道 `planId`，也可以通过可选 `planId/actionId` 参数复用同一个接口查询任务或 action 维度记录。

## 接口

```http
GET /api/ai-task/dispatch-records
```

## 查询参数

| 参数 | 类型 | 说明 |
|---|---|---|
| `planId` | string | 可选，按任务计划 ID 过滤 |
| `actionId` | string | 可选，按任务动作 ID 过滤 |
| `status` | string | 调度状态，例如 `RUNNING`、`FAILED`、`SUCCEEDED` |
| `timeField` | string | 时间过滤字段：`triggerAt`、`startedAt`、`finishedAt`；默认 `triggerAt` |
| `startTime` | OffsetDateTime | 按 `timeField >= startTime` 过滤 |
| `endTime` | OffsetDateTime | 按 `timeField <= endTime` 过滤 |
| `dispatchOwner` | string | 调度持有者，例如 `local-scheduler` |
| `retryExhausted` | boolean | `true` 查询重试耗尽记录；`false` 查询仍可重试记录 |
| `retryDue` | boolean | `true` 查询已到重试时间记录；`false` 查询未到重试时间记录 |
| `sortField` | string | 排序字段：`triggerAt`、`startedAt`、`finishedAt`、`createdAt`、`nextRetryAt`；默认 `triggerAt` |
| `sortDirection` | string | 排序方向：`ASC` 或 `DESC`；默认 `DESC` |
| `page` | long | 页码，默认走 `PageResult` 标准归一化 |
| `size` | long | 每页数量，默认走 `PageResult` 标准归一化 |

## 时间字段说明

| `timeField` | 过滤字段 | 适用场景 |
|---|---|---|
| `triggerAt` | `trigger_at` | 默认值，按任务原始触发时间查询 |
| `startedAt` | `started_at` | 查询最近开始执行、长时间 RUNNING 的记录 |
| `finishedAt` | `finished_at` | 查询最近完成、最近失败或最近成功的记录 |

非法或空 `timeField` 会回退为 `triggerAt`。

## 排序字段说明

| `sortField` | 排序字段 | 适用场景 |
|---|---|---|
| `triggerAt` | `trigger_at` | 默认排序，按任务触发时间查看 |
| `startedAt` | `started_at` | 找最早卡住的 RUNNING 记录 |
| `finishedAt` | `finished_at` | 查看最近完成、最近失败、最近成功记录 |
| `createdAt` | `created_at` | 按记录创建时间查看 |
| `nextRetryAt` | `next_retry_at` | 查看最近即将重试或最早到期重试记录 |

非法或空 `sortField` 会回退为 `triggerAt`。`sortDirection` 只有 `ASC` 会升序，其他值默认降序。

## 返回结构

```java
PageResult<TaskDispatchRecordResponse>
```

## 示例

### 查询全局 FAILED 记录

```http
GET /api/ai-task/dispatch-records?status=FAILED&page=1&size=20
```

### 查询全局 RUNNING 记录

```http
GET /api/ai-task/dispatch-records?status=RUNNING&page=1&size=20
```

### 查询某个 plan 的 dispatch records

```http
GET /api/ai-task/dispatch-records?planId=plan-123&page=1&size=20
```

### 查询某个 action 的 FAILED records

```http
GET /api/ai-task/dispatch-records?planId=plan-123&actionId=plan-123-act-1&status=FAILED&page=1&size=20
```

### 按触发时间查询失败记录

```http
GET /api/ai-task/dispatch-records?status=FAILED&timeField=triggerAt&startTime=2026-06-18T00:00:00%2B08:00&endTime=2026-06-19T00:00:00%2B08:00&page=1&size=20
```

### 按开始执行时间查询 RUNNING 记录

```http
GET /api/ai-task/dispatch-records?status=RUNNING&timeField=startedAt&startTime=2026-06-18T00:00:00%2B08:00&page=1&size=20
```

### 按开始执行时间升序查询最早卡住的 RUNNING 记录

```http
GET /api/ai-task/dispatch-records?status=RUNNING&sortField=startedAt&sortDirection=ASC&page=1&size=20
```

### 按完成时间查询最近失败记录

```http
GET /api/ai-task/dispatch-records?status=FAILED&timeField=finishedAt&startTime=2026-06-18T00:00:00%2B08:00&sortField=finishedAt&sortDirection=DESC&page=1&size=20
```

### 按下次重试时间查询最早到期重试记录

```http
GET /api/ai-task/dispatch-records?retryExhausted=false&sortField=nextRetryAt&sortDirection=ASC&page=1&size=20
```

### 查询重试耗尽记录

```http
GET /api/ai-task/dispatch-records?retryExhausted=true&page=1&size=20
```

等价于：

```sql
status = 'FAILED'
AND COALESCE(retry_count, 0) >= COALESCE(max_retry_count, 0)
```

### 查询仍可重试记录

```http
GET /api/ai-task/dispatch-records?retryExhausted=false&page=1&size=20
```

等价于：

```sql
status = 'FAILED'
AND next_retry_at IS NOT NULL
AND COALESCE(retry_count, 0) < COALESCE(max_retry_count, 0)
```

### 查询已经到期、可被重新拉起的重试记录

```http
GET /api/ai-task/dispatch-records?retryDue=true&page=1&size=20
```

等价于：

```sql
status = 'FAILED'
AND next_retry_at IS NOT NULL
AND COALESCE(retry_count, 0) < COALESCE(max_retry_count, 0)
AND next_retry_at <= NOW
```

### 查询还没到重试时间的等待记录

```http
GET /api/ai-task/dispatch-records?retryDue=false&page=1&size=20
```

等价于：

```sql
status = 'FAILED'
AND next_retry_at IS NOT NULL
AND COALESCE(retry_count, 0) < COALESCE(max_retry_count, 0)
AND next_retry_at > NOW
```

### 查询某个调度器持有者的失败记录

```http
GET /api/ai-task/dispatch-records?status=FAILED&dispatchOwner=local-scheduler&page=1&size=20
```

## 前端任务中心用法

| 场景 | 请求 |
|---|---|
| 点击 FAILED 卡片 | `/api/ai-task/dispatch-records?status=FAILED&page=1&size=20` |
| 点击 RUNNING 卡片 | `/api/ai-task/dispatch-records?status=RUNNING&page=1&size=20` |
| 查询最早卡住 RUNNING | `/api/ai-task/dispatch-records?status=RUNNING&sortField=startedAt&sortDirection=ASC&page=1&size=20` |
| 查询最近开始执行的 RUNNING | `/api/ai-task/dispatch-records?status=RUNNING&timeField=startedAt&startTime=...&sortField=startedAt&sortDirection=DESC&page=1&size=20` |
| 查询最近完成的 FAILED | `/api/ai-task/dispatch-records?status=FAILED&timeField=finishedAt&startTime=...&sortField=finishedAt&sortDirection=DESC&page=1&size=20` |
| 点击重试耗尽卡片 | `/api/ai-task/dispatch-records?retryExhausted=true&page=1&size=20` |
| 点击等待重试卡片 | `/api/ai-task/dispatch-records?retryExhausted=false&sortField=nextRetryAt&sortDirection=ASC&page=1&size=20` |
| 点击已到期重试卡片 | `/api/ai-task/dispatch-records?retryDue=true&sortField=nextRetryAt&sortDirection=ASC&page=1&size=20` |
| 点击未到期重试卡片 | `/api/ai-task/dispatch-records?retryDue=false&sortField=nextRetryAt&sortDirection=ASC&page=1&size=20` |
| 任务详情页 dispatch records | `/api/ai-task/dispatch-records?planId={planId}&page=1&size=20` |
| action 详情页 dispatch records | `/api/ai-task/dispatch-records?planId={planId}&actionId={actionId}&page=1&size=20` |

## 路由顺序

该接口路径是：

```http
/api/ai-task/dispatch-records
```

Controller 中放在：

```http
/api/ai-task/{planId}
```

之前，避免 `dispatch-records` 被误匹配为 `planId`。

## 与 plan 维度接口的关系

统一全局接口：

```http
GET /api/ai-task/dispatch-records?planId={planId}&actionId={actionId}
```

适合任务中心监控页、运营页、任务详情页和 action 详情页复用。

兼容的计划维度接口：

```http
GET /api/ai-task/{planId}/dispatch-records
GET /api/ai-task/{planId}/actions/{actionId}/dispatch-records
```

仍可继续用于旧页面。

## 当前边界

1. 全局接口暂未做用户隔离和管理权限控制，后续接入 Security 后应限制为管理端访问。
2. `retryExhausted=true/false` 会隐含 `status=FAILED` 条件。
3. `retryDue=true/false` 会隐含 `status=FAILED`、`nextRetryAt IS NOT NULL`、`retryCount < maxRetryCount` 条件。
4. 如果同时传入互相矛盾的条件，例如 `status=RUNNING&retryDue=true` 或 `retryExhausted=true&retryDue=true`，会返回空结果。
5. `timeField` 仅影响 `startTime/endTime`，不影响默认排序。
6. `sortField` 仅影响全局接口，不影响旧的 plan/action 维度兼容接口。
7. `sortField=finishedAt` 不会自动排除 `finishedAt IS NULL` 的记录；需要排除空值时建议同时传 `timeField=finishedAt&startTime=...`。

## 下一步

继续生产化建议：

1. 给全局 dispatch records 查询接口加管理权限。
2. 前端实现失败调度记录列表页和重试耗尽列表页。
3. 逐步迁移旧的 plan/action dispatch records 接口到统一查询接口。
4. 后续可增加更多专用筛选，例如 `hasError=true`、`keyword=...`。
