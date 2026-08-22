# Task Plan — TASK-20260822-024

| Field | Value |
| --- | --- |
| Task | `TASK-20260822-024` / Durable Integration Contracts |
| Phase / ADR / Blueprint | Phase 7 / ADR-0015 / `PHASE-7-live-durable-command-pipeline-blueprint.md` |
| Status | Evidence Gate PASS / Completed |
| Scope | New `integration/durable/**` contracts, configuration and focused tests |
| Report | [`PHASE-7-task-024.md`](../reports/PHASE-7-task-024.md) |
| Next Gate | Phase 7 Closure Approved; task archived |

## Goal

Define immutable durability boundaries, lifecycle states, outcomes and adapter
ports without starting a WAL, pipeline or network runtime.

## Non-Goals

No production integration, Protocol/WAL changes, new dependency, test seam,
Snapshot, Recovery or optimization.

## Acceptance

- [x] Durable/live-accepted/response-completed outcomes are distinct.
- [x] `SYNC_EACH_APPEND` is the only live mode.
- [x] Identity and terminal-state invariants are represented by value types.
- [x] Focused tests, full build, Checkstyle and frozen diff pass.

## Implementation Log

- Contracts/configuration and focused tests implemented under the authorized
  `integration/durable/**` paths.
- Evidence recorded in [`PHASE-7-task-024.md`](../reports/PHASE-7-task-024.md):
  focused tests PASS, `mvn verify` PASS, Checkstyle PASS and frozen-path audit
  PASS.
- Exact-SHA evidence passed on commit
  `93be16638a20cfc2ed16cf3ecd6d5d0b07c885e5`; GitHub Actions run
  `32562594583` passed on `feature/phase7-live-durable-command-pipeline`.
- At this checkpoint TASK-026 was the next dependency-ordered Task. TASK-024
  through TASK-028 subsequently completed, the Phase 7 Closure was approved,
  and this task is archived under the frozen `v0.6.0-engineering-baseline`.
