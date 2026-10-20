# HANDOFF.md

<handoff schema="1">
updated: 2026-10-20
author: GPT-6 via connected GitHub tools
branch: agent/CO-01-auth-session-options-v3
head_sha: pending PR creation; fetch exact final head after these checkpoint writes
ci:
  prior_green_sha: 0035befa33bd736d6f0c31d561f13eade5f63625
  prior_green_run: https://github.com/D8v1d777/KAAGAZ-VAULT/actions/runs/39365463380
  prior_green_scope: stacked CO-01 candidate branch, not canonical-based PR head
  canonical_base_sha: 7b7620f537d03a4f15c18ac80a9c8fa5bf3bc489
  known_failed_base_run: https://github.com/D8v1d777/KAAGAZ-VAULT/actions/runs/39203140191
environment:
  repository_checkout: MISSING in active tool container
  java: OpenJDK 21.0.12.1
  android_sdk: MISSING; ANDROID_HOME and ANDROID_SDK_ROOT empty
  sdkmanager: MISSING
  gh_cli: MISSING
  sub_agents: NOT_AVAILABLE
task: CO-01 owner decision memo and explicit unwired boundary
state: PARTIAL
verified:
  - Uploaded archive passed unzip integrity test and contains eight agent files; root, security, review, handoff, plan template, correction orders, and implementation-quality skill were read.
  - Canonical baseline source SHA is 7b7620f537d03a4f15c18ac80a9c8fa5bf3bc489.
  - Five security class bodies were compared against the CO-01 candidate source. Only KDoc labels changed; runtime bodies are unchanged.
  - ADR-0003 remains Proposed; memo includes auth/lock options, owner checklist, failure matrix, and warns about missing GCM AAD context in the unwired VEK envelope.
  - README states the absence of an active repository/session lock.
  - The stacked candidate PR #20 CI run #328 passed on SHA 0035befa33bd736d6f0c31d561f13eade5f63625; this is not CI evidence for this canonical-based branch.
  - CO-00 CI run #326 passed on its exact SHA 255424c96e3ee1ab88eb6c41b3aec955605f7c3a. Canonical base still has the known prior red settings-sync test unless CO-00 is merged through the owner-approved gate.
unverified:
  - Exact-head CI for this canonical-based CO-01 branch is pending.
  - No Android Keystore, BiometricPrompt, migration-crash, or device instrumentation tests ran in this environment.
decisions:
  - No authenticator/recovery policy selected; ADR remains Proposed.
  - No production authentication/session wiring before explicit owner decision and separate G2/G4 gates.
  - Track missing authenticated context on VEK wrapping as prospective CO-16 / G2 before wiring.
conflicts:
  - Existing PR #20 is stacked on PR #18, but uploaded root AGENTS.md requires every order's PR target to be canonical_branch. This branch is being prepared as a clean canonical-target alternative.
open_questions_for_human:
  - Q2: choose primary authenticator, lock lifecycle, independent recovery route, and API 24–29 behavior after reviewing the separate CO-01 and CO-02 packets.
next_actions:
  - Create draft PR targeting feat/durable-vault-migration-journal and read the exact final SHA's CI.
  - Prepare CO-02 as its own canonical-based branch and PR.
  - Ask once for both P0 policy decisions only after both memos are available.
  - After accepted policy, implement security changes in the ordered slices specified by ADR-0003; do not start lower-priority work first.
independent_review: NOT_AVAILABLE — no sub-agent capability. Manual cold source comparison performed; a second reviewer is still required for security acceptance.
</handoff>
