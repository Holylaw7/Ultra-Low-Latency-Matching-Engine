# Task Plan — TASK-20260822-027

| Field | Value |
| --- | --- |
| Task | `TASK-20260822-027` / Durability, Failure and Replay Verification |
| Phase / ADR / Blueprint | Phase 7 / ADR-0015 / `PHASE-7-live-durable-command-pipeline-blueprint.md` |
| Status | Authorized / Next; TASK-026 Evidence Gate passed |
| Scope | Tests, deterministic barriers/fixtures and verification report only |
| Next Gate | TASK-027 Evidence Gate + exact-SHA CI |

## Acceptance

- [ ] Append-before-publish order and append failure are dynamically verified.
- [ ] Durable-then-FULL, pipeline/handler/write failure and disconnect windows
  are verified without reflection or production-only seams.
- [ ] Closed WAL offline replay equals the live ordered transcript/digest/probe.
- [ ] Child-process interruption evidence is recorded where supported.
