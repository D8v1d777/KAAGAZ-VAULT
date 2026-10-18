# Kaagaz Vault — Evidence Review: Human-Centered Security, Accessibility and Product Psychology

**Purpose:** A separate evidence-to-design document. It distinguishes published guidance from product hypotheses that still need user testing. It does not treat “psychology” as a trick for persuading users to trust the product.

## Executive synthesis

Kaagaz Vault handles documents that may contain highly sensitive information. Product success depends on users understanding the trust boundary, completing routine tasks without anxiety, and recovering access without being misled. Visual polish is useful only when it supports legibility, predictability, error prevention and user agency.

The target age range of 20–90 is not one uniform persona. Design for diverse vision, motor ability, attention, memory, digital literacy, device capability and assistive technology. Validate with people rather than inferring capability from age.

## Evidence and design implications

### 1. Older-user accessibility overlaps with established accessibility standards
**Source:** W3C WAI, “Older Users and Web Accessibility” and “Developing Websites for Older People.”

The W3C describes age-related changes that can affect contrast sensitivity, near focus, dexterity, short-term memory, concentration and navigation. It explains that WCAG techniques address many of these needs, including resizing text, sufficient contrast, not using colour alone, descriptive labels and structured pages.

**Design decisions**
- Use semantic headings, clear page regions and descriptive button labels.
- Support 200% text zoom and responsive reflow without losing actions.
- Meet WCAG 2.2 AA contrast and focus requirements; use visible keyboard focus and generous touch targets.
- Convey states with text/icons as well as colour.
- Keep primary navigation stable and task flows predictable.
- Provide text-size controls and a low-distraction interface option; never force a “senior mode” based only on age.
- Test keyboard-only navigation, screen readers, high zoom, mobile touch and reduced-motion preferences.

**Sources**
- https://www.w3.org/WAI/older-users/
- https://www.w3.org/WAI/older-users/developing/
- https://www.w3.org/TR/wcag/

### 2. Security fatigue is a real design risk
**Source:** NIST-hosted study, “Security Fatigue” (2016), qualitative interviews with 40 participants.

The study reports that security fatigue can contribute to resignation, perceived lack of control, risk minimization and decision avoidance. This is evidence of a usability problem, not proof that any single UI intervention will solve it.

**Design decisions**
- Ask for security decisions at the point they matter, not through repeated generic warnings.
- Explain consequences in short, specific language.
- Use safe defaults, while making irreversible trade-offs explicit.
- Do not add meaningless “Are you sure?” dialogs to routine actions.
- Use one strong confirmation for permanent deletion and key loss; distinguish it from reversible actions.
- Avoid alarmist red banners, fake urgency, shame language or manipulative countdowns.
- Make settings easy to find so users feel agency instead of being trapped by hidden rules.

**Source:** https://csrc.nist.gov/pubs/journal/2016/09/security-fatigue/final

### 3. Human-centered cybersecurity should be designed into the workflow
**Source:** NIST, “Advancing Human-Centered Cybersecurity: Challenges and Pathways Forward” (published 15 May 2026).

This recent NIST publication summarizes workshop themes intended to improve cybersecurity outcomes through a human-centered approach.

**Design decisions**
- Treat setup, authentication, recovery, error handling and deletion as core product flows—not secondary settings pages.
- Measure security outcomes and usability together.
- Observe where users hesitate, misinterpret states, abandon recovery or choose unsafe workarounds.
- Keep a clear boundary between evidence, design assumptions and measured outcomes.

**Source:** https://www.nist.gov/publications/advancing-human-centered-cybersecurity-challenges-and-pathways-forward

### 4. Encryption UX must be honest about the consequences
**Sources:** OWASP Cryptographic Storage and Key Management Cheat Sheets; W3C cryptography guidance; RFC 9106.

Encryption design depends on threat model, key lifecycle, secure primitives, key separation and recovery. Browser cryptographic APIs are low-level building blocks, not a complete secure system. Argon2id is a memory-hard password-based derivation option described in RFC 9106; runtime and parameter selection must be validated on supported devices.

**Design decisions**
- Explain that the service cannot reset a lost vault key when it cannot decrypt content.
- Separate account sign-in from the vault passphrase.
- Require recovery setup before a user stores valuable documents.
- Offer a guided recovery-key save and verification flow; never upload or email the plaintext secret.
- Never claim “unbreakable”, “100% secure”, “zero risk” or “anonymous” without a narrowly defined, substantiated claim.
- Make the metadata boundary explicit: encrypted content does not necessarily hide file size, timing, IP address or account linkage.

**Sources**
- https://cheatsheetseries.owasp.org/cheatsheets/Cryptographic_Storage_Cheat_Sheet.html
- https://cheatsheetseries.owasp.org/cheatsheets/Key_Management_Cheat_Sheet.html
- https://www.w3.org/TR/security-guidelines-cryptography/
- https://www.rfc-editor.org/rfc/rfc9106

## Ethical application of psychology

These are product hypotheses inspired by usability and security principles; they are not universal psychological laws and should be validated.

| Principle | Proposed interface choice | Hypothesis to test |
|---|---|---|
| Cognitive load | One primary action per step; progressive disclosure for advanced options | Users make fewer setup errors and complete tasks faster |
| Recognition over recall | Visible file categories, recent actions, clear status labels and remembered non-secret preferences | Users rely less on memory and make fewer navigation errors |
| Autonomy and control | Clear lock, export, recovery and privacy settings | Users report greater understanding and control |
| Error prevention | Validation before upload; destructive-action review; undo for reversible actions | Fewer avoidable failures and accidental deletions |
| Honest feedback | “Encrypting”, “Uploading encrypted file”, “Encrypted and saved” | Users understand actual state instead of guessing |
| Calm visual hierarchy | Muted sage/neutral surfaces with high-contrast text and a restrained warning colour | Lower perceived clutter without reducing clarity |
| Progressive learning | Plain-language explanation first, technical detail available on demand | Novice users understand key concepts without blocking expert users |
| Trust through transparency | State what the service can and cannot access; explain recovery limits | Users correctly understand the threat model rather than merely reporting higher trust |

**Colour note:** Do not rely on claims that a particular colour universally produces calm or trust. Use a restrained palette as a visual hierarchy, preserve contrast and test interpretation with users. The palette is a design hypothesis, not a substitute for evidence.

## Product additions recommended

### Security and privacy
- A plain-language “How your vault protects files” page.
- Recovery readiness status that explains what is configured without exposing secrets.
- Session/device list and revoke-device capability when multi-device support is introduced.
- Clear encryption and sync status per operation.
- Data minimization and privacy-preserving telemetry; never collect document contents, names, keys or recovery secrets.
- Export and deletion flows with clear retention and backup limits.

### Comfort and accessibility
- Adjustable text size and system-aware contrast.
- Keyboard-accessible controls, visible focus and comfortable touch targets.
- Reduced-motion support, clear loading states and no motion-only feedback.
- Simple and detailed explanations for security concepts, without hiding important consequences.
- Undo for reversible changes; explicit confirmation for irreversible deletion.
- Helpful empty states that explain the next action without guilt or pressure.

### Reliability and support
- Actionable errors: what happened, whether data is safe, and the next safe step.
- Retry handling that cannot accidentally reuse an encryption nonce.
- Status that distinguishes local encryption from server upload confirmation.
- A support guide that explicitly states staff cannot decrypt user files or restore a lost key.
- Incident and release communication that does not exaggerate certainty.

## Research plan and measurable success criteria

These thresholds are initial product targets, not established research findings.

1. **Comprehension:** At least 90% of participants correctly explain who can read file contents and what happens if both the vault passphrase and recovery secret are lost.
2. **Recovery:** At least 90% complete the recovery setup without facilitator intervention; investigate every failure and accessibility barrier.
3. **Task completion:** At least 90% of participants successfully upload, find, download and delete a test document.
4. **Error recovery:** Participants can identify safe next steps for failed upload, wrong passphrase, revoked device and unsupported file.
5. **Accessibility:** WCAG 2.2 AA automated checks plus manual keyboard, screen-reader, zoom/reflow, touch and focus testing.
6. **Security:** Independent cryptographic review, authorization allow/deny tests, metadata-leakage review and destructive recovery drills.
7. **Trust calibration:** Ask participants what the service can see and what the provider can recover. Do not optimize for “trust score” alone; a user who accurately understands limitations is more valuable than one who feels reassured by misleading UI.

Recruit a diverse sample across ages 20–90, digital literacy, visual/motor access needs, devices and connection quality. Use informed consent, avoid collecting real sensitive documents, and report findings by observed need rather than age stereotypes.

## Evidence limitations

- The W3C older-user guidance is useful for accessibility but is not a user study of Kaagaz Vault.
- The NIST security-fatigue publication is qualitative and relatively small; it does not establish universal effects.
- The 2026 NIST human-centered cybersecurity publication is a workshop synthesis, not a controlled product experiment.
- Colour and interface recommendations above are hypotheses until tested in usability sessions.
- E2EE architecture requires specialist review; documentation alone does not demonstrate security.

## References
1. W3C WAI — Older Users and Web Accessibility: https://www.w3.org/WAI/older-users/
2. W3C WAI — Developing Websites for Older People: https://www.w3.org/WAI/older-users/developing/
3. W3C — WCAG 2.2: https://www.w3.org/TR/wcag/
4. NIST CSRC — Security Fatigue: https://csrc.nist.gov/pubs/journal/2016/09/security-fatigue/final
5. NIST — Advancing Human-Centered Cybersecurity (2026): https://www.nist.gov/publications/advancing-human-centered-cybersecurity-challenges-and-pathways-forward
6. OWASP — Cryptographic Storage: https://cheatsheetseries.owasp.org/cheatsheets/Cryptographic_Storage_Cheat_Sheet.html
7. OWASP — Key Management: https://cheatsheetseries.owasp.org/cheatsheets/Key_Management_Cheat_Sheet.html
8. W3C — Cryptography usage in Web Standards: https://www.w3.org/TR/security-guidelines-cryptography/
9. RFC Editor — RFC 9106 Argon2: https://www.rfc-editor.org/rfc/rfc9106
10. MDN — SubtleCrypto: https://developer.mozilla.org/en-US/docs/Web/API/SubtleCrypto
