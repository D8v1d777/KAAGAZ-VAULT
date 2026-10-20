# HANDOFF.md

<handoff schema="1">
updated: 2026-10-20
author: GPT-6 via connected GitHub tools
branch: agent/CO-01-02-owner-security-decision
task: CO-01 / CO-02 — owner security decision memo
state: DECISION_MEMO_PREPARED — OWNER DECISION REQUIRED
memo: docs/engineering/decisions/CO-01-02-owner-security-decision-memo.md
memo_commit: f10b27271eb3507797ffd3102c80f8f52f17f4ff
implementation_authorized: false
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
  - Do not set ADR-0003 to Accepted until the owner chooses policy and the compatibility/failure-test plan is reviewed.
  - Do not create replacement keys when protected data or a migration journal already exists.
decision_requested:
  - Authenticator policy: biometric only, biometric OR device credential, or a separately passphrase-wrapped envelope.
  - Recovery policy: none, independent one-time random recovery key, passphrase-wrapped recovery envelope, or both.
  - Lock policy: immediate foreground-loss lock or an explicitly selected grace period; explicit lock and screen-off always lock.
verified:
  - Memo options and failure matrix are documented.
  - Android primary documentation for auth-per-use keys, CryptoObject, API level 30 auth parameters and biometric-enrollment invalidation was checked.
unverified:
  - No implementation or Android build was run in this decision-only task.
  - API compatibility matrix and Android Keystore behavior require instrumented CI/device tests after policy approval.
next_actions:
  - Ask the owner once for the explicit decisions listed in the memo.
  - Keep ADR-0003 Proposed and production security primitives unwired until policy is accepted.
  - After approval, implement CO-01 and CO-02 in their bounded order with separate implementation PRs and exact-head CI.
independent_review: NOT_AVAILABLE — no fresh-context sub-agent capability in this workflow; required human security review remains a release gate.
</handoff>
