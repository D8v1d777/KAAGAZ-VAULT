package com.kaagazvault.security.hardening

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import java.io.File

/**
 * Comprehensive device integrity checker for root, emulator, and tamper detection.
 *
 * This class performs multiple independent checks to detect:
 * - Root access (su binary, Magisk, SuperSU, etc.)
 * - Emulator environments
 * - Debuggable builds
 * - Hooking frameworks (Xposed, Frida, etc.)
 * - System integrity compromises
 *
 * All checks are defensive and fail-open for legitimate environments.
 * Positive detections should trigger secure lockdown, not crash.
 */
internal class DeviceIntegrityChecker(private val context: Context) {

    companion object {
        private const val TAG = "DeviceIntegrity"

        /** Known su binary locations */
        private val SU_PATHS = arrayOf(
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/system/su",
            "/system/bin/.ext/.su",
            "/system/usr/we-need-root/su-backup",
            "/system/xbin/mu",
            "/data/local/su",
            "/data/local/bin/su",
            "/data/local/xbin/su",
            "/data/local/sbin/su",
            "/su/bin/su",
            "/magisk/.core/bin/su",
            "/magisk/bin/su",
            "/debug_ramdisk/su"
        )

        /** Known Magisk paths */
        private val MAGISK_PATHS = arrayOf(
            "/sbin/magisk",
            "/sbin/magiskhide",
            "/data/adb/magisk",
            "/data/adb/magisk.img",
            "/data/adb/magisk_simple",
            "/data/adb/modules",
            "/dev/.magisk.unblock",
            "/dev/.magisk",
            "/magisk",
            "/.magisk"
        )

        /** Known SuperSU paths */
        private val SUPERSU_PATHS = arrayOf(
            "/system/etc/init.d/99SuperSUDaemon",
            "/system/etc/.has_su_daemon",
            "/system/etc/.installed_su_daemon",
            "/system/xbin/daemonsu",
            "/system/xbin/sugote",
            "/system/xbin/sugote-mksh",
            "/system/xbin/supolicy",
            "/cache/superuser.log",
            "/data/superuser.log"
        )

        /** Known hooking framework paths */
        private val HOOK_FRAMEWORK_PATHS = arrayOf(
            "/data/data/de.robv.android.xposed.installer",
            "/data/data/com.saurik.substrate",
            "/data/data/com.frida.frida-server",
            "/data/local/tmp/frida-server",
            "/data/local/tmp/re.frida.server",
            "/system/framework/XposedBridge.jar",
            "/system/lib/libxposed_art.so",
            "/system/lib64/libxposed_art.so",
            "/system/lib/libsubstrate.so",
            "/system/lib64/libsubstrate.so",
            "/data/app/com.topjohnwu.magisk",
            "/data/app/com.koushikdutta.superuser",
            "/data/app/eu.chainfire.supersu"
        )

        /** Emulator detection properties */
        private val EMULATOR_BUILD_PROPS = mapOf(
            "ro.product.model" to listOf("sdk", "google_sdk", "emulator", "android sdk built for x86"),
            "ro.product.brand" to listOf("generic", "google"),
            "ro.product.device" to listOf("generic", "emulator"),
            "ro.hardware" to listOf("goldfish", "ranchu"),
            "ro.kernel.qemu" to listOf("1"),
            "ro.boot.qemu" to listOf("1"),
            "init.svc.qemud" to listOf("running"),
            "ro.build.fingerprint" to listOf("generic", "test-keys")
        )

        /** Dangerous props that indicate tampering */
        private val DANGEROUS_PROPS = arrayOf(
            "ro.debuggable",
            "ro.secure",
            "ro.build.type",
            "ro.build.tags"
        )
    }

    /** Result of integrity check */
    data class IntegrityResult(
        val isRooted: Boolean,
        val isEmulator: Boolean,
        val isDebuggable: Boolean,
        val hasHookingFramework: Boolean,
        val hasTamperedSystemProps: Boolean,
        val hasTestKeys: Boolean,
        val selinuxEnforcing: Boolean,
        val details: List<String>
    ) {
        val isCompromised: Boolean
            get() = isRooted || isEmulator || isDebuggable || hasHookingFramework || hasTamperedSystemProps
    }

    /**
     * Performs comprehensive integrity check.
     * Should be called on app startup and periodically during runtime.
     */
    fun checkIntegrity(): IntegrityResult {
        val details = mutableListOf<String>()

        val isDebuggable = isDebugBuild()
        if (isDebuggable) details.add("Debug build detected")

        val isRooted = checkRootAccess(details)
        val isEmulator = checkEmulator(details)
        val hasHookingFramework = checkHookingFrameworks(details)
        val hasTamperedSystemProps = checkSystemProperties(details)
        val hasTestKeys = checkTestKeys(details)
        val selinuxEnforcing = checkSelinuxStatus(details)

        val result = IntegrityResult(
            isRooted = isRooted,
            isEmulator = isEmulator,
            isDebuggable = isDebuggable,
            hasHookingFramework = hasHookingFramework,
            hasTamperedSystemProps = hasTamperedSystemProps,
            hasTestKeys = hasTestKeys,
            selinuxEnforcing = selinuxEnforcing,
            details = details
        )

        if (result.isCompromised) {
            Log.w(TAG, "Device integrity compromised: ${details.joinToString(", ")}")
        } else {
            Log.d(TAG, "Device integrity check passed")
        }

        return result
    }

    /** Check if app is debuggable */
    private fun isDebugBuild(): Boolean {
        return (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }

    /** Check for root access indicators */
    private fun checkRootAccess(details: MutableList<String>): Boolean {
        // Check su binary existence
        for (path in SU_PATHS) {
            if (File(path).exists()) {
                details.add("Root binary found: $path")
                return true
            }
        }

        // Check Magisk
        for (path in MAGISK_PATHS) {
            if (File(path).exists()) {
                details.add("Magisk found: $path")
                return true
            }
        }

        // Check SuperSU
        for (path in SUPERSU_PATHS) {
            if (File(path).exists()) {
                details.add("SuperSU found: $path")
                return true
            }
        }

        // Check if we can execute su
        try {
            val process = Runtime.getRuntime().exec("which su")
            process.waitFor()
            if (process.exitValue() == 0) {
                details.add("'which su' command succeeded")
                return true
            }
        } catch (_: Exception) {
            // Ignore
        }

        // Check for apps with root management
        val pm = context.packageManager
        val rootPackages = arrayOf(
            "com.noshufou.android.su",
            "com.noshufou.android.su.elite",
            "eu.chainfire.supersu",
            "com.koushikdutta.superuser",
            "com.thirdparty.superuser",
            "com.yellowes.su",
            "com.topjohnwu.magisk",
            "com.koushikdutta.rommanager",
            "com.koushikdutta.rommanager.license",
            "com.dimonvideo.luckypatcher",
            "com.chelpus.lackypatch"
        )

        for (pkg in rootPackages) {
            try {
                pm.getPackageInfo(pkg, 0)
                details.add("Root management app installed: $pkg")
                return true
            } catch (_: PackageManager.NameNotFoundException) {
                // Not installed
            }
        }

        // Check for busybox
        try {
            val process = Runtime.getRuntime().exec("busybox")
            process.waitFor()
            if (process.exitValue() == 0) {
                details.add("Busybox available")
                return true
            }
        } catch (_: Exception) {
            // Ignore
        }

        return false
    }

    /** Check for emulator environment */
    private fun checkEmulator(details: MutableList<String>): Boolean {
        // Check build properties
        for ((prop, suspiciousValues) in EMULATOR_BUILD_PROPS) {
            val value = try {
                getSystemProperty(prop).lowercase()
            } catch (_: Exception) {
                ""
            }
            for (suspicious in suspiciousValues) {
                if (value.contains(suspicious, ignoreCase = true)) {
                    details.add("Emulator property: $prop=$value")
                    return true
                }
            }
        }

        // Check for emulator-specific files
        val emulatorFiles = arrayOf(
            "/sys/class/misc/android_adb",
            "/sys/class/misc/android_adb_enable",
            "/dev/qemu_pipe",
            "/dev/socket/qemud",
            "/system/lib/libc_malloc_debug_qemu.so",
            "/system/lib64/libc_malloc_debug_qemu.so",
            "/system/bin/qemu-props",
            "/system/bin/qemud",
            "/init.goldfish.rc",
            "/init.ranchu.rc"
        )

        for (file in emulatorFiles) {
            if (File(file).exists()) {
                details.add("Emulator file found: $file")
                return true
            }
        }

        // Check phone number (emulators often have null or specific values)
        try {
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as android.telephony.TelephonyManager
            val phoneNumber = telephonyManager.line1Number
            if (phoneNumber == null || phoneNumber == "15555215554" || phoneNumber == "15555215556") {
                details.add("Emulator phone number detected: $phoneNumber")
                return true
            }
        } catch (_: Exception) {
            // Ignore
        }

        // Check IMEI
        try {
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as android.telephony.TelephonyManager
            val imei = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                telephonyManager.imei
            } else {
                @Suppress("DEPRECATION")
                telephonyManager.deviceId
            }
            if (imei == null || imei == "000000000000000" || imei == "123456789012345") {
                details.add("Emulator IMEI detected: $imei")
                return true
            }
        } catch (_: Exception) {
            // Ignore
        }

        return false
    }

    /** Check for hooking frameworks */
    private fun checkHookingFrameworks(details: MutableList<String>): Boolean {
        for (path in HOOK_FRAMEWORK_PATHS) {
            if (File(path).exists()) {
                details.add("Hooking framework found: $path")
                return true
            }
        }

        // Check for Xposed in loaded libraries
        try {
            val maps = File("/proc/self/maps").readText()
            if (maps.contains("xposed") || maps.contains("frida") || maps.contains("substrate")) {
                details.add("Hooking framework detected in memory maps")
                return true
            }
        } catch (_: Exception) {
            // Ignore
        }

        // Check for Frida server listening
        try {
            val netstat = Runtime.getRuntime().exec("netstat -tlnp").inputStream.bufferedReader().readText()
            if (netstat.contains("frida") || netstat.contains("127.0.0.1:27042")) {
                details.add("Frida server detected")
                return true
            }
        } catch (_: Exception) {
            // Ignore
        }

        return false
    }

    /** Check system properties for tampering */
    private fun checkSystemProperties(details: MutableList<String>): Boolean {
        // Check ro.secure
        val roSecure = getSystemProperty("ro.secure")
        if (roSecure != "1") {
            details.add("ro.secure != 1: $roSecure")
            return true
        }

        // Check ro.debuggable
        val roDebuggable = getSystemProperty("ro.debuggable")
        if (roDebuggable == "1" && !isDebugBuild()) {
            details.add("ro.debuggable=1 on release build")
            return true
        }

        // Check ro.build.type
        val buildType = getSystemProperty("ro.build.type")
        if (buildType != "user") {
            details.add("Non-user build type: $buildType")
            // Not necessarily compromised, but worth noting
        }

        // Check ro.build.tags
        val buildTags = getSystemProperty("ro.build.tags")
        if (buildTags.contains("test-keys")) {
            details.add("Test keys detected in build tags")
            return true
        }

        return false
    }

    /**
     * Read a system property using reflection (SystemProperties is hidden API).
     * Returns empty string if property not found or access fails.
     */
    private fun getSystemProperty(prop: String): String {
        return try {
            val systemPropertiesClass = Class.forName("android.os.SystemProperties")
            val getMethod = systemPropertiesClass.getDeclaredMethod("get", String::class.java)
            getMethod.isAccessible = true
            getMethod.invoke(null, prop) as? String ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    /** Check for test keys */
    private fun checkTestKeys(details: MutableList<String>): Boolean {
        val fingerprint = Build.FINGERPRINT.lowercase()
        val tags = Build.TAGS.lowercase()

        if (fingerprint.contains("test-keys") || tags.contains("test-keys")) {
            details.add("Test keys in build fingerprint/tags")
            return true
        }

        return false
    }

    /** Check SELinux status */
    private fun checkSelinuxStatus(details: MutableList<String>): Boolean {
        try {
            val status = File("/sys/fs/selinux/enforce").readText().trim()
            val enforcing = status == "1"
            if (!enforcing) {
                details.add("SELinux not enforcing")
            }
            return enforcing
        } catch (_: Exception) {
            details.add("Could not read SELinux status")
            return false
        }
    }

    /** Quick check for runtime - can be called frequently */
    fun quickCheck(): Boolean {
        // Quick checks that are fast
        if (isDebugBuild()) return false

        // Check a few key su paths
        for (path in SU_PATHS.take(5)) {
            if (File(path).exists()) return false
        }

        // Check Magisk
        for (path in MAGISK_PATHS.take(3)) {
            if (File(path).exists()) return false
        }

        return true
    }
}

/**
 * Factory for creating DeviceIntegrityChecker with proper context.
 * Handles context safely to avoid leaks.
 */
internal object DeviceIntegrityCheckerFactory {
    @Suppress("UNUSED_PARAMETER")
    fun create(context: Context): DeviceIntegrityChecker {
        return DeviceIntegrityChecker(context.applicationContext)
    }
}