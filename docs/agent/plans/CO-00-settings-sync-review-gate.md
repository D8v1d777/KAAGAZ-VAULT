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
  - id: 2 status: DONE what: Apply narrow merge-policy fix so voice-dependent guidance stays disabled while remote voice enablement awaits review files: [app/src/main/java/com/kaagazvault/sync/SettingsSyncProtocol.kt] verify: full configured CI passed on canonical head SHA b71606370a7a63965a7e1c280d721cb2c5fc75f2 in run #364
  - id: 3 status: DONE what: Merge the correction PR into feat/durable-vault-migration-journal and inspect exact-head workflow files: [.github/workflows/android-ci.yml] verify: canonical head SHA b71606370a7a63965a7e1c280d721cb2c5fc75f2 passed run #364
  - id: 4 status: DOING what: Update HANDOFF, newest-first progress log, and README evidence after exact-SHA CI outcome files: [HANDOFF.md, docs/engineering/progress.md, README.md] verify: README cites the exact canonical head SHA and run #364; follow-up documentation PR CI pending
risks:
  - risk: Voice settings share a review boundary; detect: current merge-policy assertions and independent review; mitigate: preserve KEEP_LOCAL and safe-off precedence, avoid format changes
gated_actions: []
decision_log:
  - when: 2026-10-20 decision: Treat the existing exact-SHA CI failure as the W3 reproduction; do not create a duplicate failing run. alternatives: [Test-only commit followed by a second failing CI run] reversible: yes
surprises: [HANDOFF said head_has_result=false, but run #39203140191 is a failed result for the recorded SHA.]
rollback: Revert the merge-policy change on this work branch; do not rewrite canonical history.
evidence:
  - command: GitHub Actions run #39203140191; exit code 1 at :app:testDebugUnitTest; key output: SettingsSyncProtocolTest > mergeKeepsLocalByDefaultAndRequiresVoiceReview FAILED on SHA 7b7620f537d03a4f15c18ac80a9c8fa5bf3bc489
  - command: GitHub Actions run #39383558020; exit code 0; key output: full configured gate passed on canonical head SHA b71606370a7a63965a7e1c280d721cb2c5fc75f2
  - command: README evidence follow-up; commit 566423bd2a2a52d170179ed8d5d991beff9aa1c5; exact-head CI for this documentation commit pending
</plan>
