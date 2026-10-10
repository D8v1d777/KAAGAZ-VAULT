# Document Imaging Evaluation Skill

## Purpose
Build a robust capture and preprocessing pipeline for real-world Indian paper documents without destroying OCR-relevant information.

## Pipeline
Evaluate each stage independently: capture guidance and focus → edge detection/crop → perspective correction → rotation/orientation → illumination/shadow correction → optional denoise/contrast → OCR → human review. Preserve the original capture unless the user explicitly deletes it; transformed output must be traceable to its source.

## Required behavior
- Detect blur, glare, clipping, severe perspective, low light, and incomplete page boundaries; request recapture when quality is insufficient.
- Avoid aggressive binarization or denoising that removes faint ink, decimal points, handwritten digits, medicine strengths, dates, or stamps.
- Keep image processing on-device in the offline build. Do not upload images to a remote service as a hidden fallback.
- Handle multi-page capture, cancellation, rotation, malformed imports, oversized files, low storage, and duplicate import safely.
- Provide an accessible preview and allow correction/recapture before OCR results become durable actions.

## Evaluation
- Use consented, de-identified or synthetic fixtures with documented provenance; never commit real Aadhaar/PAN images or identifiable prescriptions.
- Maintain a versioned corpus spanning printed receipts, handwritten kirana bills, prescriptions, education receipts, mock IDs, low-quality messaging-app images, and mixed-language layouts.
- Measure crop success, page completeness, OCR character/word error rate, field extraction accuracy, false expiry/reminder rate, and recapture precision.
- Report results by language, category, handwriting/print, image quality, and device class. Aggregate scores alone can hide dangerous failure cases.
- Keep a representative set of hard negatives and regressions for every pipeline change.

## Acceptance criteria
- Before/after examples are reproducible from synthetic fixtures.
- Any image transform has a stated purpose and regression checks.
- Thresholds are selected from measured validation results, not intuition.
