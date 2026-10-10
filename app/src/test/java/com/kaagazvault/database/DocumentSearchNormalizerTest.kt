package com.kaagazvault.database

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentSearchNormalizerTest {
    @Test
    fun normalizesEnglishCaseAndWhitespace() {
        assertEquals("electric bill april", EncryptedMetadataIndex.normalize("  ELECTRIC   Bill\nApril "))
    }

    @Test
    fun preservesTeluguAndHindiSearchTerms() {
        val normalized = EncryptedMetadataIndex.normalize("తెలుగు नमस्ते")
        assertTrue(normalized.contains("తెలుగు"))
        assertTrue(normalized.contains("नमस्ते"))
    }

    @Test
    fun normalizesCanonicallyEquivalentUnicode() {
        assertEquals(
            EncryptedMetadataIndex.normalize("café"),
            EncryptedMetadataIndex.normalize("cafe\u0301")
        )
    }
}
