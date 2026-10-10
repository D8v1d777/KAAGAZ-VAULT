package com.kaagazvault.documents

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.IOException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class DocumentPayloadCodecTest {
    @Test
    fun roundTripsOcrTextAndHumanReviewState() {
        val payload = ImportedPayload(
            displayName = "synthetic-note.png",
            mimeType = "image/png",
            content = byteArrayOf(1, 2, 3, 4),
            ocrText = "Synthetic text: నమస్కారం / नमस्ते",
            ocrConfidence = 78,
            ocrReviewed = true,
            ocrTruncated = false
        )
        val decoded = DocumentPayloadCodec.decode(DocumentPayloadCodec.encode(payload))
        assertEquals(payload.displayName, decoded.displayName)
        assertEquals(payload.mimeType, decoded.mimeType)
        assertArrayEquals(payload.content, decoded.content)
        assertEquals(payload.ocrText, decoded.ocrText)
        assertEquals(78, decoded.ocrConfidence)
        assertTrue(decoded.ocrReviewed)
        assertFalse(decoded.ocrTruncated)
    }

    @Test
    fun readsLegacyVersionOnePayloadWithoutOcrFields() {
        val content = byteArrayOf(9, 8, 7)
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { data ->
            data.writeInt(0x4b475044)
            data.writeInt(1)
            data.writeUTF("old.png")
            data.writeUTF("image/png")
            data.writeInt(content.size)
            data.write(content)
        }
        val decoded = DocumentPayloadCodec.decode(output.toByteArray())
        assertEquals("old.png", decoded.displayName)
        assertArrayEquals(content, decoded.content)
        assertNull(decoded.ocrText)
        assertNull(decoded.ocrConfidence)
        assertFalse(decoded.ocrReviewed)
    }

    @Test
    fun rejectsInvalidPayloadLengthsAndVersions() {
        val encoded = DocumentPayloadCodec.encode(
            ImportedPayload("file.png", "image/png", byteArrayOf(1, 2))
        )
        val badVersion = encoded.copyOf().apply { this[7] = 99 }
        assertThrows(IOException::class.java) { DocumentPayloadCodec.decode(badVersion) }

        val badLength = encoded.copyOf().apply {
            val lengthOffset = 4 + 4 + 2 + "file.png".toByteArray().size + 2 + "image/png".toByteArray().size
            this[lengthOffset + 3] = 99
        }
        assertThrows(IOException::class.java) { DocumentPayloadCodec.decode(badLength) }
    }

    @Test
    fun oversizedOcrTextIsBoundedAndMarkedTruncated() {
        val original = "x".repeat(150_010)
        val encoded = DocumentPayloadCodec.encode(
            ImportedPayload("file.png", "image/png", byteArrayOf(1), ocrText = original)
        )
        val decoded = DocumentPayloadCodec.decode(encoded)
        assertEquals(150_000, decoded.ocrText?.length)
        assertTrue(decoded.ocrTruncated)
    }
}
