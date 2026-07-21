# Matrix 三仓开发 Skill 实施结果

## 1. 工作项

```text
Matrix 三仓文档先行开发 Skill
```

统一分支：

```text
feature/matrix-cross-repo-development-skill
```

## 2. 分支结果

| 仓库 | 结果 |
|---|---|
| `micor-cjj-spec/matrix` | 已从 `main` 创建同名分支 |
| `micor-cjj-spec/matrix-web` | 已从 `main` 创建同名分支 |
| `micor-cjj-spec/matrix-secretary` | 已从 `main` 创建同名分支 |

本工作项只需要在 `matrix-secretary` 保存开发流程 Skill，因此 `matrix` 和 `matrix-web` 当前仅保留同名追踪分支，没有空提交。

## 3. 文档门禁

设计文档已先于 Skill 实现提交：

```text
65eae01 docs: define cross-repository document-first workflow
```

设计文档：

```text
docs/CROSS_REPO_DEVELOPMENT_SKILL.md
```

代码化 Skill 在后续独立提交中产生，满足“先文档、后成果”的顺序要求。

## 4. 实施成果

### 4.1 开发流程 Skill

```text
.agents/skills/matrix-cross-repo-development/SKILL.md
```

提交：

```text
087a462 feat: add cross-repository development skill
```

已实现：

- 三仓分支名称一致性门禁。
- 文档独立提交门禁。
- 三仓职责边界。
- 基线检查和影响分析。
- 后端、Agent、前端的依赖实施顺序。
- 分仓验证命令。
- 纵向业务闭环验收。
- 实施结果记录。
- 关联 PR 规则。
- 完成报告格式。

### 4.2 AGENTS 入口

```text
AGENTS.md
```

提交：

```text
725fd18 docs: register cross-repository development skill
```

已增加跨仓开发入口，要求涉及 `matrix`、`matrix-web` 和 `matrix-secretary` 的工作优先读取本 Skill 和主设计文档。

### 4.3 设计文档模板

```text
.agents/skills/matrix-cross-repo-development/references/DESIGN_DOCUMENT_TEMPLATE.md
```

提交：

```text
29d907a feat: add cross-repository design document template
```

模板包括：

- 三仓分支表。
- 职责分配。
- 影响分析。
- 跨仓接口契约。
- 状态、确认、幂等、重试和审计。
- 实施计划。
- 验收标准。
- 验证结果。
- 风险和回滚。
- PR 关联位置。

## 5. 验证结果

本次未修改 Java、Python、Vue、数据库或运行配置，因此没有执行 Maven、npm 或 pytest 构建测试。

已进行以下静态验证：

- 三个仓库均成功创建相同分支。
- 设计文档提交早于 Skill 文件提交。
- Skill 文件路径与设计文档约定一致。
- `AGENTS.md` 已包含 Skill 入口。
- 模板文件位于 Skill 的 `references` 目录。
- 未创建实现无关的空提交。

## 6. 已知限制

- 当前只在 `matrix-secretary` 中保存 Skill；其他两个仓库通过同名分支参与工作项追踪。
- GitHub 不会自动让所有 Agent 客户端发现 `.agents/skills`，具体客户端需要支持项目内 Skill 读取或由 `AGENTS.md` 引导加载。
- 当前没有自动脚本检查三个仓库的分支是否一致；首版由 Agent 工作流执行检查。
- 当前没有自动生成三个关联 PR；只有在用户明确要求创建 PR 时才执行。

## 7. 后续可选增强

1. 增加三仓分支一致性检查脚本。
2. 增加设计文档结构校验脚本。
3. 增加 GitHub Actions，检查实现提交之前是否已有设计文档提交。
4. 增加跨仓工作项清单和 PR 描述模板。
5. 用首个真实业务场景验证该 Skill，例如“查询未审核凭证并创建审核待办”。

## 8. 回滚

本次仅新增文档和 Agent 指令文件。回滚时可删除：

```text
docs/CROSS_REPO_DEVELOPMENT_SKILL.md
docs/CROSS_REPO_DEVELOPMENT_SKILL_IMPLEMENTATION.md
.agents/skills/matrix-cross-repo-development/
```

并从 `AGENTS.md` 移除 `Cross-Repository Development Skill` 章节。不会影响现有 Java、Python 或前端运行逻辑。
