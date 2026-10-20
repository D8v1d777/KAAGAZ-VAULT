<div align="center">

# KAAGAZ-VAULT

### Privacy-first • Offline-first • Android Document Vault

<img src="docs/images/gojo-portrait.jpg" alt="The Strongest" width="280"/>

[![License](https://img.shields.io/badge/License-Apache%202.0-4B8BBE?style=for-the-badge)](LICENSE)
[![Platform](https://img.shields.io/badge/Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com)

*Encrypted paper. Local only. Built to be audited.*

</div>

---

## What this branch is

Review source for the Android app: Compose UI, Keystore crypto, SQLCipher, camera, offline OCR, CI, policy scripts.

## Implemented so far

- Kotlin + Jetpack Compose app shell
- Keystore-backed AES-256 and versioned AES-GCM payloads
- Opaque IDs, atomic pending-file writes, document picker import
- Offline Tesseract OCR (English, Hindi, Telugu)
- SQLCipher metadata index and local search
- CameraX capture path into encrypted import
- Bounded PDF extraction / OCR prototype

## Privacy entry points

- [AndroidManifest.xml](app/src/main/AndroidManifest.xml)
- [app/build.gradle.kts](app/build.gradle.kts)
- [Unit tests](app/src/test/java)
- [Android CI](.github/workflows/android-ci.yml)

## Agents

See [AGENTS.md](AGENTS.md).


---

<div align="center">

<img src="docs/images/gojo-wide.jpg" alt="Limitless" width="640"/>

*Strongest when verified.*

</div>
