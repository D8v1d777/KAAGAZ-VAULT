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
