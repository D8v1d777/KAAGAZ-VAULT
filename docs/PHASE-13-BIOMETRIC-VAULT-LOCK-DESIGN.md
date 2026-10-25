# Phase 13 — Biometric Authentication and Vault-Lock UI

**Status:** Design proposal; no biometric integration is implemented by this document.

**Target branch:** `feat/durable-vault-migration-journal`

**Scope:** Android authentication flow, vault-lock state machine, session-aware UI, lifecycle behavior, accessibility, and device-test design.

**Out of scope:** Replacing the existing encryption primitives, inventing a new key format, completing the migration engine, adding network services, adding analytics, or claiming release security before device evidence exists.

## Product intent

Kaagaz Vault should feel quiet, predictable, and respectful when it protects sensitive documents. The lock should explain what is happening, offer a clear next step, and never imply that a failed biometric attempt means data loss. Engagement comes from confidence and continuity, not urgency, streaks, shame, or repeated prompts.

The primary action is always singular: **Unlock vault**. Secondary actions explain recovery or continue in a locked, useful state. The app must not reveal filenames, OCR text, counts, thumbnails, reminder titles, or search results while locked.

## Current foundation and constraint

`AuthBoundVaultKey` already exposes the required primitive boundary:

- `prepareSetupCipher()` creates the auth-bound Keystore cipher for initial wrapping.
- `finishSetup()` must receive the same authenticated cipher operation and durably persist the wrapped VEK before returning it.
- `prepareUnlockCipher()` creates the decrypting cipher from the persisted wrapped key.
- `finishUnlock()` performs the authenticated cryptographic use and returns the VEK only after tag verification.
- Missing aliases, malformed wrapped material, and failed authenticated reads are recovery conditions. They must not generate replacement keys automatically.

The Phase 12 plan correctly states that a UI-only biometric dialog is insufficient. Repository creation, database access, OCR, reminders, camera capture, search, background work, and Compose state all need one explicit unlocked-session boundary.

Android's current guidance supports auth-per-use keys for high-value operations and documents the `BiometricPrompt.CryptoObject` flow. It also distinguishes auth-per-use biometric/device-credential keys from time-validity keys. This phase uses auth-per-use behavior for vault unlock, not a multi-minute validity window.

## User-visible states

The UI state is a sealed model, not a collection of independent booleans:

```text
VaultUiState
├── FirstRunSetup
│   ├── Ready
│   ├── Authenticating
│   ├── Persisting
│   ├── Complete
│   └── Failed(retryable / preserved-data / unsupported)
├── Locked
│   ├── Ready
│   ├── Authenticating
│   ├── Cancelled
│   ├── TemporarilyUnavailable
│   ├── DeviceCredentialRequired
│   ├── BiometricChanged
│   └── RecoveryRequired
├── Unlocked(sessionId, startedAt)
│   ├── Ready
│   ├── Locking
│   └── Expired
└── FatalPreservationRequired(reason)
```

### Locked screen

The locked surface should show:

- Kaagaz Vault wordmark.
- A neutral lock illustration or icon with a meaningful content description.
- “Your vault is locked.”
- “Your documents remain on this device. Unlock to view them.”
- One prominent **Unlock vault** button.
- A quiet explanation link or text for “Use device credential” when the platform offers it.
- A short privacy note: “Nothing is deleted when unlocking is cancelled.”

Do not show document count, last document name, OCR preview, reminder text, search fields, or recent activity.

### Authenticating

The platform prompt is the source of truth. The app screen behind it must remain visually locked. Disable import, camera, search, reminders, and document actions while the prompt is active. Do not animate a fake progress percentage. A subtle, reduced-motion-safe transition from `Ready` to `Authenticating` is sufficient.

### Cancelled or failed

Use calm, distinct messages:

- Cancelled: “Vault stays locked. Nothing was changed.”
- Too many attempts: “The device is temporarily unavailable. Try again later or use your device credential if offered.”
- No enrolled credential: “Set up a screen lock or biometric credential in Android Settings to protect this vault.”
- Hardware unavailable: “Biometric unlock is unavailable on this device. Your encrypted documents are unchanged.”
- Key invalidated after biometric enrollment change: “This vault needs recovery because the device’s biometric setup changed. No replacement key was created.”
- Corrupt or ambiguous protected state: “Vault recovery is required. We preserved the available protected data and did not overwrite it.”

Every failure state must leave the repository locked and must not display partial document content.

### Unlocked state

When unlocked, the existing document UI can be shown. Add a persistent, accessible **Lock vault** action in the top-level app chrome. It should not compete visually with import or scan. A successful unlock should return focus to the first meaningful heading or the previously focused action, not to a random list item.

## Authentication contract

### Initial setup

1. Detect whether protected vault material already exists.
2. If no protected material exists, show `FirstRunSetup.Ready` with a plain-language explanation that the user is creating the vault's access protection.
3. Call `prepareSetupCipher()` on a worker boundary before opening the prompt.
4. Construct `BiometricPrompt.CryptoObject` with that exact cipher instance.
5. Use `BiometricPrompt` with strong biometric authentication. If product accepts device-credential fallback for setup, define that choice in the same key policy before implementation; do not silently mix policies.
6. On authentication success, call `finishSetup()` with the exact returned crypto operation and persist wrapped bytes before exposing the session.
7. Only after durable persistence succeeds, create the session and open repositories.
8. If persistence fails, clear the in-memory VEK, keep the app locked, and preserve any existing protected material.

### Unlock

1. Read and validate the bounded wrapped-key envelope without logging bytes.
2. Call `prepareUnlockCipher(wrappedKey)`.
3. Construct `BiometricPrompt.CryptoObject` with that exact cipher.
4. Launch the platform prompt.
5. On success, call `finishUnlock()` with the authenticated cipher and the same wrapped bytes.
6. Verify the returned VEK can open the required protected stores before publishing `Unlocked`.
7. Publish a short-lived `VaultSession` only after validation succeeds.
8. On any error, clear the VEK buffer where possible, close any opened handles, and remain locked.

### Device credential policy

The implementation must choose and document one of these policies before coding:

- **Strong biometric only:** preserve the current `AUTH_BIOMETRIC_STRONG` primitive and show device settings guidance when unavailable.
- **Biometric or device credential:** generate the key with `AUTH_BIOMETRIC_STRONG or AUTH_DEVICE_CREDENTIAL`, use auth-per-use semantics, and do not set a negative button on the same prompt configuration.

The recommended product choice is **biometric or device credential** for inclusive access across users aged 20–90, provided the cryptographic key policy, API matrix, and device tests support it. The UI should describe this as “biometric or device passcode” rather than using technical Android terminology.

Do not implement a fake fallback button that bypasses `BiometricPrompt` or returns a success boolean without authenticated cryptographic use.

## Session boundary

Introduce a small coordinator between the activity and repositories:

```text
MainActivity
  └── VaultSessionCoordinator
        ├── AuthenticationController
        ├── VaultSession?        // contains no UI state
        ├── DocumentRepository
        ├── EncryptedMetadataIndexProvider
        ├── ReminderRepository
        └── lifecycle / lock policy
```

The coordinator owns transitions and exposes immutable state to Compose. The UI emits actions such as:

```text
UnlockRequested
SetupRequested
LockRequested
AppBackgrounded
AppForegrounded
ScreenOff
AuthenticationSucceeded
AuthenticationCancelled
AuthenticationFailed(reason)
ProcessRestarted
```

Repositories must not be constructed with an unlocked key in global activity state before authentication. The coordinator creates or opens session-bound repositories only after successful unlock validation. No UI composable may access database internals or key material.

## Lock policy

Initial implementation should support three lock triggers:

1. Explicit user action: **Lock vault**.
2. Activity backgrounding after a configurable short grace period, with a conservative default of immediate lock for sensitive builds.
3. Screen-off or process recreation.

Do not add an arbitrary inactivity timer in this phase unless product explicitly approves the user-facing setting. A surprise timer can interrupt document review and create unnecessary stress. If a timeout is later added, show it as an accessible setting with a clear value and an explicit “lock now” action.

On lock completion:

- Stop accepting new repository work.
- Cancel or drain in-flight work according to operation type.
- Close database and file handles.
- Clear document lists, OCR text, query text, reminder details, and error strings that contain sensitive context.
- Clear or replace the `VaultSession` VEK buffer on a best-effort basis.
- Return Compose to `Locked.Ready`.
- Keep `FLAG_SECURE` enabled throughout.

No operation may return document plaintext after the lock transition is complete. An operation already in flight must either complete before the lock commits or return a locked/cancelled result without publishing data.

## Accessibility and age-friendly interaction

- Keep primary actions at least 48dp and use Material components with built-in semantics.
- Support large system font scales without clipped buttons, horizontal scrolling, or hidden error text.
- Use short sentences and avoid security jargon such as “CryptoObject” or “VEK” in user-facing copy.
- Pair color with text and icon/state; never use color alone for lock, failure, or recovery.
- Keep focus order: heading → explanation → primary unlock → secondary help/recovery.
- Ensure TalkBack announces authentication state changes without reading protected document content.
- Respect system reduced-motion preferences where available; transitions must not be required to understand state.
- Keep sound feedback opt-in to system settings and never play audio while the platform biometric prompt is active.
- Use a single calm click note for explicit actions only; do not play repeated sounds during retries or state polling.

## Error and recovery matrix

| Condition | UI state | Data action | User action |
|---|---|---|---|
| User cancels prompt | Locked.Cancelled | No writes; remain locked | Retry or leave app |
| No biometric enrolled | Locked.DeviceCredentialRequired or guidance | No key changes | Set up device credential in Settings |
| Biometric hardware unavailable | Locked.TemporarilyUnavailable | No key changes | Use supported fallback or try later |
| Too many attempts | Locked.TemporarilyUnavailable | No key changes | Wait; do not loop prompts |
| Key invalidated by enrollment | Locked.BiometricChanged | Preserve data; no replacement key | Start reviewed recovery flow |
| Wrapped key malformed | RecoveryRequired | Preserve protected files | Contact recovery/support path defined by product |
| Keystore alias missing with data present | RecoveryRequired | Never generate replacement alias | Preserve data and stop |
| Authenticated unwrap fails | Locked / RecoveryRequired | Clear VEK; close handles | Retry only if state is unambiguous |
| DB integrity/open failure | RecoveryRequired | Do not retire legacy material | Preserve and report safely |
| Process death during unlock | Locked on restart | Re-read durable state | Retry from a clean prompt |

## Implementation slices

### Slice 1 — pure state and policy

- Add immutable `VaultLockState` and `VaultLockAction` models.
- Add a coordinator reducer/state machine with tests for every allowed and rejected transition.
- Define `LockReason` and sanitized user-facing copy separately from exception messages.
- Add a session lifetime object with best-effort clearing tests where deterministic.

### Slice 2 — authentication controller

- Add an activity-scoped `BiometricPrompt.AuthenticationCallback` bridge.
- Keep the actual `Cipher` instance private to the controller; never store it in Compose state.
- Add `BiometricManager.canAuthenticate()` preflight and map each result to an explicit UI state.
- Handle configuration changes without launching duplicate prompts.
- Keep prompt cancellation idempotent.

### Slice 3 — session-gated repositories

- Move repository/database construction behind a successful session.
- Gate import, list, read, OCR, search, reminders, camera, and background work.
- Add locked-result behavior and tests proving no post-lock plaintext publication.

### Slice 4 — vault-lock UI

- Add locked, setup, authenticating, failure, recovery-required, and unlocked shell states.
- Add explicit lock action and lifecycle-driven lock transitions.
- Clear sensitive Compose state on lock.
- Preserve the current calm visual system and platform click feedback.

### Slice 5 — device and release evidence

- Add instrumentation tests for prompt success, cancellation, lockout, no enrollment, device-credential fallback, biometric enrollment change, rotation, background/foreground, screen-off, and process death.
- Run API 24 minimum-supported tests where the chosen APIs permit them, plus current Android.
- Run debug/release build, JVM tests, lint, merged-manifest/offline policy, and dependency/license checks.
- Record exact evidence before changing completion percentages or security claims.

## Acceptance gates

The phase is not complete until all of these are evidenced:

- [ ] The app starts locked when protected vault material exists.
- [ ] The app never creates repositories or exposes document data before a valid unlocked session.
- [ ] Unlock success requires the same authenticated `Cipher` instance to complete the cryptographic operation.
- [ ] Cancel, failure, lockout, missing enrollment, key invalidation, malformed envelope, and missing alias all remain locked.
- [ ] Device credential fallback is either fully supported and tested or explicitly not offered.
- [ ] Explicit lock closes session-bound handles and clears sensitive Compose state.
- [ ] Background, screen-off, rotation, and process death behavior are deterministic and tested.
- [ ] Recent-app snapshots and lock-screen notifications do not reveal sensitive document content.
- [ ] Large-font, TalkBack, focus-order, contrast, and reduced-motion checks pass on the lock and recovery surfaces.
- [ ] No new network permission, analytics, cloud dependency, or hidden network behavior is introduced.
- [ ] Debug/release build, JVM tests, lint, offline-manifest policy, and relevant instrumentation tests pass.
- [ ] Independent security review has been completed before release claims.

## Design decision required before implementation

Product must approve whether the vault accepts **biometric only** or **biometric plus device credential**. This choice changes the Keystore authenticator flags, `BiometricPrompt` configuration, fallback copy, and test matrix. Until approved, the implementation must not wire the existing `AUTH_BIOMETRIC_STRONG` primitive into production UI.

## References

- [1]: https://developer.android.com/identity/sign-in/biometric-auth "Authenticate people with biometrics"
- [2]: https://developer.android.com/reference/androidx/biometric/BiometricPrompt "BiometricPrompt API reference"
- [3]: https://developer.android.com/reference/kotlin/android/hardware/biometrics/BiometricPrompt.CryptoObject "BiometricPrompt.CryptoObject API reference"
- [4]: https://developer.android.com/privacy-and-security/keystore "Android Keystore system"
