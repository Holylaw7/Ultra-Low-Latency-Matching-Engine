# Task Plan — TASK-20260821-016

## 1. Metadata

| Field | Value |
| --- | --- |
| Task ID / Title | `TASK-20260821-016` / Phase 5 Deterministic Command Replay |
| Status | `Completed` |
| Owner / Implementer | Human Developer / Codex |
| Created / Updated | `2026-08-21` |
| Phase / ADR / Blueprint | Phase 5 / ADR-0013 / `PHASE-5-command-wal-and-replay-blueprint.md` |
| Authorization Mode | `Blueprint` |
| Depends On | TASK-015 exact-SHA evidence PASS |
| Current Stage / Next Gate | Completed / Archived in Phase 5 baseline |
| Branch / CI | `feature/phase5-command-wal-replay` / [run 32466659845](https://github.com/Holylaw7/Ultra-Low-Latency-Matching-Engine/actions/runs/32466659845) PASS |

## 2. Background and Goal

Turn a strictly validated closed WAL into a deterministic command source for a
new genesis `MatchingEngine`. Produce an immutable ordered result transcript
and canonical SHA-256 digest, then prove equality with direct execution and a
fixed future public-command probe stream.

## 3. Non-Goals

- Snapshot/state restoration or online service restart;
- live pipeline integration or durable acknowledgement;
- full internal OrderBook state export/hash;
- skipping rejected commands or repairing WAL;
- changing MatchingEngine, Domain, OrderBook or Pipeline production files.

## 4. Requirements and Acceptance Criteria

- [x] replay requires a closed, strictly valid WAL starting at Sequence 1;
- [x] every record is applied exactly once in physical/logical order;
- [x] result collections and nested maker/taker order remain significant;
- [x] at least 1,024 commands equal direct execution results;
- [x] two independent replays produce equal ordered transcripts and digest;
- [x] fixed future public commands behave equally after direct/replayed prefix;
- [x] digest has canonical field framing and no `toString`, object identity,
  locale, clock or platform-default charset dependency;
- [x] engine rejection identifies command Sequence and stops replay;
- [x] no full Recovery or state-hash claim is made;
- [x] frozen production-path diff remains zero and full build passes.

## 5. Current Implementation and Scope

The engine already deterministically regenerates TradeId/EventSequence from
genesis. TASK-015 provides ordered commands. This Task adds the offline replay
orchestration and transcript digest only in the new `recovery` package.

## 6. Design and ADR Linkage

`CommandWalReplayer` owns one new `MatchingEngine` for one replay invocation.
It returns immutable ordered public results plus metadata/digest. Digest input
uses explicitly framed primitive/domain fields in result order.

| Field | Value |
| --- | --- |
| ADR | ADR-0013 (`Approved`) |
| Decision Summary | D2, D4, D8-D10 define authority, strict replay and frozen boundary |
| Scope Boundary | offline replay/digest/tests only; no storage mutation or online recovery |
| Blueprint Status | `Approved — inherited Human Blueprint Approval; dependency-gated` |
| Exception Gates | state exposure, reflection/hook, skip policy, engine/pipeline change |

Alternatives considered: persist derived results, reflect into engine state or
add a production state-export hook. ADR-0013 rejects derived authority and the
Blueprint forbids frozen-core/test-hook changes, so ordered transcript plus
future public probes is selected.

## 7. Planned File Changes

| File or Directory | Change |
| --- | --- |
| `src/main/java/.../recovery/**` | replayer, immutable replay result, canonical digest/error |
| `src/test/java/.../recovery/**` | direct/replay/two-replay/probe/rejection tests |
| cumulative report / Task / Blueprint / context | evidence synchronization |

## 8. Test Plan

- Unit: canonical digest framing/order sensitivity.
- Integration: 1,024+ commands across multiple WAL segments.
- Determinism: direct vs replay A vs replay B ordered equality.
- Public probes: cancel/rest/match suffix after replayed prefix.
- Failure: poison semantic command, invalid WAL delegated as explicit failure.
- No reflection, test hook or private state inspection.

## 9. Benchmark and Profile Plan

Not applicable; TASK-018 measures replay after correctness is complete.

## 10. Risks and Mitigations

| Risk | Mitigation |
| --- | --- |
| transcript mistaken for internal state hash | explicit naming/docs and future probes |
| digest ambiguity | fixed binary field tags/lengths and golden vectors |
| output order lost | ordered lists compared structurally and digest order tested |
| replay hides poison input | fail at exact Sequence; never skip |

## 11. Rollback Plan

Remove the new recovery package/tests. Existing WAL storage remains isolated
and the frozen runtime is unchanged.

## 12. Verification Commands

```text
mvn -pl core -am -Dtest='*CommandWalReplay*,*ReplayTranscript*' test
mvn -pl core -am test
mvn verify
git diff --check
<frozen-path diff audit from Blueprint>
```

## 13. Git Plan

Commit: `feat(recovery): add deterministic command replay`.
Push and require exact-SHA CI PASS before TASK-017.

## 14. Approval, Reports and Implementation Log

| Date | Reviewer | Stage | Decision | Notes |
| --- | --- | --- | --- | --- |
| 2026-08-21 | Human Developer | Proposal creation | `Authorized` | Plan only; implementation awaits Blueprint approval |
| 2026-08-21 | Human Developer | Phase Blueprint Approval | `Approved (Inherited)` | TASK-016 authorized only after TASK-015 evidence PASS; offline replay only; no Snapshot/online Recovery or frozen-core change |

| Stage | Report | Status | Next Gate |
| --- | --- | --- | --- |
| Proposal | Phase 5 proposal report | Approved | TASK-015 evidence |
| Implementation / Verification | cumulative Phase 5 report | Completed | exact-SHA evidence passed |
| Completion | cumulative Phase 5 report | Completed | TASK-017 / Exception Gate |

| Date | Status | Summary | Verification |
| --- | --- | --- | --- |
| 2026-08-21 | Approved | Human Blueprint Approval inherited; execution waits for TASK-015 evidence | dependency-gated |
| 2026-08-21 | Authorized | TASK-015 Evidence Gate passed with exact-SHA CI `32466198050`; TASK-016 may begin | TASK-016 Evidence Gate |
| 2026-08-21 | Completed | Genesis replay, canonical digest, direct comparison, two-run equality and probe suffix implemented | 5 focused tests; `mvn verify` 107 tests; exact-SHA CI `32466659845` PASS; next TASK-017 |

## 15. Completion Checklist

- [x] inherited approval and TASK-015 dependency recorded
- [x] ordered replay/digest/probe criteria satisfied
- [x] tests/full build/Checkstyle/frozen diff pass
- [x] evidence/commit/push/exact-SHA CI recorded
- [x] no unresolved Exception Gate
