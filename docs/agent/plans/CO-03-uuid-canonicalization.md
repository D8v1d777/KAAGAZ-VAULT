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
  - id: 1 status: DONE what: Add the adversarial regression test before changing production code files: [app/src/test/java/com/kaagazvault/security/EncryptedDocumentStoreTest.kt] verify: prior test-first CI run #341 on SHA 0f94c179f450c74fb08f5bc855ccf2f74f54ab2c
  - id: 2 status: TODO what: Apply exact canonical UUID comparison to canonical branch files: [app/src/main/java/com/kaagazvault/security/EncryptedDocumentStore.kt] verify: exact-head CI
  - id: 3 status: TODO what: Review the changed function and all UUID parse call sites files: [app/src/main/java/com/kaagazvault/security/EncryptedDocumentStore.kt, app/src/test/java/com/kaagazvault/security/EncryptedDocumentStoreTest.kt] verify: source diff and relevant callers inspected
  - id: 4 status: TODO what: Run full CI on the final code/test SHA and record exact evidence files: [.github/workflows/android-ci.yml] verify: exact-head workflow URL
  - id: 5 status: TODO what: Record handoff and progress evidence files: [HANDOFF.md, docs/engineering/progress.md] verify: read back updated files and exact-SHA CI result
risks:
  - risk: Rejecting the old permissive spelling could affect a non-canonical ID in an external caller; detect: tests and call-site review; mitigate: save() has always generated lowercase UUIDs and no files are renamed or migrated.
gated_actions: []
decision_log:
  - when: 2026-10-20 decision: Rebase the CO-03 patch onto the current canonical branch to avoid the stale HANDOFF/progress conflicts from the previous stacked branch. alternatives: [Merge the conflicted stack or keep stale base] reversible: yes
  - when: 2026-10-20 decision: Require exact equality of UUID.fromString(id).toString() with id. alternatives: [Normalize the supplied identifier] reversible: yes
surprises: [The original CO-03 PR diverged from canonical after CO-00 merged, making it non-mergeable.]
rollback: Revert the CO-03 test and exact-comparison commits on this work branch; do not rewrite canonical history or rename stored documents.
evidence: []
</plan>
