# HANDOFF.md

<handoff schema="1">
updated: 2026-10-20
author: GPT-6 via connected GitHub tools
task: CO-01 — non-destructive document migration backend
branch: agent/CO-01-document-migration-backend
state: IN_PROGRESS — DRAFT_PR_OPEN — EXACT_HEAD_CI_RECHECK_REQUIRED
canonical_branch: feat/durable-vault-migration-journal
canonical_base_sha: 789816fece4b90bb981b9e9cb3a5954f1eebd514
pull_request: https://github.com/D8v1d777/KAAGAZ-VAULT/pull/42

owner_policy:
  - Owner approved A2 + B2 + C1 on 2026-10-20.
  - Owner separately approved option 1 in PR #40: stable androidx.biometric:biometric:1.1.0. Its implementation is in draft PR #41; no merge is authorized by that approval.
environment:
  active_container_pwd: /
  repository_checkout: MISSING
  git_rev_parse_head: FAILED_NOT_A_GIT_REPOSITORY
  java: OpenJDK 21.0.12.1
  android_home: EMPTY
  android_sdk_root: EMPTY
  local_properties: ABSENT
  gh_cli: MISSING
  sdkmanager: MISSING
  local_build_or_tests: NOT_RUN
latest_verified_before_latest_documentation_edits:
  commit: 0fae9a6ae23af84bd36c9f4d37b0ee971fc1bb82
  ci_run: https://github.com/D8v1d777/KAAGAZ-VAULT/actions/runs/39416524805
  ci_status: SUCCESS for the commit above only; documentation/order-status commits followed, so verify the PR's current head has its own completed green run before claiming final verification.
implemented_in_pr_42:
  - Added EncryptedDocumentMigrationBackend as an implementation of VaultMigrationBackend for discovering legacy IDs, encrypting and staging with the existing AesGcmEnvelope v1 format and document-ID AAD, authenticated source/staged equality, and non-destructive publication into separate roots.
  - Legacy ciphertext and staged ciphertext are retained; the active app path is not switched and no legacy key/ciphertext retirement occurs.
  - MigrationVekKeyAccess is a callback boundary intended to be backed by VaultSession later. The adapter is not connected to MainActivity or repository paths.
  - VaultDatabaseMigrationActions is an explicit injected boundary only. It does not implement database-key rewrap, SQLCipher close/reopen, or integrity checking.
  - Added EncryptedDocumentMigrationBackendTest covering same-ID staging, idempotent stage, tamper failure, non-destructive publication, refusal to overwrite invalid published data, root separation, and crash recovery at stage, verify checkpoint 1, verify checkpoint 2, publish, DB rewrap, and DB integrity.
  - Test commit preceded implementation; test commit 11e7a8adbc9bc78bff048d22f67dcd989d944126; implementation first commit 9f9efa6d6404fe9554947ddccecfee8f3203f9ae; adapter cleanup 7e3bbd4852da2b0e38c4ffd8676fbf9405bebcb5.
  - docs/engineering/progress.md and docs/agent/CORRECTION-ORDERS.md were updated on this branch; CO-01 remains IN_PROGRESS.
not_verified:
  - Latest exact-head CI after the documentation/status edits has not yet been observed. Get the PR's current head SHA, fetch the run for that exact SHA, and inspect every job step.
  - No local build/test was possible in this environment.
  - No production app integration, actual database-key rewrap, SQLCipher reopen/integrity, process-kill/power-loss testing, or Android Keystore instrumentation is included.
  - The tests inject exceptions after durable side effects; they are not evidence of actual device power-loss durability.
  - No instrumentation source set or emulator CI job has yet verified key invalidation, enrollment change, process restart, or recovery.
  - CO-06 dependency verification remains open; no dependency-verification metadata was added in this PR.
scope_guard:
  - No key alias, encryption format, journal format, Room schema, manifest permission, dependency, production call site, app active-root switch, or data deletion changes.
  - Do not merge without independent human security review.
  - Do not retire legacy ciphertext or wrappers until authenticated read-back, SQLCipher reopen/integrity and durable journal COMPLETE are demonstrated.
next_actions:
  - Confirm PR #42 current head SHA and exact-head CI; fix only reproduced failures and rerun after relevant code changes.
  - Cold-review the backend and test diff, including directory locks and recovery on interrupted writes.
  - Continue to keep CO-01 open until VaultSession is integrated, all protected repository/background access is gated, biometric prompt behavior is validated, and CO-02 instrumented cases pass.
  - Implement the actual SQLCipher key lifecycle only after an approved concrete contract; do not invent another wrapped-key envelope or silently regenerate key material.
  - Keep PRs draft/unmerged until independent human review.
decisions:
  - Use separate legacy/staging/published roots to avoid replacing legacy ciphertext before a durable completed migration.
  - Keep SQLCipher rewrap/integrity as a required dependency instead of reporting a fake successful database migration.
  - Keep the PR narrowly scoped to document migration and do not add dependencies.
conflicts:
  - docs/agent/REVIEW.md is absent on the canonical branch (GitHub contents request returned 404); a fresh-context sub-agent is not available in this workflow. Cold manual review is required here and independent human review remains mandatory.
independent_review: NOT_AVAILABLE — no sub-agent capability; human security review required.
</handoff>
