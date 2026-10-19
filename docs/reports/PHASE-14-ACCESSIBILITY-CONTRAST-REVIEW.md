# Phase 14 — UI/UX Accessibility and Contrast Review

**Review date:** 2026-10-19

**Scope:** Static light/dark calm palettes, ambient backdrop interaction, Compose accessibility semantics, touch targets, motion behavior, and elderly-user readability risks through approximately age 90.

**Status:** Static palette pass; dynamic-color device validation remains required.

## Executive summary

The explicit LightVaultColors and DarkVaultColors role pairings pass WCAG 2.2 AA contrast for normal text at **4.5:1** with meaningful margin. The key pairings also pass the **3:1** large-text threshold. The maximum ambient backdrop alpha is only 4.5%; a worst-case alpha-composite check still leaves the tested body/link pairings above 4.5:1.

No color change was necessary in the static calm palettes. Two low-risk accessibility improvements were implemented:

1. The search field now has a persistent label instead of relying only on placeholder text.
2. The live camera preview now exposes the semantic label “Live camera preview” to TalkBack and switch access.

Android 12+ dynamic color is selected from the user's wallpaper at runtime. Material 3's tonal role system is intended to provide accessible pairings, but a complete dynamic-color claim requires runtime tests on representative wallpapers and contrast modes. This report does not claim that every possible wallpaper-derived scheme has been physically verified.

## Contrast method

Ratios were calculated from the explicit sRGB hex roles in `KaagazTheme.kt` using the WCAG relative-luminance formula:

```text
contrast = (L brighter + 0.05) / (L darker + 0.05)
```

Pass criteria:

- Normal text: **4.5:1 or higher**.
- Large text: **3:1 or higher**.
- For an elderly-first baseline, the review uses the stricter normal-text threshold even for headings wherever practical.

These are readability baselines, not a diagnosis or guarantee for every person with low vision. Font scaling, display contrast settings, glare, blur, and device quality still require physical-device review.

## Static role-pair results

| UI role pairing | Light | Dark | Result |
|---|---:|---:|---|
| `primary` on `onPrimary` button | 5.87:1 | 8.10:1 | Pass |
| `onPrimaryContainer` on `primaryContainer` card | 12.90:1 | 6.94:1 | Pass |
| `onSecondaryContainer` on `secondaryContainer` badge | 13.36:1 | 7.25:1 | Pass |
| `onTertiaryContainer` on `tertiaryContainer` empty state | 12.92:1 | 6.85:1 | Pass |
| `onSurface` on background/surface | 16.30:1 | 14.26:1 | Pass |
| `onSurfaceVariant` on background/surface | 8.90:1 | 10.84:1 | Pass |
| `onSurfaceVariant` on `surfaceVariant` | 7.26:1 | 5.48:1 | Pass |
| `primary` on background for text actions/SAFE label | 5.59:1 | 11.42:1 | Pass |
| `tertiary` on background for needs-review status | 5.65:1 | 11.10:1 | Pass |

### Ambient backdrop stress check

The animated backdrop uses primary/tertiary colors at no more than 4.5% alpha over the background. Blending the strongest tested overlay against the background gave these minimum ratios:

| Foreground over maximum tested ambient overlay | Light | Dark | Result |
|---|---:|---:|---|
| `onSurfaceVariant` body/supporting text | 8.38:1 | 10.00:1 | Pass |
| `primary` action/status text | 5.27:1 | 10.54:1 | Pass |

The ambient canvas is also placed behind opaque cards and does not contain text. It is decorative and has no content description.

## UI/UX accessibility review

### Strengths

- Material buttons, text buttons, text fields, and dialogs provide standard roles and semantics.
- The primary actions use clear verbs: Import, Scan, Search, Show all, Capture page, Save, Remove, Keep it.
- The app communicates important state in text, not through color, sound, haptics, or animation alone.
- Reduced-motion behavior disables ambient particle movement and collapses motion durations to zero.
- Ambient effects do not use document names, OCR text, reminder titles, counts, or identifiers.
- The palette uses matching Material roles such as `onPrimary` on `primary` and `onPrimaryContainer` on `primaryContainer`.
- Cards, buttons, and text fields rely on Material minimum touch-target behavior; the implementation should still be checked at runtime because custom padding and large-font reflow can affect layout.
- Recent-app privacy and locked-surface work from earlier phases remain compatible with the ambient layer.

### Fixes applied during this review

- Added a persistent `label` to the search `OutlinedTextField`; placeholder text is retained as a cue but is no longer the only field identification.
- Added `contentDescription = "Live camera preview"` to the camera `AndroidView` surface.

### Remaining risks and recommended runtime checks

1. **Dynamic color:** On Android 12+, run the app with light and dark wallpapers containing low-, medium-, and high-saturation palettes. Verify primary buttons, containers, links, body text, error text, outlines, and disabled states with a screenshot contrast tool.
2. **Large text:** Test at Android font scales 1.3x, 1.5x, and 2.0x. Confirm that the headline, private-shelf actions, search label, document rows, reminder rows, dialog buttons, and OCR review controls do not clip or overlap.
3. **Touch targets:** Confirm all actions remain at least 48dp and that adjacent actions do not have overlapping expanded touch regions, especially the document removal controls and reminder Delete actions.
4. **TalkBack:** Verify traversal order: screen heading → status/privacy explanation → primary action → search label/input → reminder controls → document heading/content → document action controls. Confirm that OCR text is not announced merely because a review animation runs.
5. **Switch access and keyboard:** Confirm every button and dialog action is reachable without a gesture and that focus returns to a meaningful control after dialogs close.
6. **High contrast and glare:** Test Android high-contrast text/display settings and a bright outdoor screen. The pale container palette is readable by calculation, but physical glare can still expose weak borders or disabled-state affordances.
7. **Color-vision differences:** Confirm “needs review”, “reviewed”, “SAFE”, and errors also use text or icons. Do not rely on green, purple, or red alone.
8. **Reduced motion:** Toggle the system animator-duration scale to zero and verify no particle loop, card expansion, progress transition, or success state becomes ambiguous.
9. **Hearing/accessibility settings:** Verify the app remains fully understandable with sound and haptics disabled, and that feedback never masks TalkBack speech.

## Elderly-user acceptance checklist

- [x] Body and action text uses role pairings above 4.5:1 in both explicit themes.
- [x] Large headings exceed 3:1 and, in practice, also exceed 4.5:1 for the tested static roles.
- [x] Primary actions have plain-language labels.
- [x] Search has a persistent label.
- [x] Camera preview has a semantic label.
- [x] Motion has a reduced-motion fallback.
- [x] Ambient effects are low-alpha, slow, non-interactive, and content-independent.
- [ ] Dynamic Android 12+ schemes verified on physical devices and representative wallpapers.
- [ ] 2.0x font-scale layout and dialog reflow verified on a small phone.
- [ ] Manual TalkBack and switch-access pass completed on representative hardware.
- [ ] Outdoor/glare and high-contrast display checks completed.

## References

- [WCAG 2.2 Understanding SC 1.4.3 — Contrast (Minimum)](https://www.w3.org/WAI/WCAG22/Understanding/contrast-minimum.html)
- [Android Compose accessibility API defaults](https://developer.android.com/develop/ui/compose/accessibility/api-defaults)
- [Android Compose Material 3 theming and accessibility](https://developer.android.com/develop/ui/compose/designsystems/material3)
- [Android Compose haptics design principles](https://developer.android.com/develop/ui/views/haptics/haptics-principles)
