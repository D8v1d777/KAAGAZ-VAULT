# Authentication-Bound Vault Lock — Implementation Plan

**Target branch:** phase/11-auth-bound-vault-lock for design review; implementation should be split into small, reviewable follow-up commits.
**Status:** Design preparation; no new biometric lock implementation is claimed.
**Related decision:** [ADR-0003](engineering/decisions/ADR-0003-auth-bound-vault-session.md)

## Findings from source inspection

- MainActivity constructs the document store, database provider, index provider, repository, OCR engine, and reminder repository before any authentication gate.
- VaultHomeScreen immediately calls repository.list() in LaunchedEffect and exposes import, OCR, search, reminders, and camera workflows. A UI-only biometric dialog would not secure these call paths.
- EncryptedDocumentStore uses a non-exportable Keystore key directly for each payload. That key cannot simply be exported or wrapped; legacy payloads need a decrypt/re-encrypt migration.
- DatabaseKeyManager keeps a random SQLCipher key in wrapped form. Migration must coordinate the wrapped key, database handles, and SQLite sidecars.
- Existing payload envelopes are versioned AES-GCM with a 128-bit tag and authenticated document-ID context. Preserve those integrity properties in the new envelope.
- CI run [#253](https://github.com/D8v1d777/KAAGAZ-VAULT/actions/runs/39130810427) passed configured debug/release builds, JVM unit tests, lint, and merged-manifest offline policy on its tested head. This is not device security evidence.

## Implementation sequence

### Slice A — design and test fixtures
- [ ] Review and accept ADR-0003, including the device-credential fallback and supported API matrix.
- [ ] Add synthetic legacy/new-format fixtures and a pure JVM migration-state model.
- [ ] Define a versioned, bounded key-envelope format and context-binding rules.
- [ ] Specify migration journal format, atomic write/replace behavior, fsync policy, and crash-state recovery table.

### Slice B — cryptographic primitives
- [ ] Add an auth-per-use Android Keystore key provider with explicit alias/version and fail-closed behavior.
- [ ] Add BiometricPrompt CryptoObject unlock/initial-wrap operations; no success boolean without successful cryptographic use.
- [ ] Add VEK session object with controlled lifetime and best-effort mutable-buffer clearing.
- [ ] Add tests for wrong context, tampering, truncated/oversized envelopes, unsupported versions, key invalidation, and auth cancellation.
- [ ] Do not wire to the production UI until the primitive and device tests pass.

### Slice C — migration engine
- [ ] Implement idempotent journal transitions and test every crash point.
- [ ] Migrate documents one at a time under stable opaque IDs; verify new ciphertext before retiring old ciphertext.
- [ ] Re-wrap the SQLCipher key only after VEK is unlocked; close/reopen DB and verify integrity.
- [ ] Keep legacy keys/wrappers until the complete migration is durably marked and validated.
- [ ] Test corrupted files, missing aliases, insufficient storage, concurrent writes, process death, WAL/journal sidecars, and rollback.

### Slice D — enforce session boundaries
- [ ] Move repository/database creation behind the unlocked-session coordinator.
- [ ] Gate import, list, read, OCR, search, reminders, camera capture, and all background work through the same session.
- [ ] On lock, reject new work, cancel/drain in-flight work, close database handles, clear sensitive Compose state, and best-effort clear key buffers.
- [ ] Apply lifecycle policy, inactivity lock, explicit lock, and sensitive-screen redaction.
- [ ] Prove that no operation can return document plaintext after lock has completed.

### Slice E — release gates
- [ ] CI build, unit tests, lint, and offline merged-manifest gate pass on final implementation head.
- [ ] Instrumented tests pass on representative minimum and current Android versions.
- [ ] Physical-device testing covers biometric/device-credential fallback and key invalidation.
- [ ] Independent security review; accessibility, large text, and recovery UX review.
- [ ] Update completion percentages only when the relevant acceptance gates pass.

## Stop conditions

Stop and preserve data on any unknown key version, malformed journal, missing key with existing protected data, failed authenticated read-back, database integrity failure, or ambiguous migration state. Do not auto-delete legacy data or generate a replacement key to fix an error.
