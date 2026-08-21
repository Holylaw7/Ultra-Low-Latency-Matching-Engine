# Task Plan — TASK-20260821-014

## 1. Metadata

| Field | Value |
| --- | --- |
| Task ID | `TASK-20260821-014` |
| Title | Phase 5 WAL Format and Command Codec Foundation |
| Status | `Completed` |
| Owner | Human Developer |
| Implementer | Codex |
| Created / Updated | `2026-08-21` |
| Related Phase | Phase 5 — Command WAL and Deterministic Replay Foundation |
| Related ADR | `docs/adr/ADR-0013-command-wal-and-deterministic-replay.md` |
| Phase Blueprint | `tasks/blueprints/PHASE-5-command-wal-and-replay-blueprint.md` |
| Authorization Mode | `Blueprint` |
| Current Stage | `Completed / Archived in Phase 5 baseline` |
| Next Gate | `None; Phase 6 not authorized` |
| Branch | `feature/phase5-command-wal-replay` after approval |
| Baseline | `v0.3.0-engineering-baseline` / approved proposal commit |
| CI | [run 32464648365](https://github.com/Holylaw7/Ultra-Low-Latency-Matching-Engine/actions/runs/32464648365) PASS for `e5e4c967` |

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

- [x] exact 32-byte segment header codec and validation;
- [x] exact 52-byte submit-limit and 28-byte cancel record encoding;
- [x] explicit big-endian order, version/type/side codes and zero reserved bytes;
- [x] CRC32C coverage exactly matches ADR-0013;
- [x] maximum length checked before buffer allocation;
- [x] round-trip preserves immutable command equality;
- [x] golden-byte fixtures detect accidental format changes;
- [x] configuration validates directory, segment bound and non-null durability;
- [x] frozen production-path diff remains zero;
- [x] focused tests and full `mvn verify` pass.

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
| Status | `Approved` |
| Decision Summary | D2-D4, D6 and D10 define authority, exact format, validation and dependency boundary |
| Scope Boundary | codec/contracts/tests only; no I/O or frozen-file change |

| Field | Value |
| --- | --- |
| Blueprint Status | `Approved — inherited Human Blueprint Approval` |
| Authorized Task / Stages | TASK-014 implementation and evidence stage only |
| Exception Gates | format/CRC/type change, new dependency/command, frozen-file change |

Architecture impact: ADR-0013 approved through the Human Phase Blueprint;
implementation remains limited to the new codec/configuration boundary.

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

Commit: `feat(wal): add versioned command codec`
(`e5e4c9677ca926c5f774930472d092e42f50008e`). The commit was pushed to
`feature/phase5-command-wal-replay` and passed exact-SHA CI run `32464648365`.

## 14. Approval, Reports and Implementation Log

| Date | Reviewer | Stage | Decision | Notes |
| --- | --- | --- | --- | --- |
| 2026-08-21 | Human Developer | Proposal creation | `Authorized` | Plan only; implementation awaits Blueprint approval |
| 2026-08-21 | Human Developer | Phase Blueprint Approval | `Approved (Inherited)` | D1-D10 and TASK-014 approved; implement only new WAL codec/configuration files; frozen production paths unchanged |

| Stage | Report | Status | Next Gate |
| --- | --- | --- | --- |
| ADR / Blueprint | Phase 5 proposal report | Approved | TASK-014 Evidence Gate |
| Implementation | cumulative Phase 5 report | Completed | evidence gate passed |
| Completion | cumulative Phase 5 report | Completed | TASK-015 / Exception Gate |

| Date | Status | Summary | Verification |
| --- | --- | --- | --- |
| 2026-08-21 | Approved | Human Blueprint Approval inherited; format/codec implementation authorized | D1-D10 approved; TASK-014 is next |
| 2026-08-21 | Completed | Versioned header/record codec, configuration and golden/invalid tests implemented | 9 focused tests; `mvn verify` 92 tests; exact-SHA CI `32464648365` PASS; next TASK-015 |

## 15. Completion Checklist

- [x] Human Blueprint approval inherited and recorded
- [x] Exact scope/acceptance criteria satisfied
- [x] Tests and full build pass
- [x] Frozen diff zero
- [x] ADR/Task/Blueprint/report/context synchronized
- [x] Commit, push and exact-SHA CI recorded
- [x] No Exception Gate unresolved
