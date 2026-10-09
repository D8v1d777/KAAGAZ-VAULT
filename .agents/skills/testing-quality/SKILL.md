---
name: testing-quality
description: Apply test-driven development, regression testing, performance checks, and release gates to KAAGAZ VAULT.
---

# Testing and Quality Skill

## Test pyramid
- Unit-test deterministic domain rules: category mapping, date normalization, expiry calculations, confidence thresholds, deduplication, and reminder state transitions.
- Test repositories and Room queries against an in-memory database where suitable.
- Add instrumentation/UI tests for capture/import flows, permission states, navigation, lock behavior, and critical error states.
- Keep OCR evaluation as a reproducible benchmark suite with fixed fixtures and metrics; do not make unit tests depend on remote OCR providers.
- Prefer small fakes and deterministic clocks/schedulers. Avoid flaky sleeps and tests that depend on wall-clock timing.

## Required regression scenarios
- Mixed Telugu/English bill; multiple pages; rotated, blurred, shadowed, and low-resolution images.
- Crushed/low-contrast paper; OCR with missing or ambiguous dates; amount formats and Indian currency conventions.
- Handwritten medical fields below confidence threshold must not create dosage reminders.
- Duplicate import; file moved/deleted; insufficient storage; interrupted capture; database migration; app process death.
- Locked app, obscured recent-apps preview, failed biometric prompt, encrypted file corruption, cancelled export, and no-network operation.
- Verify FOSS merged manifest and APK permissions as part of release checks.

## Quality gates
- Build and relevant tests pass; failures are investigated, not hidden or skipped.
- Static analysis/lint and dependency vulnerability checks run in CI when configured.
- No secrets or real personal documents in repository history.
- Performance checked on low-end hardware or representative constrained profiles before claiming support.
- Review accessibility, localization, and screen sizes.
- Report exact commands, test counts, and known gaps. A green unit test suite does not prove security or OCR accuracy.
