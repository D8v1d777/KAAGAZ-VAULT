# Phase 3 Research Note — Canonical AAD Serialization Gate

**Status:** Research memo only. No encoding format is selected or approved.
**Checkpoint:** 2026-10-19
**Scope:** Choose what evidence is required before implementing the authenticated metadata (AAD) encoder/parser for KAAGAZ VAULT.
**Security boundary:** This memo is not a cryptographic review and does not establish production E2EE.

## Executive finding

RFC 8949 deterministic CBOR is a plausible candidate, but the candidate library's documented decoder limitations matter: `cborg` documents deterministic RFC 8949 encoding options and a duplicate-map-key rejection option, while also stating that decoding currently cannot enforce map-key ordering. Therefore, selecting CBOR would require an explicit strategy for rejecting non-deterministic wire encodings, strict schema validation, and dependency/runtime review. A fixed-order binary schema avoids a general-purpose map parser but creates a project-owned byte format that must be specified and tested exactly.

**Decision remains open.** Do not implement either format until the threat model, schema, negative cases, and golden-vector review are agreed.

## Primary sources reviewed

1. [RFC 8949 — CBOR](https://www.rfc-editor.org/rfc/rfc8949.html), especially deterministic encoding (§4.2) and duplicate map key handling.
2. [cborg project documentation](https://github.com/rvagg/cborg), including `rfc8949EncodeOptions`, `strict`, `allowIndefinite`, and `rejectDuplicateMapKeys`.
3. [OWASP Cryptographic Storage Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Cryptographic_Storage_Cheat_Sheet.html), covering authenticated modes, independent keys, key lifecycle, and GCM usage/nonce limits.
4. [W3C Web Cryptography API — AES-GCM](https://www.w3.org/TR/WebCryptoAPI/#aes-gcm).
5. [NIST SP 800-38D](https://csrc.nist.gov/pubs/sp/800/38/d/final), GCM requirements and IV uniqueness.

These sources describe constraints and library behavior; they do not validate KAAGAZ VAULT's proposed protocol.

## Candidate comparison

| Criterion | Deterministic CBOR | Fixed-order binary |
|---|---|---|
| Canonical encoding | RFC 8949 defines deterministic rules; implementation must use the RFC 8949 ordering, not legacy RFC 7049 ordering. | Project must specify every field width, order, length encoding, and optional-field rule. |
| Decoder ambiguity | Must reject duplicate keys, indefinite lengths, unsupported types/tags, out-of-schema values, trailing bytes, and non-deterministic representations. | Can use a narrow parser, but must reject invalid lengths, unknown versions, overflow, missing/extra bytes, and ambiguous optional fields. |
| Canonicality verification | cborg documents that map-key ordering is not currently enforced during decode. A reviewed raw-byte canonicality check may be required; simple parse-and-reserialize is insufficient unless unknown-field and parser behavior are tightly controlled. | Exact grammar and byte comparisons can make canonicality straightforward if the parser is small and fully tested. |
| Dependency/supply chain | Adds a runtime dependency; verify package provenance, maintenance, browser support, bundle impact, license, and lockfile/integrity controls. | Fewer runtime dependencies, but more bespoke security-sensitive code to maintain and review. |
| Interoperability | Better potential for future multi-language tooling if a narrow profile is specified. | Interoperability depends on the quality and stability of the written specification. |
| Main risk | A general-purpose parser accepting alternate encodings or ambiguous maps. | A bespoke format with undocumented edge cases or implementation drift. |

## Required decision questions

Before choosing:
- What is the actual attacker model for local storage, server storage, malicious uploads, compromised application updates, and a malicious same-origin script?
- Will authenticated metadata be a compact fixed schema or need future extensibility?
- Which maintained parser can run in supported browsers and low-end mobile devices without excessive bundle or memory cost?
- Can the implementation reliably reject duplicate keys and non-deterministic encodings before using the decoded values?
- What are the maximum encoded AAD length, identifier lengths, version ranges, and content-type allowlist?
- How will protocol upgrades avoid downgrade or mixed-version acceptance?
- Who will independently review the final byte-level specification and test vectors?

## Proposed AAD semantic schema (not byte encoding)

Each chunk's AAD should bind the exact values that affect interpretation:
- domain-separation identifier and protocol version;
- vault identifier;
- random document identifier;
- immutable document version;
- key identifier;
- allowlisted canonical content type;
- chunk format identifier and chunk index.

A separately authenticated final manifest must bind total plaintext length and chunk count. Do not put secrets, passphrases, raw keys, or other confidential values into AAD: AAD is authenticated but visible.

All values must be bounded and validated before serialization. Define UTF-8 handling, normalization policy, integer widths/ranges, absent-versus-empty semantics, and whether unknown fields are forbidden or explicitly versioned. Do not use casual delimiter concatenation or a generic JSON serializer.

## Golden-vector plan

Once the encoding is selected, commit independent, reviewable vectors containing:
1. A minimal valid record with all mandatory fields.
2. A record with maximum permitted identifier lengths and boundary integer values.
3. One chunk with a non-zero chunk index.
4. Exact expected AAD bytes in lowercase hex, plus the semantic input fields.
5. A stable SHA-256 digest of the expected AAD bytes as a convenience check (not a substitute for byte equality).
6. Rejection vectors for duplicate fields/keys, non-canonical encoding, unknown version/algorithm, invalid UTF-8 or normalization, overlong IDs, integer overflow, trailing bytes, and missing required fields.
7. Mutation tests proving that changing each authenticated field causes AES-GCM authentication failure.
8. At least one independently produced vector from a second implementation or a small reference encoder reviewed by a human.

Expected bytes must be independently calculated and reviewed. Do not derive both implementation output and “expected” fixtures from the same encoder code.

## Acceptance gate for a future implementation

- [ ] Format decision and rationale recorded in ADR.
- [ ] Exact grammar/schema, bounds, domain separation, and version policy reviewed.
- [ ] Dependency provenance, license, maintenance, browser compatibility, and parser limitations recorded if using a library.
- [ ] Duplicate/unknown critical fields and non-canonical encodings fail closed.
- [ ] Golden byte vectors independently checked.
- [ ] Positive, negative, mutation, fuzz, and cross-runtime tests added.
- [ ] Exact-head CI completes successfully.
- [ ] Independent cryptographic reviewer signs off on the protocol and key lifecycle.

Until these conditions are satisfied, retain the current experimental AES-GCM primitive only and do not claim complete or production-ready E2EE.
