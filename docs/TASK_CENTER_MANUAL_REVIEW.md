# Task Center 人工介入处理说明

本文档记录 `NEEDS_MANUAL_REVIEW` 状态 action 的人工处理入口。

## 1. 背景

任务中心中部分错误不适合自动重试，例如：

```text
1. 参数缺失。
2. userId 缺失。
3. 渠道消息参数错误。
4. HTTP Skill 参数错误。
```

这类 action 会进入：

```text
NEEDS_MANUAL_REVIEW
```

人工修正后，可以通过专用接口重新执行或重新进入调度队列。

## 2. 请求模型

```text
java-service/src/main/java/com/kailei/demo/model/ResolveManualReviewRequest.java
```

字段：

```text
operatorUserId
标题 title
内容 content
目标 target
调度 schedule
参数 args
优先级 priority
是否需要确认 requiresConfirmation
是否立即执行 executeNow
备注 note
```

## 3. 接口

```http
POST /api/task-center/plans/{planId}/actions/{actionId}/manual-resolve
```

示例：

```json
{
  "operatorUserId": "user-001",
  "args": {
    "platform": "feishu",
    "message": "该喝水了"
  },
  "executeNow": true,
  "note": "已补充飞书消息参数"
}
```

## 4. 服务方法

```text
TaskCommandService.resolveManualReview(planId, actionId, request)
```

处理规则：

```text
1. 校验操作者是否有权限访问 plan。
2. 只允许处理 NEEDS_MANUAL_REVIEW 状态 action。
3. 使用请求中的 title/content/target/schedule/args/priority/requiresConfirmation 修正 action。
4. 如果 executeNow = true，立即执行。
5. 如果 executeNow != true 且 action 有调度，则重新进入 SCHEDULED。
6. 如果 action 没有调度，则默认立即执行。
7. 写入执行状态日志。
8. 保存 plan 并更新 session。
```

## 5. 状态流转

立即执行：

```text
NEEDS_MANUAL_REVIEW -> CONFIRMED -> EXECUTED / FAILED / FAILED_FINAL / NEEDS_MANUAL_REVIEW
```

重新调度：

```text
NEEDS_MANUAL_REVIEW -> SCHEDULED
```

## 6. 安全边界

当前不允许通过该入口处理：

```text
FAILED
FAILED_FINAL
EXECUTED
SCHEDULED
CANCELLED
WAITING_CONFIRM
```

这些状态应该走各自的专用流程：

```text
FAILED       -> retry 接口或自动重试
FAILED_FINAL -> 后续可增加 reopen 接口
WAITING_CONFIRM -> edit / confirm
SCHEDULED    -> 等待调度或 cancel
EXECUTED     -> 不允许重跑，除非未来增加 clone/replay 机制
```

## 7. 后续建议

```text
1. 增加人工处理审计字段，例如 resolvedBy / resolvedAt / resolveNote。
2. 支持 FAILED_FINAL 的 reopen 流程。
3. 支持仅保存修复，不立即执行、不重新调度。
4. 对高风险 skill 的人工处理增加二次确认。
```
