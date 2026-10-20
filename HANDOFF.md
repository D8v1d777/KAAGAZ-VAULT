# HANDOFF.md

<handoff schema="1">
updated: 2026-10-20
author: GPT-6 via connected GitHub tools
branch: agent/CO-01-auth-session-decision
head_sha: 6ee8c39bf207d8e6e13865eae6343a6715197c6a
ci:
  last_verified_green_sha: b71606370a7a63965a7e1c280d721cb2c5fc75f2
  last_verified_green_run: https://github.com/D8v1d777/KAAGAZ-VAULT/actions/runs/39383558020
  current_branch_ci: PENDING
environment:
  android_sdk: MISSING
  java: OpenJDK 21.0.12.1
  gh_cli: MISSING
  sdkmanager: MISSING
  repository_checkout: MISSING
  sub_agents: NOT_AVAILABLE
task: prepare CO-01 and CO-02 owner decision memo
state: PARTIAL
changed_files:
  - docs/engineering/decisions/ADR-0003-auth-bound-vault-session.md
  - docs/agent/plans/CO-01-auth-session-decision.md
  - app/src/main/java/com/kaagazvault/security/AuthBoundVaultKey.kt
  - app/src/main/java/com/kaagazvault/security/VaultMigrationCoordinator.kt
  - app/src/main/java/com/kaagazvault/security/VaultMigrationJournal.kt
  - app/src/main/java/com/kaagazvault/security/VaultMigrationBackend.kt
  - app/src/main/java/com/kaagazvault/security/WrappedVaultKeyStore.kt
  - README.md
  - docs/engineering/progress.md
  - HANDOFF.md
verified:
  - ADR-0003 remains Proposed; the memo records options, recommendation, lock policy, failure matrix, and user-facing copy.
  - Five unwired security classes are labeled SPIKE: not wired in KDoc, and README discloses that no authentication-bound session gates repositories.
  - Android primary documentation supports auth-per-use biometric/device-credential keys in documented API configurations; API 24–29 compatibility and OEM behavior remain an explicit gate.
unverified:
  - Current branch exact-head CI is pending.
  - No local Android build ran because Android SDK, sdkmanager, repository checkout, and gh CLI are unavailable.
  - No fresh-context reviewer sub-agent is available; manual full review is recorded in docs/agent/plans/CO-01-auth-session-decision.md.
decisions:
  - Keep ADR-0003 Proposed; no key policy or runtime behavior is accepted by this change.
  - Recommend strong biometric OR device credential with a one-time recovery-key envelope only if the supported API matrix is verified.
  - Security-scope change touches SEC-12 review requirements; no cryptographic behavior rule was changed.
conflicts:
  - Root AGENTS.md priority requires open P0 orders before P1 orders; prepare the CO-01/CO-02 decision memo before CO-04 production implementation.
  - The uploaded ZIP's root contract and correction orders are represented by separate draft PRs and are not yet merged into canonical_branch.
open_questions_for_human:
  - Q2: choose authenticator, recovery path, and lock lifecycle after reviewing the combined CO-01/CO-02 decision memo.
next_actions:
  - Finish CO-01 exact-head CI and open its draft PR.
  - Prepare the separate CO-02 recovery extension on its own branch/PR.
  - Ask the owner once for the combined decision; do not wire runtime auth/session or recovery before approval.
independent_review: NOT_AVAILABLE
</handoff>
