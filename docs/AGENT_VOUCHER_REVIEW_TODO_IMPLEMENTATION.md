# AI 凭证审核待办闭环实施结果

## 1. 工作项

```text
查询本月未审核凭证，并生成一个审核待办
```

统一分支：

```text
feature/agent-voucher-review-todo
```

设计文档：

```text
docs/AGENT_VOUCHER_REVIEW_TODO_DESIGN.md
```

设计文档提交 `3f59ce5649ece5177debc9b423442f214a621885` 早于三个仓库的所有实现提交，满足文档门禁。

## 2. 实施结果概览

已完成首个 Matrix 三仓业务 Agent 纵向闭环的代码实现：

```text
matrix-web 任务模式
  -> matrix-secretary TaskPlan 预览
  -> 用户确认
  -> MatrixVoucherSkillExecutor
  -> matrix 待审核凭证只读接口
  -> TaskAction executionNote / execution log
  -> create_todo Notification
  -> matrix-web 展示最终状态
```

本期不存在自动审核、自动过账、自动驳回或凭证写入入口。

## 3. matrix 实际修改

新增文件：

- `fi-service/src/main/java/single/cjj/fi/gl/controller/BizfiFiAgentVoucherController.java`
- `fi-service/src/main/java/single/cjj/fi/gl/service/BizfiFiAgentVoucherQueryService.java`
- `fi-service/src/main/java/single/cjj/fi/gl/vo/PendingVoucherReviewItemVO.java`
- `fi-service/src/main/java/single/cjj/fi/gl/vo/PendingVoucherReviewResultVO.java`

实现能力：

- 提供 `GET /voucher/agent/pending-review`；
- 按 `period=yyyy-MM`、`SUBMITTED` 状态和摘要关键字查询；
- 校验 page 和 size，size 最大 100；
- 返回总数量、总金额、分页明细和 warning；
- 记录 user、tenant 和 trace 请求上下文；
- 支持通过 `MATRIX_AGENT_API_KEY` 开启服务调用密钥校验；
- 凭证没有组织和账簿字段时明确返回全局查询警告。

未修改已有 `/voucher/audit`、`/voucher/post`、`/voucher/reject` 等写接口。

## 4. matrix-secretary 实际修改

新增或修改文件：

- `java-service/src/main/resources/skills/matrix_voucher_pending_review/skill.yml`
- `java-service/src/main/java/com/kailei/demo/skill/MatrixVoucherSkillExecutor.java`
- `java-service/src/main/java/com/kailei/demo/skill/GenericSkillExecutor.java`
- `java-service/src/main/resources/application.yml`
- `python-service/app/voucher_review_parser.py`
- `python-service/app/main.py`
- `python-service/tests/test_voucher_review_parser.py`

实现能力：

- 注册 `matrix_voucher_pending_review` Skill；
- 新增 `query_pending_vouchers` actionType；
- 使用专用 builtin executor 调用 Matrix，不启用通用 HTTP Skill；
- 传播 `X-User-Id`、`X-Tenant-Id`、`X-Trace-Id` 和可选 Agent Key；
- 将总数量、总金额、本页数量、最多 3 条凭证示例和 warning 写入 executionNote；
- executionNote 限制在 500 字符内，避免超过数据库 512 字段长度；
- Matrix 调用异常进入 FAILED，并保留可读错误说明；
- 领域规则在 LLM 之前识别待审核凭证场景；
- “查询并生成待办”稳定拆成查询 action 和 create_todo action；
- 明确期间、当前月份和上月均可解析；
- 复用已有 action 执行幂等和 Notification 持久化能力。

新增配置：

```yaml
matrix:
  base-url: ${MATRIX_BASE_URL:http://127.0.0.1:10003/api}
  agent-api-key: ${MATRIX_AGENT_API_KEY:}
  tenant-id: ${MATRIX_TENANT_ID:default}
```

## 5. matrix-web 实际修改

新增或修改文件：

- `src/api/aiTask.js`
- `src/components/ai/AiTaskPlanCard.vue`
- `src/views/ai/AiAssistantView.vue`
- `vite.config.js`

实现能力：

- AI 助手增加“问答模式”和“任务模式”；
- 问答模式保留原有 Matrix 流式 AI 接口；
- 任务模式调用 Secretary preview、confirm、cancel、get 和 logs；
- 展示 TaskPlan、Action、风险等级、确认要求、状态和 executionNote；
- 支持确认、取消、刷新和查看日志；
- 使用稳定的浏览器本地 userId 保持 TaskPlan 操作隔离；
- 本地开发通过 `/secretary-api` 代理到 `http://localhost:10002/api`；
- 支持用 `VITE_AI_SECRETARY_BASE_URL` 覆盖生产环境 Secretary 地址。

## 6. 验证结果

### 6.1 分支和提交顺序

验证通过：

- 三个仓库均存在 `feature/agent-voucher-review-todo`；
- 三个分支均以各自 `dev` 为基础，当前均未落后于 `dev`；
- 设计文档提交先于实现提交；
- 三个仓库只包含本工作项相关差异。

### 6.2 Python 领域解析测试

实际执行：

```bash
pytest -q
```

结果：

```text
3 passed
```

覆盖：

- 明确期间的“查询 + 待办”拆分；
- 仅查询场景；
- 无关文本不误命中。

### 6.3 静态契约核对

已核对：

- Matrix 路径与 Secretary 调用路径一致；
- Matrix ApiResponse 的 `code/message/data` 与 Executor 解析一致；
- Python actionType、skillName 与 Java SkillCatalog 注册一致；
- ConfirmTaskResponse 的 `plan` 字段与 Web 处理一致；
- TaskPlan 使用 `sourceText`，Web 已按真实字段展示；
- TaskExecutionLogEntity 字段与 Web 日志展示一致；
- NotificationRepository 会持久化 userId、planId、actionId、title 和 content；
- executionNote 长度受到限制。

### 6.4 未执行的检查

以下完整检查未能在当前执行环境运行：

```bash
# matrix
mvn test
mvn clean package

# matrix-secretary Java
cd java-service
mvn test

# matrix-web
npm install
npm run build
```

原因：当前容器无法解析 `github.com`，无法克隆三个仓库并取得完整依赖环境。GitHub 当前也没有为这些功能分支返回 CI 状态。

这些检查不能标记为通过，合并前必须在本地或 CI 中执行。

## 7. 当前验收状态

- [x] 三仓同名分支；
- [x] 文档提交早于实现；
- [x] Matrix 待审核凭证只读契约；
- [x] 无数据返回成功空结果的代码路径；
- [x] 规则解析拆分查询和待办；
- [x] 查询 action 生成可读 executionNote；
- [x] create_todo 复用 Notification 持久化；
- [x] 同一 action 复用已有执行幂等；
- [x] Web 具备预览、确认、取消、刷新和日志界面；
- [x] 不存在自动审核或自动过账实现；
- [ ] Java 完整测试与打包；
- [ ] Vue 生产构建；
- [ ] 真实数据库、Matrix 服务和 Secretary 服务端到端联调。

## 8. 已知限制

1. `bizfi_fi_voucher` 尚无组织、账簿和租户字段，V1 查询是期间范围内的全局查询。
2. Web 的 userId 暂为浏览器稳定本地标识，尚未由 Matrix JWT 统一解析。
3. Matrix 和 Secretary 尚未通过统一网关或 Nacos 完成正式服务治理。
4. 当前执行结果通过 executionNote 展示摘要和最多 3 条凭证示例，没有单独持久化结构化业务结果表。
5. 不同 TaskPlan 之间暂未做相同审核待办的业务级去重。
6. 一个 TaskPlan 中的 actions 当前独立执行；Matrix 查询失败时，create_todo 仍可能按原计划创建通用审核待办。
7. 生产环境需配置反向代理、CORS、`VITE_AI_SECRETARY_BASE_URL`、`MATRIX_BASE_URL` 和双方一致的 `MATRIX_AGENT_API_KEY`。

## 9. 风险与回滚

### 回滚 matrix

删除四个 Agent 查询文件即可，不影响现有凭证接口和数据库结构。

### 回滚 matrix-secretary

删除 Matrix Skill、Executor 和领域解析器，移除 GenericSkillExecutor 路由及 matrix 配置；已有通用任务中心不受影响。

### 回滚 matrix-web

移除 Task Center API、TaskPlan 组件和任务模式，恢复原有 AI 问答页面；现有知识问答链路不受影响。

## 10. 后续建议

合并前先在本地完成三仓构建和真实纵向联调。下一小步应优先补：

1. Matrix 查询服务单元测试；
2. MatrixVoucherSkillExecutor 的 RestClient Mock 测试；
3. Web TaskPlan 组件测试；
4. 统一 JWT 用户身份和租户上下文；
5. 将结构化查询结果单独持久化，而不是仅依赖 executionNote。

## 11. Pull Requests

当前未创建 PR，也未合并分支，等待用户明确指令。
