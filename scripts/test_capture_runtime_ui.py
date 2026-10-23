"""Regression tests for strict runtime UI screenshot evidence capture."""
import importlib.util
import json
import os
import subprocess
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

MODULE_PATH = Path(__file__).with_name("capture_runtime_ui.py")
SPEC = importlib.util.spec_from_file_location("capture_runtime_ui", MODULE_PATH)
CAPTURE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(CAPTURE)


class CaptureRuntimeUiFailureTests(unittest.TestCase):
    def test_source_sha_comes_from_pull_request_event_not_merge_sha(self):
        with tempfile.NamedTemporaryFile("w", encoding="utf-8") as event_file:
            json.dump({"pull_request": {"head": {"sha": "expected-pr-head-sha"}}}, event_file)
            event_file.flush()
            with patch.dict(os.environ, {"GITHUB_EVENT_PATH": event_file.name}, clear=True):
                self.assertEqual(CAPTURE.pull_request_head_sha(), "expected-pr-head-sha")

    def run_in_temp_dir(self, fake_run):
        temp = tempfile.TemporaryDirectory()
        self.addCleanup(temp.cleanup)
        root = Path(temp.name) / "ui"
        with patch.object(CAPTURE, "OUT", root), patch.object(
            CAPTURE, "LOCAL", root / "setup-required-android36.png"
        ), patch.object(CAPTURE.subprocess, "run", side_effect=fake_run):
            code = CAPTURE.main()
        metadata = json.loads((root / "capture-metadata.json").read_text(encoding="utf-8"))
        return code, metadata

    def test_adb_pull_failure_is_nonzero(self):
        def fail_pull(*args, **kwargs):
            return subprocess.CompletedProcess(args[0], 1, "", "device file missing")

        code, metadata = self.run_in_temp_dir(fail_pull)
        self.assertNotEqual(code, 0)
        self.assertTrue(any(note.startswith("SCREENSHOT_PULL_FAILED:") for note in metadata["notes"]))
        self.assertEqual(metadata["screenshots"], [])

    def test_invalid_png_is_nonzero(self):
        def write_invalid_png(*args, **kwargs):
            Path(args[0][-1]).write_bytes(b"not a PNG")
            return subprocess.CompletedProcess(args[0], 0, "", "")

        code, metadata = self.run_in_temp_dir(write_invalid_png)
        self.assertNotEqual(code, 0)
        self.assertIn("SCREENSHOT_REJECTED: invalid PNG signature or unexpectedly small image", metadata["notes"])
        self.assertEqual(metadata["screenshots"], [])

    def test_unexpected_capture_error_is_nonzero(self):
        def raise_error(*args, **kwargs):
            raise RuntimeError("adb unavailable")

        code, metadata = self.run_in_temp_dir(raise_error)
        self.assertNotEqual(code, 0)
        self.assertTrue(any(note.startswith("CAPTURE_RETRIEVAL_STOPPED:RuntimeError:") for note in metadata["notes"]))
        self.assertEqual(metadata["screenshots"], [])


if __name__ == "__main__":
    unittest.main()
