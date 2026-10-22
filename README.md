<div align="center">

<img src="docs/images/vault-hero.svg" alt="Kaagaz Vault" width="100%" style="border-radius: 18px;" />

# KAAGAZ-VAULT

### The private document vault with a little limitless energy.

**Privacy-first · offline-first · Android · encrypted local storage · human-reviewed OCR**

<br />

[![Android](https://img.shields.io/badge/Android-API%2024%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com/)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.3.10-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Offline](https://img.shields.io/badge/Core-offline--first-111827?style=for-the-badge&logo=android&logoColor=93C5FD)](#privacy-stays-on-device)

</div>

<p align="center">
  <img src="docs/images/vault-hero.svg" alt="Kaagaz Vault" width="100%" />
</p>

<div align="center">

**Your documents. Your device. No cloud document pipeline.**

[Product overview](docs/overview.md) · [Document import notes](docs/engineering/document-import.md) · [Quality gates](docs/engineering/quality-gates.md)

</div>

---

## A quiet place for sensitive documents

Kaagaz means **paper**. Kaagaz Vault is being built for IDs, prescriptions, bank statements, certificates, receipts, and the other papers that should stay useful without becoming somebody else’s dataset.

Import or scan a document. Keep the encrypted copy on the device. Use local OCR when it helps. Review the extracted text yourself. Search your shelf without turning your private papers into a remote service.

| **CAPTURE** | **UNDERSTAND** | **KEEP CONTROL** |
| --- | --- | --- |
| Android document picker, CameraX capture, bounded input. | Offline OCR in English, Hindi, and Telugu with explicit review. | Encrypted local payload and metadata paths, no cloud OCR, no analytics. |

## What the active source contains

- Android Kotlin + Jetpack Compose application shell with Material 3 theming.
- Android Keystore-backed AES-256 keys and versioned AES-GCM payload encryption.
- App-private encrypted files with opaque IDs, bounded input, atomic pending-file writes, and interrupted-update recovery.
- User-selected PDF/image import without broad storage permission.
- Offline Tesseract OCR for English, Hindi, and Telugu with pinned model integrity checks.
- Human-editable OCR text and review state stored with the encrypted payload.
- SQLCipher-backed metadata search with a local in-memory fallback path.
- User-initiated CameraX capture with in-memory JPEG handling and direct encrypted import.
- Calm motion, restrained haptics, simplified mode, and privacy-safe recent-app surfaces.
- Local encrypted transfer of allowlisted accessibility settings only. Documents, OCR text, reminders, and recordings are not in that transfer surface.

The complete Android implementation lives on the review branch:

```bash
git clone --branch feat/durable-vault-migration-journal https://github.com/D8v1d777/KAAGAZ-VAULT.git
cd KAAGAZ-VAULT
```

## Privacy stays on device

The FOSS core must not add `INTERNET`, analytics, advertising, cloud OCR, hidden network calls, or cloud AI dependencies. Document images, OCR text, filenames derived from OCR, and sensitive metadata must not leave the device in the FOSS build.

OCR is untrusted evidence. It remains editable and reviewable. The app must never turn uncertain medical text into automatic instructions or actions.

This is active development, not a release-ready security product. Auth-bound vault lock, recovery and rotation evidence, physical-device leakage review, and secure deletion expectations still have explicit gates.

## Current progress

| Area | State |
| --- | --- |
| Encryption foundation | Implemented paths and exact-SHA CI evidence |
| Offline OCR | English / Hindi / Telugu path implemented |
| Document import and camera | Implemented path; physical-device evidence still needed |
| Local encrypted search | Implemented path with fallback behavior |
| Auth-bound vault lock | In progress |
| Recovery and rotation | Policy and implementation gates remain |
| Device-level validation | Not yet complete |
| Public release | Not yet |

## Run the checks

**Requirements:** JDK 21 and Android SDK Platform 36.

```bash
python3 -m unittest discover -s scripts -p 'test_*.py'
./gradlew --no-daemon clean assembleDebug testDebugUnitTest lint
python3 scripts/check_offline_manifest.py
```

Read [AGENTS.md](AGENTS.md) before changing code. The repository contract owns the offline, privacy, accessibility, and evidence boundaries.

<div align="center">

<sub>Built with the belief that some things should never leave your pocket.</sub>

</div>
