# HANDOFF.md

<handoff schema="1">
updated: 2026-10-20
author: GPT-6 via connected GitHub tools
branch: agent/CO-02-key-invalidation-recovery-v3
head_sha: 83487d07c907c9c97deb73a4946e55c85ee08b2e
ci:
  canonical_base_sha: 7b7620f537d03a4f15c18ac80a9c8fa5bf3bc489
  exact_head_run: https://github.com/D8v1d777/KAAGAZ-VAULT/actions/runs/39367507199
  exact_head_result: FAILED at SettingsSyncProtocolTest.mergeKeepsLocalByDefaultAndRequiresVoiceReview; 65 tests completed, 1 failed
  python_manifest_helper: PASSED in exact-head run #335
  merged_manifest_step: SKIPPED after Gradle test failure
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
  - CO-02 memo is documentation-only; no key policy, alias, envelope, KDF, dependency, backup rule, or runtime behavior changed.
  - Memo separates device credential as an authenticator from an independent recovery envelope; includes alternatives, failure matrix, proposed copy and instrumentation/migration test gates.
  - Canonical-target PR #23 is open/draft; exact-head CI run #335 failed on the known canonical-base CO-00 settings-sync regression, not CO-02 code.
  - CO-00 candidate run #326 passed on SHA 255424c96e3ee1ab88eb6c41b3aec955605f7c3a but PR #18 remains unmerged.
unverified:
  - Exact-head CI after this checkpoint update is pending.
  - No local Android build or Android Keystore/BiometricPrompt instrumentation test ran.
decisions:
  - No recovery method, authenticator, lock policy, key envelope, KDF or dependency selected.
  - Keep vault lock disabled until an owner-approved independent recovery path is implemented and verified.
conflicts:
  - Earlier PR #21 was stacked on CO-01; canonical-target PR #23 supersedes it under the supplied one-order-per-PR rule.
open_questions_for_human:
  - Q2: choose primary authenticator A/B/C, lock lifecycle, recovery method R1/R2/R3, and API 24–29 behavior with CO-01.
  - G2: approve versioned authenticated-context envelope design before any key wiring (CO-16).
next_actions:
  - Check the new exact-head workflow after this checkpoint update and keep the inherited CO-00 failure separate.
  - Ask once for joint CO-01/CO-02 decision after reviewing both canonical-target memos.
  - After acceptance, add emulator/instrumented tests with pinned workflow actions before runtime security wiring.
independent_review: NOT_AVAILABLE — no sub-agent capability; memo-only change still requires owner/security review.
</handoff>
