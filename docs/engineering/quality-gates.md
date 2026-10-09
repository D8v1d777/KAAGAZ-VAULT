# KAAGAZ VAULT Quality Gates

These are release requirements, not aspirational badges.

## Functional
- Capture/import works for supported image and PDF formats and handles cancellation, malformed input, low storage, and duplicate imports.
- OCR and extraction results are editable and traceable to their source. Uncertain values remain uncertain.
- Search results and reminders are consistent after process death, reboot, document edits, and deletion.
- All core vault workflows operate without connectivity.

## Privacy and security
- FOSS build has no INTERNET permission in the merged manifest and final APK.
- No analytics/ad SDKs or undocumented network behavior.
- Sensitive file bytes and sensitive metadata are protected at rest; plaintext search indexes and WAL/journal/temp files are included in the threat model.
- Key lifecycle, backup exclusions, export, deletion, app-switcher preview, lock screen, and logging are tested.
- Use synthetic/de-identified fixtures only. No actual Aadhaar/PAN data or identifiable medical records in Git.

## OCR/action quality
- Maintain a versioned, legally and ethically sourced test corpus with documented consent/provenance.
- Report metrics per language and document category, not only aggregate accuracy.
- Set confidence thresholds from measured validation data.
- No automatic medication action from unreviewed low-confidence handwriting.
- Measure false positives for expiry/reminder extraction and make reminders easy to correct or dismiss.

## Compatibility and performance
- Pin supported SDK/library versions and verify the actual minSdk support of every dependency.
- Test representative low-end devices and constrained memory/storage conditions.
- Track cold start, scan-to-preview, OCR latency, battery/thermal impact, large-vault search latency, and database growth.
- Do not claim Redmi 9A or API 21 support until tested.

## Release evidence
A release candidate must include:
1. Build/test report with exact commands and environment.
2. Dependency/license review.
3. Manifest/APK permission audit.
4. Privacy and threat-model review.
5. OCR benchmark report and known failure cases.
6. Migration, backup, restore/recovery, and deletion checks as applicable.
7. Known limitations and rollback plan.
