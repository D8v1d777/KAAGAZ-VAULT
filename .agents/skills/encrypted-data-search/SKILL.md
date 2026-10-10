# Encrypted Data Search Skill

## Purpose
Design local search without accidentally exposing private OCR text through database indexes or auxiliary files.

## Decision required before implementation
Choose and document one of these strategies based on the product's search requirements and threat model:
- **Encrypted database / encrypted index:** strongest alignment between encrypted content and searchable storage, subject to library availability, maintenance, licensing, platform compatibility, and performance review.
- **Application-level encrypted fields with a deliberately limited index:** index only explicitly approved low-sensitivity tokens/metadata; document leakage and user-visible search limitations.
- **Decrypt-on-demand scanning:** minimize persistent plaintext indexes but accept latency and battery costs; define bounded behavior for large vaults.

Do not quietly default to plaintext FTS over OCR text while describing the vault as fully encrypted.

## Required workflow
1. List every indexed field, tokenization rule, normalization step, and derived value.
2. Classify fields by sensitivity; assume OCR text, names, addresses, IDs, medicine names, shop names, amounts, and filenames can be sensitive.
3. Identify SQLite main DB, FTS shadow tables, WAL, rollback journal, temp files, backups, and diagnostic output as possible leakage surfaces.
4. Define search semantics for Telugu, Hindi, English, Hinglish, punctuation, digits, and mixed scripts before choosing tokenization.
5. Benchmark latency, memory, battery, index size, and leakage trade-offs on representative low-end hardware.
6. Test deletion/update consistency, process death, index rebuild, migrations, and lock/unlock transitions.
7. State explicitly which query types are supported and what information can leak under the selected strategy.

## Acceptance criteria
- An ADR records the selected approach and rejected alternatives.
- No sensitive field enters a plaintext index by accident.
- Tests prove index rebuild/update/delete consistency.
- Performance claims come from measurements, not estimates.
