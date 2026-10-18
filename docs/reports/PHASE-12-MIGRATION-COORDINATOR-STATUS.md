# Phase 12 — Migration Coordinator Foundation

**Updated:** 18 October 2026  
**Code PR:** [#12](https://github.com/D8v1d777/KAAGAZ-VAULT/pull/12)  
**Latest CI for coordinator tests:** [Run #298](https://github.com/D8v1d777/KAAGAZ-VAULT/actions/runs/39144064741) — pending at the time of this report.

## Delivered in this slice

- Added a VaultMigrationBackend adapter contract for legacy-document discovery, ciphertext staging, staged-payload verification, publication, database-key rewrap, and database-integrity verification.
- Added VaultMigrationCoordinator to resume from VaultMigrationJournal, persist checkpoints around idempotent operations, move interrupted work through the existing recovery state, and require document publication before database-key migration.
- Added JVM tests for a complete state-machine run, recovery after an injected publication interruption, and an empty vault that still verifies the database.
- Kept the coordinator isolated from UI and repository code. No changes were made to main; code remains on the Phase 12 feature branch and documentation remains on this documentation branch.

## Deliberate limits

This is an orchestration foundation, **not a production migration implementation**. The backend contract has no real adapter yet. In particular:

- It does not yet decrypt legacy documents and re-encrypt them with the VEK.
- It does not integrate the VEK with EncryptedDocumentStore or the current DocumentKeyProvider.
- It does not rewrap the actual SQLCipher key or verify SQLite WAL/journal/shared-memory sidecars.
- It does not gate repositories, OCR, search, camera, reminders, or background work on an authenticated session.
- It deliberately does not automate legacy-key/ciphertext retirement. Cleanup must remain a separately reviewed step after device-level recovery tests.
- JVM fake-backend tests do not prove Android Keystore, biometric, physical-device, process-death, or power-loss behavior.

## Checkpoint and retry contract

Every backend mutation must be idempotent. If the process dies after a side effect but before the next state snapshot is persisted, the coordinator will repeat that operation on resume. Real adapters must therefore detect already-staged/already-published content and avoid duplicate records or destructive replacement. Verification must authenticate the staged ciphertext and compare the recovered payload with the legacy source; checking only file existence is insufficient.

The journal's CRC32 detects accidental corruption, not malicious tampering. The journal stores migration state and opaque UUIDs only; it is not an authenticated source of truth against a local attacker.

## Verification and next gates

1. Require CI run #298 to pass on the exact latest code head before counting the coordinator tests as verified.
2. Review and test retry/idempotency for every real backend operation.
3. Implement the VEK-backed document adapter with ciphertext-only staging, authenticated verification, atomic publication, and source retention.
4. Implement SQLCipher key rewrap and test database reopen plus WAL/journal/shared-memory handling.
5. Add a shared authenticated-session gate across all sensitive repository and background entry points.
6. Add Android instrumentation and physical-device tests for auth-per-use Keystore, prompt cancellation, biometric enrollment invalidation, process death, storage leakage, and recovery.
7. Do not increase the overall weighted product estimate from 35% until these end-to-end gates produce evidence.

## Progress ratios

| Measure | Current ratio | Meaning |
|---|---:|---|
| Historical CI-backed implementation slices | 8/11 | Phases 2–9 have historical CI evidence on tested commits/descendant stacks |
| Fully release-accepted native phases | 0/11 | Strict device, integration, accessibility, and security gates remain open |
| Overall weighted product estimate | 35% | Unchanged; coordinator foundation is not end-to-end acceptance |

Do not treat the number of files or authored tests as a substitute for security acceptance.
