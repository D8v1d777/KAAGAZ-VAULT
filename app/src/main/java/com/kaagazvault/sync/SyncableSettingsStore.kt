package com.kaagazvault.sync

import android.content.Context

/**
 * Dedicated allowlisted preference file. It intentionally does not reuse the legacy UI file so
 * future local-only settings cannot become part of a backup or transfer payload accidentally.
 */
class SyncableSettingsStore(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
    private val controlPreferences = appContext.getSharedPreferences(CONTROL_FILE_NAME, Context.MODE_PRIVATE)

    fun read(): SyncableVaultSettings = runCatching {
        migrateLegacySimplifiedModeIfNeeded()
        SyncableVaultSettings(
            simplifiedModeEnabled = preferences.getBoolean(KEY_SIMPLIFIED_MODE, false),
            voiceNavigationEnabled = preferences.getBoolean(KEY_VOICE_NAVIGATION, false),
            spokenGuidanceEnabled = preferences.getBoolean(KEY_SPOKEN_GUIDANCE, false),
            voiceHapticPreference = preferences.getString(KEY_HAPTIC, null)
                ?.let { SyncHapticPreference.valueOf(it) }
                ?: SyncHapticPreference.DEVICE_DEFAULT,
            settingsRevision = preferences.getInt(KEY_SCHEMA, SyncableVaultSettings.CURRENT_SCHEMA_VERSION),
            updatedAtEpochMillis = preferences.getLong(KEY_UPDATED_AT, 0L),
            sourceRevision = preferences.getLong(KEY_SOURCE_REVISION, 0L)
        )
    }.getOrDefault(SyncableVaultSettings())

    fun write(settings: SyncableVaultSettings) {
        preferences.edit()
            .putBoolean(KEY_SIMPLIFIED_MODE, settings.simplifiedModeEnabled)
            .putBoolean(KEY_VOICE_NAVIGATION, settings.voiceNavigationEnabled)
            .putBoolean(KEY_SPOKEN_GUIDANCE, settings.spokenGuidanceEnabled)
            .putString(KEY_HAPTIC, settings.voiceHapticPreference.name)
            .putInt(KEY_SCHEMA, settings.settingsRevision)
            .putLong(KEY_UPDATED_AT, settings.updatedAtEpochMillis)
            .putLong(KEY_SOURCE_REVISION, settings.sourceRevision)
            .apply()
    }

    fun update(block: (SyncableVaultSettings) -> SyncableVaultSettings, nowEpochMillis: Long): SyncableVaultSettings {
        val current = read()
        val next = block(current).withRevision(nowEpochMillis, current.sourceRevision + 1L)
        write(next)
        return next
    }

    fun isSyncEnabled(): Boolean = controlPreferences.getBoolean(KEY_SYNC_ENABLED, false)

    fun setSyncEnabled(enabled: Boolean) {
        controlPreferences.edit().putBoolean(KEY_SYNC_ENABLED, enabled).apply()
    }

    fun resetSyncableSettings() {
        preferences.edit().clear().apply()
    }

    private fun migrateLegacySimplifiedModeIfNeeded() {
        if (preferences.contains(KEY_SIMPLIFIED_MODE)) return
        val legacy = appContext.getSharedPreferences(LEGACY_FILE_NAME, Context.MODE_PRIVATE)
        if (!legacy.contains(KEY_SIMPLIFIED_MODE)) return
        preferences.edit()
            .putBoolean(KEY_SIMPLIFIED_MODE, legacy.getBoolean(KEY_SIMPLIFIED_MODE, false))
            .putInt(KEY_SCHEMA, SyncableVaultSettings.CURRENT_SCHEMA_VERSION)
            .apply()
    }

    companion object {
        const val FILE_NAME = "syncable_accessibility_settings"
        const val CONTROL_FILE_NAME = "sync_control"
        const val KEY_SIMPLIFIED_MODE = "simplified_mode"
        private const val LEGACY_FILE_NAME = "ui_preferences"
        private const val KEY_VOICE_NAVIGATION = "voice_navigation_enabled"
        private const val KEY_SPOKEN_GUIDANCE = "spoken_guidance_enabled"
        private const val KEY_HAPTIC = "voice_haptic_preference"
        private const val KEY_SCHEMA = "settings_schema"
        private const val KEY_UPDATED_AT = "updated_at_epoch_millis"
        private const val KEY_SOURCE_REVISION = "source_revision"
        private const val KEY_SYNC_ENABLED = "sync_enabled"
    }
}

/**
 * Offline transfer boundary. Callers transport the returned opaque bytes and pairing secret;
 * this class never opens a socket, logs settings, or touches vault data.
 */
class EncryptedSettingsTransfer(private val store: SyncableSettingsStore) {
    fun export(key: javax.crypto.SecretKey): ByteArray =
        SettingsSyncCrypto.encrypt(store.read(), key)

    fun importEnvelope(
        envelope: ByteArray,
        key: javax.crypto.SecretKey,
        decision: SettingsMergeDecision = SettingsMergeDecision.KEEP_LOCAL
    ): SettingsMergeResult {
        val incoming = SettingsSyncCrypto.decrypt(envelope, key)
        val merged = SettingsSyncMerger.merge(store.read(), incoming, decision)
        store.write(merged.settings)
        return merged
    }
}
