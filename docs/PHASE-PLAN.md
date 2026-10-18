# Kaagaz Vault — Delivery plan

Progress is reported against verifiable repository changes, not estimates of code volume.

| Phase | Deliverable | Status |
|---|---|---|
| 1. Research and UI foundation | Accessibility-informed design notes, responsive dashboard, search, category filters, favourites, grid/list views, preview-only file selection, contrast and syntax regression tests, Playwright interaction coverage, axe WCAG scan | Implemented; CI and manual validation pending |
| 2. Architecture and data flow | Framework/runtime audit, persistence decision, document lifecycle, auth and threat model | Proposal documented; product decisions pending |
| 3. Functional storage | Upload pipeline, metadata persistence, previews/downloads, rename/delete flows | Not started |
| 4. Security | Authz, encryption, validation, safe file handling, backup/recovery | Not started |
| 5. Quality gates | Automated tests, accessibility checks, performance and device testing | Not started |
| 6. User validation | Usability sessions spanning ages 20–90 and varied access needs | Not started |

## Phase 1 acceptance checklist
- [x] Repository found and README inspected.
- [x] Evidence-led UX/accessibility principles documented.
- [x] Responsive UI prototype implemented in index.html.
- [x] Search, category filters, favourites, sorting, grid/list controls and larger text implemented.
- [x] File selection clearly labelled as local preview only; no upload is claimed.
- [x] Add dependency-free Node regression checks for inline JavaScript syntax, core accessibility affordances, selected contrast pairs, upload-preview limits, and escaping.
- [x] Add GitHub Actions workflow to run static checks, Playwright browser interactions, and axe WCAG A/AA scans on the feature branch and pull requests to main.
- [ ] Confirm GitHub Actions run passes.
- [ ] Run browser-based functional checks and automated accessibility scan.
- [x] Confirm original repository had no app framework or package manifest; document a backend proposal and security gates.
- [ ] Confirm user model, end-to-end encryption expectations, supported file formats, and budget before locking in the backend.

## Guardrails
- Never report the vault as secure until implementation has been reviewed and tested.
- Never imply local demo state is persistent.
- Avoid dependencies until the current stack is confirmed.
- Keep main unchanged until changes are reviewed.
