# Reminders & Action Safety Skill

## Purpose
Convert extracted dates and document facts into useful reminders without inventing certainty or causing unsafe medical behavior.

## Rules
- OCR and extraction are untrusted evidence, never authoritative truth.
- Store extracted raw text, normalized value, confidence, source span/page, extractor/model version, and review state when applicable.
- Never infer a missing expiry date, warranty period, return deadline, dosage, frequency, or start date to make a feature appear complete.
- Show the evidence used to derive a date and let users correct it before scheduling when confidence is not demonstrably safe.
- Medical reminders may reflect a user-confirmed schedule; they must not independently prescribe, change, or infer dosage from uncertain handwriting.
- For low-confidence handwritten medical text (the product threshold is currently under 60%), require human review and keep the result non-actionable. Treat that threshold as a conservative product rule, not proof that confidence scores are calibrated.
- Distinguish calendar dates from durations and time zones; handle leap years, locale ambiguity, missing years, expired dates, and daylight-saving transitions where relevant.
- Make reminders editable, dismissible, idempotent, and explainable. Reconcile scheduling after reboot, process death, edits, document deletion, and permission changes.

## Tests
Cover ambiguous dates, impossible dates, month/year rollover, duplicates, timezone changes, clock changes, reboot/rescheduling, deletion, user edits, low-confidence medical text, and extraction false positives.

## Acceptance criteria
- No reminder is created from a value that fails the product's review policy.
- The UI distinguishes extracted evidence from user-confirmed information.
- Scheduling behavior is tested with deterministic clocks and fakes; exact delivery timing is not promised where Android does not guarantee it.
