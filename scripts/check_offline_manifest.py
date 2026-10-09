#!/usr/bin/env python3
"""Fail CI if the merged debug manifest violates the FOSS offline policy."""

from pathlib import Path
import sys
import xml.etree.ElementTree as ET

ANDROID_NS = "{http://schemas.android.com/apk/res/android}"
INTERNET_PERMISSION = "android.permission.INTERNET"


def main() -> int:
    intermediates = Path("app/build/intermediates")
    if not intermediates.exists():
        print("ERROR: Android build intermediates are missing; run assembleDebug first.", file=sys.stderr)
        return 1

    candidates = []
    for path in intermediates.rglob("AndroidManifest.xml"):
        normalized = path.as_posix().lower()
        if "/debug/" in normalized and (
            "merged_manifest" in normalized
            or "merged_manifests" in normalized
            or "processdebug" in normalized
        ):
            candidates.append(path)

    if not candidates:
        print("ERROR: merged debug manifest was not found; refusing to skip the offline gate.", file=sys.stderr)
        return 1

    failures = []
    for path in sorted(candidates):
        try:
            manifest = ET.parse(path).getroot()
        except (ET.ParseError, OSError) as error:
            failures.append(f"{path}: could not parse merged manifest ({error})")
            continue

        for element in manifest.iter():
            if element.tag in {"uses-permission", "uses-permission-sdk-23", "uses-permission-sdk-m"}:
                if element.attrib.get(f"{ANDROID_NS}name") == INTERNET_PERMISSION:
                    failures.append(f"{path}: INTERNET permission is present")

        application = manifest.find("application")
        if application is not None:
            cleartext = application.attrib.get(f"{ANDROID_NS}usesCleartextTraffic", "").lower()
            if cleartext == "true":
                failures.append(f"{path}: cleartext traffic is explicitly enabled")

    if failures:
        print("FAIL: offline manifest policy violated:", file=sys.stderr)
        for failure in failures:
            print(f" - {failure}", file=sys.stderr)
        return 1

    print(f"PASS: checked {len(candidates)} merged debug manifest(s); no INTERNET permission or enabled cleartext traffic found.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
