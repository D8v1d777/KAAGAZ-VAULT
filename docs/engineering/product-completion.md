# Product Completion Tracker

This is a delivery-progress estimate, not a security certification, test pass rate, or reviewer score. Percentages reflect implemented scope against the objective definitions below. A feature can be partially implemented while still failing release acceptance.

## Weighted objectives

| Objective | Weight | Current credit | Status / remaining acceptance |
|---|---:|---:|---|
| Reproducible build, repository contract, and CI gates | 10% | 6% | Pinned CI toolchain has passed clean build, unit-test, lint, and manifest gates on Phase 3 and Phase 4 commits. A checked-in Gradle Wrapper and reproducible local clean checkout remain. |
| Encrypted local payload storage and key lifecycle | 15% | 7% | AES-GCM envelope, Keystore key provider, ciphertext-only pending files, replacement/recovery, and bounded payloads pass JVM CI. Add Android Keystore instrumentation, lifecycle/rotation/recovery policy, streaming/chunked format, and security review. |
| User import, local library, and deletion | 10% | 6% | SAF import, bounded input, signature sniffing, encrypted metadata-in-payload, list/delete UI pass CI. Device testing, stronger parsing boundaries, large-vault behavior, and accessibility review remain. |
| Camera capture and document image workflow | 10% | 3% | CameraX preview, user-initiated CAMERA permission, in-memory JPEG capture, orientation normalization, and direct encrypted import are implemented. Initial camera CI passed before the final orientation fix; latest stacked-head CI remains pending. Multi-page scan/crop/deskew and device tests remain. |
| Fully offline OCR engine and document processing | 15% | 8% | Image OCR/review passes Phase 3 CI. Bounded in-memory PDF text-layer extraction plus scanned-page OCR has been implemented on the current branch, with 20 MiB/30-page/60M-pixel/150k-character limits; PDFium dependency, JDK 21, and minSdk 24 changes still need CI verification. Device benchmarks and accuracy evaluation remain. |
| Human review, provenance, and safe field/action extraction | 10% | 0% | Not implemented. OCR output must remain untrusted; low-confidence medical text cannot become an instruction automatically. |
| Encrypted metadata store and local search | 15% | 4% | Room 2 + SQLCipher, a Keystore-wrapped random database key, encrypted normalized search text, and local search UI are implemented and pass Phase 4 CI. Runtime DB/WAL leakage, wrong-key recovery, device tests, and performance remain; ADR-0001 stays Proposed. |
| Local reminders with safe date/action confirmation | 5% | 0% | Not implemented. |
| App access control, privacy surfaces, backup/export controls | 5% | 1% | Offline/no-INTERNET policy and backup disabled are present. Biometric gate, recents redaction, key lifecycle, and explicit export policy remain. |
| Accessibility, performance, release and distribution readiness | 5% | 0% | Not implemented/reviewed. |

**Estimated implemented-scope progress: 35% of weighted objectives.** This is an implementation-scope estimate, not a security score or release-readiness claim. Phase 3 and Phase 4 CI runs pass at their recorded SHAs. The final camera orientation fix and PDFium/JDK21/minSdk24 changes are on a new branch and remain CI-pending. Device-level OCR, PDF rendering, database leakage, and camera behavior remain untested.

## Product-level release gates
- [ ] All required offline core features work without network permission or hidden network behavior.
- [ ] Latest clean CI run passes build, unit tests, lint, and merged-manifest policy.
- [ ] Android Keystore and storage integration tests pass on representative supported Android versions.
- [ ] Imported documents, metadata, OCR output, thumbnails, search indexes, logs, temporary files, and backup behavior have explicit protection decisions.
- [ ] OCR provenance, confidence, correction, and medical-action safeguards are tested.
- [ ] Search/reminder correctness, accessibility, low-memory performance, and failure/recovery paths are tested.
- [ ] Release artifact is reproducibly built, dependency/license-reviewed, and documented.

## Update rule
After each substantial cycle, update the objective credit only when code has been added and cite the relevant commit/PR and verification evidence. Reduce credit if regressions invalidate an earlier objective. Do not count documentation alone as implementation progress.
