# PLANS.md — KAAGAZ VAULT Release Gates

Updated: 2026-10-22 (Asia/Kolkata)
Canonical branch: `feat/durable-vault-migration-journal` — do not modify or merge into it.
Current validation branch: `agent/integration-security-hardening-candidate-2026-10-21`
PR #48: https://github.com/D8v1d777/KAAGAZ-VAULT/pull/48
PR #49: https://github.com/D8v1d777/KAAGAZ-VAULT/pull/49

## Current verified evidence
- PR #48 recovery-alias cleanup commit `621eca27ed3f41a3bc5aa7ac84901af93cd734bc` passed exact-head Android CI run #522: https://github.com/D8v1d777/KAAGAZ-VAULT/actions/runs/39724232188
- PR #49 build-integrity head `7b41ffe9c4d845dacf6a945c4ad167e67d40449b` passed exact-head Android CI run #521: https://github.com/D8v1d777/KAAGAZ-VAULT/actions/runs/39723726808
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
- Exact-head CI for the new instrumentation commit is pending. Do not count the baseline CI pass as validation of the new work.

## Guardrails
- Never weaken/skip dependency verification or offline-manifest gates to make CI green.
- Never add wildcard/trust rules to hide missing checksum metadata.
- Preserve legacy ciphertext, staging copies and key wrappers until authenticated read-back, durable journal COMPLETE, SQLCipher reopen/integrity, and independent review evidence exist.
- Do not claim emulator/physical-device validation or independent review unless matching evidence exists.
- Notion Grok/release pages are append-only: each update goes in a new bracketed section; do not replace prior sections.

## CO-02 instrumentation scope boundary — 2026-10-22
- Implemented first slice: unauthenticated auth-per-use Keystore operation must fail; absent unique alias must fail closed while a synthetic cache envelope remains byte-identical.
- Still open: authenticated CryptoObject completion/recovery, biometric enrollment invalidation where supported, Activity background/screen-off/process-death restart, and real SQLCipher rewrap/reopen/PRAGMA integrity on isolated synthetic data. Existing JVM migration crash tests use a fake database collaborator.
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
