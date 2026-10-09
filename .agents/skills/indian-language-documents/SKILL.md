# Indian-Language Documents Skill

## Purpose
Handle documents containing Telugu, Hindi, English, Hinglish, Indian numeric conventions, and mixed scripts while preserving evidence and uncertainty.

## Required workflow
1. Identify likely scripts/languages without assuming one language for the entire page.
2. Preserve page/line/word order and source coordinates where the OCR engine provides them.
3. Preserve raw OCR output. Normalization must be separate and reversible where possible; never overwrite the evidence with a cleaned guess.
4. Handle Telugu/Hindi/English mixed text, transliteration, local abbreviations, date formats, Indian currency formatting, decimal separators, phone-like numbers, and common receipt layouts.
5. Do not infer personal identity or silently convert uncertain numbers into financial, identity, expiry, or medical values.
6. Make language-specific behavior and model coverage explicit. A model supporting a script does not imply reliable handwriting recognition for that script.

## Dataset governance
- Use synthetic, consented, or properly de-identified documents with provenance and permitted-use notes.
- Do not add real Aadhaar/PAN numbers, phone numbers, addresses, account numbers, or identifiable medical records to source control.
- Separate train/tuning and held-out evaluation sets; prevent near-duplicate leakage.
- Track corpus version, language, category, writing style, quality tier, annotation policy, and known limitations.
- Review data-provider terms, licenses, retention, and whether the corpus permits redistribution.

## Evaluation
Report per-language and per-category character/word error rates plus exact-match/precision/recall for critical fields such as amount, purchase date, expiry date, and medicine strength. Include false-positive examples and confidence calibration where available.

## Acceptance criteria
- Mixed-language regression fixtures exist without sensitive real data.
- Unsupported languages/scripts degrade visibly and safely.
- Critical fields retain provenance and review state.
