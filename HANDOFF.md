# HANDOFF.md

<handoff schema="1">
updated: 2026-10-20
author: GPT-6 via connected GitHub tools
task: CO-01 — non-destructive document migration backend
branch: agent/CO-01-document-migration-backend
state: IN_PROGRESS — DRAFT_PR_OPEN — FINAL_HEAD_CI_RECHECK_REQUIRED
canonical_branch: feat/durable-vault-migration-journal
canonical_base_sha: 789816fece4b90bb981b9e9cb3a5954f1eebd514
pull_request: https://github.com/D8v1d777/KAAGAZ-VAULT/pull/42

owner_policy:
  - Owner approved A2 + B2 + C1 on 2026-10-20.
  - Owner separately approved option 1 in PR #40: stable androidx.biometric:biometric:1.1.0. Implementation is in draft PR #41; no merge is authorized by that approval.
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
latest_prior_verified_code_commit:
  commit: 0fae9a6ae23af84bd36c9f4d37b0ee971fc1bb82
  ci_run: https://github.com/D8v1d777/KAAGAZ-VAULT/actions/runs/39416524805
  ci_status: SUCCESS on that exact SHA only; the later directory-sync source change and documentation commits invalidate it as final-head evidence.
implemented_in_pr_42:
  - EncryptedDocumentMigrationBackend implements the document portion of VaultMigrationBackend: canonical legacy ID discovery, same-ID encryption under the existing AesGcmEnvelope v1 format and document-ID AAD, staged authenticated plaintext equality, publication into a separate root, and idempotent replay.
  - Legacy ciphertext and staged ciphertext are retained. The active app path is not switched and no legacy key/ciphertext retirement occurs.
  - Staged and published output writes use directory-level FileChannel.tryLock locking, fd.sync() before rename, and best-effort directory metadata sync when the platform/filesystem supports it.
  - MigrationVekKeyAccess is a callback boundary intended to be backed by VaultSession later. The adapter is not connected to MainActivity or production repository paths.
  - VaultDatabaseMigrationActions is an explicit injected boundary only. It does not implement database-key rewrap, SQLCipher close/reopen, or integrity checking.
  - Tests cover same-ID staging, idempotent stage/publish, tamper failure, refusal to overwrite a bad destination, separate roots, and injected exceptions after each backend side effect (stage, verify checkpoint 1, verify checkpoint 2, publish, DB rewrap, DB integrity).
  - Test commit preceded implementation: test SHA 11e7a8adbc9bc78bff048d22f67dcd989d944126; initial adapter SHA 9f9efa6d6404fe9554947ddccecfee8f3203f9ae; final source change currently recorded as 7a8671951f217d8bc9054a394153981272f5a33a.
not_verified:
  - Current PR head SHA must be read from PR #42; wait for a completed green CI run whose SHA equals that head. Do not reuse earlier green CI.
  - No local build or tests ran in the active container.
  - No production app integration, actual SQLCipher key rewrap/reopen/integrity, process-kill or physical power-loss test, Android Keystore instrumentation, or root-switch cutover is included.
  - Fault-injection tests throw after a method's durable side effect; they are not evidence of actual device power-loss behavior.
  - Directory metadata sync is best effort on API 26+; API 24–25 and filesystems that refuse directory channels remain a device-validation limitation.
  - No instrumentation source set or emulator CI job has verified key invalidation, biometric enrollment change, process restart, or recovery.
  - CO-06 dependency verification remains open; this PR does not add verification metadata.
scope_guard:
  - No key alias, cipher, envelope/journal format, Room schema, manifest permission, dependency, production call site, app active-root switch, or data deletion change.
  - Do not merge without independent human security review.
  - Do not retire legacy ciphertext or wrappers until authenticated read-back, SQLCipher reopen/integrity checks and durable journal COMPLETE are proven.
next_actions:
  - Read PR #42 current head, then verify its exact-head CI. Fix only reproduced failures.
  - Cold-review the adapter diff, including file locking and interrupted pending-file handling.
  - Keep CO-01 open until VaultSession is integrated, protected repository/background access is gated, prompt behavior is validated, and CO-02 instrumented cases pass.
  - Design and implement the actual SQLCipher key lifecycle only with an approved concrete contract; do not invent another wrapped-key envelope or silently regenerate key material.
  - Keep PR #42 draft/unmerged until independent human review.
decisions:
  - Separate legacy, staging and published roots to avoid replacing legacy ciphertext before durable migration completion.
  - Keep SQLCipher rewrap/integrity as an explicit collaborator instead of fake-success operations.
  - Add no dependencies in this slice.
conflicts:
  - docs/agent/REVIEW.md is absent on the canonical branch (GitHub contents request returned 404). No fresh-context sub-agent capability is available here; perform a cold review and retain the independent human-review gate.
independent_review: NOT_AVAILABLE — no sub-agent capability; human security review required.
</handoff>
