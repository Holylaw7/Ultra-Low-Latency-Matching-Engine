# ADR-0019: GA Qualification, RC Immutability and Release Authority

## Status

**Approved — Human Phase 11 Blueprint Approval, 2026-08-25**

This ADR authorizes the approved Phase 11 qualification design and its
dependency-ordered qualification-only task sequence. It does not authorize Full
Campaign execution, production changes, candidate mutation, CI changes to
existing workflows, tag creation, GitHub Release publication or a General
Availability declaration.

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
candidate tag:          v0.9.0-rc.2
annotated tag object:   9e2a67ada0e3b6220b730131d0bae79dc03073ed
peeled production SHA:  740e8a3dea0a759c707c597778c26c41e9bb3e47
candidate JAR SHA-256:  0B77D37985B9124AC4FD1B90D669DB550EFD0CF00C23AF65FDC29B35071703C4
Protocol v2 window:     N=8
WAL mode:               SYNC_EACH_APPEND
```

Every qualifying run records the candidate tag, tag-object SHA, peeled commit,
production tree digest, application JAR SHA-256 and qualification-controller
SHA. The former `v0.9.0-rc.1` identity and its evidence remain historical
reference material only.

### D3 — Candidate mutation requires a new RC

Any change to production source, production semantics, root/core build inputs,
runtime defaults, runtime dependency graph, Protocol/WAL/Snapshot formats or
packaged artifact makes `rc.2` ineligible for direct GA promotion. The repair
must produce a new candidate (for example `v0.9.0-rc.3`) and rerun every
affected Gate. If impact cannot be bounded with confidence, G1 through G12 all
rerun.

Existing `rc.1` evidence is never relabelled as evidence for a repaired RC.

### D4 — GA product scope is narrow and explicit

The proposed GA scope is:

- Java 21, single-node deployment;
- one MatchingEngine and the existing single-symbol topology;
- one active Protocol v2 session with the frozen bounded window `N=8`;
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

The Human-approved TASK-049 canonical mapping uses one physical execution per
matrix case and two independent canonical run views. Each case publishes a G1
`ga-run-manifest-v1` (`g1-v1`) and a G2 `ga-run-manifest-v1` (`g2-v1`) with
distinct run IDs and shared runtime/raw-evidence provenance. A separate
immutable `ga-g1-g2-physical-run-binding-v1` payload binds those views to one
physical execution without extending the frozen global schema. The 24-case
matrix therefore requires 24 physical executions, 24 G1 manifests, 24 G2
manifests, and independent G1/G2 gate-result documents; the task-specific
`ga-g1-g2-manifest-v1` remains a payload summary only.
The canonical run, inventory, binding and gate-result payloads publish and
verify their required adjacent SHA-256 sidecars; missing or mismatched
sidecars are evidence-contract failures.

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

The storage matrix uses segment sizes 4,128, 65,536 and 1,048,576 bytes. The
Human-approved TASK-050 A1 amendment removes the former 4,096-byte case because
it violates the frozen production minimum. Each of 50 graceful and 50 forced
cycles applies exactly 10,000 `LIFECYCLE_MIX` commands with seed 20260823
before shutdown/termination. The exact corruption fixtures in the GA Matrix
are mandatory. Dynamic
`force(true)` failure injection remains **not verified**: G3 accepts the frozen
implementation-path/terminal-state review and dynamic rotation failure. Adding
a force-injection production seam would mutate the candidate and require rc.2.

### D11 — Performance SLO is fixed before execution

G4 uses three independent comparable ten-minute
`MEMORY_STEADY_STATE_V1` runs with seed `20260823`, the frozen RC2 Protocol v2
TCP path with bounded window `N=8`, `SYNC_EACH_APPEND`, Pipeline
`1024/BLOCKING`, and a bounded closed-loop continuous-refill client. Every run
must meet:

| Metric | SLO |
| --- | ---: |
| accepted throughput | `>= 500 commands/s` |
| response P50 | `<= 2.5 ms` |
| response P99 | `<= 5 ms` |
| response P99.9 | `<= 10 ms` |
| errors/timeouts/sequence mismatch | `0` |

Sixty fresh lifecycle cycles must produce 60 startup and 60 graceful-shutdown
samples; each P99 must be `<= 1.25 s`. Management is evaluated as two fresh,
symmetrical pairs (`IDLE -> STATUS@1Hz` and `STATUS@1Hz -> IDLE`), with a 60 s
warmup and five-minute measurement per trial. Each pair may regress neither
throughput nor P99 by more than 10% against its idle trial. These are
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

#### Human-approved G11 false-positive disposition amendment (2026-08-26)

The v2 Gitleaks scan remains mandatory, full-history and candidate-bound; this
amendment adds only a machine-verifiable disposition path for demonstrably
non-secret findings. Raw reports are retained and every finding must either
have an exact entry in
`ga-gitleaks-false-positive-dispositions-v1.properties` or fail G11. An entry
binds the pinned scanner/rule, scan scope, canonical path, full historical
commit or immutable candidate production SHA, blob object ID, line range,
exact Gitleaks fingerprint, `DEMONSTRABLE_NON_SECRET` classification and a
digest of a fixed non-secret rationale code. Match text, secret material,
secret hashes and transformed values are prohibited.

The approved manifest is not a broad Gitleaks suppression and does not permit
inline comments, `.gitleaksignore`, path-wide rules or rule-wide exclusions.
Unknown, changed, verified or credible-secret findings remain blockers. The
workflow must execute the candidate-bound scan even when full-history findings
are dispositioned, and must publish both raw reports, the manifest and a safe
evaluation sidecar in the immutable SHA-256 inventory. Run `32947367541`
remains preserved `FAIL / NON-QUALIFYING`; this qualification-policy amendment
does not reclassify it or authorize a fresh G11 execution.

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

#### Human-approved candidate Gitleaks path-contract B2 remediation (2026-08-26)

The Human-authorized fresh offline G11 run `32952590543` preserved a
`FAIL / B2 / NON-QUALIFYING` result after the candidate-bound Gitleaks scan
completed. Its raw candidate finding used the absolute mounted path
`/repo/tasks/reports/PHASE-10-task-043.md`, while the approved disposition and
evaluator contract require the repository-relative path
`tasks/reports/PHASE-10-task-043.md`. The candidate file exists in immutable
production SHA `e2828f5`, and its resolved Blob SHA-1 matches the approved
disposition; no candidate defect was observed. The run remains preserved and
is not reclassified or reused.

Human approved a limited qualification-workflow remediation only: invoke the
candidate-bound scan from the mounted checkout root with `dir .`, preserve the
raw report, and keep the evaluator fail-closed for absolute, traversal,
separator-ambiguous or out-of-root paths. The v2 policy now records the
`repository-relative-v1` path contract and `working-directory` scan mode; its
policy digest is recomputed. No disposition identity, candidate, production,
POM, dependency, G9 evidence or G11 criterion changes. This remediation does
not authorize another G11 execution; a fresh G11 remains separately
Human-gated. The implementation is `f6db140`; Standard CI `32954953854` and
Quick Lane `32954953801` PASS, with focused/full tests and frozen-boundary
audits PASS.

The separately Human-authorized fresh offline G11 run `32955619875` then
completed `PASS / QUALIFYING / FROZEN` under the amended path contract. Its
immutable artifact is `9601871146` with GitHub-reported digest
`sha256:5c4a54e3c28ec14d7709b4a5e747d79aa4bb710d4cb80b8ee489e31912cc7afd`;
candidate identity, repository-relative Gitleaks disposition evaluation,
SBOM/dependency/license/secret evidence, strict inventory hashes and
publication all passed. Historical failed runs remain preserved and TASK-048
still requires final evidence review; no additional G11 execution is
authorized.

#### Human-approved TASK-050 Standard checkout-history exception (2026-08-31)

The Round 3.1 qualification remediation changed the existing Standard CI
checkout contract to `fetch-depth: 0` so the frozen-boundary verifier can
resolve the immutable production baseline object
`e2828f563ee41316c062385c0244ac1336731359`. This was a qualification-only
exception to the otherwise frozen workflow boundary, authorized after
Standard CI `33374002293` failed because the default shallow checkout omitted
that historical object. The change is recorded at remediation SHA
`b04786420bafd838ac4e0b378a674f766430f3bb`; Standard CI `33377267636` and
Quick Lane `33377267660` both passed with exact-SHA binding. It changes neither
production semantics nor candidate/build inputs and does not authorize the
formal G3/G7 campaign. The historical Standard failure remains preserved as
non-qualifying evidence.

#### Human-approved TASK-050 post-closure qualification-harness B2 remediation exception (2026-09-01)

After TASK-050 was closed, Human authorized a narrowly bounded,
qualification-only remediation for a false-negative lifecycle/observation
race in the G7 harness. This section records previously granted Human
decisions; it does not reopen TASK-050, authorize another TASK-050 campaign,
or change frozen evidence.

The recorded scope is limited to:

- `GaOverloadRunner.java` and `GaOverloadRunnerTest.java`;
- `MANAGEMENT_BOUND` diagnostic instrumentation;
- the observable server-release completion barrier;
- `PIPELINED_REQUEST` diagnostic instrumentation; and
- associated qualification-only regression tests.

The historical intermediate remediation/review controller is
`5b4998d8855d4e418b2e897129571c8c16de700d`. The current TASK-051
qualification remediation/review object is
`1bdab634de6c580327b1c9677a45fb08526331f1`. The historical Standard run
`33485052068` remains `FAIL / B2 / PRESERVED`; later validation does not
reclassify or replace it. No production, POM, dependency, workflow,
candidate, or formal G7 campaign mutation was authorized by this exception.
No evidence of candidate impact or formal G7 false-PASS impact was
established, so TASK-050 remains `CLOSED`, G3/G7 remain
`PASS / QUALIFYING / FROZEN`, and `rc.2` is not required.

The qualification remediation/review object and any later documentation-only
governance synchronization object are distinct. A future docs-sync commit has
its own actual Git SHA, which must not be predicted or written before that
commit exists and must not replace `1bdab634...` as qualification provenance.

### D16 — Release source and production source may be distinguished

The frozen RC2 production artifact source is `740e8a3`. Human may either:

1. require `v1.0.0` to point exactly to `740e8a3`, with final documentation as
   GitHub Release assets; or
2. select a later documentation/qualification-only release-source commit if
   its production source, root/core build inputs, runtime configuration and
   dependency graph have zero diff from `740e8a3`, and a clean rebuilt JAR is
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
a new-RC recommendation. A successful Phase Closure still does not itself
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
| 2026-08-26 | Evidence Gate follow-up | PASS — Final B2 remediation audit checkpoint | `688d955`; Standard CI `32946223271`; Quick Lane `32946223268`; verifier and frozen-path checks remain PASS; fresh G11 remains separately Human-gated |
| 2026-08-26 | Human Developer | APPROVED — Limited candidate Gitleaks path-contract B2 Remediation | `32952590543` preserved FAIL/B2; switch candidate scan to root-relative `dir .`, record `repository-relative-v1` / `working-directory` policy identity; no candidate/POM/production/G9 change or fresh G11 execution |
| 2026-08-26 | Evidence Gate | PASS — Candidate Gitleaks path-contract B2 Remediation | `f6db140`; Standard CI `32954953854`; Quick Lane `32954953801`; native `dir .` path contract, focused/full verification and frozen-boundary audit PASS; fresh G11 remains separately Human-gated |
| 2026-08-26 | Evidence Gate | PASS — Fresh G11 under amended path contract | `32955619875`; artifact `9601871146`; GitHub digest `sha256:5c4a54e3c28ec14d7709b4a5e747d79aa4bb710d4cb80b8ee489e31912cc7afd`; G11 qualifying/frozen; TASK-048 final evidence review pending; no additional G11 execution |
 | 2026-09-01 | Human | APPROVED — TASK-050 post-closure qualification-harness B2 remediation exception | `GaOverloadRunner.java` / `GaOverloadRunnerTest.java`; `MANAGEMENT_BOUND` release barrier and diagnostics plus `PIPELINED_REQUEST` diagnostics; TASK-050 remains CLOSED and formal G3/G7 evidence remains frozen |
| 2026-09-05 | Human Developer | APPROVED — RC2 TASK-053 G4 final pre-execution qualification/evidence remediation | Accepted-throughput accounting, final candidate health, lifecycle/management evidence, strict STATUS@1Hz, raw hash/recomputation, stop-on-first-blocker and active RC2 contract only; no production/candidate/G5 change; formal G4 remains Human-gated |

The `688d955` entry is the final remediation audit checkpoint. Any later
docs-only commit that records this checkpoint is external validation only and
is not a new Closure Input; it must not create a self-referential SHA update
cycle.
