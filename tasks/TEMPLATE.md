# Task Plan — TASK-YYYYMMDD-NNN

## 1. Metadata

| Field | Value |
| --- | --- |
| Task ID | `TASK-YYYYMMDD-NNN` |
| Title |  |
| Status | `Proposed` |
| Owner | Human Developer |
| Implementer | Codex |
| Created | `YYYY-MM-DD` |
| Updated | `YYYY-MM-DD` |
| Related Phase |  |
| Related ADR | `None` |
| Current Stage | `ADR / Decision` |
| Next Approval Gate | `Pending Human Approval` |
| Branch |  |
| Baseline HEAD |  |
| Remote | `Not configured` / remote name |
| CI | `Pending` / `Unavailable` / observed result |

## 2. Background

说明当前状态、问题背景和创建任务的原因。

## 3. Goal

明确本任务要交付的结果。

## 4. Non-Goals

明确本任务不处理的内容，防止实现过程中无边界扩张。

## 5. Requirements and Acceptance Criteria

### Requirements

- [ ]

### Acceptance Criteria

- [ ]

## 6. Current Implementation and Scope

### Current Implementation

说明相关模块、接口、数据结构和当前行为。

### In Scope

-

### Out of Scope

-

## 7. Design Proposal

### Proposed Design

说明目标设计、控制流、数据流和模块边界。

### Alternatives Considered

| Option | Advantages | Risks or Costs | Result |
| --- | --- | --- | --- |
|  |  |  |  |

### Decision

说明推荐方案和选择理由。

### ADR Linkage

必须在决策步骤中明确写入对应 ADR：

| Field | Value |
| --- | --- |
| ADR | `docs/adr/ADR-NNNN-title.md` or `Not required` |
| Status | `Proposed` / `Accepted` / `Accepted with constraints` / `Not applicable` |
| Decision Summary | 与 ADR `Decision` 小节一致的摘要 |
| Scope Boundary | 允许和禁止的实现范围 |

如果需要 ADR，必须在开始实现前创建或更新，并将具体路径写入本任务方案的 `Related ADR` 和本节。
需要 ADR 的技术决策必须先创建或更新状态为 `Proposed` 的 ADR 草案，再进行 Human Review、技术决策和任务审批；不能先做决定或先实现，再补 ADR。
如果不需要 ADR，必须说明不改变架构、协议、数据格式或运行时语义的理由。

### Architecture Impact

- [ ] No architecture change
- [ ] ADR required
- [ ] Human architecture decision required

## 8. Planned File Changes

| File or Directory | Change | Reason |
| --- | --- | --- |
|  |  |  |

## 9. Test Plan

### Unit Tests

- [ ]

### Integration or System Tests

- [ ]

### Failure and Boundary Tests

- [ ]

### Determinism or Replay Tests

- [ ] Not applicable

## 10. Benchmark and Profile Plan

如不涉及性能，请明确填写 `Not applicable`。

- Benchmark: `Not applicable`
- Profile: `Not applicable`
- Dataset and distribution: `Not applicable`
- Metrics: `Not applicable`
- Baseline: `Not applicable`

## 11. Risks and Mitigations

| Risk | Impact | Mitigation |
| --- | --- | --- |
|  |  |  |

## 12. Rollback Plan

说明如何撤销本任务变更，以及是否涉及数据格式、协议或持久化兼容性。

## 13. Verification Commands

```text
列出实现前、实现中和提交前需要执行的命令。
```

## 14. Git Plan

计划提交信息：

```text
<type>(<scope>): <imperative summary>
```

提交边界和拆分策略：

-

Remote、Push 和 CI 计划：

- Remote:
- Push: `Not configured` / `Planned after approval` / `Not applicable`
- CI verification:

## 15. Approval Record

| Date | Reviewer | Stage | Decision | Constraints / Notes |
| --- | --- | --- | --- | --- |
|  |  |  | Pending |  |

## 16. Phase Reports and Approval Gates

至少将任务拆分为以下阶段，并在每个阶段完成后填写报告，等待 Human 审批：

```text
ADR / Decision
    -> Task Approval
    -> Implementation
    -> Verification
    -> Benchmark / Profile（when applicable）
    -> Documentation and Synchronization
    -> Completion
```

| Stage | Report Location | Status | Next Approval Gate | Human Approval |
| --- | --- | --- | --- | --- |
| ADR / Decision |  | Pending | `Pending Human Approval` |  |
| Task Approval |  | Pending | `Pending Human Approval` |  |
| Implementation |  | Pending | `Pending Human Approval` |  |
| Verification |  | Pending | `Pending Human Approval` |  |
| Benchmark / Profile |  | `Not applicable` / Pending | `Pending Human Approval` |  |
| Documentation and Synchronization |  | Pending | `Pending Human Approval` |  |
| Completion |  | Pending | `Pending Human Approval` |  |

每个完成阶段必须在 `tasks/reports/` 建立独立报告。报告开头使用状态面板记录 Phase、Task、Stage、Result、Tests、Build、CI、Commit 和 Next Gate；正文至少记录目标、实际完成内容、修改范围、验证证据、性能证据（适用时）、ADR 对齐、Git 证据、方案偏差、风险/限制、项目影响、未验证内容、下一阶段提案和显式审批请求。阶段完成后不得直接进入下一阶段，必须先记录 Human 审批。

## 17. Implementation Log

| Date | Status | Summary | Verification |
| --- | --- | --- | --- |
|  | Proposed |  |  |

## 18. Completion Checklist

- [ ] Scope and acceptance criteria satisfied
- [ ] Tests added or updated
- [ ] Build passed
- [ ] Static or format checks passed
- [ ] Benchmark or profile completed when applicable
- [ ] Documentation updated
- [ ] Decision and ADR linkage verified
- [ ] ADR existed before the technical decision and task approval
- [ ] Every completed stage has a phase report
- [ ] Human approval is recorded before each next stage
- [ ] ADR, task plan, rules, project documents, and `AGENT_CONTEXT.md` are synchronized
- [ ] `AGENT_CONTEXT.md` updated
- [ ] Diff reviewed
- [ ] Commit created
- [ ] Remote synchronization completed or explicitly recorded as unavailable/not applicable
- [ ] CI status recorded when observable
- [ ] Post-commit Git status confirmed
