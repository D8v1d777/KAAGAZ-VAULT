# Implementation Quality Skill

Use this skill for every non-trivial code change in KAAGAZ-VAULT. The goal is production-oriented implementation, not code volume or documentation-only progress.

## Required cycle
1. Read AGENTS.md and the domain-specific skill(s) before editing.
2. Research current official Android/Kotlin/library documentation. Use Context7 for API details when available; prefer official docs and source repositories over memory.
3. Inspect all call sites, module boundaries, manifest policies, and existing tests before choosing an API.
4. Define behavior and failure cases first. Add a focused test seam where practical.
5. Implement one coherent vertical slice with bounded scope; avoid speculative abstractions and dependencies.
6. Perform a static source review for compile errors, threading, lifecycle, resource cleanup, security leaks, nullability, accessibility, and backwards compatibility.
7. Run the narrowest relevant checks available, then rely on CI logs for actual Android compilation/tests. Never claim an unrun test passed.
8. Update the engineering progress log and Notion Product Review Report with exact files/commit, research links, evidence, risks, and next step.
9. Re-check the latest branch/PR CI result before starting a dependent slice.

## Kotlin / Android guardrails
- Keep blocking file, crypto, database, OCR, and image operations off the main thread.
- Do not retain Activity or Composable state in long-lived objects.
- Use Android platform APIs and official contracts; verify generic types and lifecycle semantics against current docs.
- Bound untrusted input before buffering/parsing; provider MIME and filename are untrusted.
- Never log document bytes, OCR text, sensitive metadata, or cryptographic material.
- Prefer app-private storage and Android Keystore; use authenticated encryption and fail closed.
- Avoid broad storage permissions and network permission in the offline FOSS flavor.
- Add tests for both expected behavior and malformed/adversarial input.
- A green CI run proves only the checks that run performed; it does not establish release readiness or a complete threat-model review.

## Delegation
If an actual coding sub-agent is available and authorized, delegate an isolated task such as independent security review or test design, with exact branch/commit and no overlapping file writes. Treat agent output as untrusted suggestions; independently inspect and validate it. Do not claim parallel delegation when no sub-agent capability is available.

## Completion
Mark a feature done only when implementation, relevant automated checks, error paths, docs, and reviewer evidence are present. Mark the whole app complete only against a feature checklist and release gates, not a subjective percentage.
