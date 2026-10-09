---
name: ocr-action-extraction
description: Build multilingual document OCR, classification, field extraction, confidence handling, and safe reminders for KAAGAZ VAULT.
---

# OCR and Action Extraction Skill

## Pipeline
Treat capture, image preprocessing, OCR, layout/order reconstruction, classification, field extraction, review, persistence, and reminder creation as separate stages with explicit inputs and outputs. Preserve page order and page-level provenance.

## OCR and multilingual documents
- Support mixed Telugu, English, Hindi, and Hinglish without assuming a single language per document.
- Preserve original OCR text and layout/bounding-box metadata when available; do not silently reorder table rows or merge unrelated fields.
- Use preprocessing selectively (orientation, crop, contrast, adaptive threshold) and compare it against an unmodified baseline. Avoid transformations that erase handwriting or thin strokes.
- Distinguish printed text, handwriting, numeric fields, dates, and currency. Keep locale assumptions explicit.
- OCR engines and model files must be assessed for offline behavior, licensing, size, runtime compatibility, and data handling before adoption.

## Extraction discipline
- Every extracted field should carry value, source span/page when available, confidence, extraction method/model version, and review status.
- Normalize dates, amounts, and durations only when evidence supports the interpretation; preserve the raw value alongside the normalized value.
- Do not infer a warranty duration, expiry date, dosage, or return deadline merely because a field is missing.
- Use configurable thresholds validated against a labeled dataset. A confidence score is not automatically a calibrated probability.
- Low-confidence medical dosage or patient instructions must be marked for human review and must not generate actionable medication reminders.
- Reminder creation must be traceable to a reviewed field or an explicitly confirmed user entry. Handle time zones, missing dates, duplicate reminders, edits, and cancellation.

## Evaluation
- Build a consented, de-identified evaluation corpus; never commit real Aadhaar/PAN documents or identifiable prescriptions.
- Separate train/dev/test data by source/document where possible to prevent leakage.
- Measure character error rate, word error rate, field exact-match/normalized accuracy, date/amount accuracy, layout-order errors, confidence calibration, and per-language/per-document-type performance.
- Track false reminders and dangerous extraction errors, not just average OCR accuracy.
- Compare model/library versions on the same fixed test set before changing the pipeline.
