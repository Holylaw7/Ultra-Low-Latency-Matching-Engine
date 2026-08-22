# Task Plan — TASK-20260822-026

| Field | Value |
| --- | --- |
| Task | `TASK-20260822-026` / Durable Netty Composition |
| Phase / ADR / Blueprint | Phase 7 / ADR-0015 / `PHASE-7-live-durable-command-pipeline-blueprint.md` |
| Status | Authorized / Next |
| Scope | New `network/netty/durable/**` opt-in server/session and tests |
| Next Gate | TASK-026 Evidence Gate |

## Goal

Compose Protocol v1 with the durable coordinator while preserving one session,
one in-flight request, EventLoop result scheduling and legacy Gateway behavior.

## Acceptance

- [ ] Protocol v1 bytes remain unchanged.
- [ ] Durable mode is opt-in and uses a fresh WAL only.
- [ ] Disconnect and outbound write failure semantics are explicit.
- [ ] Legacy Phase 6 server tests remain green.
