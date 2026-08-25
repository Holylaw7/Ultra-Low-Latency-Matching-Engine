# GA Security Toolchain v1 — Normative Proposal

## Status

`Approved / Frozen by Human Phase 11 Blueprint Approval (2026-08-25)` with the
Human-approved Limited B3 Environment / Security Toolchain Amendment. This
manifest freezes G9/G11 tooling. Tools run only after the applicable Task
Evidence Gate and do not modify the
candidate build or runtime dependency graph.

## Pinned workflow foundation

| Component | Pin |
| --- | --- |
| runner | GitHub-hosted `ubuntu-24.04`; exact image OS/package manifest recorded at run time |
| checkout | `actions/checkout@11bd71901bbe5b1630ceea73d27597364c9af683` (v4.2.2), `fetch-depth: 0`, `fetch-tags: true`, `persist-credentials: false` |
| Java setup | Official Microsoft archive provisioning in the new GA workflows; the prior `actions/setup-java@c5195efecf7bdfc987ee8bae7a71cb8b11521c00` resolver is not used for JDK installation after the approved B3 amendment |
| JDK | Microsoft Build of OpenJDK `21.0.12+8-LTS`, Linux x64 archive `microsoft-jdk-21.0.12-linux-x64.tar.gz`, archive SHA-256 `f2a84ad31ebeaf3a26252dd86a4a8e1b74aefb6bfc8e55fd20190110d1353c0f` |
| Maven | Apache Maven `3.9.16`, build commit `2bdd9fddda4b155ebf8000e807eb73fd829a51d5`; executable/distribution hash recorded; version drift ABORTS |

Workflow permissions are `contents: read`; no write, package, release or OIDC
permission is allowed. Network access is used only for pinned tool/artifact and
database retrieval. Download failure or identity mismatch is `ABORTED`.

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

## Pinned scanners and generators

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

## Canonical configuration and exact invocations

The ASCII LF-terminated file
[`ga-security-toolchain-v1.properties`](ga-security-toolchain-v1.properties) is
the canonical option inventory. Its amended SHA-256 is:

```text
6abe66f22ac58b29a45287cf99402045f04b6e2d37fcdb1d144eef215b649397
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
  "6abe66f22ac58b29a45287cf99402045f04b6e2d37fcdb1d144eef215b649397"

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

mvn -B -ntp -f core/pom.xml \
  org.owasp:dependency-check-maven:13.0.0:check \
  -DautoUpdate=true \
  -DdataDirectory="${GA_CACHE_DIR}/dependency-check-data" \
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
or suppression file is accepted; the pinned image's embedded default rule set
is the exact configuration. Dependency-Check runs with no suppression file.

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

## Vulnerability policy

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

## Secret policy

Gitleaks scans the complete reachable history (`--all`) and current working
tree. A verified credential/token/private key is B0. A finding may be marked
false positive only by Human approval with fingerprint and non-secret rationale;
the original finding remains preserved. Redaction in committed reports must not
remove fingerprint/path/commit evidence needed for audit.

## License policy

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

## Update rule

Any version, coordinate, action SHA, OCI digest, database freshness rule,
severity threshold, allowed-license set or suppression policy change requires
Sol High review and Human Blueprint/Exception approval before new evidence can
qualify. Existing results stay bound to this exact manifest.
