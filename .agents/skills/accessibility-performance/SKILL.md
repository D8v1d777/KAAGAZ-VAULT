# Accessibility & Performance Skill

## Purpose
Make the vault usable with assistive technology and responsive on constrained Android devices without weakening privacy.

## Accessibility
- Give controls meaningful labels, roles, state descriptions, and logical TalkBack traversal.
- Do not communicate confidence, warnings, or category using color alone.
- Support text scaling, contrast, touch target sizing, keyboard/navigation behavior where relevant, and localized error messages.
- Keep capture, OCR review, correction, lock, export, and deletion flows understandable without relying on visual-only cues.
- Validate Compose semantics with automated UI tests and manual TalkBack checks on representative screens.

## Performance
- Measure cold start, camera-to-preview latency, OCR latency, search latency, memory, database/index growth, battery, and thermal impact.
- Use representative low-end hardware and large synthetic vaults; record device, OS, dataset size, and measurement method.
- Keep heavy image/OCR work off the main thread; use lifecycle-aware work and cancellation.
- Bound image sizes and intermediate buffers; avoid unnecessary copies and retaining document bitmaps in UI state.
- Test low storage, low memory, process death, long-running scans, and large multi-page imports.
- Do not weaken encryption, omit checks, or send content online to improve performance without a documented, reviewed decision.

## Acceptance criteria
- Critical flows have accessibility tests and a manual assistive-tech checklist.
- Performance claims include measurements and environment.
- Regressions are tracked against a stated baseline rather than subjective impressions.
