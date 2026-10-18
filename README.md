# Kaagaz Vault

An accessibility-first document-vault UI prototype focused on calm, clear document organization for adults aged 20–90.

## Current status

**Phase 1: UI foundation and automated verification.** The prototype is a dependency-free HTML/CSS/JavaScript page. It includes responsive navigation, search, category filters, favourites, sorting, grid/list views, a larger-text toggle, document details, keyboard search shortcut, and local file-selection preview.

The current UI is not a production document vault. Sample records are illustrative. Selecting a file adds metadata to the page only; the file is not uploaded, persisted, encrypted, or authenticated. Do not store real sensitive documents in this prototype.

## Run locally

Open `index.html` in a modern browser to inspect the interface.

## Run automated checks

Requires Node.js 22+ and npm.

```sh
npm install
npx playwright install chromium
npm test
```

The test suite includes:
- Static regression checks for semantic landmarks, focus affordances, responsive rules, selected WCAG contrast pairs, input limits, HTML escaping, and JavaScript syntax.
- Playwright browser tests for search, filters, favourites, sorting, grid/list controls, document details, file-selection preview, keyboard shortcut, and mobile overflow.
- axe-core scans against WCAG A/AA tags at desktop and mobile viewport sizes.

Passing automated scans does not prove complete WCAG conformance. Manual keyboard, screen-reader, browser zoom, cross-browser, and usability testing remain necessary.

## Research and delivery notes

- [UX research and accessibility decisions](docs/UX-RESEARCH.md)
- [Architecture and security proposal](docs/ARCHITECTURE.md)
- [Phased delivery plan and acceptance checklist](docs/PHASE-PLAN.md)
- [End-to-end encryption architecture](docs/E2EE-ARCHITECTURE.md)
- [Human-centered security and UX evidence review](docs/RESEARCH-EVIDENCE.md)
- [Phase 2 architecture and data-flow report](docs/PHASE-2-REPORT.md)
- [Visual Phase 2 report dashboard](docs/phase-2-report.html)
- [Phase 3 E2EE execution plan and release gates](docs/PHASE-3-E2EE-EXECUTION-PLAN.md)

## Design principles

- Calm is a hypothesis to validate with people, not a universal effect of a particular color.
- Prefer legibility, predictable behavior, generous controls, visible focus, and plain language over decoration.
- Make storage and security boundaries explicit. Never imply encryption, persistence, or privacy controls that have not been implemented.
