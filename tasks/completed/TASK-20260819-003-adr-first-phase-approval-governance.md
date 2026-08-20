# Task Plan - TASK-20260819-003

## 1. Metadata

| Field | Value |
| --- | --- |
| Task ID | `TASK-20260819-003` |
| Title | Enforce ADR-first decisions and phase approval gates |
| Status | `Completed` |
| Owner | Human Developer |
| Implementer | Codex |
| Created | `2026-08-19` |
| Updated | `2026-08-19` |
| Related Phase | Project Governance |
| Related ADR | [`ADR-0006-adr-first-decision-governance.md`](../../docs/adr/ADR-0006-adr-first-decision-governance.md) |
| Current Stage | `Completed` |
| Next Approval Gate | `None (Completed)` |

## 2. Background

现有规范要求重要决策关联 ADR，但没有明确 ADR 草案必须先于技术决策形成；任务也没有要求每个开发阶段完成后输出阶段报告并等待审批。需要统一开发规范、任务模板、任务工作区说明、ADR 和 Agent 上下文。

## 3. Goal

建立以下强制流程：

```text
识别决策 -> 创建/更新 ADR 草案 -> Human 审批决策
-> 批准任务方案 -> 完成一个开发阶段 -> 输出阶段报告
-> Human 审批 -> 进入下一阶段 -> 文档与上下文同步
```

## 4. Non-Goals

- 不修改生产代码、测试代码、构建配置或运行时行为。
- 不改变现有撮合语义、架构实现或 ADR-0005 的领域决策。
- 不执行 `git push`。

## 5. Requirements and Acceptance Criteria

### Requirements

- [x] 明确 ADR 草案先于技术决策和任务审批。
- [x] 明确每个开发阶段完成后必须输出阶段报告。
- [x] 明确阶段报告完成后必须等待并记录 Human 审批。
- [x] 明确审批不通过或范围变化时必须更新相关 ADR/任务方案并重新审批。
- [x] 对齐规范、任务工作区、模板、ADR 和 Agent 上下文。

### Acceptance Criteria

- [x] `DEVELOPMENT_RULES.md`、`MASTER_PROMPT.md`、`tasks/README.md` 和 `tasks/TEMPLATE.md` 使用一致的阶段门禁描述。
- [x] ADR-0006 状态、决策内容和审批记录完整。
- [x] `AGENT_CONTEXT.md` 记录生效的治理规则和当前状态。
- [x] 没有生产代码或运行时行为变更。
- [x] Markdown 文档检查通过，Git Diff 可审查。

## 6. Current Implementation and Scope

### Current Implementation

项目已有 `tasks/` 任务工作区、ADR 关联规则和 `.codex/` Agent 规范。`ADR-0006` 已作为本治理变更的 ADR 草案存在，现需补充阶段审批门禁并完成对齐。

### In Scope

- `.codex/DEVELOPMENT_RULES.md`
- `.codex/MASTER_PROMPT.md`
- `.codex/AGENT_CONTEXT.md`
- `tasks/README.md`
- `tasks/TEMPLATE.md`
- `docs/adr/ADR-0006-adr-first-decision-governance.md`
- 本任务方案

### Out of Scope

- `src/`
- `core/`
- `benchmark/`
- 其他业务 ADR 的历史内容

## 7. Design Proposal

### Proposed Design

以 ADR-0006 作为治理规则的长期决策来源。规范文件负责约束行为，任务 README 和模板负责落地任务状态、阶段报告和审批记录，`AGENT_CONTEXT.md` 负责记录当前生效规则和上下文恢复要求。

### Decision

采用 ADR-first 决策顺序，并为每个任务阶段设置阶段报告审批门禁。当前用户明确要求即作为本任务的 Human 审批依据。

### ADR Linkage

| Field | Value |
| --- | --- |
| ADR | [`docs/adr/ADR-0006-adr-first-decision-governance.md`](../../docs/adr/ADR-0006-adr-first-decision-governance.md) |
| Status | `Accepted` |
| Decision Summary | 技术决策必须先创建或更新 `Proposed` ADR，再进行 Human 决策和任务审批；每个开发阶段完成后必须输出阶段报告、等待并记录 Human 审批，批准后才能进入下一阶段；完成阶段必须同步文档和 Agent 上下文。 |
| Scope Boundary | 仅修改开发治理文档和任务模板；不改变生产代码和运行时行为。 |

### Architecture Impact

- [x] No production architecture change
- [x] Governance ADR required: `ADR-0006-adr-first-decision-governance.md`
- [x] Human decision recorded

## 8. Planned File Changes

| File or Directory | Change | Reason |
| --- | --- | --- |
| `.codex/DEVELOPMENT_RULES.md` | 增加 ADR-first 和阶段审批硬门禁 | 固化开发规范 |
| `.codex/MASTER_PROMPT.md` | 对齐 Agent 执行顺序和阶段报告要求 | 约束会话行为 |
| `.codex/AGENT_CONTEXT.md` | 记录生效治理决策和同步要求 | 支持上下文恢复 |
| `tasks/README.md` | 对齐任务生命周期和阶段报告规则 | 统一工作区说明 |
| `tasks/TEMPLATE.md` | 增加阶段、报告和审批字段 | 确保新任务可执行 |
| `docs/adr/ADR-0006-adr-first-decision-governance.md` | 记录治理决策和审批结果 | 长期保留决策依据 |
| `tasks/active/` | 创建本任务方案 | 追踪本次治理变更 |

## 9. Test Plan

### Unit Tests

- [x] Not applicable; no production code changed.

### Integration or System Tests

- [x] Not applicable; perform cross-document consistency checks.

### Failure and Boundary Tests

- [x] 检查 Proposed ADR、Pending Approval、Rejected 和同步要求是否有明确行为。

### Determinism or Replay Tests

- [x] Not applicable.

## 10. Benchmark and Profile Plan

- Benchmark: `Not applicable`
- Profile: `Not applicable`
- Dataset and distribution: `Not applicable`
- Metrics: `Document consistency`
- Baseline: Existing governance documents

## 11. Risks and Mitigations

| Risk | Impact | Mitigation |
| --- | --- | --- |
| 规范文件之间出现不同阶段名称 | Agent 执行歧义 | 统一使用 ADR/Decision、Task Approval、Implementation、Verification、Documentation and Synchronization |
| 阶段报告只停留在口头沟通 | 无法恢复审批依据 | 要求记录在任务方案中，必要时链接 `tasks/reports/` |
| 小任务被过度流程化 | 协作成本增加 | 允许将多个原子动作合并为一个有明确边界的开发阶段，但阶段完成仍需报告和审批 |

## 12. Rollback Plan

删除本任务新增方案并回退本任务涉及的治理文档变更即可，不影响生产代码、测试、构建配置或运行时行为。

## 13. Verification Commands

```text
git diff --check
git status --short
检查 ADR、规范、任务 README、任务模板和 AGENT_CONTEXT 的阶段名称、审批状态和同步要求
```

## 14. Git Commit Plan

计划提交信息：

```text
docs(workflow): enforce adr-first phase approvals
```

提交边界和拆分策略：

- 一个提交包含 ADR、规范、任务工作区和上下文同步。
- 不包含生产代码、测试代码或构建产物。

## 15. Approval Record

| Date | Reviewer | Stage | Decision | Notes |
| --- | --- | --- | --- | --- |
| 2026-08-19 | Human Developer | Task Plan and ADR | Approved | 用户明确要求 ADR 先于决策，并要求每个开发阶段完成后输出报告、等待审批后再进入下一阶段，完成后同步文档。 |

## 16. Phase Reports and Approval Gates

| Stage | Report | Gate Status | Human Approval |
| --- | --- | --- | --- |
| ADR Draft and Decision | `ADR-0006` 已补充阶段门禁和决策记录 | Completed | 2026-08-19，Human Developer |
| Task Approval | 已记录任务范围、ADR 链接和治理约束 | Completed | 2026-08-19，Human Developer |
| Documentation Alignment | 已同步规范、主提示、任务说明、模板、README 和上下文 | Completed | 2026-08-19，Human Developer |
| Verification and Synchronization | 已完成一致性检查并同步最终状态 | Completed | 2026-08-19，Human Developer |

每个阶段完成后均先更新本节并记录 Human 审批，再进入下一阶段。本任务的明确用户请求和后续 `continue` 指令构成阶段推进授权；本任务无未审批的后续开发阶段。

## 17. Implementation Log

| Date | Status | Summary | Verification |
| --- | --- | --- | --- |
| 2026-08-19 | Approved | Human Developer 通过明确需求批准本治理任务及 ADR-0006 | 已确认任务范围和决策顺序 |
| 2026-08-19 | In Progress | 更新 ADR-0006，明确 ADR-first 和阶段报告审批门禁 | ADR 内容已完成，待同步其余文档 |
| 2026-08-19 | Completed | 完成所有规范、模板、任务说明、README 和 Agent 上下文同步 | Markdown 内容检查、`git diff --check` 通过 |

## 18. Completion Checklist

- [x] Scope and acceptance criteria satisfied
- [x] No production code changed
- [x] Documentation updated
- [x] ADR status and task approval aligned
- [x] Every completed stage has a phase report
- [x] Human approval recorded before the next stage
- [x] `AGENT_CONTEXT.md` updated
- [x] Cross-document consistency checked
- [x] Diff reviewed
- [x] Commit created or uncommitted reason recorded
- [x] Post-commit Git status confirmed
