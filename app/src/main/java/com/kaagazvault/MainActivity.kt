package com.kaagazvault

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.kaagazvault.database.EncryptedMetadataIndexProvider
import com.kaagazvault.database.VaultDatabaseProvider
import com.kaagazvault.documents.DocumentRepository
import com.kaagazvault.documents.ImportedDocument
import com.kaagazvault.ocr.OfflineOcrEngine
import com.kaagazvault.reminders.ReminderRepository
import com.kaagazvault.reminders.ReminderSection
import com.kaagazvault.security.AndroidKeystoreDocumentKeyProvider
import com.kaagazvault.security.EncryptedDocumentStore
import java.io.IOException
import java.security.GeneralSecurityException
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    private val ioExecutor = Executors.newSingleThreadExecutor()
    private var databaseProvider: VaultDatabaseProvider? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Keep sensitive document previews out of the app switcher snapshot.
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val store = EncryptedDocumentStore(
            java.io.File(filesDir, "encrypted_documents"),
            AndroidKeystoreDocumentKeyProvider {
                java.io.File(filesDir, "encrypted_documents").listFiles()
                    ?.any { it.name.endsWith(".kgv") || it.name.endsWith(".backup") } == true
            }
        )
        val provider = VaultDatabaseProvider(applicationContext)
        databaseProvider = provider
        val repository = DocumentRepository(
            contentResolver,
            store,
            EncryptedMetadataIndexProvider(provider)
        )
        val ocrEngine = OfflineOcrEngine(applicationContext)
        val reminderRepository = ReminderRepository(applicationContext, provider)
        setContent {
            KaagazTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    VaultHomeScreen(
                        lifecycleOwner = this@MainActivity,
                        repository = repository,
                        ocrEngine = ocrEngine,
                        reminderRepository = reminderRepository,
                        submitIo = { work -> ioExecutor.execute(work) }
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        databaseProvider?.let { provider -> ioExecutor.execute { provider.close() } }
        ioExecutor.shutdown()
        super.onDestroy()
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
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    fun refresh(message: String? = null) {
        submitIo {
            runCatching { repository.list() }.onSuccess { result ->
                mainHandler.post {
                    documents.clear(); documents.addAll(result)
                    searchAvailable = repository.encryptedSearchAvailable
                    status = message ?: if (result.isEmpty()) "Your private shelf is ready for its first document." else "${result.size} document(s) safely stored on this device."
                }
            }.onFailure { mainHandler.post { status = "Could not unlock stored documents. Existing data was left untouched." } }
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

    LaunchedEffect(repository) { refresh() }

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
                            .onFailure { mainHandler.post { busy = false; status = "Capture could not be encrypted. No document was added." } }
                    }
                }
            )
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 22.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Header(documentCount = documents.size)
                VaultActionCard(
                    busy = busy,
                    status = status,
                    onImport = { playVaultClick(view); picker.launch(arrayOf("application/pdf", "image/*")) },
                    onScan = {
                        playVaultClick(view)
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) showCamera = true
                        else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                )
                SearchCard(
                    query = query,
                    enabled = !busy,
                    encryptedAvailable = searchAvailable,
                    onQueryChange = { query = it },
                    onSearch = {
                        playVaultClick(view); busy = true
                        submitIo {
                            runCatching { repository.search(query) }.onSuccess { result -> mainHandler.post { documents.clear(); documents.addAll(result); searchAvailable = repository.encryptedSearchAvailable; status = "Found ${result.size} local result(s)."; busy = false } }
                                .onFailure { mainHandler.post { busy = false; status = "Search failed. Your encrypted documents were left untouched." } }
                        }
                    },
                    onClear = { playVaultClick(view); query = ""; refresh("Showing all local documents.") }
                )
                ReminderSection(context, reminderRepository, submitIo, mainHandler)
                SectionTitle("YOUR DOCUMENTS", "${documents.size} private item${if (documents.size == 1) "" else "s"}")
                AnimatedVisibility(visible = documents.isEmpty(), enter = vaultFadeIn(), exit = vaultFadeOut()) {
                    EmptyVaultCard(onImport = { playVaultClick(view); picker.launch(arrayOf("application/pdf", "image/*")) })
                }
                documents.forEach { document ->
                    DocumentCard(document, busy, ocrEngine, repository, submitIo, mainHandler) { message -> busy = false; status = message; refresh() }
                }
                Text("Offline OCR: English, Hindi, and Telugu • review required", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = {
                    playVaultClick(view); showLicenses = true
                    submitIo { val result = runCatching { context.assets.open("licenses/THIRD_PARTY_NOTICES.txt").bufferedReader().use { it.readText() } }.getOrDefault("Third-party license notices are packaged with this application."); mainHandler.post { notices = result } }
                }) { Text("Third-party licenses") }
            }
        }
    }
    if (showLicenses) AlertDialog(onDismissRequest = { showLicenses = false }, title = { Text("Third-party licenses") }, text = { Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) { Text(notices) } }, confirmButton = { TextButton(onClick = { playVaultClick(view); showLicenses = false }) { Text("Close") } })
    }
}

@Composable
private fun Header(documentCount: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("KAAGAZ", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.secondaryContainer) { Text("ON DEVICE", Modifier.padding(horizontal = 12.dp, vertical = 7.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer, fontWeight = FontWeight.SemiBold) }
        }
        Text("A calmer place\nfor important papers.", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text(if (documentCount == 0) "Private by default. Ready when you are." else "Everything important, close at hand and under your control.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SectionTitle(title: String, supporting: String? = null) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        supporting?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun VaultActionCard(busy: Boolean, status: String, onImport: () -> Unit, onScan: () -> Unit) {
    Card(Modifier.fillMaxWidth().vaultAnimateContentSize(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), shape = RoundedCornerShape(28.dp)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
            Text("YOUR PRIVATE SHELF", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
            Text("Bring a paper in. We’ll keep it close.", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.SemiBold)
            Text("Files, names, and extracted text stay encrypted on this device.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onImport, enabled = !busy, modifier = Modifier.weight(1f), shape = RoundedCornerShape(18.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)) { Text("Import") }
                OutlinedButton(onClick = onScan, enabled = !busy, modifier = Modifier.weight(1f), shape = RoundedCornerShape(18.dp)) { Text("Scan") }
            }
            AnimatedVisibility(visible = busy, enter = vaultFadeIn(), exit = vaultFadeOut()) { LinearProgressIndicator(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primary) }
            Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

@Composable
private fun SearchCard(query: String, enabled: Boolean, encryptedAvailable: Boolean, onQueryChange: (String) -> Unit, onSearch: () -> Unit, onClear: () -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle("FIND SOMETHING", if (encryptedAvailable) "encrypted search" else "local fallback")
            OutlinedTextField(query, onQueryChange, Modifier.fillMaxWidth(), label = { Text("Search names or text") }, placeholder = { Text("Search names or text") }, singleLine = true, enabled = enabled, shape = RoundedCornerShape(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilledTonalButton(onClick = onSearch, enabled = enabled, modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp)) { Text("Search") }
                OutlinedButton(onClick = onClear, enabled = enabled, modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp)) { Text("Show all") }
            }
            if (!encryptedAvailable) Text("Encrypted search is unavailable; this search scans locally in memory.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun EmptyVaultCard(onImport: () -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Nothing here yet", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onTertiaryContainer, fontWeight = FontWeight.SemiBold)
            Text("Start with one document. You can always add more later.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
            FilledTonalButton(onClick = onImport, shape = RoundedCornerShape(16.dp)) { Text("Add first document") }
        }
    }
}

@Composable
private fun DocumentCard(document: ImportedDocument, busy: Boolean, ocrEngine: OfflineOcrEngine, repository: DocumentRepository, submitIo: (() -> Unit) -> Unit, mainHandler: Handler, onDone: (String) -> Unit) {
    var editedOcr by remember(document.id, document.ocrText) { mutableStateOf(document.ocrText.orEmpty()) }
    var showReview by remember(document.id) { mutableStateOf(false) }
    var showDeleteConfirm by remember(document.id) { mutableStateOf(false) }
    val view = LocalView.current
    Card(Modifier.fillMaxWidth().vaultAnimateContentSize(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) { Text(document.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold); Text("${document.mimeType} • ${formatBytes(document.byteSize)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Text("SAFE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
            Divider()
            if (document.ocrText != null) {
                Text("Text extraction • ${if (document.ocrReviewed) "reviewed" else "needs review"}", style = MaterialTheme.typography.labelMedium, color = if (document.ocrReviewed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary)
                if (showReview) {
                    OutlinedTextField(editedOcr, { editedOcr = it }, Modifier.fillMaxWidth(), label = { Text("Review extracted text") }, minLines = 3, maxLines = 8, enabled = !busy, shape = RoundedCornerShape(16.dp))
                    SoftButton(onClick = { submitIo { runCatching { repository.saveReviewedOcr(document.id, editedOcr) }.onSuccess { mainHandler.post { playVaultSuccess(view); onDone("Corrections saved privately. Verify every field before relying on it.") } }.onFailure { mainHandler.post { onDone("Could not save OCR corrections. Existing data was left untouched.") } } } }, enabled = !busy, label = if (document.ocrReviewed) "Save corrections" else "Save and mark reviewed")
                    TextButton(onClick = { playVaultClick(view); showReview = false }, enabled = !busy) { Text("Hide extracted text") }
                } else {
                    Text("Extracted text is hidden until you choose to review it.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    SoftButton(onClick = { showReview = true }, enabled = !busy, label = "Review extracted text")
                }
            } else if (document.mimeType.startsWith("image/") || document.mimeType == "application/pdf") {
                SoftButton(onClick = { submitIo { runCatching { repository.recognizeDocument(document.id, ocrEngine) }.onSuccess { mainHandler.post { playVaultSuccess(view); onDone("Text extracted locally. Review it before relying on any field.") } }.onFailure { mainHandler.post { onDone("Offline text extraction failed. The original remains stored.") } } } }, enabled = !busy, label = if (document.mimeType == "application/pdf") "Extract text offline" else "Recognize text offline")
            }
            TextButton(onClick = { playVaultClick(view); showDeleteConfirm = true }, enabled = !busy) { Text("Remove document") }
        }
    }
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Remove this document?") },
            text = { Text("This removes the encrypted copy from the vault. This action cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    playVaultClick(view)
                    showDeleteConfirm = false
                    submitIo { runCatching { repository.delete(document.id) }.onSuccess { mainHandler.post { onDone("Document removed from the vault.") } }.onFailure { mainHandler.post { onDone("Could not remove the document.") } } }
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { playVaultClick(view); showDeleteConfirm = false }) { Text("Keep it") } }
        )
    }
}

@Composable
private fun SoftButton(onClick: () -> Unit, enabled: Boolean, label: String) { VaultTonalButton(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text(label) } }

private fun safeMessage(error: Exception): String = when (error) {
    is com.kaagazvault.documents.DocumentTooLargeException -> "This file exceeds the 31 MiB limit. Nothing was saved."
    is com.kaagazvault.documents.UnsupportedDocumentTypeException -> "Choose a supported PDF or image file. Nothing was saved."
    is IOException -> "Import failed. No document was added."
    is GeneralSecurityException -> "Encryption failed. No document was added."
    else -> "Import failed. No document was added."
}

private fun formatBytes(size: Int): String = if (size < 1024 * 1024) "${size / 1024} KiB" else String.format(java.util.Locale.ROOT, "%.1f MiB", size / (1024f * 1024f))
