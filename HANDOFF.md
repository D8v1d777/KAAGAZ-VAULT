## Current agent handoff — 2026-10-20 — PR #42 document migration backend

- Active branch: `agent/CO-01-document-migration-backend`.
- Canonical base: `feat/durable-vault-migration-journal` at `789816fece4b90bb981b9e9cb3a5954f1eebd514`.
- Draft PR: https://github.com/D8v1d777/KAAGAZ-VAULT/pull/42.
- Test-first commit: `11e7a8adbc9bc78bff048d22f67dcd989d944126`; adapter implementation and review cleanup through `7e3bbd4852da2b0e38c4ffd8676fbf9405bebcb5`.
- Tests cover authenticated same-ID staging, source preservation, idempotent stage/publish, tampering, separate storage roots, and coordinator replay after failures injected after stage, verify checkpoint #1, verify checkpoint #2, publish, database-key rewrap, and database integrity.
- Adapter keeps legacy, staging and published roots separate. It retains both legacy and staged ciphertext, and uses the pre-existing AES-GCM v1 envelope and document-ID AAD.
- `VaultDatabaseMigrationActions` is an interface only; no actual SQLCipher rewrap/reopen/integrity implementation is claimed.
- No production call site, repository/session gating, active-root cutover, key rotation, deletion, manifest, dependency, envelope/journal format, or Room schema change.
- Environment cannot run local Gradle/Android tests; wait for exact-head GitHub Actions CI on the latest PR head. Current branch documentation update means older heads do not count.
- Must not merge without independent human security review. Do not retire legacy ciphertext or wrappers until end-to-end authenticated read-back, SQLCipher reopen/integrity checks and durable journal COMPLETE are verified.
- Next: verify final-head PR #42 CI, investigate only reproduced failures, then review the backend's file-lock/write recovery. Actual SQLCipher integration is a separate bounded gate and must preserve the current key hierarchy until its contract is accepted.

---

# HANDOFF.md

<handoff schema="1">
updated: 2026-10-20
author: GPT-6 via connected GitHub tools
branch: agent/CO-01-02-owner-security-decision
task: CO-01 / CO-02 — owner security decision memo
state: OWNER_POLICY_APPROVED — IMPLEMENTATION GATED
memo: docs/engineering/decisions/CO-01-02-owner-security-decision-memo.md
memo_commit: e3c0393f9081ead1b3018331ddeb5149c1edaf9c
implementation_authorized: true — after dependency approval gate and per-order test-first implementation
canonical_branch: feat/durable-vault-migration-journal
environment:
  android_sdk_in_active_execution_environment: MISSING
  repository_checkout_in_active_execution_environment: MISSING
  local_gradle_build: NOT_RUN
  independent_reviewer_subagent: NOT_AVAILABLE
current_release_gate:
  CO-04_pr: https://github.com/D8v1d777/KAAGAZ-VAULT/pull/26
  CO-04_documentation_head_sha: 141b1bdd1e828500861672a9020c8b3edf81a128
  CO-04_ci_run: https://github.com/D8v1d777/KAAGAZ-VAULT/actions/runs/39397935999
  observed_status_when_handoff_written: IN_PROGRESS — build/unit-tests/lint step active; final manifest gate pending
scope_guard:
  - No production authentication, key lifecycle, recovery format, encryption format, manifest, dependency, or database schema changes.
  - ADR-0003 policy direction is Accepted by owner; implementation remains subject to compatibility, failure-test, CI and human security-review gates.
  - Do not create replacement keys when protected data or a migration journal already exists.
owner_approved_decision:
  - A2: strong biometric OR secure device credential only where auth-per-use CryptoObject is supported and tested.
  - B2: separate random 256-bit recovery key; versioned authenticated recovery envelope; one-time display plus storage confirmation; no server/cloud copy.
  - C1: lock on explicit lock, screen-off, app foreground loss, and process restart; no inactivity timer without separate owner approval.
verified:
  - Owner explicitly approved A2 + B2 + C1 in chat on 2026-10-20.
  - ADR-0003 now records acceptance of the policy direction; implementation remains incomplete.
  - Memo options and failure matrix are documented.
  - Android primary documentation for auth-per-use keys, CryptoObject, API level 30 auth parameters and biometric-enrollment invalidation was checked.
unverified:
  - No production implementation or Android device test has yet passed in this task.
  - AndroidX device-credential combinations are unsupported via setAllowedAuthenticators on API 28–29; API 24–29 fallback path requires precise version review and instrumented tests.
  - Dependency gate remains: AndroidX Biometric is not in the catalog; prepare and obtain the project’s required dependency approval before Gradle changes.
next_actions:
  - Prepare dependency justification for AndroidX Biometric (version, maintenance, license, privacy/network notes) and seek its required approval before adding it.
  - Start test-first recovery-envelope and session-gate work in isolated CO-01/CO-02 PRs; do not wire UI until the dependency/API compatibility matrix is confirmed.
  - Port CO-04 onto current canonical because PR #26 is based on a diverged older branch.
independent_review: NOT_AVAILABLE — no fresh-context sub-agent capability in this workflow; required human security review remains a release gate.
</handoff>
