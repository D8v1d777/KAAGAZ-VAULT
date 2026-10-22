"""Static regression checks for pinned offline OCR build inputs."""
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
APP_BUILD = (ROOT / "app" / "build.gradle.kts").read_text(encoding="utf-8")
VERSION_CATALOG = (ROOT / "gradle" / "libs.versions.toml").read_text(encoding="utf-8")
README = (ROOT / "README.md").read_text(encoding="utf-8")

EXPECTED_MODELS = {"eng.traineddata", "hin.traineddata", "tel.traineddata"}


def model_pins(variable: str) -> dict[str, str]:
    match = re.search(rf"val {variable} = mapOf\((.*?)\n\)", APP_BUILD, re.DOTALL)
    if match is None:
        return {}
    return dict(re.findall(r'"([a-z]+\.traineddata)"\s+to\s+"([0-9a-f]+)"', match.group(1)))


class OcrModelBuildIntegrityTest(unittest.TestCase):
    def test_model_revision_and_both_integrity_pin_sets_are_complete(self) -> None:
        revision = re.search(r'val tessdataRevision = "([0-9a-f]+)"', APP_BUILD)
        self.assertIsNotNone(revision)
        self.assertEqual(40, len(revision.group(1)))

        git_blob_pins = model_pins("tessdataModels")
        sha256_pins = model_pins("tessdataModelsSha256")
        self.assertEqual(EXPECTED_MODELS, set(git_blob_pins))
        self.assertEqual(EXPECTED_MODELS, set(sha256_pins))
        self.assertTrue(all(len(pin) == 40 for pin in git_blob_pins.values()))
        self.assertTrue(all(len(pin) == 64 for pin in sha256_pins.values()))

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

    def test_readme_documents_build_time_network_and_offline_preseed_path(self) -> None:
        self.assertIn("build-time network requirement", README)
        self.assertIn("app/build/generated/ocr-assets/tessdata/eng.traineddata", README)
        self.assertIn("without " + chr(96) + "clean" + chr(96), README)


if __name__ == "__main__":
    unittest.main()
