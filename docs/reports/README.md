<div align="center">

# Engineering Reports
### KAAGAZ VAULT · Research, decisions, verification

**A review-first documentation index**

</div>

---

## Current focus

<details open>
<summary><strong>Phase 12 — Authentication-bound vault lock</strong></summary>

A structured report covering the migration threat model, durable journal, target architecture, accessible visual system, completion ratios, and explicit verification gates.

**[Open the Phase 12 report →](PHASE-12-AUTH-BOUND-VAULT-LOCK-REPORT.md)**

</details>

## Profile presentation draft

A custom SVG hero banner and a GitHub profile README concept are included for review. They are **not published** to the account profile because the username-named profile repository does not exist and the available GitHub actions cannot create repositories.

**[Open the profile README draft →](GITHUB-PROFILE-README-DRAFT.md)**

## Reading guide

1. **Decision and threat model** — why the current non-exportable document key requires document re-encryption rather than key export.
2. **Architecture** — how an authenticated cryptographic operation unlocks a VEK and gates the repository, not just the screen.
3. **Migration protocol** — staging, verification, publication, database-key rewrap, and conservative recovery.
4. **Design system** — color tokens, typography, spacing, accessible status patterns, and user-respecting error states.
5. **Acceptance gates** — evidence required before increasing completion ratios or making security claims.

> Documentation lives on `docs/auth-bound-migration-design`. Application implementation and tests live on `feat/durable-vault-migration-journal`. Keep the branches separate until their reviews are complete.


## Whole-project phase audit

The current phase ledger distinguishes historical CI-backed implementation slices from phases that have actually passed end-to-end release acceptance. The overall weighted product estimate remains **35%**; Phase 12 is still active and Phase 10–11 gates remain open.

**[Open the whole-project phase audit →](WHOLE-PROJECT-PHASE-AUDIT.md)**
