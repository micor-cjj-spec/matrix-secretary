# 任务列表分页说明

本次改造将任务列表查询从无上限全量返回升级为分页返回，避免任务中心数据量增长后接口一次性加载过多任务。

## 接口变化

### 查询任务列表

```http
GET /api/ai-task?page=1&size=20&userId=demo-user
```

参数说明：

| 参数 | 必填 | 默认值 | 说明 |
|---|---:|---:|---|
| `page` | 否 | `1` | 页码，从 1 开始 |
| `size` | 否 | `20` | 每页数量，最大 100 |
| `userId` | 否 | - | 按用户过滤任务 |

返回结构：

```json
{
  "records": [],
  "total": 0,
  "page": 1,
  "size": 20,
  "pages": 0
}
```

### 查询会话下任务列表

```http
GET /api/ai-task/sessions/{sessionId}/plans?page=1&size=20&userId=demo-user
```

同样返回 `PageResult<TaskPlan>`。

## 设计约束

- `page < 1` 时自动归一为 `1`。
- `size < 1` 时自动归一为 `20`。
- `size > 100` 时自动截断为 `100`。
- Repository 层使用 MyBatis-Plus `Page` 查询。
- 项目增加 `MybatisPlusInterceptor + PaginationInnerInterceptor`，数据库类型为 MySQL。

## 后续计划

当前只完成 API 查询分页。调度扫描仍保留原来的全量扫描逻辑，后续应继续升级为：

1. 只查询 `SCHEDULED` 状态动作。
2. 只查询 `nextRunAt <= now` 的到期动作。
3. 分页或游标扫描。
4. 加数据库锁 / 幂等键，避免多实例重复执行。
5. 记录 dispatch 批次日志。
