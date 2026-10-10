import subprocess
import tempfile
import unittest
from pathlib import Path
from unittest import mock

from check_offline_manifest import (
    audit_apk_manifest,
    audit_manifest_text,
    find_merged_manifests,
)

ANDROID_NS = "http://schemas.android.com/apk/res/android"


def manifest(permission: str = "", cleartext: str = "false") -> str:
    permission_xml = (
        f'<uses-permission xmlns:android="{ANDROID_NS}" android:name="{permission}" />'
        if permission
        else ""
    )
    return (
        f'<manifest xmlns:android="{ANDROID_NS}">'
        f"{permission_xml}"
        f'<application android:usesCleartextTraffic="{cleartext}" />'
        "</manifest>"
    )


class OfflineManifestPolicyTest(unittest.TestCase):
    def test_accepts_manifest_without_internet_or_cleartext(self) -> None:
        self.assertEqual([], audit_manifest_text(manifest(), "sample"))

    def test_rejects_internet_permission(self) -> None:
        failures = audit_manifest_text(
            manifest("android.permission.INTERNET"), "sample"
        )
        self.assertTrue(any("INTERNET permission is present" in item for item in failures))

    def test_rejects_explicit_cleartext_enablement(self) -> None:
        failures = audit_manifest_text(manifest(cleartext="true"), "sample")
        self.assertTrue(any("cleartext traffic is explicitly enabled" in item for item in failures))

    def test_rejects_malformed_manifest(self) -> None:
        failures = audit_manifest_text("<manifest>", "sample")
        self.assertTrue(any("could not parse manifest" in item for item in failures))

    def test_finds_debug_and_release_merged_manifests_independently(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            debug = root / "merged_manifest" / "debug" / "processDebugMainManifest" / "AndroidManifest.xml"
            release = root / "merged_manifest" / "release" / "processReleaseMainManifest" / "AndroidManifest.xml"
            debug.parent.mkdir(parents=True)
            release.parent.mkdir(parents=True)
            debug.write_text(manifest(), encoding="utf-8")
            release.write_text(manifest(), encoding="utf-8")

            self.assertEqual([debug], find_merged_manifests(root, "debug"))
            self.assertEqual([release], find_merged_manifests(root, "release"))

    def test_audits_the_manifest_extracted_from_the_apk(self) -> None:
        apk = Path("synthetic.apk")
        analyzer = Path("apkanalyzer")
        unsafe_xml = manifest("android.permission.INTERNET")
        completed = subprocess.CompletedProcess(
            args=[str(analyzer), "manifest", "print", str(apk)],
            returncode=0,
            stdout=unsafe_xml,
            stderr="",
        )
        with mock.patch("check_offline_manifest.subprocess.run", return_value=completed) as run:
            failures = audit_apk_manifest(apk, analyzer)

        run.assert_called_once()
        self.assertTrue(any("INTERNET permission is present" in item for item in failures))

    def test_apk_audit_fails_closed_when_analyzer_fails(self) -> None:
        apk = Path("synthetic.apk")
        analyzer = Path("apkanalyzer")
        completed = subprocess.CompletedProcess(
            args=[str(analyzer), "manifest", "print", str(apk)],
            returncode=1,
            stdout="",
            stderr="invalid APK",
        )
        with mock.patch("check_offline_manifest.subprocess.run", return_value=completed):
            failures = audit_apk_manifest(apk, analyzer)
        self.assertTrue(any("apkanalyzer failed" in item for item in failures))


if __name__ == "__main__":
    unittest.main()
