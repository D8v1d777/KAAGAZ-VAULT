## Encrypted Local File Storage — Phase 3

## Status

**Prototype implementation; not yet release-approved.** This phase adds a small encrypted-file persistence boundary. It does not complete the vault's full data protection design.

## Research basis

- Android recommends app-private internal storage for sensitive app-only files: https://developer.android.com/training/data-storage/app-specific
- Android cryptography guidance recommends established JCA algorithms and Android Keystore for keys requiring stronger protection: https://developer.android.com/privacy-and-security/cryptography
- AES-GCM uses an IV and authentication tag; the implementation uses a 12-byte random IV and a 128-bit tag: https://developer.android.com/reference/javax/crypto/spec/GCMParameterSpec
- The existing secure-storage and encrypted-search skills require fail-closed authentication, opaque filenames, no plaintext logs, and explicit review of secondary data copies.

## Design in this slice

- AndroidKeystoreDocumentKeyProvider creates or retrieves a non-exportable 256-bit AES key from Android Keystore.
- AesGcmEnvelope writes a versioned KGVF envelope containing a format version, random 12-byte IV, and AES-GCM ciphertext/tag.
- A caller-provided opaque document UUID is authenticated as additional authenticated data (AAD). Swapping ciphertext between IDs therefore fails authentication.
- EncryptedDocumentStore writes only ciphertext to a same-directory pending file, syncs it, and renames it into place. The pending file never contains plaintext.
- Files use random UUID names and live in a caller-supplied app-private directory. IDs are strictly parsed as UUIDs to prevent path traversal.
- Read rejects malformed, unsupported-version, oversized, truncated, tampered, or wrong-context envelopes rather than returning unauthenticated plaintext.
- Payload/envelope limit is 32 MiB to bound the current byte-array API's memory use.

## Tests added

- AES-GCM round trip.
- Fresh IV produces different ciphertext for repeated identical input.
- Modified ciphertext and wrong AAD fail authentication.
- Truncated/unsupported-version envelopes are rejected.
- Store saves opaque ciphertext, restores the original bytes, deletes records, rejects path traversal/invalid IDs, and fails closed on tampering.

## Important limitations and unresolved risks

- The current store accepts and returns ByteArray, so the 32 MiB limit is an explicit memory/performance trade-off. Before supporting larger multi-page scans, replace this with a reviewed streaming/chunked authenticated format that detects truncation and does not expose partial plaintext.
- JVM unit tests use an in-memory AES key. They do not exercise Android Keystore hardware/provider behavior; add Android instrumentation tests on the minimum and representative API levels.
- Key invalidation, reinstall/restore, rotation, migration, user authentication, app-lock behavior, and recovery are not designed. The provider deliberately fails instead of silently rotating an existing inaccessible key.
- Payload bytes are encrypted; document metadata, thumbnails, OCR text, search indexes, and database sidecars are not implemented in this slice. Do not describe the whole vault as encrypted.
- Deleting a file is not secure erasure on flash storage. The key is shared across files, so deleting one payload does not cryptographically erase its past copies.
- The key is not yet bound to biometric/device authentication. This is encryption-at-rest prototype work, not an app access-control boundary.
- Backup is disabled in the manifest. Recovery and export behavior require separate design.
- The FOSS build must remain offline and must not add INTERNET permission.

## Acceptance gate

- [ ] CI build, JVM tests, lint, and merged-manifest policy all pass on this branch.
- [ ] Android Keystore instrumentation test confirms create/retrieve and round trip on supported API levels.
- [ ] File corruption, wrong context, truncation, and interrupted write paths are verified.
- [ ] Threat model and key lifecycle decisions are reviewed before integrating into the UI.
- [ ] Search/storage ADR-0001 remains Proposed until database/index leakage and library compatibility are tested.

## Source files

- app/src/main/java/com/kaagazvault/security/DocumentKeyProvider.kt
- app/src/main/java/com/kaagazvault/security/AesGcmEnvelope.kt
- app/src/main/java/com/kaagazvault/security/EncryptedDocumentStore.kt
- app/src/test/java/com/kaagazvault/security/AesGcmEnvelopeTest.kt
- app/src/test/java/com/kaagazvault/security/EncryptedDocumentStoreTest.kt
