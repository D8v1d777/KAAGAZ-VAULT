#!/usr/bin/env python3
"""Pull the real setup screen captured by Android instrumentation.

No UI is drawn or synthesized here. The instrumented test confirms the production
activity is in SetupRequired state, briefly disables FLAG_SECURE only for that
non-sensitive state in a disposable CI emulator, captures the actual display via
UiAutomation, verifies visible pixel diversity, and immediately restores FLAG_SECURE.
This script only retrieves that PNG and writes provenance metadata. It never reads,
submits, or captures a recovery key.
"""
from __future__ import annotations

import base64
import binascii
import hashlib
import json
import os
import subprocess
import sys
from pathlib import Path

PACKAGE = "com.kaagazvault"
REMOTE = "files/kaagaz-vault-ui/setup-required-android36.png"
OUT = Path("docs/images/ui")
LOCAL = OUT / "setup-required-android36.png"
PNG_SIGNATURE = b"\x89PNG\r\n\x1a\n"


def pull_request_head_sha() -> str:
    explicit = os.environ.get("GITHUB_PR_HEAD_SHA", "").strip()
    if explicit:
        return explicit
    event_path = os.environ.get("GITHUB_EVENT_PATH")
    if event_path:
        try:
            event = json.loads(Path(event_path).read_text(encoding="utf-8"))
            head_sha = event.get("pull_request", {}).get("head", {}).get("sha")
            if isinstance(head_sha, str) and head_sha.strip():
                return head_sha.strip()
        except (OSError, json.JSONDecodeError):
            pass
    return "unknown"


def main() -> int:
    OUT.mkdir(parents=True, exist_ok=True)
    metadata = {
        "purpose": "Direct Android UiAutomation screenshot of the installed production MainActivity in SetupRequired state; not generated artwork",
        "source_sha": pull_request_head_sha(),
        "workflow_sha": os.environ.get("GITHUB_SHA", "unknown"),
        "workflow_run_id": os.environ.get("GITHUB_RUN_ID", "unknown"),
        "android_api": 36,
        "device": "GitHub Actions Android emulator, x86_64, google_apis",
        "capture_method": "UiAutomation.takeScreenshot after asserting VaultAccessUiState.SetupRequired; FLAG_SECURE restored before bitmap is persisted",
        "screenshots": [],
        "notes": [],
        "privacy": "Only SetupRequired state is captured. The test must assert this state before temporarily clearing FLAG_SECURE; it does not tap setup or handle recovery-key material.",
    }
    if metadata["source_sha"] == "unknown":
        metadata["notes"].append("SCREENSHOT_REJECTED: pull-request source SHA is unavailable; provenance cannot be trusted")
        return write_metadata(metadata, exit_code=1)
    try:
        # On the disposable workflow emulator the instrumentation test already installs and
        # launches the app. Do not clear package data here: that can fail after instrumentation
        # teardown and previously prevented any capture attempt.
        # Transfer base64 text instead of raw PNG bytes. Some adb/host shell combinations
        # have altered binary exec-out streams; ASCII transport makes corruption detectable.
        result = subprocess.run(
            ["adb", "exec-out", "run-as", PACKAGE, "base64", REMOTE],
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            timeout=30,
            check=False,
        )
        if result.returncode != 0:
            detail = result.stderr.decode("utf-8", errors="replace").strip()
            metadata["notes"].append(
                "SCREENSHOT_PULL_FAILED: "
                + (detail or f"adb exec-out exit {result.returncode}")[:500]
            )
            return write_metadata(metadata, exit_code=1)

        encoded = result.stdout.strip()
        if not encoded:
            metadata["notes"].append("SCREENSHOT_PULL_FAILED: app returned an empty screenshot file")
            return write_metadata(metadata, exit_code=1)
        try:
            data = base64.b64decode(encoded, validate=True)
        except (binascii.Error, ValueError) as exc:
            metadata["notes"].append(
                f"SCREENSHOT_PULL_FAILED: base64 transport was corrupted ({type(exc).__name__})"
            )
            return write_metadata(metadata, exit_code=1)
        if not data:
            metadata["notes"].append("SCREENSHOT_PULL_FAILED: decoded screenshot was empty")
            return write_metadata(metadata, exit_code=1)
        LOCAL.write_bytes(data)
        if len(data) < 4096 or not data.startswith(PNG_SIGNATURE):
            LOCAL.unlink(missing_ok=True)
            metadata["notes"].append("SCREENSHOT_REJECTED: invalid PNG signature or unexpectedly small image")
            return write_metadata(metadata, exit_code=1)

        metadata["screenshots"].append(LOCAL.as_posix())
        metadata["sha256"] = hashlib.sha256(data).hexdigest()
        metadata["bytes"] = len(data)
        metadata["notes"].append("PNG pixel variance was asserted inside Android instrumentation before it was written.")
        return write_metadata(metadata)
    except Exception as exc:
        metadata["notes"].append(f"CAPTURE_RETRIEVAL_STOPPED:{type(exc).__name__}:{str(exc)[:250]}")
        print(metadata["notes"][-1])
        return write_metadata(metadata, exit_code=1)


def write_metadata(metadata: dict, exit_code: int = 0) -> int:
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / "capture-metadata.json").write_text(
        json.dumps(metadata, indent=2) + "\n", encoding="utf-8"
    )
    print("UI_CAPTURE_SUMMARY:" + json.dumps(metadata, separators=(",", ":")))
    return exit_code


if __name__ == "__main__":
    sys.exit(main())
