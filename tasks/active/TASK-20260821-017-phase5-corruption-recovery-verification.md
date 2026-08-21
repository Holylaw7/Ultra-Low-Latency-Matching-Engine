# Task Plan — TASK-20260821-017

## 1. Metadata

| Field | Value |
| --- | --- |
| Task ID / Title | `TASK-20260821-017` / Phase 5 Corruption and Recovery-Boundary Verification |
| Status | `Completed` |
| Owner / Implementer | Human Developer / Codex |
| Created / Updated | `2026-08-21` |
| Phase / ADR / Blueprint | Phase 5 / ADR-0013 / `PHASE-5-command-wal-and-replay-blueprint.md` |
| Authorization Mode | `Blueprint` |
| Depends On | TASK-016 exact-SHA evidence PASS |
| Current Stage / Next Gate | Completed / Evidence Gate Passed / TASK-018 Evidence Gate |
| Branch / CI | `feature/phase5-command-wal-replay` / [run 32467018067](https://github.com/Holylaw7/Ultra-Low-Latency-Matching-Engine/actions/runs/32467018067) PASS |

## 2. Background and Goal

Create auditable evidence that the Phase 5 format/storage/replay baseline fails
closed and repairs only the narrowly approved incomplete final tail. This Task
adds tests and fixtures; production changes are limited to fixes within the new
Phase 5 packages when tests expose implementation defects.

## 3. Non-Goals

- new product capability, API or format;
- fault injection hooks in production code;
- Snapshot/online Recovery, live pipeline/WAL integration or Network;
- filesystem/power-loss guarantees unavailable to deterministic tests;
- weakening assertions to accommodate implementation behavior.

## 4. Requirements and Acceptance Criteria

- [x] truncate a valid WAL at every byte offset of the final record and classify
  each outcome deterministically;
- [x] strict scan never mutates a torn tail;
- [x] explicit reopen preserves the valid prefix and truncates only eligible
  final-tail bytes;
- [x] bit flips in header/body/checksum fail with segment/offset evidence;
- [x] complete invalid final record is hard corruption, not a torn tail;
- [x] missing/duplicate/misnamed segments and sequence gaps fail closed;
- [x] repeated repaired reopen is stable and does not lose valid records;
- [x] replay after eligible repair equals the valid direct prefix;
- [x] poison engine command stops replay at exact Sequence;
- [x] test matrix repeats without sleeps, reflection or production hooks;
- [x] full regression, frozen diff and exact-SHA CI pass.

## 5. Current Implementation and Scope

TASK-014..016 provide the behavior under test. Scope is new WAL/recovery tests,
small deterministic byte fixtures and defect fixes inside the new Phase 5
production packages only.

## 6. Design and ADR Linkage

Generate fixtures from the approved codec, then mutate explicit copied bytes
in Task-owned temporary directories. Assertions compare exception category,
segment, offset, retained command prefix and replay output.

| Field | Value |
| --- | --- |
| ADR | ADR-0013 (`Approved`) |
| Decision Summary | D4, D7-D9 define fail-closed integrity and replay boundary |
| Scope Boundary | verification and in-scope fixes only; no semantic/format change |
| Blueprint Status | `Approved — inherited Human Blueprint Approval; dependency-gated` |
| Exception Gates | required format/policy change, broad salvage, production hook, frozen-file change |

Alternatives considered: mock file channels or broad automatic salvage. Real
temporary files and deterministic byte mutation are selected because they
exercise the public storage boundary without production-only hooks.

## 7. Planned File Changes

| File or Directory | Change |
| --- | --- |
| `src/test/java/.../persistence/wal/**` | exhaustive truncation/corruption/segment tests |
| `src/test/java/.../recovery/**` | repaired-prefix and poison replay tests |
| new Phase 5 production packages | defect fixes only within approved semantics |
| cumulative report / Task / Blueprint / context | evidence synchronization |

## 8. Test Plan

- Parameterized final-record truncation at every byte boundary.
- Header/record/checksum bit flips and exact diagnostics.
- Segment discovery/order/gap/duplicate cases.
- Reopen idempotence and valid-prefix preservation.
- Direct valid prefix vs repaired replay equality.
- Repeat focused suite multiple times plus full regression.

## 9. Benchmark and Profile Plan

Not applicable. Failure verification is correctness evidence.

## 10. Risks and Mitigations

| Risk | Mitigation |
| --- | --- |
| platform-specific file behavior | closed channels, explicit bytes and bounded operations |
| test overfits internals | assert public errors/files/results, not private methods |
| broad repair hides corruption | explicit eligible-tail predicate and negative matrix |
| flaky filesystem timing | no sleeps/concurrent polling; synchronous close/reopen |

## 11. Rollback Plan

Revert tests together with any defect fix they justify. Never remove a valid
failure assertion while retaining behavior it disproves.

## 12. Verification Commands

```text
mvn -pl core -am -Dtest='*Wal*Corruption*,*Wal*Torn*,*Wal*Recovery*,*Replay*Failure*' test
# Repeat the focused test set as defined during implementation without sleep loops.
mvn -pl core -am test
mvn verify
git diff --check
<frozen-path diff audit from Blueprint>
```

## 13. Git Plan

Commit: `test(recovery): verify wal corruption and torn-tail boundaries`.
Push and require exact-SHA CI PASS before TASK-018.

## 14. Approval, Reports and Implementation Log

| Date | Reviewer | Stage | Decision | Notes |
| --- | --- | --- | --- | --- |
| 2026-08-21 | Human Developer | Proposal creation | `Authorized` | Plan only; implementation awaits Blueprint approval |
| 2026-08-21 | Human Developer | Phase Blueprint Approval | `Approved (Inherited)` | TASK-017 authorized only after TASK-016 evidence PASS; fail-closed/torn-tail criteria cannot be weakened |

| Stage | Report | Status | Next Gate |
| --- | --- | --- | --- |
| Proposal | Phase 5 proposal report | Approved | TASK-016 evidence |
| Verification | cumulative Phase 5 report | Completed | exact-SHA evidence passed |
| Completion | cumulative Phase 5 report | Completed | TASK-018 / Exception Gate |

| Date | Status | Summary | Verification |
| --- | --- | --- | --- |
| 2026-08-21 | Approved | Human Blueprint Approval inherited; execution waits for TASK-016 evidence | dependency-gated |
| 2026-08-21 | Authorized | TASK-016 Evidence Gate passed with exact-SHA CI `32466659845`; TASK-017 may begin | TASK-017 Evidence Gate |
| 2026-08-21 | Completed | Exhaustive torn-tail/corruption/segment/replay boundary matrix implemented and repeated | 6 focused tests x3; `mvn verify` 113 tests; exact-SHA CI `32467018067` PASS; next TASK-018 |

## 15. Completion Checklist

- [x] inherited approval and TASK-016 dependency recorded
- [x] exhaustive failure matrix passes repeatedly
- [x] full build/Checkstyle/frozen diff pass
- [x] evidence/commit/push/exact-SHA CI recorded
- [x] no unresolved Exception Gate
