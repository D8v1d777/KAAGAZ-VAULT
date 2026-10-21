# Security Hardening Integration Candidate — 2026-10-21

**Branch:** `agent/integration-security-hardening-candidate-2026-10-21`  
**Canonical base SHA:** `6b30ceaf5ab09b2494d82356b08c4bebf7406521`  
**Live candidate head / CI:** tracked in [draft PR #48](https://github.com/D8v1d777/KAAGAZ-VAULT/pull/48); use GitHub's current PR head SHA and its exact-head workflow run as authoritative evidence.  
**Status:** draft validation PR #48 is open. Exact-head CI passed on `8a3cd92d15234caf947f49f4bd69e0dacf0e642b` (run #466); subsequent documentation corrections trigger a fresh run, so the PR’s latest check is authoritative. No merge is requested.

## What is combined

This branch composes independently tested, current-base changes into one integration candidate so cross-PR behavior can be tested together:

1. **CO-08 migration journal:** new writes use explicit stable enum codes in journal format v2; the reader retains strict format-v1 ordinal compatibility; unknown stable codes fail closed; the coordinator no longer repeats staged verification.
2. **CO-04 unreadable documents:** corrupt/undecodable document ciphertext remains represented by an opaque internal ID and generic unreadable placeholder. Missing-key failures propagate instead of being treated as per-document corruption; search reconciles unreadable files before filtering.
3. **CO-01 document migration backend:** test-covered file-backed legacy→staging→published re-encryption, authenticated plaintext equivalence, separate roots, idempotent writes, and crash/replay test points. The crash matrix has been aligned to CO-08’s single coordinator verification call.
4. **Biometric prerequisite:** owner-approved `androidx.biometric:biometric:1.1.0` pin and exact-version source guard.
5. **VaultSession primitive:** 256-bit in-memory session key, temporary operation-scoped copy, best-effort clearing, lock waiting for active uses and preventing later uses.
6. **CO-02 recovery envelope primitive:** random 256-bit recovery key encoding and versioned AES-GCM envelope with wrong-key/tamper/unsupported-format tests.
7. **Session-backed document store seam:** `DocumentPayloadCryptography` supports legacy-key and session-key adapters; store read/write/replace/list/delete run within the crypto access callback, locked operations fail, startup artifact recovery is deferred until an authorized store operation, and ciphertext buffers are cleared after writes/reads where applicable.

## Session-backed document-store seam review

The production `MainActivity` now creates the home screen only from `VaultAccessUiState.Ready`, with repositories supplied by `VaultAccessController`; it no longer constructs the legacy document store or database provider directly. `SessionDocumentPayloadCryptography` keeps each protected store operation inside `VaultSession.withKey`. The controller runs the migration journal/backend before publishing the runtime, uses `SqlCipherDatabaseMigrationActions` to close/re-wrap/reopen SQLCipher and check `PRAGMA integrity_check`, and now closes the legacy provider on both success and failure. Legacy and staging ciphertext are deliberately retained; this is not permission to retire old data before device-level verification.

## What this candidate does not establish

- **CO-01 remains OPEN / P0 pending device verification.** Production UI wiring now gates access on the authenticated session and durable migration completion; source-level regression checks guard the wiring. Static review still cannot prove every asynchronous UI callback and device lifecycle edge. The reminder worker does not open SQLCipher, emits generic notification text, and cancellation now cancels scheduled work plus any displayed notification; a delivery gate suppresses in-process cancellation races and has unit tests.
- **CO-02 remains OPEN / P0 pending device verification.** Setup, auth-per-use unlock, persistent recovery envelope, exact recovery-key re-entry, recovery re-wrap and recovery-required UI are wired. Process-death, interrupted biometric prompts, malformed persistent state and real-device recovery still need instrumentation.
- The migration backend and SQLCipher key rewrap/integrity collaborator are wired into the controller, but CI uses unit tests and cannot establish device-specific Keystore/SQLCipher behavior or safe migration on real user data.
- Legacy ciphertext, staging copies and old key wrappers must not be retired until real-device authenticated read-back, SQLCipher reopen/integrity verification and durable journal completion are independently confirmed.
- Device/emulator instrumentation and independent human security review are not present. Unit/CI success cannot establish authenticator compatibility, screen-off/background locking, process-death recovery, or actual notification privacy on Android devices.

## Cross-PR compatibility check

The migration crash-injection test now exercises one coordinator-owned verification failure point after staging, matching the CO-08 sequence. The adapter independently authenticates staged content during its stage operation and rechecks at publication. The combined CI run must verify this assumption.

## Additional migration-journal regression coverage

Before the latest exact-head gate, the v1-reader tests were expanded to round-trip all journal phase states and every document migration step under the legacy ordinal encoding. New negative tests mutate v2 phase/document-step codes to unknown values and recalculate the CRC so rejection is tested independently of checksum mismatch. Stable-code tests now assert the complete explicit code sequences for both enums.

## Required next work

1. Re-run full CI on the post-documentation exact head and keep its result attached to PR #48.
2. Finish the changed-file audit and verify canonical README/HANDOFF remain unchanged.
3. Run Android device/emulator checks for setup/unlock/recovery, screen-off and app-background locking, interrupted migration, and reminder deletion/notification races.
4. Validate SQLCipher key rewrap, reopen, integrity check and legacy/staging retention on a disposable test vault before any retirement policy is considered.
5. Obtain independent human security review before release; do not merge or package as production-ready until the P0 device checks pass.
