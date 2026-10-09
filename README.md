# KAAGAZ VAULT

**An offline-first, privacy-focused Android document vault — currently in engineering setup.**

KAAGAZ VAULT is intended to help people capture, organize, search, and act on everyday documents without sending document contents to a server in the FOSS build.

## Intended product direction

- Scan or import receipts, warranties, bills, education records, government IDs, and medical documents.
- Review on-device OCR results with visible uncertainty and editable extracted fields.
- Find documents locally and track dates such as warranty expiry or return windows.
- Protect document content and sensitive metadata at rest.
- Keep core workflows available offline, with no analytics, ads, or hidden network calls in the FOSS build.

These are **product goals, not implemented or verified features**. The repository currently contains engineering standards and architecture research; it does not yet contain a buildable Android app. Do not store real identity documents, prescriptions, private records, API keys, or signing credentials in this repository.

## Engineering principles

- Kotlin and Jetpack Compose, with clear UI and data boundaries.
- Local-first data ownership and minimum permissions.
- Vetted cryptography; no home-grown cryptographic primitives.
- OCR output is untrusted evidence. Low-confidence handwritten medical text must never automatically become a medication instruction or reminder.
- No plaintext OCR index until the storage/search leakage analysis is resolved.
- Tests and security claims must be backed by reproducible evidence.

## Current status

- [x] Engineering contract and focused engineering skills.
- [x] Initial quality gates and proposed sensitive-search architecture decision record.
- [x] Repository baseline audit.
- [x] Minimal Android project bootstrap and CI build/test/lint workflow configured (first run pending).
- [ ] Checked-in Gradle Wrapper and verified build evidence.
- [ ] Capture/import → encrypted storage → OCR review vertical slice.
- [ ] Local retrieval, date/reminder safety, accessibility, and release audits.

CI uses pinned Gradle and Android SDK versions. A checked-in Gradle Wrapper is still pending; do not treat the first bootstrap as verified until CI completes successfully. See [engineering progress](docs/engineering/progress.md), [quality gates](docs/engineering/quality-gates.md), and [ADR-0001](docs/engineering/decisions/ADR-0001-sensitive-data-search.md).

## Contributing

Read [AGENTS.md](AGENTS.md) before making changes. Architectural decisions must include evidence, and PRs must report the checks actually run. Use synthetic, non-identifying fixtures only.

## License

No project license has been selected yet. Until a license is added, do not assume this repository grants permission to reuse or redistribute its contents.
