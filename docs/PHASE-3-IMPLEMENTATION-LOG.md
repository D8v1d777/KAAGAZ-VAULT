# Phase 3 implementation log — E2EE primitives

**Status:** In progress. This log records implementation facts and acceptance evidence; it is not a security certification.

## Current bounded slice

- Branch: `feat/e2ee-and-human-centered-design`
- Previous base before this slice: `f3573d26b53bac641b14af2b8802d41f74b48c91`
- New implementation commits: `8ac52c4b32245ec348a368ea3ede8b46d0fc76cd` (primitive hardening) and `a8dc989a7baeb0af8c58bb8c94b4107b22c4eb8f` (tests).
- The wrapper still uses the platform Web Crypto API and AES-256-GCM. It refuses insecure fallback when Web Crypto is unavailable.
- Encryption and decryption require non-empty associated authenticated data (AAD). This makes it harder for callers to accidentally encrypt without binding ciphertext to document/version metadata. The caller still must define a canonical encoding; arbitrary string concatenation is not a final protocol.
- Key checks require AES-GCM, a 256-bit key, and the appropriate `encrypt` or `decrypt` usage.
- Existing primitive tests cover non-extractable key properties, round-trip, tampering, wrong key, unsupported version, malformed nonce, truncated ciphertext, missing/empty AAD, AES-128 rejection, usage mismatch, bad input and missing Web Crypto.
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
- Hardened exact-record validation after review found that `Object.keys()` ignores symbol and non-enumerable properties and that accessor properties could execute caller code during field reads. The encoder now checks `Reflect.ownKeys()` and rejects non-enumerable fields and accessors.
- Added `scripts/chunk-aad.test.mjs`: byte-exact golden fixture, strict schema and boundary rejection, per-field differentiation, hidden/accessor-field rejection, and AES-GCM metadata-tampering checks.
- Added `scripts/reference/chunk_aad_reference.py`, a separate Python implementation using `struct` and byte concatenation. Local reference run passed for the 38-byte golden vector `4b5641440100027631000264310000000100026b31000a746578742f706c61696e0100000000`. This verifies the vector across two implementations but is not independent human cryptographic review.
- Added `docs/ADR-003-CHUNK-AAD-ENCODING.md` describing the candidate format and release gates. Decision status remains **proposed, awaiting independent review**.
- Included the new test in `npm run test:crypto`.
- The candidate remains unmerged and is not a frozen protocol. Exact-head GitHub Actions must pass. No full repository test suite has been run locally in this environment.
- Remaining blockers: independent human review; adversarial fuzz/cross-runtime tests; authenticated manifest and chunk completion semantics; key hierarchy/wrapping; Argon2id library/parameters; nonce usage/retry/rotation policy; recovery UX; independent cryptographic review.


## Additional validation fix — 2026-10-19

- Review identified a JavaScript end-anchor edge case: `$` can match immediately before a final line terminator, so the prior identifier pattern could accept some values outside the documented ASCII identifier grammar (for example `"v1\\n"`).
- Updated `src/crypto/chunk-aad.mjs` to use an absolute-end assertion and added regression cases for LF, CR, U+2028, and U+2029 in identifier fields. The Python reference encoder already uses `fullmatch` and rejects these characters.
- A local isolated Node.js 22 snapshot reconstructed from the fetched implementation/test files passed **7/7 focused AAD tests**; an additional smoke check rejected **4/4** line-terminator inputs. The separate Python reference snapshot reproduced the 38-byte golden vector. These are local snapshot results, **not** repository CI or an exact-checkout test run.
- Exact-head GitHub Actions and combined commit status queries for the new PR head returned empty lists. CI remains **unverified**; no claim of green CI is made.
- Next: obtain exact-head CI evidence if available; then add bounded property/fuzz cases for malformed identifier inputs and test that JavaScript/Python implementations reject the same invalid grammar. Do not expand into key wrapping/manifest code before design review.
