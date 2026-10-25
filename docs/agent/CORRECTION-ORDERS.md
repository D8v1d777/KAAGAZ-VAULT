<protocol>
source: static reading of branch feat/durable-vault-migration-journal at commit 7b7620f, audited 2026-10-20. Nothing was compiled or executed: the audit environment had no Android SDK.
initial_status_of_every_order: SUSPECTED
CO-1 Reproduce first (root AGENTS.md W3). Write a failing test or repro command. If it passes, set status REFUTED, record the evidence in HANDOFF.md, move on.
CO-2 Fix with the smallest change. One order per branch `agent/<id>-<slug>` and per PR.
CO-3 Set FIXED only with: test names, commit SHA, and the CI run URL for that SHA.
CO-4 For orders with needs_human: yes, prepare a concrete decision memo (options, recommendation, diff or design) and ask once. Never decide alone.
CO-5 Add orders you discover. Use this schema and the next free id.
statuses: OPEN | IN_PROGRESS | FIXED | REFUTED | BLOCKED_ON_HUMAN
line numbers refer to commit 7b7620f; re-locate by symbol if they have moved.
</protocol>

<index>
id     sev  needs_human  title
CO-00  P0   no           HEAD has no recorded build or test result
CO-01  P0   yes          Security primitives are unwired; no access gate exists; ADR-0003 is still Proposed
CO-02  P0   yes          Key invalidation can destroy the vault; no recovery path; no instrumented tests
CO-03  P1   no           Upper-case UUIDs accepted and addressed as a second identity
CO-04  P1   no           Undecryptable documents vanish silently from list and search
CO-05  P1   partly       Library listing decrypts every full payload; main-thread file I/O at startup
CO-06  P1   no           Build downloads models with SHA-1 pins; task never cached; no dependency verification
CO-07  P1   no           Room schema not exported; migration untested
CO-08  P2   no           Journal persists enum ordinals; tautological check; duplicate verify
CO-09  P2   no           Create-once guarantees are check-then-act; locks are per instance
CO-10  P3   no           Settings-sync expiry is in-process only; peer timestamps unbounded
CO-11  P2   no           Backup and device-transfer rules unverified for Android 12+
CO-12  P2   no           Docs are stale and contradictory; skill requires an unreachable Notion report
CO-13  P3   yes          Document list sorts Z to A
CO-14  P3   yes          Continuous ambient animation on low-end target devices
CO-15  P2   no           8 of 12 skill files lack the name and description header
</index>

<order id="CO-00" sev="P0" needs_human="no">
claim: No build or test result is recorded for the branch head, and the last green run predates several commits.
evidence:
  - README.md:19 and :35 cite CI #299 for commit 6291594886eb6f6fb03c644797afc1217adb90e6. The head at audit time is 7b7620f.
  - docs/engineering/progress.md entries dated 2026-10-19 state that Kotlin compilation or unit tests "could not start" because the sandbox had no Android SDK (for example lines 12-36, 60-71, 99-110, 138-150).
  - .github/workflows/android-ci.yml:3-6 runs on pushes to main and on pull requests only.
action:
  1 Confirm an open draft PR targets canonical_branch so CI runs.
  2 Obtain the gate result for the head SHA (CI, or local after SDK setup per root verification NO ANDROID SDK).
  3 Fix real failures only, one commit per cause, each cited from log lines.
  4 Update README.md:19 and :35 to cite only a SHA with a recorded green run.
done_when: head SHA has a recorded green gate and README cites exactly that SHA and run URL.
</order>

<order id="CO-01" sev="P0" needs_human="yes">
claim: The auth-bound key, migration coordinator, journal, and wrapped-key store are not connected to the app, so no authentication protects any data. ADR-0003 forbids implementation until accepted, yet the code exists.
evidence:
  - AuthBoundVaultKey, VaultMigrationCoordinator, VaultMigrationJournal, VaultMigrationBackend, WrappedVaultKeyStore appear only in their own files under security/ (grep over app/src/main).
  - No class implements VaultMigrationBackend in app/src/main.
  - MainActivity.kt:93-102 builds EncryptedDocumentStore, VaultDatabaseProvider, and DocumentRepository in onCreate with no authentication step.
  - DocumentKeyProvider.kt:51-60 generates the payload key without setUserAuthenticationRequired.
  - docs/engineering/decisions/ADR-0003-auth-bound-vault-session.md header: "Proposed; implementation must not begin until migration and fallback decisions are accepted."
action:
  1 Write the decision memo as a Proposed ADR update: options for authenticator policy (strong biometric only | strong biometric plus device credential | separate passphrase-wrapped second envelope), lock policy, recovery path (see CO-02). Give a recommendation and a failure matrix. Do not implement.
  2 Ask the human once (Q2 in HANDOFF.md).
  3 After acceptance, implement in this order, one PR each: VaultMigrationBackend adapter with a test per crash point; VaultSession (in-memory VEK, lock, unlock, inactivity timeout); route DocumentRepository, ReminderRepository, EncryptedMetadataIndexProvider, and VaultDatabaseProvider through the session; BiometricPrompt UI.
  4 Until acceptance, mark the unwired classes `SPIKE: not wired` in their KDoc and in README.md.
required_test: with the vault locked, every repository entry point throws or returns a locked result and never returns plaintext (JVM test with a fake session).
done_when: human sets ADR-0003 to Accepted; the session gate test passes on CI for the SHA; CO-02 instrumented tests ran.
</order>

<order id="CO-02" sev="P0" needs_human="yes">
claim: The wrapping key is invalidated by biometric enrollment and requires strong biometrics only. Once wired, adding or removing a fingerprint, or having no strong biometric, can make the vault permanently unreadable. The product targets elderly users (Phase 15) and has no recovery path or instrumented tests.
evidence:
  - AuthBoundVaultKey.kt:146-155: setUserAuthenticationRequired(true), setInvalidatedByBiometricEnrollment(true), AUTH_BIOMETRIC_STRONG on API 30 and later.
  - No recovery-key or passphrase type exists in app/src/main.
  - app/src contains `main` and `test` only; no androidTest.
action:
  1 Extend the CO-01 memo with the enrollment-change and no-biometric cases and a recovery design: recovery key shown once, or device-credential-capable key, or passphrase-wrapped second VEK envelope. Include user-facing copy and the loss scenarios.
  2 Add an androidTest source set and an emulator CI job (pin third-party actions by SHA, like the existing workflow). Cover: Keystore auth-per-use key creation, behavior after biometric enrollment change where the emulator allows it, alias loss with existing ciphertext, process restart.
required_test: instrumented tests for each case above; each labeled VERIFIED only after CI ran on the exact SHA.
done_when: recovery design Accepted; instrumented job green for the SHA.
</order>

<order id="CO-03" sev="P1" needs_human="no">
claim: EncryptedDocumentStore accepts upper-case UUIDs and then uses the original spelling for the filename and the AAD, so one logical document can exist under two identifiers.
evidence:
  - EncryptedDocumentStore.kt:135 `require(parsed.toString() == id.lowercase())`, then :136 builds the filename from `id`; AAD uses `id.toByteArray` at :32, :59, :90.
  - VaultMigrationState.kt:128 and VaultMigrationJournal.kt:183, :198 compare exactly.
action: compare exactly (`parsed.toString() == id`). Search every `UUID.fromString` in app/src/main and apply the same rule. Do not rename or migrate existing files: save() always generates lower-case ids.
required_test: read, replace, delete, and save reject an upper-case form of an existing id; lower-case round trip is unchanged.
done_when: tests green on CI for the SHA.
</order>

<order id="CO-04" sev="P1" needs_human="no">
claim: list() and search() discard every failure, so an undecryptable, tampered, or key-lost document disappears from the UI with no signal. This contradicts INV-6.
evidence:
  - DocumentRepository.kt:125-127 and :138-141 use runCatching { ... }.getOrNull() inside mapNotNull.
  - `grep -rnE "runCatching|getOrNull|Throwable|catch (" app/src/main` reports 60 matching lines across 20 files at commit 7b7620f.
action:
  1 Return a listing type carrying documents plus unreadable ids (or throw typed exceptions). Let MissingEncryptionKeyException and EncryptedDocumentAuthenticationException reach an explicit UI state: "N documents cannot be opened", with no content shown.
  2 Classify every matching site into FAIL_CLOSED_OK | SWALLOWS_SECURITY_ERROR | UI_ONLY, in docs/agent/plans/CO-04.md. Fix every SWALLOWS_SECURITY_ERROR.
required_test: with one corrupted .kgv file among three, the listing returns two documents and one unreadable id; with the key missing, the call surfaces MissingEncryptionKeyException.
done_when: tests green on CI for the SHA; classification table committed.
</order>

<order id="CO-05" sev="P1" needs_human="partly">
claim: Every list() decrypts and decodes the full payload of every document (up to 32 MiB each) and rebuilds the index; startup does file I/O on the main thread. The quality gates name low-end devices as a target.
evidence:
  - DocumentRepository.kt:125 (decode every payload), :129 (index rebuild on each list), :144 (search fallback calls list), :84 and :101 (recognizeDocument reads the payload twice).
  - EncryptedDocumentStore.kt:140 (32 MiB cap) and :20-26, :62-79 (init lists and deletes files); constructed in onCreate at MainActivity.kt:93.
  - docs/engineering/quality-gates.md names Redmi 9A.
action:
  1 Measure: JVM benchmark with synthetic payloads (50 and 200 documents of 5 MiB). Record peak heap and wall time of list() in docs/engineering/performance.md.
  2 Move store construction and temp-file recovery off the main thread. Remove the second read in recognizeDocument.
  3 Propose a listing design (ADR): list from SQLCipher metadata rows or a small authenticated per-document header. This changes a persisted format, so it is GATED (G2): ask once with the memo.
done_when: numbers recorded; steps 1 and 2 merged green; step 3 decision recorded.
</order>

<order id="CO-06" sev="P1" needs_human="no">
claim: Build integrity is weaker than the project's own standard. Model files are pinned by git-blob SHA-1 only, the download task runs on every build, dependency versions bypass the catalog, and no dependency verification exists.
evidence:
  - app/build.gradle.kts:44 `outputs.upToDateWhen { false }`; :52 and :68 MessageDigest "SHA-1"; :60 HttpURLConnection download of tessdata.
  - app/build.gradle.kts:90 and :98 declare dependencies as string literals while gradle/libs.versions.toml defines sqlcipher.
  - gradle/ contains libs.versions.toml and wrapper only; no verification-metadata.xml.
action:
  1 Compute SHA-256 for each pinned model at the pinned revision, cross-check against the existing git-blob SHA-1, and pin SHA-256 in the build script.
  2 Remove upToDateWhen { false }; declare the revision and hashes as task inputs so Gradle caches the task.
  3 Move the two literal dependencies into libs.versions.toml with the same versions (G3 applies only if a version changes).
  4 Run `./gradlew --write-verification-metadata sha256 help`, review, commit gradle/verification-metadata.xml, keep CI green.
  5 Document in README.md that the build needs network for the models while runtime stays offline, and how to pre-seed them.
done_when: gate green on CI for the SHA with verification-metadata.xml enforced.
verification: source SHA `bbea1eb7a697d89c06f27fbc725dbfa175ee50e5` passed exact-head run #565 (standard and emulator jobs, 11 instrumentation tests); dependency verification and the OCR model `UP-TO-DATE` gate passed; README documents network/offline model handling.
</order>

<order id="CO-07" sev="P1" needs_human="no">
claim: Room schemas are not exported, so migrations cannot be tested, and the one existing migration is unverified.
evidence: app/src/main/java/com/kaagazvault/database/VaultDatabase.kt:13 `exportSchema = false`; :60-72 MIGRATION_1_2; docs/engineering/progress.md records real v1 migration as unverified.
action: set exportSchema = true; add `ksp { arg("room.schemaLocation", "$projectDir/schemas") }`; commit schemas/<version>.json for versions 1 and 2 (derive version 1 from git history, verify it against the migration SQL); add an instrumented migration test using MigrationTestHelper; add a JVM test that fails when no schema file exists for the current version.
done_when: schemas committed; migration test ran on CI for the SHA.
</order>

<order id="CO-08" sev="P2" needs_human="no">
claim: The journal persists enum ordinals, contains a check that can never fail, and the coordinator verifies a staged document twice in a row.
evidence: VaultMigrationJournal.kt:127, :130, :135 (ordinal); :198 (`step !in DocumentMigrationStep.entries`); VaultMigrationCoordinator.kt:36 and :43 (verifyStagedDocument called back to back).
action: replace ordinals with stable explicit codes plus fromCode(); pin the codes in a test; if any journal could already exist on a device, bump FORMAT_VERSION to 2 and keep a v1 reader (CO-01 shows nothing wires the journal yet, so confirm and record); delete the tautology; remove the duplicate verify or document, by crash point, why both are required.
done_when: tests green on CI for the SHA.
</order>

<order id="CO-09" sev="P2" needs_human="no">
claim: Create-once guarantees are check-then-act, and the locks cover one object instance only.
evidence: @Synchronized at VaultMigrationCoordinator.kt:16, VaultMigrationJournal.kt:43 and :92, WrappedVaultKeyStore.kt:24 and :48. WrappedVaultKeyStore.kt:52-58 checks existence, then :68 `pending.renameTo(file)`; DatabaseKeyManager.kt:56-57 has the same pattern. File.renameTo behavior with an existing destination is platform-dependent and replaces the file on Linux.
action: apply SEC-10 in app/src/main/java/com/kaagazvault/security/AGENTS.md: one process-wide owner per store plus FileChannel.tryLock on a lock file.
required_test: two store instances racing on separate threads; exactly one create succeeds and the other fails closed.
done_when: tests green on CI for the SHA.
</order>

<order id="CO-10" sev="P3" needs_human="no">
claim: The five-minute pairing expiry exists only inside one process, and peer-supplied timestamps are unbounded.
evidence: SettingsSyncProtocol.kt:125-135 (ticket expiry in memory); :247-249 (incoming newer if its timestamp is larger, no upper bound); :209 (`key.encoded` copy never zeroed).
action: either bind an expiry into the authenticated payload and enforce it on import, or remove the expiry claim from docs and UI copy; reject updatedAtEpochMillis more than 24 hours ahead of local time; zero the byte array after encoding the transfer code.
required_test: future-dated incoming settings rejected; expired bundle rejected if expiry is bound.
done_when: tests green on CI for the SHA.
</order>

<order id="CO-11" sev="P2" needs_human="no">
claim: The manifest disables backup but declares no data-extraction rules, and Android 12 and later handle device-to-device transfer through those rules.
evidence: app/src/main/AndroidManifest.xml:7-8 (allowBackup="false", fullBackupContent="false"); no android:dataExtractionRules; docs/PHASE-19-CROSS-DEVICE-SETTINGS-SYNC-DESIGN.md says the rules need validation.
action: read the current Android documentation for API 31 and later behavior when allowBackup is false; record the finding with the link in docs/engineering/. If the docs or lint call for it, add dataExtractionRules excluding every domain. Add a check to scripts/check_offline_manifest.py and a unit test in scripts/.
done_when: finding recorded with source; script check and test in place; gate green on CI for the SHA.
</order>

<order id="CO-12" sev="P2" needs_human="no">
claim: Project docs contradict each other, and one skill requires a report the agent cannot reach.
evidence:
  - README.md:38 and :53 and .agents/skills/implementation-quality/SKILL.md:13 require a Notion Product Review Report.
  - docs/engineering/product-completion.md and README.md both say 35% and describe Phase 8 as awaiting CI.
  - docs/engineering/progress.md has Phase 9, 8, and 7 entries after Phase 3 entries (lines 231-467) and the title "# Engineering Progress Log" at line 469; lines 81-83 say Phases 15 and 17 are design-only while lines 60-71 record Phase 15 implemented.
action: install the provided .agents/skills/implementation-quality/SKILL.md; rewrite progress.md newest-first with `superseded_by:` markers on stale statements; regenerate product-completion.md with an evidence SHA per objective; remove Notion references or mark them optional.
done_when: no document names an unreachable required step; the tracker cites SHAs.
</order>

<order id="CO-13" sev="P3" needs_human="yes">
claim: The document list is sorted Z to A.
evidence: DocumentRepository.kt:127 `sortedByDescending { it.displayName.lowercase() }`.
action: ask the human whether this is intended (Q3). If not, sort ascending by name; add a test.
</order>

<order id="CO-14" sev="P3" needs_human="yes">
claim: AmbientVaultBackdrop animates continuously, and the stated target includes low-end devices.
evidence: app/src/main/java/com/kaagazvault/AmbientVaultBackdrop.kt (animated particles; static fallback only for reduced motion).
action: measure frame time with an emulator profile; stop the animation when Power Saver is on; offer a decision memo: keep behind a setting, or remove.
</order>

<order id="CO-15" sev="P2" needs_human="no">
claim: Eight skill files have no name and description header, so hosts that discover skills by that header will not load them. AGENTS.md routes by path, which masks the gap.
evidence: files without a header: accessibility-performance, android-release-security, document-imaging-evaluation, encrypted-data-search, implementation-quality, indian-language-documents, reminders-action-safety, secure-storage-cryptography. Files with a header: android-engineering, ocr-action-extraction, privacy-security, testing-quality.
action: add the header to each of the eight: `name` equal to the folder name; `description` stating what the skill does AND when to use it, in one sentence, copied in meaning from its Purpose section. implementation-quality is covered by the provided replacement.
done_when: all 12 skill files start with a valid header.
</order>