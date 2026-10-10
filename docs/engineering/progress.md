# Engineering Progress Log

This log records repository work and evidence. It distinguishes documentation changes from application implementation.

## 2026-10-10 — CI remediation and stable SDK correction

### Research and diagnosis
- Read `AGENTS.md` and the Android engineering, privacy/security, testing-quality, and Android release-security skills before changing CI.
- Inspected the failed Android CI job logs. The initial failure was in `android-actions/setup-android@v3` while running `sdkmanager tools`; it failed with “Failed to find package 'tools'”.
- Updated the setup action to `android-actions/setup-android@v4.0.4` and explicitly requested `platform-tools`. This passed the SDK setup step.
- The next run failed because the SDK package repository did not offer `platforms;android-37` to the runner, even when requesting channel 3. Official Android docs label API 37 as the Android 17 preview SDK and describe installing the preview platform and Build Tools 37.0.0: https://developer.android.com/about/versions/17/setup-sdk
- To avoid making a simple bootstrap depend on preview packages, reviewed official Compose release guidance and selected stable Compose BOM `2026.04.01`, which predates the August Compose 1.12 release that requires API 37: https://developer.android.com/blog/posts/whats-new-in-the-jetpack-compose-april-26-release and https://developer.android.com/blog/posts/what-s-new-in-the-jetpack-compose-august-26-release

### Changes committed to `phase/02-android-bootstrap`
- `237a4293cfb3f7abef5e284b45fd1a362e479a59`: switched Android SDK setup action.
- `5ed59d77c9113cb934271e9293be79c39341872e`: attempted preview-channel SDK installation; CI confirmed package lookup still failed.
- `245411a00b2e8a269befce14138b386495d771ed`: changed `compileSdk` to 36.
- `5847b631434132f4e472f1d0c9d77c97b2dbec6f`: pinned Compose BOM `2026.04.01`.
- `41045162498c21cd97b38215795bc6807c9cbf3c`: returned CI SDK install to stable `platforms;android-36` and `build-tools;36.0.0`.

### Verification
- Confirmed the latest CI failure cause from the job logs; Gradle build, unit-test task, lint, and manifest policy check did not run in the failed attempts.
- The workflow for the final stable-SDK commit has not yet been observed completing. **Build status remains unverified.**
- The Compose preview screen is still the only app UI; no vault feature is implemented yet.

### Next
1. Inspect CI for the final stable-SDK commit; diagnose and fix any failures using actual logs.
2. Add a standard Gradle Wrapper with validated distribution checksum and test the reproducible wrapper build.
3. Research Android Keystore, Room, SQLCipher for Android, key lifecycle, migration, and data-sidecar leakage before choosing a storage implementation.
4. Implement the smallest tested vertical slice for encrypted local document storage only after the security design is documented.

## 2026-10-09 — Engineering foundation hardening

### Completed
- Added focused skills for cryptographic storage, encrypted search, document imaging evaluation, reminder safety, Indian-language documents, Android release security, and accessibility/performance.
- Added ADR-0001 to block premature plaintext OCR indexing and force a measured decision about search/storage trade-offs.
- Updated the engineering contract with skill-routing and decision-record requirements.

### Decisions and rationale
- Search/storage leakage is a primary architecture risk because FTS, WAL/journal files, thumbnails, backups, and temporary files can undermine file-only encryption.
- Specialized skills separate distinct review disciplines while the root engineering contract remains the shared source of truth.
- Changes are on a dedicated branch so they can be reviewed as a coherent documentation set.

### Verification
- Repository baseline files were fetched from GitHub before editing.
- This change set was documentation-only; it did not implement Android functionality.
- No Android build or test suite was run as part of that documentation change.

## 2026-10-09 — Search/storage candidate research

### Findings
- Official SQLCipher for Android documentation describes Room integration for both Room 2 and Room 3; current integration uses `SupportOpenHelperFactory` for Room 2 or `SQLCipherDriver` for Room 3.
- Zetetic's Community Edition has attribution/licence-notice obligations; include those notices if selected.
- At the time of research, the repository had no Android Gradle project to compile against, so compatibility could not be confirmed.

### Decision
- SQLCipher for Android is the first candidate for a prototype, not an approved dependency.
- Keep ADR-0001 Proposed until a minimal integration is built and checked against the chosen Room generation, target/min SDK, native ABIs, key lifecycle, database sidecars, and mixed-language search requirements.

### References reviewed
- https://github.com/sqlcipher/sqlcipher-android
- https://www.zetetic.net/sqlcipher/sqlcipher-for-android-migration/
- https://www.zetetic.net/sqlcipher/license/
- https://central.sonatype.com/artifact/net.zetetic/sqlcipher-android
- https://developer.android.com/training/data-storage/room/migration-2-to-3

### Verification
- Primary documentation and artifact metadata were inspected.
- No dependency was added and no prototype/build/test was run; compatibility remains unverified.

## 2026-10-09 — Phase 1: repository baseline and public-exposure review

### Completed
- Confirmed the repository was public and PR #1 was open/draft on `docs/engineering-hardening` → `main`.
- Inspected repository trees, visible main-branch commits, PR metadata, and engineering skills.
- Recorded the missing Android baseline in `docs/engineering/baseline-audit.md`.
- Rewrote the README to distinguish product goals from implemented/verified features.
- Reviewed official Android architecture and Gradle compatibility guidance plus GitHub secret-scanning documentation.

### Public-repository safety findings
- The inspected trees contained documentation only at that point; no app source or real-document fixtures were found.
- The visible main commit history was documentation-only, but this was not a complete historical secret scan. No secret-scanning alert result was available through the audit. Do not interpret this as proof that no credential was ever exposed.
- If a real credential is discovered, revoke/rotate it first; deleting the latest copy does not invalidate historical exposure.

### Verification
- GitHub API tree and PR metadata responses were inspected.
- README, audit document, and progress log were written to the hardening branch and fetched again to verify persistence.
- No Android build/tests were run because no Android project/build files existed yet.

## 2026-10-09 — Phase 2: Android project bootstrap

### Research before implementation
- Read Android engineering, privacy/security, testing-quality, and Android release-security skills.
- Official Android guidance recommends distinct UI/data layers, repositories as the UI entry point to data, and testable state-driven UI: https://developer.android.com/topic/architecture/recommendations
- AGP 9.1.1 supports API 37, requires Gradle 9.3.1, and uses JDK 17: https://developer.android.com/build/releases/agp-9-1-0-release-notes
- The August 2026 Compose BOM `2026.08.00` requires API 37 and AGP 9.1.2 or newer: https://developer.android.com/blog/posts/what-s-new-in-the-jetpack-compose-august-26-release
- The bootstrap initially used AGP 9.1.2, Gradle 9.3.1, JDK 17, compileSdk 37, targetSdk 36, minSdk 23, Compose BOM 2026.08.00, and the AGP built-in Kotlin path with Compose compiler plugin 2.2.10. CI did not successfully install the API 37 platform.

### Implemented
- Added a minimal single-module Kotlin/Jetpack Compose Android app with an explicitly labelled engineering-preview screen.
- Added a version catalog, Gradle settings/build scripts, Android manifest and theme resources, and a conservative ignore file.
- Kept the FOSS app manifest free of `INTERNET`; disabled backup and cleartext traffic while key recovery and backup behavior remain undecided.
- Added CI for JDK 17, pinned Gradle, Android SDK provisioning, build/test/lint, and a merged-manifest permission check.
- Added a manifest audit script that fails if it cannot find the merged debug manifest or finds `android.permission.INTERNET` / enabled cleartext traffic.
- No camera, OCR, database, cryptography, scheduler, analytics, or network dependencies were added.

### Known limitations
- A standard Gradle Wrapper is not checked into the repository. CI uses the pinned Gradle distribution directly.
- Build verification is pending; no build/test success is claimed.
- No feature-level tests exist because this phase adds only a status screen.
- `minSdk 23` is provisional; reassess against selected encryption and scanning libraries.

### Next
1. Verify the final stable-SDK build configuration through GitHub Actions.
2. Add the standard Gradle Wrapper and verify its distribution checksum/provenance.
3. Start the encrypted-storage prototype only after researching Android Keystore, SQLCipher, Room, and backup/key-lifecycle constraints; keep ADR-0001 Proposed until prototype evidence exists.
