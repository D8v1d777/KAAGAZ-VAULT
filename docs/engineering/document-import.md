# Local Document Import — Phase 3 extension

## User-visible behavior
- The home screen lets the user select a PDF or supported raster image through Android's Storage Access Framework picker.
- Cancelling the picker does not change stored state.
- The import repository reads selected content with a hard 31 MiB bound and rejects empty/unreadable content, unsupported MIME types, and MIME/signature mismatches.
- The current signature validator recognizes PDF, JPEG, PNG, GIF, WebP, and BMP signatures. This is format sniffing, not a full parser or malware scan.
- The original display name and MIME type are stored inside the encrypted payload, not in filenames or a plaintext database.
- The encrypted file uses a random UUID filename in app-private internal storage.
- The home screen lists imported files and supports deleting a selected entry.
- File import and listing run on a single background executor; Compose state updates are posted to the main looper.
- No broad storage permission, network permission, account, cloud, OCR, or automatic interpretation is added.

## Security and product limits
- This version buffers content in memory up to 31 MiB and stores document bytes plus small metadata in one encrypted payload. It is not a streaming implementation.
- Provider MIME types and filenames remain untrusted. Signature checks reduce simple spoofing but do not guarantee the file is well-formed or safe to parse.
- The UI lists names by decrypting each stored payload. This is functional for the current small prototype but inefficient for a large vault. Encrypted metadata indexing and pagination require separate research and tests.
- Deletion removes the encrypted file but is not guaranteed secure erasure on flash storage.
- There is no in-app document preview, camera scanning, OCR, text search, biometric gate, reminder engine, or recovery/export flow yet.
- Android Keystore runtime behavior needs instrumentation tests. JVM tests cannot prove device/provider behavior.

## Acceptance / review
- [ ] Latest PR CI build and tests pass.
- [ ] Test valid synthetic PDF/image import, cancellation, empty content, unreadable provider, unsupported MIME, and >31 MiB content on a device/emulator.
- [ ] Verify no plaintext file or metadata appears under app storage after import.
- [ ] Add content signature validation tests (implemented; CI still pending at time of writing).
- [ ] Review the UI with TalkBack and large font sizes.
- [ ] Keep offline manifest policy green.
