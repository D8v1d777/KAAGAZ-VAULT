# Manus agentic execution loop

This loop is mandatory alongside `EXECUTION-BRIEF.md`. Do not replace root `AGENTS.md` or any newer owner instruction. Start with Manus Desktop **My Computer**.

## My Computer bootstrap (mandatory)

1. Open Manus Desktop and choose **My Computer**.
2. Authorize a dedicated KAAGAZ-VAULT working folder, not the whole home drive.
3. Confirm the working folder and terminal are on the intended local machine. If the repository is not present, clone it into a new clean directory; never overwrite or clean a dirty existing clone.
4. Probe Git, Java/JDK, Android SDK, Gradle wrapper, adb, emulator/device tools and repository status. Record commands, versions, exit codes and missing tools. Avoid disclosing tokens, secrets or unnecessary personal paths.
5. If My Computer cannot be enabled, continue safe GitHub/CI/read-only analysis and any authorized cloud work, clearly label where each command ran, and document the exact setup blocker. Never pretend a cloud command was local.

## Repeat this cycle

### A. Reconcile live state
- Read required repo instructions and relevant correction order completely.
- Refresh canonical HEAD, PR base/head SHAs, merge bases, changed files, exact-head CI, current workflow logs and recent commits.
- If any snapshot is stale, use current repository evidence. Never merge/rebase blindly just to make branches look current.

### B. Pick one task with one done-condition
- Rank by security severity and dependency: CO-01/CO-02 P0 first, then actual CI/correctness defects, then remaining correction orders.
- Choose one correction order for the branch. State an observable acceptance criterion before editing.
- Parallelize read-only audits/CI polling when possible. Only one writer per branch/files.

### C. Reproduce first
- Trace relevant callers, data flows, tests and lifecycle behavior.
- Add/run the smallest failing test or reproduction before production edits.
- If the reproduction passes unexpectedly, mark the suspicion `REFUTED` per project protocol and move on.
- Use adversarial tests where relevant: locked access, wrong/missing key, caller-buffer mutation, exception cleanup, tampering, truncation, unknown version/context, concurrency, process death and crash/replay.

### D. Implement the smallest authorized fix
- Create a fresh task branch from current canonical HEAD unless a repository-approved porting strategy requires otherwise.
- No speculative refactors, duplicate abstractions, TODO-as-implementation, fake success states or unrelated scope.
- Preserve existing data and key material. Never replace a lost/invalid key with a new key to bypass a failure.
- Respect approval gates for cryptographic formats, key lifecycle, recovery envelopes, migration journals, DB schema, dependencies, manifest permissions, destructive operations and canonical branch changes. Do all safe work that does not cross the gate.

### E. Verify locally and in CI
- Run focused regression tests, `git diff --check`, full diff review and the full repository gate.
- Use My Computer for local build/test and emulator/instrumentation work if available. Record actual environment, commands, exit codes, test counts, logs and API level.
- Do not upload logs containing secrets or user data.
- Push only isolated task branches. Wait for CI, fetch the run and confirm the workflow head SHA exactly equals the final commit SHA.
- If a test fails, inspect the real log and fix one cause at a time. Do not repeat identical no-change attempts more than twice without new evidence.

### F. Self-review, report, and close the task
- Review all changed lines and affected callers; check concurrency, lock transitions, data loss, unsafe fallback, swallowed errors, API gaps, manifest drift, secrets and dead code.
- Run an appropriate secret scan and report its scope/limitations.
- For security/database/manifest/build changes, use a genuine independent reviewer if available; otherwise say `NOT_AVAILABLE`, never self-label the review independent.
- Update `HANDOFF.md`, `docs/engineering/progress.md`, correction-order status and `MANUS-EXECUTION-REPORT.md` per root instructions.
- Record task, done-condition, reproduction, changed paths, commit SHA, exact CI URL/conclusion, tests, risk, reviewer, and unresolved items.
- Classify claims as `VERIFIED`, `FAILED`, `NOT RUN`, or `UNVERIFIED`.

### G. Continue; do not stop after reporting
After each closed task, refresh the queue and return to A. Stop only when safe high-priority work is complete or every remaining task has a specific blocker (approval, unavailable hardware/tool, external dependency, or unresolved failure). Record the exact blocker, evidence gathered, maximum safe work completed, and the next actionable step. Do not claim all work is complete just because time is short.

## Hard safety boundaries

- Never push or merge to canonical/main; never force-push or rewrite shared history.
- No unapproved crypto/key/envelope/journal/schema changes or destructive data operations.
- One correction order per task branch.
- No progress-percentage inflation for documentation, a draft PR, or an isolated primitive not integrated into production.
- Build success is not proof of physical-device behavior, Android Keystore semantics, independent review, or full vault security.