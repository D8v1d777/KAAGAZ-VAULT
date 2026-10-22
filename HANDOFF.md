# HANDOFF.md

<handoff schema="1">
updated: 2026-10-22
author: GPT-6 via connected GitHub tools
branch: agent/integration-security-hardening-candidate-2026-10-21
task: Continue CO-01/CO-02 integration hardening; preserve vault data and keep canonical branch untouched
state: RUN_527_PIN_SETUP_SUCCESS — ONE_AUTH_ASSERTION_EXCEPTION_WRAPPER_MISMATCH — SQLCIPHER_SYNTHETIC_TEST_NO_FAILURE_REPORTED — AUTH_ASSERTION_AND_LIFECYCLE_TEST_FIX_ADDED — EXACT_HEAD_CI_PENDING — PRODUCTION_KEY_MANAGER_AND_HUMAN_REVIEW_OPEN
canonical_branch: feat/durable-vault-migration-journal
candidate_pr: https://github.com/D8v1d777/KAAGAZ-VAULT/pull/48
candidate_head_sha: bcf27b1a0a59a4ed7ae5902e9438b61e4b706718 for run #527; resolve the live PR #48 head after the auth assertion/lifecycle follow-up
environment:
  repository_checkout_in_active_execution_environment: NOT_AVAILABLE — GitHub connector used for branch edits
  android_sdk_in_active_execution_environment: NOT_AVAILABLE
  local_gradle_build: NOT_RUN
  independent_reviewer_subagent: NOT_AVAILABLE
current_release_gate:
  last_completed_run: https://github.com/D8v1d777/KAAGAZ-VAULT/actions/runs/39729721807
  last_completed_source_test_sha: bcf27b1a0a59a4ed7ae5902e9438b61e4b706718
  observed_status_when_handoff_written: Standard job passed. Emulator PIN setup worked (PIN set to '2468'); the connected test command executed 3 Android tests and reported one failure. The auth-per-use test expected a top-level UserNotAuthenticatedException, while Android 16 wrapped the Keystore "Key user not authenticated" denial in IllegalBlockSizeException. The other two tests (missing-alias fail-closed and synthetic SQLCipher/journal) were not reported as failed. This follow-up checks the cause chain, adds real Activity-stop lifecycle instrumentation, and adds a source guard for the one-shell command. New exact-head CI is pending.
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
  - Run #527 standard job passed. Emulator ran 3 instrumentation tests after successfully setting the temporary PIN, with one failure caused by the auth-denial test requiring a specific top-level exception class. The underlying Keystore cause states 'Key user not authenticated'. The next source corrects this expectation and adds Activity lifecycle instrumentation; new exact-head CI is required.
  - Authenticated CryptoObject completion/recovery, screen-off/background locking, picker return flows, process death, biometric enrollment change and interrupted migration are not yet instrumented.
  - The synthetic SQLCipher test exercises real Room/SQLCipher, VEK wrapper store and durable journal, but deliberately does not exercise `DatabaseKeyManager`'s fixed production Keystore alias. That production integration remains unverified.
  - Independent human security review remains mandatory.
next_actions:
  - Verify fresh exact-head CI passes the cause-chain auth-denial check, missing-alias guard, synthetic SQLCipher/journal test and both MainActivity lifecycle tests.
  - Review lifecycle behavior from the emulator result; then expand screen-off/process-death/recovery coverage where the runner permits.
  - On a separate reviewed candidate, integrate PR #49 dependency-verification metadata and record checksums for all approved AndroidX Test dependencies.
  - Review production `DatabaseKeyManager`/legacy Keystore wrapping separately without creating/deleting the production alias in tests.
  - Keep PR #48 draft/unmerged until P0 gates and independent human security review pass.
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


## Runner script scope root cause — 2026-10-22

- Run #526 standard job passed, but emulator setup terminated before Gradle/test execution.
- Raw logs show separate shell invocations for each script line; `test_pin=2468` was a process-local assignment. The following command received an empty parameter and Android's `locksettings set-pin` failed.
- The next workflow stores setup, EXIT cleanup and Gradle invocation in one YAML folded shell command. The test PIN is ephemeral and only used inside the disposable emulator.


## Run #527 instrumentation findings — 2026-10-22

- Run #527 standard job passed. The emulator's one-shell PIN setup was effective (log says `Pin set to '2468'`).
- Three instrumented tests executed. The sole failure was an exception-class assertion in the auth-per-use test: Android 16 produced `IllegalBlockSizeException` wrapping the Keystore denial `Key user not authenticated`. The test now checks the underlying denial in the cause chain.
- The missing-alias fail-closed and synthetic SQLCipher/journal test were not reported as failed. Fresh exact-head CI is required for the follow-up and Activity lifecycle tests.
- PR #48's branch does not include PR #49's dependency-verification metadata yet. Review/integrate metadata and checksum pins for the AndroidX Test dependencies before any combined release candidate.
