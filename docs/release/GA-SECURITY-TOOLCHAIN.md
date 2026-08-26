# GA Security Toolchain — v2 Normative / v1 Historical

## Status

`ga-security-toolchain-v2.properties` is the current Human-approved normative
G11 contract. It defines `OFFLINE_SUPPLY_CHAIN_SECURITY_V1` for this portfolio
release. `ga-security-toolchain-v1.properties` is retained byte-for-byte as the
historical NVD-backed contract so every earlier G11 FAIL/B3 artifact remains
interpretable. Historical evidence is never reclassified or reused.

Tools run only after the applicable Human execution gate and do not modify the
candidate build or runtime dependency graph. The policy amendment itself does
not authorize a fresh G11 run.

## Current normative G11 contract

The current G11 evidence unit is
`g11-offline-supply-chain-evidence-v1`. It is conjunctive and requires:

1. exact candidate tag object, peeled production commit and tree identity;
2. a freshly built candidate application JAR with SHA-256 provenance;
3. a structurally valid, non-empty CycloneDX 1.6 JSON SBOM;
4. an independent Maven runtime dependency inventory whose normalized
   coordinates exactly equal the SBOM component coordinates;
5. deterministic SPDX disposition for every runtime dependency, limited to
   the v2 policy's accepted set;
6. pinned Gitleaks full-history and candidate-bound scans where every finding
   is either an exact approved non-secret disposition or a zero-result, with
   verified/credible secrets always blocking;
7. pinned JDK, Maven, CycloneDX, dependency-inventory, license and OCI tool
   identity/provenance;
8. a non-empty root-reactor `aggregate-third-party-report.html` license
   artifact reconciled with the runtime dependency inventory;
9. normalized regular-file inventory, no symlink/non-regular/duplicate or
   out-of-bound path, strict `SHA256SUMS` validation and fail-closed GitHub
   artifact publication.

The canonical v2 policy SHA-256 is
`2b9ee7de9aee3e153d76ded1118434e8bc93807b2d329442e4593839b8e4b87f`.
Dependency-Check, NVD data/API access, CVE lookup, CVSS evaluation and database
freshness are not normative inputs or outputs of v2. They are outside this
portfolio-release boundary. The workflow must not emit a fake or skipped
Dependency-Check PASS.

Permitted claim:

> `v0.9.0-rc.1 passed OFFLINE_SUPPLY_CHAIN_SECURITY_V1: candidate-bound SBOM,
> runtime dependency inventory, license, repository secret-scan and immutable
> artifact evidence were validated.`

Mandatory limitation:

> `Current external CVE/NVD vulnerability evaluation is outside this
> portfolio-release boundary. No claim is made that dependencies are free of
> currently known vulnerabilities.`

### Human-approved G11 false-positive disposition amendment (2026-08-26)

The current v2 Gitleaks contract remains fail-closed and does not use
`.gitleaksignore`, path-wide exclusions or inline allow comments. The only
additional disposition mechanism is the exact, versioned manifest
`ga-gitleaks-false-positive-dispositions-v1.properties`, whose SHA-256 is
`0854c43f9138d8073f640fe1e37f97c7d482f01bcbe3e8280534ee3cbc70466c`.

The workflow retains both raw JSON reports, executes both scans even when the
history scan returns findings, and evaluates each finding against the manifest
after the scanner exits. A disposition binds the pinned scanner/rule, scope,
canonical repository path, full commit or candidate production identity, blob
object ID, line range, exact Gitleaks fingerprint, classification and a digest
of a fixed non-secret rationale code. The manifest contains no match, secret,
secret hash, transformed secret or sensitive line. Unknown, changed or
unapproved findings fail G11; only an exact `DEMONSTRABLE_NON_SECRET`
disposition is accepted. Candidate-bound scanning is mandatory and is never
represented as passed merely because the history scan was classified.

The two findings approved by this amendment are narrowly bound to the
historical environment-variable documentation at commit `993c2477` and the
properties-schema prose at commit `d753af0`; the latter also has a separate
candidate-bound blob disposition for the immutable candidate tree. These are
safe metadata references only. The original run `32947367541` remains
`FAIL / NON-QUALIFYING / PRESERVED`; the amendment does not reclassify or reuse
that artifact and does not authorize a fresh G11 execution.

The candidate-bound Gitleaks scan is rooted at the exact mounted immutable
candidate checkout (`/repo`) but invokes `dir .` from that working directory.
This is the normative `repository-relative-v1` path contract: raw findings must
contain repository-relative `File` values, and the evaluator rejects absolute,
traversal, separator-ambiguous or otherwise non-canonical paths. The raw JSON
report remains unmodified evidence; no arbitrary prefix stripping is allowed.
The policy records this contract as
`gitleaks.candidatePathContract=repository-relative-v1` and
`gitleaks.candidateScanMode=working-directory`; changing either is a normative
toolchain identity change requiring a new policy digest and validator value.

The current v2 candidate invocation is the mounted-root form
`docker ... --workdir=/repo ... dir . ...`; the v1 historical command below is
retained unchanged for historical evidence interpretation only.

## Pinned workflow foundation

| Component | Pin |
| --- | --- |
| runner | GitHub-hosted `ubuntu-24.04`; exact image OS/package manifest recorded at run time |
| checkout | `actions/checkout@11bd71901bbe5b1630ceea73d27597364c9af683` (v4.2.2), `fetch-depth: 0`, `fetch-tags: true`, `persist-credentials: false` |
| Java setup | Official Microsoft archive provisioning in the new GA workflows; the prior `actions/setup-java@c5195efecf7bdfc987ee8bae7a71cb8b11521c00` resolver is not used for JDK installation after the approved B3 amendment |
| JDK | Microsoft Build of OpenJDK `21.0.12+8-LTS`, Linux x64 archive `microsoft-jdk-21.0.12-linux-x64.tar.gz`, archive SHA-256 `f2a84ad31ebeaf3a26252dd86a4a8e1b74aefb6bfc8e55fd20190110d1353c0f` |
| Maven | Apache Maven `3.9.16`, build commit `2bdd9fddda4b155ebf8000e807eb73fd829a51d5`; executable/distribution hash recorded; version drift ABORTS |
| Evidence publication | `actions/upload-artifact@043fb46d1a93c77aae656e7c1c64a875d1fc6a0a` (v7.0.1); one deterministic gate artifact, `if-no-files-found=error`, `retention-days=14`, `compression-level=6`, `overwrite=false`, `include-hidden-files=false`, `if: always()` |

Workflow permissions are `contents: read`; no write, package, release or OIDC
permission is allowed. Network access is used only for pinned tool/artifact
retrieval. Download failure or identity mismatch is `ABORTED`.

Each GA workflow publishes one gate-specific artifact after its evidence
directory is finalized (or partially populated after a failure). The workflow
records the action's `artifact-id`, `artifact-url` and SHA-256 `artifact-digest`
in the run summary. Evidence review must query the Actions artifact API,
download the archive, verify the returned digest, and then run the internal
`SHA256SUMS --check --strict` and manifest cross-reference checks. The artifact
name/path/options and full action commit above are normative; a floating action
tag, overwrite, missing-file warning or unverified download is not equivalent
evidence.

### Approved JDK archive provisioning amendment

The exact Microsoft JDK identity is unchanged. The resolver mechanism changed
only because the GitHub Actions Microsoft catalog did not expose `21.0.12` to
`actions/setup-java`. Both GA workflows now download the official versioned
Linux x64 archive and its `.sha256sum.txt` sidecar, require the sidecar digest
to equal the frozen value below, verify the downloaded archive before
extraction, reject unsafe archive paths, extract into a fresh `RUNNER_TEMP`
directory, and record `JAVA_HOME`, `java -version`, `javac -version` and Maven
runtime evidence.

```text
vendor: Microsoft
product: Microsoft Build of OpenJDK
version/build: 21.0.12+8-LTS
platform: linux-x64
archive: microsoft-jdk-21.0.12-linux-x64.tar.gz
archive URL: https://aka.ms/download-jdk/microsoft-jdk-21.0.12-linux-x64.tar.gz
checksum URL: https://aka.ms/download-jdk/microsoft-jdk-21.0.12-linux-x64.tar.gz.sha256sum.txt
archive SHA-256: f2a84ad31ebeaf3a26252dd86a4a8e1b74aefb6bfc8e55fd20190110d1353c0f
runner: ubuntu-24.04
extraction: fresh RUNNER_TEMP directory
```

The archive digest is a normative input. A sidecar mismatch, archive mismatch,
runtime identity mismatch or extraction failure is `ABORTED`; the workflow may
not fall back to JDK 21.0.11 or another distribution. The old runs
`32835408168` and `32835411241` remain immutable `ABORTED / B3` evidence.
The canonical properties file retains the historical `action.setupJava.sha`
field for provenance compatibility, but neither new GA workflow invokes that
resolver after this amendment.

### Current v2 Maven root-selector contract

All current v2 security-workflow Maven commands execute from the candidate
repository root so existing root-relative build configuration retains its
meaning. The lifecycle build uses `mvn -B -ntp -pl core -am package
-DskipTests`; CycloneDX and runtime-dependency goals use the same root
invocation with `-pl core`, while the aggregator license report uses
`-pl core -am` so the Maven execution root generates the report. The
historical `mvn -f core/pom.xml` form belongs only to preserved pre-remediation
evidence and is forbidden for the current v2 workflow. This qualification-only
invocation correction does not change the candidate POM, build inputs,
dependency graph or artifact identity.

### Current v2 license-report contract

The pinned `license-maven-plugin:2.7.1:aggregate-third-party-report` goal is
invoked directly from the repository execution root with
`license.executeOnlyOnRootModule=true`, `-pl core -am`, and the frozen runtime
scope options. The normative source artifact is:

```text
target/reports/aggregate-third-party-report.html
```

`core/target/reports/aggregate-third-party-report.html` is not a valid v2
artifact path. The workflow rejects that legacy path, copies only the
non-empty root report into
`license/plugin-reports/aggregate-third-party-report.html`, and includes it in
the immutable evidence inventory and strict `SHA256SUMS` validation. The
qualification validator parses the UTF-8 HTML structure, checks the pinned
plugin marker and dependency headings, and reconciles the report coordinates
with the independent runtime inventory. It writes
`license/plugin-reports/report-validation.txt` with the report SHA-256, byte
count, coordinate count and match result; that sidecar is itself mandatory in
the immutable inventory. Missing, empty, malformed, non-parseable or
unreconciled report output fails closed. The v2 policy freezes
`license.reactorAlsoMake=true`, `license.reactorProject=core`, and the exact
`license.reportPath` above; these fields describe qualification invocation
identity only and do not alter the candidate POM or dependency graph.

## Full-history checkout and G9 reproducible build contract

The workflow first checks out its qualification-controller SHA with the pinned
checkout action and the exact inputs above. It then verifies that the checkout
is not shallow, that the annotated RC tag exists locally and that both tag
identities are exact:

```bash
test "$(git rev-parse --is-shallow-repository)" = "false"
test "$(git cat-file -t refs/tags/v0.9.0-rc.1)" = "tag"
test "$(git rev-parse refs/tags/v0.9.0-rc.1)" = \
  "dfd38c08e80aed9035bf1c2d7c8faf8bae99c356"
test "$(git rev-parse 'refs/tags/v0.9.0-rc.1^{}')" = \
  "e2828f563ee41316c062385c0244ac1336731359"
```

Gitleaks history evidence is valid only from that full checkout. No shallow
fallback, partial commit range or repository archive is acceptable for the
history scan.

G9 uses two fresh directories under `${RUNNER_TEMP}` and two initially empty,
independent Maven local repositories. Both worktrees detach at the peeled
candidate SHA, never at the controller or post-tag documentation SHA. The
following setup and build sequence is normative:

```bash
export LANG=C.UTF-8
export LC_ALL=C.UTF-8
export TZ=UTC
export MAVEN_OPTS='-Dfile.encoding=UTF-8 -Duser.language=en -Duser.country=US -Duser.timezone=UTC'
export SOURCE_DATE_EPOCH="$(git show -s --format=%ct e2828f563ee41316c062385c0244ac1336731359)"
export GA_BUILD_A="${RUNNER_TEMP}/ga-build-a"
export GA_BUILD_B="${RUNNER_TEMP}/ga-build-b"

test ! -e "${GA_BUILD_A}"
test ! -e "${GA_BUILD_B}"
mkdir -p "${GA_BUILD_A}/m2" "${GA_BUILD_B}/m2"
git worktree add --detach "${GA_BUILD_A}/source" e2828f563ee41316c062385c0244ac1336731359
git worktree add --detach "${GA_BUILD_B}/source" e2828f563ee41316c062385c0244ac1336731359

(cd "${GA_BUILD_A}/source" && mvn -B -ntp -e -C \
  -Dmaven.repo.local="${GA_BUILD_A}/m2" \
  -Dproject.build.outputTimestamp="${SOURCE_DATE_EPOCH}" \
  -pl core -am clean verify)

(cd "${GA_BUILD_B}/source" && mvn -B -ntp -e -C \
  -Dmaven.repo.local="${GA_BUILD_B}/m2" \
  -Dproject.build.outputTimestamp="${SOURCE_DATE_EPOCH}" \
  -pl core -am clean verify)

test -z "$(git -C "${GA_BUILD_A}/source" diff --name-only)"
test -z "$(git -C "${GA_BUILD_B}/source" diff --name-only)"
cmp --silent \
  "${GA_BUILD_A}/source/core/target/matching-engine-rc.jar" \
  "${GA_BUILD_B}/source/core/target/matching-engine-rc.jar"
```

The workflow records both Maven/JDK/runtime identities; candidate tag object,
peeled SHA and Git tree; `SOURCE_DATE_EPOCH`; canonical source-tree digest from
`git archive --format=tar e2828f563ee41316c062385c0244ac1336731359 |
sha256sum`; both worktree paths after token normalization; the complete hashes
of both isolated Maven repository inventories; both JAR paths, sizes and
SHA-256 values; `cmp` result; and complete Maven logs. The only candidate
artifact path is `core/target/matching-engine-rc.jar`. Build A's JAR becomes the
proposed release artifact only after byte identity is established; Build B is
comparison evidence. Any pre-existing directory, checkout mismatch, source
diff, missing artifact, repository sharing or byte/hash mismatch is B0/FAIL,
not a retry. No third build is automatic.

## Historical v1 NVD-backed contract

The remainder of this section preserves the original v1 scanner, generator and
NVD acquisition contract solely to interpret historical G11 runs. It is not
the current qualifying G11 procedure and must not be invoked or quoted as a v2
PASS requirement. Common pins may be shared only where the v2 properties file
also freezes them explicitly.

## Pinned scanners and generators (historical v1)

| Purpose | Exact tool | Integrity pin / rule |
| --- | --- | --- |
| SBOM | `org.cyclonedx:cyclonedx-maven-plugin:2.9.3:makeBom` against `core/pom.xml` | plugin JAR SHA-256 `c452d5eebe28bc86bef2e7c72d129f04f60877bef843eac8120f01fb655be293`; CycloneDX JSON 1.6, reproducible mode, runtime scope |
| vulnerability | `org.owasp:dependency-check-maven:13.0.0:check` against `core/pom.xml` | plugin JAR SHA-256 `627ab0406bde4ae5261a5dc002cd233ddf6a044d6b3b92950188abf1951b4b13`; NVD update timestamp, feed/database hash and plugin report hash recorded |
| license inventory | `org.codehaus.mojo:license-maven-plugin:2.7.1:aggregate-third-party-report` plus Phase-11 policy validator | plugin JAR SHA-256 `ce1430955c570d3d742e917f4bc2f89ec19752d92887f02aef6d360b3883fbb7`; report and dependency-POM hashes recorded |
| secret history | `ghcr.io/gitleaks/gitleaks:v8.30.1` | OCI digest `sha256:c00b6bd0aeb3071cbcb79009cb16a60dd9e0a7c60e2be9ab65d25e6bc8abbb7f`; scan `git --all` and working tree; SARIF/JSON hash recorded |

Maven plugin invocation is qualification-only and must not add the plugins to
root/core poms. The resolver must verify the pinned plugin JAR SHA-256 in the
local repository before invoking its goal. All transitive tool artifacts and
tool reports are inventoried even though only the plugin entry JAR is the
architecture pin.

## Canonical configuration and exact invocations (historical v1)

The ASCII LF-terminated file
[`ga-security-toolchain-v1.properties`](ga-security-toolchain-v1.properties) is
the canonical option inventory. Its amended SHA-256 is:

```text
c677eaa8c09b17d6212f578830fa5e483f9b5bd961b8f477585d9d576ab5700e
```

TASK-048 must verify that hash before invoking a tool. The result manifest
records the properties-file SHA, every plugin/OCI identity, the candidate and
controller SHAs, `SOURCE_DATE_EPOCH`, normalized command template, actual
expanded command, exit code, start/end UTC and every output/data-directory
artifact hash. `${GA_EVIDENCE_DIR}` and `${GA_CACHE_DIR}` are fresh absolute
paths under the controller workspace; their concrete values are evidence
metadata but are replaced by those literal tokens when configuration identity
is calculated. `${SOURCE_DATE_EPOCH}` is the peeled candidate commit timestamp
and is qualification-critical.

The following Bash commands are normative for the pinned `ubuntu-24.04`
workflow. Line continuations are presentation only; argument values are exact.

```bash
test "$(sha256sum docs/release/ga-security-toolchain-v1.properties | cut -d' ' -f1)" = \
  "c677eaa8c09b17d6212f578830fa5e483f9b5bd961b8f477585d9d576ab5700e"

mvn -B -ntp -f core/pom.xml \
  org.cyclonedx:cyclonedx-maven-plugin:2.9.3:makeBom \
  -DschemaVersion=1.6 \
  -DprojectType=application \
  -DincludeBomSerialNumber=false \
  -DincludeCompileScope=true \
  -DincludeProvidedScope=false \
  -DincludeRuntimeScope=true \
  -DincludeSystemScope=false \
  -DincludeTestScope=false \
  -DincludeLicenseText=false \
  -DoutputFormat=json \
  -DoutputName=bom \
  -DoutputDirectory="${GA_EVIDENCE_DIR}/sbom" \
  -DoutputTimestamp="${SOURCE_DATE_EPOCH}" \
  -Dcyclonedx.skipAttach=true

# Validate the official NVD JSON 2.0 modified feed before this invocation.
# The validator records the .meta digest, compressed archive digest,
# uncompressed content digest, byte sizes, lastModifiedDate and age.
# Dependency-Check remains the only dependency-to-CVE analyzer.
mvn -B -ntp -f core/pom.xml \
  org.owasp:dependency-check-maven:13.0.0:check \
  -DautoUpdate=true \
  -DdataDirectory="${GA_CACHE_DIR}/dependency-check-data" \
  -DnvdDatafeedUrl="https://nvd.nist.gov/feeds/json/cve/2.0/nvdcve-2.0-{0}.json.gz" \
  -Dformats=JSON,SARIF \
  -DfailBuildOnCVSS=7.0 \
  -DfailOnError=true \
  -DskipTestScope=true \
  -DskipProvidedScope=true \
  -DskipRuntimeScope=false \
  -DskipSystemScope=true \
  -DskipDependencyManagement=true \
  -DscanDependencies=true \
  -DscanPlugins=false \
  -DenableExperimental=false \
  -DenableRetired=false \
  -DversionCheckEnabled=false \
  -DfailBuildOnUnusedSuppressionRule=true

mvn -B -ntp -f core/pom.xml \
  org.codehaus.mojo:license-maven-plugin:2.7.1:aggregate-third-party-report \
  -Dlicense.includeTransitiveDependencies=true \
  -Dlicense.includeOptional=true \
  -Dlicense.excludedScopes=test,provided,system \
  -Dlicense.encoding=UTF-8

docker run --rm --network=none --workdir=/repo \
  --volume="${PWD}:/repo:ro" \
  --volume="${GA_EVIDENCE_DIR}/gitleaks:/out" \
  ghcr.io/gitleaks/gitleaks@sha256:c00b6bd0aeb3071cbcb79009cb16a60dd9e0a7c60e2be9ab65d25e6bc8abbb7f \
  git --log-opts=--all --redact=100 --report-format=json \
  --report-path=/out/history.json --exit-code=1 --no-banner --no-color

docker run --rm --network=none --workdir=/repo \
  --volume="${PWD}:/repo:ro" \
  --volume="${GA_EVIDENCE_DIR}/gitleaks:/out" \
  ghcr.io/gitleaks/gitleaks@sha256:c00b6bd0aeb3071cbcb79009cb16a60dd9e0a7c60e2be9ab65d25e6bc8abbb7f \
  dir /repo --max-target-megabytes=10 --redact=100 --report-format=json \
  --report-path=/out/working-tree.json --exit-code=1 --no-banner --no-color
```

Before each Maven goal, the workflow resolves the exact GAV, verifies the
resolved entry JAR under the isolated Maven repository against the table hash,
and only then runs the evidence-producing goal in a second Maven invocation.
The resolution invocation, repository inventory and SHA-256 sidecar are part of
the Gate artifact. Gitleaks image identity must equal the pinned OCI digest
reported by the local container runtime before either scan. No `.gitleaks.toml`
or `.gitleaksignore` suppression file is accepted; the pinned image's embedded
default rule set plus the exact v2 disposition manifest is the configuration.
Dependency-Check runs with no suppression file.

CycloneDX writes directly to `${GA_EVIDENCE_DIR}/sbom`. The Dependency-Check
and license report goals have no approved command-line output-directory
override: their exact outputs are collected from `core/target/` and
`core/target/reports/` only after successful completion, with source and copied
artifact hashes both recorded. All JSON/report outputs are retained verbatim
and SHA-256 sidecars cover the complete SBOM, vulnerability, license, secret,
Maven-repository and NVD-data artifact inventories. A missing option, extra
option, format substitution, unrecorded environment expansion or
configuration-hash mismatch is B0; Luna is not authorized to choose alternate
evidence-affecting arguments during TASK-048.

## Vulnerability policy (historical v1 only)

- Dependency-Check database update must complete during the run; database/feed
  timestamp and hash are recorded. If the feed is unavailable, stale by more
  than 24 hours relative to run start or cannot be identified, outcome is
  `ABORTED`.
- Runtime-scope CVSS v3/v4 score `>=7.0` (High/Critical), or a scanner-labelled
  High/Critical without a score, blocks G11.
- Test/qualification/tool-only findings are reported and classified but do not
  silently become runtime blockers.
- Suppression requires a separate Human-approved file naming CVE, dependency,
  evidence, scope and expiry. No inline/ad-hoc suppression is permitted.

The approved acquisition mode is the official NVD JSON 2.0 data feed. The
workflow downloads `nvdcve-2.0-modified.meta` and its gzip archive from
`https://nvd.nist.gov/feeds/json/cve/2.0`, validates the metadata fields
`lastModifiedDate`, `size`, `gzSize` and `sha256`, verifies gzip integrity,
compressed byte size and the SHA-256 of the uncompressed JSON, and rejects
future or older-than-24-hour metadata. The official `.meta` hexadecimal digest
is accepted case-insensitively and recorded in canonical lowercase form. It
records both feed URLs, the metadata
SHA-256, compressed archive SHA-256, uncompressed content SHA-256, sizes and
freshness in `nvd-update-provenance.txt` and publishes the validated feed files
with the immutable G11 artifact. Dependency-Check `13.0.0` then receives the
same official template through `-DnvdDatafeedUrl`; it performs the normal
dependency analysis and emits JSON/SARIF. No custom vulnerability matcher is
implemented.

`NVD_API_KEY` is not required or passed to the scanner. The credential
provenance records only `credential.logicalName=NVD_API_KEY`,
`credential.present=false`, `credential.used=false`,
`credential.mode=NOT_REQUIRED` and the official feed source. Missing,
malformed, stale or unusable feed data, scanner errors, missing reports or
evidence-integrity failures remain `ABORTED`/B3. This data-feed amendment does
not alter Dependency-Check, freshness, severity, suppression, license, SBOM,
candidate identity or any G1-G12 threshold.

## Secret policy (v2 disposition contract; historical v1 retained)

Gitleaks scans the complete reachable history (`--all`) and current working
tree. A verified credential/token/private key is B0. A finding is accepted only
when the raw report's exact fingerprint, scanner/rule, scope, canonical path,
commit or candidate identity, blob, line range and rationale digest match the
Human-approved v2 disposition manifest. All other findings remain unresolved
and block G11. The original raw reports remain preserved; redaction must not
remove fingerprint/path/commit evidence needed for audit. The manifest never
contains a match, secret, secret hash or transformed secret, and broad path or
rule suppressions are forbidden.

## License policy (historical v1; accepted SPDX set retained by v2)

For runtime dependencies the accepted SPDX set is:

```text
Apache-2.0
BSD-2-Clause
BSD-3-Clause
MIT
ISC
```

Unknown/unparseable licenses and `GPL-*`, `AGPL-*`, `SSPL-*`, Commons Clause or
other source-available/non-open runtime terms block G11 pending Human/legal
review. Test/qualification tool licenses are inventoried separately. The
accepted project license policy is Apache-2.0, subject to the Human's legal
assessment; this policy is engineering governance, not legal advice.

## Update rule (historical v1)

Any version, coordinate, action SHA, OCI digest, database freshness rule,
severity threshold, allowed-license set or suppression policy change requires
Sol High review and Human Blueprint/Exception approval before new evidence can
qualify. Existing results stay bound to this exact manifest.
