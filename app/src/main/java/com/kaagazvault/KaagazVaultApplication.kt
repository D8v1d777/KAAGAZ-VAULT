package com.kaagazvault

import android.app.Application
import android.util.Log
import com.kaagazvault.security.hardening.SecurityHardening
import com.kaagazvault.security.hardening.SecurityState
import com.kaagazvault.security.hardening.StrongBoxEnforcer

/**
 * Main Application class for Kaagaz Vault.
 *
 * Initializes security hardening on startup and provides
 * secure component factory for the rest of the app.
 */
internal class KaagazVaultApplication : Application() {

    companion object {
        private const val TAG = "KaagazVaultApplication"
    }

    private var securityHardening: SecurityHardening? = null
    private var secureComponentFactory: SecurityHardening.SecureComponentFactory? = null

    override fun onCreate() {
        super.onCreate()

        Log.d(TAG, "Kaagaz Vault starting - initializing security hardening")

        // Initialize security hardening
        securityHardening = SecurityHardening(this).apply {
            initialize(
                onViolation = { reason ->
                    Log.e(TAG, "SECURITY VIOLATION: $reason")
                    // Could show user-facing alert here before lockdown
                },
                onLockdownAction = {
                    Log.e(TAG, "SECURE LOCKDOWN INITIATED")
                    // The SecurityHardening will handle key zeroing, file wiping, process kill
                }
            )
        }

        secureComponentFactory = securityHardening!!.getSecureComponentFactory()

        // Log security status
        val checkResult = securityHardening!!.performFullSecurityCheck()
        Log.i(TAG, "Security state: ${checkResult.state}")
        val strongBoxLevel = checkResult.strongBoxLevel
        if (strongBoxLevel != null) {
            Log.i(TAG, "Hardware-backed keystore level: ${strongBoxLevel.level.name}")
        }
        if (checkResult.state != SecurityState.SECURE) {
            Log.w(TAG, "Security warnings: ${checkResult.details.joinToString(", ")}")
        }
    }

    override fun onTerminate() {
        securityHardening?.stopMonitoring()
        super.onTerminate()
    }

    /**
     * Get the security hardening instance for manual checks.
     */
    internal fun getSecurityHardening(): SecurityHardening = securityHardening!!

    /**
     * Get the secure component factory for creating hardened components.
     */
    internal fun getSecureComponentFactory(): SecurityHardening.SecureComponentFactory = secureComponentFactory!!

    /**
     * Quick security check - can be called from activities/fragments.
     */
    internal fun quickSecurityCheck(): Boolean = securityHardening?.quickCheck() ?: false

    /**
     * Check if device is compromised (rooted, emulated, debugged, etc.).
     */
    internal fun isDeviceCompromised(): Boolean {
        return securityHardening?.getCurrentState() != SecurityState.SECURE
    }
}