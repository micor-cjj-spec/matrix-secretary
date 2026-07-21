# AI 凭证审核待办闭环设计

## 1. 背景

Matrix 已具备凭证列表、汇总、提交、审核和过账能力；matrix-secretary 已具备自然语言解析、TaskPlan/TaskAction、确认、执行、幂等和日志能力；matrix-web 已具备 AI 助手页面。当前三者尚未形成真实业务 Agent 闭环。

本工作项选择“查询本月未审核凭证，并生成审核待办”作为首个跨三仓纵向场景。

## 2. 目标

用户在 Matrix AI 助手中输入类似：

```text
查询本月未审核凭证，并生成一个审核待办
```

系统应生成任务预览，用户确认后：

1. 查询 Matrix 中指定期间、状态为 `SUBMITTED` 的凭证；
2. 返回凭证数量、金额和明细；
3. 创建一条站内审核待办；
4. 持久化任务状态、执行说明和审计日志；
5. 在前端展示执行结果和失败信息。

## 3. 非目标

本期不包含：

- 自动审核、自动过账、自动驳回或批量修改凭证；
- 跨组织、跨账簿权限模型重构；
- 跨不同 TaskPlan 的业务级待办去重；
- 将 matrix-secretary 注册为 Nacos 服务；
- 替换 Matrix 现有知识问答能力；
- 完整生产级服务间身份体系。

## 4. 统一分支

```text
feature/agent-voucher-review-todo
```

| 仓库 | 基础分支 | 计划改动 |
|---|---|---|
| `micor-cjj-spec/matrix` | `dev` | Agent 专用未审核凭证查询契约 |
| `micor-cjj-spec/matrix-secretary` | `dev` | 凭证查询 Skill、规则解析、Matrix 客户端和执行日志 |
| `micor-cjj-spec/matrix-web` | `dev` | 任务模式 API、预览卡片、确认及结果展示 |

## 5. 当前约束

`bizfi_fi_voucher` 当前凭证头只有凭证号、日期、摘要、金额和状态等字段，没有组织、账簿或租户字段。因此 V1 只能按期间和状态做全局查询，不得伪造组织或账簿隔离能力。

接口结果必须返回警告：当前查询尚未按组织或账簿隔离。后续在凭证模型补齐组织和账簿维度后，再升级接口契约。

## 6. 仓库职责

### 6.1 matrix

负责：

- 将 `SUBMITTED` 定义为待审核状态；
- 校验期间格式和分页边界；
- 查询指定期间的待审核凭证；
- 计算总数量和总金额；
- 返回稳定、受控的 Agent DTO；
- 接收并记录 `X-User-Id`、`X-Tenant-Id`、`X-Trace-Id`；
- 可选校验 `X-Matrix-Agent-Key`。

### 6.2 matrix-secretary

负责：

- 识别“未审核凭证查询”意图；
- 将“查询并生成待办”拆成两个 TaskAction；
- 通过专用 MatrixVoucherSkillExecutor 调用 Matrix；
- 使用现有 create_todo Skill 创建站内待办；
- 继续使用 TaskActionExecutionRepository 保证同一 action 重复确认时不重复执行；
- 将 Matrix 查询结果写入 executionNote 和执行日志。

### 6.3 matrix-web

负责：

- 在 AI 助手中增加“任务模式”；
- 调用 matrix-secretary preview/confirm/get/logs 接口；
- 展示 Action、风险级别、确认要求和执行状态；
- 展示查询结果摘要与待办创建结果；
- 保留现有“问答模式”，继续调用 Matrix `/ai/chat`。

## 7. Matrix 接口契约

### 7.1 接口

```http
GET /voucher/agent/pending-review
```

### 7.2 Query 参数

| 参数 | 必填 | 说明 |
|---|---|---|
| `period` | 否 | `yyyy-MM`，默认当前月份 |
| `page` | 否 | 默认 1，最小 1 |
| `size` | 否 | 默认 20，范围 1-100 |
| `summaryKeyword` | 否 | 凭证头摘要模糊匹配 |

### 7.3 Header

| Header | 必填 | 说明 |
|---|---|---|
| `X-User-Id` | 否 | 调用用户，V1 用于链路记录 |
| `X-Tenant-Id` | 否 | 租户上下文，V1 用于链路记录 |
| `X-Trace-Id` | 否 | 跨服务追踪 ID |
| `X-Matrix-Agent-Key` | 条件必填 | 配置 `MATRIX_AGENT_API_KEY` 后必须匹配 |

### 7.4 响应数据

```json
{
  "period": "2026-07",
  "status": "SUBMITTED",
  "page": 1,
  "size": 20,
  "totalCount": 18,
  "totalAmount": 125600.00,
  "records": [
    {
      "voucherId": 1001,
      "voucherNo": "V2026070001",
      "voucherDate": "2026-07-03",
      "summary": "支付办公费用",
      "status": "SUBMITTED",
      "amount": 3200.00,
      "createdBy": "admin"
    }
  ],
  "warnings": [
    "当前凭证头尚未包含组织和账簿维度，本次结果为期间范围内的全局查询。"
  ]
}
```

### 7.5 错误行为

| 场景 | 行为 |
|---|---|
| period 格式错误 | 返回业务参数错误 |
| page/size 越界 | 归一化到允许范围或返回业务参数错误 |
| Agent Key 不匹配 | 拒绝访问 |
| 无数据 | 成功返回，`totalCount=0`、`records=[]` |

## 8. Secretary Skill 设计

### 8.1 Skill

```text
name: matrix_voucher_pending_review
actionType: query_pending_vouchers
executor: matrix-voucher-query
riskLevel: LOW
requiresConfirmation: false
```

输入参数：

```json
{
  "period": "2026-07",
  "page": 1,
  "size": 20,
  "summaryKeyword": null,
  "tenantId": "default"
}
```

### 8.2 解析规则

当文本同时包含“凭证”以及“未审核/待审核/审核待办”等词时，规则 fallback 必须识别该业务场景。

- 仅查询：生成一个 `query_pending_vouchers` action；
- 查询并生成待办：生成 `query_pending_vouchers` 和 `create_todo` 两个 action；
- 默认期间：当前月份；
- 文本出现 `yyyy-MM` 时使用明确期间；
- 待办内容格式：`审核 <period> 未审核凭证`。

### 8.3 执行状态

```text
WAITING_CONFIRM
  -> 用户确认
  -> query action EXECUTED / FAILED
  -> todo action EXECUTED / FAILED
  -> plan 根据现有规则汇总最终状态
```

TaskPlan 仍要求用户整体确认。查询 action 自身为 LOW 风险；创建待办为 MEDIUM 风险。

### 8.4 幂等

同一 TaskAction 使用现有 action 执行幂等记录。重复确认同一 plan 时：

- 已成功的查询 action 不重复调用 Matrix；
- 已成功的 todo action 不重复创建通知。

不同 plan 的业务级去重不在 V1 范围。

### 8.5 配置

```yaml
matrix:
  base-url: ${MATRIX_BASE_URL:http://127.0.0.1:10003/api}
  agent-api-key: ${MATRIX_AGENT_API_KEY:}
  tenant-id: ${MATRIX_TENANT_ID:default}
```

真实端口和网关地址由部署环境覆盖，不写死生产地址。

## 9. Web 交互设计

AI 页面增加模式选择：

- `问答模式`：保持现有流式问答；
- `任务模式`：调用 matrix-secretary。

任务模式流程：

```text
输入自然语言
  -> POST /api/ai-task/preview
  -> 展示 TaskPlan 和 actions
  -> 用户确认或取消
  -> POST /api/ai-task/{planId}/confirm
  -> 展示每个 action 的 status/executionNote
  -> 可查看执行日志
```

V1 使用 `VITE_AI_SECRETARY_BASE_URL` 配置 Secretary 地址。前端发送当前 token，并用稳定的本地用户标识作为 `userId`；后续统一网关和 JWT 身份解析另行实施。

## 10. 实施顺序

1. 本设计文档独立提交；
2. Matrix 新增 DTO、查询服务方法、Agent Controller 接口和测试；
3. Secretary 新增 Skill、专用客户端/Executor、规则解析和测试；
4. Web 新增 Secretary API 和任务预览交互；
5. 分仓构建与测试；
6. 静态契约核对和端到端手工验收；
7. 回写实施结果。

## 11. 验收标准

- [ ] 三仓存在同名分支；
- [ ] 本文档提交早于实现提交；
- [ ] `period=yyyy-MM` 可返回真实 `SUBMITTED` 凭证；
- [ ] 无数据时返回成功空结果；
- [ ] 规则 fallback 可拆出查询和待办两个 action；
- [ ] 用户确认后查询 action 写入可读执行结果；
- [ ] 待办 action 创建 Notification；
- [ ] 重复确认同一 plan 不重复创建待办；
- [ ] Web 可展示预览、确认、成功和失败结果；
- [ ] 不存在自动审核或自动过账入口。

## 12. 验证计划

### matrix

```bash
mvn test
mvn clean package
```

### matrix-secretary

```bash
cd java-service
mvn test

cd ../python-service
python -m compileall app
pytest
```

### matrix-web

```bash
npm install
npm run build
```

### 纵向验收文本

```text
查询本月未审核凭证，并生成一个审核待办
```

应得到两个 actions，并在确认后分别出现 Matrix 查询结果和 Notification ID。

## 13. 风险与回滚

| 风险 | 缓解 | 回滚 |
|---|---|---|
| 凭证缺少组织/账簿维度 | 返回明确 warning，不伪造隔离 | 删除 Agent 查询接口和 Skill |
| Secretary 无法访问 Matrix | 可配置 base URL、超时后进入 FAILED | 禁用或移除 Skill |
| 前端双服务地址配置复杂 | 使用单独环境变量，后续统一网关 | 回退到问答模式 |
| 规则解析误命中 | 必须同时匹配凭证和审核语义 | 删除领域规则，保留 LLM 解析 |
| 重复执行 | 复用现有 action 幂等执行仓库 | 回滚专用 Executor，不影响通用任务中心 |

## 14. 实施结果

实现完成后补充实际文件、验证结果、已知限制和 PR 链接。
