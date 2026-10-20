# CO-02 — Key Invalidation and Recovery Decision Memo

**Status: Proposed for owner decision. No cryptographic or product policy is accepted by this memo.**  
**Related order:** CO-01, [ADR-0003 — Authentication-Bound Vault Session and Legacy-Key Migration](ADR-0003-auth-bound-vault-session.md).  
**Scope:** recovery behavior for the existing authentication-bound key and migration spike, before either is wired to the app.

## 1. Verified problem

The current `AuthBoundVaultKey` spike creates a Keystore AES-GCM wrapping key with user authentication required and `setInvalidatedByBiometricEnrollment(true)`. On API 30+ it currently allows only `AUTH_BIOMETRIC_STRONG` with per-use authentication; the pre-API-30 branch configures biometric-only per-use authentication. This is not a complete vault lock today—the key/session/migration code is not wired into the repository gates—but it becomes a data-loss risk if wired without a recovery path.

Android's primary docs state that auth-per-use keys can become irreversibly invalidated when new biometrics are enrolled or all biometrics are removed, unless their authorization configuration changes; they also document invalidation when the secure lock screen is disabled or forcibly reset. Combined `DEVICE_CREDENTIAL` prompt options are unsupported on Android 10 (API 29) and lower. Auth-per-use keys can be configured for strong biometrics or device credential on API 30+, but the supported API/device matrix must be tested rather than assumed. Sources:
- [Android biometric authentication: authenticator types, CryptoObject, and auth-per-use keys](https://developer.android.com/identity/sign-in/biometric-auth)
- [KeyGenParameterSpec.Builder: authentication and invalidation behavior](https://developer.android.com/reference/android/security/keystore/KeyGenParameterSpec.Builder)
- [BiometricPrompt.CryptoObject](https://developer.android.com/reference/android/hardware/biometrics/BiometricPrompt.CryptoObject)

## 2. Recovery alternatives

| Option | Recovery behavior | Advantages | Failure modes / cost |
|---|---|---|---|
| A — separate random recovery key | Generate a high-entropy recovery key and persist a separately authenticated envelope wrapping the same VEK. Show the recovery key once and require setup-time confirmation. It must not depend on the biometrics alias. | Independent of biometric enrollment and device Keystore alias; can work offline and avoids a password KDF dependency. | If the key is lost, photographed/shared, or transcribed incorrectly, recovery can fail or be exposed. The presentation, grouping/checking, backup instructions, and re-entry UX must be accessible and tested. A recovery key is not a cloud backup of the encrypted documents. |
| B — passphrase-wrapped second VEK envelope | Derive a wrapping key from a user passphrase with a reviewed, versioned KDF and store a separate authenticated recovery envelope. | A memorable phrase may be easier to preserve than a random code; can be used offline on replacement devices when encrypted data is still available. | Weak passphrases allow offline guessing if an attacker gets app data. KDF algorithm/parameters, salts, encoding, retry behavior, device performance, dependency/license/maintenance, and upgrade strategy must be reviewed. Any new dependency requires the G3 gate. Do not invent a custom KDF. |
| C — device-credential-capable auth-per-use key | On supported API levels, permit the device credential as an alternative authenticator for a per-use Keystore operation. | Can let a user unlock without a strong biometric when the device and API support it. | This is an authentication alternative, not a complete independent recovery mechanism. API 24–29 do not support the combined prompt authenticator configuration; lock-screen reset, Keystore alias loss, reinstall/device replacement, or data corruption still need explicit handling. Do not silently downgrade to time-based authorization to make the prompt work. |
| D — defer lock activation until recovery is configured | Do not enable a new auth-bound lock until the selected independent recovery method is set up, tested, and its envelope is read back successfully. | Avoids converting a currently accessible vault into a locked vault without a recovery route. | Delays availability of the lock feature; setup must communicate why recovery is required and support users who cannot complete it. |

**Recommendation for review:** treat an independently authenticated recovery envelope as a hard prerequisite for enabling the new lock. Keep device-credential authentication as a separate choice in CO-01; do not treat it alone as the recovery plan. Prefer Option A if the owner accepts an offline, one-time secret with an accessible setup/confirmation process; prefer Option B only if its KDF, parameters, low-end-device cost, and dependency governance are reviewed and approved. In either case, defer lock activation until the recovery envelope is durably written, then read back and authenticate it before reporting setup success. No existing key or legacy ciphertext may be deleted or replaced as a response to recovery failure.

## 3. Failure matrix — required behavior

| Event | Required result | Must never happen |
|---|---|---|
| New fingerprint/strong biometric enrolled, or all biometrics removed | Detect primary-key invalidation; keep app locked; offer the approved recovery flow; after successful recovery re-establish primary access only through an approved, crash-safe procedure. | Create a replacement VEK, silently create a new primary key and show an empty vault, or delete encrypted documents. |
| Device has no strong biometric or authenticator enrollment is unavailable | If the approved per-use credential route works on this API/device, offer it; otherwise use the approved independent recovery/setup policy or do not enable the lock. | Claim lock is enabled while repository access remains open, or downgrade auth policy without approval. |
| Secure lock screen disabled/reconfigured or forcibly reset | Keep locked and use the independent recovery envelope if available; explain the recovery state without exposing document contents. | Retry indefinitely, overwrite the old wrapper, or generate a new VEK. |
| Keystore alias missing, invalidated, or wrong key returned while encrypted data exists | Enter a typed recovery-required state; preserve ciphertext, journal, database, WAL/sidecars, and old wrappers. | Auto-regenerate a key or erase the data to make initialization succeed. |
| Recovery key/passphrase is wrong | Authentication/tag check fails; remain locked; keep generic user-facing error and bounded retry policy. | Return partial plaintext, leak envelope/key data in logs, or update the stored recovery envelope. |
| Recovery secret is lost | State plainly that data may be permanently inaccessible if the primary key is also unavailable; preserve remaining files for possible future recovery. | Promise that support, a reset, or a reinstall can recreate the VEK. |
| Recovery envelope is truncated, malformed, wrong-version, or tag-invalid | Fail closed; preserve all copies and mark recovery required. | Fall back to unauthenticated plaintext, skip validation, or silently replace the envelope. |
| Process death during document/database migration | Resume from the durable journal; repeat idempotent operations; authenticate staged output and database integrity before retiring legacy data. | Delete the only readable copy, infer success from file presence alone, or publish partially migrated metadata. |
| Reinstall/device replacement with no original ciphertext or recovery material | Explain the scope boundary: a recovery key does not recreate missing encrypted files; only recover data that still exists. | Imply a local-only vault has a server-side restore or that the VEK can be recovered from nothing. |
| Recovery setup persistence/read-back fails or storage is full | Do not enable vault lock; zero mutable secret buffers best-effort; preserve any valid old path and show a retryable setup failure. | Show “recovery configured” before verified persistence or retire legacy keys. |

## 4. Proposed user-facing copy

**Before enabling vault lock**

> Vault lock uses this device’s security hardware. Adding or removing fingerprints, changing device security, or losing access to the device key can make normal unlock unavailable. Set up and verify a separate recovery method before enabling lock. Kaagaz Vault does not upload your recovery material. If the device key and recovery method are both unavailable, your saved documents may be permanently inaccessible.

**When recovery is required**

> The device key is unavailable, so the vault remains locked. Your encrypted files have been preserved. Use the recovery method you configured for this vault. Do not clear app data or reinstall the app while recovery is unresolved.

**When the recovery secret is rejected**

> This recovery information could not unlock the vault. Nothing was changed. Check the recovery information and try again. Repeated failures will not reset or replace the vault key.

These strings are proposals; accessibility, localization, and real failure paths must be tested before implementation.

## 5. Required engineering acceptance after owner decision

1. Pick the recovery option and approve the required key/envelope/KDF changes explicitly; those are G2, and a new KDF dependency is G3.
2. Add an Android instrumented-test source set and an emulator CI job (pin third-party actions by commit SHA). Cover Keystore auth-per-use key creation, biometric enrollment change where the emulator supports it, no strong biometric, alias loss with existing ciphertext, wrong/corrupted recovery envelope, and process restart.
3. Add migration crash-point tests for death after staging, after file publication but before journal save, during DB-key rewrap, and before legacy retirement. All retries must be idempotent.
4. Prove that every document, reminder, search-index, database, OCR, import, and export entry point stays inaccessible when the session is locked. Include a test where an operation is active during lock.
5. Test a successful recovery by reopening/authenticating the same VEK and reading the existing encrypted documents; a replacement key with unreadable old files is not recovery.
6. Keep the migration journal and legacy recovery path until document read-back and database integrity checks pass. Never treat a JVM fake as proof of Android Keystore or biometric correctness.

## 6. Owner decision required

- Independent recovery: [ ] A — one-time random recovery key; [ ] B — passphrase-wrapped second envelope with a reviewed KDF; [ ] other reviewed design: ______.
- Enrollment/no-biometric fallback: [ ] use device credential where auth-per-use semantics are supported plus an independent recovery envelope; [ ] independent recovery only on unsupported APIs; [ ] other: ______.
- If a recovery method has not been set up and verified: [ ] keep vault lock disabled; [ ] other explicit policy: ______.
- Minimum API behavior: [ ] API 24–29 remain fail-closed until recovery is verified; [ ] other tested policy: ______.

**No implementation or ADR acceptance is authorized by this memo.** Review this memo alongside the CO-01 proposed options in ADR-0003; the same owner decision can address both orders after the two separate draft PRs are available.
