package com.kaagazvault.security

import android.os.Handler
import android.os.Looper
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.kaagazvault.database.EncryptedMetadataIndexProvider
import com.kaagazvault.database.SqlCipherDatabaseMigrationActions
import com.kaagazvault.database.VaultDatabaseProvider
import com.kaagazvault.documents.DocumentRepository
import com.kaagazvault.ocr.OfflineOcrEngine
import com.kaagazvault.reminders.ReminderRepository
import java.io.File
import java.io.IOException
import java.security.GeneralSecurityException
import javax.crypto.KeyGenerator
import java.util.concurrent.Executors
import javax.crypto.Cipher
import javax.crypto.SecretKey

internal data class VaultRuntime(
    val documents: DocumentRepository,
    val ocr: OfflineOcrEngine,
    val reminders: ReminderRepository,
    val databaseProvider: VaultDatabaseProvider
)

internal sealed interface VaultAccessUiState {
    data object Initializing : VaultAccessUiState
    data class SetupRequired(val message: String? = null) : VaultAccessUiState
    data class Locked(val message: String? = null) : VaultAccessUiState
    data class ConfirmRecoveryKey(val key: String, val message: String? = null) : VaultAccessUiState
    data class RecoveryRequired(val message: String) : VaultAccessUiState
    data class Migrating(val message: String) : VaultAccessUiState
    data class Ready(val runtime: VaultRuntime) : VaultAccessUiState
}

/** Only exposes repositories after an auth-per-use unwrap and durable migration COMPLETE. */
internal class VaultAccessController(
    private val activity: FragmentActivity,
    private val session: VaultSession
) {
    val uiState: MutableState<VaultAccessUiState> = mutableStateOf(VaultAccessUiState.Initializing)
    private val main = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private val noBackup = activity.noBackupFilesDir
    private val files = activity.filesDir
    private val records = VaultKeyRecordStore(File(noBackup, "vault-vek.record"))
    private val setup = VaultSetupStateStore(File(noBackup, "vault-recovery-confirmed.marker"))
    private val recovery = RecoveryEnvelopeStore(File(noBackup, "vault-recovery-vek.wrap"), setup)

    private enum class Purpose { SETUP, UNLOCK, RECOVERY_REWRAP }
    private var purpose: Purpose? = null
    private var authKey: AuthBoundVaultKey? = null
    private var authAlias: String? = null
    private var wrappedVek: ByteArray? = null
    private var pendingVek: ByteArray? = null
    private var pendingRecoveryText: String? = null
    private var runtime: VaultRuntime? = null
    @Volatile private var accessEpoch: Long = 0L

    private val prompt = BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                val cipher = result.cryptoObject?.cipher
                if (cipher == null) {
                    onPromptFailure("The authenticated cryptographic operation was unavailable.")
                    return
                }
                try {
                    when (purpose) {
                        Purpose.SETUP -> finishSetup(cipher)
                        Purpose.UNLOCK -> finishUnlock(cipher)
                        Purpose.RECOVERY_REWRAP -> finishRecoveryRewrap(cipher)
                        null -> throw IllegalStateException("No authentication operation is active")
                    }
                } catch (failure: Exception) {
                    onPromptFailure(safeFailure(failure))
                }
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                onPromptFailure("Authentication was cancelled or unavailable. Existing vault data was preserved.")
            }
        }
    )

    fun initialize() {
        try {
            val record = records.loadOrNull()
            if (record != null) {
                record.wrappedVek.fill(0)
                state(VaultAccessUiState.Locked())
            } else if (orphanedVekMaterialExists()) {
                state(VaultAccessUiState.RecoveryRequired(
                    "Vault-key metadata or a migration artifact exists without its authenticated key record. Existing files are preserved; fresh setup is blocked."
                ))
            } else {
                state(VaultAccessUiState.SetupRequired())
            }
        } catch (failure: Exception) {
            state(VaultAccessUiState.RecoveryRequired(safeFailure(failure)))
        }
    }

    fun beginSetup() {
        try {
            if (records.hasArtifacts() || orphanedVekMaterialExists()) {
                state(VaultAccessUiState.RecoveryRequired("Existing vault-key material prevents fresh setup. Use recovery; no replacement key was generated."))
                return
            }
            val alias = AuthBoundVaultKey().newAliasForRecoveryOrSetup()
            val key = AuthBoundVaultKey(alias)
            startPrompt(Purpose.SETUP, key, alias, null, key.prepareSetupCipher(), "Set up Kaagaz Vault")
        } catch (failure: Exception) {
            state(VaultAccessUiState.SetupRequired(safeFailure(failure)))
        }
    }

    fun beginUnlock() {
        try {
            val record = records.loadOrNull()
                ?: throw MissingEncryptionKeyException("Vault key record is missing; recovery is required.")
            val key = AuthBoundVaultKey(record.alias)
            try {
                startPrompt(Purpose.UNLOCK, key, record.alias, record.wrappedVek, key.prepareUnlockCipher(record.wrappedVek), "Unlock Kaagaz Vault")
            } finally {
                record.wrappedVek.fill(0)
            }
        } catch (failure: Exception) {
            clearPrompt()
            state(VaultAccessUiState.RecoveryRequired(safeFailure(failure)))
        }
    }

    fun recoverWithKey(text: String) {
        var keyBytes: ByteArray? = null
        var envelope: ByteArray? = null
        var vek: ByteArray? = null
        try {
            keyBytes = RecoveryKeyEnvelope().decodeRecoveryKey(text)
            envelope = recovery.loadOrNull()
                ?: throw IOException("Recovery envelope is missing. Existing protected files were preserved.")
            vek = RecoveryKeyEnvelope().unwrap(envelope, keyBytes)
            val alias = AuthBoundVaultKey().newAliasForRecoveryOrSetup()
            val key = AuthBoundVaultKey(alias)
            val cipher = key.prepareSetupCipher()
            pendingVek = vek
            vek = null
            pendingRecoveryText = text
            startPrompt(Purpose.RECOVERY_REWRAP, key, alias, null, cipher, "Restore vault access")
        } catch (failure: Exception) {
            vek?.fill(0)
            state(VaultAccessUiState.RecoveryRequired(safeFailure(failure)))
        } finally {
            keyBytes?.fill(0)
            envelope?.fill(0)
        }
    }

    /** Requires a full-key re-entry in the UI, rather than a blind "I saved it" tap. */
    fun confirmRecoveryKeyStored(confirmation: String) {
        val vek = pendingVek
        val expected = pendingRecoveryText
        if (vek == null || expected == null || confirmation != expected) {
            state(uiState.value.let {
                if (it is VaultAccessUiState.ConfirmRecoveryKey) it.copy(message = "The confirmation did not match. Store the key safely and enter it exactly.")
                else VaultAccessUiState.Locked("Recovery confirmation expired. Unlock again.")
            })
            return
        }
        try {
            setup.markRecoveryConfirmed()
            pendingVek = null
            pendingRecoveryText = null
            session.installVerifiedKey(vek)
            vek.fill(0)
            migrate()
        } catch (failure: Exception) {
            vek.fill(0)
            pendingVek = null
            state(VaultAccessUiState.Locked(safeFailure(failure)))
        }
    }

    fun lockNow(message: String? = null) {
        // Invalidate pending publication atomically with detaching the current runtime.
        val previousRuntime = synchronized(this) {
            accessEpoch += 1
            runtime.also { runtime = null }
        }
        // Wait for active session-bound operations to finish before closing SQLCipher.
        try { session.lock() } catch (_: Exception) { }
        try { previousRuntime?.databaseProvider?.close() } catch (_: Exception) { }
        pendingVek?.fill(0)
        pendingVek = null
        pendingRecoveryText = null
        clearPrompt()
        state(VaultAccessUiState.Locked(message))
    }

    fun onActivityStopped() {
        when (uiState.value) {
            is VaultAccessUiState.Ready, is VaultAccessUiState.Migrating ->
                lockNow("Vault locked because the app left the foreground.")
            is VaultAccessUiState.ConfirmRecoveryKey -> {
                pendingVek?.fill(0)
                pendingVek = null
                pendingRecoveryText = null
                state(VaultAccessUiState.Locked("Recovery confirmation was interrupted. Unlock again to redisplay the key."))
            }
            else -> Unit
        }
    }

    fun dispose() {
        val previousRuntime = synchronized(this) {
            accessEpoch += 1
            runtime.also { runtime = null }
        }
        try { prompt.cancelAuthentication() } catch (_: Exception) { }
        pendingVek?.fill(0)
        pendingVek = null
        pendingRecoveryText = null
        try { session.lock() } catch (_: Exception) { }
        try { previousRuntime?.databaseProvider?.close() } catch (_: Exception) { }
        executor.shutdownNow()
    }

    private fun startPrompt(p: Purpose, key: AuthBoundVaultKey, alias: String, wrapped: ByteArray?, cipher: Cipher, title: String) {
        clearPrompt()
        purpose = p
        authKey = key
        authAlias = alias
        wrappedVek = wrapped?.copyOf()
        val authenticators = key.promptAuthenticators()
        val builder = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle("Authenticate with this device's supported secure authenticator.")
            .setAllowedAuthenticators(authenticators)
            .setConfirmationRequired(true)
        if (authenticators and androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL == 0) {
            builder.setNegativeButtonText("Cancel")
        }
        prompt.authenticate(builder.build(), BiometricPrompt.CryptoObject(cipher))
    }

    private fun finishSetup(cipher: Cipher) {
        val key = authKey ?: throw IllegalStateException("Setup key context is missing")
        val alias = authAlias ?: throw IllegalStateException("Setup alias is missing")
        val vek = key.finishSetup(cipher) { wrapped -> records.saveIfAbsent(alias, wrapped) }
        clearPrompt()
        prepareRecoveryConfirmation(vek, null)
    }

    private fun finishUnlock(cipher: Cipher) {
        val key = authKey ?: throw IllegalStateException("Unlock key context is missing")
        val wrapped = wrappedVek ?: throw IOException("Wrapped VEK is unavailable")
        val vek = key.finishUnlock(cipher, wrapped)
        clearPrompt()
        if (!setup.isRecoveryConfirmed()) {
            prepareRecoveryConfirmation(vek, null)
            return
        }
        val envelope = recovery.loadOrNull()
        if (envelope == null) {
            vek.fill(0)
            state(VaultAccessUiState.RecoveryRequired("Recovery envelope is missing. No replacement key was created."))
            return
        }
        envelope.fill(0)
        installAndMigrate(vek)
    }

    private fun finishRecoveryRewrap(cipher: Cipher) {
        val key = authKey ?: throw IllegalStateException("Recovery key context is missing")
        val alias = authAlias ?: throw IllegalStateException("Recovery alias is missing")
        val vek = pendingVek ?: throw IllegalStateException("Recovered VEK is unavailable")
        key.finishSetupWithKey(cipher, vek) { wrapped -> records.persistRecovered(alias, wrapped) }
        clearPrompt()
        val displayed = pendingRecoveryText
        if (!setup.isRecoveryConfirmed()) {
            prepareRecoveryConfirmation(vek, displayed)
        } else {
            pendingVek = null
            pendingRecoveryText = null
            installAndMigrate(vek)
        }
    }

    private fun prepareRecoveryConfirmation(vek: ByteArray, existingText: String?) {
        try {
            val display = existingText ?: run {
                val crypto = RecoveryKeyEnvelope()
                val secret = crypto.generateRecoveryKey()
                val text = crypto.encodeRecoveryKey(secret)
                val wrapped = crypto.wrap(vek, secret)
                try { recovery.saveBeforeConfirmation(wrapped) }
                finally {
                    secret.fill(0)
                    wrapped.fill(0)
                }
                text
            }
            pendingVek?.fill(0)
            pendingVek = vek
            pendingRecoveryText = display
            state(VaultAccessUiState.ConfirmRecoveryKey(display))
        } catch (failure: Exception) {
            vek.fill(0)
            state(VaultAccessUiState.Locked("Recovery setup could not be persisted. Unlock again to retry."))
        }
    }

    private fun installAndMigrate(vek: ByteArray) {
        try {
            if (!session.isUnlocked()) session.installVerifiedKey(vek)
        } catch (failure: Exception) {
            vek.fill(0)
            state(VaultAccessUiState.Locked(safeFailure(failure)))
            return
        } finally {
            vek.fill(0)
        }
        pendingVek = null
        pendingRecoveryText = null
        migrate()
    }

    private fun migrate() {
        val migrationEpoch = accessEpoch
        state(VaultAccessUiState.Migrating("Verifying the migration journal and protected data…"))
        executor.execute {
            var oldProvider: VaultDatabaseProvider? = null
            var newProvider: VaultDatabaseProvider? = null
            var legacyKey: SecretKey? = null
            try {
                val legacy = File(files, "encrypted_documents")
                if (!legacy.exists() && !legacy.mkdirs()) throw IOException("Could not create legacy document directory")
                val staging = File(files, "encrypted_documents.migration-staging")
                val published = File(files, "encrypted_documents.vek")
                val journal = VaultMigrationJournal(File(noBackup, "vault-migration"))
                if (journal.load()?.phase != VaultMigrationPhase.COMPLETE) {
                    val hasDocs = legacy.listFiles()?.any { it.isFile && it.name.endsWith(".kgv") } == true
                    legacyKey = if (hasDocs) {
                        AndroidKeystoreDocumentKeyProvider {
                            legacy.listFiles()?.any { it.isFile && (it.name.endsWith(".kgv") || it.name.endsWith(".backup")) } == true
                        }.getOrCreateKey()
                    } else {
                        KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
                    }
                    oldProvider = VaultDatabaseProvider(activity.applicationContext)
                    val backend = EncryptedDocumentMigrationBackend(
                        legacy, legacyKey!!, staging, published,
                        SessionMigrationVekKeyAccess(session),
                        SqlCipherDatabaseMigrationActions(activity.applicationContext, oldProvider!!, session)
                    )
                    if (VaultMigrationCoordinator(journal, backend).resume().phase != VaultMigrationPhase.COMPLETE) {
                        throw IOException("Migration did not reach the durable COMPLETE state")
                    }
                }
                if (journal.load()?.phase != VaultMigrationPhase.COMPLETE) throw IOException("No durable COMPLETE checkpoint exists")
                val provider = VaultDatabaseProvider(activity.applicationContext, session)
                newProvider = provider
                val store = EncryptedDocumentStore(published, SessionDocumentPayloadCryptography(session))
                val repository = DocumentRepository(activity.contentResolver, store, EncryptedMetadataIndexProvider(provider), session)
                val next = VaultRuntime(repository, OfflineOcrEngine(activity.applicationContext), ReminderRepository(activity.applicationContext, provider, session), provider)
                // Background migration may finish after onStop locked the session. Never publish
                // repositories from a stale migration epoch into the foreground UI.
                val runtimePublished = synchronized(this) {
                    if (migrationEpoch != accessEpoch || !session.isUnlocked()) {
                        false
                    } else {
                        runtime = next
                        true
                    }
                }
                if (!runtimePublished) {
                    provider.close()
                    newProvider = null
                    throw VaultLockedException()
                }
                postStateIfEpoch(VaultAccessUiState.Ready(next), migrationEpoch)
            } catch (failure: Exception) {
                try { session.lock() } catch (_: Exception) { }
                runtime = null
                try { newProvider?.close() } catch (_: Exception) { }
                postStateIfEpoch(
                    VaultAccessUiState.Locked("Migration could not be verified; legacy files and old wrappers remain. Unlock to resume. " + safeFailure(failure)),
                    migrationEpoch
                )
            } finally {
                legacyKey = null
                try { oldProvider?.close() } catch (_: Exception) { }
            }
        }
    }

    private fun orphanedVekMaterialExists(): Boolean {
        val journal = File(noBackup, "vault-migration")
        val staging = File(files, "encrypted_documents.migration-staging")
        val published = File(files, "encrypted_documents.vek")
        val databaseWrapper = File(noBackup, "vault-database-key.vek.wrap")
        val databasePending = File(noBackup, ".vault-database-key.vek.wrap.pending")
        return AuthBoundVaultKey().hasAnyWrappingKeyAlias() ||
            setup.hasArtifacts() || recovery.hasArtifacts() ||
            databaseWrapper.exists() || databasePending.exists() ||
            listOf(staging, published).any { it.exists() && it.listFiles()?.isNotEmpty() == true } ||
            (journal.exists() && journal.listFiles()?.isNotEmpty() == true)
    }

    private fun onPromptFailure(message: String) {
        val lastPurpose = purpose
        val key = authKey
        clearPrompt()
        when (lastPurpose) {
            Purpose.SETUP -> {
                if (!records.hasArtifacts() && !recovery.hasArtifacts() && !setup.hasArtifacts() &&
                    !File(noBackup, "vault-migration").exists()) {
                    try { key?.discardAbandonedSetup() } catch (_: Exception) { }
                }
                state(VaultAccessUiState.SetupRequired(message))
            }
            Purpose.RECOVERY_REWRAP -> {
                pendingVek?.fill(0)
                pendingVek = null
                pendingRecoveryText = null
                state(VaultAccessUiState.RecoveryRequired(message + " Enter the recovery key again to retry."))
            }
            else -> state(VaultAccessUiState.Locked(message))
        }
    }

    private fun clearPrompt() {
        wrappedVek?.fill(0)
        wrappedVek = null
        purpose = null
        authKey = null
        authAlias = null
    }

    private fun safeFailure(error: Exception): String = when (error) {
        is VaultLockedException -> "Vault is locked."
        is MissingEncryptionKeyException -> "The authenticated vault key is unavailable; recovery is required."
        is GeneralSecurityException -> "Authentication or integrity verification failed."
        is IOException -> error.message?.take(180) ?: "A protected vault operation failed."
        else -> "The protected vault operation could not be completed."
    }

    private fun state(value: VaultAccessUiState) {
        if (Looper.myLooper() == Looper.getMainLooper()) uiState.value = value else postState(value)
    }

    private fun postState(value: VaultAccessUiState) {
        main.post { uiState.value = value }
    }

    private fun postStateIfEpoch(value: VaultAccessUiState, expectedEpoch: Long) {
        main.post {
            if (accessEpoch == expectedEpoch) uiState.value = value
        }
    }
}
