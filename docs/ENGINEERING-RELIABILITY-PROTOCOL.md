# Engineering Reliability Protocol

**Status:** Proposed project-wide working standard  
**Created:** 2026-10-19  
**Scope:** KAAGAZ-VAULT engineering, security-sensitive design, implementation, verification, and agent handoffs.

This document is a process control. It does not establish that the product or any cryptographic design is secure, complete, or release-ready.

## 1. Evidence before claims

Every material statement in a status report, review, or handoff must be labelled as one of:

- **Verified:** directly supported by a reproducible command/test result, current repository state, authoritative source, or independently inspected artifact. Include the commit/ref and evidence location where relevant.
- **Observed:** directly seen in source, logs, a tool response, or a UI, but not independently reproduced.
- **Inferred:** reasoned from available evidence; state the premise and uncertainty.
- **Unverified:** not checked against current evidence.
- **Blocked:** cannot be verified or completed because a named prerequisite is missing.

Never upgrade a claim's status because it appeared in an earlier report, memory, generated documentation, a green run on a different commit, or an intended action. Record failures and contradictory evidence as well as successes.

## 2. Claim-to-evidence gate

Before saying a task is complete, answer:

1. What exact acceptance criterion was required?
2. What changed, and on which repository, branch, and commit SHA?
3. Which commands/checks ran against that exact state?
4. What were the actual exit codes and results?
5. What did the checks not cover?
6. What independent review or device evidence remains outstanding?

A commit, a draft PR, a design memo, or a test file is not itself proof of correctness. A test passing on an older SHA does not verify a newer SHA. Empty CI/status results mean **unknown/unverified**, not passed or failed. Do not claim local execution unless the command was actually run in the relevant environment.

## 3. Verification ladder

Report the highest level actually achieved; do not imply that a lower level proves a higher one.

1. **Static inspection:** source/diff and dependency/configuration review.
2. **Focused tests:** tests for the changed behavior, including negative and boundary cases.
3. **Full configured suite:** every repository-configured test/check passes on the exact SHA.
4. **Integration/instrumentation:** real app components, storage, lifecycle, permissions, and platform behavior tested together.
5. **Device matrix:** representative supported devices/OS versions, including low-memory and interruption scenarios.
6. **Independent security review:** reviewer examines threat model, protocol, implementation, failure handling, and evidence.
7. **Release gate:** all product-defined criteria pass, with explicit residual risks and rollback/recovery plan.

A level is complete only when its evidence exists. Security-sensitive releases must not substitute ordinary unit/CI success for device or independent cryptographic review.

## 4. Five failure controls

### Hallucination
- Check APIs, versions, cryptographic behavior, and security recommendations against primary documentation and the actual pinned dependency/source.
- Prefer a minimal reproducible experiment or a test over plausible explanation.
- Keep unknown decisions open; do not silently choose a protocol because it is convenient.
- Separate external-source findings from project-specific facts and engineering inference.

### Verification gaps
- Inspect the exact diff before and after changes.
- Run the narrowest useful test first, then the full configured suite when feasible.
- Capture exact commit SHA, command, exit status, test counts, and logs.
- For cryptographic formats, use fixed golden vectors, an independent second implementation, adversarial/property/fuzz tests, and human review.
- Never use real sensitive documents during experimental crypto work.

### Execution continuity
- Work in bounded loops: **recover checkpoint → verify live state → research → make one scoped change → test → inspect evidence → update checkpoint**.
- Each task needs a concrete deliverable, acceptance criteria, dependencies, and a final status.
- Do not call a reminder, plan, branch, or draft PR “completed work.”
- If a tool is unavailable or returns an error, record the blocker and switch only to a clearly labelled alternative; do not imply the failed action succeeded.

### Context loss
- Before each resumed session, read this protocol, the latest resume checkpoint, the active plan, and current repository/PR state.
- The live repository and exact-head CI are authoritative for code state; the checkpoint is a navigation aid, not a substitute for checking them.
- Keep one concise current checkpoint containing: date/time, repo, branch, base/head SHA, PR state, completed evidence, unresolved risks, exact next action, and commands/links to reproduce.
- Preserve historical notes, but mark stale entries and supersede them with a timestamped delta rather than silently treating old state as current.

### Overconfidence
- Every status sentence must be supportable by its cited or linked evidence.
- Use “not checked,” “not available,” and “unknown” when appropriate.
- Report failed checks, test limitations, and unresolved risks next to successes.
- Do not increase completion percentages for documentation, planning, or tests alone unless the project's weighted definition explicitly awards that work.
- Treat security claims as hypotheses until the defined review and verification gates pass.

## 5. KAAGAZ-VAULT security invariants

Until an approved protocol and release review say otherwise:

- No claim of production-ready end-to-end encryption.
- Do not use real sensitive documents with experimental cryptography.
- Never merge a draft PR merely because it is green or looks complete.
- Never retire old keys, delete legacy data, or run irreversible migrations without tested recovery and explicit approval.
- No plaintext should reach preview, OCR, export, logs, or other consumers until the approved all-or-nothing integrity checks pass.
- Never reuse an AES-GCM key/nonce pair; retry behavior must reuse persisted ciphertext or create a fresh key/version according to a reviewed design.
- Keep content-encryption keys non-extractable where possible; do not weaken an existing key helper globally to simplify wrapping.
- Any key hierarchy, KDF parameters, nonce budget/retry policy, authenticated manifest, recovery, or anti-rollback design requires primary-source research and independent review before implementation is treated as approved.

## 6. Current workstream snapshot — 2026-10-19

**Observed from GitHub PR #14:** [Phase 3 canonical chunk AAD candidate](https://github.com/D8v1d777/KAAGAZ-VAULT/pull/14) is open, draft, and unmerged. Its recorded head is `a8abe0d5a2185f684eb58a405bd5508ced7b6431`, targeting `feat/e2ee-and-human-centered-design`.

**Reported but not independently reproduced in this session:** an isolated Node.js snapshot passed 13/13 focused tests and a separate Python reference encoder reproduced the 38-byte golden vector. The PR explicitly says these are not repository CI evidence.

**Current CI status:** exact-head CI remains **unverified** in the checkpoint and PR description. No claim of CI success is authorized until a completed run for the exact current head is inspected.

**Not implemented / still gated:** authenticated manifest and chunk completeness; key wrapping and approved VEK/DEK/KEK lifecycle; Argon2id library/parameters; crash-safe nonce/retry/rotation policy; recovery; anti-rollback; cross-runtime/property/fuzz tests; independent cryptographic review; production E2EE integration.

**Next action:** verify the current PR head and discover the actual configured workflow/Checks result. If no exact-head run exists, document that fact and use an available reproducible local checkout/test path without misrepresenting it as CI. Then inspect logs and resolve the first concrete failure. Do not expand the cryptographic implementation until the design and key-lifecycle blockers are reviewed.

**Progress accounting:** retain Phase 3 at 10% and overall project at 35% until evidence-backed product implementation warrants a change. This protocol document alone earns no implementation progress.

## 7. Definition of Done for each engineering task

- [ ] Scope and acceptance criteria stated before editing.
- [ ] Current branch, base, and starting SHA recorded.
- [ ] Primary-source research completed for any consequential unfamiliar/security-sensitive decision.
- [ ] Change is narrow and reviewed as a diff.
- [ ] Relevant negative, boundary, regression, and failure-path tests added where appropriate.
- [ ] Actual test commands, results, and exact SHA recorded.
- [ ] Remaining verification levels and residual risks stated explicitly.
- [ ] Checkpoint updated with what changed, what did not change, and one exact next action.
- [ ] No merge, release, data deletion, or key retirement without the relevant explicit approval and safety gates.
