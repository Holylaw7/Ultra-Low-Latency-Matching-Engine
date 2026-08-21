# Task Plan — TASK-20260821-015

## 1. Metadata

| Field | Value |
| --- | --- |
| Task ID | `TASK-20260821-015` |
| Title | Phase 5 Segmented Command WAL Storage |
| Status | `Approved` |
| Owner / Implementer | Human Developer / Codex |
| Created / Updated | `2026-08-21` |
| Related Phase / ADR | Phase 5 / ADR-0013 |
| Phase Blueprint | `tasks/blueprints/PHASE-5-command-wal-and-replay-blueprint.md` |
| Authorization Mode | `Blueprint` |
| Depends On | TASK-014 evidence PASS |
| Current Stage / Next Gate | Implementation authorized / TASK-015 Evidence Gate |
| Branch / Baseline | `feature/phase5-command-wal-replay` / approved TASK-014 commit |
| CI | `Pending for implementation commit` |

## 2. Background and Goal

Build the synchronous single-writer segmented storage and strict closed-WAL
reader around the approved codec. Implement rotation, durability modes,
exclusive ownership, terminal failures and explicit final torn-tail reopen.

## 3. Non-Goals

- MatchingEngine replay or live pipeline integration;
- background threads, batching, async force or concurrent writers;
- Snapshot/online Recovery/Network;
- best-effort corruption salvage, memory mapping or direct-I/O.

## 4. Requirements and Acceptance Criteria

- [ ] append uses complete writes and exact-next sequence;
- [ ] segment rotation occurs before a record and never splits it;
- [ ] `SYNC_EACH_APPEND` calls `force(true)` before success;
- [ ] `BUFFERED` has no durability claim and is not default;
- [ ] exclusive active-segment lock rejects another writer;
- [ ] close is idempotent and later append is rejected;
- [ ] I/O/force/rotation failure makes writer terminal;
- [ ] a failed force is a logical append failure but does not imply physical
  record absence; strict scan/reopen determines the persisted boundary;
- [ ] strict reader validates all segments, records, CRC and sequences;
- [ ] explicit reopen truncates only an incomplete final physical record;
- [ ] complete-record corruption, earlier truncation and segment gaps fail closed;
- [ ] frozen production-path diff remains zero and full build passes.

## 5. Current Implementation and Scope

TASK-014 will provide format/configuration/codec contracts. This Task adds only
new WAL storage types and filesystem integration tests under Task-owned temp
directories.

In scope: writer, reader, position/scan result, corruption diagnostics,
segment discovery/rotation and reopen recovery. Out of scope: engine calls,
replay digest, benchmarks and every existing production package.

## 6. Design and ADR Linkage

Use synchronous caller-thread `FileChannel` operations. Generated file names
come only from validated positive sequences. Reader order derives from parsed
segment names and validated headers, never filesystem iteration order.

| Field | Value |
| --- | --- |
| ADR | ADR-0013 (`Approved`) |
| Decision Summary | D3-D7 and D10 define format, integrity, ownership, durability and torn-tail rules |
| Scope Boundary | new WAL storage/tests only; no replay or live integration |
| Blueprint Status | `Approved — inherited Human Blueprint Approval; dependency-gated` |
| Exception Gates | broad repair, format change, background I/O, dependency or frozen-file change |

Alternatives considered: one unbounded file, memory mapping, async/background
flush and best-effort salvage are rejected or deferred by ADR-0013. The
synchronous segmented design is selected for explicit correctness boundaries.

The approved Blueprint authorizes this Task only after TASK-014's evidence
gate; format changes, broad repair and frozen-file changes trigger Exception
Gate review.

## 7. Planned File Changes

| File or Directory | Change |
| --- | --- |
| `src/main/java/.../persistence/wal/**` | writer, reader, segment/reopen/failure types |
| `src/test/java/.../persistence/wal/**` | temp-directory integration and failure tests |
| cumulative report / Task / Blueprint / context | evidence synchronization |

## 8. Test Plan

- Integration: single/multi-record, rotation boundary, multi-segment read,
  reopen, close and lock ownership.
- Failure: truncation at envelope/body/checksum bytes, checksum/header/segment
  corruption, sequence gap and complete invalid tail.
- Repeat: deterministic temp fixtures with no sleep or scheduling dependency.
- Replay: not applicable until TASK-016.

## 9. Benchmark and Profile Plan

Not applicable; TASK-018 owns component measurement.

## 10. Risks and Mitigations

| Risk | Mitigation |
| --- | --- |
| partial channel writes | loop with explicit progress checks |
| unsafe truncation | permit only incomplete final physical record after strict prefix validation |
| nondeterministic segment order | parse/sort numeric first sequence and validate continuity |
| platform force semantics | narrow claims to successful JDK/OS `force(true)` boundary |
| file leak | try-with-resources and Windows-compatible close tests |

## 11. Rollback Plan

Revert new storage types/tests. Test WALs are temporary and no deployed data or
existing API is affected.

## 12. Verification Commands

```text
mvn -pl core -am -Dtest='*CommandWalWriter*,*CommandWalReader*,*WalRecovery*' test
mvn -pl core -am test
mvn verify
git diff --check
<frozen-path diff audit from Blueprint>
```

## 13. Git Plan

Commit: `feat(wal): add segmented command storage`.
Push after gates; exact-SHA CI PASS is required before TASK-016.

## 14. Approval, Reports and Implementation Log

| Date | Reviewer | Stage | Decision | Notes |
| --- | --- | --- | --- | --- |
| 2026-08-21 | Human Developer | Proposal creation | `Authorized` | Plan only; implementation awaits Blueprint approval |
| 2026-08-21 | Human Developer | Phase Blueprint Approval | `Approved (Inherited)` | TASK-015 authorized only after TASK-014 evidence PASS; failed force is logical failure, not proof of physical absence; frozen production paths unchanged |

| Stage | Report | Status | Next Gate |
| --- | --- | --- | --- |
| Proposal | Phase 5 proposal report | Approved | TASK-014 evidence |
| Implementation / Verification | cumulative Phase 5 report | Not started | exact-SHA evidence |
| Completion | cumulative Phase 5 report | Not started | TASK-016 / Exception Gate |

| Date | Status | Summary | Verification |
| --- | --- | --- | --- |
| 2026-08-21 | Approved | Human Blueprint Approval inherited; execution waits for TASK-014 evidence | dependency-gated |
| 2026-08-21 | Authorized | TASK-014 Evidence Gate passed with exact-SHA CI `32464648365`; TASK-015 may begin | TASK-015 Evidence Gate |

## 15. Completion Checklist

- [ ] inherited approval and TASK-014 dependency recorded
- [ ] storage/reopen/failure criteria satisfied
- [ ] focused/repeated/full tests and Checkstyle pass
- [ ] frozen diff zero
- [ ] docs/evidence/commit/push/exact-SHA CI recorded
- [ ] no unresolved Exception Gate
