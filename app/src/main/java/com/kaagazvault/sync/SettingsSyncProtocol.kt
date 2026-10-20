package com.kaagazvault.sync

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Only these choices may cross a device boundary. */
enum class SyncHapticPreference {
    DEVICE_DEFAULT,
    ON,
    OFF
}

data class SyncableVaultSettings(
    val simplifiedModeEnabled: Boolean = false,
    val voiceNavigationEnabled: Boolean = false,
    val spokenGuidanceEnabled: Boolean = false,
    val voiceHapticPreference: SyncHapticPreference = SyncHapticPreference.DEVICE_DEFAULT,
    val settingsRevision: Int = 1,
    val updatedAtEpochMillis: Long = 0L,
    val sourceRevision: Long = 0L
) {
    init {
        require(settingsRevision == CURRENT_SCHEMA_VERSION) { "Unsupported settings schema" }
        require(updatedAtEpochMillis >= 0L) { "Settings timestamp cannot be negative" }
        require(sourceRevision >= 0L) { "Settings revision cannot be negative" }
    }

    fun withRevision(nowEpochMillis: Long, nextSourceRevision: Long): SyncableVaultSettings = copy(
        settingsRevision = CURRENT_SCHEMA_VERSION,
        updatedAtEpochMillis = nowEpochMillis,
        sourceRevision = nextSourceRevision
    )

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}

/**
 * Deterministic, allowlisted, line-based format. Unknown fields are ignored on decode and can
 * never become synced settings by accident. Values are intentionally restricted to booleans,
 * enum names, and bounded decimal numbers; arbitrary document text cannot enter this format.
 */
object SettingsSyncCodec {
    private const val HEADER = "KGV-SETTINGS-1"
    private const val MAX_PAYLOAD_BYTES = 16 * 1024

    fun encode(settings: SyncableVaultSettings): ByteArray {
        val canonical = buildString {
            append(HEADER).append('\n')
            append("simplifiedModeEnabled=").append(if (settings.simplifiedModeEnabled) '1' else '0').append('\n')
            append("voiceNavigationEnabled=").append(if (settings.voiceNavigationEnabled) '1' else '0').append('\n')
            append("spokenGuidanceEnabled=").append(if (settings.spokenGuidanceEnabled) '1' else '0').append('\n')
            append("voiceHapticPreference=").append(settings.voiceHapticPreference.name).append('\n')
            append("settingsRevision=").append(settings.settingsRevision).append('\n')
            append("updatedAtEpochMillis=").append(settings.updatedAtEpochMillis).append('\n')
            append("sourceRevision=").append(settings.sourceRevision).append('\n')
        }
        val bytes = canonical.toByteArray(Charsets.US_ASCII)
        require(bytes.size <= MAX_PAYLOAD_BYTES) { "Settings payload is too large" }
        return bytes
    }

    fun decode(bytes: ByteArray): SyncableVaultSettings {
        if (bytes.isEmpty() || bytes.size > MAX_PAYLOAD_BYTES) throw IOException("Settings payload size is invalid")
        val lines = bytes.toString(Charsets.US_ASCII).split('\n')
        if (lines.firstOrNull() != HEADER) throw IOException("Unrecognized settings payload")
        val fields = linkedMapOf<String, String>()
        lines.drop(1).filter { it.isNotEmpty() }.forEach { line ->
            val separator = line.indexOf('=')
            if (separator <= 0) throw IOException("Malformed settings field")
            val key = line.substring(0, separator)
            val value = line.substring(separator + 1)
            if (key in REQUIRED_FIELDS) {
                if (fields.put(key, value) != null) throw IOException("Duplicate settings field")
            }
        }
        if (!fields.keys.containsAll(REQUIRED_FIELDS)) throw IOException("Settings payload is incomplete")
        return try {
            SyncableVaultSettings(
                simplifiedModeEnabled = parseBoolean(fields.getValue("simplifiedModeEnabled")),
                voiceNavigationEnabled = parseBoolean(fields.getValue("voiceNavigationEnabled")),
                spokenGuidanceEnabled = parseBoolean(fields.getValue("spokenGuidanceEnabled")),
                voiceHapticPreference = SyncHapticPreference.valueOf(fields.getValue("voiceHapticPreference")),
                settingsRevision = fields.getValue("settingsRevision").toInt(),
                updatedAtEpochMillis = fields.getValue("updatedAtEpochMillis").toLong(),
                sourceRevision = fields.getValue("sourceRevision").toLong()
            )
        } catch (error: IllegalArgumentException) {
            throw IOException("Settings payload contains invalid values", error)
        }
    }

    private fun parseBoolean(value: String): Boolean = when (value) {
        "0" -> false
        "1" -> true
        else -> throw IllegalArgumentException("Boolean must be 0 or 1")
    }

    private val REQUIRED_FIELDS = setOf(
        "simplifiedModeEnabled",
        "voiceNavigationEnabled",
        "spokenGuidanceEnabled",
        "voiceHapticPreference",
        "settingsRevision",
        "updatedAtEpochMillis",
        "sourceRevision"
    )
}

/** A one-use secret is supplied by the pairing UI and is never serialized into the settings. */
class SettingsPairingTicket private constructor(
    val secret: SecretKey,
    val issuedAtEpochMillis: Long,
    val expiresAtEpochMillis: Long
) {
    private var consumed = false

    fun isUsable(nowEpochMillis: Long): Boolean = !consumed &&
        nowEpochMillis >= issuedAtEpochMillis && nowEpochMillis < expiresAtEpochMillis

    fun consume(nowEpochMillis: Long): SecretKey {
        if (!isUsable(nowEpochMillis)) throw IOException("Pairing ticket is expired or already used")
        consumed = true
        return secret
    }

    companion object {
        private const val DEFAULT_TTL_MILLIS = 5 * 60 * 1000L

        fun create(
            nowEpochMillis: Long,
            ttlMillis: Long = DEFAULT_TTL_MILLIS,
            random: SecureRandom = SecureRandom()
        ): SettingsPairingTicket {
            require(ttlMillis in 1_000L..15 * 60 * 1000L) { "Pairing lifetime is outside the safe range" }
            val bytes = ByteArray(32).also(random::nextBytes)
            return SettingsPairingTicket(
                secret = javax.crypto.spec.SecretKeySpec(bytes, "AES"),
                issuedAtEpochMillis = nowEpochMillis,
                expiresAtEpochMillis = nowEpochMillis + ttlMillis
            )
        }
    }
}

/** AES-GCM envelope for a small settings bundle; no document bytes are accepted here. */
object SettingsSyncCrypto {
    private val random = SecureRandom()
    private val magic = byteArrayOf(0x4b, 0x47, 0x53, 0x59) // KGSY
    private const val FORMAT_VERSION: Byte = 1
    private const val IV_LENGTH = 12
    private const val TAG_BITS = 128
    private const val HEADER_LENGTH = 4 + 1 + IV_LENGTH
    private const val MAX_ENVELOPE_BYTES = 64 * 1024

    @Throws(GeneralSecurityException::class)
    fun encrypt(settings: SyncableVaultSettings, key: SecretKey): ByteArray {
        val iv = ByteArray(IV_LENGTH).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(associatedData())
        val ciphertext = cipher.doFinal(SettingsSyncCodec.encode(settings))
        return ByteBuffer.allocate(HEADER_LENGTH + ciphertext.size)
            .order(ByteOrder.BIG_ENDIAN)
            .put(magic)
            .put(FORMAT_VERSION)
            .put(iv)
            .put(ciphertext)
            .array()
    }

    @Throws(IOException::class, GeneralSecurityException::class)
    fun decrypt(envelope: ByteArray, key: SecretKey): SyncableVaultSettings {
        if (envelope.size < HEADER_LENGTH + 16 || envelope.size > MAX_ENVELOPE_BYTES) {
            throw IOException("Settings envelope size is invalid")
        }
        val buffer = ByteBuffer.wrap(envelope).order(ByteOrder.BIG_ENDIAN)
        val actualMagic = ByteArray(magic.size).also(buffer::get)
        if (!actualMagic.contentEquals(magic)) throw IOException("Unrecognized settings envelope")
        if (buffer.get() != FORMAT_VERSION) throw IOException("Unsupported settings envelope version")
        val iv = ByteArray(IV_LENGTH).also(buffer::get)
        val ciphertext = ByteArray(buffer.remaining()).also(buffer::get)
        return try {
            Cipher.getInstance("AES/GCM/NoPadding").run {
                init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
                updateAAD(associatedData())
                SettingsSyncCodec.decode(doFinal(ciphertext))
            }
        } catch (error: GeneralSecurityException) {
            throw SettingsSyncAuthenticationException(error)
        }
    }

    private fun associatedData(): ByteArray = magic + byteArrayOf(FORMAT_VERSION)
}

class SettingsSyncAuthenticationException(cause: Throwable) : IOException("Settings authentication failed", cause)

/** Large-text/copyable representation for the user-mediated offline transfer flow. */
object SettingsTransferCode {
    fun encode(key: SecretKey): String {
        val bytes = key.encoded ?: throw IllegalArgumentException("Transfer key is not exportable")
        require(bytes.size == 32) { "Transfer key must be 256 bits" }
        return bytes.joinToString(separator = "") { byte -> "%02X".format(byte.toInt() and 0xff) }
    }

    fun decode(code: String): SecretKey {
        val normalized = code.filterNot { it.isWhitespace() || it == '-' }
        if (normalized.length != 64 || normalized.any { it !in "0123456789abcdefABCDEF" }) {
            throw IOException("Transfer code is invalid")
        }
        val bytes = ByteArray(32) { index ->
            val high = normalized[index * 2].digitToInt(16)
            val low = normalized[index * 2 + 1].digitToInt(16)
            ((high shl 4) or low).toByte()
        }
        return javax.crypto.spec.SecretKeySpec(bytes, "AES")
    }
}

enum class SettingsMergeDecision {
    KEEP_LOCAL,
    USE_INCOMING
}

data class SettingsMergeResult(
    val settings: SyncableVaultSettings,
    val voiceEnablementNeedsReview: Boolean,
    val incomingWasNewer: Boolean
)

/** Field-level merge with safe-off precedence and explicit review for enabling voice remotely. */
object SettingsSyncMerger {
    fun merge(
        local: SyncableVaultSettings,
        incoming: SyncableVaultSettings,
        decision: SettingsMergeDecision = SettingsMergeDecision.KEEP_LOCAL
    ): SettingsMergeResult {
        require(local.settingsRevision == incoming.settingsRevision) { "Settings schema versions differ" }
        val incomingWasNewer = incoming.updatedAtEpochMillis > local.updatedAtEpochMillis ||
            (incoming.updatedAtEpochMillis == local.updatedAtEpochMillis && incoming.sourceRevision > local.sourceRevision)
        if (!incomingWasNewer && decision == SettingsMergeDecision.KEEP_LOCAL) {
            return SettingsMergeResult(local, voiceEnablementNeedsReview = false, incomingWasNewer = false)
        }

        val simplified = if (decision == SettingsMergeDecision.USE_INCOMING) incoming.simplifiedModeEnabled else local.simplifiedModeEnabled
        val incomingVoiceEnablementNeedsReview = incoming.voiceNavigationEnabled && !local.voiceNavigationEnabled
        val voiceEnabled = when {
            !incoming.voiceNavigationEnabled -> false
            local.voiceNavigationEnabled -> true
            decision == SettingsMergeDecision.USE_INCOMING -> true
            else -> false
        }
        val spokenGuidance = when {
            // Do not activate spoken output from a bundle whose voice-navigation enablement
            // has not been explicitly reviewed. This is deliberately fail-closed for remote
            // settings; selecting USE_INCOMING satisfies the review boundary above.
            incomingVoiceEnablementNeedsReview && !voiceEnabled -> false
            !local.spokenGuidanceEnabled || !incoming.spokenGuidanceEnabled -> false
            decision == SettingsMergeDecision.USE_INCOMING -> true
            else -> local.spokenGuidanceEnabled
        }
        val haptics = if (local.voiceHapticPreference == SyncHapticPreference.OFF || incoming.voiceHapticPreference == SyncHapticPreference.OFF) {
            SyncHapticPreference.OFF
        } else if (decision == SettingsMergeDecision.USE_INCOMING) incoming.voiceHapticPreference else local.voiceHapticPreference

        return SettingsMergeResult(
            settings = SyncableVaultSettings(
                simplifiedModeEnabled = simplified,
                voiceNavigationEnabled = voiceEnabled,
                spokenGuidanceEnabled = spokenGuidance,
                voiceHapticPreference = haptics,
                settingsRevision = local.settingsRevision,
                updatedAtEpochMillis = maxOf(local.updatedAtEpochMillis, incoming.updatedAtEpochMillis),
                sourceRevision = maxOf(local.sourceRevision, incoming.sourceRevision)
            ),
            voiceEnablementNeedsReview = incomingVoiceEnablementNeedsReview && !voiceEnabled,
            incomingWasNewer = incomingWasNewer
        )
    }
}
