# Task Plan — TASK-20260822-025

| Field | Value |
| --- | --- |
| Task | `TASK-20260822-025` / WAL-before-Pipeline Coordinator |
| Phase / ADR / Blueprint | Phase 7 / ADR-0015 / `PHASE-7-live-durable-command-pipeline-blueprint.md` |
| Status | Evidence Gate PASS / Completed |
| Scope | New durable coordinator and tests under `integration/durable/**` |
| Report | [`PHASE-7-task-025.md`](../reports/PHASE-7-task-025.md) |
| Next Gate | Phase 7 Closure Approved; task archived |

## Goal

Implement synchronous `append -> force -> tryPublish` ordering, terminal failure,
durable-then-FULL fail-stop semantics and lifecycle ownership.

## Acceptance

- [x] No publication occurs after append/force failure.
- [x] Durable-then-FULL consumes identity and is never retryable FULL.
- [x] First failure cause is retained; later admission is rejected.
- [x] Existing WAL v1 and Pipeline APIs remain unchanged.

## Implementation Log

- Added `DurableCommandCoordinator` and `DurableTerminalException` under the
  authorized additive integration boundary.
- Added focused ordering, append-failure, durable-then-FULL, pipeline-failure,
  sequence-contract and lifecycle tests.
- Local implementation evidence is recorded in
  [`PHASE-7-task-025.md`](../reports/PHASE-7-task-025.md); commit, push and
  exact-SHA CI passed on commit `2342897` with GitHub Actions run
  `32564005988`. TASK-026 was the next dependency-ordered Evidence Gate at
  this checkpoint; the full Phase 7 task chain is now complete and archived.
