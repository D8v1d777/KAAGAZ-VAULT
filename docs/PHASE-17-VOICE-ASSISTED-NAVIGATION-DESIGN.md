# Phase 17 — Voice-Assisted Navigation for Older Users

**Status:** Design proposal; no microphone permission, recognizer, or speech engine is implemented by this document.

**Target branch:** `feat/durable-vault-migration-journal`

**Scope:** User-initiated voice navigation, short spoken guidance, elderly-friendly command vocabulary, privacy boundaries, accessibility semantics, lifecycle behavior, and test evidence.

**Out of scope:** Always-listening mode, background microphone use, voice biometrics, voice-based vault unlock, dictating OCR or document content, voice deletion, cloud search, automatic reading of sensitive documents, or replacing touch/keyboard navigation.

## Product intent

Voice should help a person reach a known part of the vault when touch is tiring, imprecise, or difficult to see. It should behave like a temporary navigation aid, not like a companion that tries to keep the user talking.

The voice feature is opt-in and push-to-talk. The app never listens while locked, in the background, during the biometric prompt, or after a command has completed. Every voice destination remains available through large visible controls and standard Android accessibility services.

## Platform boundary

Android's `SpeechRecognizer` requires `RECORD_AUDIO` permission. The platform documentation warns that the implementation may stream audio to remote services and is not intended for continuous recognition; it also requires recognizers to be destroyed when no longer needed and calls to be made from the main thread. On Android 12 and later, the app can check whether on-device recognition is available and use it when supported. [1]

Phase 17 therefore uses a single short recognition session after an explicit user action. If on-device recognition is unavailable, the UI must clearly say that the system voice service may process the short command according to the user's Android settings, and it must offer touch/keyboard navigation instead. The app must never imply that all recognition is offline.

Fixed spoken guidance can use Android `TextToSpeech`. Speech is asynchronous, should use `QUEUE_FLUSH` for a current instruction, and must be stopped and shut down with the owning lifecycle. A progress listener can be used for completion and failure handling. [2]

## Entry point and permission flow

Add one visible control in the simplified-mode settings or home-screen assistance area:

> **Voice navigation**  
> Press and hold, then say a destination such as “Open search”.

The first tap explains microphone use before requesting permission:

> “Kaagaz uses the microphone only while you hold the voice button. It listens for one navigation command, then stops. It does not unlock the vault or read documents.”

Use `RECORD_AUDIO` only after the user chooses to continue. If the user declines, hide no core feature and show “Voice navigation is off. You can use the large buttons or TalkBack instead.” Do not request permission on app launch or at random points in the flow.

A press-and-hold interaction is preferred for a first implementation because it gives the user a clear listening boundary. A tap-to-start and tap-to-stop alternative may be offered for users with limited dexterity, but it must show a large, explicit **Stop listening** control and an elapsed listening state. No mode may listen continuously.

## Supported vocabulary

The first release should recognize a small, deterministic navigation grammar rather than arbitrary natural-language commands. Examples:

- “Open search” → focus the labeled search field.
- “Show my documents” → scroll to the documents heading.
- “Open reminders” → focus the reminders heading or Add reminder action.
- “Import a document” → open the document picker after a visible confirmation.
- “Scan a document” → open the camera screen after a visible confirmation.
- “Open settings” → open settings.
- “Go back” → perform the same safe back action as the visible system/back control.
- “Help” → show the command help dialog and speak its short summary.
- “Stop” or release the push-to-talk control → stop recognition without changing vault state.

Use a visible **Voice commands** help surface with the same vocabulary. Recognized text is treated as a transient command and should not be persisted, logged, synced, or inserted into search.

## Commands that require confirmation or are disallowed

Navigation can be performed immediately when the destination is unambiguous. Actions that open a consequential flow require a visible confirmation before proceeding:

- “Import a document” → “Open the document picker?”
- “Scan a document” → “Open the camera?”
- “Open settings” → may open directly because it is reversible.

Voice must not delete documents, remove reminders, unlock the vault, change biometric policy, export data, or confirm a destructive action. A voice command can open the relevant confirmation screen, but the final destructive decision stays with a visible, accessible control. Voice cannot be used as a second factor or as a substitute for `BiometricPrompt`.

The first release does not use voice to dictate search text, reminder titles, OCR corrections, or document metadata. This avoids accidentally sending sensitive words through a remote recognition service and keeps navigation vocabulary predictable.

## Locked-vault behavior

When the vault is locked, the voice control may explain the lock and open the normal visible unlock surface, but it must not recognize arbitrary commands or expose document destinations. It must not speak document names, counts, reminder titles, OCR text, search results, or recovery details.

During `BiometricPrompt`, pause or disable the voice control and do not play Text-to-Speech. On cancellation or failure, return to the locked state with a visible message. Voice must never bypass, weaken, or simulate biometric authentication.

## Interaction state model

```text
VoiceUiState
├── Unavailable(reason)
├── PermissionNeeded
├── Ready
├── Listening(elapsedMs)
├── Processing
├── Recognized(command, confidence)
├── NeedsConfirmation(action)
├── Navigating(destination)
├── Cancelled
├── NoMatch
├── TimedOut
└── Error(reason)
```

Only one state may own the recognizer. A new start request must stop and destroy the prior session before creating another. `SpeechRecognizer` calls occur on the main thread, while command mapping remains a small pure function that can be unit tested.

Recognition time should be short and bounded. The default should stop after a brief pause or a conservative maximum duration, then announce “I did not hear a navigation command” without repeatedly reopening the microphone. A no-match result should offer the visible command list, not a loop of prompts.

## Spoken guidance policy

Use short, calm, fixed phrases. Examples include:

- “Listening for one navigation command.”
- “Opening search.”
- “I did not recognize that. You can say open search, open reminders, scan a document, or help.”
- “Voice navigation stopped. Nothing was changed.”
- “Voice navigation is unavailable. Use the large buttons or TalkBack.”

Speech guidance must be optional and independently switchable from the voice-navigation preference. It must respect the device's speech/accessibility settings and should not interrupt TalkBack. Use `QUEUE_FLUSH` for a current instruction, never queue a backlog of app messages, and call `stop()`/`shutdown()` when the owning lifecycle ends. [2]

Do not speak filenames, OCR content, reminder titles, identifiers, or raw recognition hypotheses. Do not say “great job”, “don’t leave”, or other pressure language. The user must be able to stop after one successful navigation.

## Elderly-friendly presentation

In simplified mode, the voice control uses at least a 64dp height, a 20sp label, a microphone icon with a meaningful description, a visible pressed/listening state, and a large **Stop listening** action. It must remain usable at 2.0x font scale and with high-contrast display settings.

The visual state must not rely on color or animation alone. Show text such as “Listening… release to stop” and a clear microphone state icon. A short predefined haptic on start and stop is optional; no repeated vibration is used during recognition. The app's existing reduced-motion policy disables listening pulses and replaces them with a static state change.

TalkBack users should not need this feature to navigate. The voice control must have a role, label, state description, and logical traversal position. When a command moves focus, announce the destination once and return focus to the destination's heading or primary control.

## Privacy and data handling

- Microphone access exists only during the explicit recognition session.
- No raw audio, partial hypotheses, confidence values, or recognized commands are persisted.
- Do not log command text, even in debug logs that may reach bug reports.
- Do not send document content or document-derived search terms to a recognition service.
- Stop recognition on lock, background, screen-off, configuration change, process death, permission revocation, or biometric prompt start.
- Destroy the recognizer on the main thread when the session ends and release Text-to-Speech resources with the owner lifecycle. [1] [2]
- Do not add background services, wake locks, notifications, or network calls for voice.
- The microphone permission explanation must be localized and visible before the system prompt.

## Implementation slices

### Slice A — command model and mapping

Add a sealed navigation command model and a pure parser for the small supported vocabulary. Map only exact or safely normalized phrases. Unknown, ambiguous, and low-confidence results must become `NoMatch`; they must never fall through to a destructive action.

### Slice B — recognizer controller

Create an activity/lifecycle-scoped controller that owns one `SpeechRecognizer` session at a time. Check recognition availability, check on-device availability on API 31+, request permission only after the explanation, set the listener before starting, bound the session, and destroy the recognizer on every terminal path. [1]

### Slice C — voice UI and settings

Add the large push-to-talk/stop control, visible listening state, command help, permission education, unavailable/offline messaging, and optional spoken guidance setting. Reuse Phase 15 simplified-mode tokens and preserve the touch/keyboard/TalkBack path.

### Slice D — navigation integration

Connect only reversible destinations first: search, documents, reminders, settings, help, and back. Add explicit visual confirmations before opening import or camera. Keep document content and destructive actions outside voice control.

### Slice E — speech guidance

Add a lifecycle-owned Text-to-Speech coordinator for fixed status phrases only. Use current system locale and user voice settings, flush stale speech, avoid TalkBack interference, and shut down cleanly. [2]

### Slice F — evidence

Test permission grant/deny/revoke, on-device available/unavailable, recognition unavailable, no-match, timeout, partial result suppression, repeated start, backgrounding, screen-off, rotation, process death, lock transition, biometric prompt, TalkBack, switch access, 2.0x font scale, reduced motion, sound/haptics off, and privacy logs.

## Acceptance gates

- [ ] Voice navigation is opt-in, push-to-talk or explicit start/stop, and never always-listening.
- [ ] `RECORD_AUDIO` is requested only after a plain-language explanation.
- [ ] On-device availability is checked on API 31+; the UI does not claim offline recognition when it is unavailable. [1]
- [ ] One recognizer session is owned at a time and destroyed on every terminal/lifecycle path. [1]
- [ ] No raw audio, hypotheses, confidence scores, command text, filenames, OCR text, identifiers, or reminder details are logged or persisted.
- [ ] Voice cannot unlock the vault, delete data, export data, alter security policy, or complete destructive confirmations.
- [ ] Navigation commands are a small allowlist with safe no-match behavior.
- [ ] Import and camera destinations require a visible confirmation.
- [ ] Voice remains fully optional; visible controls, keyboard navigation, and TalkBack provide complete alternatives.
- [ ] Spoken guidance is fixed-phrase, queue-flushed, lifecycle-owned, and shut down cleanly. [2]
- [ ] Simplified mode provides at least 64dp voice controls, 20sp labels, clear state text, and 2.0x font-scale support.
- [ ] Reduced motion, sound-off, haptics-off, high-contrast display, TalkBack, and switch-access checks pass.
- [ ] No background service, wake lock, notification, network feature, analytics, encryption change, or authentication change is introduced.
- [ ] Build, lint, unit tests, Compose UI tests, permission tests, and physical-device evidence are recorded before release claims.

## References

- [1]: https://developer.android.com/reference/android/speech/SpeechRecognizer "Android SpeechRecognizer API reference"
- [2]: https://developer.android.com/reference/android/speech/tts/TextToSpeech "Android TextToSpeech API reference"
- [3]: https://developer.android.com/guide/topics/ui/accessibility/apps "Android accessibility app guidance"
