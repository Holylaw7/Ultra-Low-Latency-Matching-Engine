# Task Plan — TASK-20260822-024

| Field | Value |
| --- | --- |
| Task | `TASK-20260822-024` / Durable Integration Contracts |
| Phase / ADR / Blueprint | Phase 7 / ADR-0015 / `PHASE-7-live-durable-command-pipeline-blueprint.md` |
| Status | Implementation Complete; Evidence Gate Pending |
| Scope | New `integration/durable/**` contracts, configuration and focused tests |
| Report | [`PHASE-7-task-024.md`](../reports/PHASE-7-task-024.md) |
| Next Gate | TASK-024 Evidence Gate; exact-SHA CI remains pending until commit/push |

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
- Task remains at the Evidence Gate because no commit, push or exact-SHA CI
  evidence exists yet.
