package com.kaagazvault.documents

/**
 * Rejects provider MIME hints that do not match a supported file signature.
 * This is format validation, not a complete parser or malware scanner.
 */
internal object DocumentSignatureValidator {
    fun isSupported(mimeType: String, bytes: ByteArray): Boolean = when (mimeType.lowercase()) {
        "application/pdf" -> bytes.startsWithAscii("%PDF-")
        "image/jpeg" -> bytes.startsWithBytes(0xff, 0xd8, 0xff)
        "image/png" -> bytes.startsWithBytes(0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)
        "image/gif" -> bytes.startsWithAscii("GIF87a") || bytes.startsWithAscii("GIF89a")
        "image/webp" -> bytes.size >= 12 &&
            bytes.startsWithAscii("RIFF") && bytes.copyOfRange(8, 12).contentEquals("WEBP".toByteArray())
        "image/bmp" -> bytes.startsWithAscii("BM")
        else -> false
    }

    private fun ByteArray.startsWithAscii(prefix: String): Boolean {
        val expected = prefix.toByteArray(Charsets.US_ASCII)
        if (size < expected.size) return false
        return expected.indices.all { this[it] == expected[it] }
    }

    private fun ByteArray.startsWithBytes(vararg expected: Int): Boolean {
        if (size < expected.size) return false
        return expected.indices.all { (this[it].toInt() and 0xff) == expected[it] }
    }
}
