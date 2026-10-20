# HANDOFF.md

<handoff schema="1">
updated: 2026-10-20
author: GPT-6 via connected GitHub tools
branch: agent/CO-03-uuid-canonicalization-rebased
head_sha: e6ffd2eb571496a8817d45a6c1b31afefa652c5b
ci:
  last_verified_green_sha: e6ffd2eb571496a8817d45a6c1b31afefa652c5b
  last_verified_green_run: https://github.com/D8v1d777/KAAGAZ-VAULT/actions/runs/39385455774
  documentation_checkpoint_ci: PENDING
environment:
  android_sdk: MISSING
  java: OpenJDK 21.0.12.1
  gh_cli: MISSING
  sdkmanager: MISSING
  repository_checkout: MISSING
  sub_agents: NOT_AVAILABLE
task: CO-03 canonical UUID identity
state: PARTIAL
changed_files:
  - app/src/main/java/com/kaagazvault/security/EncryptedDocumentStore.kt
  - app/src/test/java/com/kaagazvault/security/EncryptedDocumentStoreTest.kt
  - docs/agent/plans/CO-03-uuid-canonicalization.md
  - docs/engineering/progress.md
  - HANDOFF.md
verified:
  - Test-first reproduction failed on SHA 27aa8be8942800ea122a9fa764ee6e5ad9d24951 in run #367 at EncryptedDocumentStoreTest.kt:132; uppercase alias read raised IOException instead of IllegalArgumentException.
  - Minimal production fix commit e6ffd2eb571496a8817d45a6c1b31afefa652c5b passed the full configured gate in run #369.
  - Other UUID parse sites in VaultMigrationState and VaultMigrationJournal already require exact canonical spelling.
  - Java SE 21 UUID API documentation was consulted: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/UUID.html.
  - Manual full review of the three-file CO-03 diff found no issues; checklist recorded in docs/agent/plans/CO-03-uuid-canonicalization.md.
unverified:
  - Documentation-complete branch head needs its own exact-SHA CI result.
  - No local Gradle build ran because Android SDK, sdkmanager, repository checkout, and gh CLI are unavailable.
  - No fresh-context reviewer sub-agent is available; independent_review is NOT_AVAILABLE.
decisions:
  - Use exact UUID string equality; do not normalize identifiers or rename stored files.
  - Do not alter key policy, crypto formats, dependencies, manifest permissions, or Room schema in CO-03.
conflicts:
  - HANDOFF.md next_actions lists CO-03/CO-04 before the P0 decision memo; root AGENTS.md <priority> says "1 open P0 orders ... 2 open P1 orders". After completing CO-03, prepare CO-01/CO-02 decision material before CO-04 production implementation.
  - The uploaded ZIP's root contract and correction orders are represented by separate draft PRs and are not yet merged into canonical_branch.
open_questions_for_human:
  - Q2: choose authenticator, lock lifecycle, and recovery policy after reviewing the CO-01/CO-02 decision memo.
next_actions:
  - Check exact-head CI for the documentation-complete CO-03 commit.
  - Prepare the Proposed CO-01/CO-02 decision memo and ask once for the required human decision; do not wire runtime authentication or recovery.
  - Resume CO-04 only after the P0 decision work is handled; keep its branch separate and preserve its test-first evidence.
independent_review: NOT_AVAILABLE
</handoff>
