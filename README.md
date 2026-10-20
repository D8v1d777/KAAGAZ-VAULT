<div align="center">

# KAAGAZ-VAULT

### Privacy-first • Offline-first • Android Document Vault

<br>

<img src="docs/images/gojo-portrait.jpg" alt="The Strongest" width="320" style="border-radius: 12px; box-shadow: 0 8px 24px rgba(0,0,0,0.4);"/>

<br><br>

[![License](https://img.shields.io/badge/License-Apache%202.0-4B8BBE?style=for-the-badge&logo=apache&logoColor=white)](LICENSE)
[![Platform](https://img.shields.io/badge/Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com)
[![Status](https://img.shields.io/badge/Status-Active%20Development-orange?style=for-the-badge)](https://github.com/D8v1d777/KAAGAZ-VAULT)

<br>

*Your documents. Your device. No cloud.*

</div>

---

<br>

### A quiet place for sensitive documents

KAAGAZ-VAULT is being built for people who want to keep IDs, prescriptions, bank statements and personal papers **entirely on their phone** — encrypted, searchable, and offline.

No analytics.  
No hidden network calls.  
No document content leaving the device in the FOSS build.

This repository is still in active development. The core is taking shape, but it is not yet a finished product.

<br>

<p align="center">
  <img src="docs/images/gojo-wide.jpg" alt="Limitless" width="680" style="border-radius: 10px;"/>
</p>

<br>

---

### Where the real code lives

The `main` branch is intentionally kept as a calm landing page.

**The complete Android project is here:**

<p align="center">
  <a href="https://github.com/D8v1d777/KAAGAZ-VAULT/tree/feat/durable-vault-migration-journal">
    <img src="https://img.shields.io/badge/Review%20Source-feat%2Fdurable--vault--migration--journal-4B8BBE?style=for-the-badge" alt="Review Source Branch"/>
  </a>
</p>

```bash
git clone --branch feat/durable-vault-migration-journal https://github.com/D8v1d777/KAAGAZ-VAULT.git
cd KAAGAZ-VAULT
```

<br>

---

### Current Progress

| Area                        | Status              |
|----------------------------|---------------------|
| Encryption foundation      | Ready               |
| Offline OCR (EN / HI / TE) | Ready               |
| Document import & camera   | Ready               |
| Local encrypted search     | Ready               |
| Auth-bound vault lock      | In progress         |
| Recovery policy            | Decision pending    |
| Device-level validation    | Not yet             |
| Public release             | Not yet             |

<br>

---

### Privacy Stance

- No `INTERNET` permission in the FOSS build
- No analytics or advertising SDKs
- Document images and OCR text stay on device
- Government IDs and medical documents treated as sensitive by default
- You can audit the manifest and dependencies yourself

<br>

---

### For Reviewers & Developers

**Requirements:** JDK 21 • Android SDK Platform 36

```bash
python3 -m unittest discover -s scripts -p 'test_*.py'
./gradlew --no-daemon clean assembleDebug testDebugUnitTest lint
python3 scripts/check_offline_manifest.py
```

Full engineering contract and invariants live in [`AGENTS.md`](https://github.com/D8v1d777/KAAGAZ-VAULT/blob/feat/durable-vault-migration-journal/AGENTS.md).

<br>

---

<div align="center">

**Built with the belief that some things should never leave your pocket.**

<br>

<sub>Apache License 2.0 • Active development • Not release-ready</sub>

</div>

---

<div align="center">

### Maintained with intent

**Raven** — autonomous collaborator on this vault.  
Dark theme. Sharp edges. Soft only when it matters.

</div>
