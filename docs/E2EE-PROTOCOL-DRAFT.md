# E2EE Protocol Draft — Phase 3 Design Gate

**Status:** Proposal only; not approved for production. No real user documents should be encrypted with this draft.  
**Scope:** Canonical authenticated metadata, envelope versioning, and key hierarchy.  
**Decision gate:** Independent cryptographic review and adversarial protocol tests required before implementation is treated as secure.

## 1. Security boundary and source basis

AES-GCM authenticates both ciphertext and Additional Authenticated Data (AAD), but AAD remains visible. The Web Cryptography API specifies AES-GCM parameters and its ciphertext/tag representation; NIST SP 800-38D specifies IV uniqueness and GCM requirements. The format must therefore authenticate metadata that affects how a document is interpreted, and must not place secrets in AAD.

Primary sources:
- W3C Web Cryptography API, AES-GCM: https://www.w3.org/TR/WebCryptoAPI/#aes-gcm
- NIST SP 800-38D: https://csrc.nist.gov/pubs/sp/800/38/d/final
- RFC 9106, Argon2: https://www.rfc-editor.org/rfc/rfc9106

These references establish primitive constraints, not the security of this proposed protocol.

## 2. Proposed envelope v1 (design only)

A serialized document record should have explicit fields, with strict allowlisting and bounds:

- `formatVersion`: unsigned integer, exactly `1`.
- `algorithm`: exact identifier `AES-256-GCM`; reject unknown values.
- `vaultId`: stable vault identifier, encoded canonically.
- `documentId`: stable random identifier, not a user filename.
- `documentVersion`: monotonically increasing unsigned integer.
- `keyId`: identifier for the DEK/key generation used; never the key itself.
- `contentType`: allowlisted/validated media type, lower-case ASCII representation.
- `chunking`: explicit format identifier and chunk size; absent for a one-shot record.
- `chunks`: ordered records containing nonce and ciphertext+tag; nonce is not secret.
- `manifest`: authenticated total plaintext length, chunk count and format version.
- `kdf` and `wrappedKeys`: versioned descriptors only when the reviewed key-wrapping design is adopted.

Reject duplicate fields, unknown critical fields, unsupported versions, malformed IDs, invalid numeric ranges, inconsistent lengths, oversized counts and trailing/ambiguous encodings. Never silently downgrade the algorithm or format.

### Canonical AAD encoding

Do **not** use casual delimiter concatenation or a generic JSON serializer as AAD. Property order, number formatting, Unicode and duplicate-key behavior can differ.

Draft direction: define a fixed-order binary encoding with a domain-separation prefix such as the ASCII bytes `KAAGAZ-VAULT-AAD\0\x01`, followed by length-prefixed fields and fixed-width big-endian integers. The specification must define exact byte lengths, maximum lengths, string normalization, integer widths, and whether optional fields are forbidden or encoded with explicit presence bytes. The decoder must reject non-canonical input rather than normalize it silently.

Per-chunk AAD should bind at least:
- protocol/domain version;
- vault ID and document ID;
- document version and key ID;
- content type;
- chunk index and chunk-format identifier.

The final authenticated manifest must bind chunk count and total plaintext length. If a format needs the count before encrypting chunks, it must precompute it; otherwise, use a separately authenticated final manifest and reject missing, duplicate or inconsistent manifests. Metadata authenticated as AAD is still visible to the storage service.

## 3. Key hierarchy proposal (not implemented)

- **Password/passphrase:** user secret used only as input to a reviewed password KDF; never use it directly as an AES key.
- **KEK:** derive from passphrase via Argon2id with a unique random salt and versioned, bounded parameters. Persist only salt/parameters and a wrapped-key record, not the passphrase or raw KEK.
- **VEK:** random vault encryption key, distinct from authentication credentials and distinct from document keys. Wrap it under the KEK using a reviewed authenticated wrapping scheme.
- **DEK:** fresh random data-encryption key per document or per immutable document version. Encrypt content with the DEK; wrap the DEK under the VEK.
- **Session key handles:** import keys as non-extractable where feasible after required wrapping operations; explicitly design the short-lived raw-key window and zeroization limits before coding. JavaScript garbage collection means reliable memory zeroization cannot be promised.
- **Recovery:** if recovery is supported, create a separately versioned recovery path with a high-entropy recovery secret and independent wrapped VEK. Recovery must not silently weaken encryption or allow the service to recover plaintext.

This hierarchy requires further review. The existing primitive returns a non-extractable CryptoKey and cannot simply be reused as an exportable wrapping key. Do not toggle extractability as a convenience or export raw key material without a reviewed lifecycle design.

## 4. KDF decision gate

RFC 9106 describes Argon2id and provides parameter recommendations, but its general-purpose high-memory recommendations cannot be copied blindly into a browser used on low-memory mobile devices. Before implementation:
1. Select a maintained, reviewed Argon2id implementation with known browser/WASM/runtime support and a documented supply-chain path.
2. Define accepted parameter profiles, minimums, maximums and a format version; reject attacker-controlled excessive memory/time settings before allocation.
3. Benchmark supported low-end devices and browsers; calibrate memory/time targets from evidence rather than guesses.
4. Use a fresh cryptographically random salt per vault/key derivation, stored with the KDF descriptor.
5. Add RFC 9106 test vectors and cross-implementation test vectors.
6. Document passphrase change, forgotten passphrase, recovery and KDF migration behavior.

Do not substitute PBKDF2 or a fast hash silently if Argon2id fails to load; failure must be explicit.

## 5. AES-GCM nonce and chunk constraints

- A 96-bit nonce is the selected primitive-level size; nonce uniqueness under a given key is mandatory.
- Random nonces alone do not establish a complete per-key usage policy. Set a reviewed cap for encryptions and total bytes per DEK, with a safe rotation/re-encryption strategy.
- Never retry an encryption with the same key/nonce pair after an interruption.
- Define how an upload resumes: new key/nonce strategy, or discard partial output and restart safely. Never reuse a prior nonce for modified plaintext.
- Use separate DEKs per immutable document version where practical; make replacement and deletion atomic from the user’s perspective.
- One-shot Web Crypto AES-GCM is not a large-file protocol. The Web Crypto API imposes an input length bound and practical memory constraints. Chunking must specify per-chunk authentication, ordering, finalization, truncation detection, memory bounds, and interrupted-upload behavior before implementation.

## 6. Required protocol tests

- Golden vectors for AAD byte encoding, envelope serialization and round-trip encryption.
- Reject unknown versions/algorithms, duplicate or unknown critical fields, non-canonical encodings and invalid lengths.
- Alter each authenticated metadata field independently; decryption must fail.
- Wrong key, modified ciphertext/tag/nonce, missing/empty AAD and malformed envelope.
- Chunk reorder, omission, duplication, truncation, append, wrong document/version/key ID and absent final manifest.
- Oversized counts, integer overflow, huge KDF parameters, corrupt salt and KDF version downgrade.
- Interrupted write/retry tests proving no key/nonce reuse.
- Cross-browser/runtime vectors and fuzzing of the parser.

## 7. Human factors and release gate

For users aged 20–90, explain in plain language that losing the passphrase and recovery secret may permanently lose access; do not disguise this as a routine login reset. Provide clear, accessible recovery setup, comprehension checks, large-text support and confirmation before irreversible operations. Avoid fear-based copy and false “your files are safe” claims.

**Release blockers:** canonical encoding frozen; threat model updated; key lifecycle and recovery reviewed; adversarial tests pass; accessibility/usability validation completed; exact-SHA CI green; independent cryptographic review recorded. Until all blockers close, the product must not claim production E2EE.
