package com.kaagazvault

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.kaagazvault.camera.CameraCaptureScreen
import com.kaagazvault.documents.DocumentRepository
import com.kaagazvault.documents.ImportedDocument
import com.kaagazvault.ocr.OfflineOcrEngine
import com.kaagazvault.reminders.ReminderRepository
import com.kaagazvault.reminders.ReminderSection
import com.kaagazvault.security.MissingEncryptionKeyException
import com.kaagazvault.security.VaultAccessController
import com.kaagazvault.security.VaultAccessUiState
import com.kaagazvault.security.VaultRuntime
import com.kaagazvault.security.VaultSession
import com.kaagazvault.sync.SettingsMergeDecision
import com.kaagazvault.sync.SettingsPairingTicket
import com.kaagazvault.sync.SettingsSyncCrypto
import com.kaagazvault.sync.SettingsSyncMerger
import com.kaagazvault.sync.SettingsTransferCode
import com.kaagazvault.sync.SyncableSettingsStore
import com.kaagazvault.sync.SyncableVaultSettings
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.security.GeneralSecurityException
import java.util.concurrent.Executors

class MainActivity : FragmentActivity() {
    private val ioExecutor = Executors.newSingleThreadExecutor()
    private var accessController: VaultAccessController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Keep sensitive document previews and the recovery key out of app-switcher snapshots.
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)

        val controller = VaultAccessController(this, VaultSession())
        accessController = controller
        setContent {
            val accessState by controller.uiState
            LaunchedEffect(Unit) { controller.initialize() }
            KaagazTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    when (val state = accessState) {
                        is VaultAccessUiState.Ready -> {
                            val runtime: VaultRuntime = state.runtime
                            VaultHomeScreen(
                                lifecycleOwner = this@MainActivity,
                                repository = runtime.documents,
                                ocrEngine = runtime.ocr,
                                reminderRepository = runtime.reminders,
                                submitIo = { work -> ioExecutor.execute(work) }
                            )
                        }
                        else -> VaultAccessScreen(state = state, controller = controller)
                    }
                }
            }
        }
    }

    override fun onStop() {
        accessController?.onActivityStopped()
        super.onStop()
    }

    override fun onDestroy() {
        accessController?.dispose()
        accessController = null
        ioExecutor.shutdownNow()
        super.onDestroy()
    }
}

@Composable
private fun VaultAccessScreen(
    state: VaultAccessUiState,
    controller: VaultAccessController
) {
    var recoveryKey by remember(state) { mutableStateOf("") }
    var recoveryConfirmation by remember(state) { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(24.dp))
        Text("KAAGAZ VAULT", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            "Your documents stay on this device. Unlock access before opening the vault.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        when (state) {
            VaultAccessUiState.Initializing -> {
                CircularProgressIndicator()
                Text("Checking protected vault state…")
            }
            is VaultAccessUiState.SetupRequired -> {
                Text("Set up device authentication and create a recovery key before storing documents.")
                state.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(onClick = controller::beginSetup, modifier = Modifier.fillMaxWidth()) {
                    Text("Set up secure vault")
                }
            }
            is VaultAccessUiState.Locked -> {
                Text("The vault is locked. Authenticate to access your documents.")
                state.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(onClick = controller::beginUnlock, modifier = Modifier.fillMaxWidth()) {
                    Text("Unlock vault")
                }
            }
            is VaultAccessUiState.ConfirmRecoveryKey -> {
                Text("Save this recovery key somewhere private and offline. Anyone with it may be able to restore vault access.")
                Card(Modifier.fillMaxWidth()) {
                    SelectionContainer {
                        Text(
                            state.key,
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
                state.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                OutlinedTextField(
                    value = recoveryConfirmation,
                    onValueChange = { recoveryConfirmation = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Re-enter the full recovery key") },
                    supportingText = { Text("Confirmation must match exactly.") }
                )
                Button(
                    onClick = { controller.confirmRecoveryKeyStored(recoveryConfirmation) },
                    enabled = recoveryConfirmation.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Confirm saved recovery key") }
            }
            is VaultAccessUiState.RecoveryRequired -> {
                Text(
                    "Normal unlock is unavailable. Enter your existing recovery key. Do not reset or reinstall the app while recovery is unresolved.",
                    color = MaterialTheme.colorScheme.error
                )
                Text(state.message)
                OutlinedTextField(
                    value = recoveryKey,
                    onValueChange = { recoveryKey = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Recovery key") },
                    supportingText = { Text("The key is used locally and is not sent to a server.") }
                )
                Button(
                    onClick = { controller.recoverWithKey(recoveryKey.trim()) },
                    enabled = recoveryKey.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Restore access") }
            }
            is VaultAccessUiState.Migrating -> {
                CircularProgressIndicator()
                Text(state.message)
                Text("Keep the app open while protected data is verified. Existing files are not intentionally deleted by this screen.")
            }
            is VaultAccessUiState.Ready -> Unit
        }
    }
}
@Composable
private fun VaultHomeScreen(
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    repository: DocumentRepository,
    ocrEngine: OfflineOcrEngine,
    reminderRepository: ReminderRepository,
    submitIo: (() -> Unit) -> Unit
) {
    val documents = remember { mutableStateListOf<ImportedDocument>() }
    var status by remember { mutableStateOf("Your documents stay on this device.") }
    var busy by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var searchAvailable by remember { mutableStateOf(false) }
    var showLicenses by remember { mutableStateOf(false) }
    var showCamera by remember { mutableStateOf(false) }
    var notices by remember { mutableStateOf("Loading third-party notices…") }
    val context = LocalContext.current
    val view = LocalView.current
    val simplifiedMode = rememberSimplifiedMode(context)
    val syncStore = remember { SyncableSettingsStore(context) }
    var syncEnabled by remember { mutableStateOf(syncStore.isSyncEnabled()) }
    var pendingExportBundle by remember { mutableStateOf<ByteArray?>(null) }
    var exportCode by remember { mutableStateOf<String?>(null) }
    var showExportCode by remember { mutableStateOf(false) }
    var pendingImportBundle by remember { mutableStateOf<ByteArray?>(null) }
    var showImportCode by remember { mutableStateOf(false) }
    var importCode by remember { mutableStateOf("") }
    var pendingIncomingSettings by remember { mutableStateOf<SyncableVaultSettings?>(null) }
    val activeTypography = if (simplifiedMode.value) MaterialTheme.typography.simplified() else MaterialTheme.typography
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    // The authenticated home tree is removed on lock. Clear document metadata and zero any
    // pending settings-transfer buffers rather than leaving them referenced by disposed UI state.
    DisposableEffect(Unit) {
        onDispose {
            documents.clear()
            pendingExportBundle?.fill(0)
            pendingImportBundle?.fill(0)
        }
    }

    fun refresh(message: String? = null) {
        submitIo {
            runCatching { repository.list() }.onSuccess { result ->
                mainHandler.post {
                    documents.clear(); documents.addAll(result)
                    searchAvailable = repository.encryptedSearchAvailable
                    val unreadableCount = result.count { !it.isReadable }
                    status = if (unreadableCount > 0) {
                        unreadableDocumentNotice(unreadableCount)
                    } else {
                        message ?: if (result.isEmpty()) "Your private shelf is ready for its first document." else "${result.size} document(s) safely stored on this device."
                    }
                }
            }.onFailure { error ->
                mainHandler.post {
                    documents.clear()
                    searchAvailable = false
                    status = if (error is MissingEncryptionKeyException) {
                        "Vault encryption key unavailable. Existing encrypted files were left unchanged; no replacement key was created."
                    } else {
                        "Could not read the vault. Existing encrypted files were left untouched."
                    }
                }
            }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) showCamera = true else status = "Camera permission denied. You can still import existing files."
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) status = "Import cancelled. Nothing was changed."
        else {
            busy = true; status = "Encrypting locally…"
            submitIo {
                runCatching { repository.import(uri) }.onSuccess { imported ->
                    mainHandler.post { playVaultSuccess(view); busy = false; status = "Saved privately: ${imported.displayName}"; refresh() }
                }.onFailure { error -> mainHandler.post { busy = false; status = safeMessage(error as? Exception ?: Exception()) } }
            }
        }
    }
    val exportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri: Uri? ->
        val bundle = pendingExportBundle
        pendingExportBundle = null
        if (uri == null || bundle == null) {
            status = "Settings export cancelled. Nothing was changed."
        } else {
            submitIo {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(bundle) }
                        ?: throw IOException("Could not open the selected destination")
                }.onSuccess { mainHandler.post { status = "Encrypted accessibility settings saved. Transfer the code separately." } }
                    .onFailure { mainHandler.post { status = "Could not save settings. Nothing was changed." } }
            }
        }
    }
    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) {
            status = "Settings import cancelled. Nothing was changed."
        } else {
            submitIo {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        input.readBounded(MAX_SETTINGS_FILE_BYTES)
                    } ?: throw IOException("Could not open the selected settings file")
                }.onSuccess { bytes -> mainHandler.post { pendingImportBundle = bytes; importCode = ""; showImportCode = true } }
                    .onFailure { mainHandler.post { status = "This is not a valid Kaagaz settings file." } }
            }
        }
    }

    LaunchedEffect(repository) { refresh() }

    MaterialTheme(typography = activeTypography) {
        Box(Modifier.fillMaxSize()) {
        AmbientVaultBackdrop()
            Scaffold(containerColor = Color.Transparent) { padding ->
        if (showCamera) {
            CameraCaptureScreen(
                lifecycleOwner = lifecycleOwner,
                modifier = Modifier.padding(padding),
                onClose = { playVaultClick(view); showCamera = false },
                onCaptured = { bytes ->
                    showCamera = false; busy = true
                    submitIo {
                        runCatching { repository.importBytes("Scan_${System.currentTimeMillis()}.jpg", "image/jpeg", bytes) }
                            .onSuccess { imported -> mainHandler.post { playVaultSuccess(view); busy = false; status = "Captured privately: ${imported.displayName}"; refresh() } }
                            .onFailure { error -> mainHandler.post { busy = false; status = documentFailureMessage(error, "Capture could not be encrypted. No document was added.") } }
                    }
                }
            )
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = if (simplifiedMode.value) SimplifiedLayout.screenHorizontalPadding else 20.dp, vertical = if (simplifiedMode.value) 24.dp else 22.dp),
                verticalArrangement = Arrangement.spacedBy(if (simplifiedMode.value) SimplifiedLayout.sectionSpacing else 18.dp)
            ) {
                Header(documentCount = documents.size, simplified = simplifiedMode.value)
                SimplifiedModeCard(
                    enabled = simplifiedMode.value,
                    onEnabledChange = { enabled ->
                        playVaultClick(view)
                        simplifiedMode.value = enabled
                        SimplifiedModePreferences.write(context, enabled)
                    }
                )
                SyncSettingsCard(
                    enabled = syncEnabled,
                    simplified = simplifiedMode.value,
                    onEnabledChange = { enabled ->
                        playVaultClick(view)
                        syncStore.setSyncEnabled(enabled)
                        syncEnabled = enabled
                        status = if (enabled) "Encrypted settings transfer is ready. Documents remain on this device." else "Settings transfer is off. Your local vault is unchanged."
                    },
                    onExport = {
                        val ticket = SettingsPairingTicket.create(System.currentTimeMillis())
                        pendingExportBundle = SettingsSyncCrypto.encrypt(syncStore.read(), ticket.secret)
                        exportCode = SettingsTransferCode.encode(ticket.secret)
                        showExportCode = true
                    },
                    onImport = { importPicker.launch(arrayOf("application/octet-stream", "application/octet-stream+kaagaz")) },
                    onReset = {
                        syncStore.resetSyncableSettings()
                        syncStore.setSyncEnabled(false)
                        syncEnabled = false
                        simplifiedMode.value = false
                        status = "Synced accessibility settings reset locally. Documents were not changed."
                    }
                )
                VaultActionCard(
                    busy = busy,
                    status = status,
                    simplified = simplifiedMode.value,
                    onImport = { picker.launch(arrayOf("application/pdf", "image/*")) },
                    onScan = {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) showCamera = true
                        else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                )
                SearchCard(
                    query = query,
                    enabled = !busy,
                    encryptedAvailable = searchAvailable,
                    simplified = simplifiedMode.value,
                    onQueryChange = { query = it },
                    onSearch = {
                        busy = true
                        submitIo {
                            runCatching { repository.search(query) }.onSuccess { result ->
                                mainHandler.post {
                                    documents.clear()
                                    documents.addAll(result)
                                    searchAvailable = repository.encryptedSearchAvailable
                                    val unreadableCount = result.count { !it.isReadable }
                                    status = if (unreadableCount > 0) {
                                        unreadableDocumentNotice(unreadableCount)
                                    } else {
                                        "Found ${result.count { it.isReadable }} local result(s)."
                                    }
                                    busy = false
                                }
                            }.onFailure { error ->
                                mainHandler.post {
                                    busy = false
                                    documents.clear()
                                    searchAvailable = false
                                    status = if (error is MissingEncryptionKeyException) {
                                        "Vault encryption key unavailable. Existing encrypted files were left unchanged; no replacement key was created."
                                    } else {
                                        "Search failed. Your encrypted documents were left untouched."
                                    }
                                }
                            }
                        }
                    },
                    onClear = { query = ""; refresh("Showing all local documents.") }
                )
                ReminderSection(context, reminderRepository, submitIo, mainHandler, simplifiedMode.value)
                val readableDocumentCount = documents.count { it.isReadable }
                val unreadableDocumentCount = documents.size - readableDocumentCount
                val documentSummary = if (unreadableDocumentCount > 0) {
                    "$readableDocumentCount readable • $unreadableDocumentCount cannot open"
                } else {
                    "$readableDocumentCount private item${if (readableDocumentCount == 1) "" else "s"}"
                }
                SectionTitle("YOUR DOCUMENTS", documentSummary, simplifiedMode.value)
                AnimatedVisibility(visible = documents.isEmpty(), enter = vaultFadeIn(), exit = vaultFadeOut()) {
                    EmptyVaultCard(simplified = simplifiedMode.value, onImport = { playVaultClick(view); picker.launch(arrayOf("application/pdf", "image/*")) })
                }
                documents.forEach { document ->
                    DocumentCard(document, busy, simplifiedMode.value, ocrEngine, repository, submitIo, mainHandler) { message -> busy = false; status = message; refresh() }
                }
                Text("Offline OCR: English, Hindi, and Telugu • review required", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(modifier = Modifier.heightIn(min = if (simplifiedMode.value) SimplifiedLayout.controlMinHeight else 0.dp), onClick = {
                    playVaultClick(view); showLicenses = true
                    submitIo { val result = runCatching { context.assets.open("licenses/THIRD_PARTY_NOTICES.txt").bufferedReader().use { it.readText() } }.getOrDefault("Third-party license notices are packaged with this application."); mainHandler.post { notices = result } }
                }) { Text("Third-party licenses") }
            }
        }
    }
            if (showLicenses) AlertDialog(onDismissRequest = { showLicenses = false }, title = { Text("Third-party licenses") }, text = { Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) { Text(notices) } }, confirmButton = { TextButton(onClick = { playVaultClick(view); showLicenses = false }) { Text("Close") } })
            if (showExportCode && exportCode != null) {
                TransferCodeDialog(
                    code = exportCode!!,
                    onSave = { showExportCode = false; exportPicker.launch("kaagaz-accessibility-settings.kgs") },
                    onDismiss = { showExportCode = false; pendingExportBundle = null }
                )
            }
            if (showImportCode) {
                ImportCodeDialog(
                    code = importCode,
                    onCodeChange = { importCode = it },
                    onContinue = {
                        runCatching {
                            val bundle = pendingImportBundle ?: throw IOException("Settings file is missing")
                            SettingsSyncCrypto.decrypt(bundle, SettingsTransferCode.decode(importCode))
                        }.onSuccess { incoming ->
                            pendingIncomingSettings = incoming
                            showImportCode = false
                            pendingImportBundle = null
                        }.onFailure { status = "That code could not unlock this settings file. Nothing was changed." }
                    },
                    onDismiss = { showImportCode = false; pendingImportBundle = null }
                )
            }
            pendingIncomingSettings?.let { incoming ->
                SettingsImportPreviewDialog(
                    incoming = incoming,
                    onKeepLocal = {
                        val result = SettingsSyncMerger.merge(syncStore.read(), incoming, SettingsMergeDecision.KEEP_LOCAL)
                        syncStore.write(result.settings)
                        pendingIncomingSettings = null
                        status = "Local accessibility settings kept. Nothing else was changed."
                    },
                    onUseIncoming = {
                        val result = SettingsSyncMerger.merge(syncStore.read(), incoming, SettingsMergeDecision.USE_INCOMING)
                        syncStore.write(result.settings)
                        simplifiedMode.value = result.settings.simplifiedModeEnabled
                        pendingIncomingSettings = null
                        status = if (result.voiceEnablementNeedsReview) "Settings applied; voice navigation still needs local review." else "Accessibility settings applied on this device."
                    },
                    onDismiss = { pendingIncomingSettings = null }
                )
            }
        }
    }
}

@Composable
private fun SimplifiedModeCard(enabled: Boolean, onEnabledChange: (Boolean) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().heightIn(min = SimplifiedLayout.controlMinHeight),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(SimplifiedLayout.cornerRadius)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Simplified view", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("Larger text, clearer actions, and more space.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = enabled, onCheckedChange = onEnabledChange)
        }
    }
}

@Composable
private fun Header(documentCount: Int, simplified: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("KAAGAZ", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.secondaryContainer) { Text("ON DEVICE", Modifier.padding(horizontal = 12.dp, vertical = 7.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer, fontWeight = FontWeight.SemiBold) }
        }
        Text("A calmer place\nfor important papers.", style = if (simplified) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text(if (documentCount == 0) "Private by default. Ready when you are." else "Everything important, close at hand and under your control.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SectionTitle(title: String, supporting: String? = null, simplified: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
        Text(title, style = if (simplified) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        supporting?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun VaultActionCard(busy: Boolean, status: String, simplified: Boolean, onImport: () -> Unit, onScan: () -> Unit) {
    val view = LocalView.current
    Card(Modifier.fillMaxWidth().vaultAnimateContentSize(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), shape = RoundedCornerShape(28.dp)) {
        Column(Modifier.padding(if (simplified) SimplifiedLayout.cardPadding else 20.dp), verticalArrangement = Arrangement.spacedBy(if (simplified) SimplifiedLayout.actionGap else 13.dp)) {
            Text("YOUR PRIVATE SHELF", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
            Text("Bring a paper in. We’ll keep it close.", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.SemiBold)
            Text("Files, names, and extracted text stay encrypted on this device.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            if (simplified) {
                VaultPrimaryButton(onClick = onImport, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = SimplifiedLayout.primaryControlMinHeight), content = { Text("Import a document") })
                VaultOutlinedButton(onClick = onScan, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = SimplifiedLayout.primaryControlMinHeight), content = { Text("Scan a document") })
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = { playVaultClick(view); onImport() }, enabled = !busy, modifier = Modifier.weight(1f), shape = RoundedCornerShape(18.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)) { Text("Import") }
                    OutlinedButton(onClick = { playVaultClick(view); onScan() }, enabled = !busy, modifier = Modifier.weight(1f), shape = RoundedCornerShape(18.dp)) { Text("Scan") }
                }
            }
            AnimatedVisibility(visible = busy, enter = vaultFadeIn(), exit = vaultFadeOut()) { LinearProgressIndicator(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primary) }
            Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

@Composable
private fun SearchCard(query: String, enabled: Boolean, encryptedAvailable: Boolean, simplified: Boolean, onQueryChange: (String) -> Unit, onSearch: () -> Unit, onClear: () -> Unit) {
    val view = LocalView.current
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(if (simplified) SimplifiedLayout.cardPadding else 18.dp), verticalArrangement = Arrangement.spacedBy(if (simplified) SimplifiedLayout.actionGap else 12.dp)) {
            SectionTitle("FIND SOMETHING", if (encryptedAvailable) "encrypted search" else "local fallback", simplified)
            OutlinedTextField(query, onQueryChange, Modifier.fillMaxWidth().heightIn(min = if (simplified) SimplifiedLayout.controlMinHeight else 0.dp), label = { Text("Search names or text") }, placeholder = { Text("Search names or text") }, singleLine = true, enabled = enabled, shape = RoundedCornerShape(16.dp))
            if (simplified) {
                VaultTonalButton(onClick = onSearch, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = SimplifiedLayout.controlMinHeight), content = { Text("Search") })
                VaultOutlinedButton(onClick = onClear, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = SimplifiedLayout.controlMinHeight), content = { Text("Show all documents") })
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilledTonalButton(onClick = { playVaultClick(view); onSearch() }, enabled = enabled, modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp)) { Text("Search") }
                    OutlinedButton(onClick = { playVaultClick(view); onClear() }, enabled = enabled, modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp)) { Text("Show all") }
                }
            }
            if (!encryptedAvailable) Text("Encrypted search is unavailable; this search scans locally in memory.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun EmptyVaultCard(simplified: Boolean, onImport: () -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(if (simplified) SimplifiedLayout.cardPadding else 20.dp), verticalArrangement = Arrangement.spacedBy(if (simplified) SimplifiedLayout.actionGap else 10.dp)) {
            Text("Nothing here yet", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onTertiaryContainer, fontWeight = FontWeight.SemiBold)
            Text("Start with one document. You can always add more later.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
            FilledTonalButton(onClick = onImport, modifier = Modifier.fillMaxWidth().heightIn(min = if (simplified) SimplifiedLayout.primaryControlMinHeight else 0.dp), shape = RoundedCornerShape(16.dp)) { Text(if (simplified) "Add your first document" else "Add first document") }
        }
    }
}

private fun unreadableDocumentNotice(count: Int): String =
    "$count encrypted document(s) cannot be opened. Their names and contents are hidden; stored files were left unchanged."

@Composable
private fun UnreadableDocumentCard(simplified: Boolean) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Column(
            Modifier.padding(if (simplified) SimplifiedLayout.cardPadding else 18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Unreadable encrypted document",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Text(
                "This item cannot be opened. Its name and contents are hidden, and the encrypted file has not been changed.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

@Composable
private fun DocumentCard(document: ImportedDocument, busy: Boolean, simplified: Boolean, ocrEngine: OfflineOcrEngine, repository: DocumentRepository, submitIo: (() -> Unit) -> Unit, mainHandler: Handler, onDone: (String) -> Unit) {
    if (!document.isReadable) {
        UnreadableDocumentCard(simplified)
        return
    }
    var editedOcr by remember(document.id, document.ocrText) { mutableStateOf(document.ocrText.orEmpty()) }
    var showReview by remember(document.id) { mutableStateOf(false) }
    var showDeleteConfirm by remember(document.id) { mutableStateOf(false) }
    val view = LocalView.current
    Card(Modifier.fillMaxWidth().vaultAnimateContentSize(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(if (simplified) SimplifiedLayout.cardPadding else 18.dp), verticalArrangement = Arrangement.spacedBy(if (simplified) SimplifiedLayout.actionGap else 10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) { Text(document.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold); Text("${document.mimeType} • ${formatBytes(document.byteSize)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Text("SAFE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
            Divider()
            if (document.ocrText != null) {
                Text("Text extraction • ${if (document.ocrReviewed) "reviewed" else "needs review"}", style = MaterialTheme.typography.labelMedium, color = if (document.ocrReviewed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary)
                if (showReview) {
                    OutlinedTextField(editedOcr, { editedOcr = it }, Modifier.fillMaxWidth(), label = { Text("Review extracted text") }, minLines = 3, maxLines = 8, enabled = !busy, shape = RoundedCornerShape(16.dp))
                    SoftButton(onClick = { submitIo { runCatching { repository.saveReviewedOcr(document.id, editedOcr) }.onSuccess { mainHandler.post { playVaultSuccess(view); onDone("Corrections saved privately. Verify every field before relying on it.") } }.onFailure { error -> mainHandler.post { onDone(documentFailureMessage(error, "Could not save OCR corrections. Existing data was left untouched.")) } } } }, enabled = !busy, simplified = simplified, label = if (document.ocrReviewed) "Save corrections" else "Save and mark reviewed")
                    TextButton(modifier = Modifier.heightIn(min = if (simplified) SimplifiedLayout.controlMinHeight else 0.dp), onClick = { playVaultClick(view); showReview = false }, enabled = !busy) { Text("Hide extracted text") }
                } else {
                    Text("Extracted text is hidden until you choose to review it.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    SoftButton(onClick = { showReview = true }, enabled = !busy, simplified = simplified, label = "Review extracted text")
                }
            } else if (document.mimeType.startsWith("image/") || document.mimeType == "application/pdf") {
                SoftButton(onClick = { submitIo { runCatching { repository.recognizeDocument(document.id, ocrEngine) }.onSuccess { mainHandler.post { playVaultSuccess(view); onDone("Text extracted locally. Review it before relying on any field.") } }.onFailure { error -> mainHandler.post { onDone(documentFailureMessage(error, "Offline text extraction failed. The original remains stored.")) } } } }, enabled = !busy, simplified = simplified, label = if (document.mimeType == "application/pdf") "Extract text offline" else "Recognize text offline")
            }
            TextButton(modifier = Modifier.heightIn(min = if (simplified) SimplifiedLayout.controlMinHeight else 0.dp), onClick = { playVaultClick(view); showDeleteConfirm = true }, enabled = !busy) { Text("Remove document") }
        }
    }
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Remove this document?") },
            text = { Text("This removes the encrypted copy from the vault. This action cannot be undone.") },
            confirmButton = {
                TextButton(modifier = Modifier.heightIn(min = if (simplified) SimplifiedLayout.controlMinHeight else 0.dp), onClick = {
                    playVaultClick(view)
                    showDeleteConfirm = false
                    submitIo { runCatching { repository.delete(document.id) }.onSuccess { mainHandler.post { onDone("Document removed from the vault.") } }.onFailure { mainHandler.post { onDone("Could not remove the document.") } } }
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(modifier = Modifier.heightIn(min = if (simplified) SimplifiedLayout.controlMinHeight else 0.dp), onClick = { playVaultClick(view); showDeleteConfirm = false }) { Text("Keep it") } }
        )
    }
}

@Composable
private fun SoftButton(onClick: () -> Unit, enabled: Boolean, simplified: Boolean = false, label: String) { VaultTonalButton(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = if (simplified) SimplifiedLayout.controlMinHeight else 0.dp)) { Text(label) } }

private fun vaultKeyUnavailableMessage(): String =
    "Vault encryption key unavailable. Existing encrypted files were left unchanged; no replacement key was created."

private fun documentFailureMessage(error: Throwable, fallback: String): String =
    if (error is MissingEncryptionKeyException) vaultKeyUnavailableMessage() else fallback

private fun safeMessage(error: Exception): String = when (error) {
    is MissingEncryptionKeyException -> vaultKeyUnavailableMessage()
    is com.kaagazvault.documents.DocumentTooLargeException -> "This file exceeds the 31 MiB limit. Nothing was saved."
    is com.kaagazvault.documents.UnsupportedDocumentTypeException -> "Choose a supported PDF or image file. Nothing was saved."
    is IOException -> "Import failed. No document was added."
    is GeneralSecurityException -> "Encryption failed. No document was added."
    else -> "Import failed. No document was added."
}

private fun formatBytes(size: Int): String = if (size < 1024 * 1024) "${size / 1024} KiB" else String.format(java.util.Locale.ROOT, "%.1f MiB", size / (1024f * 1024f))

private fun InputStream.readBounded(maxBytes: Int): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(4096)
    var total = 0
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        total += count
        if (total > maxBytes) throw IOException("Settings file is too large")
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}

private const val MAX_SETTINGS_FILE_BYTES = 64 * 1024


@Composable
private fun SyncSettingsCard(
    enabled: Boolean,
    simplified: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onReset: () -> Unit
) {
    val view = LocalView.current
    Card(
        modifier = Modifier.fillMaxWidth().vaultAnimateContentSize(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(SimplifiedLayout.cornerRadius)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(if (simplified) SimplifiedLayout.cardPadding else 18.dp),
            verticalArrangement = Arrangement.spacedBy(if (simplified) SimplifiedLayout.actionGap else 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("Sync accessibility settings", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("Only text size, simplified view, voice guidance, and haptic choices can transfer. Documents and recordings never leave this device.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = enabled, onCheckedChange = onEnabledChange)
            }
            if (enabled) {
                Text("Encrypted file transfer is ready. Use the code shown during export on the other device.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                if (simplified) {
                    VaultTonalButton(onClick = onExport, modifier = Modifier.fillMaxWidth().heightIn(min = SimplifiedLayout.controlMinHeight), content = { Text("Export settings") })
                    VaultOutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth().heightIn(min = SimplifiedLayout.controlMinHeight), content = { Text("Import settings") })
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FilledTonalButton(onClick = { playVaultClick(view); onExport() }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp)) { Text("Export") }
                        OutlinedButton(onClick = { playVaultClick(view); onImport() }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp)) { Text("Import") }
                    }
                }
                TextButton(modifier = Modifier.heightIn(min = if (simplified) SimplifiedLayout.controlMinHeight else 0.dp), onClick = { playVaultClick(view); onReset() }) { Text("Reset synced settings on this device") }
            } else {
                Text("Not syncing. Your local accessibility choices still work normally.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun TransferCodeDialog(code: String, onSave: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save encrypted settings") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Save the settings file, then enter this code on the other device. Keep the code private and use it for this transfer only.")
                Text(code, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                Text("Documents, reminders, OCR, and voice recordings are not included.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { Button(onClick = onSave) { Text("Save settings file") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun ImportCodeDialog(code: String, onCodeChange: (String) -> Unit, onContinue: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Unlock settings file") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Enter the transfer code from the other device. Nothing will be applied until you review the changes.")
                OutlinedTextField(value = code, onValueChange = onCodeChange, modifier = Modifier.fillMaxWidth(), label = { Text("Transfer code") }, minLines = 2, maxLines = 3)
            }
        },
        confirmButton = { Button(onClick = onContinue, enabled = code.isNotBlank()) { Text("Review settings") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun SettingsImportPreviewDialog(
    incoming: SyncableVaultSettings,
    onKeepLocal: () -> Unit,
    onUseIncoming: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Review incoming settings") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Only accessibility and voice preferences are included. Your documents stay on this device.")
                Text("Simplified view: ${if (incoming.simplifiedModeEnabled) "On" else "Off"}")
                Text("Voice navigation: ${if (incoming.voiceNavigationEnabled) "On" else "Off"}")
                Text("Spoken guidance: ${if (incoming.spokenGuidanceEnabled) "On" else "Off"}")
                Text("Voice haptics: ${incoming.voiceHapticPreference.name.replace('_', ' ')}")
            }
        },
        confirmButton = { Button(onClick = onUseIncoming) { Text("Use incoming settings") } },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onKeepLocal) { Text("Keep local") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}
