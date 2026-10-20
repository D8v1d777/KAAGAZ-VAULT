# CO-01 / CO-02 — Owner Security Decision Memo

**Status:** Proposed — owner decision required; no production security behavior is approved by this memo.  
**Date:** 2026-10-20  
**Related:** CO-01 (authentication/session gate), CO-02 (key invalidation/recovery), ADR-0003, Phase 12 plan, Phase 13 lock design.  
**Decision authority:** Project owner.

## Executive recommendation

1. **Normal unlock:** support strong biometric *or* device credential where the supported Android/API combination can enforce the chosen auth-per-use cryptographic operation. Keep a strict documented compatibility path for older APIs; never fake a fallback with a UI boolean.
2. **Recovery:** add an independent, randomly generated high-entropy recovery key that can unwrap the VEK through a separate authenticated envelope. Show it once, require the user to confirm they stored it, and do not silently keep a plaintext copy. Recovery can still fail permanently if the owner loses the recovery key; explain that before vault setup.
3. **Lock lifecycle:** lock on explicit lock, screen-off, and process restart. Default to immediate lock when the app leaves the foreground; do not add an unrequested inactivity timer in this implementation. In-flight work must drain or be cancelled before the locked state commits, and no repository call may publish plaintext after lock completion.
4. **Migration:** preserve the existing legacy ciphertext and key material until the new ciphertext has been authenticated by read-back, the SQLCipher database is successfully reopened and checked, and the durable journal records completion. Never silently create replacement keys when protected data already exists.
5. Keep ADR-0003 Proposed and do not wire the production UI/session until these decisions are explicitly accepted.

This is a policy proposal, not a claim that the existing implementation supports all APIs or that the migration is safe today. The API matrix and device tests are acceptance gates.

## Verified current constraints

- The existing app constructs repositories/database access before any authentication gate; a biometric dialog alone would not protect repository entry points.
- The auth-bound primitives, migration coordinator/journal, and wrapped-key store are not connected to the production app path (CO-01 audit).
- The current key primitive is designed for strong-biometric-only auth and biometric-enrollment invalidation (CO-02 audit). No recovery-key or passphrase-wrapped VEK envelope exists.
- No Android instrumentation test source set exists yet. JVM tests and CI alone cannot establish Android Keystore behavior on devices.
- Android documents auth-per-use keys and the use of `BiometricPrompt.CryptoObject`; the Android API for `setUserAuthenticationParameters` was added in API 30. It documents that including `AUTH_DEVICE_CREDENTIAL` changes the biometric-enrollment invalidation behavior. The compatibility design must be checked for every supported API and library version before implementation.

## Decision A — Authentication policy

| Option | Advantages | Costs / failure mode | Recommendation |
|---|---|---|---|
| A1. Strong biometric only | Narrow and familiar; enrollment change can intentionally invalidate a biometric-only key. | Users without enrolled/available strong biometrics can be locked out; biometric changes need independent recovery. | Do not choose as the sole path for an accessibility-focused vault unless you explicitly prefer this restriction. |
| A2. Strong biometric OR secure device credential | Better access for users without biometrics and a recovery route from ordinary biometric-enrollment changes on compatible API/key configurations. | API differences must be handled; auth-per-use and CryptoObject behavior must be verified on minSdk and current Android. Device PIN/pattern/password becomes part of the threat model. | **Recommended**, contingent on the compatibility matrix and instrumented tests. |
| A3. Separate user passphrase wrapping the VEK | Works independently of biometric enrollment and hardware availability. | Weak passphrases permit offline guessing; requires a reviewed KDF, format, retry UX and envelope versioning. Adds complexity. | Treat as an alternative/extension, not an unreviewed substitute for A2. |

Required guardrails whichever option is chosen:
- Successful unlock must result from the correct authenticated cryptographic operation; never trust a UI callback or success boolean alone.
- Keep keys inside Keystore where applicable; zero mutable plaintext/key buffers on a best-effort basis.
- If the device has no secure credential configured, remain locked and explain the required setup.
- Do not weaken authentication to make legacy migration easier.
- No protected document, OCR, metadata, thumbnail, reminder title, or search result is shown while locked.

## Decision B — Independent recovery

| Option | Advantages | Costs / failure mode | Recommendation |
|---|---|---|---|
| B1. No independent recovery | Smallest implementation and no recovery secret to protect. | Invalidated/lost Keystore alias can make all encrypted documents permanently unreadable. This risk is unacceptable without an explicit owner decision. | Not recommended. |
| B2. Random one-time recovery key and separate VEK envelope | High entropy avoids dependence on user-chosen password strength; independent of the original Keystore alias. Can be stored offline by the owner. | If the owner loses the recovery key, recovery is unavailable; setup/restore UX and safe one-time display/confirmation are required. Anyone obtaining the recovery key may recover the vault. | **Recommended baseline**, with explicit loss warnings and no server/cloud copy. |
| B3. User passphrase-wrapped second VEK envelope | Memorability and portability may be better for some users. | Offline dictionary/guessing risk if the passphrase is weak; KDF parameters and format must be reviewed, versioned and tested. | Viable alternative if you prefer memorability over recovery-key handling; requires a separate threat-model review. |
| B4. Recovery key AND passphrase envelope | More than one recovery route. | Largest attack surface and implementation/test burden; more formats and flows to maintain. | Defer unless product requirements justify the cost. |

If B2 is approved, the recovery secret must be generated cryptographically, have sufficient entropy (recommendation: 256 random bits), be associated with a distinct versioned authenticated envelope, and be tested against tampering, wrong recovery material, partial writes, retry/re-entry and process death. Never store the recovery key next to the vault as plaintext. Recovery must not upload anything.

## Decision C — Lock lifecycle

**Recommendation:** explicit lock, screen-off and process restart force a locked state; app backgrounding locks immediately by default. No arbitrary idle timeout is added in the first implementation. The owner may later approve a clearly exposed grace-period setting, but it must not silently extend access.

Lock completion requires:
1. Stop accepting new repository operations.
2. Cancel or drain operations that could publish sensitive data.
3. Close database/file handles and prevent stale workers from publishing results.
4. Clear sensitive Compose state (document list, OCR text, query text, reminder details and content-bearing errors).
5. Clear or replace the in-memory VEK/session buffer on a best-effort basis.
6. Verify every repository entry point is locked and no result can return plaintext after lock completion.

## Failure and recovery matrix

| Event | Required behavior | Must never happen |
|---|---|---|
| User cancels authentication | Stay locked; no writes or key rotation. | Treat cancellation as success. |
| No biometric is enrolled / sensor unavailable | Offer only a supported, tested fallback or clear setup guidance. | Bypass authentication or create a replacement key. |
| Biometric enrollment changes | Apply the approved key policy. If the existing alias is invalidated, transition to RecoveryRequired and preserve ciphertext. | Delete ciphertext or pretend the vault is empty. |
| Keystore alias is missing/invalidated but encrypted data exists | Enter RecoveryRequired; recover only via the approved independent envelope, otherwise preserve data and explain access is unavailable. | Generate a new VEK or silently overwrite the old wrapped key. |
| Recovery key/passphrase is wrong | Fail closed with a generic message and bounded retry behavior; preserve every envelope and ciphertext. | Modify or retire a recoverable copy. |
| Wrapped-key envelope/ciphertext is malformed or fails authentication | Preserve state, remain locked, report a safe error. | Accept partial plaintext or auto-repair by replacing keys. |
| Process dies during migration | Restart locked; inspect journal and resume only from a validated state. Keep legacy readable copy until all acceptance checks pass. | Guess migration state or delete the only readable copy. |
| SQLCipher open/integrity check fails | Preserve legacy wrapper, ciphertext, database and sidecars for recovery/diagnosis. | Mark migration complete or delete material. |
| Device credential is removed/reset | Follow the Android key behavior established in tests; require approved recovery if the alias is invalidated. | Create a new key over existing encrypted data. |

## Required verification before production wiring

- Pure JVM state-machine, envelope parser/context-binding and crash-point tests.
- Android instrumentation source set and CI job for Keystore auth-per-use, successful/cancelled/failed CryptoObject operations, supported authenticator combinations, biometric enrollment change where testable, missing alias, wrong recovery material, process restart, and lock during import/OCR/search.
- Exercise minimum supported API (24) and a current API, with a documented matrix identifying which fallback each API actually supports.
- End-to-end test: vault starts locked; every entry point rejects while locked; unlock authenticates cryptographically; lock drains/cancels work; no post-lock plaintext is published.
- Migration tests for corrupted payload, missing key, insufficient storage, interrupted writes, WAL/sidecars and crash/restart. Validate authenticated read-back and SQLCipher integrity before durable completion.
- Independent security review; exact-head CI results; no release/security certification claim from static review alone.

## Owner decision recorded

The owner explicitly approved the recommended bundle on 2026-10-20: A2 + B2 + C1. The ADR now records the approved policy. This memo remains a policy record and not implementation evidence.

1. **Authentication:** A1 strong biometric only; A2 strong biometric OR device credential; A3 include a separate passphrase-wrapped VEK envelope.
2. **Recovery:** B1 no recovery; B2 one-time random recovery key; B3 passphrase-wrapped second envelope; B4 both B2 and B3.
3. **Locking:** C1 immediate on foreground loss, screen-off and process restart (recommended); C2 approve a specified foreground-loss grace period. Explicit lock and screen-off always lock.
4. **Implementation gate:** ADR-0003 is Accepted for policy direction. Production wiring remains gated by the API compatibility matrix, failure tests, reviewed migration plan, exact-head CI, device tests and independent security review.

No production authentication, key lifecycle, recovery format, envelope format or migration behavior is implemented by this memo. The owner policy decision is accepted; implementation and verification gates remain open. Any change to key/ciphertext format, manifest/dependencies or database schema remains subject to the repository’s gated approval rules.

## Research references

- Android Developers, [Biometric authentication and CryptoObject](https://developer.android.com/identity/sign-in/biometric-auth)
- Android Developers, [KeyGenParameterSpec.Builder](https://developer.android.com/reference/android/security/keystore/KeyGenParameterSpec.Builder)
- Android Developers, [Android Keystore system](https://developer.android.com/privacy-and-security/keystore)
