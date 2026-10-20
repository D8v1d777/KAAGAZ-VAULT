<div align="center">

# KAAGAZ-VAULT

### Privacy-first · Offline-first · Android Document Vault

<br/>

<img src="docs/images/gojo-portrait.jpg" width="280" alt="Gojo — The Strongest"/>

<br/>

[![License](https://img.shields.io/badge/License-Apache%202.0-4B8BBE?style=for-the-badge)](LICENSE)
[![Platform](https://img.shields.io/badge/Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com)
[![Status](https://img.shields.io/badge/Status-Active%20Development-orange?style=for-the-badge)](https://github.com/D8v1d777/KAAGAZ-VAULT)

*Your documents. Your device. No cloud.*

</div>

---

## A quiet place for sensitive paper

KAAGAZ-VAULT is for IDs, prescriptions, bank statements, and the rest of the stack that should never leave your pocket. Encrypted on-device. Searchable offline. FOSS build without document content on the wire.

This repo is **active development**, not a finished product.

<div align="center">
<img src="docs/images/gojo-wide.jpg" width="720" alt="Gojo wide"/>
</div>

---

## Where the real source lives

`main` stays a landing page on purpose.

**Review branch:** [feat/durable-vault-migration-journal](https://github.com/D8v1d777/KAAGAZ-VAULT/tree/feat/durable-vault-migration-journal)

```bash
git clone --branch feat/durable-vault-migration-journal https://github.com/D8v1d777/KAAGAZ-VAULT.git
cd KAAGAZ-VAULT
```

---

## Progress

| Area | Status |
|------|--------|
| Encryption foundation | Ready |
| Offline OCR (EN / HI / TE) | Ready |
| Document import & camera | Ready |
| Local encrypted search | Ready |
| Auth-bound vault lock | In progress |
| Recovery policy | Owner decision |
| Device-level validation | Not yet |
| Public release | Not yet |

<div align="center">
<img src="docs/images/gojo-purple.jpg" width="640" alt="Gojo purple energy" onerror="this.style.display='none'"/>
</div>

<!-- gojo-purple may exist on this branch after assets commit -->

---

## Privacy stance

- No `INTERNET` permission in the FOSS build source manifest
- No analytics / ad SDKs / cloud AI in FOSS
- OCR treated as untrusted evidence — review state stays explicit
- Audit the manifest and dependencies yourself

---

## Build (developers)

JDK 21 · Android SDK platform 36

```bash
python3 -m unittest discover -s scripts -p 'test_*.py'
./gradlew --no-daemon clean assembleDebug testDebugUnitTest lint
python3 scripts/check_offline_manifest.py
```

Engineering contract: [AGENTS.md](AGENTS.md) on the review branch · collaborator note: [docs/RAVEN.md](docs/RAVEN.md)

---

<div align="center">

### Collaborator

**Raven** — autonomous agent on visual polish and gap-closure docs.

*Strongest when verified. Soft only where it helps you read.*

<sub>Apache-2.0 · Active development · Not release-ready</sub>

</div>
