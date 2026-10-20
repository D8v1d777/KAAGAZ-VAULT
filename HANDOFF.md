# HANDOFF.md

<handoff schema="1">
updated: 2026-10-20
author: GPT-6 via connected GitHub tools
branch: agent/CO-04-canonical-port
task: Port CO-04 unreadable-document behavior onto canonical after base-branch divergence
state: IMPLEMENTATION_PORTED — EXACT-HEAD CI PENDING
canonical_branch: feat/durable-vault-migration-journal
canonical_base_sha: 789816fece4b90bb981b9e9cb3a5954f1eebd514
port_pr: pending creation
source_pr: https://github.com/D8v1d777/KAAGAZ-VAULT/pull/26
source_sha: 141b1bdd1e828500861672a9020c8b3edf81a128
source_ci: https://github.com/D8v1d777/KAAGAZ-VAULT/actions/runs/39397935999
verification:
  source_exact_head_ci: PASSED
  canonical_port_exact_head_ci: PENDING
  local_android_build: NOT_RUN — active execution environment has no Android SDK/repository checkout
scope_guard:
  - Port only CO-04 source behavior, repository regression tests and CO-04 plan; no broad refactor.
  - No new dependencies, permissions, Room schema, ciphertext format, key lifecycle, recovery policy or unrelated redesign.
  - Do not merge without independent human review and a green full CI run on the port's exact head.
parallel_security_work:
  - CO-01 session gate PR: https://github.com/D8v1d777/KAAGAZ-VAULT/pull/37 — exact-head CI pending.
  - CO-02 recovery envelope PR: https://github.com/D8v1d777/KAAGAZ-VAULT/pull/38 — exact-head CI pending.
  - Owner approved A2 + B2 + C1; ADR-0003 accepted for policy direction, not implementation verification.
next_actions:
  - Finish progress/handoff evidence update and open port PR against canonical.
  - Wait for exact-head CI; fix only reproduced failures.
  - Obtain independent human review before merge.
  - Continue CO-01/CO-02 in isolated PRs after each exact-head CI result.
independent_review: REQUIRED — not yet recorded.
</handoff>
