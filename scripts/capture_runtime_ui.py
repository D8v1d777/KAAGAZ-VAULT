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
    # The XML may contain visible sensitive text. Parse it only in memory, then remove
    # both device-side and host-side copies before returning any extracted UI labels.
    try:
        adb("shell", "uiautomator", "dump", "/sdcard/kaagaz-window.xml", timeout=20, check=False)
        adb("pull", "/sdcard/kaagaz-window.xml", str(TMP), timeout=20)
        root = ET.parse(TMP).getroot()
        def redact(value: str) -> str:
            value = value.strip()
            return "[redacted sensitive text]" if KEY_RE.fullmatch(value) else value

        return [
            {
                "text": redact(node.attrib.get("text", "")),
                "description": redact(node.attrib.get("content-desc", "")),
                "bounds": node.attrib.get("bounds", ""),
            }
            for node in root.iter("node")
        ]
    except (RuntimeError, ET.ParseError, OSError):
        return []
    finally:
        try:
            adb("shell", "rm", "-f", "/sdcard/kaagaz-window.xml", check=False)
        except Exception:
            pass
        try:
            TMP.unlink(missing_ok=True)
        except OSError:
            pass


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
        "purpose": "Direct Android emulator screenshots from the installed application, not generated mockups",
        "source_sha": os.environ.get("GITHUB_PR_HEAD_SHA", os.environ.get("GITHUB_SHA", "unknown")),
        "workflow_sha": os.environ.get("GITHUB_SHA", "unknown"),
        "workflow_run_id": os.environ.get("GITHUB_RUN_ID", "unknown"),
        "android_api": 36,
        "device": "GitHub Actions Android emulator, x86_64, google_apis",
        "screenshots": screenshots,
        "notes": notes,
        "privacy": "Never reads, copies, submits, or captures a recovery key. Sensitive visible text is redacted in parsed hierarchy; temporary XML is removed after every dump.",
    }
    try:
        adb("shell", "pm", "clear", PACKAGE, timeout=30)
        adb("shell", "monkey", "-p", PACKAGE, "1", timeout=20)
        setup_nodes = wait_nodes(
            lambda ns: any(n["text"].strip() == "Set up secure vault" for n in ns),
            seconds=35,
        )
        if setup_nodes is None:
            notes.append("SETUP_REQUIRED_SCREEN_NOT_REACHED; no setup screenshot saved")
            return write_metadata(metadata)

        save_screenshot("setup-required-android36.png")
        if not tap_text(setup_nodes, "Set up secure vault"):
            notes.append("SETUP_BUTTON_NOT_TAPPABLE")
            return write_metadata(metadata)

        # Capture only the actual authentication prompt. Do not authenticate on behalf of a
        # user and do not proceed to the screen that displays a recovery secret.
        recovery_labels = {"Re-enter the full recovery key", "Confirm saved recovery key"}
        auth_nodes = wait_nodes(
            lambda ns: (
                any(
                    marker in (node["text"] + " " + node["description"]).lower()
                    for node in ns
                    for marker in (
                        "set up kaagaz vault",
                        "use pin",
                        "use screen lock",
                        "use device credential",
                        "use password",
                        "confirm your screen lock",
                        "enter your pin",
                    )
                )
                and not any(
                    node["text"].strip() in recovery_labels
                    for node in ns
                )
            ),
            seconds=12,
        )
        if auth_nodes is not None:
            save_screenshot("setup-authentication-prompt-android36.png")
            adb("shell", "input", "keyevent", "4", check=False)
            notes.append("Authentication prompt captured; setup deliberately left incomplete to avoid handling recovery secret.")
        else:
            current_nodes = hierarchy()
            if any(node["text"].strip() in recovery_labels for node in current_nodes):
                notes.append("RECOVERY_CONFIRMATION_SCREEN_REACHED_BUT_NOT_CAPTURED; no recovery key read or submitted")
            else:
                notes.append("AUTHENTICATION_PROMPT_NOT_REACHED; no prompt screenshot saved")
        return write_metadata(metadata)
    except Exception as exc:
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
    return 0


if __name__ == "__main__":
    sys.exit(main())
