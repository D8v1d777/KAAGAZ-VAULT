# Phase 3 implementation log — E2EE primitives

**Status:** In progress. This log records implementation facts and acceptance evidence; it is not a security certification.

## Current bounded slice

- Branch: `feat/e2ee-and-human-centered-design`
- Previous base before this slice: `f3573d26b53bac641b14af2b8802d41f74b48c91`
- New implementation commits: `8ac52c4b32245ec348a368ea3ede8b46d0fc76cd` (primitive hardening) and `a8dc989a7baeb0af8c58bb8c94b4107b22c4eb8f` (tests).
- The wrapper still uses the platform Web Crypto API and AES-256-GCM. It refuses insecure fallback when Web Crypto is unavailable.
- Encryption and decryption now require non-empty associated authenticated data (AAD). This makes it harder for callers to accidentally encrypt without binding ciphertext to document/version metadata. The caller still must define a canonical encoding; arbitrary string concatenation is not a final protocol.
- Key checks now require AES-GCM, a 256-bit key, and the appropriate `encrypt` or `decrypt` usage.
- Tests cover non-extractable key properties, round-trip, tampering, wrong key, unsupported version, malformed nonce, truncated ciphertext, missing/empty AAD, AES-128 rejection, usage mismatch, bad input and missing Web Crypto.
- The API remains a primitive only. It does not implement Argon2id, a VEK/DEK hierarchy or key wrapping, chunked-file encryption, nonce persistence/retry coordination, recovery, storage integration, key rotation, or complete E2EE.

## Verification gates

- [ ] GitHub Actions succeeds for the exact current head SHA.
- [ ] Full `npm test` passes, including static/docs/crypto/UI suites.
- [ ] Review actual CI logs for any first failure; do not infer success from a queued or cancelled run.
- [ ] Define a canonical, unambiguous AAD schema and versioned envelope before wiring real documents.
- [ ] Design key hierarchy, KDF and recovery before adding any persistent key material.
- [ ] Add adversarial tests for chunk truncation/reordering, interrupted writes and retry/nonce policy before chunking.
- [ ] Review browser-delivery/supply-chain residual risk and complete independent cryptographic review before real sensitive documents.

## Security note

A mandatory AAD parameter reduces accidental misuse but does not prove correct metadata binding unless callers use a reviewed canonical schema. Random 96-bit GCM nonces are not a substitute for a documented per-key usage limit and retry/interruption policy. Do not use real documents with this experimental wrapper.


## Canonical chunk AAD candidate — 2026-10-19

- Added `src/crypto/chunk-aad.mjs`: strict fixed-order binary encoder with a domain separator, protocol version, bounded opaque identifiers, allowlisted content types, explicit chunk-format enum and uint32 big-endian version/index fields. No parser or decoder was added.
- Added `scripts/chunk-aad.test.mjs`: byte-exact golden fixture, strict schema and boundary rejection, per-field differentiation, and AES-GCM metadata-tampering checks.
- Added `docs/ADR-003-CHUNK-AAD-ENCODING.md` describing the candidate format and release gates. Decision status remains **proposed, awaiting independent review**.
- Included the new test in `npm run test:crypto`.
- This is not yet merged and has not been independently reproduced in a second implementation. Do not treat this as a frozen protocol or use real documents. CI must run on the exact PR head; the current editor has not executed the repository test suite locally.
- Remaining blockers: independent golden-vector verification; manifest authentication and chunk completion semantics; key hierarchy/wrapping; Argon2id library/parameters; nonce usage/retry/rotation policy; recovery UX; fuzz/cross-runtime tests; independent cryptographic review.
