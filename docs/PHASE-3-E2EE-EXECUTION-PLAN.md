# Kaagaz Vault — Phase 3 E2EE Execution Plan

**Status:** Ready for implementation planning; no production cryptography is claimed by this document.
**Product decision:** End-to-end encryption is required. The storage service must never receive plaintext document bytes, private filenames, private labels, or vault decryption keys.
**Last reviewed:** 2026-10-18

## 1. Resume point and scope

Phase 1 (accessible UI prototype) and Phase 2 (initial architecture package) were previously marked complete. The product owner selected E2EE, so the managed-encryption option is rejected for document confidentiality. This phase must turn the architecture into a testable, narrow vertical slice before expanding features.

The current web prototype is not a secure vault: it does not provide real authentication, durable storage, or encryption. Never upload real documents to it.

## 2. Architecture decision that must be explicit

The repository has both a browser-oriented UI prototype and separate Android/offline-first work on other branches. These are different trust models and must not be mixed implicitly.

- **Browser E2EE:** encrypts in the client before upload, but the service origin can deliver modified JavaScript on a later visit. A compromised deployment pipeline or origin can potentially capture passphrases/plaintext when a user unlocks the vault.
- **Offline-first native Android:** keeps document content on-device and can use Android Keystore-backed keys; this reduces remote-service exposure but still needs device-compromise, backup, migration, recovery, and app-update threat analysis.
- **Decision gate:** before production implementation, record whether the first production target is browser E2EE, offline-first Android, or both. If both are required, share a versioned cryptographic format only after interoperability and recovery have been designed and tested. Do not treat the web UI and Android branch as one deployable architecture.

## 3. Proposed Phase 3 vertical slice

Implement one narrow flow using synthetic fixtures only:

1. Create a vault and show a plain-language explanation of the recovery consequences.
2. Generate a random 256-bit vault encryption key (VEK) using a platform cryptographic random generator.
3. Derive a key-encryption key (KEK) from a vault passphrase with a maintained, reviewed Argon2id implementation; record the KDF version, salt, and bounded parameters in a versioned header.
4. Wrap the VEK using authenticated encryption and versioned associated data.
5. Generate a fresh independent data-encryption key (DEK) for each document/version; encrypt the bytes and wrap the DEK under the VEK.
6. Encrypt private metadata (filename, labels, category, notes and OCR text) in an authenticated manifest. Use only opaque identifiers outside that manifest.
7. Store only ciphertext, wrapped keys, non-secret parameters, and minimum operational metadata in storage.
8. Download, authenticate, and decrypt locally. Do not expose partial plaintext until all relevant authentication and manifest checks pass.
9. Lock/sign out and clear reachable key references and decrypted UI state as far as the runtime permits.
10. Demonstrate wrong-passphrase, modified-ciphertext, truncated-file, reordered-chunk, stale-version, interrupted-write, and recovery flows with synthetic test fixtures.

## 4. Cryptographic requirements (release blockers)

- Use maintained cryptographic libraries and standard primitives; never design a cipher, KDF, nonce scheme, or custom chunking format.
- AES-GCM requires nonce uniqueness for each key. Define a reviewed format for large files with unique per-chunk nonces and authenticated associated data binding vault ID, opaque file ID, format version, file version, chunk index, and manifest fields.
- Bound file size, chunk count, manifest size, KDF memory/time parameters, and parser inputs before allocation or expensive work.
- Select Argon2id parameters by benchmarking supported low-end devices and reviewing the chosen library's provenance, maintenance, build reproducibility, and known-answer tests. RFC 9106's generic profiles are not automatically suitable for every browser or phone.
- Browser Web Crypto does not natively provide Argon2id. If browser delivery is retained, select a maintained Argon2id implementation with pinned versions and integrity verification; document the WASM/native supply-chain and availability risks. Do not silently substitute a weaker KDF.
- Separate account authentication from vault key derivation. A server-side password reset must not silently recover or replace vault keys.
- Never persist raw keys in localStorage, IndexedDB, URLs, logs, telemetry, analytics, crash reports, or server requests. Memory clearing is best-effort in managed runtimes; document that limitation.
- Do not claim that ciphertext size, timing, IP address, account linkage, or other operational metadata is hidden unless the design actually hides it.

## 5. Recovery and device loss

Recovery is a security protocol, not an account-support feature.

- The user must be warned that losing every copy of the vault secret may make data permanently unrecoverable.
- Decide whether recovery uses a high-entropy client-generated recovery secret to wrap a recovery copy of the VEK, or whether MVP is intentionally single-device with no recovery.
- If recovery is supported, show the secret only on the client, never transmit it in plaintext, require the user to verify they saved it, and test restore on a clean device/profile.
- Define rotation, revocation, lost-device handling, backup scope, and what happens when a device is offline during a key change.
- Do not implement multi-device sharing or public links in this vertical slice.

## 6. Security and privacy acceptance tests

| Test | Required result |
|---|---|
| Server/storage inspection | No plaintext file bytes, filename, OCR text, private labels, or raw keys |
| Wrong passphrase | Unlock fails without corrupting or overwriting the vault |
| Ciphertext bit flip | Authentication fails; no corrupted plaintext is presented as valid |
| Truncation/reordering/replay | Manifest or authenticated-chunk verification fails |
| Interrupted write | Existing valid document remains recoverable; incomplete temporary data is cleaned or quarantined |
| Cross-account authorization (if cloud sync exists) | Two-user tests deny every cross-owner metadata and object operation |
| Logs and telemetry | No document contents, names, secrets, tokens, or signed URLs |
| Recovery | Successful restore with the intended secret; explicit failure with a wrong/lost secret |
| Lock/sign-out | Decrypted UI state is removed and keys are no longer intentionally retained |
| Browser deployment integrity (web target) | Threat is documented; deployment and dependency controls are tested, without claiming to eliminate malicious-origin risk |

## 7. Human-centered UX requirements

- Use calm neutral/sage surfaces only as a design hypothesis; do not claim that a palette universally reduces anxiety.
- Keep one primary action per setup step, visible progress, plain-language errors, and optional technical explanations.
- Never use fake urgency, shame language, manipulative prompts, or repeated generic warnings.
- Explain the trust boundary at setup, before recovery decisions, and in privacy settings.
- Use visible keyboard focus, semantic labels, WCAG 2.2 AA contrast, reflow at 200% zoom, reduced-motion support, and generous touch targets.
- Test with varied ages, digital literacy, vision, dexterity and assistive-technology needs. Do not infer capability from age alone.
- Measure whether participants can correctly explain what the service can read and what happens if recovery secrets are lost.

## 8. Phase gates and progress ratios

Ratios describe deliverables, not a security score.

| Workstream | Current estimate | Exit criterion |
|---|---:|---|
| Phase 1 UI prototype | 100% | Prototype scope and automated checks documented |
| Phase 2 initial architecture | 100% | Data-flow/threat-model package exists; stale managed-encryption assumptions are superseded by this E2EE plan |
| Phase 2B research and E2EE design | 100% drafted | Independent cryptographic review still required |
| Phase 3 implementation | 0% verified in the web prototype | One end-to-end encrypted import → store ciphertext → retrieve → decrypt vertical slice, with tests |
| Security verification | 0% | Independent review, negative tests, storage/log inspection and recovery tests pass |
| Accessibility and user validation | 0% | Manual accessibility review and moderated user testing completed |

**Overall design-track estimate:** Phases 1, 2, and 2B are documented as complete drafts; Phase 3 implementation and subsequent verification remain incomplete. Do not present the previous 33% project estimate as an encryption/security percentage.

### Immediate next actions

1. Reconcile the web prototype and Android branches and record the first production target.
2. Inventory existing cryptography-related code and tests before adding a second implementation.
3. Select the maintained crypto library/format and obtain specialist review.
4. Implement the smallest synthetic-data vertical slice on a dedicated branch.
5. Run tests and CI; report only checks with observed results.
6. Update the phase tracker and visual report from verified evidence.

## 9. AAD serialization evaluation

See [Phase 3 AAD Serialization Evaluation](PHASE-3-AAD-SERIALIZATION-EVALUATION.md) for the current evidence review of deterministic CBOR versus fixed-order binary encoding. The decision remains open; no format is approved until the parser, canonicality, dependency provenance, golden-vector and independent-review gates pass.

## 10. Research references

- OWASP Cryptographic Storage Cheat Sheet: https://cheatsheetseries.owasp.org/cheatsheets/Cryptographic_Storage_Cheat_Sheet.html
- OWASP Key Management Cheat Sheet: https://cheatsheetseries.owasp.org/cheatsheets/Key_Management_Cheat_Sheet.html
- RFC 9106 — Argon2: https://www.rfc-editor.org/rfc/rfc9106
- W3C Web Cryptography API: https://www.w3.org/TR/WebCryptoAPI/
- W3C WCAG 2.2: https://www.w3.org/TR/WCAG22/
- W3C Older Users and Web Accessibility: https://www.w3.org/WAI/older-users/
- OWASP File Upload Cheat Sheet: https://cheatsheetseries.owasp.org/cheatsheets/File_Upload_Cheat_Sheet.html

## 11. Release language

Until the vertical slice is implemented and independently reviewed, describe the product as an **E2EE design in progress**, not an end-to-end encrypted vault. A passing UI test suite is not evidence that cryptography, recovery, storage isolation, or deployment integrity is secure.
