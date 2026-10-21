# Manus execution handoff

This directory contains the repository-visible operating instructions for the Manus agent working on KAAGAZ-VAULT. The owner has already uploaded the single allowed ZIP task packet to Manus; use that packet together with these repository files. Do not ask the owner to pay for or upload each Markdown file separately.

## Required reading order

1. Root `AGENTS.md` — mandatory; current owner instructions and repository rules take precedence.
2. Root `HANDOFF.md`.
3. Root `docs/agent/CORRECTION-ORDERS.md` (or the actual current path discovered from the repository).
4. This directory's `EXECUTION-BRIEF.md`.
5. This directory's `AGENTIC-LOOP.md`.
6. This directory's `REPORT-TEMPLATE.md`.
7. Relevant nested `AGENTS.md`, ADRs, security docs, skills, tests and CI workflow.

## Start requirement: Manus Desktop My Computer

Use Manus Desktop's **My Computer** feature before local implementation. Authorize only a dedicated KAAGAZ-VAULT working folder. Verify commands run on the intended local machine and record Git, JDK, Android SDK, Gradle wrapper, adb and emulator availability. Never label cloud-sandbox commands as local-machine execution. If My Computer is unavailable, do safe remote/CI work that is possible and report the exact limitation; do not fabricate local test results.

## Expected output

Produce a detailed `MANUS-EXECUTION-REPORT.md` in the working tree using `REPORT-TEMPLATE.md`, and provide draft PR links plus final exact-head CI evidence. Work on isolated task branches, one correction order per branch. Do not merge or push to canonical. This handoff branch itself contains instructions only; it is not an implementation approval.

## Starting point snapshot (refresh before use)

- Repository: https://github.com/D8v1d777/KAAGAZ-VAULT
- Canonical branch: `feat/durable-vault-migration-journal`
- Canonical HEAD at handoff creation: `6b30ceaf5ab09b2494d82356b08c4bebf7406521`
- PR #37 head `0aa62ce41b66bba2f37bb841e0e908e11fa984e9`: configured exact-head CI succeeded in [run #39536769146](https://github.com/D8v1d777/KAAGAZ-VAULT/actions/runs/39536769146).
- PR #42 head `2c6a1d694e7adbf21fd226be0b69748fe6ec6282`: configured exact-head CI succeeded in [run #39536144943](https://github.com/D8v1d777/KAAGAZ-VAULT/actions/runs/39536144943).
- Existing PRs have been based on older SHA `789816fece4b90bb981b9e9cb3a5954f1eebd514`; refresh each live PR's base/head and inspect merge-base/diff before any integration recommendation.
- Both cited CI successes validate only the configured checks on those exact SHAs, not end-to-end security or physical-device behavior.

This snapshot is historical context, not a substitute for live checks.