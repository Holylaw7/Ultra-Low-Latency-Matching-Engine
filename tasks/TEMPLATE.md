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
| Phase Blueprint | `tasks/blueprints/PHASE-*-blueprint.md` / `Standalone` |
| Authorization Mode | `Blueprint` / `Strict Gate` |
| Current Stage | `ADR / Decision` |
| Next Gate | `Human Blueprint Approval` / `Pending Human Approval` / authorized checkpoint |
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
需要 ADR 的技术决策必须先创建或更新状态为 `Proposed` 的 ADR 草案，再进行直接 Human Review 或 Phase Blueprint Review；不能先做决定或先实现，再补 ADR。
如果不需要 ADR，必须说明不改变架构、协议、数据格式或运行时语义的理由。

### Phase Blueprint Linkage

| Field | Value |
| --- | --- |
| Blueprint | `tasks/blueprints/PHASE-*-blueprint.md` / `Standalone` |
| Blueprint Status | `Proposed` / `Approved` / `Not applicable` |
| Authorized Task / Stages | 精确列出继承授权的 Task 与阶段 |
| Exception Gates | 列出本任务必须停止并返回 Human Review 的条件 |

Blueprint 模式下，Human Phase Blueprint Approval 即为其明确列出 Task 的
Task Approval；必须记录审批日期和范围。未列出的工作不得继承权限。

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

Blueprint 继承审批示例：

| Date | Reviewer | Stage | Decision | Constraints / Notes |
| --- | --- | --- | --- | --- |
| YYYY-MM-DD | Human Developer | Phase Blueprint Approval | `Approved (Inherited)` | 仅限 Blueprint 中列出的 Task、阶段和文件边界 |

## 16. Phase Reports and Approval Gates

至少将任务拆分为以下阶段。Blueprint 模式下，已列出的子阶段通过证据门禁
后可以连续执行；严格门禁、Exception Gate 和 Phase Closure 才等待 Human：

```text
ADR / Decision
    -> Task Approval
    -> Implementation
    -> Verification
    -> Benchmark / Profile（when applicable）
    -> Documentation and Synchronization
    -> Completion
```

| Stage | Report Location | Status | Next Gate | Authorization |
| --- | --- | --- | --- | --- |
| ADR / Decision |  | Pending | Blueprint Approval | Direct / Inherited / Pending |
| Task Approval |  | Pending | First authorized stage | Direct / Inherited / Pending |
| Implementation |  | Pending | Verification / Exception Gate | Blueprint / Strict |
| Verification |  | Pending | Next Task / Phase Closure | Blueprint / Strict |
| Benchmark / Profile |  | `Not applicable` / Pending | Next Task / Phase Closure | Blueprint / Strict |
| Documentation and Synchronization |  | Pending | Phase Closure | Blueprint / Strict |
| Completion |  | Pending | Phase Closure / Completed | Human Closure / Blueprint |

每个完成 Task 和 Blueprint 指定的 evidence checkpoint 必须在
`tasks/reports/` 建立或更新报告。报告开头使用状态面板记录 Phase、Task、
Stage、Result、Tests、Build、CI、Commit 和 Next Gate；正文至少记录目标、
实际完成内容、修改范围、验证证据、性能证据（适用时）、ADR 对齐、Git
证据、方案偏差、风险/限制、项目影响和未验证内容。Blueprint 已授权的
下一阶段在证据门禁通过且无 Exception Gate 时可以直接继续。

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
- [ ] Blueprint-required Task / checkpoint reports exist
- [ ] Human Blueprint, Exception and Closure approvals are recorded as applicable
- [ ] ADR, task plan, rules, project documents, and `AGENT_CONTEXT.md` are synchronized
- [ ] `AGENT_CONTEXT.md` updated
- [ ] Diff reviewed
- [ ] Commit created
- [ ] Remote synchronization completed or explicitly recorded as unavailable/not applicable
- [ ] CI status recorded when observable
- [ ] Post-commit Git status confirmed
