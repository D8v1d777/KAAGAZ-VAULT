package com.kaagazvault.documents

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentSignatureValidatorTest {
    @Test
    fun acceptsKnownPdfAndImageSignatures() {
        assertTrue(DocumentSignatureValidator.isSupported("application/pdf", "%PDF-1.7".toByteArray()))
        assertTrue(DocumentSignatureValidator.isSupported(
            "image/png",
            byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)
        ))
        assertTrue(DocumentSignatureValidator.isSupported(
            "image/jpeg",
            byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte(), 0x00)
        ))
        assertTrue(DocumentSignatureValidator.isSupported("image/gif", "GIF89a".toByteArray()))
        assertTrue(DocumentSignatureValidator.isSupported(
            "image/webp",
            "RIFF0000WEBP".toByteArray()
        ))
        assertTrue(DocumentSignatureValidator.isSupported("image/bmp", "BM".toByteArray()))
    }

    @Test
    fun rejectsMimeSignatureMismatchAndUnknownFormats() {
        assertFalse(DocumentSignatureValidator.isSupported("application/pdf", "not a PDF".toByteArray()))
        assertFalse(DocumentSignatureValidator.isSupported("image/png", "%PDF-1.7".toByteArray()))
        assertFalse(DocumentSignatureValidator.isSupported("image/heic", byteArrayOf(1, 2, 3)))
        assertFalse(DocumentSignatureValidator.isSupported("image/jpeg", byteArrayOf(0xff.toByte(), 0xd8.toByte())))
    }
}
