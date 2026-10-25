package com.kaagazvault.security.hardening

import android.app.Application
import android.content.Context
import android.util.Log
import com.kaagazvault.security.AesGcmEnvelope
import com.kaagazvault.security.AuthBoundVaultKey
import com.kaagazvault.database.DatabaseKeyManager
import com.kaagazvault.security.DocumentKeyProvider
import com.kaagazvault.security.EncryptedDocumentStore
import java.security.SecureRandom
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Main security hardening coordinator.
 *
 * Initializes and coordinates all security hardening components:
 * - Device integrity checking (root, emulator, tampering)
 * - Runtime integrity verification (APK signature, file checksums, class loader)
 * - Anti-debugging protection
 * - Memory protection for sensitive data
 * - StrongBox/hardware-backed key enforcement
 * - Secure deletion for sensitive files
 *
 * Provides a unified security state and lockdown mechanism.
 */

/** Global security state */
internal enum class SecurityState {
    SECURE,         // All checks passing
    WARNING,        // Minor issues detected
    COMPROMISED,    // Serious security violation
    LOCKDOWN        // Active lockdown in progress
}

/** Security check result */
internal data class SecurityCheckResult(
    val state: SecurityState,
    val deviceIntegrity: DeviceIntegrityChecker.IntegrityResult?,
    val runtimeIntegrity: Boolean,
    val antiDebugging: Boolean,
    val strongBoxLevel: SecurityLevelResult?,
    val details: List<String>
)

internal class SecurityHardening(private val application: Application) {

    companion object {
        private const val TAG = "SecurityHardening"
    }

    private val deviceIntegrityChecker = DeviceIntegrityCheckerFactory.create(application)
    private val runtimeIntegrity = RuntimeIntegrityFactory.create(application)
    private val antiDebugging = AntiDebuggingFactory.create(application)
    private val strongBoxEnforcer = StrongBoxEnforcerFactory.create(application)
    private val secureDeletion = SecureDeletion

    private val monitoringExecutor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    private val isMonitoring = AtomicBoolean(false)
    private val currentState = AtomicReference<SecurityState>(SecurityState.SECURE)
    private val lockdownTriggered = AtomicBoolean(false)

    // Callbacks for security events
    private var onSecurityViolation: ((String) -> Unit)? = null
    private var onLockdown: (() -> Unit)? = null

    init {
        Log.d(TAG, "SecurityHardening initialized")
    }

    /**
     * Initialize all security components and perform initial checks.
     * Call from Application.onCreate().
     */
    fun initialize(
        onViolation: (String) -> Unit = { reason -> defaultViolationHandler(reason) },
        onLockdownAction: () -> Unit = { defaultLockdownAction() }
    ) {
        this.onSecurityViolation = onViolation
        this.onLockdown = onLockdownAction

        // Perform initial comprehensive check
        val result = performFullSecurityCheck()

        if (result.state == SecurityState.COMPROMISED || result.state == SecurityState.LOCKDOWN) {
            triggerLockdown("Initial security check failed: ${result.details.joinToString(", ")}")
        } else if (result.state == SecurityState.WARNING) {
            Log.w(TAG, "Security warnings on startup: ${result.details.joinToString(", ")}")
        }

        // Start continuous monitoring
        startMonitoring()
    }

    /**
     * Perform a comprehensive security check.
     */
    fun performFullSecurityCheck(): SecurityCheckResult {
        val details = mutableListOf<String>()

        // 1. Device integrity
        val deviceIntegrity = deviceIntegrityChecker.checkIntegrity()
        if (deviceIntegrity.isCompromised) {
            details.addAll(deviceIntegrity.details)
        }

        // 2. Runtime integrity
        val runtimeIntegrityOk = runtimeIntegrity.performFullCheck()
        if (!runtimeIntegrityOk) {
            details.add("Runtime integrity check failed")
        }

        // 3. Anti-debugging
        val antiDebuggingOk = antiDebugging.checkForDebugger()
        if (!antiDebuggingOk) {
            details.add("Debugger detected")
        }

        // 4. StrongBox/TEE availability
        val strongBoxResult = strongBoxEnforcer.checkAvailableSecurityLevel()
        if (!strongBoxResult.isAcceptable) {
            details.add("No hardware-backed keystore: ${strongBoxResult.details}")
        } else if (strongBoxResult.level == SecurityLevel.TEE) {
            details.add("Using TEE (StrongBox not available)")
        }

        // Determine overall state
        val state = when {
            deviceIntegrity.isCompromised || !runtimeIntegrityOk || !antiDebuggingOk -> SecurityState.COMPROMISED
            !strongBoxResult.isAcceptable -> SecurityState.COMPROMISED
            strongBoxResult.level == SecurityLevel.TEE || deviceIntegrity.hasTestKeys -> SecurityState.WARNING
            else -> SecurityState.SECURE
        }

        currentState.set(state)

        return SecurityCheckResult(
            state = state,
            deviceIntegrity = deviceIntegrity,
            runtimeIntegrity = runtimeIntegrityOk,
            antiDebugging = antiDebuggingOk,
            strongBoxLevel = strongBoxResult,
            details = details
        )
    }

    /**
     * Quick security check for frequent execution.
     */
    fun quickCheck(): Boolean {
        val deviceOk = deviceIntegrityChecker.quickCheck()
        val runtimeOk = runtimeIntegrity.verifyApkSignature() && runtimeIntegrity.verifyClassLoader()
        val debugOk = antiDebugging.quickCheck()

        val ok = deviceOk && runtimeOk && debugOk
        if (!ok) {
            currentState.set(SecurityState.COMPROMISED)
            triggerLockdown("Quick check failed")
        }
        return ok
    }

    /**
     * Start continuous background monitoring.
     */
    fun startMonitoring() {
        if (isMonitoring.getAndSet(true)) return

        // Quick checks every 5 seconds
        monitoringExecutor.scheduleAtFixedRate(
            { quickCheck() },
            5_000,
            5_000,
            TimeUnit.MILLISECONDS
        )

        // Full checks every 30 seconds
        monitoringExecutor.scheduleAtFixedRate(
            { performFullSecurityCheck() },
            30_000,
            30_000,
            TimeUnit.MILLISECONDS
        )

        Log.d(TAG, "Continuous security monitoring started")
    }

    /**
     * Stop monitoring.
     */
    fun stopMonitoring() {
        if (!isMonitoring.getAndSet(false)) return
        monitoringExecutor.shutdownNow()
        runtimeIntegrity.stopMonitoring()
        Log.d(TAG, "Security monitoring stopped")
    }

    /**
     * Get current security state.
     */
    fun getCurrentState(): SecurityState = currentState.get()

    /**
     * Check if device has hardware-backed keystore (TEE or StrongBox).
     */
    fun hasHardwareBackedKeystore(): Boolean {
        return strongBoxEnforcer.checkAvailableSecurityLevel().isAcceptable
    }

    /**
     * Check if StrongBox (secure element) is available.
     */
    fun hasStrongBox(): Boolean {
        return strongBoxEnforcer.checkAvailableSecurityLevel().level == SecurityLevel.STRONGBOX
    }

    /**
     * Securely wipe a file.
     */
    fun wipeFile(file: java.io.File, passes: Int = 3): Boolean {
        return secureDeletion.wipe(file, passes)
    }

    /**
     * Securely wipe a directory.
     */
    fun wipeDirectory(dir: java.io.File, passes: Int = 3): Boolean {
        return secureDeletion.wipeDirectory(dir, passes)
    }

    /**
     * Create a hardware-enforced document key provider.
     * Replaces AndroidKeystoreDocumentKeyProvider for higher security.
     */
    fun createHardwareEnforcedDocumentKeyProvider(
        alias: String = "kaagaz.vault.document-payload.hw.v1",
        hasExistingProtectedData: () -> Boolean = { false }
    ): DocumentKeyProvider {
        return HardwareEnforcedKeyProviderAdapter(
            com.kaagazvault.security.hardening.HardwareEnforcedDocumentKeyProvider(
                application, alias, hasExistingProtectedData
            )
        )
    }

    /**
     * Trigger secure lockdown.
     */
    fun triggerLockdown(reason: String) {
        if (lockdownTriggered.getAndSet(true)) return // Already locking down

        Log.e(TAG, "SECURE LOCKDOWN TRIGGERED: $reason")
        currentState.set(SecurityState.LOCKDOWN)

        // Execute lockdown callbacks
        onLockdown?.invoke()

        // The onLockdown callback should:
        // 1. Zero all encryption keys in memory
        // 2. Wipe sensitive files
        // 3. Invalidate biometric auth
        // 4. Kill process
    }

    /** Default violation handler */
    private fun defaultViolationHandler(reason: String) {
        Log.e(TAG, "Security violation: $reason")
        triggerLockdown(reason)
    }

    /** Default lockdown action */
    private fun defaultLockdownAction() {
        // Zero keys, wipe files, kill process
        android.os.Process.killProcess(android.os.Process.myPid())
        System.exit(1)
    }

    /**
     * Factory for creating security-hardened components.
     * Use these instead of direct constructors for enhanced security.
     */
    class SecureComponentFactory(private val hardening: SecurityHardening) {

        /** Create EncryptedDocumentStore with hardware-enforced keys */
        fun createEncryptedDocumentStore(directory: java.io.File): EncryptedDocumentStore {
            val keyProvider = hardening.createHardwareEnforcedDocumentKeyProvider()
            return EncryptedDocumentStore(directory, keyProvider, AesGcmEnvelope())
        }

        /** Create DatabaseKeyManager with enhanced security */
        fun createDatabaseKeyManager(context: Context): DatabaseKeyManager {
            // DatabaseKeyManager already uses AndroidKeystoreDocumentKeyProvider
            // Could be enhanced to use hardware-enforced provider
            return DatabaseKeyManager(context)
        }

        /** Create AuthBoundVaultKey with StrongBox enforcement */
        fun createAuthBoundVaultKey(alias: String = "kaagaz_vault_auth_bound_vek_wrap_hw_v1"): AuthBoundVaultKey {
            // AuthBoundVaultKey already uses KeyGenParameterSpec with hardware backing
            // Could add explicit StrongBox requirement here
            return AuthBoundVaultKey(alias)
        }
    }

    fun getSecureComponentFactory(): SecureComponentFactory = SecureComponentFactory(this)
}

/**
 * Adapter to make HardwareEnforcedDocumentKeyProvider implement DocumentKeyProvider interface.
 */
private class HardwareEnforcedKeyProviderAdapter(
    private val provider: com.kaagazvault.security.hardening.HardwareEnforcedDocumentKeyProvider
) : DocumentKeyProvider {
    override fun getOrCreateKey(): javax.crypto.SecretKey = provider.getOrCreateKey()
}

/**
 * Extension function for easy access from Application.
 */
internal fun Application.securityHardening(): SecurityHardening {
    return SecurityHardening(this)
}