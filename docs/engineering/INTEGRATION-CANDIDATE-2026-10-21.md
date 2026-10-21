# Security Hardening Integration Candidate — 2026-10-21

**Branch:** `agent/integration-security-hardening-candidate-2026-10-21`  
**Canonical base SHA:** `6b30ceaf5ab09b2494d82356b08c4bebf7406521`  
**Current integrated source SHA:** `83aa16a0c29a57962c65d547e1204d14688ddff8`  
**Status:** validation candidate only; PR / combined exact-head CI not yet created.

## What is combined

This branch composes independently tested, current-base changes into one integration candidate so cross-PR behavior can be tested together:

1. **CO-08 migration journal:** new writes use explicit stable enum codes in journal format v2; the reader retains strict format-v1 ordinal compatibility; unknown stable codes fail closed; the coordinator no longer repeats staged verification.
2. **CO-04 unreadable documents:** corrupt/undecodable document ciphertext remains represented by an opaque internal ID and generic unreadable placeholder. Missing-key failures propagate instead of being treated as per-document corruption; search reconciles unreadable files before filtering.
3. **CO-01 document migration backend:** test-covered file-backed legacy→staging→published re-encryption, authenticated plaintext equivalence, separate roots, idempotent writes, and crash/replay test points. The crash matrix has been aligned to CO-08’s single coordinator verification call.
4. **Biometric prerequisite:** owner-approved `androidx.biometric:biometric:1.1.0` pin and exact-version source guard.
5. **VaultSession primitive:** 256-bit in-memory session key, temporary operation-scoped copy, best-effort clearing, lock waiting for active uses and preventing later uses.
6. **CO-02 recovery envelope primitive:** random 256-bit recovery key encoding and versioned AES-GCM envelope with wrong-key/tamper/unsupported-format tests.

## What this candidate does not establish

- **CO-01 is still OPEN / P0.** `MainActivity` and the protected document/database/provider/worker paths are not yet enforced behind a successful auth-per-use CryptoObject operation and a single session boundary. The isolated `VaultSession` class does not authenticate callers.
- **CO-02 is still OPEN / P0.** Recovery is a cryptographic primitive, not a durable end-to-end recovery lifecycle. Persistent envelope storage, first-time setup, one-time display, safe-storage confirmation, recovery UI/state machine, process-death tests and device behavior remain outstanding.
- The document migration backend is not wired into production repository construction or the active storage root. It does not implement a real SQLCipher database-key rewrap, close/reopen, integrity-check collaborator, nor active-root cutover.
- Legacy ciphertext/key retirement remains prohibited until authenticated read-back, real SQLCipher reopen/integrity verification and durable journal completion are proven.
- Device/emulator instrumentation and independent human security review are not present.
- Unit/CI success cannot establish key invalidation behavior, authenticator compatibility, process/screen-off/background locking, notification privacy, or recovery on an actual Android device.

## Cross-PR compatibility check

The migration crash-injection test now exercises one coordinator-owned verification failure point after staging, matching the CO-08 sequence. The adapter independently authenticates staged content during its stage operation and rechecks at publication. The combined CI run must verify this assumption.

## Required next work

1. Run full exact-head CI on the combined branch.
2. Inspect any compile/test failures and fix only reproduced integration issues.
3. Inspect the full changed-file diff and confirm canonical README/HANDOFF were preserved.
4. Implement and test the real auth/session boundary plus SQLCipher/legacy document migration cutover without destructive key replacement.
5. Finish recovery lifecycle and device/instrumentation coverage.
6. Obtain independent human security review before release.
