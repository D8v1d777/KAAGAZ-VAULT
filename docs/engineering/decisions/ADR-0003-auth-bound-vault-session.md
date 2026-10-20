# ADR-0003: Authentication-Bound Vault Session and Legacy-Key Migration

- **Status:** Proposed; implementation must not begin until migration and fallback decisions are accepted.
- **Date:** 2026-10-18
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

This ADR remains Proposed until the migration journal/state design, fallback policy, and API compatibility matrix are reviewed. It becomes Accepted only after the project owner agrees to the recovery and biometric/device-credential behavior. A CI pass alone does not prove the lock is secure.

## CO-01 decision packet — owner approval required (Proposed; no selection accepted)

This section presents alternatives for review. It does not change the ADR status, key policy, or runtime behavior. Do not wire the existing key/migration spike until the owner accepts the authenticator, lock, and recovery behavior.

### Verified current implementation boundary

At this repository head, `AuthBoundVaultKey`, `VaultMigrationJournal`, `VaultMigrationCoordinator`, `VaultMigrationBackend`, and `WrappedVaultKeyStore` are not connected to an application session gate. No class in `app/src/main` implements the migration backend. `MainActivity` constructs the document store, database provider, and repository before any authentication flow; the running app therefore must not be represented as biometric-locked or auth-bound. The following labels mark this work as a spike, not as a completed security boundary.

### Authenticator policy alternatives

| Option | Behavior | Advantages | Risks and costs |
|---|---|---|---|
| A — strong biometric only, auth-per-use | Use a per-operation Keystore key requiring `BIOMETRIC_STRONG` and a `Cipher` in `BiometricPrompt.CryptoObject`. | Clear per-use cryptographic authorization; supported API path starts at the current minSdk with the legacy per-use biometric configuration. | Biometric enrollment changes or removing all enrolled biometrics can irreversibly invalidate the wrapping key. No strong biometric means no normal unlock. An independent recovery path is mandatory before this can safely guard user data. |
| B — strong biometric OR device credential, auth-per-use where supported | On API 30+, investigate `setUserAuthenticationParameters(0, AUTH_BIOMETRIC_STRONG | AUTH_DEVICE_CREDENTIAL)` with a CryptoObject-backed per-use operation. On API 24–29 the combined authenticator prompt configuration is unsupported; do not silently switch to a time-window key. | PIN/pattern/password can be an accessibility fallback on supported API levels and, when included in key authorizations, can change biometric-enrollment invalidation behavior. | Requires an explicit compatibility policy for API 24–29, API/device tests, handling lock-screen reset, and an independent recovery path for unsupported APIs and key/alias loss. The API-30+ combination must be verified on the actual supported devices before any support claim. |
| C — independent passphrase/recovery envelope for the same VEK | Keep a separately authenticated recovery envelope which can unwrap the same random VEK without depending on the device's biometric alias. | Can recover after biometric enrollment/key-alias loss or on devices without supported biometrics; preserves offline operation. | Requires a reviewed KDF and encoding, rate-limiting/brute-force threat model, careful secret display/entry, recovery proof, and recovery-material lifecycle. A lost recovery secret can still mean permanent loss. Dependency changes require the repository's G3 review gate. |

**Recommendation for review, not an accepted decision:** do not enable an app lock until a universal, tested recovery path exists. Evaluate Option B on API 30+ only if auth-per-use semantics and CryptoObject behavior are verified; pair it with a separately designed recovery envelope for unsupported APIs, lock-screen reset, and alias loss. Keep API 24–29 fail-closed unless the owner approves and tests a concrete recovery path. Do not downgrade to a time-based key just to make the prompt work.

Android's current primary docs distinguish auth-per-use keys from time-based authorization; they show an auth-per-use key can authorize either strong biometrics or device credential using timeout zero, and document the API-level limitations of combined prompt authenticators. They also document irreversible invalidation conditions. See [Biometric authentication and CryptoObject guidance](https://developer.android.com/identity/sign-in/biometric-auth), [KeyGenParameterSpec.Builder](https://developer.android.com/reference/android/security/keystore/KeyGenParameterSpec.Builder), and [BiometricPrompt.CryptoObject](https://developer.android.com/reference/android/hardware/biometrics/BiometricPrompt.CryptoObject).

### Session and lock policy alternatives

- **Immediate background lock:** lock as soon as the app loses foreground, cancel or drain operations which could publish plaintext, remove sensitive UI state, close database handles and related WAL state, and best-effort clear mutable key buffers. Strongest privacy boundary; system file-picker/camera/prompt lifecycles need explicit behavior and tests.
- **Short inactivity grace:** keep the session unlocked for an owner-selected interval (for example 30–60 seconds) while foregrounded, then lock. Easier for interrupted tasks but expands the time window in which an unattended app remains open. The interval must be explicit and testable, not inferred from OS screen-lock settings.
- **Manual lock only:** simplest workflow, but weak against app switching, accidental abandonment, and process/lifecycle transitions; not recommended as the only policy.

**Recommendation for review:** start every process in LOCKED state; require explicit unlock before constructing/accessing document, reminder, index, OCR, or database paths; lock on explicit request and immediately on leaving the app, with cancellation/drain rules for active work. Any grace period must be explicitly chosen. A boolean on the UI alone is not a session boundary.

### Proposed implementation order after acceptance

1. Approve and test the authenticator/recovery behavior and API matrix.
2. Implement the real, idempotent `VaultMigrationBackend` with crash-point tests; never retire the only readable legacy copy before read-back and DB integrity checks.
3. Add an in-memory `VaultSession` with locked-by-default lifecycle, auth-per-use unwrap, inactivity/background lock, and best-effort cleanup.
4. Require an unlocked session at every document repository, reminder repository, encrypted metadata index, and database provider entry point. Tests must prove every entry point refuses access while locked.
5. Add system-prompt UI only after the session and repository gates exist.

### Owner decision checklist

- Authenticator: [ ] A — strong biometric only; [ ] B — per-use biometric/device credential where supported plus defined fallback; [ ] other: ______.
- Recovery: [ ] independent recovery envelope; [ ] other reviewed and testable recovery design: ______.
- Lock: [ ] immediate background lock; [ ] explicit short inactivity grace (duration): ______.
- API 24–29 behavior: [ ] do not enable vault lock until recovery is available; [ ] other approved and tested policy: ______.
- Accept ADR-0003 only after the recovery semantics, failure matrix, and supported API/device test plan are agreed. This proposal does **not** accept the ADR.
