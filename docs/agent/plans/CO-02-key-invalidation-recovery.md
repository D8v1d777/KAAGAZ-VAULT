# CO-02 — key invalidation and recovery decision packet

<plan>
id: CO-02-key-invalidation-recovery
objective: Obtain an owner-reviewed recovery policy that prevents biometric enrollment or Keystore loss from silently making a vault unrecoverable.
non_goals: [No key policy, alias, envelope, payload, journal, KDF, dependency, backup rule, or biometric implementation change before owner approval.]
acceptance:
  - A Proposed-only recovery memo enumerates biometric enrollment, no-strong-biometric, lock-screen reset, alias loss, wrong/lost recovery secret, process-death, and corrupted-ciphertext outcomes.
  - The memo differentiates device credential as an authenticator from an independent recovery envelope.
  - A test plan includes emulator/instrumentation evidence and exact-SHA CI gates.
  - Owner decision requested once together with CO-01.
steps:
  - id: 1 status: DONE what: Inspect AuthBoundVaultKey, WrappedVaultKeyStore, migration coordinator, ADR-0003, and official Android auth docs files: [app/src/main/java/com/kaagazvault/security/AuthBoundVaultKey.kt, app/src/main/java/com/kaagazvault/security/WrappedVaultKeyStore.kt] verify: current invalidation configuration and API limitations documented from primary source
  - id: 2 status: DOING what: Produce recovery alternatives, failure matrix, recommendation, and user-facing disclosure files: [docs/engineering/decisions/CO-02-key-invalidation-recovery.md] verify: all options stay Proposed and gated
  - id: 3 status: TODO what: Open a separate draft PR stacked on CO-01 and get exact-head CI files: [docs/agent/plans/CO-02-key-invalidation-recovery.md] verify: full configured workflow succeeds on exact head
  - id: 4 status: TODO what: Ask once for the joint CO-01/CO-02 decision after both memos are reviewable files: [docs/engineering/decisions/ADR-0003-auth-bound-vault-session.md, docs/engineering/decisions/CO-02-key-invalidation-recovery.md] verify: do not implement before acceptance
risks:
  - risk: A weak or lost recovery factor can cause permanent lockout; detect: failure matrix; mitigate: require setup-time verification and explicit disclosure before enabling lock
  - risk: A device credential can be confused with independent recovery; detect: review authenticator and recovery paths separately; mitigate: require an envelope independent of the device Keystore when that is the approved recovery policy
  - risk: New KDF or key-envelope change is gated; detect: ADR and CO policies; mitigate: no runtime changes in this task
gated_actions: [G2 key/envelope/KDF policy, G3 dependency additions, G4 ADR acceptance]
decision_log:
  - when: 2026-10-20 decision: Prepare a concrete decision packet without choosing the recovery secret format. alternatives: [One-time random recovery key, separate passphrase-wrapped VEK envelope, device-credential-capable per-use key plus independent fallback] reversible: yes
rollback: The memo can be revised or withdrawn; no runtime behavior changes are intended.
evidence:
  - source: https://developer.android.com/identity/sign-in/biometric-auth
  - source: https://developer.android.com/reference/android/security/keystore/KeyGenParameterSpec.Builder
</plan>
