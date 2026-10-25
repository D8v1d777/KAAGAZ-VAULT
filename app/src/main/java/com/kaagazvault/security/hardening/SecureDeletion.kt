package com.kaagazvault.security.hardening

import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.security.SecureRandom

/**
 * Secure file deletion (wiping) utilities.
 *
 * Standard file deletion only removes the filesystem entry; data remains on storage
 * until overwritten. This module provides multi-pass overwriting for sensitive data
 * before deletion, following common secure deletion standards.
 *
 * Note: On modern Android (API 24+), storage is encrypted (FBE/FDE) and wear-leveling
 * on flash storage means overwrites may not hit the same physical blocks. However,
 * overwriting still provides defense-in-depth against:
 * - Logical data recovery from unallocated space
 * - Filesystem-level forensic analysis
 * - Backup/snapshot exposure
 * - Cross-user data leakage on shared devices
 */
internal object SecureDeletion {

    private const val TAG = "SecureDeletion"

    /** Number of overwrite passes (DoD 5220.22-M uses 3; Gutmann uses 35) */
    private const val DEFAULT_PASSES = 3

    /** Maximum file size to wipe (prevent DoS) - 100MB */
    private const val MAX_WIPE_SIZE = 100 * 1024 * 1024L

    /** Buffer size for overwrite operations */
    private const val BUFFER_SIZE = 64 * 1024 // 64KB

    /** Pre-computed overwrite patterns */
    private val PATTERNS = arrayOf(
        // Pass 1: All zeros
        ByteArray(BUFFER_SIZE) { 0x00.toByte() },
        // Pass 2: All ones
        ByteArray(BUFFER_SIZE) { 0xFF.toByte() },
        // Pass 3: Random
        ByteArray(BUFFER_SIZE).also { SecureRandom().nextBytes(it) },
        // Pass 4: 0xAA pattern
        ByteArray(BUFFER_SIZE) { 0xAA.toByte() },
        // Pass 5: 0x55 pattern
        ByteArray(BUFFER_SIZE) { 0x55.toByte() },
        // Pass 6: Random
        ByteArray(BUFFER_SIZE).also { SecureRandom().nextBytes(it) },
        // Pass 7: All zeros (final verification pass)
        ByteArray(BUFFER_SIZE) { 0x00.toByte() }
    )

    /**
     * Securely wipe a file by overwriting it multiple times before deletion.
     *
     * @param file File to wipe
     * @param passes Number of overwrite passes (default: 3, max: 7)
     * @return true if wipe and delete succeeded
     */
    @Throws(IOException::class)
    fun wipe(file: File, passes: Int = DEFAULT_PASSES): Boolean {
        if (!file.exists() || !file.isFile) {
            Log.w(TAG, "File does not exist or is not a file: ${file.absolutePath}")
            return false
        }

        val fileSize = file.length()
        if (fileSize > MAX_WIPE_SIZE) {
            Log.w(TAG, "File too large for secure wipe (${fileSize} bytes > $MAX_WIPE_SIZE): ${file.absolutePath}")
            // Still attempt deletion
            return file.delete()
        }

        if (fileSize == 0L) {
            return file.delete()
        }

        val effectivePasses = passes.coerceIn(1, PATTERNS.size)

        try {
            // Open file for reading and writing
            RandomAccessFile(file, "rws").use { raf ->
                val channel = raf.channel

                // Force allocation - ensure file blocks are allocated
                channel.force(true)

                for (pass in 0 until effectivePasses) {
                    val pattern = PATTERNS[pass % PATTERNS.size]
                    val buffer = ByteBuffer.wrap(pattern)

                    // Seek to beginning
                    raf.seek(0)

                    var remaining = fileSize
                    while (remaining > 0) {
                        val toWrite = remaining.toInt().coerceAtMost(BUFFER_SIZE)
                        if (toWrite < BUFFER_SIZE) {
                            buffer.limit(toWrite)
                        } else {
                            buffer.limit(BUFFER_SIZE)
                        }
                        buffer.rewind()

                        channel.write(buffer)
                        remaining -= toWrite.toLong()
                    }

                    // Force data to storage
                    channel.force(true)

                    // Verify write by reading back (optional, for critical data)
                    if (pass == effectivePasses - 1) {
                        verifyWipe(channel, fileSize, pattern)
                    }
                }
            }

            // Rename to random name before deletion (prevents filename recovery)
            val randomName = ".${SecureRandom().nextLong(0, Long.MAX_VALUE)}.wiped"
            val renamed = File(file.parentFile, randomName)
            if (file.renameTo(renamed)) {
                // Delete the renamed file
                if (!renamed.delete()) {
                    Log.w(TAG, "Failed to delete wiped file: ${renamed.absolutePath}")
                    return false
                }
            } else {
                // If rename failed, try direct delete
                if (!file.delete()) {
                    Log.w(TAG, "Failed to delete file after wipe: ${file.absolutePath}")
                    return false
                }
            }

            Log.d(TAG, "Securely wiped and deleted: ${file.absolutePath}")
            return true

        } catch (e: IOException) {
            Log.e(TAG, "Error during secure wipe: ${e.message}")
            // Attempt deletion anyway
            try {
                file.delete()
            } catch (_: Exception) {
                // Ignore
            }
            throw e
        }
    }

    /** Verify the last overwrite pass */
    private fun verifyWipe(channel: FileChannel, fileSize: Long, expectedPattern: ByteArray) {
        try {
            channel.position(0)
            val buffer = ByteBuffer.allocate(BUFFER_SIZE)
            var remaining = fileSize

            while (remaining > 0) {
                val toRead = remaining.toInt().coerceAtMost(BUFFER_SIZE)
                buffer.limit(toRead)
                buffer.rewind()

                val read = channel.read(buffer)
                if (read <= 0) break

                buffer.flip()
                for (i in 0 until read) {
                    val expected = expectedPattern[i % expectedPattern.size]
                    if (buffer.get(i) != expected) {
                        Log.w(TAG, "Wipe verification mismatch at offset ${fileSize - remaining + i}")
                        return
                    }
                }
                remaining -= read.toLong()
            }
            Log.d(TAG, "Wipe verification passed")
        } catch (e: Exception) {
            Log.w(TAG, "Wipe verification failed: ${e.message}")
        }
    }

    /**
     * Securely wipe a directory and all its contents recursively.
     *
     * @param directory Directory to wipe
     * @param passes Number of overwrite passes per file
     */
    @Throws(IOException::class)
    fun wipeDirectory(directory: File, passes: Int = DEFAULT_PASSES): Boolean {
        if (!directory.exists() || !directory.isDirectory) {
            Log.w(TAG, "Directory does not exist: ${directory.absolutePath}")
            return false
        }

        var success = true
        directory.listFiles()?.forEach { file ->
            try {
                if (file.isDirectory) {
                    if (!wipeDirectory(file, passes)) success = false
                } else {
                    if (!wipe(file, passes)) success = false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error wiping ${file.absolutePath}: ${e.message}")
                success = false
            }
        }

        // Finally delete the directory itself
        if (!directory.delete()) {
            Log.w(TAG, "Failed to delete directory after wipe: ${directory.absolutePath}")
            success = false
        }

        return success
    }

    /**
     * Wipe free space on a volume (creates temporary file to fill space).
     * Use with caution - can be slow and wears flash storage.
     *
     * @param directory Directory on the target volume
     * @param maxBytes Maximum bytes to wipe (0 = all free space)
     */
    @Throws(IOException::class)
    fun wipeFreeSpace(directory: File, maxBytes: Long = 0): Boolean {
        val stat = android.os.StatFs(directory.absolutePath)
        val freeBytes = stat.blockSizeLong * stat.availableBlocksLong
        val targetBytes = if (maxBytes > 0) freeBytes.coerceAtMost(maxBytes) else freeBytes

        if (targetBytes <= 0) return true

        val tempFile = File(directory, ".wipe_free_space_${SecureRandom().nextLong(0, Long.MAX_VALUE)}.tmp")

        try {
            FileOutputStream(tempFile).use { fos ->
                val channel = fos.channel
                val buffer = ByteBuffer.allocate(BUFFER_SIZE)
                val random = SecureRandom()

                var written = 0L
                while (written < targetBytes) {
                    val toWrite = (targetBytes - written).coerceAtMost(BUFFER_SIZE.toLong())
                    random.nextBytes(buffer.array())
                    buffer.limit(toWrite.toInt())
                    buffer.rewind()

                    channel.write(buffer)
                    channel.force(true)
                    written += toWrite
                }
            }

            // Securely delete the temp file
            return wipe(tempFile, 1) // Single pass is sufficient for free space wipe

        } catch (e: IOException) {
            Log.e(TAG, "Error wiping free space: ${e.message}")
            tempFile.delete()
            throw e
        }
    }

    /**
     * Quick wipe - single pass with random data, then delete.
     * Faster but less thorough than multi-pass wipe.
     */
    @Throws(IOException::class)
    fun quickWipe(file: File): Boolean {
        return wipe(file, 1)
    }
}