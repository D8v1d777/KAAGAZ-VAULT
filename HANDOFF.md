# HANDOFF.md

<handoff schema="1">
updated: 2026-10-20
author: GPT-6 via connected GitHub tools
branch: agent/CO-01-biometric-dependency
task: CO-01 — implement owner-approved AndroidX Biometric dependency pin
state: DRAFT_PR_OPEN — VERIFY_LATEST_EXACT_HEAD_CI_AND_KEEP_UNMERGED
canonical_branch: feat/durable-vault-migration-journal
canonical_base_sha: 789816fece4b90bb981b9e9cb3a5954f1eebd514
pull_request: https://github.com/D8v1d777/KAAGAZ-VAULT/pull/41
decision_record_pr: https://github.com/D8v1d777/KAAGAZ-VAULT/pull/40
dependency:
  coordinate: androidx.biometric:biometric:1.1.0
  owner_approval: option 1 explicitly approved on 2026-10-20; recorded in PR #40
  official_release_reference: https://developer.android.com/jetpack/androidx/releases/biometric
environment:
  java_version: OpenJDK 21.0.12.1
  android_home: empty
  android_sdk_root: empty
  local_properties: absent
  repository_checkout_in_active_execution_environment: absent
  gh_cli: unavailable
  sdkmanager: unavailable
  local_test_build: NOT_RUN
verified:
  - Existing exact-head Android CI runs passed for PR #37 SHA d035e4a00b4637b10c094016ae3597ab9699ef49, run 39408883628.
  - Existing exact-head Android CI runs passed for PR #38 SHA f4f53e92a9b3daea79251ab422f67d94fa8c23d7, run 39408956479.
  - Existing exact-head Android CI runs passed for PR #39 SHA bc3837036d02d73b590d2f171207693f2cc5ba75, run 39409066230.
  - Gradle version catalog contains the exact approved biometric version and alias; app Gradle declares the alias.
  - Python regression test was committed before the Gradle configuration commits.
unverified:
  - Do not use prior heads' CI for PR #41. Use its latest GitHub Actions run only after confirming the run SHA equals the current PR head SHA.
  - The updated PR #40 documentation head also needs its own exact-head CI result.
  - No local build or test was possible in this environment.
  - No independent reviewer sub-agent capability is available here; a cold manual review is required and independent human review remains mandatory.
  - The canonical base has no gradle/verification-metadata.xml. CO-06 dependency verification remains OPEN; this PR does not claim checksums are enforced.
  - AndroidX dependency presence does not prove auth-per-use CryptoObject success or any specific device-credential fallback. API 24–36 compatibility and instrumentation tests remain required.
scope_guard:
  - No BiometricPrompt UI or production authentication path was wired.
  - No key, ciphertext, envelope, journal, manifest permission, repository, plugin, or Room schema was changed.
  - Do not merge this PR until exact-head CI, dependency-verification decision, and human review gates are satisfied.
next_actions:
  - Inspect the latest CI run for PR #41 and confirm it belongs to the latest PR head; inspect failed job steps/logs if needed.
  - Inspect the latest CI run for updated PR #40.
  - Resolve or explicitly gate CO-06 dependency verification before treating the dependency as merge-ready.
  - Continue CO-01/CO-02 test-first: migration backend crash-point tests, session integration design, supported API/authenticator matrix, instrumented CryptoObject tests, recovery persistence and one-time setup/restore flow.
  - Do not wire production data access/UI to incomplete primitives; keep legacy ciphertext/key material until authenticated read-back, SQLCipher integrity and durable journal completion.
independent_review: NOT_AVAILABLE — repository review protocol file was not found at docs/agent/REVIEW.md on the canonical ref; perform a cold review from the available contract, then require independent human review.
</handoff>
