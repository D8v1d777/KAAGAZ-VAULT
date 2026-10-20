# KAAGAZ-VAULT

**Offline-first Android document vault — implementation in progress.**

KAAGAZ-VAULT is designed to capture, import, organize, search, and extract text from personal documents without sending document contents to a server in the FOSS build.

## Implemented so far

- Android Kotlin + Jetpack Compose app shell.
- Android Keystore-backed AES-256 keys and versioned AES-GCM payload encryption.
- Encrypted files in app-private storage, opaque IDs, bounded input, atomic pending-file writes, and interrupted-update recovery.
- User-selected PDF/image import through Android's document picker; no broad storage permission.
- Offline Tesseract OCR for images in English, Hindi, and Telugu with pinned model files and build/runtime integrity checks.
- Human-editable OCR text and review state stored in the encrypted payload.
- SQLCipher-backed metadata database with a wrapped random database key, local normalized search, index rebuild from encrypted payloads, and in-memory fallback when the encrypted DB is unavailable.
- User-initiated CameraX capture with in-memory JPEG handling and direct encrypted import.
- Bounded PDF text extraction/OCR prototype using in-memory PDF bytes, one page at a time, with strict page/pixel/text limits.

**These are implemented code paths, not a claim of release readiness.** The last recorded green Android CI gate was run [#299](https://github.com/D8v1d777/KAAGAZ-VAULT/actions/runs/39144069929) on commit `6291594886eb6f6fb03c644797afc1217adb90e6`; that evidence applies only to that SHA. The later source head `7b7620f537d03a4f15c18ac80a9c8fa5bf3bc489` failed Android CI [run #323](https://github.com/D8v1d777/KAAGAZ-VAULT/actions/runs/39203140191) because `SettingsSyncProtocolTest.mergeKeepsLocalByDefaultAndRequiresVoiceReview` failed. A separate correction branch is addressing that regression; do not claim the candidate is green until the complete gate passes for the exact candidate SHA. No physical-device camera/OCR tests or on-device database leakage tests have been run.

## Privacy and security boundaries

- FOSS core must remain offline. Do not add INTERNET permission, analytics, ads, cloud OCR, or network AI dependencies.
- Do not store real Aadhaar/PAN IDs, prescriptions, medical records, personal documents, secrets, or credentials in the repository.
- OCR/PDF text is untrusted evidence. It must remain editable and reviewable; do not turn uncertain medical text into medication instructions or automatic actions.
- Document payloads and metadata/search have encrypted-at-rest implementation paths. The missing-key fail-closed guard passed configured CI at commit 54a0ce2. The migration-state model and coordinator pass configured CI on the recorded head; however, the auth-bound key migration, biometric app lock, recovery/rotation, reminders, export controls, and device-level leakage review remain incomplete.
- File deletion is not guaranteed secure erasure. The app is not release-approved.

## Current project status

- Estimated implemented-scope progress: **35% of weighted objectives**. This is a scope estimate, not a security score or a test-pass percentage.
- Phase 3 encrypted import + image OCR CI: [passed](https://github.com/D8v1d777/KAAGAZ-VAULT/actions/runs/38030021406).
- Phase 4 SQLCipher/search CI: [passed](https://github.com/D8v1d777/KAAGAZ-VAULT/actions/runs/38030642290).
- Phase 5 CameraX CI: [passed](https://github.com/D8v1d777/KAAGAZ-VAULT/actions/runs/38031637413).
- Phase 12 migration coordinator: [CI #299 passed](https://github.com/D8v1d777/KAAGAZ-VAULT/actions/runs/39144069929) on commit `6291594886eb6f6fb03c644797afc1217adb90e6`.
- Draft PRs: [Phase 3](https://github.com/D8v1d777/KAAGAZ-VAULT/pull/3), [Phase 4](https://github.com/D8v1d777/KAAGAZ-VAULT/pull/4), [Phase 5](https://github.com/D8v1d777/KAAGAZ-VAULT/pull/5). No PR has been merged.

See [engineering progress](docs/engineering/progress.md), [weighted completion tracker](docs/engineering/product-completion.md), [quality gates](docs/engineering/quality-gates.md), [ADR-0001](docs/engineering/decisions/ADR-0001-sensitive-data-search.md), [ADR-0002 missing-key fail-closed policy](docs/engineering/decisions/ADR-0002-missing-key-fail-closed.md), and [ADR-0003 auth-bound vault session proposal](docs/engineering/decisions/ADR-0003-auth-bound-vault-session.md). The [Phase 12 auth-bound lock plan](docs/PHASE-12-AUTH-BOUND-VAULT-LOCK-PLAN.md) defines migration and verification gates. The human/AI reviewer report is maintained in Notion.

## Toolchain

- AGP 9.1.1
- Gradle 9.3.1
- JDK 21 for CI
- Kotlin 2.3.10 / KSP 2.3.4; AGP built-in Kotlin disabled to align with the PDFium dependency
- compileSdk/targetSdk 36; provisional minSdk 24
- Compose BOM 2026.04.01

The Gradle Wrapper is checked in. CI uses `./gradlew --no-daemon clean assembleDebug assembleRelease testDebugUnitTest lint`, preceded by `python3 -m unittest discover -s scripts -p 'test_*.py'` and followed by `python3 scripts/check_offline_manifest.py`. The project's own license has not yet been selected; do not assume the repository grants permission to reuse or redistribute project code.

## Engineering workflow

Read [AGENTS.md](AGENTS.md) before changing code. Research primary documentation first, implement a small coherent feature, verify the exact current branch, then record decisions, changes, evidence, risks, and next actions in Notion. Never claim a build/test pass without an actual completed run.
