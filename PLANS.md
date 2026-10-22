# PLANS.md — KAAGAZ VAULT Release Gates

Updated: 2026-10-22 (Asia/Kolkata)
Canonical branch: `feat/durable-vault-migration-journal` — do not modify or merge into it.
Current validation branch: `agent/integration-security-hardening-candidate-2026-10-21`
PR #48: https://github.com/D8v1d777/KAAGAZ-VAULT/pull/48
PR #49: https://github.com/D8v1d777/KAAGAZ-VAULT/pull/49

## Current verified evidence
- PR #48 recovery-alias cleanup commit `621eca27ed3f41a3bc5aa7ac84901af93cd734bc` passed exact-head Android CI run #522: https://github.com/D8v1d777/KAAGAZ-VAULT/actions/runs/39724232188
- PR #49 build-integrity head `7b41ffe9c4d845dacf6a945c4ad167e67d40449b` passed exact-head Android CI run #521: https://github.com/D8v1d777/KAAGAZ-VAULT/actions/runs/39723726808
- PR #48 validated source `bbea1eb7a697d89c06f27fbc725dbfa175ee50e5` passed both standard and emulator jobs in run #565: https://github.com/D8v1d777/KAAGAZ-VAULT/actions/runs/39758879722. Eleven emulator tests completed; standard gate verified dependency hashes, `assembleDebugAndroidTest`, and `prepareOcrModels UP-TO-DATE`.
- PR #49 metadata baseline `b2ad797238ed89759edefd04348a97b5f68c44b2` passed run #561: https://github.com/D8v1d777/KAAGAZ-VAULT/actions/runs/39755053774. Its reviewed metadata is integrated into PR #48 along with six candidate-specific components and one SavedState AAR hash; the combined candidate's run #565 passed.
- CI verifies JVM tests, debug/release builds, lint, source-policy tests and merged-manifest offline policy. It does not establish device lifecycle or real SQLCipher migration behavior.
- Local Android SDK/repository checkout is unavailable in this execution session. Use exact-head GitHub Actions runs for compile/test evidence.

## Ordered objectives
1. Exact-head check after each code change. Never transfer a green result between branches or SHAs.
2. CO-02 P0 — Android instrumentation gate. Add androidTest coverage for auth-per-use Android Keystore operations, cancellation/late-callback behavior, lock/background/process restart, and alias loss with existing ciphertext where the emulator supports it.
3. SQLCipher migration proof. On a disposable synthetic database, rewrap the existing DB key under the authenticated VEK, close/reopen SQLCipher, verify pre-existing row(s), run `PRAGMA integrity_check`, and verify legacy wrapper/ciphertext remain until the journal reaches durable COMPLETE. Current JVM crash-point tests use a fake database collaborator and are not equivalent.
4. Lifecycle instrumentation. Exercise foreground loss, screen-off/backgrounding, Activity recreation, process death/restart and picker return. Assert protected home is unavailable until the authenticated Ready state.
5. Independent cold review. Review auth/key lifecycle, recovery failure behavior, migration durability, logging, and dependency/manifest changes. Human security review remains required.
6. Release decision. Record verified/blocked/unverified items with exact SHA/run URLs; keep PRs draft/unmerged if P0/device/security gates remain open.

## Instrumentation dependency/action gate — owner approval received 2026-10-22
- Approved test-only dependencies: AndroidX Test core 1.7.0, runner 1.7.0, and ext.junit 1.3.0. Official stable versions: https://developer.android.com/jetpack/androidx/releases/test (AndroidX Test project uses Apache-2.0).
- Approved emulator action: `ReactiveCircus/android-emulator-runner@a421e43855164a8197daf9d8d40fe71c6996bb0d` (v2.38.0, immutable commit pin; release: https://github.com/ReactiveCircus/android-emulator-runner/releases/tag/v2.38.0; Apache-2.0).
- Implementation adds `AndroidJUnitRunner`, a separate API 36 Google APIs x86_64 emulator job, and two Android Keystore instrumentation checks. The action only provisions a test emulator; CI network activity is limited to normal SDK/Maven/model/build downloads. The app manifest/offline policy remains unchanged.
- PR #48 exact-head run #559 passed the standard job and 11 emulator tests. PR #49 dependency-verification run #561 is pending; do not count it green until it completes.

## CO-06 build integrity — implemented and CI-verified

- Exact validated source SHA: `bbea1eb7a697d89c06f27fbc725dbfa175ee50e5`; [run #565](https://github.com/D8v1d777/KAAGAZ-VAULT/actions/runs/39758879722) passed both jobs.
- Three OCR models are pinned with SHA-256 plus existing Git-blob SHA-1. Gradle task inputs/outputs are declared, unconditional always-run behavior removed, and the task uses bounded retry/backoff with validated temporary-file installation.
- Tesseract4Android and SQLCipher are version-catalog dependencies with versions unchanged; SQLCipher remains AAR-only/non-transitive.
- Combined dependency-verification metadata includes AndroidX Test, Biometric and transitive artifacts. Generated delta preserved baseline hashes; no existing trusted hashes changed or were removed.
- CI runs `assembleDebugAndroidTest` under normal verification and explicitly checks `:app:prepareOcrModels` is `UP-TO-DATE` on the second invocation. README documents the build-time network requirement, offline runtime OCR, and exact-file pre-seeding.
- CO-06 is complete at this source SHA. It does not close CO-02 lifecycle/recovery or independent security-review gates.

## Guardrails
- Never weaken/skip dependency verification or offline-manifest gates to make CI green.
- Never add wildcard/trust rules to hide missing checksum metadata.
- Preserve legacy ciphertext, staging copies and key wrappers until authenticated read-back, durable journal COMPLETE, SQLCipher reopen/integrity, and independent review evidence exist.
- Do not claim emulator/physical-device validation or independent review unless matching evidence exists.
- Notion Grok/release pages are append-only: each update goes in a new bracketed section; do not replace prior sections.

## CO-02 instrumentation scope boundary — 2026-10-22
- Implemented first slice: unauthenticated auth-per-use Keystore operation must fail; absent unique alias must fail closed while a synthetic cache envelope remains byte-identical.
- Still open: actual OS-driven screen-off/background delivery, true process-death restart, biometric enrollment invalidation where supported, authenticated CryptoObject recovery success, production `DatabaseKeyManager` fixed-alias migration, and independent human security review. The synthetic SQLCipher rewrap/reopen/PRAGMA integrity and pre-checkpoint crash-recovery tests pass on run #559 but do not establish production-alias behavior.
- Human security review and exact-head CI for every final documentation/code head remain release blockers.

## Emulator profile compatibility correction — 2026-10-22
- Run #524 standard job passed on `760f3a3fa738037334739e2ea2db3356e1b77034`; emulator setup failed before executing tests because `avdmanager` did not recognize `--device Pixel 6`.
- Pinned emulator runner documentation says `profile` expects an SDK hardware-profile ID and is optional. Workflow now omits the profile so the SDK default can be used.
- Pending: new exact-head CI must prove emulator provisioning and actual test execution; no claim is made from the failed setup attempt.

## Emulator secure-lockscreen prerequisite and synthetic SQLCipher instrumentation — 2026-10-22
- Run #525 standard job passed on exact SHA `7d376082dcb316af9826ef04a14e1a7c0727dcd3`; emulator booted but KeyStore generation of an auth-bound key failed because no secure lock screen was configured. This is not an application authentication-policy failure nor a passing test.
- Emulator runner script now installs a disposable PIN using `adb shell locksettings set-pin` and removes it on every script exit with `locksettings clear --old`. The command only runs on the isolated emulator inside the pinned action.
- New synthetic SQLCipher test exercises Room/SQLCipher row preservation, production VEK-wrapped DB-key envelope store, real migration journal/coordinator durable COMPLETE, PRAGMA integrity, and preservation of synthetic legacy-wrapper bytes using only a UUID-named cache fixture and random in-memory keys.
- Explicit scope limit: the test intentionally does not invoke `DatabaseKeyManager` or the app's fixed legacy Keystore alias. Production legacy-wrapper read/rewrap integration is still unverified. The exact-head emulator run for this commit is pending.

## Emulator runner script scope correction — 2026-10-22
- Run #526 standard job passed. Emulator booted, but its script failed before Gradle because the pinned action executed each multiline script line in a distinct `/usr/bin/sh -c`; the local PIN variable did not persist and Android's `locksettings set-pin` received no PIN.
- Workflow correction uses a single folded shell command: set PIN 2468, install EXIT cleanup trap to clear it, then run `:app:connectedDebugAndroidTest` in the same shell.
- Exact-head rerun required; no test result may be inferred from #526.


## Run #527 emulator instrumentation — result and follow-up
- Run #527 (`bcf27b1a0a59a4ed7ae5902e9438b61e4b706718`) standard job succeeded and the instrumented step executed 3 tests after PIN setup worked.
- One failure: auth-per-use test asserted top-level `UserNotAuthenticatedException`, but Android 16 surfaced `IllegalBlockSizeException` with nested Keystore denial `Key user not authenticated`. The updated test checks the cause chain, preserving the invariant that unauthenticated key use must fail.
- The absent-alias fail-closed and synthetic SQLCipher/journal test were not reported as failed. superseded_by: run #565 passed on source SHA bbea1eb7a697d89c06f27fbc725dbfa175ee50e5; actual OS lifecycle delivery remains unverified.
- superseded_by: CO-06 build-integrity section above; verified metadata is integrated and run #565 passed.


## Run #532 lifecycle instrumentation diagnosis and fix
- Run #532 standard CI passed; six emulator tests executed. Auth-per-use denial, absent-alias fail-closed, synthetic SQLCipher/journal integrity, and direct screen-off receiver test passed.
- Two foreground-loss tests failed: state was not Locked after `ActivityScenario.moveToState(CREATED)`. The test had not conclusively synchronized with the initial Compose `LaunchedEffect { controller.initialize() }`.
- Follow-up adds a bounded initialization waiter, pre-transition state assertions/diagnostics, and Activity recreation test. This is pending exact-head CI and does not imply actual process-death verification.
- Keep the G2 legacy DB-key fallback decision and independent human security review as release gates.


## Run #534 and SQLCipher durable recovery injection
- Run #534 on exact SHA `a35be52a870e1c94fe971607dd4d0dc16251f559` passed standard and emulator jobs; six Android instrumentation tests completed successfully. Run: https://github.com/D8v1d777/KAAGAZ-VAULT/actions/runs/39736421084
- Added next: inject one failure after the VEK-wrapped DB-key side effect; verify the durable journal marks RECOVERY_REQUIRED, then instantiate a fresh journal/coordinator and resume real SQLCipher integrity validation. Keep VEK wrapper and legacy wrapper present/byte-identical through recovery and durable COMPLETE.
- Fresh exact-head CI required for this new scenario. Real OS lifecycle delivery/process death, production DatabaseKeyManager integration and independent security review remain open.
