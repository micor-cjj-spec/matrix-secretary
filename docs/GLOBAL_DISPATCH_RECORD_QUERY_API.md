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
| `startTime` | OffsetDateTime | 按 `triggerAt >= startTime` 过滤 |
| `endTime` | OffsetDateTime | 按 `triggerAt <= endTime` 过滤 |
| `dispatchOwner` | string | 调度持有者，例如 `local-scheduler` |
| `retryExhausted` | boolean | `true` 查询重试耗尽记录；`false` 查询仍可重试记录 |
| `retryDue` | boolean | `true` 查询已到重试时间记录；`false` 查询未到重试时间记录 |
| `page` | long | 页码，默认走 `PageResult` 标准归一化 |
| `size` | long | 每页数量，默认走 `PageResult` 标准归一化 |

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

### 查询时间范围内的失败记录

```http
GET /api/ai-task/dispatch-records?status=FAILED&startTime=2026-06-18T00:00:00%2B08:00&endTime=2026-06-19T00:00:00%2B08:00&page=1&size=20
```

## 前端任务中心用法

| 场景 | 请求 |
|---|---|
| 点击 FAILED 卡片 | `/api/ai-task/dispatch-records?status=FAILED&page=1&size=20` |
| 点击 RUNNING 卡片 | `/api/ai-task/dispatch-records?status=RUNNING&page=1&size=20` |
| 点击重试耗尽卡片 | `/api/ai-task/dispatch-records?retryExhausted=true&page=1&size=20` |
| 点击等待重试卡片 | `/api/ai-task/dispatch-records?retryExhausted=false&page=1&size=20` |
| 点击已到期重试卡片 | `/api/ai-task/dispatch-records?retryDue=true&page=1&size=20` |
| 点击未到期重试卡片 | `/api/ai-task/dispatch-records?retryDue=false&page=1&size=20` |
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
5. `startTime/endTime` 当前过滤的是 `triggerAt`，不是 `startedAt` 或 `finishedAt`。

## 下一步

继续生产化建议：

1. 给全局 dispatch records 查询接口加管理权限。
2. 增加按 `startedAt/finishedAt` 的时间过滤类型。
3. 前端实现失败调度记录列表页和重试耗尽列表页。
4. 逐步迁移旧的 plan/action dispatch records 接口到统一查询接口。
