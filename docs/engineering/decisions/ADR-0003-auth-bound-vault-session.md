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

## Proposed decision memo — owner decision required (2026-10-20)

This section is a decision packet only. ADR-0003 remains **Proposed**. No runtime wiring, key-policy change, alias change, envelope-format change, migration, or dependency change is authorized by this memo.

### Decision 1: authenticator and recovery policy

**Option A — strong biometric only, plus a one-time recovery key envelope**
- Advantages: narrow primary authenticator; auth-per-use CryptoObject path.
- Costs: no strong biometric means setup cannot proceed; biometric enrollment changes can invalidate a biometric-only key; the recovery key becomes essential for those cases.
- Failure boundary: if the Keystore key is invalidated and the recovery key is lost, the vault is unrecoverable.

**Option B — strong biometric OR device credential, plus a one-time recovery key envelope (recommended for owner consideration)**
- Advantages: better accessibility and resilience when biometric enrollment changes; Android's current documentation shows auth-per-use keys configured with AUTH_BIOMETRIC_STRONG | AUTH_DEVICE_CREDENTIAL and describes device credential as a supported authenticator for such keys.
- Costs: the app's minSdk is 24, while the current implementation uses setUserAuthenticationParameters on API 30+. The API 24–29 compatibility path and OEM behavior must be specified and tested before implementation; do not claim the same behavior across the entire supported range yet.
- Important implementation constraint: the current AuthBoundVaultKey explicitly calls setInvalidatedByBiometricEnrollment(true). Adding AUTH_DEVICE_CREDENTIAL alone must not be assumed to override that explicit invalidation setting. The exact setting must be deliberately reviewed and instrumented-tested; changing it is gated by root G2.
- Recovery key: generate a high-entropy key locally, show it once, require a confirmation step, and use it only to unwrap the VEK envelope if the primary Keystore path is unavailable. No server-side recovery.
- Failure boundary: disabling/resetting the device secure lock screen can still invalidate an auth-bound key; if that happens and the recovery key is lost, the vault is unrecoverable.

**Option C — passphrase-wrapped second VEK envelope**
- Advantages: independent of biometric enrollment and device credential; usable where no strong biometric is enrolled.
- Costs: requires a separately reviewed password KDF, salt/parameters, offline brute-force threat model, retry UX, and forgotten-passphrase policy. This adds a cryptographic policy/format decision and must remain gated; no KDF or dependency is introduced in this PR.
- Failure boundary: a forgotten passphrase with no other valid envelope leaves the VEK unavailable.

**Recommendation:** choose Option B only if the API 24–29 compatibility matrix can provide a tested, fail-closed path; otherwise do not enable the new lock on unsupported combinations until the owner chooses a compatible fallback. Pair the primary path with the one-time recovery-key envelope so a biometric or secure-lock-screen change does not become silent permanent data loss. This is a recommendation, not an accepted policy.

### Decision 2: lock lifecycle

Recommended default:
- Start every process in the locked state.
- Lock immediately when the app enters the background, on explicit user lock, and after five minutes of foreground inactivity.
- On lock, stop new repository work, cancel or drain in-flight work, close database handles, clear sensitive UI state and search results, and zero mutable in-memory VEK buffers.
- A cancelled/failed prompt, lockout, missing key, invalidated alias, or failed recovery keeps the vault locked. Never create a replacement VEK when protected data already exists.
- Use FLAG_SECURE for sensitive surfaces as defense in depth, not as a substitute for the cryptographic session gate.
- Test the document picker, camera, process death, background/foreground, and in-flight OCR/search so lifecycle transitions cannot return plaintext after lock.

### Failure matrix

| Condition | Required behavior | Data-loss boundary |
|---|---|---|
| Strong biometric available; authentication succeeds | Unwrap VEK through auth-per-use CryptoObject; enter unlocked session | None expected; verify on supported API/device matrix |
| No strong biometric, device credential available | Use device-credential path only where the tested API combination supports it; otherwise offer the approved recovery path | Do not silently downgrade to an unauthenticated key |
| Biometric enrollment changes | Option A may invalidate the primary key; Option B is intended to avoid that invalidation when device credential is configured, but must be instrumented-tested | Recovery envelope required if primary alias is invalidated |
| Device lock screen disabled or forcibly reset | Treat Keystore invalidation as a locked/recovery state | If primary alias and recovery key are both unavailable, data is unrecoverable |
| User cancels, fails, or is locked out | Remain locked; expose no document/OCR/metadata plaintext | No data loss |
| Process dies or app backgrounds | Start/return locked; clear visible sensitive state and close database handles | No data loss |
| Key alias missing/invalidated while ciphertext exists | Fail closed; offer only approved recovery; never generate a replacement key | No recovery envelope means permanent unreadability |
| Recovery key/passphrase is lost and primary key is invalid | Do not bypass protection or manufacture a new VEK | Permanent unreadability is explicit and user-visible |
| Journal, ciphertext, or migration read-back is invalid | Stop migration; preserve every last authenticated readable copy; show actionable failure | Never delete the only readable copy |

### User-facing copy (draft)

**Before enabling vault lock**
“Your vault will require device authentication to open. Save the recovery key somewhere private and separate from this device. We cannot reset it or recover your documents from a server. If both device access and the recovery key are lost, your vault may be permanently unreadable.”

**Recovery**
“Your vault could not be opened with this device’s authentication key. No documents were replaced or deleted. Use your saved recovery key to restore access. Do not uninstall the app or clear its data while recovery is unresolved.”

**No supported authenticator**
“This device does not currently provide a supported unlock method for vault lock. Your existing encrypted files have not been changed. Set up a supported device credential or use the approved recovery method; the app will not create a replacement vault key.”

### Owner decision required

Please choose:
1. **Authenticator:** A (strong biometric only), B (strong biometric + device credential), or C (passphrase-wrapped second envelope).
2. **Recovery:** one-time high-entropy recovery key, device-credential-capable primary key only, or passphrase-wrapped second envelope.
3. **Lock lifecycle:** accept the recommended immediate-background + explicit-lock + five-minute foreground-inactivity policy, or specify a different policy.

Do not change ADR status to Accepted until these choices and the API compatibility matrix are reviewed.

## CO-02 recovery design proposal — not accepted

This section extends the CO-01 decision packet. It is design only. The recovery envelope, key lifecycle, and instrumented test infrastructure remain unimplemented and G2-gated. ADR-0003 remains Proposed.

### Recovery-key envelope candidate

If the owner selects a one-time recovery key:
1. Generate 256 bits of random recovery material locally using a cryptographically secure random source. Treat it as a high-entropy key, not a user-chosen password; do not derive it from a PIN or phrase.
2. Create a second, versioned AES-GCM envelope for the VEK using a fresh 12-byte IV and authenticated context that binds the envelope to this vault. Reuse only the project's reviewed AES-GCM envelope implementation; any envelope schema or key hierarchy change requires explicit G2 approval before code.
3. Store the recovery envelope only in app-private no-backup storage. It contains wrapped VEK bytes only, never the recovery key itself, document names, OCR text, or plaintext.
4. Display the recovery key once during setup, with copy/export only on explicit user intent, no logs or analytics, and a confirmation step that proves the user saved it. Do not persist the recovery key inside the app.
5. Recovery requires the user to enter/provide the recovery key, authenticate the envelope, unwrap the existing VEK, and only then establish a replacement Keystore wrapper. Never generate a replacement VEK or re-encrypt documents merely because the primary alias is missing.
6. Preserve the existing ciphertext and legacy recovery material until the recovered VEK can open representative documents and the SQLCipher database integrity check succeeds. Recovery failure must leave stored bytes unchanged.

The exact textual encoding, checksum, copy/export UX, envelope version, AAD label, and post-recovery key-wrapper rotation remain Proposed details; do not implement them before the owner accepts the design.

### Required failure behavior

| Condition | Required behavior | Recovery/data-loss boundary |
|---|---|---|
| No strong biometric enrolled | Option B may use device credential only on API combinations validated by the compatibility matrix; otherwise setup must stop safely or use an owner-approved alternate path | Never silently downgrade to an unauthenticated key |
| Biometric added/removed | Test the accepted key policy; if the primary alias is invalidated, enter recovery state and preserve ciphertext | Recovery envelope is required if primary alias is unavailable |
| Secure lock screen disabled/reset | Treat key invalidation as recovery-required; do not regenerate alias while protected data exists | If primary and recovery credentials are both lost, data is unrecoverable |
| Primary alias missing, ciphertext present | Keep vault locked and show recovery instructions; do not create a new VEK | Recovery only succeeds if an approved second envelope is available |
| Wrong recovery key or corrupted recovery envelope | Authentication fails; remain locked; do not overwrite envelope or ciphertext | No plaintext is released |
| Recovery key lost, primary key still works | Keep primary unlock working; warn that recovery coverage is absent and require user to re-establish a recovery method before claiming setup complete | Future alias loss could become permanent |
| Recovery key and primary key both lost | Explain that no valid decryption path remains; preserve ciphertext; do not promise recovery | Permanent unreadability |
| Process restart during recovery | Start locked; resume only from validated envelope state; do not infer success from file presence | Preserve the last authenticated readable copy |
| Disk-full, partial write, or failed read-back | Abort and preserve previous valid envelopes/ciphertext; report failure | Never delete the only readable copy |

### User-facing recovery copy

**Recovery key setup**
“Save this recovery key somewhere private and separate from this device. It will be shown only once. We do not upload it or keep a copy. If you lose it and this device's unlock key becomes unavailable, your vault may be permanently unreadable.”

**Recovery required**
“This device can no longer use its vault unlock key. Your encrypted documents have not been replaced or deleted. Use the recovery key you saved during setup. Do not uninstall the app or clear its data while recovery is unresolved.”

**Recovery failed**
“The recovery key could not unlock this vault. No documents were changed. Check the key and try again. The app will not create a replacement vault key or erase existing files.”

### Instrumented verification gate after owner acceptance

Add an androidTest source set and emulator CI job only after the policy is accepted. Pin every third-party workflow action to a full commit SHA. On the exact tested SHA, cover:
- Auth-per-use Keystore wrapping-key creation and BiometricPrompt CryptoObject unlock.
- Strong-biometric and device-credential combinations for every supported API bucket, including minSdk 24–29 compatibility.
- Biometric enrollment-change behavior where the emulator supports it; report unsupported capability explicitly instead of counting a skipped test as a pass.
- Missing/invalidated alias with existing ciphertext; verify no replacement key is generated and the vault remains locked.
- Wrong recovery key, corrupted/truncated recovery envelope, and failed recovery read-back.
- Process restart and interrupted recovery; verify that the last good ciphertext/envelope remains readable.
- Recovery success followed by authenticated document read and SQLCipher integrity verification.
- No document/OCR plaintext returned while locked, and no sensitive values in logs.

A JVM fake is not evidence of Android Keystore or BiometricPrompt behavior. Label each platform claim UNVERIFIED until the instrumented CI job passes on the exact SHA.

## Acceptance

This ADR remains Proposed until the migration journal/state design, fallback policy, and API compatibility matrix are reviewed. It becomes Accepted only after the project owner agrees to the recovery and biometric/device-credential behavior. A CI pass alone does not prove the lock is secure.
