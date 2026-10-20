# HANDOFF.md

<handoff schema="1">
updated: 2026-10-20
author: GPT-6 via connected GitHub tools
branch: agent/CO-02-key-recovery-decision
head_sha: b69d47d008c556e921b9d1ecdd8df76121186084
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
task: CO-02 key invalidation and recovery decision
state: PARTIAL
changed_files:
  - docs/agent/plans/CO-02-key-recovery-decision.md
  - docs/engineering/decisions/ADR-0003-auth-bound-vault-session.md
  - docs/engineering/progress.md
  - HANDOFF.md
verified:
  - ADR-0003 remains Proposed and now includes the CO-02 recovery-key envelope candidate, loss scenarios, user copy, and post-acceptance instrumented test matrix.
  - No runtime recovery, androidTest source set, emulator workflow, key policy, cipher, KDF, alias, persisted format, dependency, manifest permission, or Room schema changed.
  - Android primary documentation was reviewed for auth-per-use and biometric-enrollment invalidation behavior.
unverified:
  - Current branch exact-head CI is pending.
  - No local Android build ran because Android SDK, sdkmanager, repository checkout, and gh CLI are unavailable.
  - No fresh-context reviewer sub-agent is available; manual review is recorded in docs/agent/plans/CO-02-key-recovery-decision.md.
decisions:
  - Keep all recovery and authenticator choices Proposed until explicit owner approval.
  - Do not implement the recovery envelope or change key invalidation policy before G2 approval.
conflicts:
  - Root AGENTS.md priority places P0 CO-01/CO-02 decision work ahead of P1 CO-04 production implementation.
  - CO-01 and CO-02 are separate orders/PRs; the ADR is shared and both branches remain Proposed.
open_questions_for_human:
  - Q2: choose authenticator, recovery path, and lock lifecycle after reviewing the combined CO-01/CO-02 decision packet.
next_actions:
  - Run exact-head CI on the CO-02 decision packet.
  - Prepare the owner decision request with both PRs and the concrete options; ask once.
  - Do not wire authentication, migration, or recovery until the owner explicitly accepts ADR-0003.
independent_review: NOT_AVAILABLE
</handoff>
