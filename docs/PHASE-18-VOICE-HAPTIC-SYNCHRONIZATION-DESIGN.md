# Phase 18 — Voice Navigation Haptic Synchronization

**Status:** Design proposal; no new haptic event model or voice controller is implemented by this document.

**Target branch:** `feat/durable-vault-migration-journal`

**Scope:** Synchronize restrained haptic cues with the Phase 17 voice-navigation state machine, preserve the Phase 14 feedback policy, support older users and low-end devices, and keep all haptic behavior optional and privacy-safe.

**Out of scope:** Continuous vibration while listening, custom notification-like vibration loops, haptic voice biometrics, communicating document contents through vibration, replacing visible labels/TalkBack/TTS, adding a vibration permission, or using stronger feedback to increase engagement time.

## Product intent

A voice interaction has fewer visual landmarks than a touch interaction. A short haptic cue can tell the user that listening started or that a command was accepted, especially when the phone is held in the hand or the user is looking away. It must remain a small confirmation layer, not a second conversation channel.

Android's current haptics guidance recommends using less vibration, favoring clear action-oriented effects, matching strength to event importance and frequency, and co-designing visual, audio, and haptic timing. It also warns against long buzzy feedback and legacy one-shot vibration patterns. [1]

Phase 18 therefore uses at most two short cues for a normal voice command: one at the transition into listening and one when the command has a recognized result. There is no repeating pulse during speech capture, no vibration during silence or processing, and no vibration that encodes private content.

## Relationship to Phase 17

Phase 17 defines these voice states:

```text
Unavailable → PermissionNeeded → Ready → Listening → Processing
                                      ↘ Cancelled / TimedOut / Error
Processing → Recognized → NeedsConfirmation → Navigating
           ↘ NoMatch / Error
```

Phase 18 adds a parallel feedback state that follows meaningful transitions rather than every recognition callback:

```text
VoiceHapticState
├── Suppressed(reason)
├── ListeningStarted
├── CommandAccepted
├── NeedsVisibleConfirmation
├── Cancelled
├── NoMatch
├── TimedOut
└── Error
```

The haptic coordinator must be driven by semantic state transitions, not by raw partial results. Recomposition, repeated partial hypotheses, or duplicate callbacks must not repeat a cue.

## Cue language

Use action-oriented `HapticFeedbackConstants` through the existing view-based feedback boundary wherever a suitable platform action exists. Android documents these constants as a consistent and accessible choice with device fallback behavior. [1]

The proposed mapping is:

- **ListeningStarted:** one light key-click-like cue at the same moment the visible state changes to “Listening… release to stop” and the optional fixed TTS phrase begins.
- **CommandAccepted:** one clear confirmation cue when a safe navigation command is accepted. It is emitted once, immediately before or alongside “Opening search” or the equivalent fixed phrase.
- **NeedsVisibleConfirmation:** no additional automatic vibration. The visible confirmation card and spoken phrase are sufficient; the user must not mistake a preview of an action for completion.
- **Cancelled / Stop:** one light end cue only when the user explicitly stops a live session. Releasing a push-to-talk control must not create a second cue if the stop transition has already emitted it.
- **NoMatch / TimedOut:** one restrained rejection cue at most, paired with visible text and a short explanation. Do not repeat it while the command list is shown.
- **Error / Unavailable / PermissionDenied:** no default vibration. Use visible status and optional fixed speech so an error does not feel like a failure of the user.

The exact Android constant should be selected during implementation from the minimum SDK/API surface and device behavior. For API levels or hardware without the desired semantic effect, fall back to the existing short `performHapticFeedback` policy or to no haptic. Do not introduce custom long waveforms solely to make the effect feel more noticeable.

## Timing contract

The coordinator must receive one ordered event stream from the voice controller:

```text
startListening
→ ListeningStarted haptic
→ optional “Listening…” speech
→ stopListening
→ process result
→ CommandAccepted haptic + visible destination state + optional speech
→ destination focus/transition
```

The cue must occur after the UI state is committed, so the user can associate the sensation with the visible listening state. A command-accepted cue must occur only after the allowlisted parser returns a safe command. It must not fire on a partial result, a low-confidence hypothesis, an unknown phrase, or a command that still needs visible confirmation.

The app must not queue haptics across screens. When the lifecycle leaves the voice surface, cancel pending events. When the vault locks, the biometric prompt opens, the screen turns off, or the app moves to the background, stop the voice session and suppress all further haptics from that session.

## Consent and settings

Add a dedicated setting under the existing elderly-friendly assistance controls:

> **Haptic voice feedback**  
> A short vibration confirms listening and safe navigation results. It never contains document information.

Offer three explicit choices:

- **Device default:** honor the Android haptic-feedback setting.
- **On:** allow the app's short semantic cues when the device supports them.
- **Off:** never emit voice-navigation haptics.

The default is **Device default**. The preference is local, reversible, and independent from voice recognition, spoken guidance, sound effects, and the Phase 14 button feedback preference. Turning haptics off must not disable voice navigation or visible/TalkBack feedback.

The setting must be usable at 2.0x font scale with a 56dp or larger control. It must explain that vibration intensity is also affected by Android system settings and device hardware. Do not ask the user to estimate sensitivity or infer a health condition.

## Accessibility behavior

Haptics never carry the only meaning of a state. Every event has a visible state label, and every important result has a text announcement. TalkBack users should hear the same concise state transition once, without the haptic coordinator duplicating or interrupting accessibility speech.

Do not fire haptics for focus traversal, every semantic announcement, every partial speech result, scrolling, or every word spoken by TTS. Haptics are not a replacement for captions, state descriptions, switch access, keyboard navigation, or visible confirmation.

The listening control exposes its role, label, and state description. Suggested text states are “Ready for one voice command”, “Listening; release to stop”, “Checking command”, “Opening search”, “No command recognized”, and “Voice navigation stopped; nothing changed”.

Users with tactile hypersensitivity, neuropathy, tremor, hearing loss, or no haptic actuator must receive the same task completion path with haptics disabled. No haptic failure may block navigation.

## Implementation boundary

Extend the existing `VaultFeedback` policy rather than calling `Vibrator` directly from composables. The coordinator should accept a semantic event and a `HapticPreference`, decide whether the event is allowed, and delegate to the platform feedback boundary.

The initial implementation should prefer `View.performHapticFeedback(...)`, which keeps feedback consistent with the Android interaction model and avoids adding a vibration permission. If a later device study justifies a predefined `VibrationEffect`, it must use API-gated predefined effects with a fallback; Android documents that predefined effects are device-tailored where supported and may fall back to a generic pattern. [2]

The coordinator must be lifecycle-scoped, main-thread safe, and idempotent per voice-session event ID. A session ID prevents a delayed result from a prior recognition session from vibrating after a new session begins. No audio, document identifier, command text, confidence score, or recognition hypothesis enters the feedback API.

## Low-end device and battery policy

The design avoids waveform composition, repeating effects, timers that wake the device, background services, and vibration loops. A normal command produces no more than two short haptic events. The coordinator should not allocate a new hardware controller on every recomposition or keep a reference to a destroyed view.

For devices without a haptic actuator, with system haptics disabled, or with a power-saving policy that rejects feedback, the app silently uses visible and spoken feedback. The UI must not display “haptic sent” as though delivery were guaranteed.

## Failure and privacy rules

No haptic pattern may encode a filename, document count, reminder date, OCR confidence, biometric result details, or error category that could reveal private state to someone holding the phone. A generic accepted cue is allowed only after a safe navigation command, not after reading a document or accessing protected content.

While the vault is locked, haptics may confirm the visible voice-control state but may not confirm document destinations or reveal whether a particular document exists. During a biometric prompt, voice haptics and TTS are suppressed. A denied permission, unavailable recognizer, or haptic service exception falls back to text and stops cleanly.

## Implementation slices

### Slice A — preference and semantic event model

Add a local `HapticVoicePreference` with Device default, On, and Off values. Add a sealed or enum event model and a pure policy function that maps voice transitions to at most one cue per event ID.

### Slice B — shared feedback coordinator

Extend the existing feedback boundary with an idempotent, lifecycle-safe voice haptic coordinator. Reuse the current short action feedback policy, prefer platform constants, and make all hardware calls best-effort.

### Slice C — Phase 17 integration

Emit events only at listening start, safe command acceptance, explicit stop, no-match, and timeout. Suppress cues for partial results, processing, permission education, lock transitions, backgrounding, and biometric prompts. Keep visible state and optional TTS synchronized with each allowed event.

### Slice D — elderly-friendly settings and semantics

Add the 56dp+ haptic setting row, clear labels, state descriptions, TalkBack traversal, switch-access support, reduced-motion-safe visuals, and visible text equivalents.

### Slice E — evidence

Add unit tests for event deduplication and preference policy. Add Compose tests for setting persistence, semantics, state transitions, and no duplicate cues. Add device tests for API 24, API 29+, API 31+, low-end hardware, system haptics off, silent mode, sound off, battery saver, no actuator, TalkBack, switch access, 2.0x font scale, reduced motion, screen-off, backgrounding, lock, and biometric prompt.

## Acceptance gates

- [ ] Haptics are opt-in or device-default, independently switchable, and never required for task completion.
- [ ] A normal voice command emits no more than two short cues: listening start and safe command acceptance.
- [ ] No repeated vibration occurs during partial recognition, silence, processing, TTS playback, scrolling, or recomposition.
- [ ] No-match, timeout, cancel, error, permission, lock, background, and biometric states have visible text equivalents.
- [ ] Voice haptics never unlock the vault, confirm deletion/export, reveal document content, or encode private information.
- [ ] Haptics are synchronized with committed visual state and optional fixed TTS, with no stale event after session cancellation.
- [ ] The implementation uses the existing feedback boundary and does not add a vibration permission or background service.
- [ ] Platform action constants are preferred; custom/predefined effects have API and hardware fallbacks. [1] [2]
- [ ] Device default follows Android haptic settings; Off suppresses app voice haptics without disabling voice navigation.
- [ ] Reduced motion, sound off, silent mode, haptics off, TalkBack, switch access, 2.0x font scale, and low-end hardware tests pass.
- [ ] Unit, Compose UI, lifecycle, and physical-device evidence is recorded before release claims.

## References

- [1]: https://developer.android.com/develop/ui/views/haptics/haptics-principles "Android haptics design principles"
- [2]: https://developer.android.com/reference/android/os/VibrationEffect "Android VibrationEffect API reference"
- [3]: https://developer.android.com/reference/android/view/HapticFeedbackConstants "Android HapticFeedbackConstants API reference"
