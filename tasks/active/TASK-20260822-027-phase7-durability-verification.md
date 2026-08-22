# Task Plan — TASK-20260822-027

| Field | Value |
| --- | --- |
| Task | `TASK-20260822-027` / Durability, Failure and Replay Verification |
| Phase / ADR / Blueprint | Phase 7 / ADR-0015 / `PHASE-7-live-durable-command-pipeline-blueprint.md` |
| Status | Limited Remediation Completed / Evidence Gate Pending Review |
| Scope | Tests, deterministic barriers/fixtures and verification report only |
| Next Gate | Read-only verifier/docs-auditor Evidence Gate review |

## Acceptance

- [x] Append-before-publish order and append failure are dynamically verified.
- [x] Durable-then-FULL, pipeline/handler and the four externally observable
  disconnect windows are verified with the approved Phase-7 runtime-composition
  barriers; direct outbound write-future failure is deterministically controlled
  at the additive response-write boundary.
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
- Added the approved additive Phase-7 runtime-composition boundary: real WAL
  append/pipeline adapters can be wrapped for post-return barriers, and the
  durable response-write boundary can expose a controlled pending/failing
  Netty future. The normal public constructor remains wired to real adapters.
- Reused existing WAL rotation and Pipeline handler-failure tests; no
  production-only seam or frozen-path change was introduced.

## Evidence Gate

- Baseline verification commit `80838db` / exact-SHA CI
  [32565591806](https://github.com/Holylaw7/Ultra-Low-Latency-Matching-Engine/actions/runs/32565591806)
  passed the original 6-test attempt.
- Test-only remediation commit `16f5442` added four timing-focused network
  cases and exact-SHA CI [32566165212](https://github.com/Holylaw7/Ultra-Low-Latency-Matching-Engine/actions/runs/32566165212)
  PASS; it exposed the approved Exception Gate.
- Human Exception Gate approved the limited runtime-composition remediation.
- Remediation commit `ae71786` / exact-SHA CI
  [32570890919](https://github.com/Holylaw7/Ultra-Low-Latency-Matching-Engine/actions/runs/32570890919)
  PASS. Focused Phase 7 evidence covers 10 tests; full `mvn verify` covers 156
  core tests with Checkstyle 0.
- The Evidence Gate remains pending read-only verifier/docs-auditor review;
  TASK-028 stays locked until that review passes.
