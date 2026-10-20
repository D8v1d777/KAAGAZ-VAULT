# CO-01/CO-02 — authentication and recovery decision memo

<plan>
id: CO-01-02-auth-recovery-decision
objective: Produce one owner-decision memo covering authenticator policy, lock/session policy, recovery, and failure behavior without changing runtime security behavior or ADR status.
non_goals: [No production authentication wiring, key policy changes, cryptographic format changes, dependency changes, manifest changes, or acceptance of ADR-0003.]
acceptance:
  - cmd: Review ADR-0003, AuthBoundVaultKey, current migration coordinator/journal, root and security AGENTS, and Android primary docs
    expect: memo options and constraints trace to inspected code and official sources
  - cmd: Inspect proposed ADR section and PR diff
    expect: options, recommendation, failure matrix, and user-facing copy are explicit; status remains Proposed
  - cmd: Exact-head CI
    expect: documentation-only branch passes configured workflow, or failure is recorded against exact SHA
steps:
  - id: 1 status: DONE what: Read uploaded agent contract, handoff, correction orders, security scope, ADR-0003, key primitive and migration sources files: [AGENTS.md, HANDOFF.md, docs/agent/CORRECTION-ORDERS.md, app/src/main/java/com/kaagazvault/security/AGENTS.md, docs/engineering/decisions/ADR-0003-auth-bound-vault-session.md, app/src/main/java/com/kaagazvault/security/AuthBoundVaultKey.kt] verify: source retrieval and direct inspection
  - id: 2 status: DONE what: Verify Android auth-per-use and enrollment invalidation statements against official Android docs files: [docs/engineering/decisions/ADR-0003-auth-bound-vault-session.md] verify: https://developer.android.com/identity/sign-in/biometric-auth and https://developer.android.com/reference/kotlin/android/security/keystore/KeyGenParameterSpec.Builder
  - id: 3 status: DONE what: Add a status-neutral Proposed owner decision section with CO-01 and CO-02 options, recommendation, failure matrix, and copy files: [docs/engineering/decisions/ADR-0003-auth-bound-vault-session.md] verify: source diff review
  - id: 4 status: DOING what: Open draft PR to canonical branch and obtain exact-head CI files: [.github/workflows/android-ci.yml] verify: workflow result for exact head SHA
  - id: 5 status: TODO what: Ask the owner once to choose the proposed authenticator, lock, and recovery policy files: [docs/engineering/decisions/ADR-0003-auth-bound-vault-session.md] verify: explicit owner response
risks:
  - risk: Prematurely changing key policy could make existing ciphertext unrecoverable; detect: any runtime key flag, alias, format or status change in diff; mitigate: docs-only proposal, no production changes.
  - risk: Device credential compatibility differs by Android/API/library; detect: API-specific CryptoObject constraints; mitigate: require an API compatibility matrix and instrumented tests before implementation.
gated_actions: [G4 if changing ADR status; not requested or performed]
decision_log:
  - when: 2026-10-20 decision: Keep ADR-0003 Proposed and prepare the owner decision material before asking; alternatives: [Implement a key policy now, which violates CO-01/CO-02 and G2] reversible: yes
surprises: [The current AuthBoundVaultKey requires strong biometrics and explicitly invalidates on biometric enrollment; the primitive is not wired to app repositories according to the correction-order audit.]
rollback: Close the draft PR; no runtime behavior changes.
evidence:
  - command: Memo commit 19e74656524e753681286b89c3aea7edb3768539; ADR-0003 status remains Proposed.
  - command: Progress entry commit 2968e4bf353fa947bcde6362764086197bc3393e records scope and verification boundary.
  - command: GitHub Actions run #326 for PR #18 exact head 255424c96e3ee1ab88eb6c41b3aec955605f7c3a; exit 0; all listed workflow steps succeeded.
  - command: Official Android docs checked 2026-10-20; auth-per-use key API and enrollment invalidation behavior documented.
</plan>
