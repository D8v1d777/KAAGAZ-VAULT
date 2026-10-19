# Phase 3 research memo — manifest, nonce and key lifecycle

**Status:** Design candidate for review only. No protocol or key lifecycle is approved.
**Date:** 2026-10-19
**Purpose:** Prevent implementation from locking in a chunk format before nonce/retry, manifest, recovery and key lifecycle semantics are considered together.

## Primary-source constraints

- NIST SP 800-38D requires GCM IV/key-pair uniqueness and describes both deterministic and random IV constructions. It also warns that loss of state must not cause IV repetition. The publication is marked for revision, so re-check the current NIST status before final protocol approval: https://csrc.nist.gov/pubs/sp/800/38/d/final
- OWASP recommends authenticated encryption, independent keys for distinct purposes, tested key-management processes, and enforcing mode-specific usage/IV limits before rotation: https://cheatsheetseries.owasp.org/cheatsheets/Cryptographic_Storage_Cheat_Sheet.html and https://cheatsheetseries.owasp.org/cheatsheets/Key_Management_Cheat_Sheet.html
- OWASP Password Storage guidance recommends Argon2id and explicitly advises benchmarking memory/time parameters on target systems; this is password-storage guidance, not by itself a complete vault key-wrapping protocol: https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html
- RFC 9106 specifies Argon2 and its parameter model: https://www.rfc-editor.org/rfc/rfc9106

These sources establish constraints; they do not validate this application's proposed design.

## Argon2id implementation selection — evidence gathered 2026-10-19

No KDF dependency is selected. Initial source review found two plausible browser-compatible candidates, neither ready for automatic adoption:

- `hash-wasm` advertises Argon2id v1.3 in WebAssembly, browser/Node support, and an MIT license. Its npm page currently lists version 4.12.0 with a last publication roughly two years ago. Review release activity, reproducible WASM build provenance, memory behavior on low-end devices, and exact Argon2 version/parameters before considering it.
- `@noble/hashes` provides an Argon2id implementation and has an MIT license, but its own README states the Argon2 implementation was outside the scope of the referenced independent audit and warns that pure JavaScript Argon2 can be slow. Do not treat the library-wide audit claim as an Argon2 audit.

Neither package has been installed or benchmarked in this repository. Next gate: inspect exact source/tag, license, transitive/runtime requirements, upstream security policy and Argon2 test vectors; then benchmark realistic memory/time parameters on the lowest supported device class. Select nothing based on package popularity or bundle size alone.

Sources: [hash-wasm npm package](https://www.npmjs.com/package/hash-wasm), [hash-wasm repository](https://github.com/Daninet/hash-wasm), [noble-hashes repository and audit scope](https://github.com/paulmillr/noble-hashes/), [RFC 9106 Argon2](https://www.rfc-editor.org/rfc/rfc9106).

## Current NIST revision status — checked 2026-10-19

NIST's current publication page lists **SP 800-38D Rev. 1 (2nd Preliminary Draft)**, published 2026-06-01. The page explicitly says there is not yet an actual draft document; NIST is gathering feedback on proposed revision topics, including GCM tag sizes, variable-length IVs, and a wider-block variant. It is not a finalized replacement for the 2007 SP 800-38D final.

The superseded January 2025 initial preliminary page discussed the (2^{32}) invocation bound for the described random-nonce construction. That statement belongs to the earlier pre-draft page, not the current second preliminary page; do not attribute it to the current draft. The final 2007 publication remains the current final reference while revision work continues.

Implications:
- Re-check NIST's publication status before protocol freeze; distinguish final standards from preliminary calls for comments.
- Derive the app's GCM usage policy from the applicable current final guidance, then choose a conservative, explicit per-key cap based on approved constructions, maximum file/chunk sizes, and failure/retry behavior.
- Enforce the selected cap and rotate to a fresh key/version before reaching it; logging or monitoring alone is not enforcement.

Sources: NIST CSRC, [SP 800-38D Rev. 1 (2nd Preliminary Draft)](https://csrc.nist.gov/pubs/sp/800/38/d/r1/2prd), [superseded initial preliminary page](https://csrc.nist.gov/pubs/sp/800/38/d/r1/iprd), and [SP 800-38D (2007 final)](https://csrc.nist.gov/pubs/sp/800/38/d/final).

## Current NIST key-wrapping revision status — checked 2026-10-19

NIST's current SP 800-38F Rev. 1 page is an **initial preliminary call for comments** dated 2026-05-05, not a draft replacement standard. It says NIST plans to revisit approval for unspecified combinations of an approved encryption mode with an approved authentication method because such combinations may introduce unexpected vulnerabilities. The existing 2012 SP 800-38F final specifies AES-KW and AES-KWP as deterministic authenticated-encryption key-wrapping modes.

Implications:
- Do not create an ad hoc "encryption + MAC" key-wrapping composition to work around Web Crypto constraints.
- Compare fully specified, standardized constructions and their target-runtime support; document the exact mode, key sizes, metadata-binding mechanism, and failure behavior.
- Re-check the revision status before protocol freeze. The pre-draft page requests feedback; it is not itself a finalized new requirement.

Sources: NIST CSRC [SP 800-38F Rev. 1 initial preliminary page](https://csrc.nist.gov/pubs/sp/800/38/f/r1/iprd) and [SP 800-38F (2012 final)](https://csrc.nist.gov/pubs/sp/800/38/f/final).

## Recommended design direction (not approved)

### 1. Key roles and separation

- **VEK (Vault Encryption Key):** random 256-bit root secret generated client-side. The server never receives it in plaintext.
- **KEK (Key Encryption Key):** derived from the vault passphrase using a reviewed Argon2id implementation, a unique random salt, versioned parameters, and a benchmarked profile. Account login password and vault passphrase remain separate. No silent downgrade to a weaker KDF.
- **DEK (Data Encryption Key):** fresh random 256-bit key per immutable document version. A DEK is used only for that version's content chunks; edits/re-encryption create a new version and a fresh DEK.
- **Recovery wrapping:** a separate high-entropy recovery secret protects an independent wrapped copy of the VEK. Do not reuse the passphrase-derived KEK or a DEK as the recovery key.
- Use purpose separation: do not use one raw key for content encryption, key wrapping, manifest protection, and any future authentication purpose. A reviewed HKDF context scheme or standardized key-wrapping construction must be chosen before implementation.

### 2. VEK and DEK wrapping options to review

Do not settle the wrapping algorithm by convenience alone. Compare:
- AES-GCM wrapping with a fresh 96-bit nonce, authenticated metadata, and explicit nonce/usage accounting; versus
- a standardized key-wrapping mode such as AES-KW, optionally using a context-derived wrapping key if protocol review approves it.

Whichever route is selected, define the exact envelope fields, authenticated context, version/downgrade rules, failure behavior, key rotation, and known-answer tests. Never invent a custom wrapping primitive. Confirm supported Web Crypto behavior on the target browsers before selecting.

**Compatibility gap found in the current primitive wrapper:** `generateDocumentKey()` returns a non-extractable AES-GCM key. That is a good default for direct content use, but it cannot be passed straight into a Web Crypto key-wrapping flow that requires exporting/wrapping an extractable key. Before implementing the hierarchy, design and test a dedicated lifecycle that generates or derives a DEK, wraps it, and makes the content-use key non-extractable without persisting raw key bytes. Do not weaken the existing API's non-extractability globally just to make wrapping convenient. Record the brief exposure window, cleanup behavior, algorithm usages, and failure/retry semantics in the ADR.

### 2a. Web Crypto wrapping constraint — verified behavior, design still open

The W3C Web Cryptography API specifies that `wrapKey()` requires the wrapped key's export operation, while `unwrapKey()` imports the resulting key. A key generated with `extractable: false` therefore cannot be passed directly to `wrapKey()`. This is a platform API constraint, not a reason to weaken `generateDocumentKey()` globally.

The repository's new regression test attempts to wrap the current non-extractable AES-GCM document key using an AES-KW wrapping key and asserts that the operation rejects. Exact-head CI must confirm this test passes before the observation is treated as verified for the supported runtime.

Design consequences to review:
- If the product accepts transient raw-key exposure, one candidate is a narrowly scoped lifecycle that creates an extractable DEK, wraps it, exports/imports it into a non-extractable content-use key, and immediately discards temporary references. This still exposes raw key bytes to JavaScript memory and must not be described as equivalent to never exposing them.
- If transient raw-key exposure is unacceptable, investigate a platform-backed key store or a different architecture whose supported API can meet the requirement; do not invent custom key wrapping.
- Specify recovery, reload, crash, passphrase change, rotation, and cleanup semantics before selecting either option.

Source: W3C [Web Cryptography API — wrapKey and unwrapKey](https://www.w3.org/TR/webcrypto/), especially the algorithm requirements for exportKey on the wrapped key and importKey on the unwrapped key.

### 3. Chunk nonce and retry policy

Candidate direction: fresh random 96-bit nonce per content-chunk encryption under the version's DEK, with an enforced maximum number of encryptions and maximum byte/chunk limits derived from the selected GCM policy and actual product file-size limits. Do not choose numeric limits until chunk size, maximum file size, per-version key scope, and NIST usage constraints have been reconciled.

Rules that should be mandatory:
- Never intentionally invoke AES-GCM twice with the same key and nonce.
- Upload retry reuses the exact already-produced ciphertext/nonce bytes; it does not call encryption again.
- If the client cannot determine whether encryption output was durably retained, do not blindly repeat an encryption operation with the same key/nonce. Use a reviewed crash-safe state transition; if the operation's nonce/key state is ambiguous, abandon that immutable version and create a fresh DEK/version.
- Persist the nonce together with its ciphertext in one atomic local record before upload, or prove a safe alternative in the storage transaction design.
- A per-document-version DEK limits the scope of nonce accounting but does not remove the need to enforce a per-DEK cap and crash/retry semantics.
- Nonce generation, limits, and rotation must be tested across interrupted writes, duplicate requests, offline retry, browser restart, concurrent tabs, and restore from backup.

A deterministic nonce such as a random prefix plus chunk index may simplify uniqueness only if the design guarantees that each index is encrypted at most once under that DEK across every retry/crash path. That guarantee is not yet established; do not implement this construction now.

### 4. Authenticated manifest and all-or-nothing reads

The manifest should authenticate at least:
- protocol/format version and algorithm identifiers;
- opaque vault/document identifiers, immutable document version, and key ID;
- canonical content type;
- chunk format, chunk count, total plaintext length, and ordered chunk indices;
- expected ciphertext lengths (including authentication tags) and any bounded per-chunk metadata;
- enough immutable context to detect stale/replayed manifest versions.

Manifest protection must have explicit domain separation from content chunks. The exact encoding and whether the manifest is encrypted or authenticated in a separate envelope are open decisions. Avoid storing plaintext names or notes in server-visible manifest fields.

Read/preview/export rules:
1. Validate envelope/schema bounds before allocating large buffers.
2. Authenticate the manifest and enforce limits before processing its chunk list.
3. Require every expected chunk exactly once; reject missing, duplicate, out-of-order, oversized, truncated, or extra chunks.
4. Verify every chunk's GCM tag against AAD binding the immutable version and chunk index.
5. Do not expose partial plaintext to preview, OCR, export, or other features. For bounded MVP sizes, stage decrypted chunks in a quarantined in-memory sink and publish only after full validation. For larger files, design a bounded, reviewed temporary sink with equivalent all-or-nothing semantics before supporting them.
6. On any failure, discard staged plaintext, return a non-sensitive error, and avoid logs containing filenames, plaintext, keys, or signed URLs.

A manifest MAC/tag does not prove the server returned the latest version; anti-rollback requires a separately considered freshness strategy (for example, trusted client state or a reviewed signed/versioned checkpoint). Availability and withholding remain outside cryptographic confidentiality guarantees.

### 5. Passphrase and recovery lifecycle

- Version the KDF identifier, salt, and parameters; reject unknown algorithms/versions and malformed values before expensive derivation.
- Benchmark Argon2id on the lowest supported device class; rate-limit unlock attempts where possible without making offline vault recovery depend on server availability.
- Passphrase change should re-wrap the VEK rather than re-encrypt all document content, if the approved hierarchy supports that.
- Recovery must be explicitly tested from a clean second device with only the recovery material and ciphertext. Recovery secret should be shown once, never sent to the server in plaintext, and verified with a user-held challenge before first valuable upload.
- Loss of both passphrase and recovery secret means the service cannot restore plaintext; the UX must explain this before use.
- Define what happens when a passphrase or recovery secret is rotated, revoked, or suspected compromised, including backup retention and old wrapped-key retirement.

## Decisions still required before implementation

- Supported browser/device baseline and maximum document size.
- Chunk size, maximum chunk count, and per-DEK GCM invocation/data budget.
- Exact VEK wrapping and DEK wrapping constructions, context encoding, and rotation rules.
- Whether manifest metadata is encrypted, authenticated separately, or both.
- Crash-safe persistence boundary for ciphertext + nonce + upload state.
- Anti-rollback/freshness policy.
- Argon2id library provenance, license, version pinning, target benchmark profile and migration strategy.
- Recovery-secret format and recovery UX.
- Independent cryptographic reviewer and review checklist.

## Acceptance tests before release

- Wrong passphrase, wrong VEK, corrupt wrapped VEK/DEK, unsupported KDF/version, and corrupted recovery material all fail closed.
- Tampering with each manifest field, each chunk's ciphertext/nonce/AAD, chunk count, chunk order, length, duplicate index, and document version is rejected.
- No preview/export/OCR receives plaintext until all expected chunks and the manifest validate.
- Simulate crash at each boundary: before encryption, after encryption before persistence, after persistence before upload, during upload, after server acknowledgement before local state update, and during restore.
- Duplicate upload/retry returns the same ciphertext object and does not re-encrypt under a reused key/nonce pair.
- Maximum bounds and resource usage are tested on low-memory devices.
- Exact-head CI passes; independent cryptographic review signs off; no real sensitive documents are used before release gates pass.

## Current decision

Keep this as a design memo. Do not implement key wrapping, Argon2id, chunk encryption, or manifest parsing until the above choices are reconciled and reviewed with the AAD schema. Phase 3 progress remains unchanged.


## Additional primary-source verification — Web Crypto key wrapping — 2026-10-19

The Web Cryptography Level 2 specification's `SubtleCrypto.wrapKey()` algorithm explicitly rejects a key whose `[[extractable]]` slot is false. MDN documents the same behavior and explains that `wrapKey()` exports the key before encrypting it. Therefore the current `generateDocumentKey()` implementation, which intentionally creates a non-extractable AES-GCM key, cannot be passed directly to `wrapKey()`. This is a confirmed API constraint, not merely a test failure.

Primary sources:
- W3C Web Cryptography Level 2, §14.3.11 `wrapKey`: https://www.w3.org/TR/WebCryptoAPI/#SubtleCrypto-method-wrapKey
- MDN `CryptoKey.extractable`: https://developer.mozilla.org/en-US/docs/Web/API/CryptoKey/extractable
- MDN `SubtleCrypto.wrapKey()`: https://developer.mozilla.org/en-US/docs/Web/API/SubtleCrypto/wrapKey
- NIST SP 800-38D, GCM IV uniqueness and loss-of-state considerations: https://csrc.nist.gov/pubs/sp/800/38/d/final
- RFC 9106, Argon2 parameter-selection procedure: https://www.rfc-editor.org/rfc/rfc9106

Design implications (still unresolved; do not implement from this note alone):
1. Decide the threat model for JavaScript-visible raw DEK bytes and transient extractability. Non-extractable keys reduce export paths but do not protect a live page from same-origin script that can invoke permitted cryptographic operations.
2. Compare a narrowly scoped, explicit import/wrap lifecycle against keeping a non-extractable session key and separately persisting a wrapped key envelope. Specify exactly when raw bytes may exist, how they are zeroed best-effort, and how failures avoid leaving partial key state.
3. Choose wrapping algorithm and authenticated context together. AES-KW is standardized, but it does not expose a GCM-style AAD parameter; if metadata binding is needed, the protocol must define how it is authenticated without inventing a custom primitive. AES-GCM wrapping brings nonce uniqueness/retry requirements of its own.
4. Define what happens on tab/process restart, duplicate requests, storage failure, interrupted writes, passphrase change, recovery, and key rotation. Add known-answer tests and independent review before accepting the protocol.
5. Do not claim that zeroing a JavaScript byte array guarantees physical memory erasure; runtimes may make copies and garbage collection is outside application control.

This research confirms a hard constraint and narrows the design space; it does not select or approve a key-wrapping protocol.
