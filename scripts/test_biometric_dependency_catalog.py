"""Regression guard for the owner-approved AndroidX Biometric dependency pin."""

from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[1]
VERSION_CATALOG = ROOT / "gradle" / "libs.versions.toml"
APP_GRADLE = ROOT / "app" / "build.gradle.kts"


class BiometricDependencyCatalogTest(unittest.TestCase):
    def test_approved_stable_biometric_dependency_is_pinned_and_used(self) -> None:
        catalog = VERSION_CATALOG.read_text(encoding="utf-8")
        app_gradle = APP_GRADLE.read_text(encoding="utf-8")

        self.assertRegex(catalog, r'(?m)^biometric\s*=\s*"1\.1\.0"\s*$')
        self.assertRegex(
            catalog,
            r'(?m)^androidx-biometric\s*=\s*\{\s*module\s*=\s*"androidx\.biometric:biometric",\s*version\.ref\s*=\s*"biometric"\s*\}\s*$',
        )
        self.assertIn("implementation(libs.androidx.biometric)", app_gradle)
        self.assertNotRegex(
            app_gradle,
            r'implementation\s*\(\s*["\']androidx\.biometric:biometric:',
        )


if __name__ == "__main__":
    unittest.main()
