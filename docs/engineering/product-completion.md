# Product Completion Tracker

This is a delivery-progress estimate, not a security certification, test pass rate, or reviewer score. Percentages reflect implemented scope against the objective definitions below. A feature can be partially implemented while still failing release acceptance.

## Weighted objectives

| Objective | Weight | Current credit | Status / remaining acceptance |
|---|---:|---:|---|
| Reproducible build, repository contract, and CI gates | 10% | 6% | CI toolchain is pinned and CI has passed on the storage-only revision. Latest import/security commits still need a green run. Add a checked-in Gradle Wrapper and confirm reproducible clean checkout. |
| Encrypted local payload storage and key lifecycle | 15% | 7% | AES-GCM envelope and Keystore key provider exist. Add instrumentation tests, lifecycle/rotation/recovery policy, streaming/chunked format, interrupted-write coverage, and security review. |
| User import, local library, and deletion | 10% | 6% | SAF import, bounded input, metadata encrypted inside payload, signature sniffing, list/delete UI exist. Needs latest CI, device testing, stronger parsing boundaries, large-vault behavior, and accessibility review. |
| Camera capture and document image workflow | 10% | 0% | Not implemented. |
| Fully offline OCR engine and document processing | 15% | 0% | Not implemented. OCR engine selection must account for script coverage, bundled models, offline behavior, APK size, license, and low-end devices. |
| Human review, provenance, and safe field/action extraction | 10% | 0% | Not implemented. OCR output must remain untrusted; low-confidence medical text cannot become an instruction automatically. |
| Encrypted metadata store and local search | 15% | 0% | Not implemented. ADR-0001 remains Proposed pending compatibility and leakage evaluation. |
| Local reminders with safe date/action confirmation | 5% | 0% | Not implemented. |
| App access control, privacy surfaces, backup/export controls | 5% | 1% | Offline/no-INTERNET policy and backup disabled are present. Biometric gate, recents redaction, key lifecycle, and explicit export policy remain. |
| Accessibility, performance, release and distribution readiness | 5% | 0% | Not implemented/reviewed. |

**Estimated implemented-scope progress: 20% of weighted objectives.** This does not mean 20% secure, tested on-device, or release-ready. Current CI is pending for the newest commit at the time of this document; the import UI has not yet been device-tested.

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
