# KAAGAZ-VAULT

**Privacy-first • Offline-first • Android document vault**

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android-green.svg)](https://developer.android.com)
[![Status](https://img.shields.io/badge/Status-Active%20Development-orange.svg)](https://github.com/D8v1d777/KAAGAZ-VAULT)

> Capture, encrypt, search, and organize personal documents on your device.  
> No cloud. No analytics. No document content ever leaves the phone in the FOSS build.

---

## Why KAAGAZ-VAULT?

Most document scanners and "vault" apps quietly send your IDs, prescriptions, and bank statements to servers.  
KAAGAZ-VAULT is built on the opposite principle:

- **Everything stays on the device**
- Strong encryption with Android Keystore
- Offline OCR (English, Hindi, Telugu)
- Open source so you can verify the claims

This is still **active development**, not a finished release.

---

## Current Status

| Area                    | Status          | Notes                                      |
|-------------------------|-----------------|--------------------------------------------|
| Core encryption         | Implemented     | AES-GCM + Android Keystore                 |
| Offline OCR             | Implemented     | Tesseract, pinned models                   |
| Document import & camera| Implemented     | Document picker + CameraX                  |
| Local search            | Implemented     | SQLCipher-backed metadata                  |
| Auth-bound vault lock   | In progress     | Primitive ready, session gating next       |
| Recovery policy         | Decision needed | Owner decision required (CO-01 / CO-02)    |
| Instrumentation tests   | Scaffolded      | Real device runs still pending             |
| Release readiness       | Not yet         | No physical-device validation claimed      |

---

## Where is the real source code?

The default branch (`main`) is a documentation landing page.

**→ Review the actual Android project here:**  
[`feat/durable-vault-migration-journal`](https://github.com/D8v1d777/KAAGAZ-VAULT/tree/feat/durable-vault-migration-journal)

```bash
git clone --branch feat/durable-vault-migration-journal https://github.com/D8v1d777/KAAGAZ-VAULT.git
cd KAAGAZ-VAULT
```

---

## Privacy Promise (FOSS build)

- No `INTERNET` permission
- No analytics or ad SDKs
- No cloud AI
- Document images, OCR text, and derived metadata stay on device
- Government IDs and medical documents treated as sensitive by default

You can verify this yourself by inspecting the manifest and dependencies.

---

## Quick Start (Developers)

**Requirements**
- JDK 21
- Android SDK (platform 36 + build-tools 36.0.0)

```bash
# Unit tests + policy checks
python3 -m unittest discover -s scripts -p 'test_*.py'
./gradlew --no-daemon clean assembleDebug testDebugUnitTest lint
python3 scripts/check_offline_manifest.py
```

---

## Architecture Highlights

- **Kotlin + Jetpack Compose**
- **Android Keystore** for non-exportable keys
- **AES-256-GCM** for document payloads
- **SQLCipher** for searchable metadata
- **Opaque IDs** — filenames never leak content
- Clear separation between UI, domain, crypto, and storage

See [`AGENTS.md`](AGENTS.md) for the full engineering contract and invariants.

---

## Contributing / Reviewing

1. Read the engineering contract in `AGENTS.md`
2. Prefer small, reviewable PRs
3. Never commit real documents, Aadhaar/PAN images, or secrets
4. All security-sensitive changes require explicit evidence

Open draft PRs are currently focused on correction orders (CO-00 → CO-15) and auth/recovery decisions.

---

## License

Licensed under the [Apache License 2.0](LICENSE).

---

**Built with the belief that your documents should never leave your pocket.**
