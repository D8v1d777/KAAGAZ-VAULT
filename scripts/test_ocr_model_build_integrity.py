"""Static regression checks for pinned offline OCR build inputs."""
import re
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
APP_BUILD = (ROOT / "app" / "build.gradle.kts").read_text(encoding="utf-8")
VERSION_CATALOG = (ROOT / "gradle" / "libs.versions.toml").read_text(encoding="utf-8")
README = (ROOT / "README.md").read_text(encoding="utf-8")
WORKFLOW = (ROOT / ".github" / "workflows" / "android-ci.yml").read_text(encoding="utf-8")
VERIFICATION_METADATA = ROOT / "gradle" / "verification-metadata.xml"

EXPECTED_REVISION = "87416418657359cb625c412a48b6e1d6d41c29bd"
EXPECTED_GIT_BLOB_PINS = {
    "eng.traineddata": "bbef4675053b5b468cdb477053e28b1c698ba08e",
    "hin.traineddata": "a8f0aaec09378115b12a2c6287c57695d082aea2",
    "tel.traineddata": "ee8a33b0f658fc75b8cbfe8c10413eac4f41e6ad",
}
EXPECTED_SHA256_PINS = {
    "eng.traineddata": "7d4322bd2a7749724879683fc3912cb542f19906c83bcc1a52132556427170b2",
    "hin.traineddata": "4c73ffc59d497c186b19d1e90f5d721d678ea6b2e277b719bee4e2af12271825",
    "tel.traineddata": "d10691fddd5b67802e1c12800ebb321d3b8bcd8d24a2ac3ff206f93188c04ab5",
}
EXPECTED_MODELS = set(EXPECTED_GIT_BLOB_PINS)


def model_pins(variable: str) -> dict[str, str]:
    match = re.search(rf"val {variable} = mapOf\((.*?)\n\)", APP_BUILD, re.DOTALL)
    if match is None:
        return {}
    return dict(re.findall(r'"([a-z]+\.traineddata)"\s+to\s+"([0-9a-f]+)"', match.group(1)))


class OcrModelBuildIntegrityTest(unittest.TestCase):
    def test_model_revision_and_both_integrity_pin_sets_are_complete(self) -> None:
        revision = re.search(r'val tessdataRevision = "([0-9a-f]+)"', APP_BUILD)
        self.assertIsNotNone(revision)
        self.assertEqual(EXPECTED_REVISION, revision.group(1))

        git_blob_pins = model_pins("tessdataModels")
        sha256_pins = model_pins("tessdataModelsSha256")
        self.assertEqual(EXPECTED_GIT_BLOB_PINS, git_blob_pins)
        self.assertEqual(EXPECTED_SHA256_PINS, sha256_pins)
        self.assertEqual(EXPECTED_MODELS, set(git_blob_pins))
        self.assertEqual(EXPECTED_MODELS, set(sha256_pins))

    def test_existing_and_downloaded_models_are_checked_with_sha256_and_git_blob_sha1(self) -> None:
        self.assertIn('MessageDigest.getInstance("SHA-256")', APP_BUILD)
        self.assertIn('MessageDigest.getInstance("SHA-1")', APP_BUILD)
        self.assertIn("gitBlobSha1(bytes) == expectedGitBlobSha", APP_BUILD)
        self.assertIn("sha256Hex(bytes) == expectedSha256", APP_BUILD)
        self.assertIn("actualGitBlobSha != expectedGitBlobSha || actualSha256 != expectedSha256", APP_BUILD)

    def test_model_task_declares_inputs_and_allows_gradle_up_to_date_checks(self) -> None:
        self.assertIn('inputs.property("tessdataRevision", tessdataRevision)', APP_BUILD)
        self.assertIn('"tessdataGitBlobSha1Pins"', APP_BUILD)
        self.assertIn('"tessdataSha256Pins"', APP_BUILD)
        self.assertNotIn("outputs.upToDateWhen { false }", APP_BUILD)

    def test_dependency_coordinates_use_catalog_without_changing_versions(self) -> None:
        self.assertIn('implementation(libs.tesseract4android)', APP_BUILD)
        self.assertNotIn('implementation("cz.adaptech.tesseract4android:tesseract4android:4.9.0")', APP_BUILD)
        self.assertIn('implementation(libs.sqlcipher.android)', APP_BUILD)
        self.assertIn('isTransitive = false', APP_BUILD)
        self.assertIn('type = "aar"', APP_BUILD)
        self.assertIn('tesseract4android = "4.9.0"', VERSION_CATALOG)
        self.assertIn('tesseract4android = { module = "cz.adaptech.tesseract4android:tesseract4android", version.ref = "tesseract4android" }', VERSION_CATALOG)
        self.assertIn('sqlcipher-android = { module = "net.zetetic:sqlcipher-android", version.ref = "sqlcipher" }', VERSION_CATALOG)
        self.assertIn('sqlcipher = "4.19.1"', VERSION_CATALOG)
        self.assertNotIn('implementation("net.zetetic:sqlcipher-android:4.19.1@aar")', APP_BUILD)

    def test_committed_gradle_verification_metadata_covers_every_artifact(self) -> None:
        self.assertTrue(VERIFICATION_METADATA.is_file())
        root = ET.parse(VERIFICATION_METADATA).getroot()
        namespace = {"g": "https://schema.gradle.org/dependency-verification"}
        verify_metadata = root.find("g:configuration/g:verify-metadata", namespace)
        self.assertIsNotNone(verify_metadata)
        self.assertEqual("true", verify_metadata.text)

        components = root.findall(".//g:component", namespace)
        artifacts = root.findall(".//g:artifact", namespace)
        self.assertGreater(len(components), 0)
        self.assertGreater(len(artifacts), 0)
        missing_sha256 = [
            artifact.attrib.get("name", "<unnamed>")
            for artifact in artifacts
            if not artifact.findall("g:sha256", namespace)
        ]
        self.assertEqual([], missing_sha256)

        coordinates = {
            (component.attrib.get("group"), component.attrib.get("name"), component.attrib.get("version"))
            for component in components
        }
        self.assertIn(("cz.adaptech.tesseract4android", "tesseract4android", "4.9.0"), coordinates)
        self.assertIn(("net.zetetic", "sqlcipher-android", "4.19.1"), coordinates)

    def test_ci_no_longer_uses_temporary_metadata_bootstrap(self) -> None:
        self.assertIn("python3 -m unittest discover -s scripts -p 'test_*.py'", WORKFLOW)
        self.assertIn("Verify OCR model preparation is up-to-date", WORKFLOW)
        self.assertNotIn("commit-verification-metadata", WORKFLOW)
        self.assertNotIn("Bootstrap Gradle dependency verification metadata", WORKFLOW)
        self.assertNotIn("--write-verification-metadata", WORKFLOW)
        self.assertNotIn("co06-gradle-verification-metadata", WORKFLOW)

    def test_readme_documents_build_time_network_and_offline_preseed_path(self) -> None:
        self.assertIn("build-time network requirement", README)
        self.assertIn("app/build/generated/ocr-assets/tessdata/eng.traineddata", README)
        self.assertIn("without " + chr(96) + "clean" + chr(96), README)


if __name__ == "__main__":
    unittest.main()
