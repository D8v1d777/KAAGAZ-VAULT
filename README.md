<div align="center">

# KAAGAZ-VAULT

### Privacy-first • Offline-first • Android Document Vault

<br>

<img src="docs/images/gojo-portrait.jpg" alt="The Strongest" width="300"/>

<br>

[![License](https://img.shields.io/badge/License-Apache%202.0-4B8BBE?style=for-the-badge)](LICENSE)
[![Platform](https://img.shields.io/badge/Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com)
[![Branch](https://img.shields.io/badge/branch-feat%2Fdurable--vault--migration--journal-purple?style=for-the-badge)](https://github.com/D8v1d777/KAAGAZ-VAULT/tree/feat/durable-vault-migration-journal)

*Encrypted paper. Local only. Built to be audited.*

</div>

---

## What this branch is

This is the **review source** for the Android app: Gradle project, Compose UI, Keystore crypto, SQLCipher metadata, camera, offline OCR, CI, and policy scripts.

`main` stays a documentation landing page. Work lands here first.

## Implemented so far

- Kotlin + Jetpack Compose app shell
- Android Keystore-backed AES-256 keys and versioned AES-GCM payload encryption
- Encrypted files in app-private storage, opaque IDs, bounded input, atomic pending-file writes
- Document picker import without broad storage permission
- Offline Tesseract OCR (English, Hindi, Telugu) with pinned model integrity checks
- Human-editable OCR text and review state in encrypted payloads
- SQLCipher metadata index, local search, rebuild-from-payload paths
- CameraX capture with in-memory JPEG path into encrypted import
- Bounded PDF text extraction / OCR prototype

These are **implemented paths**, not a release-readiness claim.

## Privacy & security entry points

- [AndroidManifest.xml](app/src/main/AndroidManifest.xml) — CAMERA, POST_NOTIFICATIONS; no INTERNET in source manifest
- [app/build.gradle.kts](app/build.gradle.kts) — minSdk 24, targetSdk 36, pinned OCR hashes
- [Unit tests](app/src/test/java)
- [Android CI](.github/workflows/android-ci.yml)
- Engineering docs under `docs/engineering/`

## Build & test

```bash
python3 -m unittest discover -s scripts -p 'test_*.py'
./gradlew --no-daemon clean assembleDebug assembleRelease testDebugUnitTest lint
python3 scripts/check_offline_manifest.py
```

## Agents & collaborators

See [AGENTS.md](AGENTS.md) for invariants, definition of done, and workflow.

**Raven** operates on gap-closure and visual polish branches (`agent/raven-*`): autonomous commits for docs, scaffolding, and non-crypto presentation work unless the owner gates otherwise.

---

<div align="center">

*Strongest when verified. Soft only where it helps you read.*

<sub>Maintainer identity note: Raven · collaborator on agent/* branches</sub>

</div>
