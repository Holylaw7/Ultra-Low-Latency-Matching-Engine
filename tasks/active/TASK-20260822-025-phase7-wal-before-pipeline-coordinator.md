# Task Plan — TASK-20260822-025

| Field | Value |
| --- | --- |
| Task | `TASK-20260822-025` / WAL-before-Pipeline Coordinator |
| Phase / ADR / Blueprint | Phase 7 / ADR-0015 / `PHASE-7-live-durable-command-pipeline-blueprint.md` |
| Status | Approved; TASK-024 Evidence Gate PASS; Authorized / next |
| Scope | New durable coordinator and tests under `integration/durable/**` |
| Next Gate | TASK-025 Evidence Gate |

## Goal

Implement synchronous `append -> force -> tryPublish` ordering, terminal failure,
durable-then-FULL fail-stop semantics and lifecycle ownership.

## Acceptance

- [ ] No publication occurs after append/force failure.
- [ ] Durable-then-FULL consumes identity and is never retryable FULL.
- [ ] First failure cause is retained; later admission is rejected.
- [ ] Existing WAL v1 and Pipeline APIs remain unchanged.
