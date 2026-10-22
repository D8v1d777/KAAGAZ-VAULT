# HANDOFF.md

<handoff schema="1">
updated: 2026-10-22
author: GPT-6 via connected GitHub tools
branch: agent/integration-security-hardening-candidate-2026-10-21
task: Continue CO-01/CO-02 integration hardening; preserve vault data and keep canonical branch untouched
state: EMULATOR_BOOT_CONFIRMED — RUN_525_AUTH_KEY_SETUP_FAILED_WITHOUT_SECURE_LOCK_SCREEN — TEST_ONLY_PIN_AND_SYNTHETIC_SQLCIPHER_GATE_ADDED — EXACT_HEAD_CI_PENDING — LIFECYCLE_AND_PRODUCTION_KEY_MANAGER_REVIEW_OPEN
canonical_branch: feat/durable-vault-migration-journal
candidate_pr: https://github.com/D8v1d777/KAAGAZ-VAULT/pull/48
candidate_head_sha: resolve the live PR #48 head after the secure-lockscreen/test update commit
environment:
  repository_checkout_in_active_execution_environment: NOT_AVAILABLE — GitHub connector used for branch edits
  android_sdk_in_active_execution_environment: NOT_AVAILABLE
  local_gradle_build: NOT_RUN
  independent_reviewer_subagent: NOT_AVAILABLE
current_release_gate:
  last_completed_run: https://github.com/D8v1d777/KAAGAZ-VAULT/actions/runs/39727573230
  last_completed_source_test_sha: 7d376082dcb316af9826ef04a14e1a7c0727dcd3
  observed_status_when_handoff_written: Standard CI passed on exact SHA 7d376082dcb316af9826ef04a14e1a7c0727dcd3 (debug/release build, JVM tests, lint, source/policy tests, offline manifest gate). The emulator booted and compiled/ran the instrumented tests. AuthBoundVaultKeyInstrumentedTest failed before its assertion because Android Keystore requires a secure lock screen and the emulator had none configured. This commit adds a test-only ephemeral PIN with an EXIT cleanup trap and a synthetic cache-only SQLCipher/journal integration test. Its exact-head CI result is pending.
  prior_green_run_policy: Older green runs do not verify a later head
scope_guard:
  - No changes to canonical branch or merge performed.
  - Do not retire legacy ciphertext, staging copies, or old key wrappers until authenticated read-back, SQLCipher reopen/integrity verification, durable migration completion, and independent review are recorded.
  - Do not describe the application as production-ready or security-certified.
owner_approved_decision:
  - A2: strong biometric OR secure device credential only where auth-per-use CryptoObject is supported and tested.
  - B2: independent random 256-bit recovery key with authenticated envelope, one-time display and exact re-entry confirmation; no server/cloud copy.
  - C1: lock on explicit lock, screen-off, app foreground loss and process restart; no inactivity timer without separate approval.
verified_in_candidate_source:
  - MainActivity creates the vault home only for VaultAccessUiState.Ready and uses VaultAccessController/VaultSession.
  - Migration controller runs journal/backend before runtime publication, and legacy provider cleanup is handled on success/failure.
  - Reminder worker does not open SQLCipher and uses generic notification text; cancellation and delivery-gate tests are present.
  - Import/repository/OCR/payload/AES-GCM/settings-transfer code now clears owned temporary plaintext or key byte arrays where possible; tests cover import-buffer clearing on success/failure.
  - Controller disposal invalidates late authentication callbacks and suppresses late state publication; prompt failures clear pending recovery state and remove only uncommitted wrapper aliases, with JVM tests plus source-level regression guards. This commit adds source guards for Activity onStop/onDestroy and recovery-confirmation cleanup on foreground loss.
  - README.md and AGENTS.md remain unchanged; HANDOFF.md is updated on this candidate branch to record current release gates.
unverified:
  - Run #525's standard job passed and the emulator launched; instrumentation failed while generating an auth-bound test key because the emulator had no secure lock screen. The next commit configures a test-only emulator PIN and adds cache-isolated SQLCipher/journal coverage; its exact-head CI remains pending.
  - Authenticated CryptoObject completion/recovery, screen-off/background locking, picker return flows, process death, biometric enrollment change and interrupted migration are not yet instrumented.
  - The synthetic SQLCipher test exercises real Room/SQLCipher, VEK wrapper store and durable journal, but deliberately does not exercise `DatabaseKeyManager`'s fixed production Keystore alias. That production integration remains unverified.
  - Independent human security review remains mandatory.
next_actions:
  - Verify the test-only emulator PIN is set/cleared reliably and exact-head run passes both Keystore and synthetic SQLCipher instrumentation.
  - Diagnose any test failures from logs without weakening auth/key invariants.
  - Extend instrumentation to Activity background/screen-off/process death, cancellation/late callbacks and recovery where the emulator supports them.
  - Review the boundary to the production `DatabaseKeyManager`/legacy Android Keystore wrapper without creating/deleting the production alias in tests.
  - Independent reviewer sub-agent is unavailable; human security review is still required. Keep PR #48 draft and unmerged until all P0 gates pass.
independent_review: NOT_AVAILABLE — no fresh-context reviewer sub-agent is available through this execution environment; human security review remains a release gate.
</handoff>


## Owner-approved instrumentation implementation — 2026-10-22

- G3 approval received in chat before dependency or workflow edits.
- Added AndroidX Test core 1.7.0, runner 1.7.0, and ext.junit 1.3.0. Official stable-version table: https://developer.android.com/jetpack/androidx/releases/test
- Added `ReactiveCircus/android-emulator-runner` pinned to `a421e43855164a8197daf9d8d40fe71c6996bb0d` (v2.38.0; release: https://github.com/ReactiveCircus/android-emulator-runner/releases/tag/v2.38.0).
- First instrumented slice tests auth-per-use Keystore enforcement and absent-alias fail-closed behavior against a synthetic cache fixture using only unique ephemeral aliases.
- No manifest permission, production key policy, alias constant, migration format, schema, or canonical branch changes.
- Exact-head CI for this commit is pending. Do not merge this draft PR; continue to lifecycle/process-restart and isolated SQLCipher migration evidence, then independent human review.


## Emulator profile setup failure and correction — 2026-10-22

- Run #524 standard job succeeded; emulator job failed before any test ran with `Error: No device found matching --device Pixel 6`.
- The pinned action README documents `profile` as an optional hardware-profile ID, not a display-name selector. The workflow correction removes this optional input; it does not change test assertions.
- Exact-head run after this correction is pending. Do not count run #524 as emulator/instrumentation validation.


## Secure lock-screen prerequisite and synthetic SQLCipher follow-up — 2026-10-22

- Run #525 confirmed AVD provisioning and instrumentation execution, but auth-bound key setup failed with `Secure lock screen must be enabled to create keys requiring user authentication`.
- The emulator workflow sets a test-only PIN immediately before the instrumentation command and clears it via an EXIT trap. This is scoped to an ephemeral CI emulator.
- Added the cache-isolated SQLCipher/journal test described in `docs/engineering/progress.md`. It never calls the production database-key manager, uses no production alias, and deletes only the UUID-named test cache fixture.
- New exact-head CI is pending; #525 remains a failed run and cannot be counted green.
