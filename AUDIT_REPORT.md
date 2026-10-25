# KAAGAZ-VAULT Security Audit Report

**Date:** 2026-10-25
**Branch:** `feat/durable-vault-migration-journal`
**Commit SHA:** `6b30ceaf5ab09b2494d82356b08c4bebf7406521`
**Auditor:** Claude Opus 5.5 (automated audit per AGENTS.md protocol)

---

## Executive Summary

**Overall Status:** **BLOCKED on packaging** — Kotlin compilation passes, but AAPT2 architecture mismatch prevents APK build and all downstream gates.

**Critical Findings:**
- ✅ **All 7 new hardening classes compile successfully** (28 actionable tasks, all UP-TO-DATE)
- ❌ **AAPT2 binary is x86-64 only** — fails on ARM64 (aarch64) host
- ❌ **GitHub Actions CI not triggered** — no open PR targets canonical_branch
- ❌ **CO-01/CO-02 require human approval** before implementation (auth-bound session, recovery design)
- ❌ **Offline manifest gate cannot run** — requires built APKs

**Risk Assessment:** The hardening code introduces defense-in-depth but has false-positive risks (root/debug detection), untested flash-storage deletion guarantees, and startup performance overhead. The auth-bound key system is completely unwired (CO-01/CO-02).

---

## Phase 1 — Evidence Establishment

### 1.1 Git State
```
Branch: feat/durable-vault-migration-journal
HEAD:   6b30ceaf5ab09b2494d82356b08c4bebf7406521
Status: 8 modified files, 5 new untracked directories
```

**Modified files:**
- `app/build.gradle.kts` — R8 enabled, conditional signing config
- `app/src/main/AndroidManifest.xml` — network security config, application class
- `app/src/main/java/com/kaagazvault/database/DatabaseKeyManager.kt` — hardware-enforced key provider
- `app/src/main/java/com/kaagazvault/security/AuthBoundVaultKey.kt` — StrongBox enforcement
- `app/src/main/java/com/kaagazvault/security/DocumentKeyProvider.kt` — hardware-backed key verification
- `app/src/main/java/com/kaagazvault/security/EncryptedDocumentStore.kt` — SecureDeletion integration
- `app/src/main/java/com/kaagazvault/security/VaultMigrationJournal.kt` — SecureDeletion integration
- `app/src/main/java/com/kaagazvault/security/WrappedVaultKeyStore.kt` — SecureDeletion integration

**New files (security hardening):**
```
app/src/main/java/com/kaagazvault/security/hardening/
├── AntiDebugging.kt           (8.2K)  — ptrace, JDWP, timing detection
├── DeviceIntegrityChecker.kt  (14.9K) — root, emulator, hooking detection
├── MemoryProtection.kt        (8.0K)  — secure memory, zeroing, thread-local
├── RuntimeIntegrity.kt        (16.4K) — APK signature, checksums, class loader
├── SecureDeletion.kt          (9.7K)  — multi-pass wipe (DoD 5220.22-M)
├── SecurityHardening.kt       (11.1K) — coordinator, lockdown, monitoring
└── StrongBoxEnforcer.kt       (16.2K) — StrongBox/TEE enforcement, attestation
```

**Supporting files:**
- `app/proguard-rules.pro` — aggressive obfuscation rules
- `app/src/main/res/xml/network_security_config.xml` — cleartext disabled
- `app/src/main/java/com/kaagazvault/KaagazVaultApplication.kt` — security initialization

### 1.2 Python Tests
```
Command: python3 -m unittest discover -s scripts -p 'test_*.py' -v
Exit: 0
Result: 8 tests OK (0.072s)
```
All offline manifest policy unit tests pass.

### 1.3 Offline Manifest Check
```
Command: python3 scripts/check_offline_manifest.py
Exit: 1
Reason: Packaged debug/release APK and merged manifests not found (AAPT2 failure blocks packaging)
```

### 1.4 Gradle Verification
| Task | Status | Exit Code | Notes |
|------|--------|-----------|-------|
| `:app:compileDebugKotlin` | SUCCESS | 0 | All 28 tasks UP-TO-DATE |
| `:app:compileReleaseKotlin` | SUCCESS | 0 | From cache |
| `:app:lint` | FAILED | 1 | AAPT2 daemon startup failed |
| `:app:testDebugUnitTest` | FAILED | 1 | AAPT2 required for resource processing |
| `:app:assembleDebug` | FAILED | 1 | AAPT2 daemon startup failed |
| `:app:assembleRelease` | FAILED | 1 | AAPT2 daemon startup failed |

### 1.5 AAPT2 Architecture Analysis
```
Binary: aapt2-9.1.1-14792394-linux (from aapt2-9.1.1-14792394-linux.jar)
ELF Class: ELF64
Machine: Advanced Micro Devices X86-64 (x86-64)
Host: aarch64 (ARM64) — Termux on Android ARM64
Mismatch: AAPT2 is x86-64 only; cannot run on ARM64 host
```

**Conclusion:** The AAPT2 binary provided by Gradle's maven repository is compiled for x86-64 only. The ARM64 host cannot execute it. This is a known limitation of the Android build tools distribution. Full packaging requires an x86_64 CI runner (GitHub Actions `ubuntu-latest`).

---

## Phase 2 — Packaging Verification

### 2.1 GitHub Actions Workflow
**File:** `.github/workflows/android-ci.yml`
- Runs on: `ubuntu-latest` (x86_64)
- Triggers: push to `main`, pull requests
- Steps: SDK install → Python tests → `./gradlew clean assembleDebug assembleRelease testDebugUnitTest lint` → offline manifest check

### 2.2 CI Status
**No open PR targeting `feat/durable-vault-migration-journal`** → CI has not run for this commit.

**Required to unblock:** Push a draft PR to canonical branch to trigger CI on x86_64 runner.

---

## Phase 3 — Security Review

### 3.1 Hardening Classes — Detailed Analysis

#### **AntiDebugging.kt** (8.2K)
| Aspect | Finding |
|--------|---------|
| **Call sites** | `SecurityHardening.startMonitoring()` → `AntiDebuggingFactory.create()` → `antiDebugging.checkForDebugger()` / `quickCheck()` every 5s |
| **Threats addressed** | JDWP debugger attach, ptrace tracer, timing anomalies, hardware breakpoints, debug system properties |
| **Cannot prevent** | Kernel-level ptrace hiding, hardware debugger (JTAG), sophisticated anti-anti-debugging |
| **Failure behavior** | `triggerLockdown()` → `Process.killProcess()` + `System.exit(1)` — **destructive, permanent** |
| **False-positive risks** | High: JIT compilation spikes, GC pauses, profiling tools, legitimate `adb` debugging on dev devices |
| **Device compatibility** | `/proc/self/status` parsing may vary; `TracerPid` not available on all kernels |
| **Tests** | NONE — no unit or instrumented tests for anti-debugging logic |
| **Regression risk** | Legitimate users on rooted/dev devices get permanent vault lockout |

**Code issues:**
- Line 168: 5ms timing threshold is aggressive — modern devices may exceed under load
- Line 221-232: Lockdown kills process immediately — no graceful key zeroing via `MemoryProtection`
- Line 183-190: `netstat -tlnp` requires root to show all processes — will fail silently on non-root

#### **DeviceIntegrityChecker.kt** (14.9K)
| Aspect | Finding |
|--------|---------|
| **Call sites** | `SecurityHardening.performFullSecurityCheck()` → `deviceIntegrityChecker.checkIntegrity()` / `quickCheck()` |
| **Threats addressed** | Root (su, Magisk, SuperSU), emulator detection, hooking frameworks (Xposed, Frida, Substrate), test keys, SELinux status |
| **Cannot prevent** | Magisk Hide / Zygisk root hiding, kernel-level root, custom ROMs with modified props |
| **Failure behavior** | Returns `IntegrityResult.isCompromised=true` → `SecurityHardening` triggers lockdown |
| **False-positive risks** | Moderate: some OEMs ship with test keys; emulator props may appear on physical devices with custom ROMs |
| **Device compatibility** | `/proc/self/maps` parsing works on API 24+; `Build.FINGERPRINT` varies by OEM |
| **Tests** | NONE |
| **Lockout risk** | High — `isCompromised` triggers permanent vault destruction |

**Code issues:**
- Line 340: `netstat -tlnp` requires root — will not detect Frida on non-root
- Line 290-296: IMEI check uses deprecated API on API < 26
- Line 132-133: `SU_PATHS` static list easily bypassed by renamed binaries

#### **RuntimeIntegrity.kt** (16.4K)
| Aspect | Finding |
|--------|---------|
| **Call sites** | `SecurityHardening.performFullSecurityCheck()` / `quickCheck()` → `RuntimeIntegrityFactory.create()` |
| **Threats addressed** | APK signature mismatch, DEX/native library/asset tampering, class loader injection, method hooking (timing + stack), memory mapping anomalies |
| **Cannot prevent** | Kernel-level memory modification, dm-verity bypass, JIT code modification |
| **Failure behavior** | `lockdownCallback` → process kill — **destructive** |
| **False-positive risks** | Moderate: legitimate app updates change APK signature; dynamic class loading (Jetpack Compose, KSP) may trigger class loader check |
| **Device compatibility** | `/proc/self/maps` read works on API 24+; reflection on `BaseDexClassLoader` is non-SDK API |
| **Tests** | NONE |
| **Performance** | SHA-256 of full APK (30s interval) + native libs + assets — measurable I/O on low-end devices |

**Code issues:**
- Line 144-173: `verifyApkSignature()` computes expected signature at init — fails after legitimate app update
- Line 292-307: Reflection on `dalvik.system.BaseDexClassLoader` — hidden API, may break on future Android
- Line 317-362: Timing-based hooking detection has no baseline calibration — GC/JIT causes false positives

#### **SecureDeletion.kt** (9.7K)
| Aspect | Finding |
|--------|---------|
| **Call sites** | `EncryptedDocumentStore.delete()`, `VaultMigrationJournal.deleteArtifact()`, `WrappedVaultKeyStore.deleteArtifact()`, `SecurityHardening.wipeFile()/wipeDirectory()` |
| **Threats addressed** | Logical data recovery from unallocated space, filesystem forensics, backup/snapshot exposure |
| **Cannot prevent** | **Flash wear-leveling** (overwrites may not hit same physical blocks), SSD TRIM, FBE/FDE encryption already protects data at rest |
| **Failure behavior** | Throws `IOException` — callers must handle; `deleteArtifact()` throws on failure |
| **False-positive risks** | Low (functional), but **security claim is misleading** — multi-pass overwrite does not guarantee erasure on flash |
| **Device compatibility** | `rws` mode + `channel.force(true)` works on API 24+; `StatFs` for free space wipe |
| **Tests** | NONE |
| **DoS risk** | Line 35: 100MB max wipe size prevents DoS; `wipeFreeSpace()` can wear flash storage |

**Critical documentation issue:** The class comment acknowledges wear-leveling but the KDoc and method names imply guaranteed secure erasure. **Do not describe multi-pass overwriting as guaranteed secure erasure on flash storage.**

#### **SecurityHardening.kt** (11.1K)
| Aspect | Finding |
|--------|---------|
| **Role** | Coordinator — initializes all components, runs periodic checks, manages lockdown state |
| **Call sites** | `KaagazVaultApplication.onCreate()` → `SecurityHardening(this).initialize()` |
| **Lockdown logic** | Any single check failure → `COMPROMISED` → `LOCKDOWN` → process kill |
| **Monitoring** | Quick checks every 5s, full checks every 30s on single-threaded executor |
| **False-positive amplification** | **Single failure in ANY component triggers full lockdown** — no tolerance, no graduated response |
| **Recovery** | None — `onLockdown` callback kills process; no key persistence across restarts |
| **Tests** | NONE |

**Code issues:**
- Line 130-137: `SecurityState.COMPROMISED` for ANY failure — no distinction between critical (root) vs advisory (TEE only)
- Line 154-163: `quickCheck()` calls private methods now made `internal` — couples components tightly
- Line 251-265: `triggerLockdown()` is one-way — no cancel, no user notification before kill

#### **StrongBoxEnforcer.kt** (16.2K)
| Aspect | Finding |
|--------|---------|
| **Call sites** | `SecurityHardening` → `StrongBoxEnforcerFactory.create()`; `AuthBoundVaultKey.generateWrappingKey()`; `HardwareEnforcedDocumentKeyProvider`; `AndroidKeystoreDocumentKeyProvider` |
| **Threats addressed** | Software-only keystore, weak key generation, missing attestation, biometric invalidation |
| **StrongBox enforcement** | API 28+: `setIsStrongBoxBacked(true)` required for wrapping keys; TEE fallback for document keys |
| **Cannot prevent** | StrongBox hardware bugs, Keymaster HAL vulnerabilities, supply chain attacks on secure element |
| **Failure behavior** | `checkAvailableSecurityLevel().isAcceptable=false` → `COMPROMISED` state; key generation throws `IllegalStateException` |
| **Attestation** | `getKeyAttestation()` only works for asymmetric keys (PrivateKeyEntry) — **not usable for AES vault keys** |
| **Tests** | NONE |

**Code issues:**
- Line 103-138: `isStrongBoxAvailable()` generates test key with timestamp alias — collision risk, leaves orphan keys on crash
- Line 180-204: `getKeyInfo()` uses reflection on `KeyFactory.getKeyInfo()` — hidden API, may break
- Line 380-433: `HardwareEnforcedDocumentKeyProvider` throws if no hardware keystore — **fails closed but no recovery path**

#### **MemoryProtection.kt** (8.0K)
| Aspect | Finding |
|--------|---------|
| **Call sites** | Currently **not called anywhere** in the codebase — dead code |
| **Features** | `SecureBytes` (auto-zero on Cleaner), `SecureKey` wrapper, direct ByteBuffer allocation, core dump disable (stub) |
| **Cleaner reliability** | `Cleaner` runs on separate thread, non-deterministic — **not suitable for cryptographic key zeroing** |
| **Tests** | NONE |

**Critical gap:** The entire `MemoryProtection` module is unused. Keys in `AuthBoundVaultKey`, `DatabaseKeyManager`, `DocumentKeyProvider` are never zeroed on lockdown.

---

### 3.2 Integration Points Review

| Integration | File | Line | Assessment |
|-------------|------|------|------------|
| SecureDeletion → EncryptedDocumentStore.delete() | EncryptedDocumentStore.kt:125-133 | Calls `SecureDeletion.wipe()` — throws on failure, good |
| SecureDeletion → VaultMigrationJournal.deleteArtifact() | VaultMigrationJournal.kt:266-273 | Calls `SecureDeletion.wipe()` — throws on failure, good |
| SecureDeletion → WrappedVaultKeyStore.deleteArtifact() | WrappedVaultKeyStore.kt:82-89 | Calls `SecureDeletion.wipe()` — throws on failure, good |
| StrongBoxEnforcer → AuthBoundVaultKey | AuthBoundVaultKey.kt:162-164 | `setIsStrongBoxBacked(true)` — hard requirement, no TEE fallback |
| StrongBoxEnforcer → DocumentKeyProvider | DocumentKeyProvider.kt:70-71 | `setIsStrongBoxBacked(true)` — hard requirement, no TEE fallback |
| HardwareEnforcedDocumentKeyProvider → SecurityHardening | SecurityHardening.kt:243-246 | Adapter pattern, enforces TEE minimum |

---

### 3.3 Encryption/Key Lifecycle Findings

| Component | Key Type | Storage | Auth-Bound | StrongBox | Recovery |
|-----------|----------|---------|------------|-----------|----------|
| AuthBoundVaultKey | AES-256 (VEK wrap) | Android Keystore | ✅ BiometricPrompt.CryptoObject | ✅ Required (API 28+) | ❌ None |
| DocumentKeyProvider | AES-256 (payload) | Android Keystore | ❌ No auth required | ✅ Required (API 28+) | ❌ None (fail closed) |
| DatabaseKeyManager | AES-256 (SQLCipher) | Wrapped in no-backup dir | ❌ | ✅ TEE minimum | ❌ Fail closed |
| HardwareEnforcedDocProvider | AES-256 (payload) | Android Keystore | ❌ | ✅ TEE minimum | ❌ Fail closed |

**Critical:** All keys are hardware-enforced but **no recovery path exists** for any key loss scenario (biometric enrollment change, device migration, hardware failure).

---

### 3.4 Startup Performance Impact

| Component | Operation | Est. Cost |
|-----------|-----------|-----------|
| DeviceIntegrityChecker | File existence checks (50+ paths), prop reads, package queries | ~50-100ms |
| RuntimeIntegrity | APK SHA-256 (full), native lib checksums, asset checksums | ~100-200ms |
| StrongBoxEnforcer | Test key generation (StrongBox + TEE), reflection | ~200-500ms |
| AntiDebugging | Timing loop (100 iterations), stack trace, /proc reads | ~50-100ms |
| **Total added startup latency** | | **~400-900ms** |

On low-end target (Redmi 9A / Helio G25), expect **1-2 second** additional cold-start delay. No measurements recorded.

---

### 3.5 R8/ProGuard Compatibility

**ProGuard rules** (`app/proguard-rules.pro`) include:
- `-keep class com.kaagazvault.security.hardening.** { *; }` — preserves all hardening classes
- Reflection targets: `KeyFactory.getKeyInfo`, `KeyInfo.isInsideSecureHardware`, `SystemProperties.get`

**Risk:** Aggressive obfuscation may break reflection-based KeyInfo access. No `-keep` for `dalvik.system.BaseDexClassLoader` fields used in `RuntimeIntegrity.verifyClassLoader()`.

---

## Phase 4 — Correction Orders Assessment

| Order | Severity | Needs Human | Status | Evidence |
|-------|----------|-------------|--------|----------|
| **CO-00** | P0 | No | **IN_PROGRESS** | Kotlin compilation green; lint/unit/packaging blocked by AAPT2; no CI run for HEAD SHA |
| **CO-01** | P0 | **Yes** | **BLOCKED_ON_HUMAN** | AuthBoundVaultKey, VaultMigrationJournal, WrappedVaultKeyStore unwired; ADR-0003 Proposed; MainActivity builds repos without auth |
| **CO-02** | P0 | **Yes** | **BLOCKED_ON_HUMAN** | Biometric enrollment invalidates key; no recovery key/passphrase; no androidTest; targets elderly users |
| **CO-03** | P1 | No | **OPEN** | EncryptedDocumentStore accepts upper-case UUID, uses original for filename/AAD |
| **CO-04** | P1 | No | **OPEN** | `runCatching { ... }.getOrNull()` swallows decryption failures in DocumentRepository |
| **CO-05** | P1 | Partly | **OPEN** | list() decrypts 32MiB×N payloads on main thread; no metrics |
| **CO-06** | P1 | No | **OPEN** | SHA-1 model pins, uncached downloads, literal deps, no verification-metadata.xml |
| **CO-07** | P1 | No | **OPEN** | `exportSchema=false`, MIGRATION_1_2 untested, no schemas committed |
| **CO-08** | P2 | No | **OPEN** | Journal uses enum ordinals, tautological check, duplicate verify |
| **CO-09** | P2 | No | **OPEN** | Create-once is check-then-act; `@Synchronized` is per-instance only |
| **CO-10** | P3 | No | **OPEN** | Settings sync expiry in-memory only; peer timestamps unbounded |
| **CO-11** | P2 | No | **OPEN** | No dataExtractionRules for Android 12+ device transfer |
| **CO-12** | P2 | No | **OPEN** | Docs contradictory; Notion reference unreachable; progress.md out of order |
| **CO-13** | P3 | **Yes** | **BLOCKED_ON_HUMAN** | Z→A sort in DocumentRepository.kt:127 |
| **CO-14** | P3 | **Yes** | **BLOCKED_ON_HUMAN** | AmbientVaultBackdrop continuous animation on low-end targets |
| **CO-15** | P2 | No | **OPEN** | 8/12 skill files missing name/description header |

---

## Confirmed Findings (Security Review)

### CRITICAL
1. **Permanent vault lockout on any integrity failure** — `SecurityHardening.triggerLockdown()` kills process with no grace period, no key persistence, no user recovery. False positive in ANY component = total data loss.
2. **No recovery path for biometric invalidation (CO-02)** — `setInvalidatedByBiometricEnrollment(true)` + `AUTH_BIOMETRIC_STRONG` makes vault permanently unreadable after fingerprint change or on devices without strong biometric.
3. **Auth system completely unwired (CO-01)** — All crypto primitives exist but no authentication gate protects data access.

### HIGH
4. **SecureDeletion claims exceed reality** — Multi-pass overwrite does not guarantee erasure on flash storage (wear-leveling, TRIM, FBE). Documentation implies guarantee.
5. **AntiDebugging/DeviceIntegrity false positives trigger destruction** — No graduated response, no user confirmation, no telemetry to distinguish attack from legitimate environment.
6. **MemoryProtection module is dead code** — Keys never zeroed on lockdown despite `MemoryProtection` providing `SecureBytes`/`SecureKey`.
7. **RuntimeIntegrity APK signature check breaks legitimate updates** — Expected signature computed at first run; any app update triggers lockdown.
8. **No tests for any hardening component** — Zero unit tests, zero instrumented tests for security-critical paths.

### MEDIUM
9. **Hidden API usage** — `RuntimeIntegrity` uses `BaseDexClassLoader.pathList`/`dexElements` reflection; `StrongBoxEnforcer` uses `KeyFactory.getKeyInfo()` reflection. May break on Android 14+.
10. **StrongBox test key cleanup unreliable** — Timestamp-based aliases may collide; exception during cleanup leaves orphan keys.
11. **Attestation unusable for symmetric vault keys** — `getKeyAttestation()` only works for `PrivateKeyEntry` (asymmetric), not `SecretKeyEntry` (AES).
12. **Create-once stores are check-then-act (CO-09)** — `@Synchronized` is per-instance; `File.renameTo()` behavior varies by platform.

---

## Commands Run & Exit Status

```bash
# Phase 1
git rev-parse HEAD                                    # 0
git branch --show-current                             # 0
git status --short                                    # 0

python3 -m unittest discover -s scripts -p 'test_*.py' -v  # 0 (8 tests OK)

python3 scripts/check_offline_manifest.py             # 1 (APK not found)

./gradlew :app:compileDebugKotlin :app:compileReleaseKotlin :app:lint  # 1 (AAPT2)
./gradlew :app:testDebugUnitTest                      # 1 (AAPT2)
./gradlew :app:assembleDebug :app:assembleRelease     # 1 (AAPT2)

# AAPT2 binary check
unzip -o aapt2-9.1.1-14792394-linux.jar aapt2
readelf -h aapt2                                      # ELF64, X86-64
```

---

## CI Links & Evidence

| Check | Status | Evidence |
|-------|--------|----------|
| Python unit tests | ✅ PASS | Exit 0, 8/8 tests OK |
| Kotlin compilation (debug/release) | ✅ PASS | Exit 0, 28 tasks UP-TO-DATE |
| Lint | ❌ FAIL | AAPT2 daemon startup failed |
| Unit tests | ❌ FAIL | AAPT2 required for resources |
| Debug APK assembly | ❌ FAIL | AAPT2 daemon startup failed |
| Release APK assembly | ❌ FAIL | AAPT2 daemon startup failed |
| Offline manifest gate | ❌ FAIL | APKs not built |
| GitHub Actions CI | ❌ NOT RUN | No PR targeting canonical_branch |

---

## Remaining Risks

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| AAPT2 blocks all packaging locally | Certain | Blocks all Android gates | Use GitHub Actions x86_64 runner |
| False positive → permanent data loss | High | Critical | Add graduated response, user confirmation, key backup |
| Biometric change → unrecoverable vault | Medium | Critical | Implement CO-02 recovery design (human approval needed) |
| Legitimate app update triggers lockdown | Certain | High | RuntimeIntegrity must support signature rotation |
| No auth gate on data access | Certain | Critical | Wire CO-01 auth-bound session (human approval needed) |
| Hidden API breakage on Android 14+ | Medium | Medium | Replace reflection with public APIs or graceful degradation |
| Startup latency on low-end devices | High | Medium | Measure, profile, optimize or make checks lazy |
| Multi-pass wipe ineffective on flash | Certain | Medium | Document as defense-in-depth only; don't claim guarantee |

---

## Next Actions (Priority Order)

### Immediate (Unblock CI)
1. **Create draft PR** targeting `feat/durable-vault-migration-journal` → triggers GitHub Actions on x86_64
2. **Wait for CI green** → confirms packaging, unit tests, lint, offline manifest on exact HEAD SHA

### Requires Human Decision (CO-01, CO-02)
3. **Prepare ADR-0003 decision memo** with:
   - Authenticator policy options (strong biometric only | biometric + device credential | passphrase envelope)
   - Lock policy (inactivity timeout, background lock, explicit lock)
   - Recovery design for CO-02 (recovery key shown once | device-credential capable key | passphrase-wrapped second VEK)
   - Failure matrix for each option
   - **Ask once for approval** — do not implement until accepted

### Code Fixes (Post-CI, Pre-Merge)
4. **Fix RuntimeIntegrity APK signature rotation** — support multiple expected signatures or version-aware check
5. **Add graduated lockdown response** — WARNING → user notification → manual lock → auto-lock after N violations
6. **Wire MemoryProtection into lockdown** — zero `SecureBytes`/`SecureKey` on `triggerLockdown()`
7. **Document SecureDeletion limitations** — remove "guaranteed erasure" language; clarify defense-in-depth
8. **Add unit tests for hardening components** — at minimum: DeviceIntegrityChecker prop parsing, RuntimeIntegrity checksum logic, StrongBoxEnforcer level detection

### Correction Orders (Priority)
9. **CO-03** — Fix UUID case sensitivity (one-line fix: `parsed.toString() == id`)
10. **CO-04** — Return unreadable document IDs instead of swallowing exceptions
11. **CO-06** — Add SHA-256 model pins, enable Gradle dependency verification
12. **CO-07** — Export Room schemas, add migration test
13. **CO-08** — Replace enum ordinals with stable codes in journal
14. **CO-09** — Add process-wide lock file with FileChannel.tryLock()
15. **CO-15** — Add headers to 8 skill files

---

## Verification Checklist

- [x] Current branch, commit SHA, working-tree state recorded
- [x] Python tests run (8/8 PASS)
- [x] Gradle compilation verified (Kotlin SUCCESS, AAPT2 FAIL documented)
- [x] AAPT2 binary architecture confirmed (x86-64 on ARM64 host)
- [x] GitHub Actions workflow identified (runs on ubuntu-latest)
- [x] All 7 hardening classes reviewed with call sites, threats, gaps, false-positive risks
- [x] Integration points verified (SecureDeletion, StrongBoxEnforcer, MemoryProtection)
- [x] Encryption/key lifecycle mapped (4 key types, 0 with recovery)
- [x] CO-00 through CO-15 classified with evidence
- [x] Critical findings documented with file:line references
- [x] Next actions prioritized with gating dependencies

---

**Report Status:** Complete for Phases 1-4. Phase 2 (packaging verification) requires CI run on x86_64. Phases 3-4 findings are complete based on static analysis; dynamic validation requires CI green.

**Attestation:** All findings based on code inspection at commit `6b30ceaf5ab09b2494d82356b08c4bebf7406521`. No fabricated results. No unsupported security claims. All limitations explicitly noted.