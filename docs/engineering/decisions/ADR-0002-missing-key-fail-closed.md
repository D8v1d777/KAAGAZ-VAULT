# ADR-0002: Fail Closed When an Android Keystore Alias Is Missing

- **Status:** Implemented in source; CI and device verification pending.
- **Date:** 2026-10-18
- **Scope:** Phase 11 auth-bound vault lock branch, key-loss safety prerequisite.
- **Decision owner:** Product owner V; engineering implementation is provisional pending review.

## Context

The document payload key provider returned a new AES key whenever its Android Keystore alias was absent. Existing AES-GCM ciphertext is bound to the original key. Replacing a missing alias with a new key cannot recover old documents and risks converting a recoverable key-loss condition into silent vault inaccessibility.

The SQLCipher database key is separately stored as a wrapped random key under no-backup storage. Its wrapper key has the same risk if the Keystore alias disappears while the wrapped key remains.

## Decision

- The key provider accepts a conservative callback indicating whether protected data already exists for that alias.
- If the alias is missing and protected data exists, key creation is refused with a typed MissingEncryptionKeyException.
- The document store checks for existing encrypted .kgv payloads and replacement backups before permitting initial key creation.
- The database wrapper provider checks for an existing wrapped database key or database file before permitting initial key creation.
- Existing aliases continue to be used as before. Keystore errors and authentication/invalidation errors are not caught and converted into new key creation.
- A missing key is a recovery/support condition. Do not delete encrypted files, regenerate keys, or imply recovery succeeded.

## Tests

A deterministic JVM test covers the policy boundary:
- no protected data → initial key creation is allowed;
- protected data exists → key replacement is rejected.

The test does not emulate Android Keystore alias deletion, StrongBox/TEE behavior, real database corruption, or physical-device recovery. Those remain integration/device-test requirements.

## Consequences and limitations

- This prevents one specific silent key-replacement path when the caller's protected-data check is accurate.
- It does not restore a lost key or make file deletion recoverable.
- It does not yet make the document key authentication-bound. Android Keystore encryption at rest and an app-level biometric prompt are different controls.
- The app still lacks a verified auth-bound lock, lock/relock lifecycle behavior, recents-screen privacy validation, key rotation/recovery, and physical-device security testing.
- The check is intentionally conservative: if any protected data may exist, preserve it and fail closed rather than risking data loss.

## Research references

- Android Developers, biometric authentication and auth-per-use Keystore keys: https://developer.android.com/identity/sign-in/biometric-auth
- Android Keystore system: https://developer.android.com/privacy-and-security/keystore
- OWASP Key Management Cheat Sheet: https://cheatsheetseries.owasp.org/cheatsheets/Key_Management_Cheat_Sheet.html

## Verification status

Source and test files have been committed on branch phase/11-auth-bound-vault-lock. The branch currently has no PR-triggered CI result for this change. Do not mark this decision security-verified until CI passes and Android Keystore/database integration tests are performed.
