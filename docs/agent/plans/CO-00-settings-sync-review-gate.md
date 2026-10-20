# CO-00 — repair exact-head Android CI failure

<plan>
id: CO-00-settings-sync-review-gate
objective: Fix the confirmed SettingsSyncMerger regression and obtain a passing Android CI gate on the final SHA.
non_goals: [No cryptographic-format changes, key-policy changes, dependency changes, manifest permissions, Room schema changes, or unrelated settings-sync changes.]
acceptance:
  - cmd: GitHub Actions on the final branch head
    expect: workflow succeeds on that exact SHA, including Python policy tests, debug/release build, unit tests, lint, and merged-manifest gate
  - cmd: inspect testDebugUnitTest failure on source SHA 7b7620f537d03a4f15c18ac80a9c8fa5bf3bc489
    expect: recorded reproduction is SettingsSyncProtocolTest.mergeKeepsLocalByDefaultAndRequiresVoiceReview at assertion line 105
steps:
  - id: 1 status: DONE what: Read correction orders, source, failing test, and exact-SHA CI logs files: [app/src/main/java/com/kaagazvault/sync/SettingsSyncProtocol.kt, app/src/test/java/com/kaagazvault/sync/SettingsSyncProtocolTest.kt] verify: GitHub Actions run #39203140191 log
  - id: 2 status: DOING what: Apply narrow merge-policy fix so voice-dependent guidance stays disabled while remote voice enablement awaits review files: [app/src/main/java/com/kaagazvault/sync/SettingsSyncProtocol.kt] verify: focused unit test green in CI
  - id: 3 status: TODO what: Open draft PR to feat/durable-vault-migration-journal and inspect exact-head workflow files: [.github/workflows/android-ci.yml] verify: workflow succeeds on final SHA
  - id: 4 status: TODO what: Update HANDOFF, newest-first progress log, and README evidence after exact-SHA CI outcome files: [HANDOFF.md, docs/engineering/progress.md, README.md] verify: README cites a SHA with recorded green run URL
risks:
  - risk: Voice settings share a review boundary; detect: current merge-policy assertions and independent review; mitigate: preserve KEEP_LOCAL and safe-off precedence, avoid format changes
gated_actions: []
decision_log:
  - when: 2026-10-20 decision: Treat the existing exact-SHA CI failure as the W3 reproduction; do not create a duplicate failing run. alternatives: [Test-only commit followed by a second failing CI run] reversible: yes
surprises: [HANDOFF said head_has_result=false, but run #39203140191 is a failed result for the recorded SHA.]
rollback: Revert the merge-policy change on this work branch; do not rewrite canonical history.
evidence:
  - command: GitHub Actions run #39203140191; exit code 1 at :app:testDebugUnitTest; key output: SettingsSyncProtocolTest > mergeKeepsLocalByDefaultAndRequiresVoiceReview FAILED on SHA 7b7620f537d03a4f15c18ac80a9c8fa5bf3bc489
</plan>
