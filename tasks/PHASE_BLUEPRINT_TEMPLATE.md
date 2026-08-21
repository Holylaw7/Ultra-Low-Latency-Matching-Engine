# Phase Blueprint — PHASE-N

## 1. Executive Status

| Field | Value |
| --- | --- |
| Phase | `Phase N — Name` |
| Blueprint Status | `Proposed` |
| Owner | Human Developer |
| Architect | Codex / model if relevant |
| Created | `YYYY-MM-DD` |
| Updated | `YYYY-MM-DD` |
| Baseline | commit / tag |
| Blueprint Branch |  |
| Planned Tasks | `TASK-...`, `TASK-...` |
| Next Gate | `Human Phase Blueprint Approval` |

## 2. Phase Goal

State the capability the Phase must deliver and why it is the next coherent
engineering boundary.

## 3. Non-Goals and Frozen Boundaries

Explicitly list excluded behavior, unchanged modules, deferred architecture,
unsupported claims and actions that require another Phase or Exception Gate.

## 4. Current State and Dependencies

Record the verified baseline, existing APIs/data formats, upstream/downstream
dependencies, prior ADRs and known limitations.

## 5. ADR Set and Decision Matrix

All required ADR drafts must exist before Human Blueprint Review.

| Decision ID | ADR | Proposed Decision | Scope / Constraint | Approval Result |
| --- | --- | --- | --- | --- |
| D1 | `docs/adr/ADR-NNNN-*.md` |  |  | Pending |

Human Blueprint Approval accepts only the decision rows explicitly marked
approved. Synchronize the result into every linked ADR before implementation.

## 6. Target Architecture

Describe component ownership, control/data flow, sequence and failure
semantics, persistence/recovery boundaries and external contracts.

```text
Input
    -> Component A
    -> Component B
    -> Output
```

## 7. Task Decomposition

| Order | Task | Goal | Depends On | Authorized Scope | Report |
| ---: | --- | --- | --- | --- | --- |
| 1 | `TASK-...` |  | Baseline |  | `tasks/reports/...` |

Every Task must have its own file under `tasks/active/`, link this Blueprint
and record inherited Human Blueprint Approval.

## 8. Stage Authorization Matrix

| Task / Stage | Files or Modules | Deliverable | Evidence Gate | Manual Gate? |
| --- | --- | --- | --- | --- |
| TASK / Stage 1 |  |  | tests + diff + CI | No |

`Manual Gate: No` means execution may continue after all evidence gates pass
and no Exception Gate exists. It does not waive commits, reports or CI.

## 9. Phase Acceptance Criteria and Invariants

### Functional / Correctness

- [ ]

### Determinism / Ordering

- [ ] Not applicable / criteria

### Failure / Recovery

- [ ] Not applicable / criteria

### Compatibility / Boundary

- [ ]

### Completion Evidence

- [ ] All Blueprint Tasks completed
- [ ] Required reports and CI evidence recorded
- [ ] Phase Closure Report approved

## 10. Verification Strategy

| Layer | Required Evidence | Command / Method | Pass Condition |
| --- | --- | --- | --- |
| Unit |  |  |  |
| Integration |  |  |  |
| Determinism / Replay |  |  |  |
| Failure / Recovery |  |  |  |
| Static / Build |  |  |  |
| CI |  |  |  |

## 11. Benchmark and Profile Strategy

Use `Not applicable` when the Phase is correctness-only. Otherwise record
baseline, environment, workload, metrics, profiler and keep/revert criteria.

## 12. Planned Repository Changes

| File or Directory | Task / Stage | Planned Change | Boundary |
| --- | --- | --- | --- |
|  |  |  |  |

## 13. Exception Gates

Execution must stop for Human review when any of these occurs:

- [ ] ADR or invariant conflict
- [ ] scope expansion or unlisted Task/stage/file boundary
- [ ] unapproved public API compatibility break
- [ ] unapproved matching/order/concurrency semantic change
- [ ] protocol/WAL/Snapshot/persistence/recovery format change
- [ ] new critical dependency or different implementation strategy
- [ ] verification exposes an architecture problem
- [ ] an acceptance criterion cannot be met without weakening it
- [ ] destructive Git, Release or other separately governed action

Add Phase-specific exception conditions below:

- [ ]

## 14. Git, Commit and CI Strategy

- Branches:
- Commit sequence:
- Remote push checkpoints:
- Required exact-SHA CI checkpoints:
- Merge strategy:
- History constraints:

## 15. Rollback and Compatibility Plan

Describe per-Task rollback, Phase rollback, data/protocol compatibility and
whether partial rollout is valid.

## 16. Documentation and Evidence Plan

List Task reports, architecture docs, ADR updates, benchmark/recovery evidence,
README and `AGENT_CONTEXT.md` synchronization.

## 17. Closure and Baseline Plan

- Closure report:
- Human Closure Approval criteria:
- Master verification:
- Candidate baseline tag:
- Tag annotation scope:
- Explicitly not a Release:
- Next Phase remains unauthorized until:

## 18. Human Phase Blueprint Approval

| Date | Reviewer | Decision | Approved ADRs / Tasks / Stages | Constraints |
| --- | --- | --- | --- | --- |
|  | Human Developer | Pending |  |  |

```text
Blueprint Status: Proposed
Implementation: Not Authorized
Next Gate: Human Phase Blueprint Approval
```

## 19. Execution Checkpoints

| Date | Task / Stage | Result | Evidence | Next State |
| --- | --- | --- | --- | --- |
|  |  |  |  |  |

## 20. Phase Closure Checklist

- [ ] Blueprint approval recorded and synchronized into ADRs/Tasks
- [ ] All authorized Tasks and acceptance criteria completed
- [ ] All automated evidence gates pass
- [ ] No unresolved Exception Gate
- [ ] Architecture and documentation synchronized
- [ ] Phase Closure Report prepared
- [ ] Human Phase Closure Approval recorded
- [ ] Authorized merge/tag/baseline actions verified
- [ ] Active Tasks moved to completed
- [ ] Next Phase remains explicitly authorized or unauthorized
