## 2026-10-10 — Phase 7 reliability hardening (CI pending)

### Research → work
- Rechecked the official Android WorkManager documentation for unique work, delayed one-time requests, and worker input. Reminder work is deferrable and does not promise exact-to-the-minute delivery; no exact-alarm permission is requested.
- Reviewed the worker's failure behavior. A notification permission revoked between check and post can throw; a disabled channel can suppress delivery. These are user-controlled delivery states, not transient worker failures.

### Changes on `phase/07-private-reminders`
- Extracted UUID-only WorkManager input construction into `ReminderScheduler.buildInputData`.
- Added a JVM test asserting the WorkManager payload contains exactly one key (the opaque reminder UUID), and rejects non-UUID content.
- Added graceful handling for notification permission revocation races and disabled notification channels. The encrypted reminder remains visible in-app; the one-time worker does not retry forever when Android intentionally suppresses notifications.
- Draft PR #6 remains open and unmerged: https://github.com/D8v1d777/KAAGAZ-VAULT/pull/6.

### Verification status
- CI run https://github.com/D8v1d777/KAAGAZ-VAULT/actions/runs/38044125829 was still in progress at the last check. Build, tests, lint, and offline-manifest gate are not yet confirmed green for these newest changes.
- Still required: device testing for notification denial/channel disabled, reboot/process death, database migration from a real v1 SQLCipher database, and worker execution after document-vault lock/key invalidation.
- Do not claim phase completion until CI passes and the product-level acceptance criteria are addressed.

---

## 2026-10-10 — Phase 7 local reminders implementation (CI pending)

### Research
- Checked the official AndroidX WorkManager release notes; selected stable WorkManager 2.12.0 (released 2026-09-23): https://developer.android.com/jetpack/androidx/releases/work
- Applied the existing privacy rule: WorkManager stores only an opaque reminder UUID; reminder title and linked document ID live in the SQLCipher metadata database.
- Notification text is generic by default. No document title, reminder title, medical term, or OCR-derived action is sent to the notification surface.
- Reminder creation is explicitly user-triggered; OCR does not create reminders automatically.

### Implemented on `phase/07-private-reminders`
- Added `ReminderEntity` / `ReminderDao` and a Room migration from database v1 to v2.
- Added a `ReminderRepository` for validated future reminders, persistence, unique WorkManager scheduling, and cancellation on deletion.
- Added a WorkManager worker that reads the reminder from SQLCipher at execution time, no-ops for deleted reminders, and posts generic notification text.
- Added Compose UI to create, list, and delete reminders with explicit date/time selection.
- Added the Android 13+ notification permission declaration and request only after the user saves a reminder.
- Draft PR #6: https://github.com/D8v1d777/KAAGAZ-VAULT/pull/6 (base `phase/06-pdf-ocr-stacked`; not merged).

### Verification / limitations
- PR creation succeeded and CI was triggered, but its result has not yet been retrieved. Do not mark this phase build-verified until build, unit tests, lint, and offline-manifest checks finish.
- Device-level notification permission denial, channel disablement, reboot/rescheduling, process death, duplicate work replacement, and migration from a real v1 SQLCipher database remain untested.
- No biometric gate, export/recovery, or release readiness is claimed.

### Next actions
1. Retrieve PR #6 CI jobs and fix compile/test/lint failures from actual logs.
2. Add tests for UUID-only WorkManager input, reminder validation, deletion/no-op behavior, and Room migration.
3. Continue with biometric/app lock and key lifecycle only after the PDF OCR branch is verified and the reminder branch is green.
4. Update the weighted completion tracker only after verification evidence exists.

---

## 2026-10-10 — Phase 6 bounded in-memory PDF OCR

### Research
- Reviewed PdfiumAndroidKt 2.0.0 APIs for opening ByteArray input, native text-layer extraction, page rendering, and explicit document/page/text-page lifecycle: https://github.com/johngray1965/PdfiumAndroidKt
- Selected PdfiumAndroidKt 2.0.0, the current stable-SDK-compatible release with ByteArray input, native text extraction, and page rendering. Version 2.0.3 was rejected because it requires compileSdk 37 while Android 17 remains preview. The trade-off is minSdk 24 and JDK 21; app bytecode target remains Java 17.
- No plaintext PDF/page temp files are used. PDF input is bounded to 20 MiB, 30 pages, 60 million total rendered pixels, 2000 px max edge, 10,000 native-text chars/page, and 150,000 combined output characters.

### Implemented
- Added PdfiumAndroidKt 2.0.0 dependency, moved minSdk to 24, and switched CI JDK to 21.
- Extended offline OCR to prefer embedded PDF text and render/OCR scanned pages one at a time. Tesseract is initialized only when a page requires OCR and reused across pages.
- Added bounded page and text limits, truncation reporting, Unicode/control-character sanitization, and null confidence for native PDF text (no fabricated 100% confidence).
- Wired the document action to process both images and PDFs and store results in the existing encrypted payload/index.
- Added dependency attribution to the packaged third-party notices and docs/engineering/pdf-ocr.md.

### Verification
- Phase 3 CI passed at run https://github.com/D8v1d777/KAAGAZ-VAULT/actions/runs/38030021406.
- Phase 4 CI passed at run https://github.com/D8v1d777/KAAGAZ-VAULT/actions/runs/38030642290.
- CameraX initial code passed at run https://github.com/D8v1d777/KAAGAZ-VAULT/actions/runs/38031188831; the latest stacked orientation-normalization head is still awaiting CI.
- The current PDF OCR branch changes minSdk/JDK/Kotlin/KSP and adds native PDFium; the first run failed because PDFium 2.0.3 required preview compileSdk 37. The branch now pins 2.0.0 and aligns Kotlin/KSP; the new CI run must pass before this feature is considered build-verified. No device PDF rendering, native text accuracy, memory performance, or storage-leakage tests have been run.

### Next cycle
1. Resolve any compile/dependency errors from the latest PDFium/JDK21/minSdk24 CI run.
2. Add synthetic PDF fixtures and instrumented tests for native text, scanned pages, malformed/password-protected PDFs, and resource cleanup.
3. Continue with biometric/app lock, reminders, preview/export, accessibility, and release packaging.

---

## 2026-10-10 — Phase 4 encrypted metadata/search prototype

### Research
- Read the encrypted-data-search, secure-storage, and implementation-quality skills and ADR-0001 before implementation.
- Room 2.8.4 + KSP 2.2.10-2.0.2 matches the Kotlin 2.2.10 toolchain. SQLCipher for Android 4.19.1 documents Room 2 integration via SupportOpenHelperFactory and API 23+ support: https://github.com/sqlcipher/sqlcipher-android
- SQLCipher Java logging is redirected to NoopTarget. Required Apache-2.0 and SQLCipher BSD-style license texts are packaged and third-party notices are exposed in the UI.
- A random 256-bit SQLCipher key is wrapped by a separate Android Keystore AES key; only the wrapped envelope is stored under noBackupFilesDir.

### Implemented
- Added Room/KSP/AndroidX SQLite/SQLCipher dependencies.
- Added SQLCipher metadata database, a wrapped database-key manager, lazy database initialization on the I/O executor, and a metadata index for normalized name + OCR text.
- Added substring/phrase search UI. Search query text is bound to DAO parameters and not logged. If encrypted DB initialization fails, the app falls back to an in-memory scan of decrypted payloads.
- Kept encrypted document payload files as the source of truth; library refresh rebuilds the metadata index. Added fail-closed handling when the database exists but its wrapped key is missing.
- Added mixed-script normalization tests and packaged third-party license notices.
- Updated ADR-0001 prototype status while keeping the decision Proposed.

### Verification
- CI run https://github.com/D8v1d777/KAAGAZ-VAULT/actions/runs/38030642290 completed successfully on commit d1f6e4ccbe758d05fad0bff2ab0fb7ce082d18e2.
- Passed: SDK provisioning, Gradle clean/assembleDebug/testDebugUnitTest/lint, and the offline merged-manifest policy.
- This proves build/test/lint gates only. SQLCipher runtime encryption, WAL/journal leakage, wrong-key behavior on device, database recovery, and search performance remain unverified.
- Estimated implemented-scope progress: 29% weighted objectives. See docs/engineering/product-completion.md.

### Next cycle
1. Verify the Phase 5 CameraX branch and fix any actual dependency/compile/test errors.
2. Add Android instrumentation tests for wrapped database keys, database open/reopen, and storage leakage.
3. Continue with PDF OCR, biometric/app lock, reminders, preview/export, accessibility, and release packaging.

---

## 2026-10-10 — Offline OCR and human review slice

### Research
- Context7 documentation review confirmed ML Kit bundled Android OCR covers Latin and Devanagari, not Telugu, so it was not selected as the only OCR engine.
- Tesseract4Android 4.9.0 wraps Tesseract 5.5.1 and requires language models under a private `tessdata` directory: https://github.com/adaptech-cz/Tesseract4Android
- Official `tessdata_fast` model files for English, Hindi, and Telugu are pinned to commit `87416418657359cb625c412a48b6e1d6d41c29bd`: https://github.com/tesseract-ocr/tessdata_fast/tree/87416418657359cb625c412a48b6e1d6d41c29bd
- Each model's Git blob SHA-1 is checked both during build-time asset generation and before copying to private storage.

### Implemented in code
- Added Tesseract4Android dependency and a Gradle task to fetch/pin/hash-check English, Hindi, and Telugu traineddata models, then package them as app assets. Model downloads occur during build; runtime OCR is offline.
- Added image-only OCR with a bounds-first decode and downsampling to a maximum 2400 px dimension, worker-thread execution, explicit native-resource cleanup, and mean engine score.
- Extended encrypted document payload format to v2 for OCR text, engine score, truncation, and review state; legacy v1 payloads remain readable.
- Added encrypted same-ID payload replacement and startup recovery for interrupted replacement backup/pending files.
- Added UI to run OCR on imported images, edit extracted text, and explicitly save it as reviewed. New OCR resets the review flag. No OCR result is automatically turned into an action.
- Added `docs/engineering/offline-ocr.md` and a weighted product completion tracker.

### Verification status
- The latest CI run after the OCR dependency/model task was still queued/in progress at the last check: https://github.com/D8v1d777/KAAGAZ-VAULT/actions
- **No passing result is claimed for the OCR changes yet.** The critical next step is to inspect the newest run after model downloads, Gradle dependency resolution, JNI packaging, compile, unit tests, lint, and offline-manifest checks.
- OCR model assets add about 7.9 MB before packaging; installed app storage also holds a verified private copy.
- Notion AI sub-agent discovery was attempted but blocked by the workspace entitlement (Business plan or higher); no sub-agent session was created. Independent review is therefore not claimed.

### Next actions
1. Wait for/check the latest CI result and fix actual build/test failures.
2. Add payload format v1/v2 round-trip and encrypted OCR update/recovery tests.
3. Add Android Keystore instrumentation coverage and device/emulator smoke tests when available.
4. Continue with PDF page OCR only after designing a path that does not leave decrypted plaintext in temporary files.
5. Build the encrypted metadata/search layer, then reminders, camera scanning, access-control, and release gates.

---

## 2026-10-10 — Phase 3: encrypted local payload prototype

### Research before implementation
- Read AGENTS.md and the Android engineering, privacy/security, testing-quality, secure-storage/cryptography, encrypted-search, and release-security skills.
- Android recommends app-private internal storage for private app-only files: https://developer.android.com/training/data-storage/app-specific
- Android cryptography guidance recommends established platform cryptography and Android Keystore for stored keys: https://developer.android.com/privacy-and-security/cryptography
- AES-GCM uses an IV and authentication tag: https://developer.android.com/reference/javax/crypto/spec/GCMParameterSpec
- Kept ADR-0001 Proposed; this phase does not select a database/search index or persist OCR text.

### Implemented on phase/03-encrypted-local-files
- Added Android Keystore AES-256 key provider; existing-key retrieval errors fail instead of silently rotating the key.
- Added a versioned AES-GCM envelope with fresh 12-byte IV, 128-bit tag, and document UUID authenticated as AAD.
- Added encrypted file persistence with UUID names, ciphertext-only pending file, sync-before-rename, strict ID validation, fail-closed reads, and a 32 MiB byte-array bound.
- Added JVM tests for round-trip, fresh IVs, ciphertext tampering, wrong AAD, truncation/version rejection, file lifecycle, and path traversal.
- Added docs/engineering/secure-local-files.md with implementation boundaries and acceptance gates.
- Opened draft PR #3: https://github.com/D8v1d777/KAAGAZ-VAULT/pull/3. It has not been merged.

### Verification at log update
- CI runs 38028960654, 38028971588, and 38029001766 had reached the Gradle build/test/lint step but were still reported in progress when checked.
- Therefore, no passing result is claimed for this phase yet. Check the latest run and fix any failures from actual logs.
- Android Keystore runtime behavior is not covered by the current JVM tests; instrumentation coverage remains a gate.

### Product functionality added after the initial storage prototype
- Added `DocumentRepository` for Android Storage Access Framework imports of user-selected PDFs/images, bounded at 31 MiB, with display name/MIME metadata stored inside the encrypted payload.
- Added opaque encrypted-ID listing to the storage boundary and a local vault UI for import, list, and delete; file I/O is off the main thread.
- Added a test that the storage listing ignores invalid filenames and pending files.
- Added `docs/engineering/document-import.md` describing behavior, limits, and remaining device-level acceptance checks.
- A source review caught and fixed a main-looper reference in the UI before claiming CI verification.

### Limitations and next actions
- Only document payload bytes are encrypted; metadata, OCR text, thumbnails, search indexes, and database sidecars are not implemented.
- ByteArray API is capped at 32 MiB; large/multi-page documents need a separately reviewed streaming/chunked authenticated format.
- No biometric lock, key recovery/rotation, backup recovery, secure deletion, database, OCR, or UI integration yet.
- Next: verify CI, add Android Keystore instrumentation coverage, then research and implement user-selected SAF import with strict size bounds and encrypted metadata. No broad storage permissions or INTERNET permission should be added.

---

# Engineering Progress Log

This log records repository work and evidence. It distinguishes documentation changes from application implementation.

## 2026-10-10 — CI remediation and stable toolchain correction

### Research and diagnosis
- Read `AGENTS.md` and the Android engineering, privacy/security, testing-quality, and Android release-security skills before changing CI.
- The initial failure was in `android-actions/setup-android@v3` while running `sdkmanager tools`; it failed with “Failed to find package 'tools'”.
- Updated the setup action to `android-actions/setup-android@v4.0.4` and explicitly requested `platform-tools`. This passed the SDK setup step.
- The next run failed because the SDK repository did not offer `platforms;android-37` to the runner, even with channel 3. Official Android docs identify API 37 as the Android 17 preview SDK: https://developer.android.com/about/versions/17/setup-sdk
- Switched to stable SDK 36 and Compose BOM `2026.04.01`. Official release notes say the April Compose release is stable; the August Compose 1.12 release requires compileSdk 37: https://developer.android.com/blog/posts/whats-new-in-the-jetpack-compose-april-26-release and https://developer.android.com/blog/posts/what-s-new-in-the-jetpack-compose-august-26-release
- The stable SDK installed successfully, but Gradle then failed to resolve plugin `com.android.application:9.1.2`. The official AGP release notes identify published AGP 9.1.1 and its Gradle/JDK compatibility: https://developer.android.com/build/releases/agp-9-1-0-release-notes

### Changes committed to `phase/02-android-bootstrap`
- `237a4293cfb3f7abef5e284b45fd1a362e479a59`: switched Android SDK setup action.
- `5ed59d77c9113cb934271e9293be79c39341872e`: attempted preview-channel SDK installation; CI confirmed package lookup still failed.
- `245411a00b2e8a269befce14138b386495d771ed`: changed `compileSdk` to 36.
- `5847b631434132f4e472f1d0c9d77c97b2dbec6f`: pinned Compose BOM `2026.04.01`.
- `41045162498c21cd97b38215795bc6807c9cbf3c`: returned CI SDK install to stable `platforms;android-36` and `build-tools;36.0.0`.
- `b4ab192da99501f10dab16fb7c317d0daef138e5`: changed AGP to published version 9.1.1 based on the official release notes.

### Verification
- SDK setup and stable SDK package installation passed in [CI run 38023963731](https://github.com/D8v1d777/KAAGAZ-VAULT/actions/runs/38023963731).
- Gradle configuration failed before compilation because AGP 9.1.2 could not be resolved. Unit tests, lint, and the offline-manifest gate did not run in that attempt.
- A new CI run for the AGP 9.1.1 correction is pending. **Build status remains unverified.**
- The Compose preview screen is still the only app UI; no vault feature is implemented yet.

### Next
1. Inspect CI for the AGP 9.1.1 commit; diagnose any failures from actual logs.
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
