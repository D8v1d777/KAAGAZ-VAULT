# Phase 15 — Elderly-Friendly Simplified Mode

**Status:** UI slice implemented; device and instrumentation validation remain pending.

**Target branch:** `feat/durable-vault-migration-journal`

**Scope:** User-controlled simplified mode, large typography, high-contrast roles, enlarged controls, simplified information hierarchy, accessibility semantics, and device-test acceptance.

**Out of scope:** Age inference, profiling, medical claims, removing user choice, changing encryption/authentication, changing document data, or using urgency/notifications to drive engagement.

## Product intent

Kaagaz Vault should remain comfortable for people with reduced vision, reduced dexterity, tremor, slower reading speed, or unfamiliarity with dense mobile interfaces. The mode should feel respectful and capable, not childish or patronizing.

The mode is a **user preference**, not an age label. It never guesses a user's age and never silently changes the interface based on behavior or device telemetry. A person can enable or disable it at any time without losing documents, settings, or task progress.

The goal is confident completion and easy stopping—not longer sessions.

## Important unit decision

Android touch targets use **dp**, while typography uses **sp**. The request for “48sp+ touch targets” is therefore implemented as:

- **48dp absolute minimum** for every interactive target, matching Android/Material accessibility guidance.
- **56dp minimum** for ordinary simplified-mode buttons and list actions.
- **64dp minimum** for the primary Import, Scan, Capture, Unlock, Save, and Lock actions where layout permits.
- Typography measured in sp, with **48sp+ reserved for the main display/hero heading**, not used as a button touch-target unit.

This avoids creating physically oversized or clipped controls while preserving large text and reliable touch areas.

## Mode activation and persistence

Add a plain setting:

> **Simplified view**  
> Larger text, clearer actions, and more space between controls. You can change this anytime.

Requirements:

- Default: off, preserving the current interface for existing users.
- Persist locally through the existing lightweight preferences boundary; do not add a database migration for a UI preference.
- Do not show a one-time age question or infer a need from biometrics, camera behavior, reading speed, or failed taps.
- The setting itself must be reachable with large controls and must explain that the vault works fully in either mode.
- If the preference cannot be read, fall back to the standard UI without blocking vault access.
- No analytics or telemetry is attached to enable/disable actions.

## Typography tokens

Use a dedicated `VaultTypography.simplified` overlay rather than scattering literal sizes through screens. Preserve the user's system font scale on top of these minimums; never cap or override accessibility font scaling.

| Semantic role | Simplified minimum | Guidance |
|---|---:|---|
| Hero/display heading | 48sp | Use once per primary screen; allow wrapping |
| Screen heading | 32sp | Bold or semibold; do not truncate |
| Section heading | 24sp | Clear hierarchy; 2-line wrap allowed |
| Body / document title | 20sp | Prefer 1.45–1.6 line height |
| Button label | 18sp | Sentence case, plain verb |
| Supporting/status text | 18sp | Never use color alone for state |
| Small metadata | 16sp | Do not use below 16sp in simplified mode |
| Legal/license text | 16sp minimum | Scrollable, selectable where appropriate |

Typography rules:

- Use `sp`, not `dp`, for text sizes.
- Use `maxLines` only when truncation cannot hide meaning; prefer wrapping.
- Do not use all-caps for primary instructions. Existing eyebrow labels may remain visually uppercase only if their accessible text is normal sentence case.
- Avoid thin weights, tight letter spacing, italic-only emphasis, and decorative type.
- Keep line length comfortable; split dense cards into short labeled blocks.
- Preserve the user’s font scale at 1.0x, 1.3x, 1.5x, and 2.0x in tests.

## High-contrast color strategy

Simplified mode uses the active Material You scheme on Android 12+ only when the standard Material role pairings remain valid. It must not manually tint or reduce contrast in dynamic roles.

Add explicit high-contrast light and dark schemes for pre-Android 12 and as a safe fallback:

- Body and supporting text target **7:1** against the page/surface background wherever practical.
- Normal text must remain at least **4.5:1**.
- Large text and icons must remain at least **3:1**, with 4.5:1 preferred.
- Primary button text uses `onPrimary` on `primary`.
- Container text uses the matching `onPrimaryContainer`, `onSecondaryContainer`, or `onTertiaryContainer` role.
- Error, reviewed, needs-review, and protected states use text plus icon/label; never color alone.
- Disabled controls remain visually distinguishable from the background but are not relied on for task-critical information.
- Ambient particles and color drift are reduced to a static, near-zero-alpha backdrop in simplified mode; content contrast wins over atmosphere.

The high-contrast palette must be tested after alpha compositing with the ambient backdrop, not only as raw hex pairs.

## Layout and control tokens

```text
SimplifiedLayout
├── screenHorizontalPadding = 24dp
├── sectionSpacing = 24dp
├── cardPadding = 24dp
├── actionGap = 16dp
├── controlMinHeight = 56dp
├── primaryControlMinHeight = 64dp
├── iconTouchTarget = 56dp
├── cornerRadius = 20dp
└── dividerThickness = 1dp
```

Rules:

- Maintain a single primary action per screen or card.
- Stack Import and Scan vertically in simplified mode; do not force side-by-side buttons on a narrow phone.
- Stack Search and Show all vertically when text scaling or width requires it.
- Give dialog actions at least 56dp height and enough horizontal padding for 18sp labels.
- Use generous spacing instead of large decoration to separate tasks.
- Keep the document title, type/size metadata, review state, and action controls in a predictable order.
- Keep destructive actions visually secondary and require a clear confirmation with equally large “Keep it” and “Remove” choices.
- Do not hide advanced actions permanently; place them behind a clearly labeled “More actions” control if density requires it.

## Simplified home screen structure

```text
Screen heading: “Your private shelf”
Supporting privacy statement
Primary: Import a document
Secondary: Scan a document
Search: labeled field + Search
Reminders: summary + Add reminder
Documents: one card per document
Each card: name → type/size → review state → primary next action → secondary actions
Settings: Simplified view, Motion, Sound feedback, Haptic feedback
```

The current calm visual language may remain, but decorative elements must not outrank the heading, status, or primary action. The simplified mode should remove non-essential eyebrow labels, reduce ambient movement, and avoid presenting more than one new concept per card.

## Interaction and feedback

- Keep Phase 14 platform click sound and brief haptic behavior, but never use stronger feedback to compensate for small or ambiguous controls.
- Every audio/haptic cue remains optional and has visible text/state equivalence.
- Reduced motion is automatically honored; simplified mode additionally defaults to static ambient background behavior.
- Never auto-focus into OCR content, auto-scroll after a save, or repeatedly prompt after cancellation.
- After a dialog closes, return focus to the initiating control or the next meaningful heading.
- Announce state changes such as “Document saved privately” once; do not announce raw filenames or OCR text unless the user explicitly focuses that content.

## Accessibility semantics

- Every button exposes a verb and object, such as “Import a document”, “Scan a document”, “Save corrections”, or “Keep document”.
- Use headings and traversal groups so TalkBack follows the visual hierarchy.
- Add `stateDescription` for reviewed/unreviewed and enabled/disabled states where the component semantics do not already provide it.
- Do not merge a document card into one giant semantic node; allow the user to reach title, status, and actions independently.
- Decorative ambient canvas remains excluded from accessibility semantics.
- Search, OCR review, camera preview, date/time selection, and recovery surfaces each need explicit labels and error announcements.
- Support keyboard, switch access, touch exploration, and large cursor/high-contrast system settings.

## Implementation slices

### Slice A — mode model and preferences

- Add `SimplifiedMode` to the UI settings model.
- Add a local preference with safe default and corruption fallback.
- Add a settings row with semantics, explanation, and 56dp/18sp interaction sizing.

### Slice B — typography and spacing tokens

- Add `VaultTypography.simplified` and `SimplifiedLayout` tokens.
- Apply tokens to the home, search, reminder, document, camera, OCR review, lock, and recovery surfaces.
- Remove hard-coded small text below 16sp in simplified mode.
- Preserve system font scaling without clipping.

### Slice C — high-contrast roles

- Add explicit high-contrast light/dark fallback schemes.
- Keep Android 12+ Material You role extraction intact when it passes the role policy.
- Add a contrast-test helper for the actual role pairs and ambient-composited surfaces.
- Test error, disabled, focus, outline, and pressed states separately.

### Slice D — layout adaptation

- Stack primary/secondary actions vertically on narrow screens.
- Enlarge all controls to 56dp, with 64dp primary actions.
- Add semantic headings and stable focus order.
- Ensure document/reminder rows do not create overlapping expanded touch targets.

### Slice E — evidence

- Add Compose UI tests for semantics, touch-target bounds, focus order, font-scale reflow, and mode persistence.
- Test API 24 fallback, API 31+ dynamic palettes, light/dark mode, 2.0x font scale, TalkBack, switch access, keyboard navigation, reduced motion, sound off, haptics off, and high-contrast display.
- Record screenshots and contrast measurements from at least one small phone and one larger device.

## Acceptance gates

- [ ] Simplified view is user-controlled, reversible, local, and does not infer age or ability.
- [ ] Every interactive control is at least 48dp; standard simplified controls are at least 56dp; primary actions are at least 64dp where layout permits.
- [ ] Simplified typography uses at least 16sp metadata, 18sp actions/supporting text, 20sp body, 24sp section headings, and 32sp screen headings; 48sp hero text is allowed to wrap.
- [ ] Normal text meets 4.5:1; body/supporting text targets 7:1; large text/icons meet at least 3:1.
- [ ] Dynamic Android 12+ roles are not post-processed into lower-contrast combinations.
- [ ] Fallback high-contrast light/dark palettes pass contrast tests after ambient compositing.
- [ ] Import/Scan, Search/Show all, reminder actions, OCR actions, camera actions, lock/recovery actions, and destructive dialogs remain understandable at 2.0x font scale.
- [ ] TalkBack, switch access, keyboard navigation, touch exploration, and reduced-motion tests pass.
- [ ] No important state relies on color, audio, haptics, animation, or font weight alone.
- [ ] No document data, age inference, analytics, network behavior, encryption, or authentication behavior changes.
- [ ] Build, lint, unit tests, Compose UI tests, and physical-device evidence are recorded before release claims.

## References

- [Android Compose accessibility API defaults](https://developer.android.com/develop/ui/compose/accessibility/api-defaults)
- [Android Material 3 theming and accessibility](https://developer.android.com/develop/ui/compose/designsystems/material3)
- [WCAG 2.2 Contrast (Minimum)](https://www.w3.org/WAI/WCAG22/Understanding/contrast-minimum.html)
- [Material accessible design overview](https://m3.material.io/foundations/accessible-design/overview)
