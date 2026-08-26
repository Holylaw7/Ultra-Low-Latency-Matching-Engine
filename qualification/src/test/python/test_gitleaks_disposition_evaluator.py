"""Focused tests for the Gitleaks false-positive disposition contract."""

import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[4]
SCRIPT = ROOT / "qualification/src/main/python/gitleaks_disposition_evaluator.py"
MANIFEST = ROOT / "docs/release/ga-gitleaks-false-positive-dispositions-v1.properties"
HISTORY = ROOT / "qualification/src/test/resources/gitleaks/history-approved.json"
CANDIDATE = ROOT / "qualification/src/test/resources/gitleaks/working-tree-approved.json"
PRODUCTION_SHA = "e2828f563ee41316c062385c0244ac1336731359"


class GitleaksDispositionEvaluatorTest(unittest.TestCase):

    def run_evaluator(self, history=HISTORY, candidate=CANDIDATE, manifest=MANIFEST):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "evaluation.txt"
            return subprocess.run(
                [
                    sys.executable,
                    str(SCRIPT),
                    str(history),
                    str(candidate),
                    str(manifest),
                    str(ROOT),
                    str(ROOT),
                    PRODUCTION_SHA,
                    "1",
                    "1",
                    str(output),
                ],
                check=False,
                capture_output=True,
                text=True,
            )

    def test_exact_history_and_candidate_dispositions_pass(self):
        result = self.run_evaluator()
        self.assertEqual(0, result.returncode, result.stderr)

    def test_unapproved_finding_fails_closed(self):
        with tempfile.TemporaryDirectory() as directory:
            report = Path(directory) / "new.json"
            report.write_text(json.dumps([{
                "RuleID": "generic-api-key",
                "File": "README.md",
                "Commit": "",
                "StartLine": 1,
                "EndLine": 1,
                "Fingerprint": "README.md:generic-api-key:1",
            }]), encoding="utf-8")
            result = self.run_evaluator(candidate=report)
            self.assertNotEqual(0, result.returncode)
            self.assertIn("unapproved CANDIDATE", result.stderr)

    def test_absolute_candidate_path_fails_closed(self):
        with tempfile.TemporaryDirectory() as directory:
            report = Path(directory) / "absolute.json"
            finding = json.loads(CANDIDATE.read_text(encoding="utf-8"))[0]
            finding["File"] = "/repo/tasks/reports/PHASE-10-task-043.md"
            finding["Fingerprint"] = "/repo/tasks/reports/PHASE-10-task-043.md:generic-api-key:28"
            report.write_text(json.dumps([finding]), encoding="utf-8")
            result = self.run_evaluator(candidate=report)
            self.assertNotEqual(0, result.returncode)
            self.assertIn("non-canonical metadata", result.stderr)

    def test_parent_escape_candidate_path_fails_closed(self):
        with tempfile.TemporaryDirectory() as directory:
            report = Path(directory) / "escape.json"
            finding = json.loads(CANDIDATE.read_text(encoding="utf-8"))[0]
            finding["File"] = "../tasks/reports/PHASE-10-task-043.md"
            finding["Fingerprint"] = "../tasks/reports/PHASE-10-task-043.md:generic-api-key:28"
            report.write_text(json.dumps([finding]), encoding="utf-8")
            result = self.run_evaluator(candidate=report)
            self.assertNotEqual(0, result.returncode)
            self.assertIn("non-canonical metadata", result.stderr)

    def test_mutated_disposition_fails_closed(self):
        with tempfile.TemporaryDirectory() as directory:
            manifest = Path(directory) / "mutated.properties"
            text = MANIFEST.read_text(encoding="ascii")
            text = text.replace(
                "disposition.0002.startLine=28",
                "disposition.0002.startLine=29",
            )
            manifest.write_text(text, encoding="ascii", newline="\n")
            result = self.run_evaluator(manifest=manifest)
            self.assertNotEqual(0, result.returncode)
            self.assertIn("line range is reversed", result.stderr)


if __name__ == "__main__":
    unittest.main()
