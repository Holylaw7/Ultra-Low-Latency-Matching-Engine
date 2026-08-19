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

## 14. Git Commit Plan

计划提交信息：

```text
<type>(<scope>): <imperative summary>
```

提交边界和拆分策略：

-

## 15. Approval Record

| Date | Reviewer | Decision | Notes |
| --- | --- | --- | --- |
|  |  | Pending |  |

## 16. Implementation Log

| Date | Status | Summary | Verification |
| --- | --- | --- | --- |
|  | Proposed |  |  |

## 17. Completion Checklist

- [ ] Scope and acceptance criteria satisfied
- [ ] Tests added or updated
- [ ] Build passed
- [ ] Static or format checks passed
- [ ] Benchmark or profile completed when applicable
- [ ] Documentation updated
- [ ] Decision and ADR linkage verified
- [ ] `AGENT_CONTEXT.md` updated
- [ ] Diff reviewed
- [ ] Commit created
- [ ] Post-commit Git status confirmed
