<div align="center">

<img src="assets/kaagaz-blue-eye-lock.jpg" alt="Kaagaz Vault privacy lock mark" width="150" />

# Kaagaz Vault / Product Overview

### Important papers, held close.

An offline-first Android document vault for people who want useful OCR and fast retrieval without making their private documents part of a cloud pipeline.

</div>

<p align="center">
  <img src="assets/kaagaz-hero-banner.jpg" alt="Original blue-lit guardian surrounded by protected paper fragments" width="100%" />
</p>

<div align="center">

[Back to README](../README.md) · [Engineering progress](engineering/progress.md) · [Quality gates](engineering/quality-gates.md)

</div>

---

## The feeling we are building

Most document apps feel like filing cabinets. Kaagaz Vault is aiming for something more reassuring: a private shelf that is easy to return to when life is already noisy.

The visual language is built around deep navy, electric blue, paper-white surfaces, soft motion, generous spacing, and clear language. The guardian imagery is an original visual metaphor for protection, not a claim that security is magical. The real trust comes from boundaries that can be inspected in code.

<div align="center">

| **Quiet** | **Local** | **Human-reviewed** |
| --- | --- | --- |
| Calm surfaces, restrained feedback, reduced-motion support | Capture, OCR, search, and storage designed around the device | OCR remains editable evidence; the user stays in the loop |

</div>

## The product loop

```text
        CAPTURE              UNDERSTAND              KEEP CLOSE
   PDF · image · camera   →   OCR · review · edit   →   encrypted local shelf
          ↑                                                  ↓
          └────────────── search · reminders · return ──────┘
```

### 01 · Capture without a cloud hand-off

The user chooses a PDF or image through Android’s document picker, or opens the in-app camera flow. Imported bytes are bounded and moved into the encrypted local path. The app does not need broad storage access for the basic flow.

### 02 · Let OCR help, never silently decide

Offline Tesseract models support English, Hindi, and Telugu. Extracted text is shown as reviewable evidence. Corrections and review state are explicit, because a document that looks readable to a machine can still be wrong in a consequential way.

### 03 · Return to a shelf, not a feed

The home surface emphasizes the next useful action: import, scan, search, review, or revisit a reminder. The design avoids engagement tricks and notification pressure. It should feel dependable for a 20-year-old student and legible for a 90-year-old family member.

## The visual system

<p align="center">
  <img src="assets/kaagaz-sorcerer-card.jpg" alt="Original portrait of a white-haired blindfolded guardian holding a secured paper vault" width="42%" />
  &nbsp;&nbsp;
  <img src="assets/kaagaz-blue-eye-lock.jpg" alt="Original glowing blue eye and lock privacy mark" width="42%" />
</p>

| Layer | Direction |
| --- | --- |
| **Base** | Midnight navy and blue-black surfaces that keep the app quiet rather than sterile. |
| **Signal** | Electric blue for focus, trust cues, active controls, and the privacy mark. |
| **Paper** | Warm white and pale surfaces for readable document cards and high-contrast text. |
| **Motion** | Short, purposeful transitions with reduced-motion fallbacks. No decorative motion should compete with a document action. |
| **Sound and haptics** | Optional, restrained interaction notes that respect device settings and avoid turning privacy work into a noisy game loop. |
| **Type** | Material 3 hierarchy with an elderly-friendly simplified mode, larger controls, and persistent labels. |

## What the repository already contains

- Encrypted document payload and metadata paths backed by Android Keystore and AES-GCM.
- Private app storage with opaque document identifiers, bounded input, atomic pending-file writes, and interrupted-update recovery.
- Local PDF/image import and CameraX capture.
- Offline OCR for English, Hindi, and Telugu with human review and correction.
- SQLCipher-backed metadata/search paths with an in-memory fallback when encrypted search is unavailable.
- Reminder UI and local persistence boundaries.
- Dynamic Material You color extraction with calm fallbacks.
- Privacy protection for recent-app previews.
- Simplified mode for larger type, clearer hierarchy, and 56dp/64dp action targets.
- Local encrypted transfer of allowlisted accessibility settings, with code entry and preview-before-apply. Vault documents are explicitly outside that transfer surface.

## What is still intentionally unfinished

The repository is a serious implementation in progress, not a release-approved security product. Remaining gates include physical-device validation, the complete biometric/auth-bound vault lock, recovery and rotation evidence, secure leakage review, release licensing, and broader end-to-end testing.

The FOSS core must stay offline. No analytics, ads, cloud OCR, hidden network calls, or cloud AI should be added as a shortcut. Any future export or sync feature must keep explicit user intent, encryption, bounded data, and a documented review surface.

## Start reading by intent

- **I want the product story:** this page and the [README](../README.md).
- **I want current evidence:** [engineering progress](engineering/progress.md).
- **I want the acceptance boundary:** [quality gates](engineering/quality-gates.md).
- **I want the security decisions:** [engineering decisions](engineering/decisions/).
- **I want the active phase plans:** [phase designs](./).
- **I want to contribute code:** [AGENTS.md](../AGENTS.md) first, then the focused skill under `.agents/skills/`.

## Artwork note

The visual assets on this page are original generated artwork created for Kaagaz Vault in a blue, blindfolded-sorcerer-inspired direction. They are not official *Jujutsu Kaisen* artwork, do not reproduce a canon frame, and do not imply affiliation with or endorsement by the rights holders.

<div align="center">

**A private shelf for the papers that matter.**

</div>
