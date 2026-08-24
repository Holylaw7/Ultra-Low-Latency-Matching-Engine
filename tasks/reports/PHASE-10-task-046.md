# Phase 10 — TASK-046 Pre-Campaign Evidence Report

## Status

`TASK-046` pre-campaign implementation and lifecycle Evidence Gate passed. The two `RC_ASSEMBLED_RUNTIME_V1` 60-minute Full Runs remain separately Human-gated and were not started.

| Item | Evidence |
| --- | --- |
| Implementation | `0a96593` — `test(runtime): qualify release-candidate assembly` |
| Standard CI | [32730760419](https://github.com/Holylaw7/Ultra-Low-Latency-Matching-Engine/actions/runs/32730760419) — PASS |
| Qualification Quick Lane | [32730760501](https://github.com/Holylaw7/Ultra-Low-Latency-Matching-Engine/actions/runs/32730760501) — PASS |
| Local regression | 225 core + 48 qualification tests; 0 failures/errors; 2 expected skips |
| Checkstyle | 0 violations |
| Package | `qualification/target/matching-engine-qualification.jar` |
| Package SHA-256 | `3fa56c55988344421660e06571bcc5ab3a157bd81012734fed3c016361aa6a5a` |

## Authorized Scope Implemented

The qualification module now provides a qualification-only packaged entrypoint and parent-side process wrapper. The lifecycle runner drives the real Protocol v1 TCP client and bounded management boundary through:

```text
packaged JAR child
    -> ReleaseCandidateRuntime
    -> recovery / sequence convergence
    -> Protocol v1 command exchange
    -> management READY query
    -> graceful or approved post-response forced termination
    -> offline recovery and lease reacquisition
    -> immutable cycle artifacts and SHA-256 sidecars
```

The Windows forced-termination path uses bounded resource-release polling after the child has exited. This is qualification harness lifecycle cleanup, not a correctness oracle and not a production runtime change.

## Pre-Campaign Lifecycle Matrix

The packaged Java 21 artifact was executed with:

```text
java -jar qualification/target/matching-engine-qualification.jar
  lifecycle --output qualification-results/phase10-precampaign
```

The immutable result directory is ignored local evidence at:

```text
qualification-results/phase10-precampaign/
  rc-lifecycle-bf54b378-357a-45d3-aa30-faaf9c7a25eb/
```

| Scenario | Required | Passed |
| --- | ---: | ---: |
| Empty / `PURE_WAL` start and clean shutdown | 10 | 10 |
| Snapshot-plus-tail start and clean shutdown | 10 | 10 |
| Post-response forced child termination and recovery convergence | 10 | 10 |
| **Total** | **30** | **30** |

Evidence:

| Artifact | SHA-256 |
| --- | --- |
| `rc-lifecycle-summary-v1.txt` | `71862f5e49ec554c2344f0836785d6e737e1457fc06083d755c2d98e10564bc6` |
| `artifact-hashes-v1.txt` | `1181a931950d32a4c0be21b937f2998584299bcb9950436c2dcabb88401ffc37` |

All 30 cycles report `passed=true`; no temporary artifacts remained, recovery converged, leases were reacquired, and all completed command exchanges passed through the public Protocol v1 boundary. Historical Phase 9 artifacts remain unchanged.

## Verification Boundary

The pre-campaign gate verifies packaged startup, readiness, Protocol v1 command round-trip, Snapshot-tail bootstrap, graceful shutdown, approved forced termination after a completed response, recovery convergence, lease release and immutable cycle evidence. It does not claim arbitrary in-flight crash safety, exactly-once semantics, power-loss safety or production readiness.

The existing Phase 10 TASK-041–045 failure and configuration tests remain part of the full reactor regression. No production source, WAL/Snapshot/Protocol format, runtime semantic, dependency or `.vscode/` file was changed.

## Full Campaign Gate

```text
Pre-campaign implementation:
✅ Evidence complete

Pre-campaign lifecycle matrix:
✅ 30/30

RC_ASSEMBLED_RUNTIME_V1 Full Run A:
⏳ Human approval required

RC_ASSEMBLED_RUNTIME_V1 Full Run B:
⏳ Human approval required

Phase 10 Closure / merge / v0.9.0-rc.1 / Product Release:
NOT AUTHORIZED
```

No 60-minute Full Run was started. A separate Human Full Campaign Approval is required before exactly two independent Full Runs may be executed. Any failed, aborted or provenance-invalid Full Run must be preserved and stop the campaign; no replacement run is implicit.

## Claim Boundary

This report supports only a reproducible, single-node, local-host release-candidate assembly pre-campaign. It is not a Product Release, Production Ready, SLA/RTO, exactly-once, HA, bounded-WAL-retention or hardware-power-loss claim.

## Next Gate

```text
TASK-046 pre-campaign Evidence Gate
        ↓
Human Full Campaign Approval
        ↓
exactly two independent RC_ASSEMBLED_RUNTIME_V1 Full Runs
        ↓
campaign Evidence Gate
        ↓
Sol High Phase 10 Closure Review
```

