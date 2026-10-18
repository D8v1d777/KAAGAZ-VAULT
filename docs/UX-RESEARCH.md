# Kaagaz Vault — UX research and design principles

## Objective
Help people aged 20–90 store, locate, understand, and manage important documents with low friction and a clear sense of control. This age range is a design constraint, not a substitute for user testing.

## Evidence consulted
- W3C, WCAG 2.2: https://www.w3.org/TR/wcag/
- W3C WAI, Older Users and Web Accessibility: https://www.w3.org/WAI/older-users/
- W3C WAI, Developing Websites for Older People: https://www.w3.org/WAI/older-users/developing/
- UK Government Service Manual, Understanding WCAG 2.2: https://www.gov.uk/service-manual/helping-people-to-use-your-service/understanding-wcag

## Design decisions
1. Perceivable: legible type, meaningful labels, clear hierarchy, strong contrast. Never communicate status through color alone.
2. Operable: keyboard navigation, visible focus, generous controls, skip-to-content, no drag-only interactions.
3. Understandable: familiar words, consistent navigation, plain-language empty states, predictable dialogs and feedback.
4. Robust: semantic HTML and native controls where possible; validate with keyboard and assistive technology.
5. Emotional reassurance without false claims: warm neutrals and restrained natural accents are a visual hypothesis, not a universal psychological law. Calm should come from clarity, low clutter, user control, and predictable feedback.
6. Privacy as behavior: explain storage, access, and protection accurately. Never label a prototype or unencrypted storage as secure.
7. Inclusive text: offer larger text and test browser zoom. Avoid tiny secondary text and precision-dependent controls.
8. Motion: respect prefers-reduced-motion.

## Proposed acceptance targets
- WCAG 2.2 AA review of key flows.
- Contrast at least 4.5:1 for normal text and 3:1 for large text and meaningful UI boundaries where applicable.
- Keyboard-only completion of search, filtering, details, favourites, and file selection.
- No loss of essential information at 200% zoom and on narrow mobile screens.
- Test representative participants across ages and access needs; observe task completion, errors, time-on-task, and confidence.
- Automated scans plus manual review; automated scans alone do not establish conformance.

## Current prototype limitations
The UI is a front-end concept using illustrative sample metadata. Selecting a file adds metadata to in-memory browser state only. It does not upload files, persist them after refresh, encrypt them, authenticate users, or connect to a backend. Sample documents are fictional examples.

## Next phases
1. Foundation: accessible dashboard, responsive layout, search/filter/favourites, local preview interactions.
2. Architecture: framework/runtime audit, persistence, document model, authentication, threat model, encryption strategy.
3. Core functionality: real upload/storage, metadata persistence, previews/downloads, edit and delete with confirmation.
4. Security: access control, encryption at rest/in transit, secure sessions, safe file handling, backups and recovery.
5. Quality: automated tests, accessibility audit, performance profiling, cross-browser and mobile testing.
6. Validation: moderated usability tests across the target age range; prioritize observed friction over aesthetic assumptions.
