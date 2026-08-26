# Phase 11 — TASK-20260825-049 G1/G2 Correctness and Deterministic Recovery Report

## Status

`Implementation remediation in progress / historical matrix technical
observation preserved; canonical Evidence Gate pending.` The evidence
checkpoint `c3659fa` passed
Standard CI `32976467453` and Qualification Quick Lane `32976467177` at its
exact head SHA. TASK-049 remains in progress until the verifier,
docs-auditor and final governance decision are reconciled. TASK-050 is still
locked. No Full Campaign, candidate mutation, release or GA action is
authorized by this report.

## Scope and frozen boundaries

This task adds qualification-only correctness and deterministic-recovery
evidence for the frozen `v0.9.0-rc.1` candidate. The runner uses the public
Protocol v1 TCP boundary and the approved `QualificationWorkloadV1` golden
semantics. It does not modify production source, production tests, POMs,
dependencies, WAL/Snapshot formats, recovery semantics or runtime defaults.

The approved matrix is fixed at:

```text
profiles       = LIFECYCLE_MIX, CROSSING_MULTI_MATCH, RESTING_DEPTH,
                 MEMORY_STEADY_STATE_V1
seeds          = 20260823, 20260824, 20260825
repetitions    = 2
cases          = 4 × 3 × 2 = 24
commands/case  = 100,000
WAL segment    = 65,536 bytes
snapshots      = prefixes 25,000 / 50,000 / 75,000
recovery       = PURE_WAL + 3 independent Snapshot-tail observations/case
recovery total = 24 × 4 = 96
```

The live observation is retained in each case as a reference; the 96-count
matrix criterion counts the four recovery observations per case.

## Preserved exact-controller technical observation

The preserved technical observation was generated from controller commit
`d75a3a02e7d01dca6bdef997109cb47f8f1b1400`, with candidate and baseline both
`v0.9.0-rc.1`:

```text
qualification-results/ga-g1-g2-exact-d75a3a0-20260826/
  ga-g1-g2-ce194ae8-06be-448b-bf03-dd0b1a082ad9/
```

| Evidence | Value |
| --- | --- |
| Summary | `ga-g1-g2-summary-v1.txt` |
| Summary SHA-256 | `960e76e0569fb97109bd0d4c29f919b781bcd06317acf6afc3e99ee0415fd619` |
| Task manifest | `ga-g1-g2-manifest-v1.txt` |
| Task manifest SHA-256 | `71483d1df0080a07018779a1d893a9ac27c47223bb2768efabd90cdd582b304c` |
| Artifact sidecar | `artifact-hashes-v1.txt` |
| Artifact sidecar SHA-256 | `455600135eed4faa312e34655a1ef3c1f093da4840366c1bffa31b7f8af17b74` |
| Sidecar entries | `4,322` |
| Files including sidecar | `4,323` |
| Case directories/results | `24 / 24` |
| Case result status | `24 passed=true`, `0 passed=false` |
| Matrix summary | `observedCaseCount=24`, `passed=true`, `failureCount=0` |
| Recovery observations | `expectedRecoveryObservationCount=96` |

The task-specific immutable manifest records:

```text
candidate=v0.9.0-rc.1
controllerGitSha=d75a3a02e7d01dca6bdef997109cb47f8f1b1400
baselineTag=v0.9.0-rc.1
```

The runner retained five observations per case (LIVE, PURE_WAL and the three
independent Snapshot-tail prefixes), for 120 observations in the raw case
evidence. Every case also records ordered result/transcript/probe digests,
WAL/checkpoint digests, snapshot suffix expectations and the deterministic
workload check.

## Independent evidence validation

The artifact sidecar was parsed and recomputed independently after the runner
completed. All 4,322 listed paths were regular files under the run root, with
normalized repository-relative separators. The result was:

```text
sidecar entries                 4,322
actual files excluding sidecar 4,322
malformed sidecar lines         0
missing paths                   0
extra paths                     0
hash mismatches                 0
```

The summary and manifest hashes above were also recomputed from the persisted
bytes. No file was filtered, replaced or re-emitted after the run. An earlier
working-tree controller run remains separate local validation evidence; it is
not substituted for this exact-controller run and is not used to claim a
second qualifying run.

## Verification already completed

| Check | Result |
| --- | --- |
| Focused canonical evidence/runner suite | 4 tests PASS; 0 failures/errors |
| Full reactor `mvn verify` | Immediate rerun PASS: 225 core + 80 qualification; 2 expected skips |
| Checkstyle | 0 violations |
| `git diff --check` before report sync | PASS |
| Frozen production-path audit | No `src/main`, POM, dependency or candidate changes |
| `.vscode/` | untouched / untracked |
| Exact-SHA Standard CI for evidence checkpoint `c3659fa` | [32976467453](https://github.com/Holylaw7/Ultra-Low-Latency-Matching-Engine/actions/runs/32976467453) — PASS |
| Qualification Quick Lane for evidence checkpoint `c3659fa` | [32976467177](https://github.com/Holylaw7/Ultra-Low-Latency-Matching-Engine/actions/runs/32976467177) — PASS |

The first full-reactor invocation showed one transient failure in the existing
`MatchingEnginePipelineFailureTest`; its immediate rerun passed without any
source or test modification. This observation remains visible and is not
converted into a hidden waiver.

## Evidence interpretation and claims

The current evidence supports deterministic correctness and convergence for
the approved profiles, seeds, command count, WAL configuration and public
Protocol v1 recovery paths. It does not claim exactly-once processing,
arbitrary in-flight crash safety, hardware power-loss durability, SLA/RTO,
Production Ready, GA authorization or any current CVE/security conclusion.

The emitted `ga-g1-g2-manifest-v1` is a task-specific immutable matrix
manifest with a complete sidecar. It is not a release manifest and does not
grant release authority. Historical or unrelated evidence is not merged into
this run. A verifier review identified that the preserved run did not emit the
frozen `ga-run-manifest-v1`/`ga-gate-result-v1` contract, so that run remains a
valid technical observation but is non-qualifying evidence and is not being
backfilled.

The approved remediation keeps one physical execution per matrix case. The
runner now publishes separate G1 and G2 canonical run views, each with its own
UUID, plus an immutable `ga-g1-g2-physical-run-binding-v1` payload. The views
share the physical case's timestamps, runtime provenance and raw inventory;
the binding establishes that relationship without changing the frozen global
schema. Gate results are published independently only after all case views
are present and validated.

## Remaining Evidence Gate

Before TASK-049 can close, the following remain required:

```text
canonical remediation focused tests and full verification
fresh Human approval for the affected 24-case matrix
one physical execution per approved case (no 48-case duplication)
verifier read-only review
docs-auditor read-only review
report/plan/status reconciliation
final TASK-049 Evidence Gate decision
```

Until those checks are recorded, TASK-049 remains `In Progress / Changes
Required`, TASK-050 remains locked, and merge/tag, `v1.0.0`, GitHub Release and
GA remain unauthorized.
