# ADR-0001: Sensitive Data Search Strategy

- **Status:** Proposed — implementation must not assume a decision has been accepted until validated against actual Android/library constraints.
- **Date:** 2026-10-09
- **Owner:** Connor (engineering)

## Context
KAAGAZ VAULT must support local search while protecting OCR text and sensitive document metadata. Plain SQLite FTS over OCR text can create readable copies in FTS shadow tables, database pages, WAL/rollback journals, temporary files, and backups. Encrypting image files alone does not protect those copies.

## Decision
Before implementing search persistence, compare:
1. An encrypted SQLite/database-and-index solution with verified Android compatibility and acceptable licensing/maintenance.
2. Application-level encryption with a deliberately limited index containing only fields approved by a documented leakage analysis.
3. Decrypt-on-demand search with measured latency and battery cost.

No option is approved yet. Until a decision record is amended with evidence, do not persist plaintext OCR text in a search index and do not describe the vault as fully encrypted.

## Initial candidate research (2026-10-09)

The first candidate to prototype is the current **SQLCipher for Android** project, rather than the older `android-database-sqlcipher` integration:
- Zetetic's current project documents Room 2 integration through `SupportOpenHelperFactory` and Room 3 integration through `SQLCipherDriver`.
- Maven Central lists `net.zetetic:sqlcipher-android:4.19.1` at the time of research; resolve and pin the version again when a Gradle project exists.
- SQLCipher Community Edition is usable subject to its BSD-style attribution and licence-notice requirements. Include those notices in a user-accessible in-app location and distribution documentation if adopted.

Primary references:
- Current Android library and Room integration: https://github.com/sqlcipher/sqlcipher-android
- Migration guidance from the older Android library: https://www.zetetic.net/sqlcipher/sqlcipher-for-android-migration/
- Community Edition licensing and attribution: https://www.zetetic.net/sqlcipher/license/
- Maven Central artifact metadata: https://central.sonatype.com/artifact/net.zetetic/sqlcipher-android
- Android Room driver/compatibility documentation: https://developer.android.com/training/data-storage/room/migration-2-to-3

This remains a **proposed candidate, not an approved final decision**. A Room 2.8.4 + SQLCipher for Android 4.19.1 prototype has now been implemented on branch phase/04-encrypted-search-v2. Its first CI verification is pending, and device-level database/sidecar leakage tests have not been performed. Before approval, verify Kotlin/AGP/NDK/minSdk compatibility, supported ABIs, dependency graph, binary size, license notices, key lifecycle, WAL behavior, and actual database sidecar leakage. Do not copy a static sample passphrase into production code.

## Evaluation criteria
- What an attacker can recover from a locked device, copied app data, backups, crash artifacts, and a compromised running process.
- Whether query terms, result counts, token frequency, or access patterns leak.
- Telugu/Hindi/English/Hinglish search behavior and numeric/date matching.
- Key lifecycle, migrations, deletion, corruption recovery, and process-death safety.
- Library license, maintenance, minSdk/API compatibility, APK size, latency, memory, and battery.
- Ability to test the actual database and sidecar files rather than only a wrapper API.

## Required evidence before approval
- Primary-source documentation and license review for candidate libraries.
- A small reproducible prototype and tests on the intended Android API range.
- Leakage inspection of database, WAL/journal, temporary files, and backup artifacts.
- Search correctness and performance measurements on a synthetic mixed-language corpus.
- Explicit residual-risk statement and product implications.

## Prototype status (2026-10-10)
- Room 2 + SQLCipher 4.19.1 + AndroidX SQLite 2.7.0 is the current implementation candidate. KSP 2.2.10-2.0.2 is pinned to the project's Kotlin 2.2.10 toolchain.
- Database key material is generated randomly and wrapped with a separate Android Keystore AES key. The wrapper file is in no-backup storage; an existing invalid wrapper fails closed.
- The database stores a normalized copy of document names and OCR text. This field is sensitive but resides inside the SQLCipher database. Search is substring/phrase search; no plaintext FTS table is used.
- The encrypted document files remain the source of truth. The index is rebuilt from those files after library refresh; cross-file/database operations are not atomic.
- SQLCipher Java logging is redirected to NoopTarget. Native/core logging behavior still needs device verification.
- Required license notices are packaged and a UI entry point exposes them.
- CI and device tests have not yet validated the prototype. **ADR status remains Proposed** until dependency/build checks, encrypted DB open/reopen, wrong-key/tamper behavior, WAL/journal inspection, and performance tests pass.

## Consequences
This gate may delay final approval, but it prevents a misleading security promise and expensive storage migrations later. The implementation is a candidate prototype only; do not claim the metadata/search layer is verified or release-ready until the acceptance evidence is recorded.
