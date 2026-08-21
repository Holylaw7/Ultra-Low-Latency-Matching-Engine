# Task Plan — TASK-20260821-014

## 1. Metadata

| Field | Value |
| --- | --- |
| Task ID | `TASK-20260821-014` |
| Title | Phase 5 WAL Format and Command Codec Foundation |
| Status | `Proposed` |
| Owner | Human Developer |
| Implementer | Codex |
| Created / Updated | `2026-08-21` |
| Related Phase | Phase 5 — Command WAL and Deterministic Replay Foundation |
| Related ADR | `docs/adr/ADR-0013-command-wal-and-deterministic-replay.md` |
| Phase Blueprint | `tasks/blueprints/PHASE-5-command-wal-and-replay-blueprint.md` |
| Authorization Mode | `Blueprint` |
| Current Stage | `Proposal` |
| Next Gate | `Human Phase 5 Blueprint Approval` |
| Branch | `feature/phase5-command-wal-replay` after approval |
| Baseline | `v0.3.0-engineering-baseline` / approved proposal commit |
| CI | `Pending` |

## 2. Background and Goal

The current command API has no persistent representation. Implement the exact
ADR-0013 version-1 binary layout, project-owned configuration/value contracts
and strict encode/decode validation without performing file I/O.

## 3. Non-Goals

- writer, reader, segment lifecycle or file repair;
- MatchingEngine or pipeline integration;
- Snapshot/Recovery/Network;
- Java serialization, JSON, compression or encryption;
- new command types or dependency changes.

## 4. Requirements and Acceptance Criteria

- [ ] exact 32-byte segment header codec and validation;
- [ ] exact 52-byte submit-limit and 28-byte cancel record encoding;
- [ ] explicit big-endian order, version/type/side codes and zero reserved bytes;
- [ ] CRC32C coverage exactly matches ADR-0013;
- [ ] maximum length checked before buffer allocation;
- [ ] round-trip preserves immutable command equality;
- [ ] golden-byte fixtures detect accidental format changes;
- [ ] configuration validates directory, segment bound and non-null durability;
- [ ] frozen production-path diff remains zero;
- [ ] focused tests and full `mvn verify` pass.

## 5. Current Implementation and Scope

### Current Implementation

`SubmitLimitCommand` and `CancelOrderCommand` are immutable engine contracts.
No persistence package or codec exists.

### In Scope

- new `com.ultralatency.matching.persistence.wal` format/configuration types;
- package-internal numeric format constants and command-type mapping;
- pure encode/decode logic;
- focused unit/golden/invalid-format tests.

### Out of Scope

All existing production packages and all filesystem behavior.

## 6. Design and ADR Linkage

Use JDK `ByteBuffer` and `CRC32C`; no reflection and no class-name metadata.
Every unsupported field fails explicitly. Records decode through existing
domain constructors so invalid values cannot bypass domain validation.

| Field | Value |
| --- | --- |
| ADR | ADR-0013 |
| Status | `Proposed` |
| Decision Summary | D2-D4, D6 and D10 define authority, exact format, validation and dependency boundary |
| Scope Boundary | codec/contracts/tests only; no I/O or frozen-file change |

| Field | Value |
| --- | --- |
| Blueprint Status | `Proposed` |
| Authorized Task / Stages | None until Human approval; then TASK-014 only |
| Exception Gates | format/CRC/type change, new dependency/command, frozen-file change |

Architecture impact: ADR required and pending through the Phase Blueprint.

Alternatives considered are recorded normatively in ADR-0013: Java
serialization/JSON and implicit Java layouts are rejected in favor of an exact
manual binary codec.

## 7. Planned File Changes

| File or Directory | Change |
| --- | --- |
| `src/main/java/.../persistence/wal/**` | new format, codec, configuration and value/error types |
| `src/test/java/.../persistence/wal/**` | golden, round-trip and invalid-format tests |
| `tasks/reports/PHASE-5-command-wal-replay.md` | cumulative evidence checkpoint |
| Task/Blueprint/ADR/context | status and exact evidence sync |

## 8. Test Plan

- Unit: every command type, min/max valid domain values, configuration values.
- Invalid: magic/version/header length/type/flags/side/reserved/length/CRC.
- Determinism: repeated encoding of equal commands is byte-identical.
- Filesystem/Replay: not applicable in TASK-014.

## 9. Benchmark and Profile Plan

Not applicable. Codec performance claims are not part of this Task.

## 10. Risks and Mitigations

| Risk | Mitigation |
| --- | --- |
| accidental host byte order | set and golden-test big endian explicitly |
| checksum ambiguity | golden corrupted vectors and exact coverage tests |
| format leaks Java representation | manual field codec only |
| premature extensibility | reject unknown fields; version later by ADR |

## 11. Rollback Plan

Remove the unused new package/types/tests. No persisted production data,
baseline tag or existing API is changed.

## 12. Verification Commands

```text
mvn -pl core -am -Dtest='*Wal*Codec*,*Wal*Format*,*Wal*Configuration*' test
mvn verify
git diff --check
git diff --name-only v0.3.0-engineering-baseline...HEAD -- src/main/java/com/ultralatency/matching/domain src/main/java/com/ultralatency/matching/orderbook src/main/java/com/ultralatency/matching/engine src/main/java/com/ultralatency/matching/pipeline
```

## 13. Git Plan

Commit: `feat(wal): add versioned command codec`.
Push after local gates; record exact-SHA CI before TASK-015.

## 14. Approval, Reports and Implementation Log

| Date | Reviewer | Stage | Decision | Notes |
| --- | --- | --- | --- | --- |
| 2026-08-21 | Human Developer | Proposal creation | `Authorized` | Plan only; implementation awaits Blueprint approval |

| Stage | Report | Status | Next Gate |
| --- | --- | --- | --- |
| ADR / Blueprint | Phase 5 proposal report | Proposed | Human Blueprint Approval |
| Implementation | cumulative Phase 5 report | Not started | evidence gate |
| Completion | cumulative Phase 5 report | Not started | TASK-015 / Exception Gate |

| Date | Status | Summary | Verification |
| --- | --- | --- | --- |
| 2026-08-21 | Proposed | Format/codec plan created; no implementation | documentation review pending |

## 15. Completion Checklist

- [ ] Human Blueprint approval inherited and recorded
- [ ] Exact scope/acceptance criteria satisfied
- [ ] Tests and full build pass
- [ ] Frozen diff zero
- [ ] ADR/Task/Blueprint/report/context synchronized
- [ ] Commit, push and exact-SHA CI recorded
- [ ] No Exception Gate unresolved
