# CO-02 — Key invalidation and recovery decision memo

**Status: Proposed for owner decision. No runtime or cryptographic policy is accepted by this document.**

## Verified issue

The current inactive `AuthBoundVaultKey` spike sets `setInvalidatedByBiometricEnrollment(true)`. For a biometric-only per-use Keystore key, enrollment changes or removing all enrolled biometrics can make the key unusable. The current spike is not wired into the app's access/session lifecycle; this memo is a gate before any wiring.

Primary Android sources:
- https://developer.android.com/identity/sign-in/biometric-auth
- https://developer.android.com/reference/android/security/keystore/KeyGenParameterSpec.Builder
- https://developer.android.com/reference/android/hardware/biometrics/BiometricPrompt.CryptoObject

The platform supports auth-per-use keys with strong biometrics or device credential on API 30+, subject to correct `CryptoObject` use. Combined authenticator prompt options are unsupported on API 29 and lower. Device credential is an authenticator choice, not a complete recovery mechanism for lost Keystore aliases or corrupted/missing data.

## Recovery alternatives

| Option | Recovery path | Main trade-off |
|---|---|---|
| A — separate random recovery key | Independently authenticated envelope wraps the same random VEK; show once, require confirmation and clear safe instructions. | Strong offline recovery, but losing or exposing the secret is serious; UX must be accessible and re-entry tested. |
| B — passphrase-wrapped recovery envelope | Versioned second envelope uses a vetted KDF with reviewed parameters. | Easier to remember, but weak phrases enable offline guessing; KDF/version/performance/dependency/license are G2/G3 review gates. Never invent a KDF. |
| C — device credential as auth-per-use option | Where supported, device credential can authorize a per-use Keystore operation. | Useful fallback on API 30+, but not a separate recovery envelope; does not solve alias loss, lock-screen reset, reinstall or absent encrypted data. |
| D — defer lock activation | Require a verified recovery method before enabling the new lock. | Delays the lock feature, but avoids taking away access before recovery exists. |

**Recommendation for review:** defer enabling vault lock until a separate recovery envelope is set up, durably stored and successfully read back/authenticated. Keep the authenticator choice in CO-01 separate from recovery. Option A avoids a KDF dependency; Option B needs the full dependency/parameter/low-end-device review before selection. No old key, wrapper or ciphertext may be removed when recovery fails.

## Failure matrix

- **Biometric enrollment changes / all biometrics removed:** remain locked; offer the approved recovery method. Never generate a new VEK or present an empty vault.
- **No strong biometric or authenticator unavailable:** use only an approved, verified fallback; otherwise do not enable the lock on that API/device.
- **Device lock-screen disabled/reset or Keystore alias lost:** enter recovery-required state; preserve ciphertext, DB/WAL, journal and key wrappers. Never silently regenerate.
- **Wrong recovery secret or corrupted envelope:** remain locked with a generic error; preserve all data and copies, bound retries, do not replace the envelope.
- **Recovery secret lost while primary key is unavailable:** explain that data may be permanently inaccessible; do not promise a reset can recreate the VEK.
- **Process death during migration:** resume from durable journal and repeat idempotent work; keep the last readable copy until staged data and database integrity are validated.
- **Storage full / recovery setup cannot persist:** do not enable the lock; report retryable setup failure and preserve the existing access path.
- **Reinstall/device replacement without original encrypted files:** recovery material cannot recreate missing ciphertext; do not imply cloud restore exists.

## Proposed user-facing copy

Before enabling lock:

> Vault lock uses this device's security hardware. Changes to fingerprints or device security, or losing access to the device key, can make normal unlock unavailable. Set up and verify a separate recovery method before enabling lock. Kaagaz Vault does not upload recovery material. If both the device key and recovery method are unavailable, saved documents may be permanently inaccessible.

When recovery is required:

> The device key is unavailable, so the vault remains locked. Your encrypted files have been preserved. Use the recovery method you configured. Do not clear app data or reinstall while recovery is unresolved.

These are proposals; accessibility, localization, and actual failure paths require tests.

## Required acceptance after owner decision

1. Approve the recovery method and any envelope/KDF changes explicitly (G2; new dependency is G3).
2. Add Android instrumentation tests for auth-per-use unlock, enrollment/invalidation behavior where testable, alias loss with existing ciphertext, wrong/corrupt envelope, and restart.
3. Test migration interruption after staging, publication-before-journal, database-key rewrap, and before legacy retirement.
4. Prove all document, reminder, search, DB, OCR, import and export paths refuse access while locked, including in-flight work during lock.
5. Prove recovery opens the same VEK and reads existing encrypted documents; a newly generated key with unreadable old files is not recovery.
6. Keep the migration journal and old readable copy until authenticated read-back and DB integrity succeed.

## Owner decision required

- Recovery: [ ] A — random recovery key; [ ] B — passphrase envelope with reviewed KDF; [ ] other reviewed method: ______.
- Authenticator fallback: [ ] device credential on supported API + independent recovery; [ ] recovery only where combined auth is unsupported; [ ] other: ______.
- Until recovery is verified: [ ] keep vault lock disabled; [ ] other: ______.
- API 24–29: [ ] fail closed until approved recovery path is available; [ ] other tested policy: ______.

**This memo does not accept ADR-0003 or authorize implementation.** Review it with CO-01 and make one joint owner decision after both separate draft PRs are available.
