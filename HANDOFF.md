# HANDOFF.md

<handoff schema="1">
updated: 2026-10-22
author: GPT-6 via connected GitHub tools
branch: agent/integration-security-hardening-candidate-2026-10-21
task: Continue CO-01/CO-02 integration hardening; preserve vault data and keep canonical branch untouched
state: ANDROID_KEYSTORE_INSTRUMENTATION_SLICE_ADDED — RUN_524_EMULATOR_PROFILE_SETUP_FAILED_BEFORE_TESTS — PROFILE_FIX_PENDING_CI — LIFECYCLE_AND_SQLCIPHER_TESTS_OPEN — HUMAN_REVIEW_REQUIRED
canonical_branch: feat/durable-vault-migration-journal
candidate_pr: https://github.com/D8v1d777/KAAGAZ-VAULT/pull/48
candidate_head_sha: 760f3a3fa738037334739e2ea2db3356e1b77034 at the instrumentation attempt; resolve the live PR head after the profile-fix commit
environment:
  repository_checkout_in_active_execution_environment: NOT_AVAILABLE — GitHub connector used for branch edits
  android_sdk_in_active_execution_environment: NOT_AVAILABLE
  local_gradle_build: NOT_RUN
  independent_reviewer_subagent: NOT_AVAILABLE
current_release_gate:
  last_attempt_run: https://github.com/D8v1d777/KAAGAZ-VAULT/actions/runs/39726598625
  attempted_source_test_sha: 760f3a3fa738037334739e2ea2db3356e1b77034
  observed_status_when_handoff_written: Standard CI passed on exact SHA 760f3a3fa738037334739e2ea2db3356e1b77034 (debug/release build, JVM tests, lint, policy tests, offline manifest gate). Emulator job failed before tests because avdmanager could not find hardware profile named "Pixel 6". The runner expects an optional profile ID; the workflow correction removes this input. The instrumentation tests have not run yet; next exact-head CI is pending.
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
  - Run #524 passed the standard Android CI build/policy/test job on exact source SHA `760f3a3fa738037334739e2ea2db3356e1b77034`, but its emulator job failed during AVD creation before test execution because the configured hardware profile was not recognized. The workflow profile-compatibility correction and its exact-head retry remain pending.
  - No physical-device/emulator instrumentation run for authenticator compatibility, recovery, screen-off/background locking, picker return flows, process death or interrupted migration.
  - SQLCipher rewrap/reopen/integrity behavior remains unverified on a disposable Android vault.
  - Independent human security review remains mandatory.
next_actions:
  - Re-run exact-head CI after removing the invalid `Pixel 6` profile; verify the runner boots and the instrumentation command executes.
  - Fix any reproduced instrumentation assertion/build failures without weakening the security invariant.
  - Add isolated synthetic-database instrumentation for real SQLCipher key re-wrap, close/reopen, row preservation, `PRAGMA integrity_check`, and legacy-wrapper retention. Keep production paths and aliases untouched; skip safely if the emulator is not clean.
  - Add activity background/screen-off/process-death and late-callback coverage, then perform a cold diff review.
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
