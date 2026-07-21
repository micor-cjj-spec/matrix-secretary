# Matrix 三仓文档先行开发 Skill

## 1. 目标

Matrix 由三个核心仓库组成：

- `matrix`：业务后端、网关、认证和财务服务。
- `matrix-web`：Web 前端和 Agent 交互界面。
- `matrix-secretary`：AI Agent、任务编排、调度、Skill 执行和审计。

本 Skill 用于规范跨三个仓库的开发流程，确保同一个工作项能够被统一追踪并形成端到端成果。

## 2. 核心流程

```text
需求
  -> 生成统一分支名
  -> 三仓创建同名分支
  -> 先在 docs 目录生成设计文档并提交
  -> 检查文档是否可实施
  -> 按依赖顺序生成代码和配置
  -> 分仓验证
  -> 跨仓联调
  -> 更新实施结果
  -> 创建关联 PR
```

## 3. 强制规则

### 3.1 三仓分支名完全一致

每个跨仓工作项必须在以下仓库创建相同分支：

```text
micor-cjj-spec/matrix
micor-cjj-spec/matrix-web
micor-cjj-spec/matrix-secretary
```

示例：

```text
feature/agent-voucher-query
```

任意仓库无法创建同名分支时，不得进入代码阶段。

### 3.2 文档先于代码

每个工作项必须先在 `docs/` 下生成设计文档，并形成独立提交。首次文档提交中不得混入实现代码。

文档至少包含：

1. 背景、目标和非目标。
2. 三仓职责分配。
3. 影响模块、页面、接口和数据结构。
4. 跨仓接口契约。
5. 状态、确认、幂等、重试和审计要求。
6. 实施步骤。
7. 验收标准和测试计划。
8. 风险与回滚方案。

进入实现后，发现设计需要调整时，必须先更新文档，再修改代码。

### 3.3 纵向闭环优先

一次工作项优先完成一条可验收链路：

```text
matrix-web 发起操作
  -> matrix 完成身份和业务校验
  -> matrix-secretary 解析并生成任务预览
  -> 用户确认
  -> matrix-secretary 调用 matrix 业务接口
  -> 写入执行记录
  -> matrix-web 展示最终结果
```

不得只增加菜单、Controller 或 Skill 声明而没有真实调用闭环。

## 4. 分支命名

推荐格式：

```text
feature/<work-item-slug>
fix/<work-item-slug>
refactor/<work-item-slug>
docs/<work-item-slug>
```

要求：

- 使用小写英文和短横线。
- 三仓名称完全一致。
- 一个分支只处理一个工作项。
- 未指定基础分支时，从各仓库 `main` 创建。

## 5. 文档位置

主设计文档放在最能代表需求的仓库：

- Agent、任务、调度相关：`matrix-secretary/docs/`
- 纯后端业务相关：`matrix/docs/`
- 纯前端体验相关：`matrix-web/docs/`

跨三仓功能默认放在 `matrix-secretary/docs/`。

推荐文件名：

```text
<FEATURE_NAME>_DESIGN.md
<FEATURE_NAME>_IMPLEMENTATION.md
<FEATURE_NAME>_ACCEPTANCE.md
```

## 6. 标准执行阶段

### 阶段 0：建立工作项

确定工作项目标、统一分支名、主文档位置和初始影响范围。

### 阶段 1：检查仓库基线

检查当前代码、相关文档、已有分支和未合并改动。不得只依据历史记忆修改项目。

### 阶段 2：创建三仓同名分支

输出三个仓库的分支创建结果。三者必须全部成功或已存在。

### 阶段 3：生成并提交设计文档

文档提交信息推荐：

```text
docs: describe <work-item>
```

该提交完成前不得修改实现代码。

### 阶段 4：检查文档完整性

确认接口、职责、状态、数据、验收和回滚均可执行。缺少关键内容时继续完善文档。

### 阶段 5：按依赖顺序实现

默认顺序：

```text
1. matrix：业务接口和数据能力
2. matrix-secretary：Agent Skill 和任务编排
3. matrix-web：交互页面和结果展示
```

### 阶段 6：分仓验证

`matrix`：

```bash
mvn test
mvn clean package
```

`matrix-web`：

```bash
npm install
npm run build
```

仓库存在对应命令时继续执行 lint、单元测试和端到端测试。

`matrix-secretary`：

```bash
cd java-service
mvn test

cd ../python-service
python -m compileall app
pytest
```

未执行的检查必须明确说明原因。

### 阶段 7：跨仓联调

验证正常流程、参数错误、权限失败、下游不可用、重复操作、高风险动作确认、调度重试以及前端错误展示。

### 阶段 8：记录实施结果

更新设计文档或新增实施文档，记录：

- 实际修改文件。
- 最终接口和数据变更。
- 验证命令及结果。
- 未执行检查。
- 已知限制。
- 风险与回滚步骤。

### 阶段 9：创建关联 PR

三个 PR 使用相同工作项名称和相同分支名，并在描述中说明相互依赖与合并顺序。

## 7. Skill 实现位置

开发流程 Skill 放在：

```text
.agents/skills/matrix-cross-repo-development/SKILL.md
```

该目录与 `java-service/src/main/resources/skills` 不同。后者是 AI 秘书运行时业务 Skill，本目录用于指导 AI/Codex 开发三个仓库。

根目录 `AGENTS.md` 需要增加入口说明：涉及 `matrix`、`matrix-web`、`matrix-secretary` 的跨仓开发任务应优先使用本 Skill。

## 8. 首版验收标准

- 三仓存在相同开发分支。
- 本设计文档先于 Skill 实现提交。
- 项目中存在可读取的 `SKILL.md`。
- `AGENTS.md` 声明跨仓开发入口。
- Skill 明确执行“同名分支、文档提交、实现、验证、成果记录”的顺序。
