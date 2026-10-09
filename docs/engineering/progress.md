# Engineering Progress Log

This log records repository work and evidence. It distinguishes documentation changes from application implementation.

## 2026-10-09 — Engineering foundation hardening

### Completed in this change set
- Added focused skills for cryptographic storage, encrypted search, document imaging evaluation, reminder safety, Indian-language documents, Android release security, and accessibility/performance.
- Added ADR-0001 to block premature plaintext OCR indexing and force a measured decision about search/storage trade-offs.
- Updated the engineering contract with skill-routing and decision-record requirements.

### Decisions and rationale
- Search/storage leakage is the first architecture risk because FTS, WAL/journal files, thumbnails, backups, and temporary files can undermine file-only encryption.
- Specialized skills separate distinct review disciplines while the root engineering contract remains the shared source of truth.
- Changes are being made on a dedicated branch so they can be reviewed as one coherent documentation set.

### Verification
- Repository baseline files were fetched from GitHub before editing.
- This change set is documentation-only; it does not implement Android functionality.
- No Android build or test suite has been run as part of this documentation change. No build/test success is claimed.

### Next
1. Review the complete diff and fetch every new file from GitHub to verify persistence.
2. Resolve the sensitive-search ADR with primary-source library research and a small prototype before implementing FTS.
3. Establish a minimal Android project and CI/build evidence only after confirming the repository's current code/build state.

## 2026-10-09 — Search/storage candidate research

### Findings
- Official SQLCipher for Android documentation describes Room integration for both Room 2 and Room 3; the current integration uses `SupportOpenHelperFactory` for Room 2 or `SQLCipherDriver` for Room 3.
- Zetetic's Community Edition has explicit attribution/licence-notice obligations; licence compliance must be included in product implementation if selected.
- The repository has no Android Gradle project yet, so compatibility and build feasibility cannot be truthfully confirmed at this stage.

### Decision
- SQLCipher for Android is the first candidate for a prototype, not an approved dependency.
- Keep the search ADR in Proposed status until a minimal integration is built and checked against the chosen Room generation, target/min SDK, native ABIs, key lifecycle, database sidecars, and mixed-language search requirements.

### References reviewed
- https://github.com/sqlcipher/sqlcipher-android
- https://www.zetetic.net/sqlcipher/sqlcipher-for-android-migration/
- https://www.zetetic.net/sqlcipher/license/
- https://central.sonatype.com/artifact/net.zetetic/sqlcipher-android
- https://developer.android.com/training/data-storage/room/migration-2-to-3

### Verification update
- Primary documentation and artifact metadata were inspected.
- No dependency was added and no prototype/build/test was run; compatibility remains unverified.
