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
  - id: 1 status: DONE what: Add the adversarial regression test before changing production code files: [app/src/test/java/com/kaagazvault/security/EncryptedDocumentStoreTest.kt] verify: exact-head CI run #367 on test-only SHA 27aa8be8942800ea122a9fa764ee6e5ad9d24951 failed the new test at EncryptedDocumentStoreTest.kt:132 with IOException instead of IllegalArgumentException; 67 tests completed, one failed
  - id: 2 status: DONE what: Apply exact canonical UUID comparison to canonical branch files: [app/src/main/java/com/kaagazvault/security/EncryptedDocumentStore.kt] verify: exact code/test SHA e6ffd2eb571496a8817d45a6c1b31afefa652c5b passed full CI run #369
  - id: 3 status: DONE what: Review the changed function and all UUID parse call sites files: [app/src/main/java/com/kaagazvault/security/EncryptedDocumentStore.kt, app/src/test/java/com/kaagazvault/security/EncryptedDocumentStoreTest.kt] verify: only EncryptedDocumentStore required a change; VaultMigrationState and VaultMigrationJournal already compare canonical spelling exactly
  - id: 4 status: DONE what: Run full CI on the code/test SHA and record exact evidence files: [.github/workflows/android-ci.yml] verify: exact-head run #369 succeeded on SHA e6ffd2eb571496a8817d45a6c1b31afefa652c5b
  - id: 5 status: DOING what: Record handoff and progress evidence files: [HANDOFF.md, docs/engineering/progress.md] verify: documentation-complete SHA must pass exact-head CI before PR is ready
risks:
  - risk: Rejecting the old permissive spelling could affect a non-canonical ID in an external caller; detect: tests and call-site review; mitigate: save() has always generated lowercase UUIDs and no files are renamed or migrated.
gated_actions: []
decision_log:
  - when: 2026-10-20 decision: Rebase the CO-03 patch onto the current canonical branch to avoid the stale HANDOFF/progress conflicts from the previous stacked branch. alternatives: [Merge the conflicted stack or keep stale base] reversible: yes
  - when: 2026-10-20 decision: Require exact equality of UUID.fromString(id).toString() with id. alternatives: [Normalize the supplied identifier] reversible: yes
surprises: [The original CO-03 PR diverged from canonical after CO-00 merged, making it non-mergeable. A clean branch based on canonical avoids unrelated HANDOFF/progress conflicts.]
rollback: Revert the CO-03 test and exact-comparison commits on this work branch; do not rewrite canonical history or rename stored documents.
evidence:
  - command: GitHub Actions run #39384857603; exit code 1; SHA: 27aa8be8942800ea122a9fa764ee6e5ad9d24951; key output: EncryptedDocumentStoreTest.rejectsUpperCaseUuidAliasForReadReplaceAndDelete FAILED at line 132 because uppercase alias read raised IOException; 67 tests completed, one failed.
  - command: GitHub Actions run #39385455774; exit code 0; SHA: e6ffd2eb571496a8817d45a6c1b31afefa652c5b; key output: full configured gate passed in run #369.
  - source: Java SE 21 UUID API, https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/UUID.html; fromString parses the standard UUID representation and toString returns its canonical representation.
</plan>

## Manual full review (root REVIEW.md full; D5 fallback)

Reviewer mode: separate cold pass after re-reading the exact diff; fresh-context reviewer sub-agent unavailable. No findings were identified.

| Item | Result | Evidence |
|---|---|---|
| F1 Diff map | PASS | Three scoped files: UUID validation, regression test, CO-03 plan; no persisted-format change. |
| F2 Storage | PASS | No new storage path or write; invalid IDs are rejected before filename use. |
| F3 Cryptography | PASS | AES-GCM, IV, AAD construction, and key lifecycle unchanged; canonical identity check only. |
| F4 Authentication | PASS | No authentication/session behavior changed. |
| F5 Platform | PASS | No exported component, intent, PendingIntent, provider, or notification changes. |
| F6 Network/privacy | PASS | No network or dependency change; exact-head offline-manifest gate passed in run #369. |
| F7 Crash durability | PASS | No multi-step file protocol changed. |
| F8 Failure behavior | PASS | Run #367 reproduced the alias bug; exact canonical equality rejects uppercase input before filesystem access. |
| F9 Performance | PASS | No new large allocation, blocking work, or unbounded loop in production code. |
| F10 Tests | PASS | Test-only SHA failed the regression; exact code/test SHA passed the full configured gate. |
| Q1–Q9 quick review | PASS | Scope-only diff, no secret logging, test buffers wiped in finally, no catch suppression changes, adversarial and positive-path assertions present. |
