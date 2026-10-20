# CO-01 — authentication-bound session decision packet

<plan>
id: CO-01-auth-bound-session-options
objective: Give the owner a reviewable authentication/session proposal that closes the CO-01 decision gate without implementing or accepting a cryptographic policy.
non_goals: [No key-generation or key-use change, no alias/payload/envelope/journal/Room format change, no BiometricPrompt UI, no repository wiring, no dependency or permission change.]
acceptance:
  - ADR-0003 remains Proposed and contains three authenticator options, session/lock choices, consequences, residual risks, and a decision checklist.
  - Current auth/migration primitives are explicitly labelled SPIKE: not wired in source comments and README; no claim that the running app is locked.
  - Cold review covers every changed security file; full exact-head Android CI result recorded.
steps:
  - id: 1 status: DONE what: Read packed correction order, ADR-0003, security classes, main activity wiring, and current Android primary docs files: [docs/engineering/decisions/ADR-0003-auth-bound-vault-session.md, app/src/main/java/com/kaagazvault/security/AuthBoundVaultKey.kt] verify: source and official Android documentation inspected
  - id: 2 status: DONE what: Add proposed decision section with authenticator, session/lock, and consequences; keep ADR status Proposed files: [docs/engineering/decisions/ADR-0003-auth-bound-vault-session.md] verify: no implementation decision accepted
  - id: 3 status: DONE what: Mark unwired primitives as SPIKE and make README security boundary explicit files: [app/src/main/java/com/kaagazvault/security/AuthBoundVaultKey.kt, app/src/main/java/com/kaagazvault/security/VaultMigrationCoordinator.kt, app/src/main/java/com/kaagazvault/security/VaultMigrationJournal.kt, app/src/main/java/com/kaagazvault/security/VaultMigrationBackend.kt, app/src/main/java/com/kaagazvault/security/WrappedVaultKeyStore.kt, README.md] verify: every identified unwired class has KDoc warning
  - id: 4 status: DOING what: Cold review security diff, update handoff/progress, and obtain exact-head CI files: [HANDOFF.md, docs/engineering/progress.md] verify: CI success on exact final head; independent reviewer availability recorded honestly
risks:
  - risk: An ADR could be misread as approval; detect: status/header and PR text; mitigate: leave Proposed and do not implement session/key wiring
  - risk: Device credential compatibility differs by API; detect: official API docs; mitigate: explicit API 24–29 fallback gate and instrumented tests are required
  - risk: Biometric enrollment/lock-screen changes can irreversibly invalidate keys; detect: KeyGenParameterSpec documentation; mitigate: owner-approved recovery envelope before enabling vault lock
  - risk: The existing VEK-wrap spike does not supply authenticated additional data; detect: AuthBoundVaultKey.kt:59 and :93; mitigate: define a versioned, context-bound envelope and swap/replay tests as a separate G2 order before wiring
gated_actions: [G2 key policy/alias/cipher/envelope, G4 ADR status]
decision_log:
  - when: 2026-10-20 decision: Prepare choices and recommendation only; no policy selection without the owner. alternatives: [Strong biometrics only, per-use biometric plus device credential where supported, separate passphrase/recovery envelope] reversible: yes
rollback: Revert only proposed documentation and KDoc warning changes; no runtime behavior changes are intended.
evidence:
  - source: https://developer.android.com/identity/sign-in/biometric-auth
  - source: https://developer.android.com/reference/android/security/keystore/KeyGenParameterSpec.Builder
  - source: https://developer.android.com/reference/android/hardware/biometrics/BiometricPrompt.CryptoObject
</plan>
