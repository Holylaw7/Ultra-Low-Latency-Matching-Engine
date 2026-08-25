# GA Evidence Schemas v1 — Normative Proposal

## Status

`Approved / Frozen by Human Phase 11 Blueprint Approval (2026-08-25)`. These
contracts are frozen by ADR-0019 D7 before qualification implementation.

## Canonical encoding shared by all schemas

| Rule | Normative value |
| --- | --- |
| bytes | UTF-8 restricted to canonical ASCII output; no BOM |
| record | one `key=value\n` field; final LF required; CR forbidden |
| ordering | keys sorted by unsigned ASCII byte order; duplicate keys forbidden |
| key | `[a-z][a-zA-Z0-9]*(\.[a-zA-Z0-9]+)*`, 1..128 ASCII bytes |
| value | UTF-8 percent encoded; only `A-Z a-z 0-9 - . _ ~` remain literal; uppercase `%HH`; space is `%20`; `+` forbidden |
| value bound | decoded 0..4096 bytes; encoded line including LF <=12290 bytes |
| document bound | <=1,048,576 bytes and <=4096 fields before allocation |
| integer | base-10, no sign/leading zero except `0`; signed fields explicitly named |
| decimal | non-negative plain decimal, no exponent, trailing zero removed |
| boolean | exactly `true` or `false` |
| enum | exact uppercase token declared below |
| duration | non-negative integer milliseconds |
| timestamp | UTC RFC3339 `Instant.toString()` form, ending `Z` |
| Git object ID | repository object format SHA-1; exactly 40 lowercase hexadecimal characters; full object ID only |
| SHA-256 | exactly 64 lowercase hexadecimal characters |
| relative path | forward slash, NFC, 1..240 decoded bytes; no drive, root, empty segment, `.` or `..` |
| unknown data | unknown schema version, field or enum is rejected |

Git object ID fields are explicitly typed and never use the SHA-256 rule:
`candidate.productionSha`, `candidate.tagObjectSha`, `controller.gitSha` and
`release.releaseSourceSha` require the full 40-character lowercase SHA-1 Git
object ID. Abbreviations, uppercase values, alternate object formats and
whitespace are rejected. Every field named `*Sha256`, every identity digest,
and every evidence/artifact digest continues to require exactly 64 lowercase
hexadecimal characters.

The document SHA-256 covers every canonical byte including the final LF. A
writer creates a unique same-directory `.tmp`, writes the complete document,
calls `FileChannel.force(true)`, reads back and strictly validates/hash-checks
it, then performs `ATOMIC_MOVE` without replacement. Unsupported atomic move,
existing final path, write/force/read-back/move failure or directory inventory
change fails the evidence unit. The writer deletes only its own un-published
temp file. Readers never repair.

## Artifact inventory and sidecars

Every payload artifact has adjacent `<name>.sha256` containing exactly:

```text
<64-lowercase-hex><two ASCII spaces><basename>\n
```

The basename obeys the relative-path rules and contains no separator. A run
also publishes `SHA256SUMS` whose entries use the same format, are sorted by
relative path and include all payload artifacts except `SHA256SUMS`, its own
sidecar and the run manifest. The run manifest contains
`artifact.inventory.path`, `.sha256` and `.size`. A parent Gate/campaign result
hashes the immutable manifest, preventing a self-reference cycle.

Artifact families use consecutive zero-padded identifiers:

```text
artifact.0001.path
artifact.0001.sha256
artifact.0001.size
...
```

Indices start at 0001 with no gap, maximum 1000. Paths are unique. Size is
0..1099511627776. All referenced files must stay under the manifest directory
after normalized resolution and must match size/hash before a manifest is
accepted.

## `ga-run-manifest-v1`

All fields below are required and lexical ordering is canonical. The code
blocks are field inventories, not presentation order; the wire order is always
the shared unsigned-ASCII lexical key order. The artifact family is the only
repeatable family.

```text
candidate.applicationJarSha256
candidate.productionSha
candidate.productionTreeSha256
candidate.tag
candidate.tagObjectSha
comparability.identitySha256
configuration.identitySha256
controller.gitSha
evidence.completedAtUtc
evidence.elapsedMillis
evidence.failureCode
evidence.failureDigestSha256
evidence.outcome
evidence.startedAtUtc
gate.id
gate.version
run.commandCount
run.id
run.profile
run.seed
runtime.cpuModel
runtime.filesystem
runtime.gcCollectors
runtime.heapMaxBytes
runtime.javaRuntimeVersion
runtime.javaVendor
runtime.javaVmArguments
runtime.javaVmName
runtime.javaVmVersion
runtime.logicalProcessors
runtime.nettyAllocator
runtime.osArch
runtime.osName
runtime.osVersion
runtime.storageIdentity
schema.version
workload.version
artifact.0001.path ... artifact.NNNN.size
artifact.inventory.path
artifact.inventory.sha256
artifact.inventory.size
```

Constraints:

- `schema.version=ga-run-manifest-v1`;
- `gate.id=G1` through `G12`; gate version is an approved lowercase token;
- run ID is UUID lowercase canonical form; profile/workload must be Matrix-
  approved; seed and counts are non-negative integers;
- outcome is `PASS`, `FAIL` or `ABORTED`; `PASS` requires
  `failureCode=NONE`; other outcomes require a stable code and non-empty digest;
- candidate values must equal the approved target before execution;
- `candidate.productionSha`, `candidate.tagObjectSha` and `controller.gitSha`
  are full 40-character lowercase Git SHA-1 object IDs;
- all fields ending in `Sha256` and all identity/statement digests are 64
  lowercase hexadecimal SHA-256 values;
- `configuration.identitySha256` hashes canonical qualification-critical
  fields only; run ID, time, PID, paths and measured results are excluded;
- `comparability.identitySha256` hashes JDK/JVM/GC/heap/OS/CPU/filesystem/
  storage/Netty allocator/JFR configuration fields required by that Gate.

## `ga-gate-result-v1`

Required fixed fields:

```text
blocker.classification
candidate.applicationJarSha256
candidate.productionSha
candidate.productionTreeSha256
candidate.tag
candidate.tagObjectSha
comparability.identitySha256
configuration.identitySha256
controller.gitSha
criterion.count
evidence.completedAtUtc
evidence.outcome
evidence.startedAtUtc
gate.id
gate.version
limitation.count
manifest.count
schema.version
```

Then exactly these consecutive families:

```text
criterion.0001.id
criterion.0001.actual
criterion.0001.operator
criterion.0001.required
criterion.0001.result
...
limitation.0001.code
limitation.0001.statementDigestSha256
...
manifest.0001.path
manifest.0001.sha256
...
```

`blocker.classification` is exactly `NONE`, `B0`, `B1`, `B2`, `B3` or `B4`.
`criterion.count` and `manifest.count` are 1..1000; `limitation.count` is
0..1000. Every count matches its family cardinality without gaps. Operators
are `EQ`, `NE`, `LT`, `LE`, `GT`, `GE`, `EXACT` or `ZERO`. Criterion result is
`PASS`/`FAIL`; Gate PASS requires every criterion PASS, all manifests valid and
`blocker.classification=NONE`. A FAIL or ABORTED Gate records the applicable
highest-priority blocker class; evidence-contract/integrity failure is B0 and
an unapproved comparability-environment mismatch is B3.

`configuration.identitySha256` and `comparability.identitySha256` are required
64-character lowercase SHA-256 values recomputed from the Gate's approved
canonical definition and environment policy. They are never inferred from the
candidate tag alone. Manifest paths/hashes bind immutable run or campaign
manifests.

## `ga-campaign-summary-v1`

Required fixed fields:

```text
campaign.completedAtUtc
campaign.configurationIdentityEqual
campaign.id
campaign.outcome
campaign.requiredRunCount
campaign.startedAtUtc
campaign.validRunCount
candidate.applicationJarSha256
candidate.productionSha
candidate.tag
candidate.tagObjectSha
comparability.policy
controller.gitSha
gate.id
run.count
schema.version
```

Run family, consecutive from 0001:

```text
run.0001.comparabilityIdentitySha256
run.0001.configurationIdentitySha256
run.0001.id
run.0001.manifestPath
run.0001.manifestSha256
run.0001.outcome
...
```

`schema.version=ga-campaign-summary-v1`; candidate production/tag object and
controller values are full 40-character lowercase Git SHA-1 object IDs; run
count is 1..100; summaries
reference manifests and never copy measured evidence. Required identities and
outcomes are recomputed from referenced manifests. PASS requires exact required
count, no FAIL/ABORTED member, configuration identity equality and the approved
comparability policy.

## `ga-release-manifest-v1`

Required fixed fields:

```text
artifact.applicationJarPath
artifact.applicationJarSha256
artifact.sbomPath
artifact.sbomSha256
artifact.sha256sumsPath
artifact.sha256sumsSha256
candidate.productionSha
candidate.productionTreeSha256
candidate.tag
candidate.tagObjectSha
evidence.gateCount
release.channel
release.knownLimitationCount
release.product
release.releaseSourceSha
release.version
schema.version
```

Consecutive families:

```text
evidence.gate.01.id
evidence.gate.01.path
evidence.gate.01.sha256
...
release.knownLimitation.0001.code
release.knownLimitation.0001.statementDigestSha256
...
```

Gate count is exactly 12 with IDs G1..G12. Candidate production/release-source
and tag-object fields are full 40-character lowercase Git SHA-1 object IDs;
release version is exactly `1.0.0`
for this proposal and channel is `GITHUB_BINARY`. The manifest is a draft until
separate Human GA/tag authority is recorded externally; it contains no
self-approval field.

## Validation order

All readers validate before semantic allocation: file-size/field-count bounds,
UTF-8/ASCII canonical form, line/key/value syntax, ordering/duplicates, schema
version, exact field set/families, scalar ranges/enums, path containment,
artifact size/hash, identity recomputation and finally Gate/campaign/release
semantics. Any failure rejects the complete evidence unit.
