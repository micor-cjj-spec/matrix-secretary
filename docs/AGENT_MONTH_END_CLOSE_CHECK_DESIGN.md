# AI 月结检查闭环设计

## 1. 目标

用户在 Matrix AI 助手中输入：

```text
检查本月是否满足结账条件，并列出阻塞项
```

系统生成任务预览，用户确认后调用 Matrix 现有月结工作台能力，返回是否可结账、就绪度、阻塞项、警告项和建议动作；用户可选择同时创建处理待办。

## 2. 统一分支

```text
feature/agent-month-end-close-check
```

三个仓库均基于 `feature/agent-voucher-review-todo` 创建，以复用上一阶段 TaskPlan、专用 Matrix Skill 调用和 Web 任务卡片能力。

## 3. 非目标

- 不自动执行结账、反结账、损益结转、调汇或过账；
- 不修改 Matrix 现有月结算法；
- 不新增数据库表；
- 不在本期完成统一网关和 JWT 服务身份治理。

## 4. 仓库职责

### matrix

复用现有：

```http
GET /period-process/month-end-workbench?forg={forg}&period={yyyy-MM}
```

该接口已返回：

- `canClose`
- `closeStatus`
- `readinessScore`
- `blockingCount`
- `warningCount`
- `pendingCount`
- `checkItems`
- `warnings`

本期增加 Agent 专用只读入口或认证包装，传播 user、tenant、trace 和可选 Agent Key，不开放结账写操作。

### matrix-secretary

新增 Skill：

```text
name: matrix_month_end_close_check
actionType: check_month_end_close
executor: matrix-month-end-close-check
riskLevel: LOW
```

领域解析规则：文本同时包含“月结/结账/期末”和“检查/条件/阻塞/是否可以”时识别；支持当前月、上月和明确 `yyyy-MM`。如果文本包含“生成待办/创建待办”，再生成一个 `create_todo` action。

### matrix-web

复用现有 TaskPlan 卡片，展示执行说明中的：

- 是否可结账；
- 就绪度；
- 阻塞、警告、待处理数量；
- 最多 5 个关键检查项；
- Matrix 返回的提示。

仅补充任务模式示例文案，不新增独立页面。

## 5. 执行规则

```text
WAITING_CONFIRM
  -> 用户确认
  -> 月结检查 action EXECUTED / FAILED
  -> 可选 create_todo action EXECUTED / FAILED
```

同一 action 重复确认复用现有幂等记录。检查为只读；创建待办需要确认。不同 TaskPlan 之间不做业务级去重。

## 6. Matrix 契约

请求参数：

| 参数 | 必填 | 说明 |
|---|---|---|
| `forg` | 否 | 业务单元 ID；缺失时 Matrix 会返回基础资料提示 |
| `period` | 否 | `yyyy-MM`，默认当前月份 |

Secretary 将调用现有月结工作台接口并解析统一 `ApiResponse.code/message/data`。

## 7. 验收标准

- [ ] 三仓同名分支；
- [ ] 文档提交早于代码；
- [ ] “检查本月是否满足结账条件”生成一个检查 action；
- [ ] “检查并生成处理待办”生成两个 actions；
- [ ] 返回 `canClose`、状态、就绪度和计数；
- [ ] executionNote 展示最多 5 个关键检查项；
- [ ] Matrix 不可用时 action 进入 FAILED；
- [ ] 不存在自动结账或其他财务写操作。

## 8. 验证计划

```bash
# matrix
mvn test

# matrix-secretary
cd java-service && mvn test
cd ../python-service && pytest

# matrix-web
npm run build
```

纵向验收文本：

```text
检查本月是否满足结账条件，并列出阻塞项
```

以及：

```text
检查 2026-07 是否可以结账，并生成一个处理待办
```

## 9. 风险与回滚

- 业务单元缺失：保留 Matrix 原始 warning，不伪造组织维度；
- 结果过长：executionNote 限制 500 字符，只展示最多 5 项；
- 误命中：领域规则要求月结语义和检查语义同时存在；
- 回滚：删除 Skill、Executor、领域解析器及示例文案，不影响上一阶段凭证闭环。