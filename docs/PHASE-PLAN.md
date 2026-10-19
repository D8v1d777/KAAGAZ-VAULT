# Kaagaz Vault — Delivery plan

Progress is reported against defined acceptance criteria, not code volume. Phase 1 prototype scope and Phase 2 design artifacts are complete. E2EE architecture and human-centered research are now documented as a new design track; production cryptography is not implemented.

**Overall progress: 35%** (Phase 1: 100%, Phase 2: 100%, Phase 3: 10%, Phases 4–6: 0%; equal-weight average 35%, rounded). This ratio reflects project phases and does not mean security is 33% complete.

| Phase | Deliverable | Status |
|---|---|---|
| 1. Research and UI foundation | Accessible responsive prototype, core interactions, static tests, Playwright and axe scans | **100% — prototype scope complete** |
| 2. Architecture and data flow | Stack recommendation, data model, auth/upload/read/delete flows, threat model, privacy/recovery, visual report | **100% — initial design package complete** |
| 2B. E2EE and human-centered design | E2EE key hierarchy, threat model, recovery and metadata boundaries, separate evidence-to-design research | **100% — design documents drafted; independent review still required** |
| 3. Functional storage and crypto | Reviewed crypto library, client-side encryption, encrypted metadata, private ciphertext storage, key recovery | **10% — isolated AES-GCM primitive and regression tests only; no file protocol, KDF, recovery or storage** |
| 4. Security | E2EE verification, authz, upload validation, secret hygiene, privacy and recovery controls | **0% — not started** |
| 5. Quality gates | Automated security/accessibility tests, performance, cross-browser/device testing | **0% — not started** |
| 6. User validation | Moderated usability sessions across ages 20–90 and varied access needs | **0% — not started** |

## Immediate validation follow-through\n- [x] Add static regression tests for E2EE claims, key lifecycle, research traceability, progress gates, and report content.\n- [x] Extend CI to run the E2EE documentation tests and to trigger for the E2EE branch.\n- [ ] Confirm CI passes on the E2EE branch; connector cannot execute tests locally.
- [x] Add isolated Web Crypto AES-256-GCM wrapper with input validation and fail-closed behavior.
- [x] Add tests for round-trip, authenticated metadata, ciphertext tampering, wrong keys and malformed envelopes.
- [ ] Confirm crypto suite passes in CI and add browser-target coverage.\n\n## E2EE is now a required product constraint
- [x] Document client-side encryption and server trust boundary.
- [x] Document key hierarchy, encrypted metadata, recovery and device onboarding options.
- [x] Document browser-delivery/supply-chain limitations.
- [x] Document release gates and cryptographic test requirements.
- [x] Add separate evidence-to-design research on accessibility, security fatigue and human-centered UX.
- [ ] Select and review a maintained crypto library and its provenance. The current wrapper uses native Web Crypto AES-GCM only; Argon2id and protocol/key wrapping remain unimplemented.
- [ ] Obtain specialist review of the protocol and format before implementation.
- [ ] Implement and test encryption, recovery and encrypted storage.
- [ ] Validate that server/database/object storage and telemetry contain no plaintext or raw keys.

## Phase 3 owner/security gates
1. Confirm supported browsers and minimum device performance.
2. Approve recovery-secret model and explicit unrecoverable-loss warning.
3. Approve whether initial release excludes sharing, search/OCR, previews and AI processing.
4. Decide acceptable browser-origin supply-chain risk versus a signed desktop/mobile client.
5. Confirm storage region, quota, retention, budget and data-deletion policy.

## UX and accessibility acceptance
- [ ] WCAG 2.2 AA automated and manual validation.
- [ ] Manual keyboard, screen-reader, zoom/reflow, focus and touch tests.
- [ ] Usability testing with varied ages, digital literacy, vision, motor ability and assistive technology.
- [ ] At least 90% of test participants correctly explain the E2EE trust boundary and recovery-loss consequence; investigate all failures.
- [ ] Measure successful upload/retrieval/deletion, recovery completion, error recovery and task completion without collecting real documents.

## Guardrails
- Keep main unchanged until reviewed and approved.
- Do not claim E2EE or production security until implemented and independently reviewed.
- Never store raw keys in localStorage, IndexedDB, logs, analytics, URLs or server requests.
- Do not claim the provider can recover a lost vault key if it does not hold a decryption secret.
- Keep research evidence, design hypotheses and measured usability results clearly distinguished.
