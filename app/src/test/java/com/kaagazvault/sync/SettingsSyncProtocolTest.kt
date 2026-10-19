package com.kaagazvault.sync

import java.io.IOException
import javax.crypto.KeyGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class SettingsSyncProtocolTest {
    private val settings = SyncableVaultSettings(
        simplifiedModeEnabled = true,
        voiceNavigationEnabled = true,
        spokenGuidanceEnabled = false,
        voiceHapticPreference = SyncHapticPreference.OFF,
        updatedAtEpochMillis = 100L,
        sourceRevision = 4L
    )

    @Test
    fun codecIsDeterministicAndRoundTripsAllowlistedFields() {
        val first = SettingsSyncCodec.encode(settings)
        val second = SettingsSyncCodec.encode(settings)
        assertEquals(first.toList(), second.toList())
        val canonical = first.toString(Charsets.US_ASCII)
        assertFalse(canonical.contains("document", ignoreCase = true))
        assertFalse(canonical.contains("audio", ignoreCase = true))
        assertFalse(canonical.contains("command", ignoreCase = true))
        assertEquals(settings, SettingsSyncCodec.decode(first))
    }

    @Test
    fun codecIgnoresUnknownFutureFieldsButRejectsDuplicates() {
        val encoded = SettingsSyncCodec.encode(settings).toString(Charsets.US_ASCII)
            .replace("sourceRevision=4\n", "futureField=ignored\nsourceRevision=4\n")
            .toByteArray(Charsets.US_ASCII)
        assertEquals(settings, SettingsSyncCodec.decode(encoded))
        val duplicate = SettingsSyncCodec.encode(settings).toString(Charsets.US_ASCII) + "voiceNavigationEnabled=0\n"
        assertThrows(IOException::class.java) { SettingsSyncCodec.decode(duplicate.toByteArray(Charsets.US_ASCII)) }
    }

    @Test
    fun encryptedBundleRoundTripsAndUsesFreshIv() {
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val first = SettingsSyncCrypto.encrypt(settings, key)
        val second = SettingsSyncCrypto.encrypt(settings, key)
        assertNotEquals(first.toList(), second.toList())
        assertEquals(settings, SettingsSyncCrypto.decrypt(first, key))
    }

    @Test
    fun modifiedBundleAndWrongKeyFailAuthentication() {
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val bundle = SettingsSyncCrypto.encrypt(settings, key)
        bundle[bundle.lastIndex] = (bundle.last().toInt() xor 1).toByte()
        assertThrows(SettingsSyncAuthenticationException::class.java) { SettingsSyncCrypto.decrypt(bundle, key) }
        val otherKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        assertThrows(SettingsSyncAuthenticationException::class.java) {
            SettingsSyncCrypto.decrypt(SettingsSyncCrypto.encrypt(settings, key), otherKey)
        }
    }

    @Test
    fun pairingTicketExpiresAndIsSingleUse() {
        val ticket = SettingsPairingTicket.create(nowEpochMillis = 1_000L, ttlMillis = 2_000L)
        assertTrue(ticket.isUsable(1_500L))
        ticket.consume(1_500L)
        assertFalse(ticket.isUsable(1_500L))
        assertThrows(IOException::class.java) { ticket.consume(1_600L) }
        val expired = SettingsPairingTicket.create(nowEpochMillis = 1_000L, ttlMillis = 2_000L)
        assertThrows(IOException::class.java) { expired.consume(3_000L) }
    }

    @Test
    fun mergeKeepsLocalByDefaultAndRequiresVoiceReview() {
        val local = settings.copy(
            simplifiedModeEnabled = false,
            voiceNavigationEnabled = false,
            spokenGuidanceEnabled = true,
            voiceHapticPreference = SyncHapticPreference.ON,
            updatedAtEpochMillis = 200L,
            sourceRevision = 2L
        )
        val incoming = settings.copy(
            simplifiedModeEnabled = true,
            voiceNavigationEnabled = true,
            spokenGuidanceEnabled = true,
            voiceHapticPreference = SyncHapticPreference.ON,
            updatedAtEpochMillis = 300L,
            sourceRevision = 3L
        )
        val result = SettingsSyncMerger.merge(local, incoming)
        assertEquals(local.simplifiedModeEnabled, result.settings.simplifiedModeEnabled)
        assertFalse(result.settings.voiceNavigationEnabled)
        assertFalse(result.settings.spokenGuidanceEnabled)
        assertTrue(result.voiceEnablementNeedsReview)
    }

    @Test
    fun safetyOffWinsEvenWhenIncomingIsExplicitlySelected() {
        val local = settings.copy(
            spokenGuidanceEnabled = false,
            voiceHapticPreference = SyncHapticPreference.OFF,
            updatedAtEpochMillis = 100L
        )
        val incoming = settings.copy(
            spokenGuidanceEnabled = true,
            voiceHapticPreference = SyncHapticPreference.ON,
            updatedAtEpochMillis = 200L
        )
        val result = SettingsSyncMerger.merge(local, incoming, SettingsMergeDecision.USE_INCOMING)
        assertFalse(result.settings.spokenGuidanceEnabled)
        assertEquals(SyncHapticPreference.OFF, result.settings.voiceHapticPreference)
    }
}
