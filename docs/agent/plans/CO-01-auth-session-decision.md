# CO-01 — authentication-bound vault session decision

<plan>
id: CO-01-auth-session-decision
objective: Present a Proposed ADR decision memo for vault authentication, lock lifecycle, and migration gating without changing runtime behavior or accepting the ADR.
non_goals: [No runtime session/authentication wiring, no key-policy or alias changes, no cipher or persisted-format changes, no dependency or manifest changes, no key generation/deletion, no Room schema change.]
acceptance:
  - cmd: Review ADR-0003 Proposed update
    expect: Three explicit authenticator/recovery options, recommendation, failure matrix, user-facing copy, and unresolved API compatibility gates are documented.
  - cmd: Owner decision
    expect: Ask once for the authenticator and recovery choice; leave ADR status Proposed until explicit acceptance.
steps:
  - id: 1 status: DONE what: Read root contract, nested security rules, current ADR-0003, AuthBoundVaultKey, WrappedVaultKeyStore, and key provider files: [app/src/main/java/com/kaagazvault/security/AGENTS.md, docs/engineering/decisions/ADR-0003-auth-bound-vault-session.md] verify: static source read at canonical head b71606370a7a63965a7e1c280d721cb2c5fc75f2
  - id: 2 status: DONE what: Document options, recommendation, lock policy, failure matrix, and user-facing copy files: [docs/engineering/decisions/ADR-0003-auth-bound-vault-session.md] verify: Proposed memo includes three options, lock lifecycle, failure matrix, user copy, and explicit API compatibility gate; ADR status remains Proposed
  - id: 3 status: DOING what: Run documentation-complete CI and prepare the CO-02 recovery extension before asking once files: [.github/workflows/android-ci.yml, docs/engineering/decisions/ADR-0003-auth-bound-vault-session.md] verify: exact-head workflow result; do not change ADR status or runtime policy before owner decision
risks:
  - risk: A recommendation may promise an unsupported authenticator combination on API 24–29; detect: compare current Android primary docs and minSdk 24; mitigate: gate implementation on an API-level compatibility matrix and instrumented tests.
  - risk: Key invalidation can make existing ciphertext unreadable; detect: failure matrix; mitigate: preserve one-time recovery-key option and fail closed if all authorized recovery paths are lost.
gated_actions: [G4 only if the human explicitly accepts ADR-0003; G2 before any key-policy or recovery-envelope implementation]
decision_log:
  - when: 2026-10-20 decision: Keep ADR-0003 Proposed; this task prepares options only and changes no runtime policy. alternatives: [Implement first and ask later] reversible: yes
surprises: [The app minSdk is 24, while the current auth-bound key uses API 30 authenticator configuration; API compatibility must be explicitly tested before claiming supported lock behavior.]
rollback: Revert only the ADR decision memo and plan from this work branch; no runtime or persisted data changes were made.
evidence:
  - source: Android Developers BiometricPrompt auth-per-use example, https://developer.android.com/identity/sign-in/biometric-auth
  - source: Android KeyGenParameterSpec invalidation behavior, https://developer.android.com/reference/android/security/keystore/KeyGenParameterSpec.Builder
  - source: Android Keystore system, https://developer.android.com/privacy-and-security/keystore
</plan>

## Manual full review (root REVIEW.md full; D5 fallback)

Reviewer mode: cold pass of the documentation/KDoc-only diff; fresh-context reviewer sub-agent unavailable. No findings were identified.

| Item | Result | Evidence |
|---|---|---|
| F1 Diff map | PASS | ADR proposal, five KDoc markers, README disclosure, and plan/handoff/progress only; no runtime behavior changed. |
| F2 Storage | PASS | No storage writes or data paths changed. |
| F3 Cryptography | PASS | No cryptographic implementation, key alias, key policy, or persisted format changed. |
| F4 Authentication | PASS | No repository/session runtime gate implemented; the current absence is disclosed rather than hidden. |
| F5 Platform | PASS | No platform components, intents, or notifications changed. |
| F6 Network/privacy | PASS | No dependency, permission, network, or manifest change. |
| F7 Crash durability | PASS | No migration protocol behavior changed. |
| F8 Failure behavior | NOT_CHECKED | No runtime failure-path code changed; proposed failure matrix is a design requirement only. |
| F9 Performance | PASS | No runtime operation changed. |
| F10 Tests | NOT_CHECKED | No new behavior is claimed; Keystore/BiometricPrompt claims remain unverified until instrumented tests. |
| Q1–Q9 quick review | PASS | Scope limited to decision documentation and explicit SPIKE labels; no new behavior or claims of verified lock support. |
