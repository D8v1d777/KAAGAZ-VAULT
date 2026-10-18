# Kaagaz Vault — Delivery plan

Progress is reported against verifiable repository changes, not estimates of code volume.

| Phase | Deliverable | Status |
|---|---|---|
| 1. Research and UI foundation | Accessibility-informed design notes, responsive dashboard, search, category filters, favourites, grid/list views, local file-selection preview | In progress |
| 2. Architecture and data flow | Framework/runtime audit, persistence decision, document lifecycle, auth and threat model | Not started |
| 3. Functional storage | Upload pipeline, metadata persistence, previews/downloads, rename/delete flows | Not started |
| 4. Security | Authz, encryption, validation, safe file handling, backup/recovery | Not started |
| 5. Quality gates | Automated tests, accessibility checks, performance and device testing | Not started |
| 6. User validation | Usability sessions spanning ages 20–90 and varied access needs | Not started |

## Phase 1 acceptance checklist
- [x] Repository found and README inspected.
- [x] Evidence-led UX/accessibility principles documented.
- [ ] Responsive UI prototype implemented.
- [ ] Search, filters, favourites, layout controls and larger text implemented.
- [ ] File selection clearly labelled as local preview only.
- [ ] Run browser functional checks and accessibility scan.
- [ ] Confirm actual framework/backend direction before production storage.

## Guardrails
- Never report the vault as secure until implementation has been reviewed and tested.
- Never imply local demo state is persistent.
- Avoid dependencies until the current stack is confirmed.
- Keep main unchanged until changes are reviewed.
