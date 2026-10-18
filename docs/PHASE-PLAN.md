# Kaagaz Vault — Delivery plan

Progress is reported against defined acceptance criteria, not code volume. Phase 1 is complete for prototype scope; Phase 2 design artifacts are complete. Phase 3 implementation is gated on owner approval of product assumptions.

**Overall progress: 33%** (Phase 1: 100%, Phase 2: 100%, Phases 3–6: 0%; exact average 33.3%, rounded).

| Phase | Deliverable | Status |
|---|---|---|
| 1. Research and UI foundation | Accessible responsive prototype, core interactions, static tests, Playwright and axe scans | **100% — prototype scope complete; automated CI passed** |
| 2. Architecture and data flow | Stack recommendation, data model, auth/upload/read/delete flows, threat model, privacy/crypto gate, backup/recovery design, visual report | **100% — design package complete; owner approvals remain as Phase 3 gates** |
| 3. Functional storage | Implement auth, metadata persistence, private uploads/downloads, rename/delete/export | **0% — not started** |
| 4. Security | Enforce and test authz, upload validation, secret hygiene, privacy and recovery controls | **0% — not started** |
| 5. Quality gates | Automated security/accessibility tests, performance, cross-browser/device testing | **0% — not started** |
| 6. User validation | Moderated usability sessions across ages 20–90 and varied access needs | **0% — not started** |

## Phase 1 acceptance checklist
- [x] Evidence-led UX/accessibility principles documented.
- [x] Responsive prototype, search, category filters, favourites, sorting, grid/list, larger text and preview-only file selection implemented.
- [x] Static regression checks, Playwright interactions and axe WCAG A/AA scans added.
- [x] GitHub Actions passed on baseline commit: https://github.com/D8v1d777/KAAGAZ-VAULT/actions/runs/39120914235
- [ ] Manual keyboard, screen-reader, zoom and cross-browser checks remain in Phase 5/6.

## Phase 2 acceptance checklist
- [x] Research current official Supabase and OWASP guidance.
- [x] Recommend frontend, identity, database and private object-storage boundaries.
- [x] Define metadata schema, owner isolation and indexes.
- [x] Document sign-in, upload, read/download, rename/favourite/delete, export and account-deletion flows.
- [x] Document threat model, privacy/cryptography boundary, secrets, backups and object recovery.
- [x] Add architecture decision records and Phase 3 acceptance gates.
- [x] Add a visual HTML report dashboard.
- [x] Update README and pull request with status and links.
- [ ] Owner approval of product model, E2EE requirement, supported formats, region/budget and retention before Phase 3 implementation.

## Explicit planning defaults (not owner-approved)
- Multi-user service, each account owns a private vault; no sharing in MVP.
- Supabase Auth + PostgreSQL RLS + private Storage, subject to region/cost fit.
- TLS and managed at-rest encryption as initial baseline; no end-to-end/zero-knowledge claim.
- Proposed allowlist: PDF, JPEG, PNG and plain text; limits to be confirmed.
- Database and binary-object backups/recovery are separate acceptance concerns.

## Guardrails
- Keep main unchanged until changes are reviewed.
- Do not claim the vault is secure until implemented controls are tested.
- Never imply local demo state is persistent.
- Product-owner decisions are gates, not reasons to silently assume a more permissive security model.
