# Phase 19 — Cross-Device Synchronization of Elderly-Friendly Settings

**Status:** Settings-transfer protocol core implemented; account, network sync, UI, and backup-rule integration remain pending.

**Target branch:** `feat/durable-vault-migration-journal`

**Scope:** Opt-in synchronization of a small allowlist of elderly-friendly UI, voice-navigation, and voice-haptic preferences across a user's devices. Includes privacy boundaries, backup/device-transfer behavior, pairing, conflict resolution, revocation, accessibility, and acceptance gates.

**Out of scope:** Syncing documents, encrypted files, OCR text, document metadata, reminders, raw audio, recognition hypotheses, microphone permission state, biometric credentials, vault keys, recovery secrets, account tokens, or live collaborative editing.

## Product decision

Phase 19 must not turn Kaagaz Vault into a cloud document service. The vault remains local-first. Cross-device support applies only to preferences that describe how the interface behaves for the same person.

The default is **local only**. A user must explicitly enable “Sync accessibility settings” and review what will be transferred. Disabling sync stops future transfers and leaves the current device usable. Sync failures never block document import, scan, OCR, reminders, locking, or unlock.

A first release should support two clearly named transfer modes:

1. **New-device restore:** Android Auto Backup or device-to-device transfer can restore the allowlisted settings during app setup.
2. **Optional settings sync:** if a reviewed backend and account model become available, the app may synchronize only the encrypted settings envelope. Until that backend exists, the product must not present a fake “live sync” promise; it should offer an explicit encrypted settings transfer between devices instead.

Android Auto Backup automatically includes shared-preference files and other app-private data for apps targeting API 23+, subject to backup configuration and user/device behavior. The platform supports include/exclude rules and a 25 MB per-app quota. [1] Because the current Phase 15 preference is stored in a general `ui_preferences` file, a future implementation must first separate syncable settings from local-only preferences rather than allowing an expanding preference file to become an accidental sync contract.

Android 12+ distinguishes cloud backup from device-to-device transfer and requires separate configuration rules when an app needs different inclusion behavior. Manufacturer behavior can also differ when `allowBackup` is false, so device-transfer behavior must be tested rather than assumed. [1] [2]

## Allowlisted settings contract

The sync payload is versioned and explicit. It contains only app-level choices:

- `simplifiedModeEnabled`: whether the elderly-friendly layout is enabled.
- `voiceNavigationEnabled`: whether the user has enabled the Phase 17 feature.
- `spokenGuidanceEnabled`: whether fixed navigation phrases may be spoken.
- `voiceHapticPreference`: `DEVICE_DEFAULT`, `ON`, or `OFF` from Phase 18.
- `settingsRevision`: schema version, not an app version.
- `updatedAt` and a non-identifying per-device revision used for conflict handling.

The payload must not contain:

- Microphone or notification permission results.
- On-device recognizer availability.
- TTS engine, installed voice, locale availability, pitch, or speech rate.
- Device model, Android ID, advertising ID, phone number, contacts, or location.
- Raw voice, recognized text, partial hypotheses, confidence values, or command history.
- Document names, counts, MIME types, OCR, reminder titles, dates, or IDs.
- Vault keys, biometric material, recovery codes, session tokens, or backend credentials.

Permissions and hardware capabilities are device-local. For example, enabling voice navigation on a second device does not grant `RECORD_AUDIO`; the second device must explain and request its own permission only when the user chooses to use voice. A device without a haptic actuator keeps the preference but silently uses text, touch, and speech alternatives.

## Storage separation and migration

Create a dedicated syncable settings store rather than syncing the existing `ui_preferences` file wholesale. The store should have a stable schema and an allowlist serializer. Local-only preferences remain in a separate file or no-backup location.

Migration requirements:

- Read the existing Phase 15 simplified-mode value locally.
- Write it into the new versioned settings record once.
- Preserve the current value when migration succeeds.
- If the value is malformed or unavailable, fall back to simplified mode off without blocking vault access.
- Never migrate document databases, encrypted files, wrapped keys, reminder storage, or WorkManager state into the settings record.
- Keep a migration marker local to the device; it is not user-facing sync data.

The settings record should be small enough for normal backup limits and should not contain arbitrary JSON or future preferences by default. Every new syncable field requires a privacy review and an explicit schema change.

## Encryption and pairing

An optional network sync backend must receive only an authenticated encrypted envelope. Encryption must occur before upload, and the backend must not need the plaintext settings to route or store the record. The design does not authorize choosing a backend, adding credentials, or adding network permission in this phase.

For the first cross-device implementation, prefer user-mediated pairing rather than silently linking every device on the same account. The source device shows a short-lived pairing code or QR code containing only a one-time transfer capability. The target device displays the same destination and asks the user to approve import. For older users, the code must also be readable in large text and copyable; QR must never be the only path.

The pairing ceremony must have these properties:

- Short expiry and single use.
- Explicit source and target confirmation.
- No document names or private content in the code.
- No voice read-back of the code in a public environment.
- A clear **Cancel** action at every step.
- Pairing approval is separate from vault unlock; pairing must not weaken authentication.
- If biometric confirmation is used as local approval, it remains a local user-presence check and is never synced as biometric data.

For an explicit offline transfer, export an encrypted settings bundle with a version, allowlisted fields, expiry, and integrity protection. The bundle must not be a copy of shared preferences or a database backup. Import remains preview-first and user-confirmed.

## Sync lifecycle and user control

Expose a plain settings flow:

> **Sync accessibility settings**  
> Keep your text size, simplified view, voice guidance, and haptic choice consistent on devices you approve. Documents and voice recordings are never included.

The flow contains **Not syncing**, **Sync now**, **Devices**, and **Turn off sync**. The last successful sync time and a non-sensitive status such as “Settings up to date” may be shown. Do not show document counts or private filenames in sync notifications.

Sync is opportunistic and bounded. It may run when the app is foregrounded or under a user-approved background policy, but it must not add a persistent microphone service, wake lock, notification loop, or pressure prompt. A failed transfer shows a calm, reversible message and preserves local settings.

Turning sync off prevents new uploads and removes future remote access according to the backend contract. The device keeps its current local settings unless the user chooses **Reset synced settings locally**. Device removal must revoke that device's pairing capability and clear its local sync token without deleting vault documents.

## Conflict resolution

Conflicts are expected when two devices are offline. Never silently replace an actively used local accessibility choice with a remote value.

On first restore, show a large preview:

> “Use settings from this device” or “Use settings from the other device”.

For ongoing sync, resolve fields independently with a user-visible conflict surface. Safety-preserving rules are:

- `OFF` wins automatically for voice haptics and spoken guidance when the alternatives are otherwise equivalent.
- Enabling voice navigation remotely never grants microphone permission and requires local review before the feature becomes available.
- Simplified mode may follow the user's confirmed incoming choice, but the local user can immediately undo it.
- Unknown future fields are ignored and preserved locally rather than guessed.
- A conflict record contains only setting names and revisions, never document or voice content.
- Apply, keep local, and undo actions are all at least 56dp in simplified mode and are exposed clearly to TalkBack and switch access.

A successful sync should be idempotent. Replaying the same envelope must not toggle a setting repeatedly or emit voice/haptic feedback as a side effect.

## Accessibility and elderly-friendly presentation

The sync settings surface reuses Phase 15 typography and spacing: 20sp body text, 18sp actions, 16sp minimum metadata, 24dp content padding, and 56dp or larger controls. It uses short sentences and avoids terms such as “cloud” unless the storage destination is explained in the same screen.

All important status is visible as text. TalkBack announces whether sync is off, pending, complete, failed, or awaiting approval. Switch access can reach Sync now, Devices, Turn off sync, Keep local, Use incoming, and Cancel in a stable order. No state relies on a spinner, color, sound, or haptic cue alone.

Voice navigation may open the settings surface, but voice cannot approve pairing, reveal a transfer code aloud, change the vault's security policy, or confirm a settings overwrite without the visible confirmation step. Haptic feedback follows Phase 18 only for local interaction confirmation and never reveals remote setting values.

## Backup and device-transfer rules

A future implementation must create explicit backup configuration for both cloud backup and device-to-device transfer. The allowlist must include only the dedicated syncable settings record and exclude:

- Encrypted document files and temporary import/capture files.
- SQLCipher databases, metadata indexes, and reminder storage.
- Wrapped vault keys and authentication-bound material.
- Raw voice, OCR, logs, caches, and pending network artifacts.
- Pairing tokens, access tokens, recovery codes, and backend credentials.

The app must document to users that Android backup and device-to-device restore are controlled partly by Android setup and device settings. It must not claim that turning on app sync guarantees a live mirror or that disabling app sync removes an already-created operating-system backup. The product must provide a local reset/revoke flow and document the remaining platform backup behavior.

## Implementation slices

### Slice A — schema and storage separation

Create an allowlisted versioned settings model and migrate Phase 15's local value into a dedicated syncable store. Keep local-only preferences separate. Add deterministic encode/decode tests with unknown-field and malformed-payload behavior.

### Slice B — local sync settings UI

Add the Sync accessibility settings surface, opt-in explanation, status, device list placeholder, local reset, conflict preview, and large accessible controls. No backend or network permission is added until the service contract is approved.

### Slice C — backup and D2D configuration

Add explicit Android cloud and device-transfer rules only after security review. Verify that vault files, keys, databases, reminders, voice data, and tokens are excluded while the settings allowlist survives new-device setup. Test API 24, API 31+, and manufacturer-specific transfer behavior.

### Slice D — encrypted transfer or approved backend

Choose one reviewed transport. For offline transfer, implement one-time encrypted bundles. For a backend, implement an authenticated encrypted envelope, device registration, revocation, bounded retries, and no plaintext server storage. Do not add both paths without a clear user-visible distinction.

### Slice E — conflict, revocation, and accessibility evidence

Test two-device offline edits, interrupted transfer, duplicate envelopes, clock skew, revoked devices, account sign-out, permission differences, TalkBack, switch access, 2.0x font scale, low connectivity, process death, and deletion/reset semantics.

## Acceptance gates

- [ ] Local-only is the default and enabling sync requires an explicit explanation and confirmation.
- [ ] The payload is versioned and allowlisted; it contains only simplified view, voice-navigation, spoken-guidance, and haptic-preference choices plus non-identifying revision metadata.
- [ ] Documents, OCR, reminders, filenames, raw audio, voice commands, permissions, biometric material, vault keys, tokens, and device identifiers never enter the sync payload.
- [ ] Phase 15's general `ui_preferences` file is not synced wholesale; the new record is stored separately with deterministic serialization.
- [ ] Android cloud-backup and device-transfer rules are explicit and exclude vault content, keys, databases, reminders, temporary files, logs, and credentials. [1] [2]
- [ ] Cross-device transfer is encrypted before upload/export, integrity-protected, short-lived where paired, and user-approved on the target device.
- [ ] Sync never grants microphone or notification permissions and never weakens vault authentication.
- [ ] Conflicts do not silently overwrite active local accessibility choices; Off wins for voice haptics/spoken guidance, and incoming voice enablement requires local review.
- [ ] Turning sync off, removing a device, resetting local sync, and revoking a pairing are visible, accessible, and reversible where technically possible.
- [ ] Sync failure never blocks local vault operations and never causes repeated prompts, sound, haptic, or voice announcements.
- [ ] Sync settings work with TalkBack, switch access, keyboard navigation, 2.0x font scale, high contrast, reduced motion, no network, low battery, and low-end devices.
- [ ] Unit, serialization, backup/restore, D2D, security, Compose UI, and two-device evidence is recorded before release claims.

## References

- [1]: https://developer.android.com/identity/data/autobackup "Android Auto Backup for Apps"
- [2]: https://developer.android.com/about/versions/12/behavior-changes-12#backup-restore "Android 12 backup and restore behavior changes"
