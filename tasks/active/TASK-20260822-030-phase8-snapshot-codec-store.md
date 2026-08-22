# Task Plan — TASK-20260822-030

| Field | Value |
| --- | --- |
| Task | `TASK-20260822-030` / Snapshot v1 Codec and Atomic Store |
| Phase / ADR / Blueprint | Phase 8 / ADR-0016 / `PHASE-8-snapshot-checkpoint-and-online-recovery-blueprint.md` |
| Status | Approved — Authorized / Next |
| Depends on | TASK-029 Evidence Gate PASS at `66fc9d2` / CI `32577713667` |
| Manual Gate | No after Blueprint approval; Exception Gate remains active |
| Planned report | `tasks/reports/PHASE-8-task-030.md` |

## Goal

Implement the exact Snapshot v1 binary codec, strict reader, immutable atomic
publisher and offline generator from a closed, strictly validated WAL.

## Authorized Scope After Approval

New production/tests under:

```text
src/main/java/com/ultralatency/matching/persistence/snapshot/**
src/test/java/com/ultralatency/matching/persistence/snapshot/**
```

Reuse frozen WAL v1 readers/replay APIs without changing their byte or
corruption semantics.

## Non-Goals

No live/hot Snapshot, recovery bootstrap, WAL retention/deletion, automatic
fallback, WAL/Protocol format change, network work or new dependency.

## Acceptance Criteria

- [ ] Golden bytes match ADR-0016's 128-byte header, 48-byte order records and
  exact total length.
- [ ] WAL-prefix SHA-256, canonical checkpoint SHA-256 and file CRC32C
  validate.
- [ ] Canonical checkpoint SHA-256 covers checkpoint Sequence, next TradeId,
  next EventSequence, active-order count, record length and exact order payload.
- [ ] Version, flags, reserved bytes, side, numeric values, duplicate OrderId,
  canonical ordering, length/count and configured limits fail closed.
- [ ] Publication uses same-directory temp, complete write, `force(true)`,
  strict read-back and required `ATOMIC_MOVE`.
- [ ] Final Snapshot is immutable and never overwritten.
- [ ] Orphan temp files are ignored; a corrupt latest final file is not.
- [ ] Offline generation succeeds only from closed WAL with strict contiguous
  replay through the checkpoint Sequence.
- [ ] Offline generation acquires the shared exclusive recovery lease before
  scan and holds it through publication; lease contention rejects generation.
- [ ] Segment inventory and exact file sizes remain unchanged from strict scan
  through publication; a concurrent/non-quiescent change rejects generation.

## Evidence Gate

Golden-vector, store integration, concurrent-lease and changed-inventory tests,
full `mvn verify`, Checkstyle 0,
`git diff --check`, approved/frozen-path audit, logical commit, normal push and
exact-SHA CI PASS.

## Exception Gate

Stop for any Snapshot layout change, non-atomic publication fallback, WAL v1
change, hot capture, Snapshot authority/fallback, new dependency or weaker
integrity rule.
