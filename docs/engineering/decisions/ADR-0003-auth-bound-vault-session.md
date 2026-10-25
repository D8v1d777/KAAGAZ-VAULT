# ADR-0003: Authentication-Bound Vault Session and Legacy-Key Migration

- **Status:** Accepted by the project owner for the policy direction below; implementation remains gated by the verification requirements in this ADR.
- **Original date:** 2026-10-18
- **Accepted:** 2026-10-20 (owner approval in ChatGPT conversation)
- **Scope:** Native Android offline-first vault.
- **Security classification:** Architecture decision, not security verification.

## Context

The current document payload store encrypts each payload directly with a non-exportable Android Keystore AES-GCM key. The SQLCipher database uses a separate random 256-bit database key, stored in AES-GCM-wrapped form under another Keystore key. The database and repository are currently constructed before any authentication gate; the UI immediately lists documents. Therefore, adding only a BiometricPrompt would be cosmetic and would not prevent the existing repository from decrypting data.

The existing document encryption key cannot be exported and simply re-wrapped: its ciphertext must be decrypted using the legacy key and re-encrypted under a new data-key design. The SQLCipher key is available to the database key manager as bytes and can be re-wrapped, but database handles, caches, and WAL state must also be closed/managed across lock transitions.

## Decision direction

Use an explicit in-memory vault session whose lifetime is controlled by authentication and app lifecycle, rather than a boolean UI flag.

1. Generate a cryptographically random 256-bit Vault Encryption Key (VEK) for the new format.
2. Protect the persisted VEK using an Android Keystore AES-GCM key configured for authentication-per-use. The unlock path initializes a decrypting Cipher and passes that Cipher through AndroidX BiometricPrompt.CryptoObject; only a successful cryptographic operation yields the VEK.
3. Keep versioned key-envelope metadata and authenticated context. Bound and validate every stored envelope before allocation/decryption. Do not log keys, plaintext, OCR, names, or decrypted metadata.
4. Use the unlocked VEK for document payload encryption and for wrapping the random SQLCipher key. Do not silently create replacement VEK/database/document keys if corresponding ciphertext or a migration journal already exists.
5. Re-encrypt existing document payloads under the new key using a crash-recoverable per-document migration journal. Preserve opaque document IDs and metadata relationships. Never delete the only readable legacy copy until the new ciphertext has been read back and authenticated.
6. Re-wrap the SQLCipher key under the VEK only after the VEK is available; close database handles before changing key wrapping. Retain a recoverable legacy wrapper until the migrated database opens and integrity checks pass.
7. Route every repository action through an unlocked session. A lock transition must stop new work, cancel or drain queued work, close database handles, remove sensitive UI state, and best-effort clear mutable in-memory key buffers.
8. Lock on backgrounding according to a documented lifecycle policy, on explicit user action, and after an inactivity timeout. Handle process death as locked by default. Use FLAG_SECURE for sensitive screens where appropriate, but do not treat screenshot protection as a substitute for cryptographic locking.

## Accepted owner policy (2026-10-20)

The project owner explicitly approved the recommended bundle:

- **Authentication A2:** strong biometric OR secure device credential, but only on Android/API combinations where the authenticated cryptographic operation is supported and tested. Keep the unlock path tied to the same auth-per-use `Cipher` delivered through `BiometricPrompt.CryptoObject`; no UI-only success state.
- **Recovery B2:** a separately generated 256-bit random recovery key, held by the owner and never uploaded or stored as a plaintext app-side copy. Persist a separately versioned, authenticated recovery envelope for the VEK. Display the recovery material once during setup and require the owner to confirm safe storage before setup is considered complete. A lost recovery key can still mean permanent data loss and must be explained plainly.
- **Locking C1:** lock immediately on explicit lock, screen-off, app foreground loss and process restart. Do not add an inactivity/grace-period setting unless the owner separately approves it. Lock completion must prevent any in-flight repository operation from publishing plaintext after the locked transition completes.
- **Migration preservation:** never replace a key or discard legacy ciphertext merely because unlock or migration failed. Keep legacy copies and wrappers until authenticated read-back, SQLCipher reopen/integrity verification, and durable journal completion all pass.

### Android compatibility constraint

The supported AndroidX Biometric API documentation reports that `BIOMETRIC_STRONG | DEVICE_CREDENTIAL` is unsupported by `PromptInfo.Builder.setAllowedAuthenticators` on API 28–29, and device-credential-only is unsupported before API 30. Therefore A2 is conditional, not blanket support across minSdk 24. For combinations where device-credential plus auth-per-use `CryptoObject` cannot be proven, keep a supported biometric-only auth-per-use path and the independent recovery route; do not present an unsupported credential fallback, introduce a time-window key, or imply that recovery is equivalent to ordinary unlock. Confirm the precise API 24–29 path using the selected AndroidX version and instrumented tests before production wiring.

The AndroidX Biometric library dependency is not currently in the version catalog. Adding it and any other dependency is separately gated by the repository's dependency-approval rule; the implementation must prepare the version/maintenance/license/privacy justification and obtain the required approval before changing Gradle dependency declarations.

This approval accepts the architecture policy direction only. It does **not** satisfy CI, Android Keystore instrumentation, migration crash tests, independent security review or device acceptance. ADR acceptance does not permit marking the feature complete ahead of those gates.

## Authentication and accessibility gates

- Confirm minimum supported Android versions and available AndroidX Biometric APIs before choosing the final authenticator combination.
- Define a deliberate fallback for users without enrolled strong biometrics: device credential if it can be safely combined with auth-per-use CryptoObject on the supported API matrix, otherwise a separately designed recovery path. Never silently fall back to an unauthenticated key.
- Explain consequences of biometric enrollment changes and Keystore invalidation in plain language before enabling the lock.
- Test cancel, failed authentication, lockout, missing enrollment, device credential, biometric enrollment changes, key invalidation, process death, app background/foreground, and device restart.
- Provide readable system-authentication instructions and a recovery path; do not make accessibility or large-text support contingent on an app-specific visual puzzle.

## Migration protocol

The migration must be resumable and idempotent:
1. Read and validate a durable migration journal before any key creation.
2. Create the new VEK envelope only if no new-format data or migration journal already exists.
3. Record a migration epoch, old/new format versions, and per-document states; never store keys or document names in the journal.
4. For each document, authenticate/decrypt the legacy payload, encrypt it under the VEK with the same opaque ID as authenticated context, write to a ciphertext-only temporary file, fsync, and read back/decrypt to verify.
5. Atomically publish the new file and record completion. On restart, determine state from journal plus file validity; never guess from file presence alone.
6. Migrate the SQLCipher key wrapper only after all document files are verified. Close and reopen SQLCipher, run the supported integrity check, and validate metadata/search reconstruction.
7. Mark migration complete durably; only then retire legacy wrappers and ciphertext. If any step fails, preserve both readable recovery paths and show a safe, actionable error.

## Non-goals and prohibited shortcuts

- A biometric dialog that only toggles UI state.
- Reusing the legacy non-exportable document key as if it could be exported or wrapped.
- Moving plaintext documents to external/shared storage during migration.
- Uploading files, OCR, keys, or telemetry; adding INTERNET permission; remote recovery/cloud sync.
- Deleting legacy keys before successful authenticated read-back and database validation.
- Claiming end-to-end encryption, secure deletion, or protection against rooted/compromised devices without separate threat-model and verification evidence.

## Verification gates

- Pure JVM tests for envelope parsing, authentication context, malformed sizes/versions, migration state transitions, and every crash point.
- Instrumented tests for Android Keystore auth-per-use key generation and BiometricPrompt CryptoObject unlock on supported API levels.
- Instrumented migration tests with synthetic documents, corrupted ciphertext, missing/invalidated aliases, interrupted writes, full storage, database WAL/sidecars, and process restart.
- Test lock behavior while import/OCR/search is in progress and prove no post-lock repository operation returns plaintext.
- Confirm no INTERNET permission, no telemetry/ad SDK, no plaintext external writes, backup disabled, and no sensitive values in logs.
- Independent security review and representative physical-device testing before release claims.

## Research references

- Android Developers, [BiometricPrompt and CryptoObject authentication](https://developer.android.com/identity/sign-in/biometric-auth)
- Android API reference, [BiometricPrompt.CryptoObject](https://developer.android.com/reference/kotlin/android/hardware/biometrics/BiometricPrompt.CryptoObject)
- Android Developers, [Android Keystore system](https://developer.android.com/privacy-and-security/keystore)
- OWASP, [Key Management Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Key_Management_Cheat_Sheet.html)
- OWASP, [Cryptographic Storage Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Cryptographic_Storage_Cheat_Sheet.html)

## Acceptance

This ADR is Accepted for the policy direction recorded above after explicit owner approval. The implementation remains incomplete until the API compatibility matrix, key/recovery-envelope design, crash-point tests, Android Keystore instrumentation, exact-head CI, independent security review, and representative device testing all pass. A CI pass alone does not prove the lock is secure.
