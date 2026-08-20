# Task Plan — TASK-20260820-005

## 1. Metadata

| Field | Value |
| --- | --- |
| Task ID | `TASK-20260820-005` |
| Title | Align project documentation with the engineering controller |
| Status | `Completed` |
| Owner | Human Developer |
| Implementer | Codex |
| Created | `2026-08-20` |
| Updated | `2026-08-20` |
| Related Phase | Cross-phase governance documentation |
| Related ADR | `Not required` |
| Current Stage | `Completion` |
| Next Approval Gate | `Human review` |

## 2. Background

The Human Developer replaced `.codex/MASTER_PROMPT.md` with an engineering
controller that separates governance, engineering rules, current state and
task state. Supporting documents still contain the previous lifecycle, report,
Git push and context-maintenance rules.

## 3. Goal

Synchronize the project-facing governance documents and architecture overview
with the new controller, verify internal links and textual consistency, and
create one reviewable documentation commit.

## 4. Non-Goals

- No production, test, benchmark or build changes.
- No new matching, persistence, protocol or performance decision.
- No change to the pending Phase 2 Steady-State Evidence Review gate.
- No remote push or release operation.

## 5. Requirements and Acceptance Criteria

### Requirements

- [x] Treat `MASTER_PROMPT`, `DEVELOPMENT_RULES`, `AGENT_CONTEXT` and `tasks/`
  as the four authoritative layers.
- [x] Align task lifecycle, mandatory stage reports, Git/remote/CI evidence and
  artifact rules.
- [x] Convert `AGENT_CONTEXT` into a compact current-state index.
- [x] Correct stale architecture status and expose the project framework.

### Acceptance Criteria

- [x] Supporting documents do not contradict the new master prompt.
- [x] Verified facts, targets, hypotheses and future work remain distinct.
- [x] Markdown links resolve and `git diff --check` passes.
- [x] The change is prepared as one Conventional Commit.

## 6. Current Implementation and Scope

### Current Implementation

The repository has complete Phase 1 domain work and an implemented Phase 2
OrderBook baseline. Measurement-isolation evidence is complete and pending
Human review. The new master prompt is an uncommitted Human-authored change.

### In Scope

- `.codex/DEVELOPMENT_RULES.md`
- `.codex/AGENT_CONTEXT.md`
- `tasks/README.md`
- `tasks/TEMPLATE.md`
- `README.md`
- `docs/README.md`
- `docs/architecture/overview.md`
- This task plan and its stage report

### Out of Scope

- `.codex/MASTER_PROMPT.md` content changes beyond preserving the Human edit
- Production source, tests, benchmarks, ADR decisions and prior reports

## 7. Design Proposal

Use the new master prompt as the governance source of truth. Keep detailed
engineering constraints in `DEVELOPMENT_RULES`, current facts and links in a
compact `AGENT_CONTEXT`, execution state in `tasks/`, and stable architecture
facts in `docs/`.

### Alternatives Considered

| Option | Advantages | Risks or Costs | Result |
| --- | --- | --- | --- |
| Patch only `AGENT_CONTEXT` | Small diff | Leaves direct contradictions elsewhere | Rejected |
| Rewrite historical reports | Uniform wording | Alters historical evidence and approvals | Rejected |
| Align live governance/index documents | Preserves history and removes active conflicts | Multi-file documentation diff | Selected |

### ADR Linkage

| Field | Value |
| --- | --- |
| ADR | `Not required` |
| Status | `Not applicable` |
| Decision Summary | Documentation synchronization only |
| Scope Boundary | No architecture, runtime, protocol or data-format change |

## 8. Planned File Changes

The in-scope files listed above will be updated for source-of-truth ownership,
stage reporting, repository synchronization, current-state indexing and the
verified/planned project framework.

## 9. Test Plan

- Unit / integration / system / replay tests: `Not applicable` (documentation only).
- Validate repository links and tracked paths.
- Run `git diff --check` and inspect the complete diff.

## 10. Benchmark and Profile Plan

- Benchmark: `Not applicable`
- Profile: `Not applicable`
- Dataset, metrics and baseline: `Not applicable`

## 11. Risks and Mitigations

| Risk | Impact | Mitigation |
| --- | --- | --- |
| Historical evidence is rewritten | Approval trail becomes unreliable | Do not edit prior ADRs or reports |
| Governance layers duplicate or conflict | Future sessions recover incorrect state | State one owner for each information class |
| Human master-prompt edit is obscured | Reviewability is reduced | Include it unchanged in the same alignment commit |

## 12. Rollback Plan

Revert the documentation commit. No runtime, protocol, persistence or data
migration rollback is required.

## 13. Verification Commands

```text
git status --short --branch
git diff --check
git diff --stat
git diff
rg --files
git diff --cached --check
git diff --cached
```

## 14. Git Plan

```text
docs(governance): align documentation with engineering controller
```

One logical commit; no push because no remote is configured.

## 15. Approval Record

| Date | Reviewer | Stage | Decision | Constraints / Notes |
| --- | --- | --- | --- | --- |
| 2026-08-20 | Human Developer | Task approval and execution | Approved | Align to the new master prompt and commit; then output the overall project framework |

## 16. Phase Reports and Approval Gates

| Stage | Report Location | Status | Next Approval Gate | Human Approval |
| --- | --- | --- | --- | --- |
| Documentation Synchronization | `tasks/reports/PHASE-GOVERNANCE-master-prompt-documentation-alignment.md` | Completed | Completion | Authorized 2026-08-20 |
| Completion | Same report | Completed | Human review | Requested in report |

## 17. Implementation Log

| Date | Status | Summary | Verification |
| --- | --- | --- | --- |
| 2026-08-20 | In Progress | Context recovered; active governance conflicts identified | Repository and document inspection |
| 2026-08-20 | Completed | Governance, current-state, task and architecture documents aligned | `git diff --check`, path and full-diff review |

## 18. Completion Checklist

- [x] Scope and acceptance criteria satisfied
- [x] Tests/benchmarks are not applicable
- [x] Documentation and context synchronized
- [x] ADR linkage verified as not required
- [x] Stage report completed
- [x] Diff reviewed
- [x] Commit prepared; final hash and post-commit status reported externally
