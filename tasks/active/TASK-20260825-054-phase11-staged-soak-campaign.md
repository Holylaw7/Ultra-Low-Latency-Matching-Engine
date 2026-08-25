# Task Plan — TASK-20260825-054

## 1. Metadata

| Field | Value |
| --- | --- |
| Task ID / Title | `TASK-20260825-054` — Staged GA Soak Campaign |
| Status | `Proposed — Human Soak Gates Locked` |
| Phase / ADR | Phase 11 / [ADR-0019](../../docs/adr/ADR-0019-ga-qualification-rc-immutability-and-release-authority.md) |
| Blueprint | [Phase 11](../blueprints/PHASE-11-ga-qualification-and-product-release-blueprint.md) — Proposed |
| Depends On | TASK-053 PASS and separate Human two-hour approval |
| Gates | G6, G8 |

## 2. Goal

Execute an immutable two-hour soak, stop for Human review, then—only after a
new Human approval—execute the six-hour soak.

## 3. Acceptance Criteria Per Run

- [ ] Duration/accepted floors: 2h/1.44M and 6h/4.32M respectively.
- [ ] Unexpected error, invalid trade, lost command, sequence gap and state
  divergence counts equal zero.
- [ ] Runtime checkpoint equals strict WAL recovery checkpoint; transcript and
  public probe agree.
- [ ] Chronological post-GC and thread/file/temp-resource guards pass.
- [ ] Final-window P99 regresses <=20% versus first window.
- [ ] Recovery, listener, lease and shutdown checks pass.
- [ ] Immutable manifests/artifacts/hashes and JFR/GC data remain complete.

## 4. Governance

Run A cannot authorize Run B. Run B is locked until Human accepts Run A.
FAIL/ABORTED stops execution; no Run C, automatic restart, sample filtering or
configuration change.

## 5. Evidence Gate

Per-run and campaign identity/hash evaluation; verifier; mandatory
benchmark-reviewer; docs-auditor; full regression/frozen audit/exact-SHA CI.

## 6. Failure / Rollback / Approval

Candidate correctness/resource defect is B0/B1. Environmental abort is B3.
No production remediation is authorized. This Task remains locked until both
its dependency and each explicit Human run gate are met.

## 7. Background / Current Implementation

TASK-052 will validate the runner only. No 2h/6h GA soak evidence exists.
Historical one-hour runs remain reference/non-participating evidence.

## 8. Requirements, Inputs, Outputs and Non-Goals

Input: exact candidate/controller/environment and frozen soak manifest. Output:
one immutable 2h run, Human review record, one immutable 6h run, campaign/G6/G8
results. Non-goals: automatic continuation, replacement, production fix,
changed rate/heap/GC or memory-leak-free/SLA claim.

## 9. Design / Alternatives / Decision

Selected: sequential A then manual gate then B, because a failed 2h run should
not spend six more hours or be hidden. Rejected: combined uninterrupted 8h,
parallel runs and retry until PASS.

## 10. Planned File Changes

No implementation file expected. Outputs are ignored immutable raw artifacts,
`tasks/reports/PHASE-11-task-054.md` and status/evidence documents. Code changes
are an Exception Gate.

## 11. Detailed Test / Profile Plan

Benchmark/profile: fixed offered rate and full latency distribution, JFR/GC,
heap/RSS, allocation, CPU, thread/file/temp/WAL/Snapshot/recovery evidence.
Replay/probe/checkpoint equality is required after each run.

## 12. Verification Commands

```powershell
# Each line requires its separate Human gate.
java -jar qualification/target/matching-engine-qualification.jar ga-soak --lane 2h --campaign ga-g6-2h-v1
java -jar qualification/target/matching-engine-qualification.jar ga-evidence-verify --campaign ga-g6-2h-v1
java -jar qualification/target/matching-engine-qualification.jar ga-soak --lane 6h --campaign ga-g6-6h-v1
java -jar qualification/target/matching-engine-qualification.jar ga-evidence-verify --campaign ga-g6-6h-v1
mvn verify
git diff --check
```

## 13. Rollback, Gates, Approval and Log

Evidence is immutable and cannot be rolled back. ABORTED replacement is Human-
gated; candidate defect is rc.2.

### Risks

Long-run resource cost, runner interruption and hidden drift are controlled by
staged Human gates, immutable evidence and zero automatic replacement.

| Stage | Status | Gate |
| --- | --- | --- |
| 2h | Not Authorized | explicit Human 2h approval |
| 6h | Not Authorized | 2h accepted + explicit Human 6h approval |
| Completion | Locked | G6/G8 reviewers + CI |

| Date | Reviewer / status | Record |
| --- | --- | --- |
| 2026-08-25 | Human / Pending | Neither soak authorized |
| 2026-08-25 | Proposed | No run |

### Implementation Log

No soak has begun; the preceding dated rows are the initial log.
