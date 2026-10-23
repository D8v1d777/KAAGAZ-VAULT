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

import hashlib
import json
import os
import subprocess
import sys
from pathlib import Path

PACKAGE = "com.kaagazvault"
REMOTE = "/sdcard/Android/data/com.kaagazvault/files/kaagaz-vault-ui/setup-required-android36.png"
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
    try:
        # On the disposable workflow emulator the instrumentation test already installs and
        # launches the app. Do not clear package data here: that can fail after instrumentation
        # teardown and previously prevented any capture attempt.
        result = subprocess.run(
            ["adb", "pull", REMOTE, str(LOCAL)],
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
            timeout=30,
            check=False,
        )
        if result.returncode != 0:
            metadata["notes"].append(
                "SCREENSHOT_PULL_FAILED: "
                + (result.stderr.strip() or result.stdout.strip() or f"adb exit {result.returncode}")[:500]
            )
            return write_metadata(metadata, exit_code=1)

        data = LOCAL.read_bytes()
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
