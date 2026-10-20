# CO-02 — key invalidation and recovery decision packet

<plan>
id: CO-02-key-invalidation-recovery
objective: Obtain an owner-reviewed recovery policy that prevents biometric enrollment or Keystore loss from silently making a vault unrecoverable.
non_goals: [No key policy, alias, envelope, payload, journal, KDF, dependency, backup rule, or biometric implementation change before owner approval.]
acceptance:
  - A Proposed-only recovery memo enumerates biometric enrollment, no-strong-biometric, lock-screen reset, alias loss, wrong/lost recovery secret, process-death, and corrupted-ciphertext outcomes.
  - The memo differentiates device credential as an authenticator from an independent recovery envelope.
  - A test plan includes emulator/instrumentation evidence and exact-SHA CI gates.
  - Owner decision requested once together with CO-01 after both separate packets exist.
steps:
  - id: 1 status: DONE what: Inspect AuthBoundVaultKey, WrappedVaultKeyStore, migration coordinator, ADR-0003, and primary Android auth docs files: [app/src/main/java/com/kaagazvault/security/AuthBoundVaultKey.kt, app/src/main/java/com/kaagazvault/security/WrappedVaultKeyStore.kt] verify: current invalidation configuration and API limits documented from official sources
  - id: 2 status: DONE what: Produce recovery alternatives, failure matrix, recommendation, and user-facing disclosure files: [docs/engineering/decisions/CO-02-key-invalidation-recovery.md] verify: all options remain Proposed and gated
  - id: 3 status: DOING what: Open a separate draft PR targeting canonical and inspect exact-head CI files: [docs/agent/plans/CO-02-key-invalidation-recovery.md] verify: workflow result recorded for exact head; disclose any inherited CO-00 failure
  - id: 4 status: TODO what: Ask once for the joint CO-01/CO-02 decision files: [docs/engineering/decisions/ADR-0003-auth-bound-vault-session.md, docs/engineering/decisions/CO-02-key-invalidation-recovery.md] verify: explicit owner response; no implementation before acceptance
risks:
  - risk: A weak or lost recovery factor can cause permanent lockout; detect: failure matrix; mitigate: require setup-time verification and explicit disclosure before enabling lock
  - risk: Device credential can be mistaken for independent recovery; detect: review authenticator/recovery paths separately; mitigate: require independent envelope if approved policy demands it
  - risk: New KDF or envelope changes are gated; detect: ADR and CO policies; mitigate: no runtime changes in this task
gated_actions: [G2 key/envelope/KDF policy, G3 dependency additions, G4 ADR acceptance]
decision_log:
  - when: 2026-10-20 decision: Prepare a concrete decision packet without choosing a recovery secret format. alternatives: [One-time random recovery key, separate passphrase-wrapped VEK envelope, device-credential-capable per-use key plus independent fallback] reversible: yes
rollback: Close the draft PR; no runtime behavior changes are intended.
evidence:
  - source: https://developer.android.com/identity/sign-in/biometric-auth
  - source: https://developer.android.com/reference/android/security/keystore/KeyGenParameterSpec.Builder
</plan>
