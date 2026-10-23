#!/usr/bin/env python3
"""Capture real KAAGAZ-VAULT Android emulator UI without storing recovery secrets.

Runs inside the Android CI emulator job. Screenshots are direct adb screencaps of the
installed application (not mockups). The recovery-key confirmation screen is never
captured; the randomly generated recovery key is used only in process memory and in
transient adb input to complete first-run setup.
"""
from __future__ import annotations

import json
import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from pathlib import Path

PACKAGE = "com.kaagazvault"
PIN = "2468"  # Created and removed by the surrounding disposable CI emulator script.
OUT = Path("docs/images/ui")
TMP = Path("/tmp/kaagaz-ui-capture-window.xml")
KEY_RE = re.compile(r"^[0-9a-f]{64}$")
PNG_SIGNATURE = b"\x89PNG\r\n\x1a\n"
screenshots: list[str] = []
notes: list[str] = []


def adb(*args: str, timeout: int = 30, check: bool = True) -> bytes:
    result = subprocess.run(
        ["adb", *args], stdout=subprocess.PIPE, stderr=subprocess.PIPE,
        timeout=timeout, check=False,
    )
    if check and result.returncode != 0:
        raise RuntimeError(
            f"adb {' '.join(args[:3])} failed ({result.returncode}): "
            + result.stderr.decode("utf-8", "replace")[-500:]
        )
    return result.stdout


def hierarchy() -> list[dict[str, str]]:
    adb("shell", "uiautomator", "dump", "/sdcard/kaagaz-window.xml", timeout=20, check=False)
    adb("pull", "/sdcard/kaagaz-window.xml", str(TMP), timeout=20)
    try:
        root = ET.parse(TMP).getroot()
    except (ET.ParseError, OSError):
        return []
    return [
        {
            "text": node.attrib.get("text", ""),
            "description": node.attrib.get("content-desc", ""),
            "bounds": node.attrib.get("bounds", ""),
        }
        for node in root.iter("node")
    ]


def wait_nodes(predicate, seconds: int = 25) -> list[dict[str, str]] | None:
    deadline = time.monotonic() + seconds
    while time.monotonic() < deadline:
        nodes = hierarchy()
        if predicate(nodes):
            return nodes
        time.sleep(0.6)
    return None


def tap_node(node: dict[str, str]) -> None:
    match = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node["bounds"])
    if not match:
        raise RuntimeError("UI node has no valid tap bounds")
    x1, y1, x2, y2 = map(int, match.groups())
    adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
    time.sleep(0.8)


def tap_text(nodes: list[dict[str, str]], *labels: str) -> bool:
    for label in labels:
        for node in nodes:
            if node["text"].strip() == label or node["description"].strip() == label:
                tap_node(node)
                return True
    return False


def save_screenshot(filename: str) -> bool:
    image = adb("exec-out", "screencap", "-p", timeout=20)
    if not image.startswith(PNG_SIGNATURE) or len(image) < 4096:
        notes.append(f"SCREENSHOT_REJECTED:{filename}:invalid PNG or too small")
        return False
    OUT.mkdir(parents=True, exist_ok=True)
    path = OUT / filename
    path.write_bytes(image)
    screenshots.append(path.as_posix())
    print(f"CAPTURED:{path.as_posix()} bytes={len(image)}")
    return True


def main() -> int:
    OUT.mkdir(parents=True, exist_ok=True)
    metadata = {
        "purpose": "Direct Android emulator screenshots from the installed PR source, never generated mockups",
        "source_sha": os.environ.get("GITHUB_SHA", "unknown"),
        "workflow_run_id": os.environ.get("GITHUB_RUN_ID", "unknown"),
        "android_api": 36,
        "device": "GitHub Actions Android emulator, x86_64, google_apis",
        "screenshots": screenshots,
        "notes": notes,
        "privacy": "Recovery-key confirmation screen is deliberately never captured; no documents or personal data are created.",
    }
    try:
        adb("shell", "pm", "clear", PACKAGE, timeout=30)
        adb("shell", "monkey", "-p", PACKAGE, "1", timeout=20)
        setup_nodes = wait_nodes(
            lambda ns: any(n["text"].strip() == "Set up secure vault" for n in ns),
            seconds=35,
        )
        if setup_nodes is None:
            notes.append("SETUP_REQUIRED_SCREEN_NOT_REACHED; no screenshot labelled as setup-required was saved")
            return write_metadata(metadata)

        save_screenshot("setup-required-android36.png")
        if not tap_text(setup_nodes, "Set up secure vault"):
            notes.append("SETUP_BUTTON_NOT_TAPPABLE")
            return write_metadata(metadata)

        # Drive the real system credential prompt only where the emulator exposes it.
        # Any unknown/error state ends capture rather than creating a substitute screen.
        key = None
        pin_sent = False
        start = time.monotonic()
        while time.monotonic() - start < 100:
            nodes = hierarchy()
            texts = [n["text"].strip() for n in nodes if n["text"].strip()]
            key = next((value for value in texts if KEY_RE.fullmatch(value)), None)
            if key:
                break

            if tap_text(
                nodes, "Use PIN", "Use screen lock", "Use device screen lock",
                "Use device credential", "Use password", "Use pattern",
            ):
                continue

            pin_prompt = any(
                marker in " ".join(texts).lower()
                for marker in ("enter pin", "confirm pin", "screen lock", "device pin", "enter your pin")
            )
            if pin_prompt and not pin_sent:
                adb("shell", "input", "text", PIN)
                adb("shell", "input", "keyevent", "66")
                pin_sent = True
                time.sleep(1.2)
                # Some Android credential screens expose a confirmation button.
                next_nodes = hierarchy()
                tap_text(next_nodes, "OK", "Confirm", "Continue", "Done")
                continue

            if any("setup failed" in value.lower() or "not available" in value.lower() for value in texts):
                break
            time.sleep(0.7)

        if not key:
            notes.append("FIRST_RUN_AUTH_OR_RECOVERY_FLOW_NOT_REACHED; no recovery screen captured and no key persisted")
            return write_metadata(metadata)

        # The recovery secret is a generated runtime secret. Use it transiently to complete the
        # real first-run screen; never save it in a file, screenshot, log, or repository.
        nodes = hierarchy()
        field_label = next(
            (node for node in nodes if node["text"].strip() == "Re-enter the full recovery key"),
            None,
        )
        if field_label is None:
            notes.append("RECOVERY_CONFIRMATION_FIELD_NOT_FOUND; key not captured")
            key = None
            return write_metadata(metadata)
        tap_node(field_label)
        adb("shell", "input", "text", key)
        nodes = wait_nodes(lambda ns: any(n["text"].strip() == "Confirm saved recovery key" for n in ns), 8) or hierarchy()
        if not tap_text(nodes, "Confirm saved recovery key"):
            notes.append("RECOVERY_CONFIRMATION_BUTTON_NOT_FOUND; home UI not captured")
            key = None
            return write_metadata(metadata)
        key = None
        nodes = wait_nodes(
            lambda ns: any("A calmer place" in n["text"] for n in ns)
            or any(n["text"].strip() == "YOUR DOCUMENTS" for n in ns),
            seconds=45,
        )
        if nodes is None:
            notes.append("AUTHENTICATED_HOME_NOT_REACHED_AFTER_REAL_SETUP")
            return write_metadata(metadata)
        time.sleep(1.5)
        save_screenshot("vault-home-empty-android36.png")

        # Process restart forces a fresh controller to discover the persisted key record;
        # the session key is not persisted and must not restore authenticated UI.
        adb("shell", "am", "force-stop", PACKAGE)
        adb("shell", "monkey", "-p", PACKAGE, "1", timeout=20)
        locked_nodes = wait_nodes(
            lambda ns: any(n["text"].strip() == "Unlock vault" for n in ns),
            seconds=35,
        )
        if locked_nodes is not None:
            time.sleep(0.8)
            save_screenshot("vault-locked-after-restart-android36.png")
        else:
            notes.append("RESTARTED_LOCKED_SCREEN_NOT_REACHED; no locked screenshot saved")
        return write_metadata(metadata)
    except Exception as exc:
        # The capture is supplemental evidence. Keep the CI security tests authoritative,
        # report exactly where capture stopped, and never substitute an illustration.
        notes.append(f"CAPTURE_STOPPED:{type(exc).__name__}:{str(exc)[:250]}")
        print(notes[-1])
        return write_metadata(metadata)


def write_metadata(metadata: dict) -> int:
    metadata["screenshots"] = list(screenshots)
    metadata["notes"] = list(notes)
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / "capture-metadata.json").write_text(
        json.dumps(metadata, indent=2) + "\n", encoding="utf-8"
    )
    print("UI_CAPTURE_SUMMARY:" + json.dumps(metadata, separators=(",", ":")))
    # Do not fail or misreport the security gate because an optional UI capture path isn't reached.
    return 0 if screenshots else 1


if __name__ == "__main__":
    sys.exit(main())
