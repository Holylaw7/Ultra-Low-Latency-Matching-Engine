# Task Plan — TASK-20260822-026

| Field | Value |
| --- | --- |
| Task | `TASK-20260822-026` / Durable Netty Composition |
| Phase / ADR / Blueprint | Phase 7 / ADR-0015 / `PHASE-7-live-durable-command-pipeline-blueprint.md` |
| Status | Completed / Evidence Gate PASS |
| Scope | New `network/netty/durable/**` opt-in server/session and tests |
| Next Gate | Phase 7 Closure Approved; task archived |

## Goal

Compose Protocol v1 with the durable coordinator while preserving one session,
one in-flight request, EventLoop result scheduling and legacy Gateway behavior.

## Acceptance

- [x] Protocol v1 bytes remain unchanged.
- [x] Durable mode is opt-in and uses a fresh WAL only.
- [x] Disconnect and outbound write failure semantics are explicit.
- [x] Legacy Phase 6 server tests remain green.

## Implementation Log

- Added `DurableNetworkConfiguration` under the authorized additive durable
  network package.
- Added `DurableMatchingEngineTcpServer`, which composes a fresh synchronous
  WAL, the TASK-025 coordinator, the frozen Pipeline and Protocol v1 codecs.
- Added loopback and non-empty-WAL startup tests without changing the legacy
  Phase 6 gateway or introducing a production-only test seam.
- Final implementation evidence is recorded in
  [`PHASE-7-task-026.md`](../reports/PHASE-7-task-026.md); commit `a978fe7`
  and exact-SHA CI run `32565087793` passed.
