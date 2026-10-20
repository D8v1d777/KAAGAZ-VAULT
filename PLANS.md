# Work Plan — CO-01 AndroidX Biometric Dependency

## Observable done-condition
The approved stable dependency `androidx.biometric:biometric:1.1.0` is pinned in the version catalog and declared by the Android app, a regression test fails if the pin or usage disappears, and the full configured Android CI gate passes on the final exact commit. The change remains an unmerged PR for independent review.

## Baseline and constraints
- Canonical base: `feat/durable-vault-migration-journal` at `789816fece4b90bb981b9e9cb3a5954f1eebd514`.
- Baseline review found no Biometric catalog alias or app dependency.
- Owner approved option 1 in PR #40 on 2026-10-20.
- AndroidX official release notes list 1.1.0 as stable and 1.4.0-alpha07 as alpha.
- No Android SDK or repository checkout is available in the active container; exact-head GitHub Actions CI is the build gate.
- This dependency PR does not wire BiometricPrompt, select runtime fallback behavior, modify the manifest, or assert auth-per-use behavior is supported. API compatibility and Android Keystore instrumentation remain open gates.
- Dependency-verification metadata is not yet present on canonical; CO-06 remains open and is not claimed fixed by this slice.

## Steps
1. Add a narrow Python regression test first to pin the approved catalog version and module usage.
2. Add the version and library alias to `gradle/libs.versions.toml`; use the alias from `app/build.gradle.kts`.
3. Run the full configured gate in CI on the final SHA; inspect actual job steps and resolve only reproduced failures.
4. Cold-review the diff against `docs/agent/REVIEW.md`.
5. Update `HANDOFF.md` and the top of `docs/engineering/progress.md` with exact SHA/run evidence and all remaining gates.
