<div align="center">

<img src="docs/assets/kaagaz-blue-eye-lock.jpg" alt="Kaagaz Vault blue privacy lock mark" width="118" />

# KAAGAZ VAULT

### A calmer, private place for important papers.

**Offline-first Android document vault · local encryption · human-reviewed OCR · no cloud document pipeline**

<br />

[![Android](https://img.shields.io/badge/Android-API%2024%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com/)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.3.10-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Privacy](https://img.shields.io/badge/Privacy-local--first-0B1020?style=for-the-badge&logo=shield&logoColor=7DD3FC)](#privacy-is-the-product)

</div>

<br />

<p align="center">
  <img src="docs/assets/kaagaz-hero-banner.jpg" alt="Original blue-lit guardian surrounded by protected paper fragments" width="100%" />
</p>

> **Your documents are personal.** Kaagaz Vault is being built around a simple promise: capture, search, review, and organize important papers without making a cloud service part of the trust boundary.

<div align="center">

[Explore the overview](docs/overview.md) · [Read the engineering progress](docs/engineering/progress.md) · [See the quality gates](docs/engineering/quality-gates.md) · [Review the phase designs](docs/)

</div>

---

## The product idea

Kaagaz means **paper**. The app is designed for the moment after a document enters your life: a receipt, certificate, prescription, identity paper, warranty, or form that needs to stay findable without becoming somebody else’s dataset.

Kaagaz Vault keeps the experience quiet and tactile. Import or scan a paper, let local OCR help, review what was extracted, then keep the encrypted copy close at hand. The interface is intentionally calm; the security boundary is intentionally strict.

| **01 · Capture** | **02 · Understand** | **03 · Keep control** |
| --- | --- | --- |
| Import a PDF or image through Android’s document picker, or capture a page with CameraX. | Run OCR locally in English, Hindi, or Telugu, then review and correct the result yourself. | Store document payloads and metadata through encrypted local paths with no cloud OCR or analytics dependency. |

## What is implemented

- Android Kotlin + Jetpack Compose application shell with Material 3 theming and Android 12+ dynamic-color support.
- Android Keystore-backed AES-256 document keys and versioned AES-GCM payload encryption.
- App-private encrypted files, opaque IDs, bounded input, atomic pending-file writes, and interrupted-update recovery.
- User-selected PDF/image import with no broad storage permission.
- Offline Tesseract OCR for English, Hindi, and Telugu with pinned model files and integrity checks.
- Human-editable OCR text and an explicit review state stored with the encrypted payload.
- Bounded offline suggestions for dates, amounts, labelled identifiers, and names, shown with source excerpts and uncertainty labels; values remain unaccepted until the user edits and confirms them, and reviewed fields stay inside the encrypted payload.
- SQLCipher-backed metadata search with encrypted-index and in-memory fallback paths.
- User-initiated CameraX capture with in-memory JPEG handling and direct encrypted import.
- Calm motion, restrained haptics, accessibility-aware typography, simplified mode, and privacy-safe app-switcher surfaces.
- Local encrypted transfer of allowlisted accessibility settings only; document images, OCR text, reminders, and recordings stay out of that flow.

**These are implemented code paths, not a claim of release readiness.** Candidate SHA `acbe7d8e5092d762d89f202d0d2ad02ef1d60afa` passed the configured Android CI gate in [run #559](https://github.com/D8v1d777/KAAGAZ-VAULT/actions/runs/39753800533): debug/release builds, JVM tests, lint, Python policy tests, the merged-manifest offline policy gate, and 11 Android emulator instrumentation tests. Coverage includes auth-per-use denial, missing-alias fail-closed behavior, controller/receiver lock paths, and synthetic SQLCipher row preservation, integrity checking, and durable migration recovery after an interruption before the DB-key-rewrapped journal checkpoint. This is emulator evidence using synthetic keys and a synthetic database; it does not exercise the production `DatabaseKeyManager` fixed alias, actual OS-driven screen-off/background delivery, true process death/restart, biometric enrollment change, or physical devices. Independent human security review and the G2 decision on the fail-closed legacy-key fallback remain outstanding. PR #48 stays draft/unmerged. PR #49's dependency-verification metadata update is being validated separately in [run #561](https://github.com/D8v1d777/KAAGAZ-VAULT/actions/runs/39755053774); no combined release candidate is approved.

## Privacy is the product

The FOSS core is designed to work without an internet permission, analytics SDK, ad SDK, cloud OCR, or cloud AI dependency. Sensitive document images, OCR text, filenames derived from OCR, and personal metadata must not leave the device in the FOSS build.

OCR is treated as untrusted evidence. It remains editable and reviewable. Kaagaz Vault does not turn uncertain medical text into automatic instructions or actions.

The app is not release-approved yet. Secure deletion is not guaranteed, physical-device leakage testing is incomplete, the auth-bound biometric lock still has verification gates, and the project license has not yet been selected. Read the [engineering contract](AGENTS.md) before contributing.

## Project map

| Area | Start here |
| --- | --- |
| Product overview | [docs/overview.md](docs/overview.md) |
| Current progress and evidence | [docs/engineering/progress.md](docs/engineering/progress.md) |
| Weighted completion tracker | [docs/engineering/product-completion.md](docs/engineering/product-completion.md) |
| Quality and release gates | [docs/engineering/quality-gates.md](docs/engineering/quality-gates.md) |
| Sensitive-data search decisions | [ADR-0001](docs/engineering/decisions/ADR-0001-sensitive-data-search.md) |
| Missing-key fail-closed policy | [ADR-0002](docs/engineering/decisions/ADR-0002-missing-key-fail-closed.md) |
| Auth-bound vault session proposal | [ADR-0003](docs/engineering/decisions/ADR-0003-auth-bound-vault-session.md) |
| Phase designs | [docs/](docs/) |

## Toolchain

- AGP 9.1.1 · Gradle 9.3.1 · JDK 21 for CI
- Kotlin 2.3.10 · KSP 2.3.4
- compileSdk / targetSdk 36 · provisional minSdk 24
- Compose BOM 2026.04.01

The Gradle wrapper is checked in. The configured CI sequence runs the script tests, Android build, JVM unit tests, lint, and the offline-manifest check. Never claim a build or test pass without a completed run.

```bash
git clone https://github.com/D8v1d777/KAAGAZ-VAULT.git
cd KAAGAZ-VAULT
./gradlew --no-daemon clean assembleDebug testDebugUnitTest lint
```

## Contributing with care

1. Read [AGENTS.md](AGENTS.md) and the relevant `.agents/skills/` guidance.
2. Keep changes small, reviewable, and inside the correct boundary.
3. Do not commit real Aadhaar/PAN data, prescriptions, personal documents, secrets, or credentials.
4. Record decisions, evidence, known limitations, and the next concrete step in the engineering progress log.
5. Preserve offline behavior and test the sensitive-data paths before polishing the happy path.

## Visual note

The banner and visual accents in this README are original generated artwork created for this repository in a blue, blindfolded-sorcerer-inspired direction. They are not official *Jujutsu Kaisen* artwork, do not depict a canon frame, and do not imply affiliation with the rights holders.

<div align="center">

**Private by default. Calm by design. Still being verified.**

</div>
