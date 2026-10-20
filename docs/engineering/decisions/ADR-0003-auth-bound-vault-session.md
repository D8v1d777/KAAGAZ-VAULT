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


---

## Owner decision memo — authenticator, lock, and recovery policy (CO-01 / CO-02)

- **Status:** Proposed decision request only. ADR-0003 remains **Proposed**. No runtime implementation or key-policy change is authorized by this memo.
- **Decision requested:** Select one primary unlock policy, one lock policy, and one independent recovery policy. The owner must approve the combined choice before any repository/session wiring begins.
- **Current source evidence:** `AuthBoundVaultKey.kt` requires authentication for every key use, permits `AUTH_BIOMETRIC_STRONG` only on API 30+, uses the legacy per-use biometric path on earlier APIs, and calls `setInvalidatedByBiometricEnrollment(true)`. The migration coordinator, journal, and wrapped-key store remain unwired according to CO-01's source audit. A BiometricPrompt alone would not gate repositories.

### A. Primary unlock options

| Option | Behavior | Benefits | Failure / compatibility cost |
|---|---|---|---|
| **A — Strong biometric only** | Keep auth-per-use strong-biometric key; enrollment changes may invalidate the key. | Narrow authenticator set; clear biometric confirmation. | Users without an enrolled strong biometric cannot unlock; adding/removing biometrics can force recovery; not acceptable without an independently tested recovery route. |
| **B — Strong biometric OR device credential** | Auth-per-use key permits a strong biometric or the device PIN/pattern/password, subject to supported Android/API and AndroidX Biometric combinations. | Accessible fallback; Android Keystore documents that including `AUTH_DEVICE_CREDENTIAL` changes the biometric-enrollment invalidation behavior. | Must validate `CryptoObject` support and prompt behavior on every supported API. Secure-lock-screen removal/reset can still invalidate keys. Device credential is not an independent recovery route for lost app data or lost device. |
| **C — Passphrase unlock envelope** | Keep a VEK envelope wrapped by a separately entered passphrase-derived key, using a reviewed KDF and explicit throttling/parameter policy. | Can work without biometrics and can be independent of device biometric enrollment. | Requires an approved KDF, parameters, dependency/implementation review, offline guessing threat model, passphrase UX, and recovery testing. A forgotten passphrase is unrecoverable unless another recovery path exists. Not ready to implement by guesswork. |

**Recommendation for owner review:** Option B on API levels where the auth-per-use `CryptoObject` flow is verified, with a separately approved recovery mechanism that is not tied solely to the same device's biometric enrollment or Keystore alias. For older supported API levels, do not silently downgrade; use a reviewed compatible path or block vault setup with a clear explanation. This is a recommendation, not an accepted decision. If a consistent device-credential-plus-`CryptoObject` path cannot be proven on the declared minimum API, return to the owner with the compatibility matrix rather than weakening authentication.

### B. Independent recovery options

| Option | Recovery material | Main risk / user obligation |
|---|---|---|
| **R1 — One-time recovery key** | Generate a high-entropy recovery secret and use it to protect a second VEK envelope. Show it once, require confirmation that it was saved, never log or sync it, and provide an explicit recovery flow. | If lost, and the primary key becomes unavailable, data may be unrecoverable. Display/copy/screenshot exposure must be controlled. Define encoding, entropy, envelope format, retry limits, and whether the recovery envelope survives app-data loss before implementation. |
| **R2 — Device-credential-capable key only** | Permit device credential as an authenticator for the auth-bound Keystore key. | Improves enrollment/no-biometric accessibility on compatible APIs but does not recover from Keystore alias loss, device loss, app-data deletion, or every lock-screen reset. This alone is not a complete recovery plan. |
| **R3 — Passphrase-wrapped second VEK envelope** | Protect a second copy of the VEK using a user passphrase and a reviewed password KDF. | Depends on passphrase strength, KDF cost and implementation, retry throttling, safe envelope storage/export, and the user remembering the passphrase. A KDF dependency/version is a separate approval gate. |

**Recommendation for owner review:** R1 as the independent recovery route, alongside Option B where supported. Do not ship until the recovery secret's format, generation, confirmation, storage/export, throttling, envelope authentication, and recovery UX are specified and tested. If the owner prefers R3, first prepare a separate KDF dependency and parameter memo; do not select a KDF in this change. R2 alone is insufficient as the recovery plan.

### C. Lock/session policy options

| Option | Rule | Trade-off |
|---|---|---|
| **L1 — Background lock** | Lock whenever the app leaves the foreground, plus explicit lock and process-death handling. Model system authentication/document-picker transitions explicitly. | Strongest short exposure window; may interrupt legitimate flows if lifecycle transitions are mishandled. |
| **L2 — Inactivity-only** | Lock after a selected idle timeout (e.g. 1, 5, or 15 minutes), even if the app remains foregrounded. | Less interruption; leaves sensitive state available longer and does not by itself protect app-switcher snapshots. |
| **L3 — Hybrid (recommended)** | Explicit lock; lock on background except narrowly modeled system UI transitions; 5-minute inactivity timeout; process death starts locked. Stop accepting new work on lock, cancel/drain in-flight work, close database handles/WAL as designed, clear sensitive UI state, and best-effort clear mutable key buffers. | Balances protection and usability, but needs lifecycle/crash tests proving no repository call returns plaintext after lock. |

No option may treat `FLAG_SECURE`, UI hiding, or a boolean as the security boundary. The actual boundary is a session gate around every repository/database/index/reminder entry point and cryptographic use.

### D. Failure matrix — required behavior regardless of selected option

| Event | Required behavior | Forbidden behavior |
|---|---|---|
| No enrolled strong biometric | Offer only the owner-approved supported fallback; otherwise explain setup cannot proceed. | Create an unauthenticated key or silently weaken the policy. |
| Biometric enrolled/removed | Follow the selected key policy; if the key is invalidated, enter recovery-required state. | Delete ciphertext, regenerate a replacement VEK, or report an empty vault. |
| Secure lock screen removed/reset | Fail closed; offer the independent recovery path if available. | Silently recreate the wrapping alias. |
| Keystore alias missing or permanently invalidated | Keep vault locked and show recovery options; preserve files and database. | Replace the alias or discard protected data automatically. |
| Recovery secret lost or mistyped | Remain locked; rate-limit attempts and provide no secret-dependent detail. | Bypass authentication or delete data to restore usability. |
| Wrapped envelope truncated, tampered, wrong version, or wrong key | Return a typed recovery/authentication failure; preserve all candidate data. | Return partial VEK/plaintext or silently start a new vault. |
| Process death during migration | Restart locked; validate the durable journal and file states; resume idempotently or enter recovery-required state. | Infer completion from file presence alone or retire the last readable copy. |
| Lock while import/OCR/search/reminder/database work is active | Prevent new work, cancel or drain existing work, close handles, and prove no plaintext is returned after lock. | Leave a background job, cache, database handle, or callback with continued access to plaintext. |
| App data cleared/reinstall/device lost | Recovery succeeds only if the approved recovery material and required encrypted data are available. State the boundary plainly to the user. | Promise recovery from the Keystore alone or claim cloud backup exists. |

### E. Proposed user-facing copy (not yet shipped)

**Before enabling vault lock**

> **Protect access to your vault**  
> Kaagaz Vault will ask you to authenticate before opening protected documents. If your phone's security settings change, your authentication key may become unavailable. Set up and verify your recovery method before storing important documents. Kaagaz Vault cannot recover documents if both the vault key and your recovery method are lost.

**Recovery material confirmation**

> **Save your recovery key now**  
> This key is shown only during setup. Keep it somewhere private and separate from this phone. Anyone who obtains it may be able to recover your vault. If you lose it and your phone's vault key becomes unavailable, your documents may be permanently inaccessible.

**Key unavailable**

> **Your vault is locked**  
> The saved authentication key is unavailable. Your documents have not been deleted. Use your configured recovery method to restore access. Do not uninstall the app or clear its data until recovery is complete.

**Recovery unavailable**

> **Recovery is not available**  
> Kaagaz Vault cannot unlock this vault with the available keys. The app will not create a replacement key because that would not decrypt your existing documents. Keep the app data and encrypted files intact while you check your saved recovery material.

Copy must be localized, accessible at large text sizes, and reviewed for the actual recovery behavior before shipping.

### F. Implementation gates after owner decision

1. Record the selected primary authenticator, minimum API compatibility, lock policy, recovery route, and loss scenarios here; change ADR status only after explicit owner acceptance.
2. Add the exact API compatibility matrix and instrumented tests for key creation, auth-per-use `CryptoObject` success/cancel/failure, biometric enrollment changes where supported, no biometric enrollment, credential reset, alias loss with ciphertext present, process restart, and recovery.
3. Design and test the recovery envelope before connecting a production session. No production code may generate/delete keys if ciphertext, database, wrapped key, or journal may already exist.
4. Implement one PR per slice: migration backend/crash-point tests; in-memory VaultSession and lock semantics; repository/database/index/reminder gating; then BiometricPrompt UI.
5. Independent security review and exact-SHA CI are mandatory. JVM fakes do not verify Android Keystore or BiometricPrompt behavior.

### Research basis

- Android Developers, [BiometricPrompt and CryptoObject](https://developer.android.com/identity/sign-in/biometric-auth): documents authenticator combinations, API compatibility notes, and auth-per-use flows.
- Android API reference, [KeyGenParameterSpec.Builder](https://developer.android.com/reference/kotlin/android/security/keystore/KeyGenParameterSpec.Builder): `setUserAuthenticationParameters` is API 30+; authentication types include `AUTH_BIOMETRIC_STRONG` and `AUTH_DEVICE_CREDENTIAL`; key invalidation behavior depends on authenticator configuration.
- Android API reference, [KeyPermanentlyInvalidatedException](https://developer.android.com/reference/android/security/keystore/KeyPermanentlyInvalidatedException): describes permanent key invalidation cases.
- OWASP, [Key Management Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Key_Management_Cheat_Sheet.html) and [Cryptographic Storage Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Cryptographic_Storage_Cheat_Sheet.html): key lifecycle, recovery, and authenticated-storage principles.

**Owner decision required:** choose A/B/C, L1/L2/L3, and R1/R2/R3. Until that answer is recorded, implementation remains blocked and ADR-0003 remains Proposed.
