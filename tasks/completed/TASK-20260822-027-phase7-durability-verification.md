# Task Plan — TASK-20260822-027

| Field | Value |
| --- | --- |
| Task | `TASK-20260822-027` / Durability, Failure and Replay Verification |
| Phase / ADR / Blueprint | Phase 7 / ADR-0015 / `PHASE-7-live-durable-command-pipeline-blueprint.md` |
| Status | Limited Remediation Round 2 Completed / Evidence Gate PASS |
| Scope | Tests, deterministic barriers/fixtures and verification report only |
| Next Gate | Phase 7 Closure Approved; task archived |

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
- Round 2 converges active-session disconnect and synchronous outbound-write
  exceptions through coordinator terminal failure, and adds a deterministic
  pre-response-completion disconnect test.
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
- Round 1 remediation commit `ae71786` / exact-SHA CI
  [32570890919](https://github.com/Holylaw7/Ultra-Low-Latency-Matching-Engine/actions/runs/32570890919)
  PASS; Round 2 supersedes its evidence counts.
- Human Exception Gate approved Round 2 terminal convergence remediation.
- Round 2 commit `7b9106f` / exact-SHA CI
  [32571940187](https://github.com/Holylaw7/Ultra-Low-Latency-Matching-Engine/actions/runs/32571940187)
  PASS. Focused Phase 7 evidence covers 12 tests; full `mvn verify` covers 158
  core tests with Checkstyle 0.
- Final evidence synchronization commit `62ae68f` / exact-SHA CI
  [32572441090](https://github.com/Holylaw7/Ultra-Low-Latency-Matching-Engine/actions/runs/32572441090)
  PASS.
- Status-only reconciliation `b24db93` / exact-SHA CI
  [32572561973](https://github.com/Holylaw7/Ultra-Low-Latency-Matching-Engine/actions/runs/32572561973)
  PASS; it reconciles final evidence references without changing production
  code or the approved evidence counts.
- Final Evidence-Gate documentation verification `b6eaa8d` / exact-SHA CI
  [32572786850](https://github.com/Holylaw7/Ultra-Low-Latency-Matching-Engine/actions/runs/32572786850)
  PASS; it records the reconciliation checkpoint without changing production
  code or the approved evidence counts.
- Read-only verifier and docs-auditor Evidence Gate: PASS. TASK-028
  benchmark/documentation implementation and Closure Proposal are complete at
  `9fed6b2` / exact-SHA CI
  [32574274905](https://github.com/Holylaw7/Ultra-Low-Latency-Matching-Engine/actions/runs/32574274905);
  verifier, benchmark-reviewer and docs-auditor all PASS. Human Phase 7
  Closure is approved; merge, baseline tag and task archival are complete.
  Phase 8 remains unauthorized.
