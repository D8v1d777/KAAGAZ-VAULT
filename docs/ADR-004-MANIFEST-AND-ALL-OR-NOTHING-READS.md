# ADR-004: Authenticated manifest and all-or-nothing reads

**Status:** Proposal for review only — no production protocol approved  
**Date:** 2026-10-19  
**Scope:** Define the minimum manifest contract required before implementing chunked document reads, previews, OCR, or exports.

## Problem

Authenticating each chunk's AES-GCM ciphertext and AAD does not prove that the document is complete. A storage layer could omit, duplicate, reorder, truncate, or mix chunks from different document versions. Without an authenticated manifest and an all-or-nothing plaintext boundary, individual valid chunks could still be assembled into an incomplete or semantically incorrect document.

This ADR is a requirements and option analysis, not an implementation decision. Do not treat it as proof of complete E2EE.

## Required logical manifest fields

The authenticated manifest payload must bind:

- Protocol and manifest-format version.
- Algorithm suite identifier and key identifier.
- Opaque vault ID, document ID, and immutable document version.
- Canonical content type and chunk-format identifier.
- Exact chunk count and total plaintext length.
- An ordered entry for each chunk containing its index, nonce, and ciphertext length (including the AEAD authentication tag).
- Explicit maximum bounds for document length, chunk count, individual chunk size, and manifest size.

No plaintext document name, user note, OCR text, or other sensitive label should be included unless separately justified and protected. Content type, sizes, and chunk counts can still leak metadata if left visible; the threat model must state which fields are visible to the storage provider.

The manifest's canonical serialization must be versioned and unambiguous. Unknown fields, duplicate fields, invalid lengths, unsupported versions, integer overflow, and non-canonical encodings must fail closed.

## Authentication and key-separation options

| Option | Advantages | Costs / risks | Status |
|---|---|---|---|
| Encrypt/authenticate the manifest under the same per-document-version DEK as chunks, using a separate manifest AAD domain and a unique nonce | Fewer key objects; simpler hierarchy | Manifest and chunks share the DEK's nonce/usage budget; retry and crash state must cover every encryption operation; metadata binding still needs a reviewed manifest AAD schema | Candidate only |
| Derive a separate manifest key with a reviewed HKDF-SHA-256 context from a suitable root key | Clear purpose separation and independent nonce budget | Requires a defined root-key hierarchy and derivation API; the current non-extractable AES-GCM CryptoKey is not itself an HKDF base key | Candidate only |
| Generate a separate manifest key and wrap it with the selected key-wrapping design | Clear separation without deriving from the DEK | Adds wrapped-key metadata, lifecycle/recovery complexity, and the same unresolved Web Crypto wrapping constraints | Candidate only |

No option is selected here. Choose only after the VEK/DEK/KEK lifecycle, Web Crypto key-handling constraints, nonce budget, recovery, and threat model are reconciled and independently reviewed. Do not invent a custom MAC or wrapping primitive to avoid these decisions.

## Read/preview/export invariant

The implementation must not release any plaintext to a preview component, OCR engine, export path, search index, cache, or other consumer until all checks succeed.

1. Parse the outer envelope using strict schema and size limits before allocating large buffers.
2. Authenticate and validate the manifest.
3. Check declared bounds before iterating or allocating.
4. Require exactly the declared chunk count, each expected index exactly once, with no missing, duplicate, extra, out-of-order, or cross-version entries.
5. Validate nonce length and ciphertext length against the selected algorithm and tag size.
6. Authenticate every chunk using AAD that binds the same immutable vault/document/version/key context plus that chunk's index and format.
7. Check that the sum of verified plaintext chunk lengths equals the authenticated total plaintext length and stays within the configured limit.
8. Stage plaintext in a bounded, non-user-visible buffer or protected temporary sink until all chunks verify; only then atomically publish it to the next consumer.
9. On any error, discard staged output, return a generic failure to the caller, and retain encrypted source data for recovery. Do not silently return partial plaintext.

Secure deletion of discarded buffers is not guaranteed by JavaScript runtimes; the design must not claim physical memory erasure.

## Freshness and rollback

An authenticated manifest proves integrity under the relevant key; by itself, it does not prove that the manifest is the newest version. If an attacker can replace both the manifest and all related local metadata with an older valid snapshot, local authentication alone cannot detect rollback. The design must state what trusted monotonic state or external witness exists, what works offline, and what residual rollback risk remains. Do not claim anti-rollback until that mechanism is specified and tested.

## Required tests before implementation is accepted

- Modify every manifest field, its nonce/tag, chunk count, total length, and every per-chunk entry; all mutations must fail authentication or validation.
- Missing, duplicate, extra, reordered, cross-version, truncated, and oversized chunks must fail closed.
- Invalid manifest versions, integer overflow, duplicate/unknown fields, malformed lengths, and non-canonical encodings must fail before large allocations.
- No partial plaintext reaches preview, OCR, export, search, logs, or caches if a later chunk fails.
- Simulate crashes before encryption, after encryption before persistence, after persistence before upload, during upload, after server acknowledgement before local state update, and during restore.
- Retrying an upload reuses persisted ciphertext and nonce bytes; it must not re-encrypt a chunk under a reused key/nonce pair.
- Test maximum-size files and low-memory devices, plus property/fuzz tests for parser and serializer boundaries.
- Validate freshness/rollback behavior and recovery semantics.
- Run the exact repository suite on the exact candidate SHA and obtain independent cryptographic review before release.

## Decisions still open

- Maximum file size, chunk size/count, and memory budget.
- Manifest encryption and authentication construction.
- Manifest AAD field layout and domain separation.
- Whether key separation uses a derived manifest key or a separately wrapped key.
- GCM nonce generation, per-key limits, crash/retry behavior, and rotation.
- Trusted freshness/anti-rollback model for offline use.
- Recovery, key loss, passphrase change, and interrupted migration behavior.
- Independent reviewer and acceptance evidence.

## References

- NIST SP 800-38D, GCM IV uniqueness and usage constraints: https://csrc.nist.gov/pubs/sp/800/38/d/final
- W3C Web Cryptography Level 2, SubtleCrypto.wrapKey(): https://www.w3.org/TR/WebCryptoAPI/#SubtleCrypto-method-wrapKey
- OWASP Cryptographic Storage Cheat Sheet: https://cheatsheetseries.owasp.org/cheatsheets/Cryptographic_Storage_Cheat_Sheet.html
- OWASP Key Management Cheat Sheet: https://cheatsheetseries.owasp.org/cheatsheets/Key_Management_Cheat_Sheet.html

These sources define constraints; they do not independently validate this application's protocol.
