# Phase 14 — Calm Micro-interactions and Audio Feedback

**Status:** Design proposal; no new audio, haptic, or animation implementation is claimed by this document.

**Target branch:** `feat/durable-vault-migration-journal`

**Scope:** Compose motion tokens, interaction feedback policy, platform sound effects, haptics, reduced-motion behavior, accessibility, and deterministic tests.

**Out of scope:** Engagement manipulation, streaks, infinite feeds, notification pressure, custom background music, analytics, remote audio, new media dependencies, or any change to encryption/authentication boundaries.

## Product intent

Kaagaz Vault should feel composed and reassuring. Feedback should help a person understand what just happened, especially during import, scanning, OCR review, reminders, locking, and recovery. It should not try to keep people in the app longer than they need to be there.

“Addictive” is not a design goal for this privacy app. The safe goal is **pleasant confidence**: predictable responses, low cognitive load, gentle confirmation, and an easy stopping point. No feedback may shame, surprise, repeatedly interrupt, or create a compulsion loop.

## Design principles

1. **Meaning before delight.** Every animation, sound, or haptic must communicate a state change, affordance, or completion.
2. **One event, one cue.** Do not stack a sound, vibration, scale bounce, toast, dialog, and progress animation for the same low-value tap.
3. **Quiet by default.** Frequent actions use the lightest feedback; important actions use a clear but brief confirmation.
4. **System settings win.** Respect silent mode, notification/audio settings, touch exploration, reduced-motion preferences where available, and accessibility services.
5. **Never use feedback as pressure.** No countdowns, urgency pulses, loss framing, fake progress, red badges for ordinary work, or repeated prompts after cancellation.
6. **The UI remains understandable without sensory effects.** Text, semantics, and state styling must carry the full meaning.
7. **Sensitive data stays out of effects.** Audio labels, logs, haptic payloads, animation state, and accessibility announcements must not contain filenames, OCR text, reminder titles, identifiers, or document content.

## Current foundation

The app currently uses `playVaultClick(view)`, which delegates to the platform click sound and suppresses it in silent mode. This is a good low-dependency baseline. The next implementation should evolve it behind a small policy boundary rather than adding raw audio files or a media engine.

Existing Compose surfaces already use `AnimatedVisibility` and `animateContentSize`. The next phase should standardize their durations and decide where motion is appropriate instead of adding animation everywhere.

Android's haptics guidance recommends using predefined action-oriented feedback, keeping effects short and consistent, and avoiding buzzy or legacy one-shot vibration. Compose guidance supports `AnimatedVisibility`, `animateContentSize`, `AnimatedContent`, and value-based transitions, with explicit duration/easing choices and animation tests.

## Feedback model

Introduce a UI-only policy boundary:

```text
FeedbackController
├── sound(event)
├── haptic(event)
├── announce(state)
└── motion(token, reducedMotion)
```

The controller receives semantic events, not arbitrary strings:

```text
FeedbackEvent
├── PrimaryActionPressed
├── SecondaryActionPressed
├── ImportStarted
├── ImportCompleted
├── ImportFailed
├── ScanReady
├── ScanCaptured
├── OcrStarted
├── OcrCompleted
├── ReviewSaved
├── ReminderSaved
├── VaultLocked
├── VaultUnlocked
├── DestructiveConfirmationOpened
└── DestructiveActionCompleted
```

The controller must not know document names or inspect repository data. It should be safe to call when a device has no haptic actuator or when system audio is unavailable.

## Audio policy

### Default behavior

- Use the platform click effect for explicit button presses, as the current helper does.
- Do not request audio focus for a short UI click or confirmation note.
- Do not create a `MediaPlayer`, `SoundPool`, or raw audio asset for the default interaction layer unless profiling and accessibility review show a concrete need.
- Do not play sounds for scrolling, text editing, search keystrokes, progress updates, permission denial, or repeated retry loops.
- Never play audio while `BiometricPrompt` is visible.
- Suppress or avoid app feedback when the device is silent; do not override the user's ringer choice.
- Keep audio under a simple app preference that is off if the user disables sound feedback. The preference must be available before the first repeated interaction, not hidden behind a reward loop.

### Sound mapping

| Event | Sound | Frequency policy | Copy/semantics still required |
|---|---|---:|---|
| Primary button press | Platform click | Frequent | Yes |
| Secondary button press | Platform click | Frequent | Yes |
| Import/scan/OCR starts | None | Avoid noise | Yes |
| Import/scan/OCR succeeds | One short platform/system confirmation if enabled | Occasional | Yes |
| Save review/reminder | One short confirmation if enabled | Occasional | Yes |
| Failure/recovery required | No alarming sound by default | Rare | Yes, explicit text |
| Vault lock | None or one soft confirmation | Occasional | Yes |
| Vault unlock | One soft confirmation if enabled | Occasional | Yes |
| Destructive action confirmed | None by default | Rare | Yes |

Success feedback must be rate-limited per operation, not per recomposition. A retry must not create a rising sound pattern. A cancelled operation must end quietly.

## Haptic policy

Use platform action-oriented haptics through `LocalHapticFeedback` or the supported Android action constants. Prefer a short, clear confirmation over custom waveforms.

- Primary/secondary button press: optional subtle click haptic.
- Scan capture: one clear capture confirmation after the image is accepted.
- Save review/reminder: one clear confirmation after durable success.
- Unlock/lock: one clear confirmation only after the state transition completes.
- Failure/recovery: no buzzy or repeated vibration; communicate with text and focus.
- Scrolling, OCR progress, typing, and repeated polling: no haptic feedback.

If the platform cannot provide a suitable effect, use no haptic rather than a long one-shot vibration. Rich effects are not required for this phase.

## Motion tokens

Create named tokens so the UI does not scatter magic durations:

```text
VaultMotion
├── instant = 0ms
├── quick = 120ms
├── standard = 220ms
├── settle = 320ms
└── reduced = 0ms
```

Suggested usage:

- Pressed-state scale/tonal change: `quick`.
- Card expansion and OCR review reveal: `standard`.
- Import or save confirmation surface: `settle` once.
- Lock/unlock shell transition: `standard`, with no looping pulse.
- Error/recovery state: `standard` fade/replace; never shake continuously.

Use opacity, color, and size changes more often than translation. Avoid parallax, 3D rotation, elastic overshoot, continuous shimmer, and infinite decorative loops. Motion must not move a control away from its expected touch target.

## Reduced-motion behavior

The implementation must provide a single motion decision used by every component:

```text
motionDuration = if (reducedMotion) 0ms else VaultMotion.token
```

When reduced motion is enabled or the user chooses “Less motion” in app settings:

- Replace expansion/slide transitions with immediate visibility changes or a short opacity change only if safe.
- Disable looping or breathing animations.
- Do not replace a motion cue with sound or haptics automatically; sensory substitution must remain optional.
- Preserve state order, focus behavior, and text announcements.
- Keep touch feedback and pressed states understandable without scale animation.

The first implementation should read the system's accessibility/reduced-motion signal where reliably available and expose a local override only if the platform signal is not consistently available across the supported API range.

## Interaction choreography

### Import and scan

1. User presses **Import** or **Scan**.
2. The button gives one subtle press cue.
3. The screen moves to a stable working state with a determinate label such as “Encrypting locally…” or “Starting camera…”.
4. No looping sound, vibration, or fake percentage is used.
5. On success, show one calm confirmation and update the document list with a short `settle` transition.
6. On failure, keep the original protected data unchanged, show plain-language text, and avoid alarming audio.

### OCR review

1. User explicitly starts OCR.
2. Show a stable progress state without simulated progress percentages.
3. On completion, reveal the review affordance once; do not auto-scroll or auto-focus into sensitive text.
4. Saving corrections gives one confirmation after durable success.
5. The review text remains hidden again when the user locks the vault or leaves the sensitive screen.

### Lock and unlock

The platform biometric prompt remains authoritative. The app shell behind it stays visually locked, with no pulsing lock icon or looping animation. On successful unlock, use one short, non-essential confirmation cue and a stable crossfade. On cancellation or failure, keep the locked state visually still.

### Destructive actions

Opening a removal confirmation can use a subtle opacity transition, but it must not flash red, vibrate, or play an alarming sound. The user should be able to choose “Keep it” without penalty or repeated prompting.

## Accessibility contract

- All feedback-bearing controls retain meaningful labels, roles, state descriptions, and logical traversal order.
- Every sound/haptic cue has an equivalent visible text/state update.
- TalkBack must not announce raw OCR text because an animation or focus change occurred.
- Do not use sound pitch, vibration strength, or animation speed as the only indication of status.
- Large text must not clip or push the primary action below an inaccessible viewport.
- Keyboard, switch access, and touch exploration must reach the same actions in the same order.
- Audio feedback must never mask speech output from TalkBack; avoid announcements during platform authentication.
- Custom visual effects must remain legible in high-contrast and dark themes.

## Preference surface

Add a small, plain settings section only when the app has a settings surface. Do not create a settings screen solely to increase visits.

Recommended controls:

- **Sound feedback** — On/Off, default On only if the system allows platform click feedback.
- **Haptic feedback** — On/Off, default On only when the platform reports a suitable capability.
- **Motion** — System default / Less motion.

Each control must explain that the app still works fully when disabled. Preferences are local, non-sensitive, and must not be logged or synced.

## Implementation slices

### Slice A — policy and tokens

- Add semantic `FeedbackEvent` values and a `FeedbackController` interface.
- Move `playVaultClick` behind the controller.
- Add `VaultMotion` tokens and a reduced-motion decision.
- Add pure tests for event-to-feedback mapping and rate limits.

### Slice B — Compose integration

- Apply the controller to primary buttons, camera capture, import, OCR save, reminders, lock, and unlock.
- Keep progress and error states text-first.
- Replace ad hoc animation durations with named tokens.
- Ensure no event is emitted from recomposition or list rendering.

### Slice C — haptics and settings

- Add optional predefined haptic feedback for discrete success/press events.
- Add sound, haptic, and motion preferences only within an existing settings surface or the lock/accessibility surface.
- Persist preferences locally without adding a database migration if a simple existing preference boundary is available; otherwise defer persistence and document it.

### Slice D — accessibility and regression tests

- Add Compose semantics tests for state labels and focus order.
- Add reduced-motion tests asserting zero-duration or non-looping behavior.
- Add manual checks with TalkBack, large fonts, switch access, silent mode, sound-off, no-haptic devices, and dark theme.
- Test repeated taps, cancellation, retry, process recreation, and list recomposition to ensure no duplicate cues.

### Slice E — evidence and review

- Run debug/release build, JVM tests, lint, offline-manifest policy, and relevant instrumentation tests.
- Record device models, API levels, sound mode, accessibility settings, and observed behavior.
- Review battery, thermal, and memory impact on a constrained device.
- Do not claim “stress relief” as a medical or psychological outcome. Describe the UI as calm, predictable, and user-controlled.

## Acceptance gates

- [ ] Every feedback event has a visible/textual equivalent.
- [ ] No sound plays in silent mode, during biometric prompt, during typing, scrolling, or repeated progress updates.
- [ ] No haptic uses a long legacy one-shot vibration or a buzzy fallback.
- [ ] Feedback is emitted once per semantic event, not once per recomposition.
- [ ] Reduced motion disables looping and collapses transitions without losing meaning.
- [ ] Sound, haptic, and motion can each be disabled without breaking core flows.
- [ ] Import, scan, OCR, reminders, lock, unlock, error, cancel, and delete flows have consistent feedback rules.
- [ ] TalkBack, large font, switch access, high contrast, dark theme, silent mode, and no-haptic-device checks pass.
- [ ] No document content, OCR text, identifiers, or reminder details enter audio labels, logs, haptic payloads, or accessibility announcements.
- [ ] No analytics, network permission, media service, background audio, or new cloud dependency is introduced.
- [ ] Build, unit tests, lint, offline-manifest policy, and relevant UI tests pass.

## References

- [1]: https://developer.android.com/develop/ui/views/haptics/haptics-principles "Haptics design principles"
- [2]: https://developer.android.com/develop/ui/compose/animation/introduction "Animations in Compose"
- [3]: https://developer.android.com/develop/ui/compose/accessibility/api-defaults "Compose accessibility API defaults"
- [4]: https://m3.material.io/foundations/usability/applying-m-3-expressive "Material 3 Expressive usability guidance"
