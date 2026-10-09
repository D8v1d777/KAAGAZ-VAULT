---
name: privacy-security
description: Threat-model and implement local-first storage, encryption, biometric gating, sensitive previews, export, and network minimization for KAAGAZ VAULT.
---

# Privacy and Security Skill

## Threat model first
Consider a lost/unlocked device, another app, backups, app-switcher snapshots, logs/crash reports, malicious imported files, rooted devices, accidental export, and future maintenance mistakes. Document what is and is not protected; do not promise protection against a fully compromised OS.

## Storage and cryptography
- Use Android Keystore to protect key-encryption keys where supported; select vetted cryptographic primitives and libraries rather than inventing cryptography.
- AES-GCM requires a unique nonce/IV per encryption under a given key. Store nonce, version, and authentication tag safely; validate authentication on decrypt.
- Define key generation, wrapping, rotation, recovery, biometric changes, backup exclusion, and data deletion behavior before claiming end-to-end protection.
- Encrypt document bytes and sensitive metadata at rest, not only thumbnails. Ensure SQLite/FTS does not unintentionally persist plaintext OCR or government identifiers.
- Consider encrypted database/FTS design explicitly; a claim of “AES-256 per page” is not satisfied by encrypting image files while leaving OCR text searchable in plaintext.
- Use random opaque filenames and internal app storage. Never place OCR text or personal identifiers in paths.
- Avoid secrets and document contents in logs, exceptions, analytics, crash reports, notifications, and test fixtures.

## App surfaces and permissions
- Apply FLAG_SECURE to sensitive screens where appropriate; do not rely on it as a substitute for storage encryption.
- Redact sensitive previews in the recent-apps snapshot and use generic notifications that do not expose document content on a lock screen.
- Use biometric/device-credential prompts as an access gate, with clear fallback and lockout behavior. Biometrics are not themselves an encryption algorithm.
- Request minimum permissions. Use Storage Access Framework for user-selected import/export destinations.
- For FOSS builds, verify the merged manifest and final APK have no INTERNET permission and inspect dependencies for hidden network-capable code. A manifest check alone does not prove absence of all network behavior.
- Treat imported PDFs/images as untrusted input; constrain size, handle malformed content, and avoid unsafe temporary-file exposure.

## Security verification
- Write tests for wrong keys, corrupted ciphertext, modified tags, nonce handling, lock state, deletion, process restart, and export boundaries.
- Review dependency licenses, update status, transitive dependencies, and permissions.
- Keep security claims proportional to evidence. Record residual risks and limitations.
