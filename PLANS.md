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

## Instrumentation dependency/action gate — G3 approval required
The repository currently has no `app/src/androidTest` tree and no AndroidX Test runner setup. Adding AndroidX Test dependencies or a third-party emulator action changes dependencies/CI and is gated under root AGENTS.md. Do not commit those dependencies/actions before explicit owner approval. Prepare a concrete memo with exact pinned versions, official docs, licenses, network/data boundaries, and an exact commit-SHA-pinned action before asking once.

## Guardrails
- Never weaken/skip dependency verification or offline-manifest gates to make CI green.
- Never add wildcard/trust rules to hide missing checksum metadata.
- Preserve legacy ciphertext, staging copies and key wrappers until authenticated read-back, durable journal COMPLETE, SQLCipher reopen/integrity, and independent review evidence exist.
- Do not claim emulator/physical-device validation or independent review unless matching evidence exists.
- Notion Grok/release pages are append-only: each update goes in a new bracketed section; do not replace prior sections.
