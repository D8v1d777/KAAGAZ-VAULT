# CO-01 — authentication-bound session decision packet

<plan>
id: CO-01-auth-session-options
objective: Give the owner a reviewable authentication/session proposal that closes the CO-01 decision-gathering task without implementing or accepting a cryptographic policy.
non_goals: [No key-generation or key-use change, no alias/payload/envelope/journal/Room format change, no BiometricPrompt UI, no repository wiring, no dependency or permission change.]
acceptance:
  - ADR-0003 remains Proposed and contains authenticator options, session/lock choices, consequences, residual risks, and owner checklist.
  - Current auth/migration primitives are explicitly labelled SPIKE: not wired; README states repository access is not gated by an unlocked vault session.
  - Cold review covers all changed security files; exact-head CI status is recorded without hiding unrelated baseline failures.
steps:
  - id: 1 status: DONE what: Read packed AGENTS contract, HANDOFF, correction orders, REVIEW, security AGENTS, and implementation-quality skill files: [AGENTS.md, HANDOFF.md, docs/agent/CORRECTION-ORDERS.md, docs/agent/REVIEW.md, app/src/main/java/com/kaagazvault/security/AGENTS.md, .agents/skills/implementation-quality/SKILL.md] verify: archive integrity and file inspection
  - id: 2 status: DONE what: Inspect auth-bound primitive and app boundary; compare source bodies against canonical baseline files: [app/src/main/java/com/kaagazvault/security/AuthBoundVaultKey.kt, app/src/main/java/com/kaagazvault/security/VaultMigrationCoordinator.kt, app/src/main/java/com/kaagazvault/security/VaultMigrationJournal.kt, app/src/main/java/com/kaagazvault/security/VaultMigrationBackend.kt, app/src/main/java/com/kaagazvault/security/WrappedVaultKeyStore.kt] verify: function bodies unchanged; only KDoc labels changed
  - id: 3 status: DONE what: Add proposed-only choices, failure outcomes, and context-binding warning to ADR-0003 files: [docs/engineering/decisions/ADR-0003-auth-bound-vault-session.md] verify: status remains Proposed
  - id: 4 status: DONE what: Add explicit SPIKE labels and README limitation files: [five security class files, README.md] verify: KDoc labels read back and repository-session warning present
  - id: 5 status: DOING what: Add handoff, newest-first progress, and order plan; submit a draft PR directly against canonical branch files: [HANDOFF.md, docs/engineering/progress.md, docs/agent/plans/CO-01-auth-bound-session-options.md] verify: PR base exactly feat/durable-vault-migration-journal; inspect CI for final SHA
  - id: 6 status: TODO what: Ask owner once for the joint CO-01/CO-02 choices after both separate memos are reviewable files: [ADR-0003, docs/engineering/decisions/CO-02-key-invalidation-recovery.md] verify: explicit owner response; ADR acceptance remains gated
risks:
  - risk: Proposed memo is mistaken for accepted policy; detect: ADR status and PR body; mitigate: leave Proposed and no runtime wiring
  - risk: Auth-per-use GCM envelope has no AAD context binding; detect: AuthBoundVaultKey setup/unlock implementation; mitigate: track CO-16 / G2 before any wiring
  - risk: Canonical baseline has a known failing settings-sync test; detect: CI run #323; mitigate: keep CO-00 in its separate PR and disclose exact-head CO-01 CI result.
gated_actions: [G2 key policy/alias/cipher/envelope; G4 ADR status]
decision_log:
  - when: 2026-10-20 decision: Prepare options only and do not choose policy without the owner. alternatives: [Strong biometrics only, per-use biometric plus device credential where supported, independent recovery envelope] reversible: yes
rollback: Close the draft PR; no runtime behavior changes were made.
evidence:
  - Exact baseline: feat/durable-vault-migration-journal at 7b7620f537d03a4f15c18ac80a9c8fa5bf3bc489.
  - Prior stacked PR #20 CI passed run #328 on SHA 0035befa33bd736d6f0c31d561f13eade5f63625; this does not verify the canonical-base PR.
  - Local Android SDK, sdkmanager, gh CLI, and repository checkout are absent from the active container; no local build was run.
</plan>
