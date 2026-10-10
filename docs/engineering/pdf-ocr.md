# Bounded PDF Text Extraction and OCR

## Design

- PDF bytes are read from the already-decrypted payload into memory and opened with PdfiumAndroidKt 2.0.0. No plaintext PDF, rendered page, or OCR temporary file is written to disk.
- The engine extracts embedded PDF text first. Pages with little/no text are rendered one at a time and passed to the existing offline Tesseract engine.
- Native text extraction reports null OCR confidence; confidence is reported only for pages actually processed by Tesseract.
- PDF text and OCR results are stored in the existing AES-GCM encrypted payload, and the metadata/search index is updated through the repository.

## Limits

- PDF input: 20 MiB maximum for OCR (larger PDFs can remain stored encrypted but are rejected by the OCR operation).
- Pages: process at most 30; more pages set the truncation flag.
- Render size: longest edge at most 2000 px; total rendered pixels at most 60 million per operation.
- Native text: at most 10,000 characters per page. Combined output: at most 150,000 characters.
- Password-protected PDFs are not supported in this slice; failure leaves the original encrypted file untouched.
- The current UI does not expose a PDF password prompt, page selection, or progress/cancel action yet.

## Dependency and platform trade-off

- Uses PdfiumAndroidKt 2.0.0, which opens ByteArray input and provides native text extraction and page rendering. The maintained engine was preferred over older PDFium forks for processing untrusted PDFs.
- This dependency requires Android API 24+ and Java 21 for the build environment. The app's provisional minSdk has therefore moved from 23 to 24; this is an explicit compatibility trade-off. Version 2.0.0 is pinned because it supports compileSdk 36, unlike 2.0.3 which requires the Android 17/API 37 preview platform.
- CI uses JDK 21; app Java and Kotlin bytecode targets remain 17. Kotlin 2.3.10 and KSP 2.3.4 are aligned with the PDFium 2.0.0 publication, and AGP's built-in Kotlin is explicitly disabled in favor of the external Kotlin Android/Compose plugins.
- Apache-2.0 license text is already packaged in app/src/main/assets/licenses/Apache-2.0.txt and the dependency is listed in THIRD_PARTY_NOTICES.txt.

## Remaining verification

- [ ] CI confirms PDFium dependency resolution, native ABI packaging, Kotlin compile, unit tests, lint, and offline manifest policy.
- [ ] Add Android instrumentation tests with synthetic text-layer and image-only PDFs.
- [ ] Verify malformed/password-protected PDFs fail without crashes or plaintext files.
- [ ] Verify page/document/bitmap/Tesseract resources close on all error paths.
- [ ] Measure OCR latency and memory on representative low-memory devices.
- [ ] Keep OCR output human-reviewable and never turn it into automatic medication instructions or other actions.
