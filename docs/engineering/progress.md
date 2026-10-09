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


## 2026-10-09 — Phase 1: repository baseline and public-exposure review

### Completed
- Confirmed the repository is public and PR #1 remains open/draft on `docs/engineering-hardening` → `main`.
- Inspected recursive trees for `main` and the hardening branch, the visible main-branch commit list, the PR metadata, and the repository engineering skills.
- Recorded the missing Android baseline in `docs/engineering/baseline-audit.md`: no Gradle wrapper/build scripts, manifest, Kotlin sources, tests, CI workflow, or `.gitignore` were present in the inspected trees.
- Rewrote the README to distinguish intended product goals from features that exist or have been verified.
- Reviewed official Android architecture and Gradle compatibility guidance, plus GitHub secret-scanning documentation.

### Public-repository safety findings
- The inspected current trees contain documentation only; no application source or real-document test fixtures were found.
- The visible main commit history is documentation-only, but this is not a complete historical secret scan. No secret-scanning alert result was available through this audit. Do not interpret this as proof that no credential was ever exposed.
- If a real credential is discovered, revoke/rotate it first; removing the latest copy does not invalidate historical exposure.

### Verification
- GitHub API tree and PR metadata responses were inspected.
- README, audit document, and progress log are written to the hardening branch and must be fetched again to verify persistence.
- No Android build or tests were run because no Android project/build files exist yet. No build or test success is claimed.

### Next phase
1. Research current official Android/Compose/Gradle setup guidance and compatible stable versions.
2. Bootstrap the smallest useful single-module Android app and test setup, keeping the FOSS manifest free of `INTERNET`.
3. Verify dependency versions, license/permission impact, and build-tool compatibility before adding OCR, CameraX, Room, or encryption libraries.
4. Run the build and tests in a real Android/Gradle environment if available; if the connected tooling cannot execute builds, state that limitation and keep CI/build evidence as an explicit gate.


## 2026-10-09 — Phase 2: Android project bootstrap

### Research before implementation
- Read the repository Android engineering, privacy/security, testing-quality, and Android release-security skills before changing the build.
- Official Android guidance recommends a distinct UI/data layer, repositories as the UI's entry point to data, and testable state-driven UI: https://developer.android.com/topic/architecture/recommendations
- AGP 9.1.1 supports API 37, requires Gradle 9.3.1, and uses JDK 17: https://developer.android.com/build/releases/agp-9-1-0-release-notes
- Compose's August 2026 stable BOM is `2026.08.00`; it requires compileSdk 37 and AGP 9.1.2 or newer: https://developer.android.com/blog/posts/what-s-new-in-the-jetpack-compose-august-26-release
- The selected bootstrap therefore uses AGP 9.1.2, Gradle 9.3.1, JDK 17, compileSdk 37, targetSdk 36, minSdk 23, Compose BOM 2026.08.00, and the AGP built-in Kotlin path with Compose compiler plugin 2.2.10. These versions are pinned in the repository and must be validated by CI.
- GitHub Actions' Gradle setup supports installing a pinned Gradle version for projects without a checked-in wrapper: https://github.com/gradle/actions/blob/main/docs/setup-gradle.md

### Implemented in this phase
- Added a minimal single-module Kotlin/Jetpack Compose Android app with an explicitly labelled engineering-preview screen.
- Added a version catalog, Gradle settings/build scripts, Android manifest and theme resources, and a conservative initial ignore file.
- Kept the FOSS app manifest free of `INTERNET`; explicitly disabled backup and cleartext traffic while key recovery and backup behavior remain undecided.
- Added a CI workflow to provision JDK 17, Gradle 9.3.1, and Android SDK 37, then run assemble, unit-test task, lint, and a merged-manifest permission check.
- Added a manifest audit script that fails if it cannot find the merged debug manifest or finds `android.permission.INTERNET` / enabled cleartext traffic.
- No camera, OCR, database, cryptography, scheduler, analytics, or network dependencies were added. The preview stores no documents and does not claim those features exist.

### Known limitations
- A standard Gradle Wrapper is not yet checked into the repository. CI uses the pinned Gradle 9.3.1 distribution directly; a checked-in wrapper remains a portability requirement before treating local command-line builds as ready for contributors.
- The CI workflow has been committed but has not yet produced a run result in this log. No build or test success is claimed.
- No feature-level test exists yet because this phase adds only a status screen. Test-first domain behavior begins with the first real storage/OCR/review feature.
- `minSdk 23` is a provisional bootstrap floor, not a product compatibility promise. Reassess it against the selected encryption and scanning libraries before those dependencies are adopted.

### Next
1. Verify the pushed build configuration through GitHub Actions and fix actual failures rather than guessing.
2. Add the standard Gradle Wrapper and verify its distribution checksum/provenance.
3. Start the encrypted-storage prototype only after researching the Android Keystore, SQLCipher, Room, and backup/key-lifecycle compatibility constraints; keep ADR-0001 Proposed until prototype evidence exists.
