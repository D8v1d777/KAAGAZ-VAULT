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

This is a **candidate, not an adoption decision**. The repository currently has no Android Gradle project to compile against. Before selection, verify the chosen Room generation, Kotlin/AGP/NDK/minSdk compatibility, supported ABIs, dependency graph, binary size, licence notices, passphrase/key lifecycle, WAL behavior, and real database sidecar leakage. Do not copy a static sample passphrase into production code.

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

## Consequences
This gate may delay the search implementation, but it prevents a misleading security promise and expensive storage migrations later. The next implementation phase should establish key management and storage boundaries before indexing.
