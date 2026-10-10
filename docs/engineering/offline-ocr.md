# Offline OCR — initial image-only implementation

## Research and selected engine
- ML Kit's bundled Android text-recognition models cover Latin and Devanagari but not Telugu, so ML Kit alone does not meet the project's Telugu document requirement: https://developers.google.com/ml-kit/vision/text-recognition/android
- Tesseract4Android 4.9.0 wraps Tesseract 5.5.1 and supports model files supplied under a private `tessdata` directory. It is Apache-2.0 licensed: https://github.com/adaptech-cz/Tesseract4Android
- Official `tessdata_fast` supplies LSTM models for English, Hindi, and Telugu. The repository snapshot is pinned to commit `87416418657359cb625c412a48b6e1d6d41c29bd`: https://github.com/tesseract-ocr/tessdata_fast/tree/87416418657359cb625c412a48b6e1d6d41c29bd
- The build downloads those models at build time, verifies their Git blob SHA-1 against the pinned repository tree, and packages them as application assets. Runtime OCR uses only bundled assets and app-private files; it does not download models or require network permission.
- Models are copied to `filesDir/tesseract/tessdata` and re-verified against their pinned Git blob hashes before use.

## Implemented behavior
- Image-only offline OCR for English, Hindi (Devanagari), and Telugu.
- Tesseract runs on the existing background I/O executor, not the main thread.
- Image decoding uses a bounds pass and power-of-two sampling to keep each image dimension at or below 2400 pixels before OCR.
- OCR output, mean engine score, truncation flag, and review state are serialized into payload format v2 and encrypted with the original document payload.
- Payload format v1 is still readable for migration compatibility.
- Users can edit extracted text and explicitly save it as reviewed. A new OCR run resets the reviewed flag.
- The UI labels Tesseract's mean confidence as an engine score out of 100, not a calibrated probability. Human review is required; no extracted text triggers an automatic action.
- Encrypted payload replacement uses a ciphertext-only temporary file and backup; startup attempts to restore a backup if replacement was interrupted.

## Limitations and security gates
- **Not yet CI-verified at the time of writing.** The new Gradle model-download task, JitPack dependency, JNI packaging, Kotlin code, and tests must pass the latest GitHub Actions run.
- Models add roughly 7.9 MB before packaging/compression (eng 4,113,088 bytes; hin 1,122,751; tel 2,769,654). Runtime also copies them into private app storage, so installed storage use increases.
- The build needs network access to fetch pinned model artifacts; the installed app's OCR path is offline. If the build must be fully offline later, check in approved model artifacts or establish an internal artifact cache.
- OCR currently processes imported images only. PDFs need a separate page-rendering design that avoids leaving decrypted plaintext in temporary files.
- Image downsampling can reduce small-text accuracy. Handwriting quality varies and must be benchmarked with consented/synthetic fixtures; no accuracy claim is made.
- OCR text remains untrusted. The UI does not infer medication instructions, deadlines, or actions.
- The 150,000-character OCR cap can truncate long results; the UI displays a warning.
- No database/search index stores OCR text yet. The OCR text is encrypted inside the document payload, but listing currently decrypts each payload, which will not scale.
- Device-level latency/memory tests and Android Keystore instrumentation tests remain outstanding.

## Next acceptance checks
- [ ] Latest CI downloads and verifies all three models and completes build, unit tests, lint, and offline manifest policy.
- [ ] Confirm the merged manifest still has no INTERNET permission.
- [ ] Run OCR on synthetic English/Hindi/Telugu images on device/emulator and confirm all text stays local.
- [ ] Confirm tampered ciphertext blocks OCR retrieval and editing.
- [ ] Test no-text images, huge-dimension images, low-confidence output, truncated OCR, app process interruption, and model-copy recovery.
- [ ] Review text editing with TalkBack/large fonts.
- [ ] Add PDF page OCR only after a privacy-safe rendering path is designed.
