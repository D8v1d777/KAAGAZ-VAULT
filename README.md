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

**These are implemented code paths, not a claim of release readiness.** The current PDFium/Kotlin toolchain update is awaiting CI verification. CameraX and SQLCipher branches have passing CI at their recorded commits, but no physical-device camera/OCR tests or on-device database leakage tests have been run.

## Privacy and security boundaries

- FOSS core must remain offline. Do not add INTERNET permission, analytics, ads, cloud OCR, or network AI dependencies.
- Do not store real Aadhaar/PAN IDs, prescriptions, medical records, personal documents, secrets, or credentials in the repository.
- OCR/PDF text is untrusted evidence. It must remain editable and reviewable; do not turn uncertain medical text into medication instructions or automatic actions.
- Document payloads and metadata/search are encrypted at rest in the implemented paths, but recovery/rotation, biometric app lock, reminders, export controls, PDF edge cases, and device-level leakage review remain incomplete.
- File deletion is not guaranteed secure erasure. The app is not release-approved.

## Current project status

- Estimated implemented-scope progress: **35% of weighted objectives**. This is a scope estimate, not a security score or a test-pass percentage.
- Phase 3 encrypted import + image OCR CI: [passed](https://github.com/D8v1d777/KAAGAZ-VAULT/actions/runs/38030021406).
- Phase 4 SQLCipher/search CI: [passed](https://github.com/D8v1d777/KAAGAZ-VAULT/actions/runs/38030642290).
- Phase 5 CameraX CI: [passed](https://github.com/D8v1d777/KAAGAZ-VAULT/actions/runs/38031637413).
- Phase 6 PDFium/Kotlin toolchain update: CI is pending/failing until the latest compatibility correction is verified.
- Draft PRs: [Phase 3](https://github.com/D8v1d777/KAAGAZ-VAULT/pull/3), [Phase 4](https://github.com/D8v1d777/KAAGAZ-VAULT/pull/4), [Phase 5](https://github.com/D8v1d777/KAAGAZ-VAULT/pull/5). No PR has been merged.

See [engineering progress](docs/engineering/progress.md), [weighted completion tracker](docs/engineering/product-completion.md), [quality gates](docs/engineering/quality-gates.md), and [ADR-0001](docs/engineering/decisions/ADR-0001-sensitive-data-search.md). The human/AI reviewer report is maintained in Notion.

## Toolchain

- AGP 9.1.1
- Gradle 9.3.1
- JDK 21 for CI
- Kotlin 2.3.10 / KSP 2.3.4; AGP built-in Kotlin disabled to align with the PDFium dependency
- compileSdk/targetSdk 36; provisional minSdk 24
- Compose BOM 2026.04.01

A checked-in Gradle Wrapper is still pending. The project's own license has not yet been selected; do not assume the repository grants permission to reuse or redistribute project code.

## Engineering workflow

Read [AGENTS.md](AGENTS.md) before changing code. Research primary documentation first, implement a small coherent feature, verify the exact current branch, then record decisions, changes, evidence, risks, and next actions in Notion. Never claim a build/test pass without an actual completed run.
