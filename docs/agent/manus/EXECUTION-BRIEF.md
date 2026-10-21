# KAAGAZ-VAULT — Manus execution brief

## Mission

Execute the owner-uploaded Manus task packet, this repository brief, and `AGENTIC-LOOP.md`. This is an implementation assignment, not a request for a plan. Do not stop after a repository survey: perform safe, authorized, evidence-backed engineering work and continue through the queue until high-priority work is complete or a genuine blocker is precisely documented.

## Required first actions

1. Activate Manus Desktop **My Computer**. Authorize a dedicated project folder only. Confirm terminal execution is on the user's intended local machine.
2. Read root `AGENTS.md`, `HANDOFF.md`, correction orders, nested instructions, ADRs, security docs, repository skills, tests and `.github/workflows/android-ci.yml`.
3. Refresh canonical HEAD, every relevant PR's current base/head, merge base, changed files and exact-head CI. The baseline in `README.md` is a snapshot; live evidence wins.
4. Record environment probes and a baseline before edits: `git rev-parse HEAD`, `git status --short --branch`, `java -version`, Android SDK variables, `./gradlew --version` (or Windows wrapper), `adb devices -l`, and tool availability. Do not print secrets or sensitive local paths.
5. Pick the highest-risk safe bounded task. Reproduce first with a failing test or minimal command. If the suspected defect does not reproduce, record `REFUTED` according to project rules instead of inventing a fix.

## Priority order

1. **CO-00 evidence/integration correctness:** refresh PR heads and exact-SHA CI; resolve stale-base understanding without merging.
2. **P0 CO-01 authentication/session boundary:** trace every path that reads documents, reminders, encrypted metadata, database state, indexes, and background work. Identify any access that bypasses a verified session or uses unverified key material. The session primitive is not a complete security boundary until all required paths are wired and tested.
3. **P0 CO-02 recovery lifecycle:** review recovery envelope, key invalidation, missing/wrong key, tampering, truncation, unknown versions/context, one-time display/confirmation, failure-closed behavior and persistence boundaries.
4. Continue through the remaining correction orders by severity/dependency. Address real correctness or CI failures before UI polish.

## Owner-approved security policy — preserve, do not reinterpret

The owner approved A2/B2/C1:

- **A2 authentication:** strong biometric or secure device credential only where the same auth-per-use cryptographic operation is supported and tests demonstrate it. No silent downgrade to unauthenticated keys or time-window auth. Verify API-level compatibility against current primary Android documentation and the project's min/target SDK.
- **B2 recovery:** independent random 256-bit recovery key; separately versioned authenticated recovery envelope for the VEK; one-time display plus storage-confirmation; no server/cloud copy and no plaintext app-side persistence. Wrong, missing, tampered, truncated or unsupported-version material must fail closed.
- **C1 lock:** explicit lock, screen-off, app foreground loss and process restart. Do not introduce an inactivity grace period without owner approval.

AndroidX Biometric 1.1.0 had separate owner approval documented in PR #40; PR #41 remains separate implementation work and that approval does not authorize a merge. Follow current repo dependency verification and merge gates.

## Execution discipline

- Work on fresh, isolated task branches based on the current canonical HEAD unless the repository's documented porting strategy says otherwise. One correction order per branch/PR.
- Parallelize independent read-only investigations and CI polling only when supported; never allow concurrent agents to edit the same branch/files.
- Reproduce before production edits; add adversarial regression tests; make the smallest coherent fix.
- Preserve existing ciphertext, database state, key aliases and migration journals. Never generate replacement keys just to make a failed vault appear usable.
- Respect all owner/repository approval gates for crypto/key/envelope/journal formats, schema, dependencies, manifest permissions/exported components, canonical writes, history rewrites, destructive operations and releases. Complete safe preparatory work without guessing a gated decision.
- Run focused tests, inspect the complete diff, run `git diff --check`, then run the repository's full verification gate. Use My Computer for local Gradle and emulator/device tests if supported.
- After pushing an isolated branch, wait for CI and verify the run's head SHA exactly matches the final commit. A newer commit invalidates older CI evidence for the new head.
- Two identical no-change retries maximum without new diagnostic evidence; then change diagnostic approach or record a concrete blocker.
- Update `HANDOFF.md`, `docs/engineering/progress.md`, correction-order status, and the execution report per repository rules. Do not inflate completion for docs, a draft PR, or an unwired primitive.
- Do not merge to canonical, force-push/rewrite shared history, or claim an independent security review when none occurred.

## Completion condition

Continue the agentic loop until safe high-priority work is completed with evidence or the remaining work is blocked by a specific unavailable capability, owner decision, external dependency, or unresolved defect. Leave a clean working tree and report every claim as `VERIFIED`, `FAILED`, `NOT RUN`, or `UNVERIFIED`. A green JVM/CI run is not proof of device Keystore behavior or complete security integration.