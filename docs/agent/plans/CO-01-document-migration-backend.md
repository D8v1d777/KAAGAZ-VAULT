# Work Plan — CO-01 document migration backend

## Observable done-condition
A concrete file-backed implementation of the existing migration-backend contract stages each legacy document under the same opaque ID into a distinct destination root using the existing authenticated envelope and VEK key-access callback; verifies exact plaintext equivalence and authentication before publication; preserves legacy and staging copies; and resumes after an injected failure at each backend side-effect boundary. The real SQLCipher key-rewrap/integrity behavior remains behind an explicit collaborator and is not simulated as production success.

## Baseline
- Current canonical base: `feat/durable-vault-migration-journal` SHA `6b30ceaf5ab09b2494d82356b08c4bebf7406521`. This is a clean port of the existing draft slice, not verification of the earlier head.
- Existing `VaultMigrationBackend` is an interface only; current coordinator tests use a fake and cover interruption at publish, but there is no file-backed document migration adapter.
- `EncryptedDocumentStore` uses the current `AesGcmEnvelope` v1 format and document-ID AAD. Migration must retain the legacy directory until a separately reviewed app-path switch/retirement gate.
- Existing database wrapping uses a distinct Keystore alias and SQLCipher key. This slice must not invent or persist a new database-key envelope; delegate SQLCipher rewrap/integrity to an explicit interface.
- Do not change manifest, key aliases, envelope/journal formats, SQLCipher schema, dependencies or production call sites in this slice.

## Test-first
1. Add a regression test that constructs a legacy vault and expects `EncryptedDocumentMigrationBackend` to preserve the legacy copy, stage/re-encrypt under the same ID, verify tampering, publish to a separate root, and recover when failures are injected after stage/verify/publish/database operations.
2. Add the smallest adapter that satisfies those tests.
3. Run the full configured gate on the final exact SHA in GitHub Actions.
4. Cold-review the final diff; human security review remains mandatory before merge.

## Boundaries / next
This PR does not switch the production app to the migrated root or retire legacy ciphertext. Database rewrap is an injected boundary only; a real SQLCipher implementation and instrumentation evidence are still required before production wiring.
