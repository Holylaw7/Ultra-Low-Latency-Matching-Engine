# Task Plan — TASK-20260822-027

| Field | Value |
| --- | --- |
| Task | `TASK-20260822-027` / Durability, Failure and Replay Verification |
| Phase / ADR / Blueprint | Phase 7 / ADR-0015 / `PHASE-7-live-durable-command-pipeline-blueprint.md` |
| Status | Implementation Complete; Evidence Gate Pending |
| Scope | Tests, deterministic barriers/fixtures and verification report only |
| Next Gate | TASK-027 Evidence Gate + exact-SHA CI |

## Acceptance

- [x] Append-before-publish order and append failure are dynamically verified.
- [x] Durable-then-FULL, pipeline/handler and disconnect windows are verified
  without reflection or production-only seams; direct outbound write fault
  injection remains an explicit limitation.
- [x] Closed WAL offline replay equals the live ordered transcript/digest/probe.
- [x] Child-process interruption support was evaluated; no process harness is
  added to this tests-only task, so no child-process claim is made.

## Implementation Log

- Added deterministic coordinator failure-matrix coverage for append failure,
  durable-then-`FULL` and post-durability publication failure.
- Added a real WAL + Pipeline live transcript test that compares ordered
  results, SHA-256 digest and a future public probe against offline genesis
  replay.
- Added loopback disconnect and coalesced second-request verification for the
  durable single-session server.
- Reused existing WAL rotation and Pipeline handler-failure tests; no
  production-only seam or frozen-path change was introduced.
