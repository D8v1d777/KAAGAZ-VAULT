# Kaagaz Vault — End-to-End Encryption Architecture

**Status:** Research-backed architecture proposal; implementation has not begun. Requires specialist cryptographic review before real user documents are accepted.

## 1. Security objective

Kaagaz Vault must encrypt document contents in the user's browser before upload and decrypt only on an authorized client. The storage service receives ciphertext and the minimum metadata required to operate. The service must not possess a decryption key or recovery secret.

**Do not call the application end-to-end encrypted until this architecture is implemented, tested, and independently reviewed.** TLS and provider-managed encryption at rest are useful additional layers, not E2EE.

## 2. Threat model

### In scope
- Database or object-storage disclosure without a user's keys.
- Curious or compromised storage administrators who can inspect server-side data.
- Unauthorized account access where the attacker lacks vault keys.
- Ciphertext modification, truncation, reordering, and replay of older object versions.
- Accidental loss of a device, subject to the user's recovery setup.

### Not fully solved by E2EE
- Malware, browser extensions, keyloggers, screenshots, or a compromised endpoint while the vault is unlocked.
- A malicious application update or compromised web origin serving altered JavaScript. A web service can change the code delivered to clients; this is a serious residual risk for browser-based E2EE.
- Traffic analysis, upload/download timing, ciphertext size, IP address, and whatever metadata the service must retain.
- Weak user passphrases, stolen recovery keys, coercion, or a user exporting plaintext.
- Availability attacks, service outages, deletion of ciphertext, or a server withholding updates.

### Explicit non-goals for MVP
- Public links or anonymous sharing.
- Server-side full-text search, document OCR, previews, thumbnails, or AI analysis over plaintext.
- Claims of anonymous usage, metadata secrecy, or protection from compromised endpoints.

## 3. Key hierarchy

Use a reviewed, maintained cryptographic library for key derivation and a narrow application wrapper. Do not invent a cipher, protocol, custom PRNG, or key stretching algorithm.

1. Generate a random 256-bit Vault Encryption Key (VEK) using a cryptographically secure random generator on the client.
2. Generate a fresh random salt and derive a Key Encryption Key (KEK) from the user's vault passphrase using **Argon2id**, with parameters benchmarked against supported low-end devices and recorded in a versioned vault header. Select a maintained, audited implementation with known-answer tests; do not hand-roll Argon2. If implementation/runtime support is not adequate, block release rather than silently falling back to a weak KDF.
3. Wrap the VEK using an authenticated encryption construction with a fresh nonce and versioned associated data. Store only the wrapped VEK, salt, KDF parameters, algorithm/version identifiers and non-secret account linkage on the service.
4. Generate an independent random Data Encryption Key (DEK) for each document or file version. Encrypt document bytes with authenticated encryption and wrap the DEK under the VEK. This limits the impact of a single file-key compromise and enables key rotation without automatically re-encrypting every file.
5. Keep key material in memory only while required; minimize lifetime and copies, clear references on lock/sign-out where practical, and never write raw keys to localStorage, IndexedDB, logs, telemetry, URLs, crash reports, or server requests.
6. The account login credential and vault passphrase have different purposes. Server-side password reset must not silently recover or reset the vault keys.

### Primitive selection (to be reviewed)
- File/content encryption: AES-256-GCM using the platform Web Crypto API where supported, with a unique random 96-bit nonce for every encryption under the same key and a 128-bit authentication tag.
- Key derivation: Argon2id from a reviewed maintained implementation, with memory/time parameters calibrated on target devices. Web Crypto's built-in PBKDF2 is not an automatic equivalent to Argon2id; any alternative requires a documented security and performance decision.
- Randomness: browser cryptographic random generator only.
- Additional cryptographic formats or public-key sharing: defer until a reviewed protocol and library are selected.

Web Crypto exposes low-level primitives and explicitly warns that secure overall design is difficult. The existence of AES-GCM support is not evidence that this application is secure by itself.

## 4. File encryption format and large files

Do not encrypt an entire large file in one unbounded browser memory operation. Use a versioned, reviewed chunked format with:
- a unique nonce per chunk under a given key;
- authenticated associated data binding vault/file identifier, file version, format version, chunk index, and relevant manifest fields;
- authenticated manifest describing chunk count and ordering;
- strict bounds on chunk count, chunk sizes and manifest sizes;
- verification of authentication tags and manifest before exposing decrypted bytes;
- no plaintext temporary files or server-side previews.

Nonce uniqueness is mandatory for GCM under a key. The implementation must define and test how nonces are generated, persisted, and made unique across retries and interrupted uploads. Never retry encryption with the same key/nonce pair. Do not improvise a chunking scheme without specialist review.

## 5. Metadata minimization

Encrypt the filename, user-defined labels, notes, document category, and other private descriptive fields inside an authenticated encrypted manifest whenever possible. Use a random opaque object key.

The service may still need to know:
- account identifier for access and quotas;
- ciphertext object key and byte count;
- creation/update time and upload state;
- format/version identifiers, salt and wrapped-key material;
- deletion/tombstone state.

Treat these as sensitive metadata, document their purpose, and retain only what is necessary. Ciphertext size and timing can reveal information even when content is encrypted. Do not log signed URLs, auth tokens, plaintext names or contents.

## 6. Recovery and multi-device access

**Recovery is a product-critical security decision, not a support ticket.** If every decryption secret is lost, the service cannot recover plaintext. The UI must communicate this before the user relies on the vault.

MVP proposal:
- During setup, generate a high-entropy recovery secret on the client.
- Use it to wrap a recovery copy of the VEK with a separate random salt and authenticated encryption; upload only the wrapped recovery key and required public parameters.
- Show the recovery secret once, with a clear explanation and copy/print/download option. Require a short verification step that confirms the user has saved it without transmitting it.
- Warn that anyone holding the recovery secret can decrypt the vault. Never email or log it.
- Allow the user to rotate the passphrase by deriving a new KEK and re-wrapping the VEK; document that this does not protect against a previously copied VEK or compromised endpoint.
- For multi-device setup, initially require explicit transfer from an already trusted, unlocked device or recovery-secret import. A QR code may transfer a one-time encrypted package only after a reviewed authenticated key-transfer design. Do not invent a home-grown device pairing protocol.
- Lost account password and lost vault recovery material are separate cases. Support staff must never claim they can restore E2EE contents.

Avoid social recovery, server-held escrow, or a hidden master key in MVP: each would change the stated trust model.

## 7. Authentication, storage and API changes

- Retain managed authentication for account identity, session lifecycle, abuse controls and quotas; it does not supply vault decryption keys.
- Keep the object bucket private. Enforce account ownership for ciphertext access using database grants/RLS and Storage policies.
- Server endpoints accept ciphertext only for document content. Validate request size, rate limits, ownership, quota, declared version and schema without parsing decrypted content.
- Keep service-role keys server-side. E2EE does not remove the need for authorization.
- Ensure upload completion reconciles ciphertext and metadata. Add idempotent retry and orphan cleanup for partial writes.
- Export must be a client-side decrypting export, with explicit confirmation for plaintext files. Delete and account deletion must state what is deleted immediately and what remains in backups, including retention limits.

## 8. Browser delivery and supply-chain risk

Browser E2EE has an important limitation: the origin serving JavaScript can potentially serve malicious code that steals keys or plaintext. The following reduce risk but do not eliminate it:
- strict Content Security Policy, no unsafe inline script/eval, narrow script origins, dependency pinning and automated dependency/security review;
- minimize third-party scripts, analytics and embedded content; never include ad or session-replay scripts on vault pages;
- reproducible builds, reviewed release changes, protected branch and release approvals;
- publish release hashes and a security contact; consider signed releases and independent audits;
- do not overstate CSP or SRI as protection against a malicious same-origin release.

If protection against a malicious web operator is a hard requirement, evaluate a signed desktop/mobile client or independently verifiable client distribution as a separate product decision.

## 9. Privacy-preserving UX requirements

- Clearly separate “Account password” and “Vault passphrase”.
- Explain the trust boundary in plain language before first upload: “Files are encrypted on this device. Kaagaz Vault stores encrypted files and cannot reset your vault key.”
- Show honest state labels: “Encrypting on this device”, “Uploading encrypted file”, “Encrypted and saved”, “Unlock required”. Never display “saved” before server confirmation.
- Provide an unobtrusive lock control and configurable auto-lock; explain that lock cannot erase copies already exported.
- Recovery setup is mandatory before storing valuable documents, but should be calm and stepwise rather than fear-based.
- Confirm permanent deletion, explain impact and provide undo for reversible metadata actions. Do not promise recovery of permanently deleted ciphertext.
- Errors should explain the next safe action and never expose cryptographic internals, secrets or sensitive file names in logs.

## 10. Required verification before claiming E2EE

### Cryptographic correctness
- Known-answer tests for every primitive and KDF wrapper.
- Round-trip tests across browsers, devices, vault versions and encrypted chunks.
- Tampered ciphertext, nonce, tag, manifest, chunk order/count and wrong-key tests must fail closed.
- Nonce uniqueness property tests and crash/retry/interrupted-upload tests.
- Wrong passphrase, corrupt wrapped key, corrupt recovery material and unsupported version tests.
- No plaintext/key leakage into network requests, local persistent storage, logs, analytics, URLs, error traces or crash reports.

### Authorization and privacy
- Two-account and signed-out allow/deny tests for every metadata and object operation.
- Inspect network payloads and storage/database contents to verify that names and contents remain encrypted.
- Verify export, delete, restore, key rotation and account-deletion behavior.
- Test server compromise assumptions separately from client compromise assumptions.

### Human factors and usability
- Moderated tests with varied ages, digital literacy, vision, motor ability and assistive technology.
- At least 90% of test participants should correctly explain who can read files and what happens if both passphrase and recovery secret are lost; investigate failures rather than treating this target as proof of security.
- Measure recovery completion, accidental lockouts, upload errors, task time, and user confidence without collecting document contents.
- Conduct an independent cryptographic/security review before production release.

## 11. Implementation sequence

1. Freeze and approve the threat model, supported platforms and recovery policy.
2. Select a maintained cryptographic library and document its provenance, audit history, update process and licensing.
3. Implement isolated crypto module with typed versioned formats and unit/property tests; no production documents yet.
4. Implement encrypted metadata and private ciphertext storage in a development environment.
5. Add recovery and multi-device flow; conduct destructive-loss drills.
6. Integrate accessible setup/unlock/upload UX.
7. Complete external review, threat-driven tests and usability validation.
8. Enable production rollout only after all release gates pass.

## References
- OWASP Cryptographic Storage Cheat Sheet: https://cheatsheetseries.owasp.org/cheatsheets/Cryptographic_Storage_Cheat_Sheet.html
- OWASP Key Management Cheat Sheet: https://cheatsheetseries.owasp.org/cheatsheets/Key_Management_Cheat_Sheet.html
- W3C Cryptography usage in Web Standards: https://www.w3.org/TR/security-guidelines-cryptography/
- MDN SubtleCrypto security and API guidance: https://developer.mozilla.org/en-US/docs/Web/API/SubtleCrypto
- MDN AES-GCM authenticated encryption: https://developer.mozilla.org/en-US/docs/Web/API/SubtleCrypto/encrypt
- RFC 9106 Argon2: https://www.rfc-editor.org/rfc/rfc9106
- OWASP Secrets Management Cheat Sheet: https://cheatsheetseries.owasp.org/cheatsheets/Secrets_Management_Cheat_Sheet.html
