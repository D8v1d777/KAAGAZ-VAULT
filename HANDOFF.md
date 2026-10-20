# HANDOFF.md

<handoff schema="1">
updated: 2026-10-20
author: GPT-6 via connected GitHub tools
branch: agent/CO-02-key-invalidation-recovery-v3
head_sha: pending final PR creation; refresh after checkpoint commits
ci:
  canonical_base_sha: 7b7620f537d03a4f15c18ac80a9c8fa5bf3bc489
  known_failed_base_run: https://github.com/D8v1d777/KAAGAZ-VAULT/actions/runs/39203140191
  CO00_fix_sha: 255424c96e3ee1ab88eb6c41b3aec955605f7c3a
  CO00_fix_run: https://github.com/D8v1d777/KAAGAZ-VAULT/actions/runs/39363536166
environment:
  repository_checkout: MISSING in active tool container
  java: OpenJDK 21.0.12.1
  android_sdk: MISSING; ANDROID_HOME and ANDROID_SDK_ROOT empty
  sdkmanager: MISSING
  gh_cli: MISSING
  sub_agents: NOT_AVAILABLE
task: CO-02 owner decision memo for key invalidation and recovery
state: PARTIAL
verified:
  - Read uploaded root/security agent contracts, correction-order index, review protocol, and implementation-quality skill.
  - Canonical branch base is 7b7620f537d03a4f15c18ac80a9c8fa5bf3bc489.
  - CO-02 memo is documentation only and explicitly does not authorize a key policy, envelope, KDF, dependency, backup or runtime change.
  - Memo distinguishes an independent recovery envelope from device credential as an authenticator; includes failure matrix, copy proposals and emulator/migration test requirements.
  - Separate CO-01 canonical-target draft PR #22 is open: https://github.com/D8v1d777/KAAGAZ-VAULT/pull/22.
  - CO-00 candidate exact-head CI run #326 passed on 255424c96e3ee1ab88eb6c41b3aec955605f7c3a. This does not make the canonical base green because PR #18 remains unmerged.
unverified:
  - Exact-head CI for this CO-02 canonical-based PR is pending.
  - No local Android build or Android Keystore/BiometricPrompt instrumentation test ran.
decisions:
  - No recovery method, authenticator, lock policy, key envelope, KDF or dependency selected.
  - Keep vault locking disabled until an owner-approved independent recovery path is implemented and verified.
conflicts:
  - Root AGENTS requires a separate order-specific PR targeting canonical; earlier CO-02 PR #21 is stacked on CO-01 and is not the canonical-target PR for this order.
open_questions_for_human:
  - Q2: choose the primary authenticator, lock lifecycle, independent recovery, and API 24–29 behavior after reading CO-01 and CO-02 proposals together.
next_actions:
  - Open draft PR targeting feat/durable-vault-migration-journal; inspect exact-head workflow.
  - Ask owner once for combined CO-01/CO-02 decisions after both canonical-target packets are available.
  - After acceptance, add emulator/instrumented tests with pinned workflow actions before any security wiring.
independent_review: NOT_AVAILABLE — no sub-agent capability; memo-only change still needs owner/security review.
</handoff>
