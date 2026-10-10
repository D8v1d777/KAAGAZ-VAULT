# Android Bootstrap — Phase 2

## Pinned toolchain

| Component | Version | Reason |
| --- | --- | --- |
| Android Gradle Plugin | 9.1.2 | Pinned Android Gradle Plugin; verify in CI before treating the toolchain as established |
| Gradle | 9.3.1 | Minimum Gradle version documented for AGP 9.1 |
| JDK | 17 | AGP 9.1 documented minimum/default |
| Kotlin built into AGP | 2.2.10 | AGP 9.1.1 release notes list KGP 2.2.10; Compose compiler plugin is pinned to the same version |
| Compose BOM | 2026.04.01 | Stable Compose release chosen to avoid requiring the Android 17 preview SDK for this bootstrap |
| compileSdk / targetSdk | 36 / 36 | Use stable Android SDK packages for reproducible CI |
| minSdk | 23 | Provisional floor; revisit against actual storage and scanner dependencies |

Primary sources:
- https://developer.android.com/build/releases/agp-9-1-0-release-notes
- https://developer.android.com/build/releases/about-agp
- https://developer.android.com/build/migrate-to-built-in-kotlin
- https://developer.android.com/blog/posts/whats-new-in-the-jetpack-compose-april-26-release
- https://developer.android.com/develop/ui/compose/setup-compose-dependencies-and-compiler
- https://github.com/gradle/actions/blob/main/docs/setup-gradle.md

## Scope

This phase establishes only a single-module Compose shell and a build gate. It does not implement scanning, OCR, persistence, encryption, indexing, reminders, export, or authentication. No feature is to be advertised as available until implemented and tested.

The manifest intentionally declares no permissions. Backups are disabled until a reviewed key recovery and backup design exists. The CI script checks the merged debug manifest—not just the source manifest—for INTERNET permission and explicitly enabled cleartext traffic.

## Build execution

CI installs the pinned Gradle distribution directly and runs:
```sh
gradle --no-daemon clean assembleDebug testDebugUnitTest lint
python3 scripts/check_offline_manifest.py
```

A checked-in Gradle Wrapper (including its official wrapper JAR and validated distribution checksum) is still required before the project is considered convenient and reproducible for local command-line contributors. The CI workflow does not substitute for that repository deliverable. No successful build is claimed until the workflow completes.

## Deferred decisions

- SQLCipher/Room integration and encrypted search remain blocked by ADR-0001.
- Key lifecycle, biometric gate, restore/recovery, secure deletion, and data migration are not implemented.
- OCR/camera dependencies are intentionally absent until their API compatibility, licenses, permissions, offline behavior, and low-end-device performance are researched.
