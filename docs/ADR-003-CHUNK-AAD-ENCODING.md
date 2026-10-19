# ADR-003: Chunk AAD encoding — fixed-order binary candidate

**Status:** Proposed implementation candidate; independent security review required before protocol freeze.
**Date:** 2026-10-19
**Scope:** Canonical byte encoding for per-chunk AES-GCM additional authenticated data (AAD).

## Decision rationale

Use a small, fixed-order binary encoding for the current narrow chunk metadata schema rather than introducing a general-purpose CBOR decoder at this stage. The implementation has no decoder, rejects unknown/missing properties, bounds identifiers, allowlists content types, and uses explicit big-endian integer fields. It also rejects symbol/non-enumerable extra properties and accessor fields so the exact-schema check cannot be bypassed through ordinary JavaScript object descriptors.

This is a candidate, not final cryptographic approval. RFC 8949 remains a viable alternative if the project later needs general structured metadata, but any CBOR profile must specify deterministic encoding and reject duplicate keys and disallowed alternate encodings. RFC 8949 discusses deterministic encoding in §4.2 and duplicate-key validity in §5.6: https://www.rfc-editor.org/rfc/rfc8949.html

## Byte layout, version 1

All offsets are byte offsets. Integers are unsigned, big-endian. String fields are ASCII; this removes Unicode normalization ambiguity for this version.

| Order | Field | Encoding / bounds |
|---:|---|---|
| 1 | Domain separator | Four bytes ASCII `KVAD` |
| 2 | AAD protocol version | One byte, value 1 |
| 3 | `vaultId` | uint16 byte length followed by 1–128 ASCII bytes matching `[A-Za-z0-9_-]+` |
| 4 | `documentId` | Same identifier encoding |
| 5 | `documentVersion` | uint32, range 1..2^32-1 |
| 6 | `keyId` | Same identifier encoding |
| 7 | `contentType` | uint16 byte length + exact ASCII allowlisted MIME string |
| 8 | `chunkFormat` | One byte enum; 1 = `chunked-v1` |
| 9 | `chunkIndex` | uint32, range 0..2^32-1 |

Supported content types are explicitly enumerated in `src/crypto/chunk-aad.mjs`. Unknown types fail closed. The encoder accepts exactly the documented seven semantic properties, requires them to be enumerable data properties, and rejects symbol keys, non-enumerable extras, getters, and setters. It does not parse attacker-supplied AAD; application metadata is validated and encoded before encryption/decryption.

## Scope and limitations

- AAD is authenticated but not encrypted; do not put secrets in it.
- This encoding does not itself implement chunking, manifest authentication, VEK/DEK wrapping, Argon2id, recovery, storage transactions, or nonce/retry coordination.
- The final manifest must independently authenticate chunk count and total plaintext length. Verify the complete manifest and all chunks before exposing decrypted output.
- Never reuse a GCM key/nonce pair. Random 96-bit nonces still require a documented per-key operation/data budget and safe retry semantics. See NIST SP 800-38D: https://csrc.nist.gov/pubs/sp/800/38/d/final
- GCM and key lifecycle must be reviewed against OWASP cryptographic-storage and key-management guidance:
  - https://cheatsheetseries.owasp.org/cheatsheets/Cryptographic_Storage_Cheat_Sheet.html
  - https://cheatsheetseries.owasp.org/cheatsheets/Key_Management_Cheat_Sheet.html

## Verification

- `scripts/chunk-aad.test.mjs` covers a byte-exact manual golden fixture, strict schema rejection, boundary validation, per-field AAD differentiation, accessor/symbol/non-enumerable-property rejection, and AES-GCM failure when bound metadata changes.
- `scripts/reference/chunk_aad_reference.py` independently encodes the same fixture using Python `struct`/byte operations and verifies the expected 38-byte sequence. Local execution on 2026-10-19 printed the expected vector. This is an independent implementation check, not an independent human security review.
- Exact-head GitHub Actions is still required. A successful local reference-vector run does not establish that repository CI passes or that the protocol is secure.

## Acceptance gate

- [ ] Independent reviewer approves schema, versioning and byte layout.
- [x] Golden vector reproduced by a second-language reference implementation; human review still pending.
- [ ] Add adversarial fuzz/property tests and cross-runtime tests.
- [ ] Define and implement authenticated manifest format.
- [ ] Approve key hierarchy, Argon2id parameters and recovery model.
- [ ] Define nonce uniqueness/retry/rotation policy and per-key GCM usage limits.
- [ ] Run all CI checks on the exact commit.
- [ ] Do not use real sensitive documents until independent cryptographic review and all release gates pass.
