# KAAGAZ VAULT — Engineering Contract

This repository builds a privacy-first, offline-first Android document vault. Product review belongs to V; engineering owns implementation quality, risk identification, and evidence.

## Product invariants
- Offline core: capture, local OCR where supported, review, search, encrypted storage, and scheduled reminders must work without network access.
- No document images, OCR text, filenames derived from OCR, or sensitive metadata leave the device in the FOSS build.
- No analytics SDKs, ad SDKs, hidden network calls, or cloud AI dependencies in the FOSS build.
- Treat OCR output as untrusted evidence. Preserve source text, field-level confidence, and review state. Never silently turn uncertain medical text into instructions.
- Government IDs and medical documents are sensitive by default. Minimize retention, redact previews, protect app-switcher surfaces, and require explicit user intent for export.
- Do not claim a feature is offline, encrypted, tested, or supported on API 21 without reproducible evidence.

## Engineering approach
- Kotlin and Jetpack Compose for Android; use the official Android architecture guidance and stable, maintained libraries.
- Keep UI, domain rules, persistence, OCR, encryption, and scheduling behind clear boundaries. UI code must not directly manipulate database internals.
- Prefer straightforward, idiomatic code over clever abstractions. Name concepts precisely, keep functions focused, and make state transitions explicit.
- No generic scaffolding, decorative architecture, speculative frameworks, or needless wrappers. Introduce abstractions when they remove real duplication or isolate a volatile boundary.
- Do not leave TODOs, fake implementations, hard-coded success states, stubbed security, or placeholder behavior in a feature presented as complete. If something is intentionally deferred, document it.
- Never guess library APIs or dependency versions. Check current primary documentation and verify the project builds.
- Keep dependencies minimal. Every new dependency needs a concrete use case, maintenance review, license check, and privacy/network impact assessment.
- Avoid broad refactors unrelated to the task. Preserve existing conventions unless there is a documented reason to change them.

## Definition of done
1. Acceptance criteria are explicit and met.
2. Relevant unit, integration, UI, and regression tests are added or updated.
3. Build and tests were actually run where tooling permits; report exact commands and results.
4. Sensitive-data handling, permissions, logs, exported data, and network behavior are reviewed.
5. Accessibility, loading/empty/error states, and low-memory/low-end-device behavior are considered.
6. Documentation and decision records are updated when behavior or architecture changes.
7. Final report separates verified results, unverified assumptions, and known limitations.

## Workflow
- Read the existing code and relevant docs before editing.
- For non-trivial work: state the plan, identify risks, define tests first, implement the smallest coherent change, run checks, review the diff, then summarize.
- Use test-driven development for domain logic and regression fixes where practical.
- Never say tests passed unless the test command actually completed successfully.
- Prefer fakes over mocks when they make tests clearer; tests must not depend on real user documents or network services.
- Use pull requests and small reviewable commits. Never commit secrets, private documents, real Aadhaar/PAN data, prescription images, or API credentials.
