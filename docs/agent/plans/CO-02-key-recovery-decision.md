# CO-02 — key invalidation and recovery decision

<plan>
id: CO-02-key-recovery-decision
objective: Extend the Proposed ADR decision packet with a concrete recovery-envelope design, enrollment-change/no-biometric failure cases, user copy, and instrumented-test plan without implementing key policy.
non_goals: [No androidTest source-set changes, emulator workflow, runtime key recovery, key alias changes, KDF, cipher, persisted envelope change, dependency, manifest permission, or Room schema change before owner acceptance.]
acceptance:
  - cmd: Review the CO-02 extension to ADR-0003
    expect: Recovery key lifecycle, failure matrix, user-facing copy, and required instrumented tests are explicit; ADR remains Proposed.
  - cmd: Owner decision
    expect: Ask once for the combined CO-01/CO-02 authenticator and recovery decision; no implementation before acceptance.
steps:
  - id: 1 status: DONE what: Read CO-02 order, ADR-0003, AuthBoundVaultKey, WrappedVaultKeyStore, and Android primary documentation files: [docs/engineering/decisions/ADR-0003-auth-bound-vault-session.md, app/src/main/java/com/kaagazvault/security/AuthBoundVaultKey.kt] verify: source and official Android API references reviewed
  - id: 2 status: DONE what: Extend the Proposed ADR with recovery design, enrollment-change/no-biometric cases, and instrumented test plan files: [docs/engineering/decisions/ADR-0003-auth-bound-vault-session.md] verify: recovery-key envelope candidate, failure matrix, user copy, and post-acceptance instrumented test matrix are documented; no implementation
  - id: 3 status: DOING what: Record handoff/progress and run exact-head CI; ask owner once after both decision packets are ready files: [HANDOFF.md, docs/engineering/progress.md] verify: exact-head workflow result; ADR remains Proposed pending owner decision
risks:
  - risk: Recovery key format or envelope design could become an accidental accepted policy; detect: memo text review; mitigate: mark all format/key choices Proposed and G2-gated.
  - risk: Emulator cannot simulate biometric enrollment changes reliably; detect: instrumented test capability check; mitigate: report UNSUPPORTED/UNVERIFIED rather than passing by assumption.
gated_actions: [G2 before implementing any recovery envelope/key policy; G4 before accepting ADR-0003]
decision_log:
  - when: 2026-10-20 decision: Keep recovery design Proposed and do not add androidTest or CI changes before owner approval. alternatives: [Implement recovery path before policy decision] reversible: yes
surprises: [AuthBoundVaultKey explicitly requests biometric-enrollment invalidation, so recovery must cover alias invalidation unless the accepted policy deliberately changes this behavior.]
rollback: Revert only the CO-02 ADR extension and plan from this branch; no runtime or persisted data changes.
evidence:
  - source: Android KeyGenParameterSpec invalidation behavior, https://developer.android.com/reference/android/security/keystore/KeyGenParameterSpec.Builder
  - source: Android BiometricPrompt auth-per-use keys, https://developer.android.com/identity/sign-in/biometric-auth
</plan>

## Manual full review (root REVIEW.md full; D5 fallback)

Reviewer mode: cold pass of the ADR/plan-only diff; fresh-context reviewer sub-agent unavailable. No findings were identified.

| Item | Result | Evidence |
|---|---|---|
| F1 Diff map | PASS | ADR-0003 and CO-02 plan only; no production code or persisted format changed. |
| F2 Storage | PASS | Recovery envelope is a proposal only; no storage behavior changed. |
| F3 Cryptography | NOT_CHECKED | Envelope/key details are explicitly Proposed and G2-gated; no cryptographic implementation changed. |
| F4 Authentication | NOT_CHECKED | No runtime lock/session behavior implemented. |
| F5 Platform | PASS | No manifest, exported component, or platform behavior changed. |
| F6 Network/privacy | PASS | No dependency, permission, or network change; recovery is explicitly local-only. |
| F7 Crash durability | NOT_CHECKED | Crash behavior is specified as acceptance criteria but no protocol is implemented. |
| F8 Failure behavior | NOT_CHECKED | Failure matrix is design intent, not runtime evidence. |
| F9 Performance | PASS | No runtime operation changed. |
| F10 Tests | NOT_CHECKED | Instrumented tests are deferred until owner accepts the policy. |
| Q1–Q9 quick review | PASS | Scope is design-only; no unsupported security/verification claim is made. |
