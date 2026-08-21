# Task Plan — TASK-20260821-022

## 1. Metadata

| Field | Value |
| --- | --- |
| Task ID / Title | `TASK-20260821-022` / Phase 6 Network Determinism and Failure Verification |
| Status | `Conditionally Authorized` |
| Owner / Implementer | Human Developer / Codex |
| Created / Updated | `2026-08-21` |
| Phase / ADR / Blueprint | Phase 6 / ADR-0014 / [`PHASE-6`](../blueprints/PHASE-6-network-protocol-blueprint.md) |
| Authorization Mode | Blueprint |
| Current Stage / Next Gate | Awaiting TASK-021 evidence / TASK-022 evidence gate |
| Branch / Baseline | `feature/phase6-network-protocol` after approval / approved proposal commit |
| Remote / CI | `origin` / Pending |

## 2. Background

The network baseline requires system evidence across TCP framing, gateway
identity, pipeline ordering and outbound result framing, not only codec units.

## 3. Goal

Prove protocol determinism, fragmented input equivalence, ordered framing,
one-in-flight enforcement, bounded lifecycle and fail-stop behavior using
public contracts.

## 4. Non-Goals

No new feature, performance optimization, live WAL, security, multi-client,
Snapshot, Recovery or production fault-injection seam.

## 5. Requirements and Acceptance Criteria

- [ ] two genesis runs produce byte-identical response streams;
- [ ] fixed mixed Submit/Cancel stream matches direct gateway-assigned commands;
- [ ] every TCP fragmentation boundary for one valid frame is equivalent;
- [ ] the framing layer splits coalesced frames in wire order, while the
  Gateway rejects the second decoded request if the first is still in flight
  and never publishes it;
- [ ] multi-match indices/counts and list ordering are exact;
- [ ] FULL retry preserves request/command identities;
- [ ] malformed frame never mutates pipeline/engine state;
- [ ] second client, disconnect, write/pipeline failures are bounded/terminal;
- [ ] resources are released and tests repeat without flakes;
- [ ] frozen path audit remains zero.

## 6. Current Implementation and Scope

### Current Implementation

Before this Task, TASK-019..021 are expected to provide codec, failure observer
and gateway unit/integration coverage, but not the cumulative determinism and
failure matrix required for Phase Closure.

### In Scope

Tests and deterministic fixtures under `src/test/java/.../network/**`; defect
fixes only within TASK-019..021 approved production files and unchanged ADR
semantics.

### Out of Scope

New features, format/API changes and all Blueprint Non-Goals.

## 7. Design Proposal

Use `EmbeddedChannel` for exhaustive byte/framing cases and real loopback
channels for system lifecycle. Use latches/futures and bounded timeouts, never
sleep. Compare canonical response bytes plus decoded project-owned values.

| Alternative | Advantages | Risks | Result |
| --- | --- | --- | --- |
| public-contract byte/value comparison | durable behavioral proof | fixture work | selected |
| reflection/internal state probes | easy inspection | over-coupled/forbidden | rejected |
| sleep-based socket tests | simple | flaky and scheduler-dependent | rejected |

### ADR / Blueprint Linkage

| Field | Value |
| --- | --- |
| ADR Status | ADR-0014 Approved |
| Decision | verification of D3-D9 |
| Blueprint | Approved; TASK-022 after TASK-021 exact-SHA CI |
| Exception Gates | weakened equality, hooks, architecture/format change |

### Architecture Impact

- [x] No new architecture change
- [x] Existing ADR verification only

## 8. Planned File Changes

| Directory | Change |
| --- | --- |
| `src/test/java/.../network/**` | deterministic vectors/system/failure matrix |
| approved Phase 6 production files | defect fixes only if tests prove mismatch |

## 9. Test Plan

Unit, EmbeddedChannel, loopback integration, repeated determinism and failure
matrix as stated in acceptance criteria. Focused suite repeats at least five
times without timing dependence.

## 10. Benchmark and Profile Plan

Not applicable.

## 11. Risks and Mitigations

| Risk | Mitigation |
| --- | --- |
| flaky sockets | loopback/ephemeral ports/bounded futures/repeat suite |
| testing internals | public bytes and contracts only |
| leaked resources | per-test cleanup assertions |

## 12. Rollback Plan

Revert verification and any paired defect fix. Never delete or weaken a failing
test to preserve implementation.

## 13. Verification Commands

```text
mvn -pl core -am -Dtest=*Network* test
mvn -pl core -am -Dtest=*Network* -Dsurefire.rerunFailingTestsCount=0 test
mvn verify
git diff --check
frozen path audit
```

## 14. Git Plan

`test(network): verify protocol ordering and failure boundaries`; push and
exact-SHA CI before TASK-023.

## 15. Approval Record

| Date | Reviewer | Stage | Decision | Notes |
| --- | --- | --- | --- | --- |
| 2026-08-21 | Human Developer | Proposal | Proposal only | no implementation |
| 2026-08-21 | Human Developer | Blueprint | Approved / Conditional | verification-only scope; start after TASK-021 exact-SHA CI |

## 16. Phase Reports and Approval Gates

| Stage | Report | Status | Next Gate | Authorization |
| --- | --- | --- | --- | --- |
| Approval | Phase 6 proposal | Pending | Blueprint Approval | Pending |
| Verification | cumulative report | Pending | exact-SHA CI | Blueprint |
| Benchmark | Not applicable | N/A | completion | Blueprint |
| Completion | cumulative report | Pending | TASK-023 / Exception Gate | Blueprint |

## 17. Implementation Log

| Date | Status | Summary | Verification |
| --- | --- | --- | --- |
| 2026-08-21 | Proposed | system evidence plan | baseline PASS |

## 18. Completion Checklist

- [ ] full deterministic/failure matrix passes repeatedly
- [ ] build/static/diff/frozen audit pass
- [ ] evidence commit/CI/report recorded
- [ ] no Exception Gate
