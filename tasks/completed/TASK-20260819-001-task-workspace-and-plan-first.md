# Task Plan — TASK-20260819-001

## 1. Metadata

| Field | Value |
| --- | --- |
| Task ID | `TASK-20260819-001` |
| Title | Establish task workspace and plan-first development workflow |
| Status | `Completed` |
| Owner | Human Developer |
| Implementer | Codex |
| Created | `2026-08-19` |
| Updated | `2026-08-19` |
| Related Phase | Phase 0 - Project Bootstrap |
| Related ADR | `None` |

## 2. Background

项目已有工程规范、Git 工作流和基础 Maven 骨架，但缺少统一的开发方案工作区，无法在实现前集中记录范围、设计、验收标准、验证方式和风险。

## 3. Goal

建立 `tasks/` 工作区，并将“先生成方案、审批后开发”的流程固化到项目规范和 Agent 上下文中。

## 4. Non-Goals

- 不修改撮合引擎生产代码。
- 不改变交易语义、核心架构、网络协议或持久化格式。
- 不引入新的第三方依赖。
- 不执行 `git push`。

## 5. Requirements and Acceptance Criteria

### Requirements

- [x] 建立 `tasks/` 目录及任务生命周期目录。
- [x] 提供任务方案模板。
- [x] 在 `MASTER_PROMPT.md` 中要求方案先行。
- [x] 在 `DEVELOPMENT_RULES.md` 中定义方案审批、状态流转和归档规则。
- [x] 在 `AGENT_CONTEXT.md` 中记录任务工作区和当前状态。
- [x] 创建下一开发阶段的领域模型方案，且未审批前不修改生产代码。

### Acceptance Criteria

- [x] `tasks/README.md` 能说明工作区用途、状态和使用规则。
- [x] `tasks/TEMPLATE.md` 覆盖设计、验收、测试、Benchmark、风险和 Git 计划。
- [x] 当前仓库规范明确禁止在 `Proposed` 方案获批前修改生产代码。
- [x] Maven 构建和现有测试保持通过。
- [x] Git Diff 只包含本任务相关文件。

## 6. Current Implementation and Scope

### Current Implementation

项目已有 `.codex/` 规范、`docs/` 文档体系、根 Maven Reactor、Core 模块和 JMH Benchmark 模块。

### In Scope

- `.codex/MASTER_PROMPT.md`
- `.codex/DEVELOPMENT_RULES.md`
- `.codex/AGENT_CONTEXT.md`
- `tasks/`

### Out of Scope

- `src/`
- `core/`
- `benchmark/`
- 交易领域模型实现

## 7. Design Proposal

### Proposed Design

使用 `tasks/active`、`tasks/completed` 和 `tasks/archive` 管理任务方案。每个任务使用唯一编号，并通过 `Proposed -> Approved -> In Progress -> Completed` 进行状态管理。

### Alternatives Considered

| Option | Advantages | Risks or Costs | Result |
| --- | --- | --- | --- |
| 将方案散落在 Issue 或聊天记录中 | 入口简单 | 无法随代码版本化，难以恢复上下文 | Rejected |
| 为每个任务建立独立分支但不保留方案文件 | 变更隔离 | 缺少验收、风险和设计记录 | Rejected |
| 仓库内统一 `tasks/` 工作区 | 可版本化、可审查、可恢复 | 需要维护任务状态 | Accepted |

### Decision

采用仓库内 `tasks/` 工作区。方案文件必须先存在并获得 Human 审批，才允许进入生产代码实现。

### Architecture Impact

- [x] No architecture change
- [ ] ADR required
- [ ] Human architecture decision required

## 8. Planned File Changes

| File or Directory | Change | Reason |
| --- | --- | --- |
| `.codex/MASTER_PROMPT.md` | 增加方案先行规则 | 约束 Agent 会话行为 |
| `.codex/DEVELOPMENT_RULES.md` | 增加任务工作区和审批门禁 | 固化软件工程流程 |
| `.codex/AGENT_CONTEXT.md` | 记录工作区和当前阶段 | 支持上下文恢复 |
| `tasks/` | 新建模板、说明和任务记录 | 统一管理开发方案 |

## 9. Test Plan

### Unit Tests

- [x] 不涉及生产代码，复用现有测试套件。

### Integration or System Tests

- [x] 执行 Maven Reactor 构建。

### Failure and Boundary Tests

- [x] 检查任务状态规则和方案审批门禁是否明确。

### Determinism or Replay Tests

- [x] Not applicable

## 10. Benchmark and Profile Plan

- Benchmark: `Not applicable`
- Profile: `Not applicable`
- Dataset and distribution: `Not applicable`
- Metrics: `Not applicable`
- Baseline: `Not applicable`

## 11. Risks and Mitigations

| Risk | Impact | Mitigation |
| --- | --- | --- |
| 方案文件成为形式记录 | 实现范围仍可能漂移 | 将审批、验收、状态、提交和归档纳入 Definition of Done |
| 任务状态长期不更新 | Agent 无法恢复上下文 | 每次会话开始和结束都检查并更新任务状态 |
| 方案未审批就修改生产代码 | 架构和范围不可控 | 规范明确禁止，任务状态必须为 `Approved` 才能实现 |

## 12. Rollback Plan

删除本任务新增的 `tasks/` 文件并回退三份 `.codex/` 文档变更即可，不影响生产代码、构建配置和数据格式。

## 13. Verification Commands

```text
git status --short --branch
git diff --check
mvn verify
git diff --cached --check
git status --short --branch
git log -1 --oneline --decorate
git show --stat --oneline HEAD
```

## 14. Git Commit Plan

计划提交信息：

```text
docs(workflow): add task-first development workspace
```

提交边界和拆分策略：

- 一个提交包含任务工作区、规范更新和上下文更新。
- 不包含生产代码或无关格式化。

## 15. Approval Record

| Date | Reviewer | Decision | Notes |
| --- | --- | --- | --- |
| 2026-08-19 | Human Developer | Approved | 根据用户明确要求建立 task 工作区并固化方案先行流程 |
| 2026-08-19 | Codex | Completed | 验收标准已满足 |

## 16. Implementation Log

| Date | Status | Summary | Verification |
| --- | --- | --- | --- |
| 2026-08-19 | Approved | 创建任务工作区、模板和下一阶段方案 | 文档检查、Maven Verify |
| 2026-08-19 | Completed | 完成规范更新并完成 Git 提交 | `mvn verify` 通过，提交后状态干净 |

## 17. Completion Checklist

- [x] Scope and acceptance criteria satisfied
- [x] Tests added or updated
- [x] Build passed
- [x] Static or format checks passed
- [x] Benchmark or profile completed when applicable
- [x] Documentation updated
- [x] `AGENT_CONTEXT.md` updated
- [x] Diff reviewed
- [x] Commit created
- [x] Post-commit Git status confirmed
