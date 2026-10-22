# HANDOFF.md

<handoff schema="1">
updated: 2026-10-22
author: GPT-6 via connected GitHub tools
branch: agent/integration-security-hardening-candidate-2026-10-21
task: Continue CO-01/CO-02 integration hardening; preserve vault data and keep canonical branch untouched
state: INTEGRATION_CANDIDATE — RECOVERY_ALIAS_CLEANUP_CI_VERIFIED_AT_621eca27 — LIFECYCLE_WIRING_GUARDS_ADDED_CI_PENDING — DEVICE_VERIFICATION_REQUIRED — HUMAN_REVIEW_REQUIRED
canonical_branch: feat/durable-vault-migration-journal
candidate_pr: https://github.com/D8v1d777/KAAGAZ-VAULT/pull/48
candidate_head_sha: resolve from the live PR #48 head after this handoff commit; do not trust a cached SHA here
environment:
  repository_checkout_in_active_execution_environment: NOT_AVAILABLE — GitHub connector used for branch edits
  android_sdk_in_active_execution_environment: NOT_AVAILABLE
  local_gradle_build: NOT_RUN
  independent_reviewer_subagent: NOT_AVAILABLE
current_release_gate:
  exact_head_ci_run: https://github.com/D8v1d777/KAAGAZ-VAULT/actions/runs/39724232188
  verified_source_test_sha: 621eca27ed3f41a3bc5aa7ac84901af93cd734bc
  observed_status_when_handoff_written: Android CI run #522 completed successfully on exact source/test head 621eca27ed3f41a3bc5aa7ac84901af93cd734bc. Python policy tests, debug/release builds, JVM unit tests, lint, and offline merged-manifest policy all passed, including recovery alias cleanup tests. The current commit adds static lifecycle wiring checks; its exact-head CI is pending.
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
  - The code/test head `621eca27ed3f41a3bc5aa7ac84901af93cd734bc` passed exact-head Android CI run #522. This current commit adds static lifecycle guards and planning notes; the fresh exact-head CI result is not yet available. Check it before claiming current-head CI.
  - No physical-device/emulator instrumentation run for authenticator compatibility, recovery, screen-off/background locking, picker return flows, process death or interrupted migration.
  - SQLCipher rewrap/reopen/integrity behavior remains unverified on a disposable Android vault.
  - Independent human security review remains mandatory.
next_actions:
  - Confirm the new exact-head CI run covers lifecycle wiring guards; fix only reproduced failures.
  - Prepare a dependency/action decision memo for the CO-02 Android instrumented-test gate; obtain explicit G3 approval before adding AndroidX Test dependencies or external emulator workflow actions. Then test setup, unlock, recovery, app background/screen-off, external picker return, process death, and interrupted migration.
  - Add and run a real Android instrumentation test for SQLCipher rewrap/reopen/integrity and legacy/staging retention on a disposable synthetic test vault after the test framework is approved.
  - Perform a cold review of the final diff; independent reviewer sub-agent is unavailable, so human review is still required.
  - Keep PR #48 draft and unmerged until all P0 gates pass.
independent_review: NOT_AVAILABLE — no fresh-context reviewer sub-agent is available through this execution environment; human security review remains a release gate.
</handoff>
