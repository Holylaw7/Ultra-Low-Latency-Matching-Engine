# Phase 5 Command WAL and Deterministic Replay Blueprint Proposal

## Status Dashboard

| Field | Value |
| --- | --- |
| Phase | Phase 5 — Command WAL and Deterministic Replay Foundation |
| Stage | Discovery / Complete Blueprint Proposal |
| Result | `Approved / Implemented / Baseline Frozen` |
| Production Changes | None |
| Tests | Baseline `mvn verify` PASS — 83 tests / 0 failures |
| Build | Maven reactor 3/3 SUCCESS; Checkstyle 0 violations |
| CI | [run 32462826593](https://github.com/Holylaw7/Ultra-Low-Latency-Matching-Engine/actions/runs/32462826593) PASS |
| Commit | `a2a7c758109f1dcf0460a99d8eb4e14f26c07787` |
| Next Gate | Phase 6 Complete Blueprint; not authorized |

## Discovery Outcome

Phase 5 should establish the internal command WAL and strict offline replay
foundation before Network/Protocol work.

```text
EngineCommand
    -> versioned segmented WAL
    -> strict validation / torn-tail policy
    -> offline replay
    -> genesis MatchingEngine
    -> ordered EngineResult transcript + digest + future probes
```

This order follows ADR-0011's accepted command-log authority and avoids making
future network frames, sessions or retry behavior part of recovery semantics.

## Proposed Decisions

| Decision | Proposal |
| --- | --- |
| D1 | WAL/replay foundation precedes Network/Protocol |
| D2 | command stream is authoritative; Trade/Execution remain derived |
| D3 | exact version-1 big-endian segmented binary format |
| D4 | strict length/version/CRC32C/sequence validation |
| D5 | synchronous caller-owned single writer with bounded segments |
| D6 | `SYNC_EACH_APPEND` default; `BUFFERED` evidence-only |
| D7 | only incomplete final physical record is eligible for explicit truncation |
| D8 | closed-WAL genesis replay with ordered transcript, digest and probes |
| D9 | frozen Domain/OrderBook/Engine/Pipeline; no live integration |
| D10 | JDK-only; Snapshot/online Recovery/Network/optimization deferred |

The durable draft is
[`ADR-0013`](../../docs/adr/ADR-0013-command-wal-and-deterministic-replay.md).

## Task Breakdown

| Task | Purpose | Status |
| --- | --- | --- |
| [`TASK-014`](../completed/TASK-20260821-014-phase5-wal-format-codec.md) | exact format, configuration and command codec | Completed / Archived |
| [`TASK-015`](../completed/TASK-20260821-015-phase5-segmented-wal-storage.md) | segmented writer/reader/durability/reopen | Completed / Archived |
| [`TASK-016`](../completed/TASK-20260821-016-phase5-deterministic-replay.md) | strict genesis replay and transcript digest | Completed / Archived |
| [`TASK-017`](../completed/TASK-20260821-017-phase5-corruption-recovery-verification.md) | torn-tail/corruption/sequence/failure evidence | Completed / Archived |
| [`TASK-018`](../completed/TASK-20260821-018-phase5-wal-benchmark-docs.md) | component evidence, documentation and Closure preparation | Completed / Archived |

The complete scope, exact format, file boundaries, acceptance criteria,
verification matrix, benchmark plan, risks, rollback, Git strategy and Closure
plan are in the
[`Phase 5 Blueprint`](../blueprints/PHASE-5-command-wal-and-replay-blueprint.md).

## Frozen Boundary

The proposal authorizes no implementation and plans zero modifications to
existing production files in Domain, OrderBook, Engine or Pipeline. New code,
if the Blueprint is approved, is limited to new WAL/Recovery packages.

`v0.3.0-engineering-baseline` remains immutable. The unrelated untracked
`.vscode/settings.json` is not part of Phase 5 and must remain untouched.

## Evidence and Claim Boundary

The planned evidence proves format/storage integrity and deterministic offline
re-execution. It does not prove:

- a durably acknowledged live matching pipeline;
- Snapshot or online service recovery;
- complete internal-state hashing;
- end-to-end client/trade durability latency;
- hardware power-loss safety beyond documented JDK/OS force semantics;
- production throughput or Release readiness.

## Proposal Verification

- `mvn verify`: PASS;
- tests: 83 passed, 0 failures/errors/skips;
- Checkstyle: 0 violations;
- Maven reactor: 3/3 SUCCESS;
- local Markdown links in ADR/Blueprint/Tasks/report: PASS;
- `git diff --check`: PASS;
- frozen Domain/OrderBook/Engine/Pipeline working-tree changes: zero;
- production, test, build and runtime files changed by this proposal: none;
- `.vscode/` remains unrelated, untracked and untouched;
- proposal content commit: `a2a7c758109f1dcf0460a99d8eb4e14f26c07787`;
- exact-SHA GitHub Actions CI: run
  [32462826593](https://github.com/Holylaw7/Ultra-Low-Latency-Matching-Engine/actions/runs/32462826593)
  PASS.

## Exception Gate Summary

Execution must stop for any format change, frozen-file/API change, live
pipeline integration, new dependency, broader corruption salvage, Snapshot or
Network scope, weakened replay criterion, performance-driven default change or
destructive Git/Release action.

## Human Decision Record

Human Blueprint Approval was recorded for ADR-0013 D1-D10 and TASK-014 through
TASK-018 in dependency order. At that gate it granted no Phase Closure, merge,
tag, Release, Network, Snapshot or online Recovery authority. Human Closure was
later approved; the authorized merge and engineering-baseline tag workflow is
complete. Release, Network, Snapshot, online Recovery and Phase 6 remain
unauthorized.

The implementation interpretation is explicit: a failed write/force/rotation
must never be reported as a successful logical append and makes the writer
terminal, but complete record bytes may physically exist. Strict scan/reopen
determines the valid persisted boundary.

```text
Phase 5 Blueprint: Completed / Approved / Baseline Frozen
Implementation: TASK-014 through TASK-018 completed and archived
Next Action: Phase 6 Complete Blueprint only after Human authorization
```
