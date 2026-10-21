## 2026-10-21 — Integration security hardening continuation

- Candidate branch `agent/integration-security-hardening-candidate-2026-10-21`, PR #48; consult the live PR head and matching workflow run because follow-up handoff/plan commits continue this documentation pass.
- Cleared owned plaintext scratch buffers in document import streams, document serialization, OCR/reviewed-OCR reads and replacements, AES-GCM envelope operations, and settings-transfer crypto. Import tests cover plaintext buffer clearing on successful read-back and invalid signature rejection.
- Controller disposal now marks the controller disposed, invalidates the active prompt purpose, clears retained wrapped-key bytes before cancelling the prompt, and suppresses late authentication/UI-state callbacks. Source-level regression coverage added.
- Synchronous setup/recovery prompt setup failures now clear pending recovery material and remove only the uncommitted wrapper alias, avoiding orphaned-key lockout on retry; source-level guards cover this cleanup.
- Reminder cancellation coverage and lock-time UI metadata/settings-buffer cleanup remain part of the candidate. Migration retains legacy/staging data; no retirement or canonical merge was performed.
- Exact-head full Android CI is required for the final head. At this entry, run #485 was queued on the prior head; verify the new run after this documentation commit. Device/emulator verification and independent human security review remain outstanding.

---

## 2026-10-21 — Session-backed document store seam added to integration candidate

- Added `DocumentPayloadCryptography` with legacy and session-backed implementations. The session-backed path holds `VaultSession` access across the full encrypted-store operation (including atomic file writes and deletion), and store recovery cleanup is deferred until a permitted operation rather than running at construction time.
- Added regression coverage that locked sessions reject save/read/replace/list/delete and keep existing ciphertext.
- This adapter is not wired into `MainActivity`; production access paths and SQLCipher/worker locking remain open. Combined exact-head CI on the live PR #48 head is required after these changes.

---

## 2026-10-21 — Combined candidate regression hardening

- Expanded the integration candidate's CO-08 journal tests to cover all legacy v1 phase states and each document-step ordinal, and reject unknown v2 phase/document-step codes even when the CRC is recalculated correctly.
- Replaced partial stable-code assertions with exact expected code sequences for every persisted phase and document step.
- These additional regression tests are part of the live [draft integration PR #48](https://github.com/D8v1d777/KAAGAZ-VAULT/pull/48). Its current head's combined exact-head CI is the release gate; earlier CI runs apply only to their named SHAs.

---

## 2026-10-21 — Combined integration candidate created

- Combined CO-08, CO-04, CO-01 document-migration adapter, approved Biometric dependency, VaultSession primitive and CO-02 recovery envelope primitive onto one validation branch created from canonical SHA `6b30ceaf5ab09b2494d82356b08c4bebf7406521`.
- Integrated source SHA before report: `83aa16a0c29a57962c65d547e1204d14688ddff8`. Exact-head combined CI is pending; isolated green runs do not substitute for this run.
- See `docs/engineering/INTEGRATION-CANDIDATE-2026-10-21.md` for scope and explicit security blockers. No canonical branch changes or merges performed.

---

## 2026-10-20 — CO-01 / CO-02 owner policy approved; implementation gates opened

### Decision recorded
- The project owner approved **A2 + B2 + C1**: strong biometric OR device credential only where supported with auth-per-use cryptographic enforcement; independent random 256-bit recovery key with a separate authenticated VEK envelope; immediate lock on explicit lock, foreground loss, screen-off and process restart.
- Updated ADR-0003 to Accepted for this policy direction. This is architecture-policy acceptance, not feature completion or security certification.
- Recorded the API constraint from AndroidX primary documentation: `BIOMETRIC_STRONG | DEVICE_CREDENTIAL` via `setAllowedAuthenticators` is unsupported on API 28–29; device-credential-only is unsupported before API 30. Keep the compatibility path explicit and test it; never substitute an unauthenticated or time-window key.

### Verification boundary
- Exact-head Android CI passed on prior decision-doc head `05dfbe9ba0fc53548d345c9d8de327b35543649c` in [run #382](https://github.com/D8v1d777/KAAGAZ-VAULT/actions/runs/39399533594). The owner-acceptance updates after that SHA require the new head’s CI run.
- No production authentication, key-management, recovery-envelope, or migration code has been changed in this policy update.
- AndroidX Biometric is not currently in the version catalog. The repository’s separate dependency-approval rule still applies; the next gate is a concrete dependency justification and owner approval before Gradle dependency changes.
- Android Keystore instrumentation, API matrix verification, migration crash tests, independent security review and physical-device acceptance remain required.

### Next
- Obtain approval for the necessary AndroidX Biometric dependency after showing exact version, maintenance, license and privacy/network rationale.
- Begin test-first work in separate CO-01 and CO-02 implementation PRs after dependency/API compatibility is cleared.
- Rebase/port CO-04 changes onto current canonical and rerun full CI on that new exact head before considering merge.

---

## 2026-10-20 — CO-03 canonical UUID identity corrected

### Reproduced and fixed
- Added `EncryptedDocumentStoreTest.rejectsUpperCaseUuidAliasForReadReplaceAndDelete` before changing production code.
- Test-only SHA `27aa8be8942800ea122a9fa764ee6e5ad9d24951` failed in [run #367](https://github.com/D8v1d777/KAAGAZ-VAULT/actions/runs/39384857603): the uppercase alias read raised `IOException` rather than `IllegalArgumentException` at `EncryptedDocumentStoreTest.kt:132`; 67 tests completed, one failed.
- Changed only `EncryptedDocumentStore.fileFor` to require exact equality between `UUID.fromString(id).toString()` and the supplied ID. No file rename/migration, AAD/envelope change, key policy, dependency, manifest, or Room schema change.
- Reviewed the other UUID parse sites: `VaultMigrationState` and `VaultMigrationJournal` already enforce exact canonical spelling.
- Primary API reference checked: [Java SE 21 UUID](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/UUID.html).

### Verification
- Exact code/test SHA `e6ffd2eb571496a8817d45a6c1b31afefa652c5b` passed the full configured Android CI gate in [run #369](https://github.com/D8v1d777/KAAGAZ-VAULT/actions/runs/39385455774): Python policy tests, debug/release builds, JVM tests, lint, and merged-manifest policy gate.
- Full manual review was completed against `docs/agent/REVIEW.md`; no findings. A fresh-context reviewer sub-agent was unavailable, so HANDOFF records `independent_review: NOT_AVAILABLE`.
- The final documentation-complete commit still requires exact-head CI. No local Gradle build ran because the active container has no Android SDK or repository checkout.

### Next
- Complete the CO-03 handoff/progress update and verify the resulting exact SHA.
- Root `AGENTS.md` priority ranks open P0 orders ahead of P1 orders; prepare the CO-01/CO-02 decision memo before starting CO-04 production changes.
## 2026-10-20 — CO-00 exact-head Android CI gate passed

### Corrected
- Repaired `SettingsSyncMerger` so spoken guidance remains disabled while incoming voice-navigation enablement is awaiting an explicit user decision; preserved safe-off precedence.
- Added `confirmedIncomingVoiceEnablementCanPreserveLocallyEnabledSpokenGuidance` to cover the explicit-acceptance path.
- Corrected README evidence so the historical green run is not misrepresented as the current code head.

### Verification
- Reproduction: Android CI run [#323](https://github.com/D8v1d777/KAAGAZ-VAULT/actions/runs/39203140191) on baseline SHA `7b7620f537d03a4f15c18ac80a9c8fa5bf3bc489` failed `SettingsSyncProtocolTest.mergeKeepsLocalByDefaultAndRequiresVoiceReview` at assertion line 105 (65 tests completed, one failed).
- Fix commit: `d6083c0c428cc5f2423462bc4c1089cb0c7eee32`. Positive-path regression test commit: `356b4d76ba0ad45f57aedce5b25c60e05d14f548`.
- Full configured workflow passed on code/test SHA `c2a783385ade3f59144eb282a2983ebf1eab3720` in [run #324](https://github.com/D8v1d777/KAAGAZ-VAULT/actions/runs/39361833253).
- Full configured workflow also passed on documentation-follow-up SHA `e6db4d368ccb44499c285888fc2cdadb71bace65` in [run #325](https://github.com/D8v1d777/KAAGAZ-VAULT/actions/runs/39362435654). Build/release, JVM tests, lint, Python manifest-policy tests, and merged-manifest policy steps all report success.
- PR #18 remains open, draft and unmerged. No physical-device or release-readiness claim is made.

### Next
- CO-00 gate is closed for the verified SHA above. Continue P0 CO-01/CO-02 as separate Proposed decision work; do not wire or accept an authentication/recovery policy without the owner.
- Environment limitation remains: no local Android SDK, repository checkout, `sdkmanager`, or `gh` CLI in the active tool container.

## 2026-10-20 — CO-00 candidate repair: settings merge fails closed pending voice review

### Reproduced
- The recorded baseline source SHA `7b7620f537d03a4f15c18ac80a9c8fa5bf3bc489` failed Android CI run [#323](https://github.com/D8v1d777/KAAGAZ-VAULT/actions/runs/39203140191) at `:app:testDebugUnitTest`.
- The exact failing test was `SettingsSyncProtocolTest.mergeKeepsLocalByDefaultAndRequiresVoiceReview` at assertion line 105. Gradle reported 65 tests completed, one failed; the Python offline-manifest helper passed. This is the reproduced failure for CO-00, not a pass.

### Changed in candidate branch
- Branch: `agent/CO-00-settings-sync-review-gate`.
- `app/src/main/java/com/kaagazvault/sync/SettingsSyncProtocol.kt`: keep spoken guidance off when incoming voice-navigation enablement is awaiting explicit review and the merge leaves navigation disabled. Preserve the requirement that spoken guidance must already be enabled locally and in incoming settings, and preserve safe-off precedence.
- `app/src/test/java/com/kaagazvault/sync/SettingsSyncProtocolTest.kt`: add a positive-path regression for explicitly accepting incoming voice enablement while spoken guidance is already enabled locally.
- `README.md`: distinguish historical green run #299 on SHA `6291594886eb6f6fb03c644797afc1217adb90e6` from the later failed baseline run #323. The candidate is not described as green.

### Verification boundary
- Source fix commit: `d6083c0c428cc5f2423462bc4c1089cb0c7eee32`; the branch also has a plan and evidence-record commits. Final PR head must be checked from live PR metadata.
- Environment probe: OpenJDK 21.0.12.1 present; `ANDROID_HOME` and `ANDROID_SDK_ROOT` empty; no repository checkout at the active filesystem root; no `gh` CLI or `sdkmanager`.
- No local Gradle build ran. Exact-head CI for the candidate is pending; this correction is **UNVERIFIED** until the full gate succeeds for the final PR SHA. CO-00 remains open until then.

## 2026-10-19 — Phase 15 elderly-friendly simplified mode design

### Designed
- Added `docs/PHASE-15-ELDERLY-SIMPLIFIED-MODE-DESIGN.md` with a user-controlled simplified mode, large typography, high-contrast role strategy, layout tokens, 48dp minimum and 56–64dp preferred controls, TalkBack semantics, focus order, dynamic-color interaction, and device acceptance gates.
- Clarified the Android unit boundary: touch targets are measured in dp, while text is measured in sp; 48sp is reserved for hero typography rather than used as a touch-target unit.
- Explicitly excluded age inference, profiling, medical claims, patronizing defaults, analytics, pressure mechanics, and changes to encryption/authentication.

### Verification boundary
- This is a design-only slice; no simplified-mode preference, typography overlay, high-contrast scheme, or layout refactor was implemented.
- The design requires 2.0x font-scale, TalkBack, switch access, keyboard, dynamic-color, high-contrast, reduced-motion, and physical-device evidence before release claims.

## 2026-10-19 — Phase 19 local encrypted transfer UI implementation

### Implemented
- Added an elderly-friendly **Sync accessibility settings** card to the home screen with a clear opt-in toggle, 56dp+ simplified-mode controls, local reset, and explicit copy explaining that documents and recordings never transfer.
- Added encrypted settings export through the Android document picker, a large copyable transfer code, encrypted settings import, code entry, preview-before-apply, Keep local, Use incoming, and Cancel actions.
- Kept transfer fully local/user-mediated: no account, backend, socket, network permission, document picker access to vault files, or background sync was introduced.
- Added bounded 64 KiB settings-file reads and preserved the existing document/OCR/reminder/license UI paths.

### Verification boundary
- `git diff --check`, source-shape checks, original-activity preservation checks, and forbidden-scope audit passed.
- Android Kotlin compilation was attempted but could not start because the active sandbox has no Android SDK configured.
- Android device-picker behavior, TalkBack traversal, 2.0x font scale, and two-device transfer must be validated on physical/emulated devices before release claims.

## 2026-10-19 — Phase 19 settings-sync protocol implementation

### Implemented
- Added `sync/SettingsSyncProtocol.kt` with an allowlisted versioned settings model, deterministic codec, AES-GCM authenticated envelope, one-use expiring pairing ticket, idempotent field-level merge policy, safe-off precedence, and explicit review for incoming voice enablement.
- Added `sync/SyncableSettingsStore.kt` with a dedicated `syncable_accessibility_settings` store, migration from the legacy simplified-mode preference, local-only sync enablement control, reset support, and an offline encrypted export/import boundary that never opens a socket or touches vault data.
- Routed `SimplifiedModePreferences` through the dedicated store so the current Phase 15 setting no longer lives in a general preference file that could later become an accidental sync contract.
- Added JVM tests for deterministic serialization, unknown-field handling, duplicate rejection, AES-GCM round trips/tamper/wrong-key failures, pairing expiry and single use, forbidden-field boundaries, and conflict-safe merges.

### Verification boundary
- `git diff --check` and source-scope audits passed; no network permission, backend, account, document sync, voice data, or vault-key code was added.
- `:app:testDebugUnitTest` was attempted but stopped before test execution because the active sandbox has no Android SDK configured.
- Backup/D2D XML rules, settings UI, pairing screens, account transport, and physical two-device evidence remain separate follow-up slices and are not claimed as implemented by this commit.

## 2026-10-19 — Phase 19 cross-device settings synchronization design

### Designed
- Added `docs/PHASE-19-CROSS-DEVICE-SETTINGS-SYNC-DESIGN.md` for opt-in synchronization of only simplified view, voice navigation, spoken guidance, and voice-haptic preferences.
- Defined a separate allowlisted settings record instead of syncing the general Phase 15 `ui_preferences` file, with explicit exclusion of documents, OCR, reminders, raw voice, permissions, keys, tokens, identifiers, and biometric material.
- Defined new-device restore versus optional live sync as distinct experiences, encrypted user-mediated pairing/transfer, explicit Android cloud/D2D backup rules, conflict-safe resolution, device removal, revocation, local reset, accessibility behavior, and offline-first failure handling.

### Verification boundary
- This is a design-only slice; no account, backend, network permission, backup rule, settings-sync serializer, pairing flow, or cross-device transport was implemented.
- Android Auto Backup and Android 12+ cloud/D2D separation requirements are cited in the design and must be validated on API 24/API 31+ and representative manufacturer transfer paths before release claims.

## 2026-10-19 — Phase 18 voice-navigation haptic synchronization design

### Designed
- Added `docs/PHASE-18-VOICE-HAPTIC-SYNCHRONIZATION-DESIGN.md` with a semantic haptic event model synchronized to Phase 17 voice states, a maximum two-cue normal-command budget, explicit suppression rules, device-default/on/off preference behavior, elderly-friendly settings requirements, lifecycle/session deduplication, privacy boundaries, low-end hardware policy, and acceptance gates.
- Kept haptics as optional confirmation only: no repeating listening pulses, no document-content encoding, no voice unlock or destructive confirmation, and no replacement for visible text, TalkBack, switch access, keyboard navigation, or TTS.
- Based the design on Android guidance favoring restrained action-oriented haptics and predefined/fallback effects over long buzzy or legacy one-shot vibration patterns.

### Verification boundary
- This is a design-only slice; no new haptic event model, preference, voice controller, or vibration API call was implemented.
- The design requires API 24/API 29+/API 31+, low-end hardware, no-actuator, system-haptics-off, TalkBack, switch-access, 2.0x font-scale, reduced-motion, lock, background, and biometric-prompt evidence before release claims.

## 2026-10-19 — Phase 15 simplified-mode UI implementation

### Implemented
- Added `SimplifiedMode.kt` with a local, corruption-safe `SharedPreferences` flag, reusable simplified typography minimums, and 24dp/56dp/64dp layout tokens.
- Added an always-visible, user-controlled “Simplified view” switch to the home screen. It is reversible, does not infer age or ability, and does not touch vault data, encryption, authentication, or network behavior.
- Added enlarged typography and spacing to the home hierarchy, vertically stacked Import/Scan and Search/Show-all actions, larger empty-state and document/OCR controls, and enlarged reminder actions.
- Kept standard mode behavior and visual density when the preference is disabled; simplified mode preserves system font scaling rather than overriding it.

### Verification boundary
- `git diff --check` and source-scope audits passed.
- `:app:compileDebugKotlin` was attempted but stopped before compilation because the active sandbox has no Android SDK configured.
- Physical low-end device, TalkBack, switch-access, 2.0x font-scale, and instrumentation validation remain pending.

## 2026-10-19 — elderly-feature integration and accessibility test pass

### Executed
- Added `docs/reports/ELDERLY-FEATURES-END-TO-END-TEST-REPORT.md` with exact commands, static contrast results, feature-presence audit, and the blocked device matrix.
- `git diff --check` passed and static contrast recalculation passed; minimum tested static ratio was 5.48:1 and minimum body-text ratio after the ambient overlay was 8.38:1.
- Attempted `:app:testDebugUnitTest`; Gradle stopped before test execution because the active sandbox has no Android SDK configured.

### Findings
- The repository has 12 JVM test source files but zero instrumentation-test files and no SDK, ADB, emulator, or AV​​D configuration in the active sandbox.
- Phase 15 simplified mode and Phase 17 voice navigation are design-only at this head; no `SimplifiedMode`, simplified typography/layout tokens, `SpeechRecognizer`, `TextToSpeech`, `RECORD_AUDIO`, or voice controller exists to integrate or test.
- Phase 14 source-level behavior and static contrast are reviewable, but low-end-device, TalkBack, switch-access, font-scale, dynamic-wallpaper, and performance evidence remain unrun.

### Verification boundary
- No end-to-end, instrumentation, physical-device, or release-readiness pass is claimed from this environment.

## 2026-10-19 — Phase 17 voice-assisted navigation design

### Designed
- Added `docs/PHASE-17-VOICE-ASSISTED-NAVIGATION-DESIGN.md` with a privacy-first push-to-talk model, bounded `SpeechRecognizer` lifecycle, Android 12+ on-device availability check, fixed-phrase Text-to-Speech guidance, elderly-friendly controls, supported command grammar, confirmation rules, accessibility semantics, and acceptance gates.
- Restricted voice to reversible navigation and explicitly excluded always-listening, voice unlock, voice deletion, voice export, document dictation, OCR dictation, raw content read-aloud, and cloud search.
- Required complete touch, keyboard, TalkBack, and switch-access alternatives so voice remains optional.

### Verification boundary
- This is a design-only slice; no microphone permission, recognizer, Text-to-Speech engine, manifest query, or voice preference was added.
- Android's documented remote-processing possibility, `RECORD_AUDIO` requirement, main-thread lifecycle, on-device availability check, and TTS cleanup requirements are cited in the design and must be verified in implementation tests.

## 2026-10-19 — Android 12+ Material You color extraction

### Implemented
- Refactored `KaagazTheme` around an explicit `materialYouColorScheme` engine.
- Android 12+ uses Material 3 `dynamicLightColorScheme`/`dynamicDarkColorScheme` to extract the wallpaper-derived Material You palette.
- Preserved generated `on-*` and container role pairings without post-processing so dynamic contrast relationships remain intact.
- Added `dynamicColor` opt-out support and safe fallback to the reviewed calm light/dark palettes when unsupported, disabled, or extraction throws.

### Verification boundary
- Source-level fallback and API gating are implemented; physical Android 12+ wallpaper, light/dark, font-scale, and contrast-mode validation remains required.
- `git diff --check` passed; `:app:compileDebugKotlin` could not start because the active sandbox has no Android SDK configured (`SDK location not found`).
- No dependency, permission, network, persistence, encryption, or authentication changes were introduced.

## 2026-10-19 — UI/UX accessibility and contrast review

### Reviewed
- Calculated WCAG contrast ratios for explicit light and dark calm-theme role pairings; all tested normal-text pairs passed 4.5:1, with the lowest static result at 5.48:1.
- Stress-tested the maximum 4.5% ambient backdrop overlay; tested body and action pairings remained above 4.5:1.
- Reviewed elderly-user risks around font scaling, touch targets, semantics, reduced motion, color-only status, dynamic color, and TalkBack.
- Added `docs/reports/PHASE-14-ACCESSIBILITY-CONTRAST-REVIEW.md` with results, methodology, fixes, and physical-device acceptance gates.

### Implemented
- Added a persistent search-field label and a semantic “Live camera preview” description.

### Verification boundary
- Explicit light/dark palette calculations and source review pass.
- Android 12+ dynamic-color wallpapers, 2.0x font scale, TalkBack, switch access, high-contrast display, glare, and physical-device checks remain required before a complete accessibility claim.

## 2026-10-19 — Phase 14 ambient vault backdrop

### Implemented
- Added a low-contrast `AmbientVaultBackdrop` with six fixed, content-independent particles and very slow primary/tertiary color drift.
- Disabled particle motion when Android's animator duration scale indicates reduced motion; the fallback is a static, low-alpha gradient.
- Layered the backdrop behind the transparent scaffold so it never intercepts input or competes with document cards, text, and actions.

### Verification boundary
- Ambient effects are atmosphere only: no document state, OCR text, filenames, counts, identifiers, or reminders influence them.
- No custom audio, background service, dependency, permission, or network behavior was added.

## 2026-10-19 — Phase 14 calm micro-interactions implementation

### Implemented
- Added semantic `VaultFeedbackEvent` routing for platform click audio and brief predefined key haptics.
- Added reusable `VaultPrimaryButton`, `VaultTonalButton`, `VaultOutlinedButton`, and `VaultTextButton` Compose components.
- Added named motion tokens and reduced-motion-aware `AnimatedVisibility`/`animateContentSize` helpers using the system animator-duration scale.
- Wired success-only haptic confirmation into import, camera capture, OCR completion/review save, and reminder save without adding custom audio assets or media dependencies.
- Kept failures, cancellation, typing, scrolling, polling, and progress updates quiet; no sensitive content enters feedback payloads.

### Verification boundary
- `git diff --check` passed.
- `./gradlew --no-daemon :app:compileDebugKotlin` could not reach Kotlin compilation because the active sandbox has no Android SDK configured (`SDK location not found`); no build or test pass is claimed.
- No manifest, dependency, network-permission, persistence, encryption, or authentication changes were introduced.

## 2026-10-19 — Phase 14 calm micro-interactions and audio feedback design

### Designed
- Added `docs/PHASE-14-CALM-MICROINTERACTIONS-AUDIO-DESIGN.md` with semantic feedback events, platform-respecting sound policy, sparse predefined haptics, named motion tokens, reduced-motion behavior, accessibility requirements, preference guidance, implementation slices, and acceptance gates.
- Explicitly rejected dark-pattern engagement mechanics, repeated prompt loops, urgency theater, background audio, analytics, and sensory feedback that would make the app harder to leave or understand.

### Verification boundary
- This is a design-only slice. No new audio assets, haptic implementation, motion system, preference storage, or dependency was added.
- Existing platform click feedback remains the only implemented audio behavior until the policy and tests are approved.

## 2026-10-19 — Phase 13 biometric authentication and vault-lock UI design

### Designed
- Added `docs/PHASE-13-BIOMETRIC-VAULT-LOCK-DESIGN.md` covering the auth-per-use `BiometricPrompt.CryptoObject` flow, biometric/device-credential policy choice, locked/authenticating/recovery UI states, session boundaries, lifecycle lock policy, accessibility, error copy, implementation slices, and acceptance gates.
- Kept the design UI- and coordinator-focused; it does not claim that the existing auth-bound primitive is integrated into production access control.

### Verification boundary
- No biometric prompt, repository gate, migration runner, or key-policy change was implemented in this design-only slice.
- Product approval is required before implementation: biometric-only versus biometric-plus-device-credential fallback changes the Keystore flags, prompt configuration, copy, and device-test matrix.

## 2026-10-18 — Authentication-bound lock design and migration-state foundation

### Research and source inspection
- Reviewed Android's official BiometricPrompt/CryptoObject guidance and the Android Keystore auth-per-use key model, plus OWASP key-management and cryptographic-storage guidance.
- Confirmed the current app constructs repository/database dependencies before any auth gate and immediately lists documents; a biometric UI prompt alone would not gate the data path.
- Confirmed document payloads are encrypted directly with a non-exportable Keystore key, so legacy ciphertext cannot be migrated by simply wrapping/exporting that key. Documents require decrypt/re-encrypt migration.
- Confirmed SQLCipher uses a separately wrapped random database key; DB handles and SQLite sidecars need explicit lifecycle treatment.

### Implemented
- Added ADR-0003 proposing an auth-bound VEK/session design and documenting migration, fallback, lifecycle, accessibility, and verification gates.
- Added docs/PHASE-12-AUTH-BOUND-VAULT-LOCK-PLAN.md with staged implementation and stop conditions.
- Added VaultMigrationState as a pure immutable policy model with ordered document states, database-key migration and integrity gates, recovery pause/resume, and a legacy-retirement guard.
- Added VaultMigrationStateTest for skipped steps, premature database migration, premature legacy retirement, verification requirements, recovery resume, duplicate IDs, and canonical UUID validation.

### Verification boundary
- This model is not yet a durable on-disk journal and is not wired to production migration. It cannot itself guarantee crash safety.
- No biometric lock, authentication-bound key, document re-encryption, or SQLCipher rewrap has been implemented in this slice.
- No weighted product-completion credit is added for this foundation. Keep the overall tracker at its existing estimate until end-to-end acceptance gates pass.
- CI must pass on the latest source head before the model/test is considered verified. Device Keystore, BiometricPrompt, migration interruption, and physical-device tests remain required.

### Next actions
1. Run CI against this head and fix actual build/test failures.
2. Implement and test a durable atomic migration journal using the model, including corruption and crash recovery.
3. Finalize API-level/authenticator fallback matrix and review ADR-0003.
4. Implement auth-per-use key/CryptoObject primitives and instrumented tests before wiring the UI.
5. Implement per-document migration and SQLCipher wrapper migration; only then gate all repository actions behind the session.

---

## 2026-10-18 — Phase 11 missing-key fail-closed hardening

### Research
- Re-read the repository engineering contract and the Android, privacy/security, secure-storage/cryptography, testing-quality, implementation-quality, and accessibility/performance skills.
- Reviewed the Android Developers biometric-authentication guidance, including its distinction between an app-level BiometricPrompt and a Keystore key configured for authentication-per-use. This change does not claim to implement the latter.
- Reviewed OWASP Key Management guidance: key lifecycle and recovery must fail closed and must not silently replace lost key material.

### Implemented on phase/11-auth-bound-vault-lock
- Updated AndroidKeystoreDocumentKeyProvider to refuse first-time key creation when its caller reports protected data already exists, raising MissingEncryptionKeyException.
- Wired the document payload store to check existing encrypted payloads and replacement backups before creating a missing document key.
- Wired DatabaseKeyManager to check for an existing wrapped database key, database file, and SQLite WAL/journal/shared-memory sidecars before creating a missing wrapping key.
- Added MissingEncryptionKeyPolicyTest for the pure allow/deny policy boundary.
- Added ADR-0002-missing-key-fail-closed.md with the decision, limitations, and verification gates.

### Verification status
- Source edits and documentation are committed. Android CI run [39129650660](https://github.com/D8v1d777/KAAGAZ-VAULT/actions/runs/39129650660) completed successfully on commit `54a0ce2897fa4aeeeab896066c34adfffd290211`.
- CI completed the offline manifest helper tests, `./gradlew --no-daemon clean assembleDebug assembleRelease testDebugUnitTest lint`, and the merged-manifest policy gate. This verifies the CI build/test/lint tasks at that commit; it is not physical-device or security-review evidence.
- No Android Keystore alias-deletion test, SQLCipher recovery test, physical-device test, or independent security review was performed.
- The guard depends on an accurate caller-supplied protected-data predicate. It prevents a specific silent-replacement path but does not recover a missing key.
- This change does not add an authentication-bound Keystore key, biometric app lock, automatic relock, FLAG_SECURE validation, key rotation, or recovery mechanism.

### Next actions
1. Open/update a draft PR so the Android CI workflow runs against this branch.
2. Resolve any compilation/test/lint failures based on actual logs.
3. Add instrumented tests for missing Keystore aliases with existing document ciphertext and wrapped SQLCipher keys.
4. Design the auth-bound lock and data/key migration as a separate reviewed change; do not just add a cosmetic biometric prompt.
5. Update weighted progress only after verification evidence exists.

---

## 2026-10-10 — Phase 9 reproducible build and artifact-level offline gate

### Research and decisions
- Re-read AGENTS.md, the Android engineering, implementation-quality, release-security, and testing-quality skills.
- Followed the official Gradle Wrapper guidance: use the checked-in wrapper, validate its JAR, and pin the distribution checksum. The Gradle 9.3.1 binary distribution checksum is sourced from Gradle's published checksum reference.
- Followed the Android APK Analyzer documentation to inspect the manifest packaged in each APK, rather than treating a merged-manifest scan as sufficient.
- Pinned GitHub Actions to full commit SHAs as recommended by GitHub's Actions security guidance. Kept the workflow's read-only token permissions and concurrency control.

### Implemented on phase/09-reproducible-offline-gates
- Added the official Gradle 9.3.1 wrapper scripts/JAR and a wrapper properties file with a pinned Gradle distribution checksum.
- CI now uses ./gradlew, builds both debug and release APKs, and runs deterministic Python unit tests for the offline policy gate.
- Reduced duplicate CI work by limiting push-triggered builds to main; pull requests still run the full gate.
- Replaced the debug-only manifest script with fail-closed checks for both debug and release merged manifests and every packaged APK. The APK audit uses apkanalyzer to inspect the packaged manifest for INTERNET permission and explicitly enabled cleartext traffic.
- Added tests for safe manifests, INTERNET permission, cleartext enablement, malformed XML, variant discovery, APK manifest inspection, and analyzer failure.

### Verification status at commit time
- The preceding Phase 8 head 029c77f693ff0ee4cb95c852d19f349c62f0252b had successful Android CI runs 38044704501 and 38044696303.
- This Phase 9 change has not yet been built by CI at the time this entry is authored. Do not treat wrapper bootstrap, release build, APK analysis, or the new Python tests as verified until the new PR run completes.

### Post-commit CI evidence (2026-10-10)
- Draft PR #8 code commit 05ce7d7cc0c46aa4f1c1abf2e68dc4b297138cd7 completed CI run 38070788162 successfully.
- Python offline-policy tests, Gradle setup, assembleDebug, assembleRelease, testDebugUnitTest, lint, and the merged-manifest plus packaged-APK offline gate all completed successfully.
- This run did not install the APK on a physical device and does not constitute release-signing evidence or an independent security review.

### Remaining risks / next steps
- The wrapper and distribution checksums must be validated by CI; a locally unexecuted build is not evidence.
- Device-level privacy behavior, SQLCipher WAL/journal leakage, Keystore invalidation, OCR language accuracy, and release signing remain outside this CI hardening slice.
- Next product slice remains a real auth-bound vault lock and crash-safe migration of document/database wrapping keys; do not substitute a cosmetic biometric prompt.


## 2026-10-10 — Phase 8 OCR provenance and backward-compatible payloads

### Research → work
- Followed the repository OCR skill: extraction method and language must remain explicit, confidence is not automatically a calibrated probability, and OCR output remains untrusted until human review.
- Reviewed the current offline OCR engine and encrypted payload codec. Before this change, image OCR and PDF native text extraction could be confused in the UI; only a numeric score and a generic “PDF text layer” fallback were displayed.

### Implemented on `phase/08-ocr-provenance`
- OCR results now record a source identifier: `tesseract-image`, `pdf-text-layer`, `tesseract-pdf`, or `pdf-mixed-text-and-ocr`.
- Persists the OCR language/model set alongside the encrypted OCR text and raw engine confidence.
- Advances the encrypted payload format to v3 while continuing to read v1 legacy payloads and v2 OCR payloads. Older records show provenance as unknown rather than fabricating it.
- UI labels raw OCR confidence as not a probability, and displays extraction source/languages separately from human-review state.
- Added codec tests for v3 provenance round-trip and v2 backward compatibility.

### Verification / safety boundaries
- This branch has not yet run CI; compile, tests, lint, and offline-manifest policy are pending.
- This does not add field extraction, medical action extraction, or automated reminders. OCR text remains untrusted; users must review/correct it before relying on it.
- Need tests for malformed/oversized provenance strings and explicit payload-v1/v2/v3 compatibility before calling this phase complete.

---

## 2026-10-10 — Phase 7 notification tap flow

### Research → work
- Reviewed Android notification guidance and added an immutable explicit activity PendingIntent rather than embedding any reminder title, document name, or linked document identifier in the notification intent.
- Tapping a reminder now opens the vault entry point; it does not expose the reminder content directly from the notification.

### Change
- `ReminderNotificationWorker` attaches an immutable, update-current PendingIntent targeting `MainActivity`, with clear-top/single-top navigation flags.
- Updated the product completion tracker to reflect that reminder code exists in draft PR #6 while keeping its weighted credit at 0 until CI and device acceptance evidence exist.

### Verification
- The latest CI run must cover this commit; no green result is claimed yet.
- Tapping behavior and lock-screen privacy still require device testing.

---

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
