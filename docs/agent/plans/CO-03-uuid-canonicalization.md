# CO-03 — canonical UUID identity

<plan>
id: CO-03-uuid-canonicalization
objective: Reject non-canonical UUID spellings before a document ID is used for a filename or authenticated-data context.
non_goals: [No filename migration, envelope/AAD format change, key policy change, dependency change, manifest permission change, Room schema change, or unrelated refactor.]
acceptance:
  - cmd: GitHub Actions workflow on the exact code/test SHA
    expect: Python policy tests, debug/release builds, unit tests, lint, and merged-manifest gate all pass
  - cmd: EncryptedDocumentStoreTest.rejectsUpperCaseUuidAliasForReadReplaceAndDelete
    expect: upper-case aliases throw IllegalArgumentException for read, replace, and delete; canonical save/read remains unchanged
steps:
  - id: 1 status: DONE what: Add the adversarial regression test before production edit files: [app/src/test/java/com/kaagazvault/security/EncryptedDocumentStoreTest.kt] verify: CI run #341 on test-only SHA 0f94c179f450c74fb08f5bc855ccf2f74f54ab2c
  - id: 2 status: DONE what: Replace the lowercase-normalized check with exact canonical UUID comparison files: [app/src/main/java/com/kaagazvault/security/EncryptedDocumentStore.kt] verify: exact-head CI run #344
  - id: 3 status: DONE what: Self-review the two-file diff and other UUID parsing sites files: [app/src/main/java/com/kaagazvault/security/EncryptedDocumentStore.kt, app/src/test/java/com/kaagazvault/security/EncryptedDocumentStoreTest.kt] verify: source diff and relevant callers inspected
  - id: 4 status: DONE what: Run full CI on final code/test SHA and record exact evidence files: [.github/workflows/android-ci.yml] verify: https://github.com/D8v1d777/KAAGAZ-VAULT/actions/runs/39375095096
  - id: 5 status: DOING what: Record handoff and progress without changing the production diff files: [HANDOFF.md, docs/engineering/progress.md] verify: read back updated files and check latest documentation-head workflow
risks:
  - risk: Rejecting the old permissive spelling could affect a non-canonical ID in an external caller; detect: tests and call-site review; mitigate: save() has always generated lowercase UUIDs and no files are renamed or migrated.
gated_actions: []
decision_log:
  - when: 2026-10-20 decision: Keep CO-03 isolated from the known CO-00 baseline regression by stacking the verification PR on the CO-00 branch. alternatives: [Mix the CO-00 fix into this correction PR, or accept unrelated red CI] reversible: yes
  - when: 2026-10-20 decision: Require exact equality of UUID.fromString(id).toString() with id. alternatives: [Normalize the supplied identifier] reversible: yes
surprises: [The first canonical-base CI reproduction showed the CO-03 regression and the unrelated CO-00 unit-test failure together; a stacked verification branch isolated the CO-03 result.]
rollback: Revert the CO-03 test and exact-comparison commits on this work branch; do not rewrite canonical history or rename stored documents.
evidence:
  - command: GitHub Actions run #341 on test-only SHA 0f94c179f450c74fb08f5bc855ccf2f74f54ab2c; exit code 1; key output: EncryptedDocumentStoreTest.rejectsUpperCaseUuidAliasForReadReplaceAndDelete failed at line 123 with IOException where IllegalArgumentException was expected; run also hit CO-00 baseline failure.
  - command: GitHub Actions full gate; exit code 0; SHA: 79aa3a717d7825e536bba484b95e57486b535671; URL: https://github.com/D8v1d777/KAAGAZ-VAULT/actions/runs/39375095096; build/test/lint and offline merged-manifest gate passed.
</plan>
