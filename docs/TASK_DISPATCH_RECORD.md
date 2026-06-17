# 任务调度幂等记录表说明

本次新增 `ai_task_dispatch_record`，用于记录 action 每一次触发执行的幂等状态。

## 表结构

| 字段 | 说明 |
|---|---|
| `id` | 调度记录 ID |
| `plan_id` | 任务计划 ID |
| `action_id` | 任务动作 ID |
| `trigger_at` | 本次触发时间 |
| `idempotency_key` | 幂等键 |
| `status` | `RUNNING` / `SUCCEEDED` / `FAILED` |
| `dispatch_owner` | 调度实例标识 |
| `started_at` | 开始时间 |
| `finished_at` | 结束时间 |
| `error_message` | 错误信息 |
| `created_at` | 创建时间 |
| `updated_at` | 更新时间 |

## 幂等键规则

当前 Repository 提供：

```java
buildIdempotencyKey(planId, actionId, triggerAt)
```

默认格式：

```text
planId:actionId:triggerAt
```

手动触发时，如果没有 `triggerAt`，会使用：

```text
planId:actionId:manual
```

## Repository 能力

```java
findByIdempotencyKey(idempotencyKey)
hasSucceeded(idempotencyKey)
tryStart(planId, actionId, triggerAt, dispatchOwner)
markSucceeded(idempotencyKey)
markFailed(idempotencyKey, errorMessage)
```

## 当前边界

本次只是落表和基础 Repository，不直接接入 skill 执行链路。

当前 `tryStart(...)` 的语义是：

```text
先查 idempotencyKey 是否存在
不存在则插入 RUNNING 记录
```

它还不是数据库级强幂等，因为 `idempotency_key` 当前只是索引，不是唯一约束。并发情况下仍可能出现两个实例同时插入同一个幂等键。

## 下一步

继续生产化建议：

1. 给 `idempotency_key` 增加唯一约束。
2. 将 `tryStart(...)` 改为原子 insert，捕获唯一键冲突。
3. 在 `TaskExecutionService` 或 skill 执行入口接入幂等记录。
4. 对外部副作用类 skill，例如邮件、飞书、HTTP 回调，执行前先 `tryStart`。
5. 执行成功后 `markSucceeded`，失败后 `markFailed`。
