# Phase 10 — TASK-046 Full Campaign Evidence Report

## Status

`TASK-046` pre-campaign and lifecycle Evidence Gates passed. Human approval then
authorized exactly two independent `RC_ASSEMBLED_RUNTIME_V1` Full Runs. Both
completed with PASS and the immutable campaign evaluator returned PASS. The
qualification-only characterization evidence is preserved, but its final
benchmark review found that the v3 trial timer started before Protocol client
construction; therefore the v3 throughput interval includes one-time connection
setup and is not accepted as final characterization evidence. A bounded
qualification-only correction is staged; no new 60-minute Full Run is
authorized. The first source-checkpoint CI run exposed one unrelated flaky
core-test assertion; the same Standard verification passed on the docs/evidence
synchronization commits. The remaining gate is a corrected characterization
run, read-only audit and then Sol High delta-only review, followed by Human
Phase 10 Closure.

| Item | Evidence |
| --- | --- |
| Implementation | `0a96593` — `test(runtime): qualify release-candidate assembly` |
| Assembled Full runner | `1a02e66` — `test(runtime): add assembled full campaign runner` |
| Characterization source checkpoint | `7ba7ed0` — source tree used for the v3 qualification-only characterization evidence |
| Standard CI for Full runner | [32734798459](https://github.com/Holylaw7/Ultra-Low-Latency-Matching-Engine/actions/runs/32734798459) — PASS |
| Qualification Quick Lane for Full runner | [32734798461](https://github.com/Holylaw7/Ultra-Low-Latency-Matching-Engine/actions/runs/32734798461) — PASS |
| Standard CI | [32730760419](https://github.com/Holylaw7/Ultra-Low-Latency-Matching-Engine/actions/runs/32730760419) — PASS |
| Qualification Quick Lane | [32730760501](https://github.com/Holylaw7/Ultra-Low-Latency-Matching-Engine/actions/runs/32730760501) — PASS |
| Standard CI for characterization checkpoint | [32754918129](https://github.com/Holylaw7/Ultra-Low-Latency-Matching-Engine/actions/runs/32754918129) — FAIL due to flaky core-test assertion; docs/evidence sync reruns `a0747bb` [32802089849](https://github.com/Holylaw7/Ultra-Low-Latency-Matching-Engine/actions/runs/32802089849) and `a6a623b` [32802326848](https://github.com/Holylaw7/Ultra-Low-Latency-Matching-Engine/actions/runs/32802326848) — PASS |
| Qualification Quick Lane for characterization checkpoint | [32754918118](https://github.com/Holylaw7/Ultra-Low-Latency-Matching-Engine/actions/runs/32754918118) — historical PASS for the original `7ba7ed0` checkpoint; final docs-sync Quick Lane is [32802512953](https://github.com/Holylaw7/Ultra-Low-Latency-Matching-Engine/actions/runs/32802512953) — PASS |
| Final docs/evidence sync | `241485c` — Standard [32802512908](https://github.com/Holylaw7/Ultra-Low-Latency-Matching-Engine/actions/runs/32802512908) and Quick [32802512953](https://github.com/Holylaw7/Ultra-Low-Latency-Matching-Engine/actions/runs/32802512953) — PASS; these validate the docs sync, not the uncorrected v3 measurement boundary |
| Local regression | 225 core + 50 qualification tests; 0 failures/errors; 2 expected skips |
| Checkstyle | 0 violations |
| Package | `qualification/target/matching-engine-qualification.jar` |
| Package SHA-256 | `1805904b9f5c7eda8350be524473d9f102cba403f36c4ae5005cd3b1692b181e` |

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

Review status: `CHANGES REQUIRED`. The preserved v3 artifacts are not deleted or
rewritten, but their trial elapsed/throughput evidence is non-final because the
measurement timer began before Protocol client construction. The qualification
runner has a bounded correction staged to construct the client first, start the
timer immediately before the command loop, and record the fixed execution model
(`warmup=none`, one child process per trial, one sequential Protocol client and
management thread). A new characterization result directory must be generated
before this evidence can support final Closure. No new 60-minute Full Run is
authorized.

Human Limited Remediation was restricted to `qualification/**` and evidence
documentation. It did not alter the existing Full Run A/B or campaign
artifacts, and it did not start Run C. The new characterization command uses
the packaged Java 21 process and the public Protocol v1 / loopback management
boundaries.

| Item | Evidence |
| --- | --- |
| Characterization directory | `qualification-results/phase10-characterization-v3/rc-characterization-60bac1ef-bbe1-4df1-adb0-fa5ab310464b/` |
| Characterization result | `PASS` |
| Empty-WAL lifecycle | `30/30` samples passed |
| Snapshot-tail lifecycle | `30/30` samples passed |
| Lifecycle raw samples | `lifecycle-samples.csv` — SHA-256 `23823439b948d9194fe9becc48c8bf7f87940e9e19c4d8eeac3e07a4c18b7c31` |
| Management idle trial | `600,000 ms`, `353,589` accepted, `P99 response 3,126,500 ns` |
| STATUS @ 1 Hz trial | `600,002 ms`, `369,562` accepted, `601` management requests, `P99 response 2,642,100 ns` |
| Idle response raw samples | `353,589` observations; immutable CSV retained |
| STATUS response raw samples | `369,562` observations; immutable CSV retained |
| JFR/resource evidence | `62` JFR files and `62` resource CSV files retained; all 62 have non-zero allocation-sample evidence |
| Throughput regression trigger | `false` (>10% threshold not reached) |
| Response P99 regression trigger | `false` (>10% threshold not reached) |
| Manifest | `characterization-manifest-v1.txt` — SHA-256 `3b093b39dc765172ac8b97ee22beda5798f7e96d0bd7da694608318915508212` |
| Summary | `characterization-summary-v1.txt` — SHA-256 `6204f190e70415b4aa8bfc48a43824b211530d315bd5cc29e00ec8da0d29f4d6` |
| Artifact sidecar | `artifact-hashes-v1.txt` — SHA-256 `fd8432ccd7b4f064b7766cb5397e32741405a7a043267612caf5efc17c46d7b1` |
| Application JAR | SHA-256 `1805904b9f5c7eda8350be524473d9f102cba403f36c4ae5005cd3b1692b181e` |
| Configuration identity | `2eac433a94cd8a9879e150a447a4df5af5ba6eddc751e4e2ae52a4c11b05572c` |
| Comparability identity | `ea09a2676ff21fc5350404dbf15eb2613384265d95881edf17fa1941c2766cbe` |

The immutable summary records the complete percentile distributions (all values
are nanoseconds; `count=60` for each lifecycle distribution):

| Distribution | P50 | P95 | P99 | P99.9 | Max |
| --- | ---: | ---: | ---: | ---: | ---: |
| Startup-to-ready | 677,920,000 | 724,395,800 | 763,668,700 | 763,668,700 | 763,668,700 |
| Graceful shutdown | 813,318,500 | 834,417,700 | 835,941,400 | 835,941,400 | 835,941,400 |
| Live Protocol response | 21,423,000 | 32,569,800 | 34,464,700 | 34,464,700 | 34,464,700 |

The paired ten-minute trial distributions are also retained in the immutable
summary and raw CSVs. Management-idle response latency is P50/P95/P99/P99.9/max
`1,961,700 / 2,363,000 / 3,126,500 / 4,410,600 / 27,759,800 ns`; the
STATUS-at-1-Hz response distribution is `1,863,200 / 2,186,700 / 2,642,100 /
4,163,400 / 33,303,100 ns`, and its management-query distribution is
`1,141,000 / 1,741,500 / 2,113,900 / 2,931,400 / 2,931,400 ns`.

All raw lifecycle, response, management, JFR, allocation-summary and resource
artifacts are local ignored evidence. The recursive sidecar contains 919 file
hashes and was independently re-scanned with zero missing or mismatched entries.
No percentile filtering or retry was used. The fixed paired trials use the same
semantic build/configuration and JDK/GC environment; ephemeral protocol and
management ports are intentionally distinct per child process. Netty allocator
is recorded as `default-configured`; the shaded application JAR does not expose
a package implementation version, so Netty/Disruptor runtime fields remain
`UNAVAILABLE` rather than being invented. The approved build dependencies remain
Netty `4.2.17.Final` and Disruptor `4.0.0`.

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
qualification-only characterization correction and evidence regeneration
        ↓
final read-only Evidence/claim review (including latency/profile criterion)
        ↓
Sol High Phase 10 Closure Review
        ↓
Human Phase 10 Closure Approval
```
