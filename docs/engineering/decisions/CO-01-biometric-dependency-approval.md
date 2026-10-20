# CO-01 — AndroidX Biometric dependency approval

**Status:** Approval requested; no dependency declaration changed.  
**Decision authority:** Project owner.  
**Related:** Accepted ADR-0003; CO-01/CO-02; repository gate E6/G3.

## Concrete use

The vault needs a consistent Android authentication prompt and `BiometricPrompt.CryptoObject` handoff for auth-per-use key operations, with compatibility behavior across minSdk 24 and current Android. A platform-only implementation would require multiple API-specific paths and still would not make unsupported combinations work. AndroidX Biometric supplies the compatibility wrapper; it does not remove the need for a tested authenticator/API matrix.

## Proposed artifact

- Coordinate: `androidx.biometric:biometric:1.1.0`
- Version choice: latest documented **stable** release as of 2026-10-20; Android's release page also lists 1.4.0-alpha07, but this security-critical vault should not adopt an alpha dependency without a separate justification.
- Catalog proposal: `androidx-biometric = { module = "androidx.biometric:biometric", version.ref = "biometric" }` and `biometric = "1.1.0"`.
- No other version changes, plugin changes or new repositories proposed.

## Maintenance review

The stable line is old relative to the current Android toolchain while 1.4 remains alpha. Before merge, CI must resolve the full dependency graph and run Gradle dependency verification. Review release notes and transitive constraints at implementation time. If the old stable line cannot meet the accepted authenticator/API behavior, stop and return with a new stable-vs-alpha decision; do not silently upgrade to an alpha.

## License and supply-chain

- Google's AndroidX Biometric library is distributed under Apache License 2.0 (per Google's/Chromium's published component metadata).
- Pin the exact version in `gradle/libs.versions.toml`; keep dependency verification metadata enforced.
- The change must not add unpinned dependencies, repositories, plugins or dynamic versions.

## Privacy and network

- Intended use is on-device Android system authentication and local Android Keystore cryptographic operations.
- The library is not intended to upload biometrics, keys, document content, OCR, metadata or telemetry; the vault must not add INTERNET permission or network code.
- CI must confirm the merged manifest still declares no INTERNET permission. Review the resolved dependency graph for unexpected network/analytics artifacts before accepting the dependency.

## Compatibility constraints (non-negotiable)

- `BIOMETRIC_STRONG | DEVICE_CREDENTIAL` is not supported by AndroidX PromptInfo on API 28–29, and device-credential-only is unsupported before API 30. The minSdk 24 path must not claim universal credential fallback.
- The accepted A2 policy is conditional: use the same authenticated crypto operation only where supported and instrumented tests prove it. Otherwise use the approved biometric-only auth-per-use path plus the independent recovery route. Never replace this with a UI-only boolean or an unapproved time-window key.
- A dependency approval is not approval of an untested fallback path or proof of Android Keystore security.

## Options

1. **Approve stable 1.1.0** (recommended for now): known stable channel, pinned dependency; accept that compatibility still must be tested and stop if the old stable line is inadequate.
2. **Do not add AndroidX Biometric**: use platform APIs only, with an explicit implementation proposal for API 24–27 compatibility and API 28+ prompt handling. This increases custom platform-specific code and testing burden.
3. **Use 1.4.0-alpha07**: not recommended for a security-critical release path unless the owner explicitly accepts alpha dependency risk.

## Owner decision requested

Approve option 1, reject the dependency and choose option 2, or explicitly authorize option 3. Until the owner chooses, Gradle files remain unchanged.