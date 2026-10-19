# Phase 3 Research Note — Canonical AAD Serialization Evaluation

**Date:** 2026-10-19  
**Status:** Evidence review only; no encoding format is selected and no production protocol is approved.  
**Scope:** Compare deterministic CBOR with a narrowly specified fixed-order binary encoding for authenticated additional data (AAD) and versioned envelope metadata.

## Decision summary

**Keep the encoding decision open.** A maintained CBOR implementation is worth a bounded prototype, but it must not be adopted merely because it supports deterministic encoding. The reviewed `cborg` documentation exposes useful controls for deterministic output, strict integer/length decoding, disallowing indefinite-length items, and duplicate-map-key rejection. Its documentation also says decoder-side map-order enforcement is not currently available. Therefore, if evaluated further, the protocol would need a strict schema plus a canonicality check (for example, re-encode the accepted value using the selected deterministic profile and reject if the bytes differ), backed by adversarial tests and an independent review. This re-encoding approach itself must be tested against the actual decoder's representation and must not be assumed safe without evidence.

The alternative is a fixed-order binary schema with a domain-separation prefix, exact field order, fixed-width integers, and explicit length-prefix rules. It reduces general-purpose parser behavior but creates bespoke byte-level code and review burden. Neither option is approved yet.

## Evidence reviewed

### RFC 8949 — deterministic CBOR

RFC 8949 §4.2 defines core deterministic encoding requirements, including preferred serialization and deterministic map-key ordering. The RFC also says CBOR protocols must define how duplicate map keys are handled; rejecting duplicate keys as malformed is a valid policy. These are protocol requirements, not an automatic security guarantee.

Source: https://www.rfc-editor.org/rfc/rfc8949.html

### `cborg` — candidate for a browser-compatible prototype

The package documentation describes browser and Node.js support using `Uint8Array`, deterministic encoding support, RFC 8949 encoding options, strict checks for preferred integer/length representations, an `allowIndefinite: false` option, and a `rejectDuplicateMapKeys: true` option. The same documentation identifies limitations: decoder-side map-order enforcement is not implemented, and some float canonicality checks are unavailable. The intended protocol can avoid floats, tags and indefinite-length values, but decoder behavior still needs tests.

Source: https://www.npmjs.com/package/cborg

**Required prototype gates before choosing this candidate:**
1. Pin an exact package version and lockfile; review repository, release history, license, transitive/development dependency posture, and supply-chain provenance.
2. Use a closed schema with only explicitly permitted types and fields; exclude floats, tags, indefinite-length items, unknown critical fields and ambiguous optional-field encodings.
3. Reject duplicate map keys and all malformed/oversized inputs before expensive work.
4. Verify deterministic encoding follows RFC 8949 ordering, not an older CBOR canonicalization rule.
5. Verify canonical input on decode. Because the documented decoder does not enforce map ordering, test a byte-for-byte re-encode comparison or another independently reviewed canonicality check; reject alternate encodings rather than silently normalize them.
6. Add golden AAD vectors and negative vectors for alternate integer widths, map ordering, duplicate keys, trailing bytes, unknown fields, unsupported versions, oversized values and malformed UTF-8.
7. Run fuzzing and cross-runtime/browser vectors; document any parser differential.
8. Obtain independent cryptographic review before the format is used for real data.

### Fixed-order binary encoding

A fixed-order binary format can avoid a generic CBOR parser, but only if the complete schema is specified and reviewed: domain-separation prefix, field ordering, exact integer widths and endianness, string normalization, byte lengths, maximum field sizes, optional-field representation, versioning, and rejection behavior. Every parser and encoder edge case becomes application-owned code.

No independent performance or security conclusion is claimed for either approach. A smaller parser is not automatically safer if its custom implementation is under-tested.

## Proposed comparison gate

| Criterion | Deterministic CBOR candidate | Fixed-order binary |
|---|---|---|
| Browser data representation | `Uint8Array` documented | Straightforward but custom |
| Deterministic encoding | RFC 8949 options documented; verify exact profile | Determinism defined by the schema |
| Duplicate-key handling | Explicit decoder option documented | Not applicable to positional fields; reject duplicate/unknown fields in any outer representation |
| Canonicality on input | Map order not enforced by documented decoder; needs additional verification | Exact parser grammar can reject alternate encodings |
| Parser attack surface | General-purpose parser; constrain types and bounds | Smaller format, but bespoke parser code must be reviewed |
| Dependency/supply-chain burden | New runtime dependency; pin and review provenance | Less dependency surface, more project-owned security-critical code |
| Decision | Candidate for prototype only | Candidate for comparison only |

## Current blockers and next actions

- Do not implement key derivation, key wrapping, chunking or storage integration while the envelope/AAD encoding and key lifecycle remain unreviewed.
- Build a small isolated prototype only after reviewing the exact pinned dependency and its behavior. It must not encrypt real user documents.
- Produce golden vectors for the selected schema only after the candidate passes the gates above.
- Keep Phase 3 at 10% and overall progress at 35% until a verified implementation milestone is actually achieved.
- PR #10 remains draft and unmerged. Never merge without explicit owner authorization.
