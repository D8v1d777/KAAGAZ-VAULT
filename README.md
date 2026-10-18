# KAAGAZ-VAULT

**Privacy-first, offline-first Android document vault — implementation in progress.**

> **Source code is not on `main` yet.** The default branch currently serves as a documentation landing page. The complete Android project is available on the review branch below.

## Review the actual Android source

**Recommended branch:** [`feat/durable-vault-migration-journal`](https://github.com/D8v1d777/KAAGAZ-VAULT/tree/feat/durable-vault-migration-journal)

This branch includes the Gradle project, Android app source, manifest, tests, offline-manifest policy script, CI workflow, and implementation README. The earlier [`phase/11-auth-bound-vault-lock`](https://github.com/D8v1d777/KAAGAZ-VAULT/tree/phase/11-auth-bound-vault-lock) branch contains the underlying app before the latest Phase 12 migration work.

To check out the current review source:

```bash
git clone --branch feat/durable-vault-migration-journal https://github.com/D8v1d777/KAAGAZ-VAULT.git
cd KAAGAZ-VAULT
```

## Build and test

Requirements: JDK 21, Android SDK platform 36, build-tools 36.0.0, and an available Android SDK license acceptance.

```bash
python3 -m unittest discover -s scripts -p 'test_*.py'
./gradlew --no-daemon clean assembleDebug assembleRelease testDebugUnitTest lint
python3 scripts/check_offline_manifest.py
```

These are the commands configured in [Android CI](https://github.com/D8v1d777/KAAGAZ-VAULT/blob/feat/durable-vault-migration-journal/.github/workflows/android-ci.yml). Latest recorded run for the migration-coordinator head: [CI #299 — passed](https://github.com/D8v1d777/KAAGAZ-VAULT/actions/runs/39144069929), commit `6291594886eb6f6fb03c644797afc1217adb90e6`. This is CI evidence for that commit, not physical-device or release-security acceptance.

## Privacy review entry points

- [Android manifest](https://github.com/D8v1d777/KAAGAZ-VAULT/blob/feat/durable-vault-migration-journal/app/src/main/AndroidManifest.xml) — declares CAMERA and POST_NOTIFICATIONS; no explicit INTERNET permission is present in the source manifest.
- [App Gradle configuration](https://github.com/D8v1d777/KAAGAZ-VAULT/blob/feat/durable-vault-migration-journal/app/build.gradle.kts) — minSdk 24, targetSdk 36, pinned OCR model hashes.
- [Unit tests](https://github.com/D8v1d777/KAAGAZ-VAULT/tree/feat/durable-vault-migration-journal/app/src/test/java)
- [CI workflow](https://github.com/D8v1d777/KAAGAZ-VAULT/blob/feat/durable-vault-migration-journal/.github/workflows/android-ci.yml)
- [Phase 12 migration status](https://github.com/D8v1d777/KAAGAZ-VAULT/blob/docs/auth-bound-migration-design/docs/reports/PHASE-12-MIGRATION-COORDINATOR-STATUS.md)

## What is implemented vs. pending

The source branch includes Kotlin/Jetpack Compose UI, document-picker import, encrypted local payload storage, Android Keystore-backed cryptographic primitives, SQLCipher metadata/search paths, camera capture, offline OCR and bounded PDF extraction. The migration coordinator and auth-bound VEK work are partial foundations.

Not release-accepted: real VEK-backed migration adapter, SQLCipher key rewrap integration, app-wide authenticated session gating, physical-device camera/OCR and biometric tests, crash/power-loss validation, accessibility review, and independent security review. The available test suite is primarily JVM unit tests plus Python CI policy tests; do not assume instrumentation coverage for a test category unless it exists on the branch. In particular, reviewers should verify the import-signature validation tests directly; their presence is not currently confirmed by this landing page.

No real Aadhaar/PAN data, prescription images, credentials, or keystores should be committed. Synthetic fixtures only.

## License

A project license has not been selected. Do not assume this repository grants permission to reuse or redistribute the project.
