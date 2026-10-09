# Secure Storage & Cryptography Skill

## Purpose
Design and verify at-rest protection for private document images, OCR-derived fields, thumbnails, and vault metadata. Security claims must match the actual implementation and artifacts.

## Required workflow
1. Map every sensitive datum from capture through processing, persistence, indexing, backup, export, deletion, and crash recovery.
2. Define the attacker model and explicitly state what device compromise, an unlocked device, screenshots, rooted devices, backups, and user exports are outside or inside scope.
3. Prefer Android Keystore-backed key encryption/wrapping over embedding keys or deriving an encryption key directly from a user PIN. Keep key-encryption and data-encryption roles distinct.
4. Use a vetted platform cryptographic API and authenticated encryption (AES-GCM where appropriate). Generate a fresh, unpredictable nonce/IV for every encryption under a given key; never reuse a nonce with the same key. Store algorithm/version/nonce/tag metadata needed for decryption, but not key material.
5. Define key creation, rotation, invalidation, biometric/device-credential changes, reinstall, backup/restore, and unrecoverable-key behavior before shipping.
6. Protect files and sensitive metadata consistently. Opaque filenames are not encryption. Avoid plaintext temporary files and unencrypted thumbnails.
7. Inspect SQLite database pages, FTS indexes, WAL/journal files, crash reports, logs, caches, and backups for secondary copies of sensitive values.
8. Test wrong-key failure, ciphertext tampering, truncation, interrupted writes, key invalidation, migration, deletion, and restore behavior.
9. Document residual risks and verify the merged manifest and built APK when making privacy or network claims.

## Hard rules
- Never implement custom cryptographic primitives or hand-roll key management without a reviewed, concrete need.
- Never log OCR text, document content, keys, tokens, or sensitive identifiers.
- Do not claim "end-to-end encrypted", "fully encrypted", or "secure deletion" without a precise threat model and evidence.
- Authentication failure or tampering must fail closed; do not silently return partial plaintext.
- Keep test fixtures synthetic and non-identifying.

## Deliverables
- Data-flow/threat-model update for meaningful changes.
- Tests for cryptographic failure paths, not only happy-path round trips.
- A clear statement of what is and is not encrypted at rest.
