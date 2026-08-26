# ADR-0019: GA Qualification, RC Immutability and Release Authority

## Status

**Approved — Human Phase 11 Blueprint Approval, 2026-08-25**

This ADR authorizes the approved Phase 11 qualification design and TASK-047 as
the next implementation task. It does not authorize Full Campaign execution,
production changes, candidate mutation, CI changes to existing workflows, tag
creation, GitHub Release publication or a General Availability declaration.

## Context

Phase 10 produced the annotated candidate tag `v0.9.0-rc.1`. The annotated tag
object is `dfd38c08e80aed9035bf1c2d7c8faf8bae99c356`; its peeled production commit
is `e2828f563ee41316c062385c0244ac1336731359`. Master commit
`b8489bf8d8fe979bfd4b28bd7e6c2da8bb33b1d4` contains post-tag documentation
only and is not part of the qualified production candidate.

Phase 9 and Phase 10 provide strong engineering qualification evidence, but an
RC tag is not a Product Release. GA requires an explicit, reproducible and
auditable decision matrix whose thresholds are frozen before execution. It
also requires a strict rule for candidate defects: production-path repair must
create a new RC and invalidate direct promotion of the old RC.

## Decisions

### D1 — Phase 11 is qualification-first

Phase 11 qualifies an immutable candidate and prepares a Human release
decision. Production code is frozen by default. Qualification results may not
silently authorize optimization or a feature change.

### D2 — The qualification target is exact

The target is exactly:

```text
candidate tag:          v0.9.0-rc.1
annotated tag object:   dfd38c08e80aed9035bf1c2d7c8faf8bae99c356
peeled production SHA:  e2828f563ee41316c062385c0244ac1336731359
post-tag docs SHA:      b8489bf8d8fe979bfd4b28bd7e6c2da8bb33b1d4
```

Every qualifying run records the candidate tag, tag-object SHA, peeled commit,
production tree digest, application JAR SHA-256 and qualification-controller
SHA. `b8489bf` may be cited as documentation evidence only.

### D3 — Candidate mutation requires a new RC

Any change to production source, production semantics, root/core build inputs,
runtime defaults, runtime dependency graph, Protocol/WAL/Snapshot formats or
packaged artifact makes `rc.1` ineligible for direct GA promotion. The repair
must produce `v0.9.0-rc.2` (or later) and rerun every affected Gate. If impact
cannot be bounded with confidence, G1 through G12 all rerun.

Existing `rc.1` evidence is never relabelled as evidence for a repaired RC.

### D4 — GA product scope is narrow and explicit

The proposed GA scope is:

- Java 21, single-node deployment;
- one MatchingEngine and the existing single-symbol topology;
- one active Protocol v1 session with one request in flight;
- `SubmitLimitCommand` and `CancelOrderCommand` only;
- loopback/trusted-network operation;
- `SYNC_EACH_APPEND`, WAL-before-execute and fail-stop durability semantics;
- `PURE_WAL` and `SNAPSHOT_THEN_WAL` recovery;
- GitHub-hosted binary artifact and documentation release.

The GA claim excludes market/amend commands, multiple symbols/sessions,
TLS/authentication, public-Internet exposure, reconnect/dedup/exactly-once,
hardware power-loss guarantees, replication/HA, WAL retention/compaction,
Maven Central publication and universal latency/SLA/RTO guarantees.

### D5 — G1 through G12 are conjunctive

Correctness, deterministic recovery, durability, performance, capacity,
stability, overload, observability, reproducibility, documentation, security
and evidence audit must all be `PASS`. There is no waiver, majority or
"accepted failure" route to GA.

### D6 — Qualification controller and candidate are distinct

Qualification-only code may evolve after Blueprint approval without mutating
the candidate. Every manifest records `candidateProductionSha` and
`qualificationControllerSha`. Controller repair is classified separately from
candidate repair and reruns affected evidence.

### D7 — Evidence schemas are immutable and versioned

Phase 11 defines:

- `ga-gate-result-v1`;
- `ga-run-manifest-v1`;
- `ga-campaign-summary-v1`;
- `ga-release-manifest-v1`.

Schemas use canonical encoding, explicit versioning, SHA-256 sidecars,
immutable atomic publication and fail-closed validation. A schema change after
campaign start invalidates affected evidence. Exact field sets, ordering,
encoding, bounds, hashing and publication rules are normative in
[`GA-EVIDENCE-SCHEMAS.md`](../release/GA-EVIDENCE-SCHEMAS.md).

Human Limited Schema Amendment on 2026-08-25 explicitly separates full
40-character lowercase SHA-1 Git object IDs (`productionSha`, `tagObjectSha`,
`controller.gitSha` and `releaseSourceSha`) from 64-character lowercase
SHA-256 digest fields. This is a lexical typing correction only; no candidate,
Gate, campaign, release authority or schema version semantics changed.

### D8 — Run outcomes and failures are retained

Every run is `PASS`, `FAIL` or `ABORTED`. Raw evidence and manifests remain
immutable. No automatic replacement run, result filtering, outlier deletion,
threshold change or retry-until-pass is allowed. A replacement after an
environmental abort requires a separate Human decision.

### D9 — Correctness and deterministic recovery are blockers

G1/G2 require exact ordered results, canonical checkpoints, transcript digests,
WAL command digests, public probe suffixes, TradeId and EventSequence. Any
invalid trade, lost command, sequence gap or state divergence is a GA blocker.

Each of the four profiles uses exactly 100,000 commands for each seed
20260823/24/25. WAL segment size is 65,536 bytes. For every profile/seed, run
two deterministic repetitions of PURE_WAL plus Snapshot-tail recovery at exact
prefix sequences 25,000, 50,000 and 75,000. All eight recovery observations per
profile/seed (2 repetitions × 4 modes) must agree on the final state; each
Snapshot mode also agrees with PURE_WAL on its exact suffix.

### D10 — Durability uses an explicit fault model

G3 covers WAL append/force error paths, final torn-tail handling, hard
corruption, checksum/sequence validation, approved completed-response forced
termination and restart convergence. JDK/OS `force(true)` remains the stated
boundary. Hardware power-loss safety is not claimed unless a future separately
approved hardware campaign proves it.

The storage matrix uses segment sizes 4,096, 65,536 and 1,048,576 bytes. Each
of 50 graceful and 50 forced cycles applies exactly 10,000
`LIFECYCLE_MIX` commands with seed 20260823 before shutdown/termination. The
exact corruption fixtures in the GA Matrix are mandatory. Dynamic
`force(true)` failure injection remains **not verified**: G3 accepts the frozen
implementation-path/terminal-state review and dynamic rotation failure. Adding
a force-injection production seam would mutate the candidate and require rc.2.

### D11 — Performance SLO is fixed before execution

G4 uses three independent comparable ten-minute
`MEMORY_STEADY_STATE_V1` runs with seed `20260823`, Protocol v1 TCP,
`SYNC_EACH_APPEND`, Pipeline `1024/BLOCKING`, one sequential client and idle
management path. Every run must meet:

| Metric | SLO |
| --- | ---: |
| accepted throughput | `>= 500 commands/s` |
| response P50 | `<= 2.5 ms` |
| response P99 | `<= 5 ms` |
| response P99.9 | `<= 10 ms` |
| errors/timeouts/sequence mismatch | `0` |

Sixty retained lifecycle samples must have startup P99 `<= 1.25 s` and clean
shutdown P99 `<= 1.25 s`. A paired `STATUS @ 1 Hz` trial may regress neither
throughput nor P99 by more than 10% against idle management. These are
environment-qualified product SLOs, not universal SLAs.

The three runs must execute on the Phase 10 reference environment exactly:
Windows 11 build `10.0.26200`, amd64, Intel Core i9-13900H, 20 logical
processors, local fixed NTFS `E:` volume reported as NVMe (device isolation not
claimed), Microsoft OpenJDK `21.0.12+8-LTS`, G1 collectors, no explicit VM
arguments, max heap 8,493,465,600 bytes, Netty `4.2.17.Final`, Disruptor
`4.0.0`, default-configured Netty allocator, locale `zh-CN` and timezone
`Asia/Hong_Kong`. Any mismatch is B3 and requires a separate Human Environment
Approval before execution; it cannot silently produce comparable SLO evidence.

### D12 — Capacity proves a support envelope, not an absolute maximum

G5 exercises 100k, 250k, 500k and 1M-command scales. The minimum published
support envelope is 1M accepted `LIFECYCLE_MIX` commands and at least 166,000
recovered active orders with exact recovery convergence, no OOM, gap, invalid
trade or timeout. Evidence records active orders, price levels, WAL/Snapshot
sizes, recovery time, heap/RSS and throughput. It must not be labelled the
system's maximum capacity.

### D13 — Stability is a staged Human-gated campaign

G6 uses `MEMORY_STEADY_STATE_V1`, seed `20260823`, the public assembled path
and fixed offered rate `200 commands/s`:

- a two-hour run accepting at least 1.44M commands;
- after its Human review, a six-hour run accepting at least 4.32M commands.

Each run requires zero unexpected error, invalid trade, loss, gap and state
divergence; runtime checkpoint must equal WAL replay; transcript/probe evidence
must agree; chronological post-GC guard and thread/file/temp-resource guards
must pass; and final-window P99 may regress at most 20% from the first window.
Two-hour and six-hour execution each require a separate Human gate.

### D14 — Overload and observability stay bounded

G7 proves second-session rejection, no second admission for pipelined/coalesced
requests, request/response bounds, durable-FULL fail-stop and management
saturation. No second producer, unbounded queue or retryable durable-FULL path
may appear. G8 proves management schema/counters, JFR/GC evidence and process
exit outcomes without observing mutable engine state.

### D15 — Reproducibility, documentation and security are blockers

G9 requires two independent clean builds with byte-identical JARs, pinned
toolchain identity, source tree digest, SBOM and `SHA256SUMS`. G10 requires
install/run/configuration/recovery/failure/upgrade/rollback/limitations
documentation. Under the Human-approved portfolio security-policy amendment,
G11 remains conjunctive as `OFFLINE_SUPPLY_CHAIN_SECURITY_V1`: it requires a
candidate-bound CycloneDX SBOM, an independently generated runtime dependency
inventory, exact SBOM/inventory consistency, deterministic runtime-license
disposition, full-history and candidate-bound secret scans, application JAR
provenance and strict immutable artifact/hash publication. Any missing,
malformed, inconsistent, prohibited/unknown-license, secret-scan or integrity
evidence blocks G11.

External CVE/NVD vulnerability-database evaluation and Dependency-Check are
outside this portfolio-release qualification boundary. No claim is made that
dependencies are free of currently known vulnerabilities or CVSS `>=7`
findings. Historical NVD-backed G11 failures remain preserved under the v1
toolchain contract and are never rewritten or reused.

The full-history checkout inputs, detached candidate worktrees, isolated Maven
repositories, environment, exact build/scanner commands, artifact path,
comparison procedure and canonical tool configuration hash are normative in
[`GA-SECURITY-TOOLCHAIN.md`](../release/GA-SECURITY-TOOLCHAIN.md). No
implementation-time alternative procedure may produce qualifying G9/G11
evidence.

Human approved Apache-2.0 as the repository/release policy. This ADR is not
legal advice. Human approved GitHub binary publication without a Maven Central
coordinate for this GA scope; requiring Maven 1.0.0 metadata or Central
publication changes build/release inputs and requires a new RC.

The exact approved generators, action SHAs, plugin JAR hashes, OCI digest,
inventory rules and license policy are normative in
[`GA-SECURITY-TOOLCHAIN.md`](../release/GA-SECURITY-TOOLCHAIN.md). Toolchain
drift is an Exception Gate, not an implementation choice.

#### Human-approved G11 portfolio policy amendment (2026-08-26)

The normative G11 gate changes from the NVD-backed v1 contract to
`OFFLINE_SUPPLY_CHAIN_SECURITY_V1`. Gate ID `G11` and its conjunctive nature do
not change. `ga-security-toolchain-v1.properties` and all historical FAIL/B3
runs remain immutable interpretation evidence; the new normative policy is
`ga-security-toolchain-v2.properties`, with gate-specific evidence schema
`g11-offline-supply-chain-evidence-v1`. Top-level GA evidence schemas remain
v1.

This is a qualification-policy/tooling amendment, not a candidate repair.
`v0.9.0-rc.1` remains immutable, no candidate defect was observed and rc.2 is
not required. A fresh offline G11 execution remains separately Human-gated.
G9 run `32856372581` remains qualifying/frozen and must not be rerun.

#### Human-approved Limited B3 provisioning amendment (2026-08-25)

The Microsoft OpenJDK identity remains `21.0.12+8-LTS` on `ubuntu-24.04`.
Because the GitHub Actions Microsoft resolver did not expose that exact version,
the two new GA workflows may provision the same JDK only from the official
Microsoft archive below:

```text
archive: microsoft-jdk-21.0.12-linux-x64.tar.gz
archive URL: https://aka.ms/download-jdk/microsoft-jdk-21.0.12-linux-x64.tar.gz
checksum URL: https://aka.ms/download-jdk/microsoft-jdk-21.0.12-linux-x64.tar.gz.sha256sum.txt
archive SHA-256: f2a84ad31ebeaf3a26252dd86a4a8e1b74aefb6bfc8e55fd20190110d1353c0f
```

The archive and sidecar must be verified before extraction into a fresh
`RUNNER_TEMP` directory; `JAVA_HOME`, `java`, `javac` and Maven identity must
be recorded. This is a qualification-only provisioning amendment. It does not
change the candidate, production source, dependency graph, scanner policy,
thresholds or G1-G12 semantics. JDK 21.0.11 and all other distributions are
forbidden. Replacement G9/G11 execution requires a separate Human approval;
the previous runs remain immutable `ABORTED / B3` evidence.

#### Historical / superseded NVD JSON 2.0 data-feed amendment (2026-08-25)

Under that historical contract, Dependency-Check `13.0.0` remained the pinned
dependency-to-CVE analyzer, and G11 data acquisition changed from anonymous/API
path to the official NVD JSON 2.0 data-feed path. The workflow validates
`nvdcve-2.0-modified.meta` and its gzip archive before invoking the scanner:
the metadata `lastModifiedDate` must be no older than 24 hours, `size` and
`gzSize` must match the downloaded bytes, gzip decoding must succeed, and the
SHA-256 of the uncompressed JSON must equal the metadata `sha256` field. The
metadata digest, compressed archive digest, content digest, source URLs, byte
sizes and freshness are retained in immutable evidence.

The scanner receives the approved template
`https://nvd.nist.gov/feeds/json/cve/2.0/nvdcve-2.0-{0}.json.gz` through
`-DnvdDatafeedUrl`; no custom vulnerability matcher is introduced and
`NVD_API_KEY` is not required or passed to the scanner. Missing, malformed,
stale or unusable feed data, scanner errors or missing JSON/SARIF reports
remain fail-closed B3 outcomes. `Dependency-Check=13.0.0`, CVSS blocker
threshold `7.0`, freshness `<=24h`, auto-update behavior, report formats,
SBOM/license/secret policies, candidate identity and all G1-G12 thresholds
remained unchanged. This historical amendment did not authorize a replacement
G11 execution or unlock TASK-049. It is superseded by the 2026-08-26 offline
G11 portfolio policy amendment above and is retained only to interpret prior
FAIL/B3 evidence.

#### Human-approved Limited G11 B2 remediation (2026-08-26)

Fresh offline G11 run `32925783003` failed before SBOM/dependency/license
qualification because the workflow invoked Maven as `mvn -f core/pom.xml`.
That qualification-only invocation changed Maven's project base and broke the
candidate's frozen root-relative Checkstyle path. The run and artifact
`9591451565` (digest
`2511c2276f40f83868db27591a2eb7afc644c4eeb7621a1cda8aa17af3cb40cf`) remain
`FAIL / B2 / NON-QUALIFYING / PRESERVED`; no candidate defect was observed.

Human authorized a limited workflow remediation only. Current v2 security
commands execute from the repository root, using `-pl core -am` for the
lifecycle build and `-pl core` for SBOM, runtime-dependency and license goals.
The v2 policy digest was recomputed to
`e834d18b0cb51624edbac40e6294bf575ebf73bab3a8cbf469423fba150de4fc`.
Candidate/POM/production/G9 inputs and G11 criteria remain unchanged, and the
remediation is implemented at `e1464ed` with Standard CI `32927818204` and
Quick Lane `32927818172` PASS. Its Evidence Gate is PASS; a fresh G11
execution remains separately Human-gated and is not authorized by this
remediation.

#### Human-approved shell-scope B2 remediation (2026-08-26)

The next Human-authorized fresh offline G11 run, `32943456313`, preserved a
`FAIL / B2 / NON-QUALIFYING` result after candidate identity, candidate build,
SBOM input and dependency input generation completed. The license-report
validation step referenced `REPO` without declaring it in that independent
shell block, so `set -u` terminated the workflow before the remaining G11
criteria. The failure artifact is `9597396741` with digest
`bc3ad708418d0194c05e695d4990daa1e9319480b6787493e4968f132fc17689`.

A read-only cross-step audit found no second undeclared local shell variable.
Human then approved a limited workflow-only remediation: declare
`REPO="$RUNNER_TEMP/ga-security-cache/m2"` in the license validation step and
add a static audit over every GA security `run:` block. The Maven command,
policy hash, candidate, POM, dependencies, production and G9 evidence remain
unchanged. The remediation is implemented at `eced533`, with Standard CI
`32945056542` and Quick Lane `32945056508` PASS. This Evidence Gate does not
authorize another G11 execution; a fresh G11 remains separately Human-gated.

### D16 — Release source and production source may be distinguished

The qualified production artifact source remains `e2828f5`. Human may either:

1. require `v1.0.0` to point exactly to `e2828f5`, with final documentation as
   GitHub Release assets; or
2. select a later documentation/qualification-only release-source commit if
   its production source, root/core build inputs, runtime configuration and
   dependency graph have zero diff from `e2828f5`, and a clean rebuilt JAR is
   byte-identical to the qualified candidate.

`ga-release-manifest-v1` records both production and release-source SHAs. This
decision never authorizes either option automatically.

### D17 — Blockers determine requalification

| Class | Meaning | Required action |
| --- | --- | --- |
| B0 GA-INTEGRITY | correctness/recovery/durability divergence, evidence tamper or verified secret | stop; root-cause; no GA |
| B1 CANDIDATE | production/build/runtime/dependency repair | new RC; rerun impacted Gates; all G1-G12 if uncertain |
| B2 QUALIFICATION | qualification-only harness/schema defect | Limited Remediation; rerun affected Gates and G12 |
| B3 ENVIRONMENT | external runner/storage/scanner outage | preserve `ABORTED`; Human authorizes replacement |
| B4 DOCS-RELEASE | docs/license/release metadata defect | repair; rerun G9/G10/G11/G12 as applicable |

### D18 — Release authority is separated

The following are separate Human authorities and cannot be inferred from one
another:

1. Phase 11 Blueprint Approval;
2. performance/capacity Full Campaign Approval;
3. two-hour soak approval;
4. six-hour soak approval;
5. Sol High Phase Closure Review;
6. Human Phase 11 Closure Approval;
7. Human GA decision naming the exact source and artifact;
8. explicit `v1.0.0` tag authorization;
9. Tag CI acceptance;
10. explicit GitHub Release publication and GA declaration.

No Agent may create `v1.0.0`, publish a GitHub Release or declare GA without
the corresponding Human authority.

## Alternatives Considered

| Option | Benefit | Reason not selected |
| --- | --- | --- |
| Directly promote RC | Fast | Existing engineering qualification is not a complete product release audit |
| Add more features first | Broader product | Mutates a qualified RC and expands risk instead of proving the frozen scope |
| Qualification-first GA Phase | Auditable candidate-to-release decision | Selected |
| Universal performance claim | Marketing simplicity | Unsupported across hosts/storage and therefore prohibited |

## Consequences

Phase 11 can produce evidence strong enough for a narrow GA decision while
preserving the candidate. It may also legitimately end in `FAIL`, `ABORTED` or
an `rc.2` recommendation. A successful Phase Closure still does not itself
publish or declare GA.

## Exception Gates

Stop for Sol High and Human review if work requires candidate mutation, a new
dependency, a changed workload/threshold/schema after execution starts, a
weaker claim, replacement evidence without approval, unsupported security or
license treatment, Maven publication, tag movement or any Product Release
action.

## Approval Record

| Date | Reviewer | Decision | Scope |
| --- | --- | --- | --- |
| 2026-08-25 | Human Developer | APPROVED — Human Phase 11 Blueprint Approval | ADR-0019 D1-D18 and TASK-047..056 qualification scope; candidate immutability, separate campaign gates and no release authority |
| 2026-08-26 | Human Developer | APPROVED — Limited G11 B2 Remediation | Qualification workflow Maven root-selector correction only; `32925783003` preserved FAIL/B2; no candidate/POM/production change and no new G11 execution |
| 2026-08-26 | Human Developer | APPROVED — Limited G11 license-report B2 Remediation | Preserve `license-maven-plugin:2.7.1:aggregate-third-party-report`; use repository-root `-pl core -am`; require `target/reports/aggregate-third-party-report.html`; `32929258318` preserved FAIL/B2; no candidate/POM/production change and no fresh G11 execution |
| 2026-08-26 | Evidence Gate | PASS — License-report B2 Remediation | `6cea0b2`; Standard CI `32932067229`; Quick Lane `32932067238`; HTML parseability, runtime-coordinate reconciliation and immutable validation-sidecar checks added; fresh G11 remains Human-gated |
| 2026-08-26 | Evidence Gate follow-up | PASS — License-report inventory parser compatibility | `30c89c4`; Standard CI `32932454011`; Quick Lane `32932454009`; Maven `dependency:list` module annotations are accepted without relaxing coordinate matching; fresh G11 remains Human-gated |
| 2026-08-26 | Human Developer | APPROVED — Limited shell-scope B2 Remediation | `32943456313` preserved FAIL/B2; workflow-only step-local `REPO` correction and static variable audit; no policy/candidate/POM/production change or fresh G11 execution |
| 2026-08-26 | Evidence Gate | PASS — G11 shell-scope B2 Remediation | `eced533`; Standard CI `32945056542`; Quick Lane `32945056508`; fresh G11 remains separately Human-gated |
| 2026-08-26 | Evidence Gate | PASS — TASK-048/Phase 11 status synchronization | `e51db47`; Standard CI `32945333516`; Quick Lane `32945333468`; current failure/remediation state reconciled; fresh G11 remains separately Human-gated |
