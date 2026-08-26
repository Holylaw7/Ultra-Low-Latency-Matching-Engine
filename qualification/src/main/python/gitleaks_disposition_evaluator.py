#!/usr/bin/env python3
"""Fail-closed evaluation of the approved Gitleaks disposition contract."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import subprocess
import sys
from dataclasses import dataclass
from pathlib import Path


SCHEMA = "ga-gitleaks-false-positive-disposition-v1"
SCANNER = "gitleaks-v8.30.1"
CLASSIFICATION = "DEMONSTRABLE_NON_SECRET"
RATIONALE_DIGESTS = {
    "DOCUMENTED_ENVIRONMENT_VARIABLE_NAME":
        "2b315134c51be0c7e9b2b5587292dc6fb837377efdf5b0030e085a5d04e67807",
    "PROPERTIES_SCHEMA_PROSE":
        "d4f046784686bb55e51b344b0d71ce8288b411e3d722102d19db0f5cf7330ea8",
}
HEX40 = re.compile(r"^[0-9a-f]{40}$")
HEX64 = re.compile(r"^[0-9a-f]{64}$")
INDEXED_KEY = re.compile(r"^disposition\.(\d{4})\.([a-z][A-Za-z0-9]*)$")
SCHEMA_KEY = "schema.version"
COMMON_FIELDS = {
    "blobSha", "classification", "commitSha", "endLine", "findingFingerprint",
    "path", "rationaleCode", "rationaleDigestSha256", "ruleId", "scanner", "scope",
    "startLine",
}


@dataclass(frozen=True)
class Disposition:
    index: int
    scope: str
    scanner: str
    rule_id: str
    path: str
    commit_sha: str
    candidate_production_sha: str | None
    blob_sha: str
    start_line: int
    end_line: int
    fingerprint: str
    classification: str
    rationale_code: str
    rationale_digest: str


def reject(message: str) -> None:
    raise ValueError(message)


def read_ascii_lf(path: Path) -> tuple[bytes, str]:
    data = path.read_bytes()
    if not data or data[-1:] != b"\n" or b"\r" in data:
        reject("disposition manifest must be non-empty LF text")
    try:
        text = data.decode("ascii")
    except UnicodeDecodeError as error:
        raise ValueError("disposition manifest must be ASCII") from error
    if text.encode("ascii") != data:
        reject("disposition manifest must be canonical ASCII")
    return data, text


def parse_manifest(path: Path) -> tuple[bytes, list[Disposition]]:
    data, text = read_ascii_lf(path)
    values: dict[str, str] = {}
    previous = ""
    for number, line in enumerate(text.split("\n")[:-1], start=1):
        if not line or line.count("=") != 1:
            reject(f"malformed disposition line {number}")
        key, value = line.split("=", 1)
        if not key or not value or key <= previous:
            reject(f"non-canonical disposition key order at line {number}")
        previous = key
        if key != SCHEMA_KEY and not INDEXED_KEY.fullmatch(key):
            reject(f"unknown disposition key at line {number}")
        if key in values:
            reject(f"duplicate disposition key at line {number}")
        values[key] = value

    if values.pop(SCHEMA_KEY, None) != SCHEMA:
        reject("disposition schema is not approved")
    grouped: dict[int, dict[str, str]] = {}
    for key, value in values.items():
        match = INDEXED_KEY.fullmatch(key)
        if match is None:
            reject("missing disposition schema")
        index = int(match.group(1))
        grouped.setdefault(index, {})[match.group(2)] = value
    expected_indexes = list(range(1, len(grouped) + 1))
    if sorted(grouped) != expected_indexes:
        reject("disposition indexes must be contiguous")

    parsed: list[Disposition] = []
    for index in expected_indexes:
        entry = grouped[index]
        scope = entry.get("scope", "")
        expected_fields = COMMON_FIELDS | ({"candidateProductionSha"} if scope == "CANDIDATE" else set())
        if set(entry) != expected_fields or scope not in {"HISTORY", "CANDIDATE"}:
            reject(f"disposition {index} has an invalid field set")
        scanner = entry["scanner"]
        rule_id = entry["ruleId"]
        path_value = entry["path"]
        commit_sha = entry["commitSha"]
        candidate_sha = entry.get("candidateProductionSha")
        blob_sha = entry["blobSha"]
        start_line = parse_line(entry["startLine"], index)
        end_line = parse_line(entry["endLine"], index)
        fingerprint = entry["findingFingerprint"]
        rationale_code = entry["rationaleCode"]
        rationale_digest = entry["rationaleDigestSha256"]
        if scanner != SCANNER or not rule_id or not canonical_path(path_value):
            reject(f"disposition {index} has invalid scanner/rule/path")
        if not HEX40.fullmatch(blob_sha):
            reject(f"disposition {index} blob identity is not a full Git object id")
        if end_line < start_line:
            reject(f"disposition {index} line range is reversed")
        if rationale_code not in RATIONALE_DIGESTS:
            reject(f"disposition {index} rationale is not approved")
        if rationale_digest != RATIONALE_DIGESTS[rationale_code] or not HEX64.fullmatch(rationale_digest):
            reject(f"disposition {index} rationale digest is invalid")
        expected_fingerprint = f"{commit_sha}:{path_value}:{rule_id}:{start_line}"
        if scope == "HISTORY":
            if not HEX40.fullmatch(commit_sha) or fingerprint != expected_fingerprint:
                reject(f"disposition {index} history identity is invalid")
        else:
            if commit_sha != "NONE" or not HEX40.fullmatch(candidate_sha or ""):
                reject(f"disposition {index} candidate identity is invalid")
            expected_fingerprint = f"{path_value}:{rule_id}:{start_line}"
            if fingerprint != expected_fingerprint:
                reject(f"disposition {index} candidate fingerprint is invalid")
        if entry["classification"] != CLASSIFICATION:
            reject(f"disposition {index} classification is not demonstrably non-secret")
        parsed.append(Disposition(
            index=index,
            scope=scope,
            scanner=scanner,
            rule_id=rule_id,
            path=path_value,
            commit_sha=commit_sha,
            candidate_production_sha=candidate_sha,
            blob_sha=blob_sha,
            start_line=start_line,
            end_line=end_line,
            fingerprint=fingerprint,
            classification=entry["classification"],
            rationale_code=rationale_code,
            rationale_digest=rationale_digest,
        ))
    if not parsed:
        reject("disposition manifest has no entries")
    if len({(item.scope, item.fingerprint) for item in parsed}) != len(parsed):
        reject("disposition manifest contains duplicate fingerprints")
    return data, parsed


def parse_line(value: str, index: int) -> int:
    if not value.isdigit() or int(value) <= 0:
        reject(f"disposition {index} line is invalid")
    return int(value)


def canonical_path(value: str) -> bool:
    return bool(value) and not value.startswith(("/", "\\")) and "\\" not in value \
        and value not in {".", ".."} and "/../" not in f"/{value}/" \
        and not value.endswith("/..")


def load_report(path: Path) -> list[dict]:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeError, json.JSONDecodeError) as error:
        raise ValueError("Gitleaks report is missing or malformed") from error
    if not isinstance(value, list) or any(not isinstance(item, dict) for item in value):
        reject("Gitleaks report must be an array of finding objects")
    return value


def git_blob(repository: Path, revision: str, path: str) -> str:
    completed = subprocess.run(
        ["git", "-C", str(repository), "rev-parse", f"{revision}:{path}"],
        check=False, capture_output=True, text=True,
    )
    if completed.returncode != 0:
        reject("finding path/blob identity cannot be resolved")
    value = completed.stdout.strip()
    if not HEX40.fullmatch(value):
        reject("resolved finding blob is not a full Git object id")
    return value


def finding_value(finding: dict, key: str):
    if key not in finding:
        reject(f"Gitleaks finding is missing {key}")
    return finding[key]


def check_finding(
        finding: dict,
        scope: str,
        dispositions: dict[tuple[str, str], Disposition],
        repository: Path,
        candidate: Path,
        candidate_production_sha: str,
) -> Disposition:
    rule_id = finding_value(finding, "RuleID")
    path = finding_value(finding, "File")
    commit = finding_value(finding, "Commit") or ""
    start_line = finding_value(finding, "StartLine")
    end_line = finding_value(finding, "EndLine")
    fingerprint = finding_value(finding, "Fingerprint")
    if not isinstance(rule_id, str) or not isinstance(path, str) or not canonical_path(path):
        reject(f"{scope} finding has non-canonical metadata")
    if not isinstance(commit, str) or not isinstance(start_line, int) or not isinstance(end_line, int):
        reject(f"{scope} finding has invalid identity metadata")
    if not isinstance(fingerprint, str) or not fingerprint:
        reject(f"{scope} finding has no fingerprint")
    key = (scope, fingerprint)
    disposition = dispositions.get(key)
    if disposition is None:
        reject(f"unapproved {scope} Gitleaks finding count is non-zero")
    if disposition.rule_id != rule_id or disposition.path != path \
            or disposition.start_line != start_line or disposition.end_line != end_line \
            or disposition.fingerprint != fingerprint:
        reject(f"{scope} disposition metadata does not match exactly")
    if scope == "HISTORY":
        if commit != disposition.commit_sha:
            reject("history commit identity does not match disposition")
        blob = git_blob(repository, commit, path)
    else:
        if commit:
            reject("candidate-bound finding unexpectedly contains a commit")
        if disposition.candidate_production_sha != candidate_production_sha:
            reject("candidate production identity does not match disposition")
        blob = git_blob(candidate, "HEAD", path)
    if blob != disposition.blob_sha:
        reject(f"{scope} blob identity does not match disposition")
    return disposition


def validate_exit(status: int, count: int, scope: str) -> None:
    if status not in {0, 1}:
        reject(f"{scope} Gitleaks scanner exited with an infrastructure error")
    if (count == 0 and status != 0) or (count > 0 and status != 1):
        reject(f"{scope} Gitleaks exit code does not match report findings")


def evaluate(args: argparse.Namespace) -> None:
    manifest_bytes, entries = parse_manifest(Path(args.manifest))
    candidate_production_sha = args.candidate_production_sha
    if not HEX40.fullmatch(candidate_production_sha):
        reject("candidate production identity is not a full Git object id")
    history = load_report(Path(args.history_report))
    candidate = load_report(Path(args.candidate_report))
    dispositions = {(item.scope, item.fingerprint): item for item in entries}
    seen: set[tuple[str, str]] = set()
    for finding in history:
        item = check_finding(
            finding, "HISTORY", dispositions, Path(args.repository),
            Path(args.candidate_repository), candidate_production_sha,
        )
        seen.add((item.scope, item.fingerprint))
    for finding in candidate:
        item = check_finding(
            finding, "CANDIDATE", dispositions, Path(args.repository),
            Path(args.candidate_repository), candidate_production_sha,
        )
        seen.add((item.scope, item.fingerprint))
    expected = {(item.scope, item.fingerprint) for item in entries}
    if not seen.issubset(expected):
        reject("an observed finding is not in the approved disposition set")
    validate_exit(int(args.history_exit), len(history), "history")
    validate_exit(int(args.candidate_exit), len(candidate), "candidate")
    output = Path(args.output)
    output.write_text(
        "".join((
            "schema.version=ga-gitleaks-disposition-evaluation-v1\n",
            "outcome=PASS\n",
            f"history.findings={len(history)}\n",
            f"history.approved={len(history)}\n",
            f"history.exitCode={args.history_exit}\n",
            f"candidate.findings={len(candidate)}\n",
            f"candidate.approved={len(candidate)}\n",
            f"candidate.exitCode={args.candidate_exit}\n",
            f"disposition.manifestSha256={hashlib.sha256(manifest_bytes).hexdigest()}\n",
            f"disposition.unused={len(expected - seen)}\n",
            "candidateBoundScan.executed=true\n",
            "unapprovedFindings=0\n",
        )),
        encoding="ascii",
        newline="\n",
    )


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("history_report")
    parser.add_argument("candidate_report")
    parser.add_argument("manifest")
    parser.add_argument("repository")
    parser.add_argument("candidate_repository")
    parser.add_argument("candidate_production_sha")
    parser.add_argument("history_exit", type=int)
    parser.add_argument("candidate_exit", type=int)
    parser.add_argument("output")
    args = parser.parse_args()
    try:
        evaluate(args)
    except (OSError, ValueError) as error:
        print(f"Gitleaks disposition evaluation failed: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
