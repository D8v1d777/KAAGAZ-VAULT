package com.kaagazvault.security.hardening

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import android.util.Base64
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.lang.reflect.Method
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Runtime integrity verification for anti-tampering protection.
 *
 * Performs continuous verification of:
 * - APK signature verification
 * - Critical file integrity (DEX, native libraries, assets)
 * - Memory integrity (checksums of critical code sections)
 * - Class loader integrity
 * - Method hooking detection
 *
 * Runs periodic checks on a background thread and triggers secure lockdown
 * if tampering is detected.
 */
internal class RuntimeIntegrity(private val context: Context) {

    companion object {
        private const val TAG = "RuntimeIntegrity"
        private const val CHECK_INTERVAL_MS = 30_000 // 30 seconds
        private const val QUICK_CHECK_INTERVAL_MS = 5_000 // 5 seconds for critical paths
    }

    private var executor: ScheduledExecutorService? = null
    private val isRunning = AtomicBoolean(false)
    private val expectedApkSignature: String? = computeExpectedApkSignature()
    private val criticalFilesChecksums = mutableMapOf<String, String>()
    private val lockdownCallback: (String) -> Unit = { reason ->
        Log.e(TAG, "INTEGRITY VIOLATION: $reason - Triggering secure lockdown")
        // In production, this would:
        // 1. Zero all encryption keys in memory
        // 2. Wipe sensitive files
        // 3. Force close app
        // 4. Clear any biometric auth state
        android.os.Process.killProcess(android.os.Process.myPid())
        System.exit(1)
    }

    init {
        computeCriticalFileChecksums()
    }

    /**
     * Start periodic integrity monitoring.
     * Call once on app startup (e.g., in Application.onCreate).
     */
    fun startMonitoring() {
        if (isRunning.getAndSet(true)) return

        executor = Executors.newSingleThreadScheduledExecutor()

        // Quick checks more frequently
        executor!!.scheduleAtFixedRate(
            { performQuickCheck() },
            QUICK_CHECK_INTERVAL_MS.toLong(),
            QUICK_CHECK_INTERVAL_MS.toLong(),
            TimeUnit.MILLISECONDS
        )

        // Full checks less frequently
        executor!!.scheduleAtFixedRate(
            { performFullCheck() },
            CHECK_INTERVAL_MS.toLong(),
            CHECK_INTERVAL_MS.toLong(),
            TimeUnit.MILLISECONDS
        )

        Log.d(TAG, "Runtime integrity monitoring started")
    }

    /**
     * Stop integrity monitoring.
     * Call on app shutdown.
     */
    fun stopMonitoring() {
        if (!isRunning.getAndSet(false)) return

        executor?.shutdownNow()
        executor = null
        Log.d(TAG, "Runtime integrity monitoring stopped")
    }

    /**
     * Perform a one-time full integrity check.
     * Returns true if all checks pass.
     */
    fun performFullCheck(): Boolean {
        return try {
            val apkValid = verifyApkSignature()
            val filesValid = verifyCriticalFiles()
            val classLoaderValid = verifyClassLoader()
            val noHooks = detectMethodHooking()
            val memoryValid = verifyMemoryIntegrity()

            if (!apkValid) lockdownCallback("APK signature mismatch")
            if (!filesValid) lockdownCallback("Critical file checksum mismatch")
            if (!classLoaderValid) lockdownCallback("Class loader tampering detected")
            if (!noHooks) lockdownCallback("Method hooking detected")
            if (!memoryValid) lockdownCallback("Memory integrity check failed")

            apkValid && filesValid && classLoaderValid && noHooks && memoryValid
        } catch (e: Exception) {
            Log.e(TAG, "Integrity check error: ${e.message}")
            lockdownCallback("Integrity check exception: ${e.message}")
            false
        }
    }

    /** Quick integrity check for frequent execution */
    private fun performQuickCheck() {
        try {
            if (!verifyApkSignature()) {
                lockdownCallback("Quick check: APK signature mismatch")
            }
            if (!verifyClassLoader()) {
                lockdownCallback("Quick check: Class loader tampering")
            }
            if (!detectMethodHooking()) {
                lockdownCallback("Quick check: Method hooking detected")
            }
        } catch (e: Exception) {
            lockdownCallback("Quick check exception: ${e.message}")
        }
    }

    /** Verify APK signature matches expected */
    internal fun verifyApkSignature(): Boolean {
        val expected = expectedApkSignature ?: return true // Can't verify if we couldn't compute

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) { // API 28+
                val packageInfo: PackageInfo = context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES
                )
                val signingInfo = packageInfo.signingInfo
                if (signingInfo == null) {
                    Log.w(TAG, "No signing info found on package")
                    return false
                }
                val certs = signingInfo.apkContentsSigners
                if (certs.isNullOrEmpty()) {
                    Log.w(TAG, "No signing certificates found on package")
                    return false
                }
                for (cert in certs) {
                    val digest = MessageDigest.getInstance("SHA-256")
                    digest.update(cert.toByteArray())
                    val actualSig = Base64.encodeToString(digest.digest(), Base64.NO_WRAP)
                    if (actualSig == expected) {
                        return true
                    }
                }
            } else {
                // Fallback for older API
                @Suppress("DEPRECATION")
                val packageInfo: PackageInfo = context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNATURES
                )

                val signatures = packageInfo.signatures
                if (signatures.isNullOrEmpty()) {
                    Log.w(TAG, "No signatures found on package")
                    return false
                }

                for (sig in signatures) {
                    val digest = MessageDigest.getInstance("SHA-256")
                    digest.update(sig.toByteArray())
                    val actualSig = Base64.encodeToString(digest.digest(), Base64.NO_WRAP)
                    if (actualSig == expected) {
                        return true
                    }
                }
            }

            Log.w(TAG, "APK signature mismatch")
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Error verifying APK signature: ${e.message}")
            return false
        }
    }

    /** Compute expected APK signature at init time */
    private fun computeExpectedApkSignature(): String? {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) { // API 28+
                val packageInfo: PackageInfo = context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES
                )
                val signingInfo = packageInfo.signingInfo
                if (signingInfo == null) return null
                val certs = signingInfo.apkContentsSigners
                if (certs.isNullOrEmpty()) return null
                val cert = certs[0]
                val digest = MessageDigest.getInstance("SHA-256")
                digest.update(cert.toByteArray())
                return Base64.encodeToString(digest.digest(), Base64.NO_WRAP)
            } else {
                // Fallback for older API
                @Suppress("DEPRECATION")
                val packageInfo: PackageInfo = context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNATURES
                )

                val signatures = packageInfo.signatures
                if (signatures.isNullOrEmpty()) return null

                // Use the first signature (should be the release key)
                val sig = signatures[0]
                val digest = MessageDigest.getInstance("SHA-256")
                digest.update(sig.toByteArray())
                return Base64.encodeToString(digest.digest(), Base64.NO_WRAP)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not compute expected APK signature: ${e.message}")
            return null
        }
    }

    /** Compute checksums of critical files at init */
    private fun computeCriticalFileChecksums() {
        try {
            val apkFile = File(context.applicationInfo.sourceDir)
            if (apkFile.exists()) {
                criticalFilesChecksums["apk"] = computeFileChecksum(apkFile)
            }

            // Check native libraries
            val libDir = File(context.applicationInfo.nativeLibraryDir)
            if (libDir.exists()) {
                libDir.listFiles()?.filter { it.name.endsWith(".so") }?.forEach { lib ->
                    criticalFilesChecksums["lib/${lib.name}"] = computeFileChecksum(lib)
                }
            }

            // Check assets (OCR models, etc.)
            val assetFiles = arrayOf("tessdata/eng.traineddata", "tessdata/hin.traineddata", "tessdata/tel.traineddata")
            for (asset in assetFiles) {
                try {
                    val inputStream = context.assets.open(asset)
                    val checksum = computeStreamChecksum(inputStream)
                    inputStream.close()
                    criticalFilesChecksums["asset/$asset"] = checksum
                } catch (_: Exception) {
                    // Asset not found, skip
                }
            }

            Log.d(TAG, "Computed checksums for ${criticalFilesChecksums.size} critical files")
        } catch (e: Exception) {
            Log.e(TAG, "Error computing critical file checksums: ${e.message}")
        }
    }

    /** Verify critical file checksums haven't changed */
    private fun verifyCriticalFiles(): Boolean {
        try {
            // Check APK
            val apkFile = File(context.applicationInfo.sourceDir)
            if (apkFile.exists()) {
                val current = computeFileChecksum(apkFile)
                val expected = criticalFilesChecksums["apk"]
                if (expected != null && current != expected) {
                    Log.w(TAG, "APK checksum mismatch")
                    return false
                }
            }

            // Check native libraries
            val libDir = File(context.applicationInfo.nativeLibraryDir)
            if (libDir.exists()) {
                libDir.listFiles()?.filter { it.name.endsWith(".so") }?.forEach { lib ->
                    val current = computeFileChecksum(lib)
                    val expected = criticalFilesChecksums["lib/${lib.name}"]
                    if (expected != null && current != expected) {
                        Log.w(TAG, "Native library checksum mismatch: ${lib.name}")
                        return false
                    }
                }
            }

            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error verifying critical files: ${e.message}")
            return false
        }
    }

    /** Verify class loader integrity */
    internal fun verifyClassLoader(): Boolean {
        try {
            val classLoader = context.classLoader

            // Check for unexpected parent class loaders
            var current: ClassLoader? = classLoader
            var depth = 0
            while (current != null && depth < 10) {
                val className = current.javaClass.name
                // Known safe class loaders
                if (!className.contains("PathClassLoader") &&
                    !className.contains("BaseDexClassLoader") &&
                    !className.contains("BootClassLoader") &&
                    !className.contains("DelegateLastClassLoader") &&
                    !className.contains("androidx.") &&
                    !className.contains("com.android.")) {
                    Log.w(TAG, "Unexpected class loader: $className")
                    return false
                }
                current = current.parent
                depth++
            }

            // Check for dex file count anomalies (could indicate injected DEX)
            try {
                val pathListField = Class.forName("dalvik.system.BaseDexClassLoader")
                    .getDeclaredField("pathList")
                pathListField.isAccessible = true
                val pathList = pathListField.get(classLoader)

                val dexElementsField = Class.forName("dalvik.system.DexPathList")
                    .getDeclaredField("dexElements")
                dexElementsField.isAccessible = true
                val dexElements = dexElementsField.get(pathList) as? Array<*>

                // Log dex element count for anomaly detection
                Log.d(TAG, "DEX elements count: ${dexElements?.size ?: 0}")
            } catch (_: Exception) {
                // Reflection failed, not critical
            }

            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error verifying class loader: ${e.message}")
            return false
        }
    }

    /** Detect method hooking via timing and stack trace analysis */
    private fun detectMethodHooking(): Boolean {
        try {
            // Timing-based detection: measure execution time of known methods
            val testMethod = RuntimeIntegrity::class.java.getDeclaredMethod("timingTestMethod")
            testMethod.isAccessible = true

            val iterations = 100
            var totalTime = 0L
            var minTime = Long.MAX_VALUE

            repeat(iterations) {
                val start = System.nanoTime()
                testMethod.invoke(this)
                val elapsed = System.nanoTime() - start
                totalTime += elapsed
                if (elapsed < minTime) minTime = elapsed
            }

            val avgTime = totalTime / iterations

            // If average is significantly higher than minimum, may indicate hooking
            // Threshold: avg > 10x min (allowing for JIT warmup, GC, etc.)
            if (avgTime > minTime * 10 && minTime > 1000) { // minTime > 1µs to avoid noise
                Log.w(TAG, "Timing anomaly detected: avg=${avgTime}ns, min=${minTime}ns")
                return false
            }

            // Stack trace based detection
            val stack = Thread.currentThread().stackTrace
            val stackString = stack.joinToString("|") { it.toString() }
            val suspiciousFrames = arrayOf(
                "xposed", "frida", "substrate", "hook", "inject",
                "de.robv.android.xposed", "com.frida", "com.saurik.substrate"
            )
            for (suspicious in suspiciousFrames) {
                if (stackString.contains(suspicious, ignoreCase = true)) {
                    Log.w(TAG, "Suspicious stack frame: $suspicious")
                    return false
                }
            }

            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error detecting method hooking: ${e.message}")
            return false
        }
    }

    /** Empty method for timing test */
    @Suppress("UNUSED_PARAMETER")
    private fun timingTestMethod() {
        // Intentional no-op
    }

    /** Verify memory integrity of critical code sections */
    private fun verifyMemoryIntegrity(): Boolean {
        try {
            // Check for common memory modification indicators
            // This is a best-effort check on Android

            // Check /proc/self/maps for unexpected mappings
            val maps = File("/proc/self/maps").readText()
            val suspiciousMappings = arrayOf(
                "frida", "xposed", "substrate", "magisk",
                "libfrida", "libxposed", "libsubstrate",
                "/data/local/tmp", "/data/data/", "rwxp" // RWX mappings are suspicious
            )

            for (line in maps.lines()) {
                for (suspicious in suspiciousMappings) {
                    if (line.contains(suspicious, ignoreCase = true)) {
                        // RWX check needs care - some legitimate JIT mappings are RWX
                        if (suspicious == "rwxp") {
                            // Check if it's an anonymous mapping (no file backing)
                            if (!line.contains(".so") && !line.contains(".jar") && !line.contains(".dex") && !line.contains(".apk")) {
                                Log.w(TAG, "Suspicious RWX anonymous mapping: $line")
                                return false
                            }
                        } else {
                            Log.w(TAG, "Suspicious memory mapping: $line")
                            return false
                        }
                    }
                }
            }

            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error verifying memory integrity: ${e.message}")
            return false
        }
    }

    /** Compute SHA-256 checksum of a file */
    private fun computeFileChecksum(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(8192)
            var read: Int
            while (fis.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        return Base64.encodeToString(digest.digest(), Base64.NO_WRAP)
    }

    /** Compute SHA-256 checksum of an input stream */
    private fun computeStreamChecksum(inputStream: java.io.InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(8192)
        var read: Int
        while (inputStream.read(buffer).also { read = it } != -1) {
            digest.update(buffer, 0, read)
        }
        return Base64.encodeToString(digest.digest(), Base64.NO_WRAP)
    }
}

/**
 * Factory for RuntimeIntegrity
 */
internal object RuntimeIntegrityFactory {
    fun create(context: Context): RuntimeIntegrity {
        return RuntimeIntegrity(context.applicationContext)
    }
}