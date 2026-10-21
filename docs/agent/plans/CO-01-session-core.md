# CO-01 — In-memory VEK session primitive clean port

## Purpose
Port the already-tested in-memory `VaultSession` primitive onto the current canonical source base. The primitive copies a 256-bit verified VEK into a session, yields only temporary key copies inside a callback, clears those copies when callback exits, and serializes lock against active key uses.

## Baseline
- Canonical branch `feat/durable-vault-migration-journal` SHA `6b30ceaf5ab09b2494d82356b08c4bebf7406521`.
- Prior implementation branch was based on stale SHA `789816fece4b90bb981b9e9cb3a5954f1eebd514`; its exact-head CI does not verify this clean port.
- Current canonical production routes still do not require a session. This is a primitive only; no end-to-end authentication claim follows from adding it.

## Tests
- Locked session rejects key access.
- Only 32-byte VEK values are accepted; caller-buffer mutations do not affect the session.
- Temporary key copies are cleared after success and thrown exceptions.
- A lock waits for active key operations and prevents new uses after lock completion.
- An already-unlocked session cannot be silently replaced with another key.

## Safety boundaries / not done
- Authentication and persistence happen elsewhere; `installVerifiedKey` may only be called after the approved auth-per-use cryptographic operation succeeds and wrapped key state is durably persisted.
- Production `MainActivity`, document repository, metadata/index, SQLCipher/Room, OCR/import/export/share and worker paths remain ungated until separately integrated.
- In particular, clearing this session does not close an already-open SQLCipher/Room handle or revoke a separate legacy non-auth-bound document key. Never describe this PR alone as a vault lock.
- No recovery-key storage, DB migration, manifest, dependency or payload-format changes are included.
- Local Android tests unavailable in the active environment; require exact-head CI and independent human review.