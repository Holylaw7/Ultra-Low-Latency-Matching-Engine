# GA Qualification Matrix — Approved / Frozen

## Status and target

```text
Status:                  Approved / Frozen by Human Blueprint Approval 2026-08-25
Candidate tag:           v0.9.0-rc.1
Annotated tag object:    dfd38c08e80aed9035bf1c2d7c8faf8bae99c356
Peeled production SHA:   e2828f563ee41316c062385c0244ac1336731359
Post-tag docs SHA:       b8489bf8d8fe979bfd4b28bd7e6c2da8bb33b1d4
GA / tag / publication:  Not Authorized
Repository license:      Apache-2.0 — Human accepted
Distribution:             GitHub binary — Human accepted
Maven Central:             Not required / not authorized
```

All twelve Gates are conjunctive. `FAIL` cannot be waived. `ABORTED` is not
`PASS`; replacement execution requires the Human gate defined by ADR-0019.

## Gate matrix

| Gate | Objective | Required execution / evidence | PASS | Blocker |
| --- | --- | --- | --- | --- |
| G1 Correctness | Preserve price-time matching semantics | Golden/domain suites; public Protocol v1 workloads; ordered result/checkpoint/transcript/probe comparison | zero invalid trade, loss, gap or divergence | B0/B1 |
| G2 Deterministic Replay | Live, PURE_WAL and Snapshot-tail converge | `LIFECYCLE_MIX`, `CROSSING_MULTI_MATCH`, `RESTING_DEPTH`, `MEMORY_STEADY_STATE_V1`; seeds 20260823/24/25 | checkpoint, suffix results, TradeId, EventSequence, transcript and public probe exact | B0/B1 |
| G3 Crash Recovery / Durability | Prove approved durability fault model | append/force failures, final torn tail, corruption/gap/checksum, 50 graceful and 50 post-response forced terminations | fail-closed or exact convergence; no unreported durability claim | B0/B1 |
| G4 Performance SLO | Meet fixed local-host SLO | three comparable ten-minute public-path runs; 60 lifecycle samples; paired management trial | every threshold in ADR-0019 D11 PASS | B1/B2/B3 |
| G5 Capacity | Establish supported envelope | 100k/250k/500k/1M scales, active-order and recovery evidence | 1M accepted, >=166k recovered active orders, exact convergence, no OOM/gap/timeout | B1/B2/B3 |
| G6 Soak / Stability | Detect long-run drift | Human-gated 2h then 6h fixed-rate public-path runs | command floors, zero correctness errors, replay equality, heap/resource guards, P99 drift <=20% | B0/B1/B3 |
| G7 Overload | Stay bounded under excess/invalid input | session, in-flight, frame, management, Pipeline FULL and resource-bound matrix | deterministic rejection/fail-stop; no second admission/producer/unbounded queue | B0/B1 |
| G8 Observability | Diagnose without changing semantics | management schema/counters, JFR, GC, process outcomes | complete bounded evidence; no mutable engine observation | B1/B2 |
| G9 Reproducibility | Rebuild identical release artifact | two clean builds, pinned environment, source digest, SBOM, `SHA256SUMS` | byte-identical JAR and internally consistent provenance | B1/B3/B4 |
| G10 Documentation | Enable a third party to operate the supported scope | install, config, recovery, failure, security boundary, rollback and limitations runbooks | executable instructions and no unsupported claim | B4 |
| G11 Security | Establish offline supply-chain evidence | candidate-bound SBOM, independent runtime dependency inventory, license disposition, full-history and candidate-bound secret scans, exact non-secret disposition manifest, artifact integrity/provenance | every `OFFLINE_SUPPLY_CHAIN_SECURITY_V1` criterion PASS; no verified/credible secret, unresolved finding, inventory mismatch, prohibited/unknown license or missing/invalid artifact | B0/B1/B2/B4 |
| G12 Evidence Audit | Bind every claim to immutable evidence | schema/hash/link/stale-state audit plus reviewers and exact-SHA CI | all Gates resolve PASS against the same candidate; no stale or missing artifact | B0/B2/B4 |

## G1 / G2 qualification profiles

Each profile uses only supported `SubmitLimitCommand` and
`CancelOrderCommand`, traverses the packaged Protocol v1 public boundary, then
performs strict WAL scan, `PURE_WAL` recovery and Snapshot-tail recovery.

Every profile/seed uses exactly 100,000 commands, WAL segment size 65,536 and
two deterministic repetitions. Each repetition executes PURE_WAL plus
Snapshot-tail at prefix sequences 25,000, 50,000 and 75,000. This produces
eight recovery observations per profile/seed; all final checkpoints must agree
and each Snapshot mode must match the PURE_WAL suffix exactly.

| Profile | Seeds | Required comparison |
| --- | --- | --- |
| `LIFECYCLE_MIX` | 20260823, 20260824, 20260825 | ordered results, checkpoint, command/transcript digest, probe |
| `CROSSING_MULTI_MATCH` | same | trades, TradeId, EventSequence, suffix transcript |
| `RESTING_DEPTH` | same | book/checkpoint depth and replay equality |
| `MEMORY_STEADY_STATE_V1` | same | bounded-state convergence and public response equality |

## G3 exact durability and corruption matrix

Storage layouts use WAL segment sizes 4,096, 65,536 and 1,048,576 bytes. The
lifecycle campaign contains exactly 50 graceful and 50 forced child-process
terminations; every cycle applies 10,000 `LIFECYCLE_MIX` commands, seed
20260823, and forced termination occurs only after the final complete response.

The following fixtures are mandatory for every applicable segment size:

1. segment filename/first-sequence mismatch, magic, version, flags and each
   reserved-byte mutation;
2. record length `0`, `27`, `29`, `51`, `53` and configured maximum plus one;
3. unsupported record version/type, non-zero flags, invalid side and every
   reserved-byte region mutation;
4. one body-bit flip and one stored-checksum-bit flip for both command types;
5. duplicate previous sequence, one-sequence gap and cross-segment gap;
6. incomplete header/body/checksum in a non-final segment, all fail closed;
7. final-record truncation after bytes 1, 27, 28 and 51, with repair allowed
   only when physically incomplete at the final segment tail;
8. Snapshot magic/version/flags/reserved/count/length/CRC/WAL-prefix digest/
   checkpoint digest mutations, duplicate/non-canonical order, Snapshot newer
   than WAL, orphan temp ignored and corrupt published Snapshot no-fallback;
9. deterministic next-segment path collision proving rotation failure,
   terminal writer and later append rejection.

Dynamic `FileChannel.force(true)` failure injection is not required and remains
the accepted limitation from Phase 5: implementation path and terminal logical
semantics are reviewed, but hardware/physical-absence atomicity is not claimed.
Adding a production injection seam requires rc.2.

## G4 fixed SLO

Three independent ten-minute runs use the same candidate, workload seed,
JDK/JVM/GC, filesystem/storage, `SYNC_EACH_APPEND`, Pipeline
`1024/BLOCKING`, Netty allocator and single sequential client.

They must match the Phase 10 reference comparability identity: Windows 11
`10.0.26200`, amd64, i9-13900H / 20 logical processors, fixed NTFS `E:` volume
reported as NVMe, Microsoft OpenJDK `21.0.12+8-LTS`, G1, no VM arguments,
8,493,465,600-byte max heap, Netty 4.2.17.Final, Disruptor 4.0.0,
default-configured allocator, `zh-CN`, `Asia/Hong_Kong`. Mismatch requires
Human Environment Approval before running and is not comparable by default.

| Criterion | PASS |
| --- | ---: |
| accepted throughput | `>= 500 commands/s` in every run |
| response P50 | `<= 2.5 ms` in every run |
| response P99 | `<= 5 ms` in every run |
| response P99.9 | `<= 10 ms` in every run |
| errors/timeouts/mismatch | `0` |
| startup P99 across 60 samples | `<= 1.25 s` |
| shutdown P99 across 60 samples | `<= 1.25 s` |
| STATUS@1Hz throughput and P99 regression | `<= 10%` versus paired idle trial |

Raw samples, P50/P95/P99/P99.9/max, throughput, CPU, storage, allocation,
GC/JFR and hashes are retained. There is no outlier filtering or best-run
selection.

## G5 capacity profile

All scale points use `LIFECYCLE_MIX`, seed 20260823 and 65,536-byte WAL
segments. They do not support an independent high-cardinality price-level
claim.

| Commands | Required evidence |
| ---: | --- |
| 100,000 | accepted/result counts, active orders/levels, WAL/Snapshot, heap/RSS, recovery |
| 250,000 | same |
| 500,000 | same |
| 1,000,000 | same plus >=166,000 recovered active orders and exact convergence |

This is a tested support envelope, not an absolute system maximum.

## G6 soak campaign

| Stage | Manual gate | Duration / floor | PASS |
| --- | --- | --- | --- |
| Soak A | Human approval required | 2h / >=1.44M accepted | all correctness, recovery, resource and drift guards PASS |
| Soak B | new Human approval after A review | 6h / >=4.32M accepted | same; no replacement run without Human approval |

Both use `MEMORY_STEADY_STATE_V1`, seed 20260823 and 200 accepted commands/s.
Every run is an immutable evidence unit.

## Evidence contracts

Exact canonical field, encoding, hashing and publication rules are in
[`GA-EVIDENCE-SCHEMAS.md`](GA-EVIDENCE-SCHEMAS.md). Security tools and pins are
in [`GA-SECURITY-TOOLCHAIN.md`](GA-SECURITY-TOOLCHAIN.md).

### `ga-gate-result-v1`

Records gate ID/version, candidate and controller identity, configuration and
comparability identities, start/end time, outcome, criteria with measured
values, artifact references/hashes, limitations and blocker classification.

### `ga-run-manifest-v1`

Records run ID, gate/profile/seed, candidate tag-object/peeled SHA/tree/JAR,
controller SHA, JDK/JVM/GC/heap, OS/CPU/filesystem/storage, Netty/Disruptor/WAL,
start/end/elapsed, counts, latency/resource metrics, result, reason and artifact
hash inventory. It is generated during the run and atomically published.

For TASK-049, one physical matrix case produces two independent run-manifest
views: `G1/g1-v1` and `G2/g2-v1`. They reference the same immutable raw case
inventory and are linked by `ga-g1-g2-physical-run-binding-v1`; they do not
share a `run.id`, and neither view can substitute for the other. The approved
24-case matrix therefore yields 24 physical executions, 24 G1 manifests, 24
G2 manifests, and one gate result per gate. The older task-specific
`ga-g1-g2-manifest-v1` remains a raw/payload summary only.
Each raw payload, inventory, canonical view, physical binding and gate result
must also have the schema-required adjacent `<name>.sha256` sidecar; missing or
mismatched sidecars fail closed.

### `ga-campaign-summary-v1`

References immutable run-manifest hashes; it does not copy mutable run
evidence. It records required/observed run counts, identity equality,
aggregate criteria, all outcomes and campaign result.

### `ga-release-manifest-v1`

Records product/version, candidate production SHA, selected release-source SHA,
tag-object SHA, JAR/SBOM/SHA256SUMS hashes, Gate-result hashes, documentation,
known limitations and release-channel identity. Documentation is bound
indirectly and immutably through the referenced G10 and G12 Gate-result hashes;
the release manifest does not copy mutable documentation fields. Human
Blueprint, campaign, Closure, GA/tag and publication authorities remain
external governance records and are intentionally absent from the manifest.
Preparing it does not authorize publication.

## Blocker and requalification matrix

| Changed or failed area | Class | Minimum requalification |
| --- | --- | --- |
| matching/domain/orderbook/sequence | B1 | new RC; G1-G12 |
| Protocol/Pipeline/coordinator | B1 | new RC; G1-G12 |
| WAL/Snapshot/recovery | B1 | new RC; G1-G12 |
| app/config/management/shutdown | B1 | new RC; G1-G12 unless Sol+Human approve narrower impact |
| runtime dependency/build artifact | B1 | new RC; G1,G4,G6-G9,G11,G12 plus impacted Gates; all if uncertain |
| qualification harness/schema | B2 | affected Gates and G12 |
| runner/storage/scanner outage | B3 | preserve ABORTED; Human-approved replacement |
| docs/license/release metadata | B4 | G9/G10/G11/G12 as applicable |
| provenance/hash mismatch | B0 | stop, root-cause, new evidence IDs; no GA |

## Claim boundary

Passing this matrix may support only the narrow scope in ADR-0019 D4 on the
recorded environment. It does not prove public-Internet safety, exactly-once,
HA, hardware power-loss safety, universal SLO/SLA/RTO, maximum capacity or
memory-leak freedom.

G11 is the Human-amended `OFFLINE_SUPPLY_CHAIN_SECURITY_V1` gate. Current
external CVE/NVD vulnerability-database evaluation is outside this
portfolio-release qualification boundary. Passing G11 does not support claims
of CVE/NVD cleanliness, absence of known vulnerabilities, absence of CVSS
`>=7` findings, Dependency-Check success, production security certification
or general production security.
