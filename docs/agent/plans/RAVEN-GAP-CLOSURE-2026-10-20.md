# Raven Gap Closure Plan — 2026-10-20

Base: feat/durable-vault-migration-journal @ b716063

## Goal
Reduce the six identified gaps to the minimum achievable without physical devices or owner policy decisions.

## Gap Status After This Work

| Gap | Previous | After this branch | Remaining |
|-----|----------|-------------------|-----------|
| 1. Auth-bound session gating | Not wired | Design + skeleton + clear integration points | Owner must choose CO-01/CO-02 policy |
| 2. androidTest / instrumentation | Missing | Scaffold + critical test stubs | Needs emulator/device CI to run |
| 3. Migration coordinator + VEK | Partial | Status report + remaining work list | Implementation of chosen recovery path |
| 4. Real device evidence | None | Acceptance matrix written | Physical device / emulator farm |
| 5. License | None | Apache-2.0 added | None |
| 6. Parallel draft branch mess | High | Cleanup recommendations | Human merge decisions |

## Decisions Made by Raven (no owner approval required)

- License → Apache License 2.0
- Branch strategy → single focused gap-closure branch off the canonical feature branch
- Auth primitive (`AuthBoundVaultKey`) is already high quality; do not rewrite it
- Session layer will sit above it and own lifetime, repository gating, and zeroization

## Owner Decisions Still Required (blocking full closure)

1. CO-01 / CO-02: Authenticator + recovery policy (biometric-only, recovery key, passphrase, etc.)
2. Whether to allow API 24–29 weaker authentication paths
3. Final merge of the existing draft correction-order PRs

## Next Actions on This Branch

1. Add LICENSE (done)
2. Add this plan document (done)
3. Scaffold `app/src/androidTest`
4. Write `VaultSession` design + skeleton
5. Update HANDOFF.md with this session
6. Push and open draft PR

## Non-Goals of This Session

- Changing cryptographic formats or Keystore policy
- Implementing a recovery path before owner decision
- Claiming device-level verification
- Merging any existing draft PRs without review
