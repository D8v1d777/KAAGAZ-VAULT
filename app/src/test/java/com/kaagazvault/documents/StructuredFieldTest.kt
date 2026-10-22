package com.kaagazvault.documents

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StructuredFieldTest {
    @Test
    fun extractsBoundedFieldsWithSourceAndUncertainty() {
        val fields = StructuredFieldExtractor.extract("Name: Synthetic Patient\nDate: 12/10/2026\nAmount: ₹1,250.00\nID: AB-12345")
        assertEquals(4, fields.size)
        assertTrue(fields.any { it.type == "date" && it.uncertain && it.sourceExcerpt.contains("12/10/2026") })
        assertTrue(fields.any { it.type == "amount" && it.value.contains("1,250") })
        assertTrue(fields.any { it.type == "identifier" && it.value.contains("AB-12345") })
        assertTrue(fields.any { it.type == "name" && it.value == "Synthetic Patient" })
        assertFalse(fields.any { it.accepted })
    }

    @Test
    fun ignoresBlankAndUnlabelledAmbiguousText() {
        assertTrue(StructuredFieldExtractor.extract("\nnot enough\n2026\n₹\n").isEmpty())
    }

    @Test
    fun payloadRoundTripKeepsReviewedFieldAndProvenance() {
        val field = StructuredField("date", "12/10/2026", "Date: 12/10/2026", "bounded date pattern", accepted = true)
        val payload = ImportedPayload("synthetic.png", "image/png", byteArrayOf(1, 2), ocrText = "Date: 12/10/2026", structuredFields = listOf(field))
        val decoded = DocumentPayloadCodec.decode(DocumentPayloadCodec.encode(payload))
        assertEquals(listOf(field), decoded.structuredFields)
        assertEquals("Date: 12/10/2026", decoded.ocrText)
    }
}
