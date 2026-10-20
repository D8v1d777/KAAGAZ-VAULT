# HANDOFF.md

<handoff schema="1">
updated: 2026-10-20
author: GPT-6 via connected GitHub tools
branch: agent/CO-02-key-invalidation-recovery-v2
head_sha: 7df62c8350185d8018aa96434e173386a755ae2a
ci:
  prior_green_sha: 255424c96e3ee1ab88eb6c41b3aec955605f7c3a
  prior_green_run: https://github.com/D8v1d777/KAAGAZ-VAULT/actions/runs/39363536166
  current_candidate_run: https://github.com/D8v1d777/KAAGAZ-VAULT/actions/runs/39365663867
  current_candidate_has_result: false
environment:
  android_sdk: MISSING in active tool container
  java: OpenJDK 21.0.12.1
  gh_cli: MISSING
  sdkmanager: MISSING
  sub_agents: NOT_AVAILABLE
task: CO-02 Proposed key invalidation/recovery decision packet
state: PARTIAL
verified:
  - CO-00 exact-head gate passed run #326 on SHA 255424c96e3ee1ab88eb6c41b3aec955605f7c3a.
  - CO-01 packet is isolated in draft PR #20; its exact-head CI run #328 is still in progress as of last lookup.
  - CO-02 memo contains recovery alternatives, a failure matrix, proposed user-facing disclosures, instrumented-test acceptance and an owner checklist.
  - CO-02 is documentation only: no key alias, envelope format, KDF, dependency, backup rule, or runtime behavior changes.
unverified:
  - CO-02 candidate exact-head CI run #329 is still in progress on SHA 7df62c8350185d8018aa96434e173386a755ae2.
  - No Android Keystore/biometric/migration instrumentation test ran; no auth/recovery policy has been selected.
decisions:
  - Keep ADR-0003 Proposed. Device credential is not treated as an independent recovery envelope.
  - Recommend verified independent recovery before enabling lock; no old key or ciphertext may be discarded on recovery failure.
conflicts:
  - Work branches are stacked (CO-02 on CO-01, CO-01 on CO-00) to avoid reintroducing the known failing baseline; this differs from the root preference for targeting canonical_branch directly because CO-00 is still unmerged behind G1. No merge or canonical push was performed.
open_questions_for_human:
  - Q2 blocking: choose authenticator, lock lifecycle, recovery method, and API 24–29 policy after reading both decision packets.
next_actions:
  - A1: verify full CI for final CO-01 and CO-02 heads after handoff/progress commits.
  - A2: ask the owner once for the joint CO-01/CO-02 decision; do not implement gated key/session changes until approved.
  - A3: formalize CO-16 context-bound VEK envelope as its own G2 order before any wiring.
independent_review: NOT_AVAILABLE — no sub-agent capability; manual cold source review only.
</handoff>
