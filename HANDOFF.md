# HANDOFF.md

<handoff schema="1">
updated: 2026-10-22
author: GPT-6 via connected GitHub tools
branch: agent/integration-security-hardening-candidate-2026-10-21
task: Continue CO-01/CO-02 integration hardening; preserve vault data and keep canonical branch untouched
state: RUN_526_EMULATOR_SCRIPT_SCOPE_FAILED_BEFORE_GRADLE — ONE_SHELL_PIN_BOOTSTRAP_FIX_ADDED — SYNTHETIC_SQLCIPHER_TEST_ADDED — EXACT_HEAD_CI_PENDING — LIFECYCLE_AND_PRODUCTION_KEY_MANAGER_REVIEW_OPEN
canonical_branch: feat/durable-vault-migration-journal
candidate_pr: https://github.com/D8v1d777/KAAGAZ-VAULT/pull/48
candidate_head_sha: resolve the live PR #48 head after the secure-lockscreen/test update commit
environment:
  repository_checkout_in_active_execution_environment: NOT_AVAILABLE — GitHub connector used for branch edits
  android_sdk_in_active_execution_environment: NOT_AVAILABLE
  local_gradle_build: NOT_RUN
  independent_reviewer_subagent: NOT_AVAILABLE
current_release_gate:
  last_completed_run: https://github.com/D8v1d777/KAAGAZ-VAULT/actions/runs/39728903631
  last_completed_source_test_sha: 52906c18a8d567d0c152d06e3a8e6ff249477f2e
  observed_status_when_handoff_written: Standard CI passed on exact SHA 52906c18a8d567d0c152d06e3a8e6ff249477f2e. Emulator booted, but no Gradle/test command ran: the pinned action launches each script line in a separate shell, so the PIN variable was not retained across lines and `locksettings set-pin` received an empty argument (Android NPE). This commit collapses PIN setup, cleanup trap and Gradle invocation into one folded shell command. New exact-head CI is pending.
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
  - Run #526 standard job passed but emulator job failed before Gradle because the action executes script lines separately and the PIN variable was empty. The next one-shell correction has pending exact-head CI. No instrumentation tests ran in #526.
  - Authenticated CryptoObject completion/recovery, screen-off/background locking, picker return flows, process death, biometric enrollment change and interrupted migration are not yet instrumented.
  - The synthetic SQLCipher test exercises real Room/SQLCipher, VEK wrapper store and durable journal, but deliberately does not exercise `DatabaseKeyManager`'s fixed production Keystore alias. That production integration remains unverified.
  - Independent human security review remains mandatory.
next_actions:
  - Verify the folded single-shell command sets the disposable PIN, executes connected tests and clears the PIN regardless of Gradle result.
  - Diagnose any instrumentation build/assertion failures from the exact run logs without weakening auth/key invariants.
  - Run the prepared MainActivity lifecycle instrumentation only after the current test slice compiles/executes.
  - Review the boundary to production `DatabaseKeyManager`/legacy Android Keystore wrapper; do not create/delete the production alias in tests.
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


## Runner script scope root cause — 2026-10-22

- Run #526 standard job passed, but emulator setup terminated before Gradle/test execution.
- Raw logs show separate shell invocations for each script line; `test_pin=2468` was a process-local assignment. The following command received an empty parameter and Android's `locksettings set-pin` failed.
- The next workflow stores setup, EXIT cleanup and Gradle invocation in one YAML folded shell command. The test PIN is ephemeral and only used inside the disposable emulator.
