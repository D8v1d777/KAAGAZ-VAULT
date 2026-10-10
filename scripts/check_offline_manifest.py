#!/usr/bin/env python3
"""Fail CI if merged manifests or packaged APKs violate the FOSS offline policy."""

import os
from pathlib import Path
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET

ANDROID_NS = "{http://schemas.android.com/apk/res/android}"
INTERNET_PERMISSION = "android.permission.INTERNET"
PERMISSION_TAGS = {
    "uses-permission",
    "uses-permission-sdk-23",
    "uses-permission-sdk-m",
}
VARIANTS = ("debug", "release")


def audit_manifest_root(root: ET.Element, source: str) -> list[str]:
    failures: list[str] = []
    for element in root.iter():
        if (
            element.tag in PERMISSION_TAGS
            and element.attrib.get(f"{ANDROID_NS}name") == INTERNET_PERMISSION
        ):
            failures.append(f"{source}: INTERNET permission is present")

    application = root.find("application")
    if application is not None:
        cleartext = application.attrib.get(
            f"{ANDROID_NS}usesCleartextTraffic", ""
        ).strip().lower()
        if cleartext == "true":
            failures.append(f"{source}: cleartext traffic is explicitly enabled")
    return failures


def audit_manifest_text(contents: str, source: str) -> list[str]:
    try:
        root = ET.fromstring(contents)
    except ET.ParseError as error:
        return [f"{source}: could not parse manifest ({error})"]
    return audit_manifest_root(root, source)


def audit_manifest_file(path: Path) -> list[str]:
    try:
        contents = path.read_text(encoding="utf-8")
    except OSError as error:
        return [f"{path}: could not read manifest ({error})"]
    return audit_manifest_text(contents, str(path))


def find_merged_manifests(intermediates: Path, variant: str) -> list[Path]:
    if not intermediates.is_dir():
        return []

    variant_name = variant.lower()
    candidates: list[Path] = []
    for path in intermediates.rglob("AndroidManifest.xml"):
        normalized = path.as_posix().lower()
        path_parts = {part.lower() for part in path.parts}
        is_variant = variant_name in path_parts
        is_merged = (
            "merged_manifest" in normalized
            or f"process{variant_name}" in normalized
        )
        if is_variant and is_merged:
            candidates.append(path)
    return sorted(set(candidates))


def find_apkanalyzer() -> Path | None:
    installed = shutil.which("apkanalyzer")
    if installed:
        return Path(installed)

    sdk_roots = [
        os.environ.get("ANDROID_HOME"),
        os.environ.get("ANDROID_SDK_ROOT"),
    ]
    for sdk_root in filter(None, sdk_roots):
        root = Path(sdk_root)
        for candidate in (
            root / "cmdline-tools" / "latest" / "bin" / "apkanalyzer",
            root / "cmdline-tools" / "bin" / "apkanalyzer",
            root / "tools" / "bin" / "apkanalyzer",
        ):
            if candidate.is_file():
                return candidate
    return None


def audit_apk_manifest(apk: Path, apkanalyzer: Path) -> list[str]:
    try:
        result = subprocess.run(
            [str(apkanalyzer), "manifest", "print", str(apk)],
            capture_output=True,
            text=True,
            check=False,
        )
    except OSError as error:
        return [f"{apk}: could not run apkanalyzer ({error})"]

    if result.returncode != 0:
        detail = result.stderr.strip() or f"exit code {result.returncode}"
        return [f"{apk}: apkanalyzer failed ({detail})"]
    if not result.stdout.strip():
        return [f"{apk}: apkanalyzer returned an empty manifest"]
    return audit_manifest_text(result.stdout, f"APK {apk}")


def main() -> int:
    root = Path.cwd()
    intermediates = root / "app" / "build" / "intermediates"
    apk_root = root / "app" / "build" / "outputs" / "apk"
    if not intermediates.is_dir():
        print(
            "ERROR: Android build intermediates are missing; run assembleDebug "
            "and assembleRelease first.",
            file=sys.stderr,
        )
        return 1

    failures: list[str] = []
    manifest_count = 0
    apk_paths: list[Path] = []

    for variant in VARIANTS:
        manifests = find_merged_manifests(intermediates, variant)
        if not manifests:
            failures.append(
                f"ERROR: merged {variant} manifest was not found; refusing to skip the gate."
            )
        for manifest in manifests:
            manifest_count += 1
            failures.extend(audit_manifest_file(manifest))

        variant_apks = sorted((apk_root / variant).rglob("*.apk")) if (
            apk_root / variant
        ).is_dir() else []
        if not variant_apks:
            failures.append(
                f"ERROR: packaged {variant} APK was not found; refusing to skip the gate."
            )
        apk_paths.extend(variant_apks)

    apkanalyzer = find_apkanalyzer()
    if apk_paths and apkanalyzer is None:
        failures.append(
            "ERROR: apkanalyzer was not found in PATH or the Android SDK; "
            "refusing to skip the APK-level offline gate."
        )
    elif apkanalyzer is not None:
        for apk in apk_paths:
            failures.extend(audit_apk_manifest(apk, apkanalyzer))

    if failures:
        print("FAIL: offline artifact policy violated or could not be verified:", file=sys.stderr)
        for failure in failures:
            print(f" - {failure}", file=sys.stderr)
        return 1

    print(
        f"PASS: checked {manifest_count} merged manifest(s) and "
        f"{len(apk_paths)} packaged APK(s) across debug and release; "
        "no INTERNET permission or enabled cleartext traffic found."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
