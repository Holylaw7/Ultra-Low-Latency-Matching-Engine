# Phase 10 — TASK-046 Full Campaign Evidence Report

## Status

`TASK-046` pre-campaign and lifecycle Evidence Gates passed. Human approval then
authorized exactly two independent `RC_ASSEMBLED_RUNTIME_V1` Full Runs. Both
completed with PASS and the immutable campaign evaluator returned PASS. The
qualification-only characterization remediation is complete; the remaining
gate is Sol High delta-only review, followed by Human Phase 10 Closure.

| Item | Evidence |
| --- | --- |
| Implementation | `0a96593` — `test(runtime): qualify release-candidate assembly` |
| Assembled Full runner | `1a02e66` — `test(runtime): add assembled full campaign runner` |
| Characterization source checkpoint | `5dbe95b` — source tree used for the qualification-only remediation evidence |
| Standard CI for Full runner | [32734798459](https://github.com/Holylaw7/Ultra-Low-Latency-Matching-Engine/actions/runs/32734798459) — PASS |
| Qualification Quick Lane for Full runner | [32734798461](https://github.com/Holylaw7/Ultra-Low-Latency-Matching-Engine/actions/runs/32734798461) — PASS |
| Standard CI | [32730760419](https://github.com/Holylaw7/Ultra-Low-Latency-Matching-Engine/actions/runs/32730760419) — PASS |
| Qualification Quick Lane | [32730760501](https://github.com/Holylaw7/Ultra-Low-Latency-Matching-Engine/actions/runs/32730760501) — PASS |
| Local regression | 225 core + 50 qualification tests; 0 failures/errors; 2 expected skips |
| Checkstyle | 0 violations |
| Package | `qualification/target/matching-engine-qualification.jar` |
| Package SHA-256 | `af4c270cfe550ab9166dee09752f12d03b8730aa1ea50cfb23835080c4146b39` |

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

Human Full Campaign Approval:
✅ Recorded

RC_ASSEMBLED_RUNTIME_V1 Full Run A:
✅ PASS — `3,601,045 ms`, `1,799,401` accepted, 8 natural GC samples

RC_ASSEMBLED_RUNTIME_V1 Full Run B:
✅ PASS — `3,601,029 ms`, `1,848,908` accepted, 9 natural GC samples

Campaign evaluator:
✅ `2/2 qualifying`, `17` cumulative natural samples, `campaign.result=true`

Immutable manifest SHA-256 values:
- Run A: `f65a395256a919fe5a576c8858c2c5a6cd8f8c996bd5a9c2af367a51a33a1fcc`
- Run B: `60f24746c23222fa23209117eee1300bc0c0aac1a3a497f8aa23d756ce83a596`

Campaign summary:
`qualification-results/phase10-rc-campaign/rc-assembled-campaign-72ea9c3f-0619-41b5-9d90-3dbb3ec9eaf6/qualification-campaign-summary-v1.txt`
SHA-256 `89799b16f317f0cb083821368dcfe005dbbe508964adf8de234a1be61db78ae6`.

The manifests' declared artifact relative paths and SHA-256 values were
verified against the preserved local artifacts. The Full result artifacts
include counts, digests, resource CSV and JFR evidence. The separate live
latency percentile/management-overhead distribution evidence is produced by
the characterization remediation above; final claim/closure review remains
separately governed and no unsupported production performance claim is made.
```

The two authorized Full Runs completed without failure. Any failed, aborted or
provenance-invalid Full Run would have been preserved and stopped the campaign;
no replacement run was started.

## Characterization Remediation Evidence

Human Limited Remediation was restricted to `qualification/**` and evidence
documentation. It did not alter the existing Full Run A/B or campaign
artifacts, and it did not start Run C. The new characterization command uses
the packaged Java 21 process and the public Protocol v1 / loopback management
boundaries.

| Item | Evidence |
| --- | --- |
| Characterization directory | `qualification-results/phase10-characterization/rc-characterization-41879303-0a74-452d-9cf9-9ab05c565e3b/` |
| Characterization result | `PASS` |
| Empty-WAL lifecycle | `30/30` samples passed |
| Snapshot-tail lifecycle | `30/30` samples passed |
| Lifecycle raw samples | `lifecycle-samples.csv` — SHA-256 `0dafa54c4a6eff9caee3c371b1d417f58bf599da31033bb5a99c280fccbb9e26` |
| Management idle trial | `601,818 ms`, `332,578` accepted, `P99 response 3,375,700 ns` |
| STATUS @ 1 Hz trial | `601,847 ms`, `342,074` accepted, `601` management requests, `P99 response 3,096,000 ns` |
| Idle response raw samples | `332,578` observations; immutable CSV retained |
| STATUS response raw samples | `342,074` observations; immutable CSV retained |
| JFR/resource evidence | `62` JFR files and `62` resource CSV files retained; no failure artifact |
| Throughput regression trigger | `false` (>10% threshold not reached) |
| Response P99 regression trigger | `false` (>10% threshold not reached) |
| Manifest | `characterization-manifest-v1.txt` — SHA-256 `6001363d0ef08ad8d2d20f10862a7432711706621e3a61e407f9d12c42f03882` |
| Summary | `characterization-summary-v1.txt` — SHA-256 `9d7bbfad3fe1464b776b34f714054528989e02ff132ddb6b448ddaa02bf9e888` |
| Artifact sidecar | `artifact-hashes-v1.txt` — SHA-256 `264a4fad7e8c5e350b691b641393324742b9e318617d06c6d5163eed46167dd7` |
| Application JAR | SHA-256 `671313a35acf46cb3dd2722b797c2c2fff3be1544700cf4f84b45f15ea3ec494` |
| Configuration identity | `4ec0dfee372e8294af1e35ea0c8979e7c9161bbc088352dc37edcc6f3468f086` |
| Comparability identity | `ea09a2676ff21fc5350404dbf15eb2613384265d95881edf17fa1941c2766cbe` |

The immutable summary records the complete percentile distributions (all values
are nanoseconds; `count=60` for each lifecycle distribution):

| Distribution | P50 | P95 | P99 | P99.9 | Max |
| --- | ---: | ---: | ---: | ---: | ---: |
| Startup-to-ready | 738,424,100 | 769,152,700 | 789,171,400 | 789,171,400 | 789,171,400 |
| Graceful shutdown | 789,025,500 | 814,677,700 | 819,572,700 | 819,572,700 | 819,572,700 |
| Live Protocol response | 25,705,100 | 38,971,600 | 46,693,400 | 46,693,400 | 46,693,400 |

The paired ten-minute trial distributions are also retained in the immutable
summary and raw CSVs. Management-idle response latency is P50/P95/P99/P99.9/max
`2,029,300 / 2,648,700 / 3,375,700 / 6,407,000 / 34,704,500 ns`; the
STATUS-at-1-Hz response distribution is `1,970,200 / 2,547,100 / 3,096,000 /
5,590,500 / 51,381,100 ns`, and its management-query distribution is
`1,376,800 / 2,279,200 / 2,545,000 / 2,893,900 / 2,893,900 ns`.

All raw lifecycle, response, management, JFR and resource artifacts are local
ignored evidence. No percentile filtering or retry was used. The fixed paired
trials use the same packaged build/configuration and JDK/GC environment. Netty
allocator is recorded as `default-configured`; the shaded application JAR does
not expose a package implementation version, so that runtime field remains
`UNAVAILABLE` rather than being invented. The approved build dependency remains
Netty `4.2.17.Final`.

## Claim Boundary

This report supports only reproducible, single-node, local-host release-candidate
assembly qualification evidence. It is not a Product Release, Production
Ready, SLA/RTO, exactly-once, HA, bounded-WAL-retention or hardware-power-loss
claim. The characterization distributions are local component/loopback
evidence and do not establish a production latency guarantee.

## Next Gate

```text
TASK-046 Full Campaign Evidence PASS
        ↓
final read-only Evidence/claim review (including latency/profile criterion)
        ↓
Sol High Phase 10 Closure Review
        ↓
Human Phase 10 Closure Approval
```
