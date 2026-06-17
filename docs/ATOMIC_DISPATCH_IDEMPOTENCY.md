# 调度幂等原子启动说明

本次改造将调度幂等从“先查再插”升级为数据库唯一键约束下的原子插入。

## 唯一约束

`ai_task_dispatch_record.idempotency_key` 声明为唯一索引：

```java
@Index(name = "uk_task_dispatch_idempotency_key", type = IndexTypeEnum.UNIQUE)
@ColumnType(value = MysqlTypeConstant.VARCHAR, length = 192)
private String idempotencyKey;
```

幂等键规则仍然是：

```text
planId:actionId:triggerAt
```

## tryStart 语义

旧逻辑：

```text
findByIdempotencyKey
  -> exists: false
  -> not exists: insert RUNNING
```

新逻辑：

```text
insert RUNNING directly
  -> success: true
  -> DuplicateKeyException: false
```

这样并发场景下，即使多个调度实例同时处理同一个 trigger，也只有一个实例能写入成功。

## 当前执行链路

```text
tryAcquireDispatchLease
  -> build idempotencyKey
  -> hasSucceeded(idempotencyKey): skip
  -> tryStart(...)
     -> insert success: execute action
     -> duplicate key: skip
  -> markSucceeded / markFailed
```

## 当前边界

1. 唯一键可以防重复启动，但 RUNNING 记录如果因为 JVM 崩溃停住，仍需要后续超时恢复。
2. 当前 FAILED 不会自动重试，因为 `tryStart` 遇到已有记录就会返回 false。
3. 外部副作用类 skill 仍建议在业务层增加幂等检查，例如邮件、飞书、HTTP 回调。

## 下一步

继续生产化建议：

1. 增加 RUNNING 超时恢复策略。
2. 增加 FAILED 退避重试策略。
3. 增加最大重试次数。
4. 将幂等记录查询暴露到调度日志接口，方便排查重复执行和跳过原因。
