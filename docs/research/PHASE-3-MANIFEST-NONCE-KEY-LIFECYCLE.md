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
