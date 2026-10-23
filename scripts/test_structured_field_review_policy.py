"""Regression guards for OCR correction bounds and edited-field provenance."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
REPOSITORY = ROOT / "app/src/main/java/com/kaagazvault/documents/DocumentRepository.kt"
ACTIVITY = ROOT / "app/src/main/java/com/kaagazvault/MainActivity.kt"


class StructuredFieldReviewPolicyTest(unittest.TestCase):
    def test_corrected_ocr_extraction_uses_the_same_bounded_text_as_storage(self):
        source = REPOSITORY.read_text(encoding="utf-8")
        self.assertIn("ocrText = correctedText.take(MAX_OCR_CHARACTERS)", source)
        self.assertIn(
            "StructuredFieldExtractor.extract(correctedText.take(MAX_OCR_CHARACTERS))",
            source,
        )

    def test_initial_ocr_extraction_uses_the_same_bounded_text_as_storage(self):
        source = REPOSITORY.read_text(encoding="utf-8")
        self.assertIn("ocrText = result.text.take(MAX_OCR_CHARACTERS)", source)
        self.assertIn(
            "StructuredFieldExtractor.extract(result.text.take(MAX_OCR_CHARACTERS))",
            source,
        )

    def test_editing_a_suggestion_clears_confirmation_and_warns_about_old_provenance(self):
        source = ACTIVITY.read_text(encoding="utf-8")
        self.assertIn("accepted = false", source)
        self.assertIn(
            "user-edited; original OCR excerpt may not match edited value",
            source,
        )
        self.assertIn(
            "Original OCR excerpt (may not match edited value)",
            source,
        )


if __name__ == "__main__":
    unittest.main()
