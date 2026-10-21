# CO-01 — In-memory VEK session primitive clean port

## Purpose
Port the already-tested in-memory `VaultSession` primitive onto the current canonical source base. The primitive copies a 256-bit verified VEK into a session, yields only temporary key copies inside a callback, clears those copies when callback exits, and serializes lock against active key uses.

## Baseline
- Canonical branch `feat/durable-vault-migration-journal` SHA `6b30ceaf5ab09b2494d82356b08c4bebf7406521`.
- Prior implementation branch was based on stale SHA `789816fece4b90bb981b9e9cb3a5954f1eebd514`; its exact-head CI does not verify this clean port.
- This document describes the original primitive-only plan at its canonical baseline; do not use that historical baseline statement to characterize the separate combined candidate PR #48. On candidate branch `agent/integration-security-hardening-candidate-2026-10-21`, MainActivity gates home creation on `VaultAccessUiState.Ready` and repositories use `VaultSession`. The combined candidate remains unverified until exact-head CI, Android device checks, and human security review pass.

## Tests
- Locked session rejects key access.
- Only 32-byte VEK values are accepted; caller-buffer mutations do not affect the session.
- Temporary key copies are cleared after success and thrown exceptions.
- A lock waits for active key operations and prevents new uses after lock completion.
- An already-unlocked session cannot be silently replaced with another key.

## Safety boundaries / not done
- Authentication and persistence happen elsewhere; `installVerifiedKey` may only be called after the approved auth-per-use cryptographic operation succeeds and wrapped key state is durably persisted.
- Candidate integration routes the home screen through the controller/session and closes the active database provider on lock; async UI lifecycle, external activity-result behavior, process death and device-specific Keystore/SQLCipher behavior remain to be verified. This plan file remains the historical scope of the primitive-only slice.
- In particular, clearing this session does not close an already-open SQLCipher/Room handle or revoke a separate legacy non-auth-bound document key. Never describe this PR alone as a vault lock.
- No recovery-key storage, DB migration, manifest, dependency or payload-format changes are included.
- Local Android tests unavailable in the active environment; require exact-head CI and independent human review.

## Candidate follow-through — 2026-10-21

The combined candidate also adds best-effort plaintext buffer clearing in import streams, serialized document payloads, OCR updates, AES-GCM envelope operations and settings-transfer crypto. Controller disposal invalidates late biometric callbacks and clears retained wrapped-key state before cancelling authentication. These are follow-through changes outside the original session primitive slice; see `docs/engineering/INTEGRATION-CANDIDATE-2026-10-21.md` and `HANDOFF.md` for the candidate gate and limitations.
