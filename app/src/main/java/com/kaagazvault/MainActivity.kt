package com.kaagazvault

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kaagazvault.camera.CameraCaptureScreen
import com.kaagazvault.database.EncryptedMetadataIndexProvider
import com.kaagazvault.database.VaultDatabaseProvider
import com.kaagazvault.documents.DocumentRepository
import com.kaagazvault.documents.ImportedDocument
import com.kaagazvault.ocr.OfflineOcrEngine
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
        val store = EncryptedDocumentStore(
            java.io.File(filesDir, "encrypted_documents"),
            AndroidKeystoreDocumentKeyProvider()
        )
        val provider = VaultDatabaseProvider(applicationContext)
        databaseProvider = provider
        val indexProvider = EncryptedMetadataIndexProvider(provider)
        val repository = DocumentRepository(contentResolver, store, indexProvider)
        val ocrEngine = OfflineOcrEngine(applicationContext)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    VaultHomeScreen(
                        lifecycleOwner = this@MainActivity,
                        repository = repository,
                        ocrEngine = ocrEngine,
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
    submitIo: (() -> Unit) -> Unit
) {
    val documents = remember { mutableStateListOf<ImportedDocument>() }
    val status = remember { mutableStateOf("Your documents stay on this device.") }
    val busy = remember { mutableStateOf(false) }
    val searchQuery = remember { mutableStateOf("") }
    val searchAvailable = remember { mutableStateOf(false) }
    val showLicenses = remember { mutableStateOf(false) }
    val showCamera = remember { mutableStateOf(false) }
    val thirdPartyNotices = remember { mutableStateOf("Loading third-party notices…") }
    val context = LocalContext.current
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            showCamera.value = true
        } else {
            status.value = "Camera permission denied. You can still import existing files."
        }
    }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) {
            status.value = "Import cancelled. Nothing was changed."
        } else {
            busy.value = true
            status.value = "Encrypting document locally…"
            submitIo {
                try {
                    val imported = repository.import(uri)
                    val refreshed = repository.list()
                    mainHandler.post {
                        documents.clear()
                        documents.addAll(refreshed)
                        searchAvailable.value = repository.encryptedSearchAvailable
                        status.value = "Saved encrypted: ${imported.displayName}"
                        busy.value = false
                    }
                } catch (error: Exception) {
                    mainHandler.post {
                        status.value = safeMessage(error)
                        busy.value = false
                    }
                }
            }
        }
    }

    LaunchedEffect(repository) {
        submitIo {
            try {
                val refreshed = repository.list()
                mainHandler.post {
                    documents.clear()
                    documents.addAll(refreshed)
                    searchAvailable.value = repository.encryptedSearchAvailable
                    status.value = if (refreshed.isEmpty()) {
                        "No documents yet. Import a PDF or image to begin."
                    } else {
                        "${refreshed.size} encrypted document(s) on this device."
                    }
                }
            } catch (_: Exception) {
                mainHandler.post {
                    status.value = "Could not unlock stored documents. Existing data was left untouched."
                }
            }
        }
    }

    Scaffold { contentPadding ->
        if (showCamera.value) {
            CameraCaptureScreen(
                lifecycleOwner = lifecycleOwner,
                modifier = Modifier.padding(contentPadding),
                onClose = { showCamera.value = false },
                onCaptured = { bytes ->
                    showCamera.value = false
                    busy.value = true
                    submitIo {
                        try {
                            val imported = repository.importBytes(
                                "Scan_${System.currentTimeMillis()}.jpg",
                                "image/jpeg",
                                bytes
                            )
                            val refreshed = repository.list()
                            mainHandler.post {
                                documents.clear()
                                documents.addAll(refreshed)
                                searchAvailable.value = repository.encryptedSearchAvailable
                                status.value = "Captured page encrypted: ${imported.displayName}"
                                busy.value = false
                            }
                        } catch (_: Exception) {
                            mainHandler.post {
                                status.value = "Capture could not be encrypted. No document was added."
                                busy.value = false
                            }
                        }
                    }
                }
            )
        } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "KAAGAZ VAULT",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Your documents.\nYour control.",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "An offline-first place for the papers that matter.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("PRIVATE LOCAL VAULT", style = MaterialTheme.typography.labelMedium)
                    Text(
                        "Files, document names, and extracted text are encrypted before storage.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Button(
                        onClick = { picker.launch(arrayOf("application/pdf", "image/*")) },
                        enabled = !busy.value,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Import PDF or image")
                    }
                    OutlinedButton(
                        onClick = {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                                PackageManager.PERMISSION_GRANTED
                            ) {
                                showCamera.value = true
                            } else {
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        },
                        enabled = !busy.value,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Scan with camera")
                    }
                    if (busy.value) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator()
                            Text("Working locally…", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Text(status.value, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Maximum file size: 31 MiB. No account or network connection is used.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("LOCAL SEARCH", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = searchQuery.value,
                    onValueChange = { searchQuery.value = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Search names and extracted text") },
                    singleLine = true,
                    enabled = !busy.value
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            busy.value = true
                            submitIo {
                                try {
                                    val results = repository.search(searchQuery.value)
                                    mainHandler.post {
                                        documents.clear()
                                        documents.addAll(results)
                                        searchAvailable.value = repository.encryptedSearchAvailable
                                        status.value = if (searchAvailable.value) {
                                            "Encrypted local search found ${results.size} result(s)."
                                        } else {
                                            "Search used a local in-memory scan; encrypted database is unavailable."
                                        }
                                        busy.value = false
                                    }
                                } catch (_: Exception) {
                                    mainHandler.post {
                                        status.value = "Search failed. Your encrypted documents were left untouched."
                                        busy.value = false
                                    }
                                }
                            }
                        },
                        enabled = !busy.value
                    ) { Text("Search") }
                    OutlinedButton(
                        onClick = {
                            searchQuery.value = ""
                            busy.value = true
                            submitIo {
                                val refreshed = repository.list()
                                mainHandler.post {
                                    documents.clear()
                                    documents.addAll(refreshed)
                                    searchAvailable.value = repository.encryptedSearchAvailable
                                    status.value = "Showing all local documents."
                                    busy.value = false
                                }
                            }
                        },
                        enabled = !busy.value
                    ) { Text("Clear") }
                }
                if (!searchAvailable.value) {
                    Text(
                        "Encrypted search database unavailable. Search will scan encrypted documents in memory.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            Text("YOUR DOCUMENTS", style = MaterialTheme.typography.titleMedium)
            if (documents.isEmpty()) {
                Text(
                    "Your vault is empty. Imported documents will appear here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                documents.forEach { document ->
                    val editedOcr = remember(document.id, document.ocrText) {
                        mutableStateOf(document.ocrText.orEmpty())
                    }
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                document.displayName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "${document.mimeType} • ${formatBytes(document.byteSize)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "Encrypted on this device",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )

                            if (document.ocrText != null) {
                                Text(
                                    "Offline OCR • engine score ${document.ocrConfidence ?: 0}/100 • ${if (document.ocrReviewed) "reviewed" else "needs review"}",
                                    style = MaterialTheme.typography.labelMedium
                                )
                                if (document.ocrTruncated) {
                                    Text(
                                        "Extracted text was shortened to fit local storage. Re-run OCR on smaller sections if needed.",
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                OutlinedTextField(
                                    value = editedOcr.value,
                                    onValueChange = { editedOcr.value = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text("Review or correct extracted text") },
                                    minLines = 3,
                                    maxLines = 8,
                                    enabled = !busy.value
                                )
                                OutlinedButton(
                                    onClick = {
                                        busy.value = true
                                        submitIo {
                                            try {
                                                repository.saveReviewedOcr(document.id, editedOcr.value)
                                                val refreshed = repository.list()
                                                mainHandler.post {
                                                    documents.clear()
                                                    documents.addAll(refreshed)
                                                    searchAvailable.value = repository.encryptedSearchAvailable
                                                    status.value = "Corrections encrypted and marked reviewed. Verify every field before relying on it."
                                                    busy.value = false
                                                }
                                            } catch (_: Exception) {
                                                mainHandler.post {
                                                    status.value = "Could not save OCR corrections. Existing encrypted data was left untouched."
                                                    busy.value = false
                                                }
                                            }
                                        }
                                    },
                                    enabled = !busy.value
                                ) {
                                    Text(if (document.ocrReviewed) "Save corrections" else "Save and mark reviewed")
                                }
                            } else if (document.mimeType.startsWith("image/")) {
                                OutlinedButton(
                                    onClick = {
                                        busy.value = true
                                        status.value = "Recognizing text locally…"
                                        submitIo {
                                            try {
                                                repository.recognizeImage(document.id, ocrEngine)
                                                val refreshed = repository.list()
                                                mainHandler.post {
                                                    documents.clear()
                                                    documents.addAll(refreshed)
                                                    searchAvailable.value = repository.encryptedSearchAvailable
                                                    status.value = "Text extracted locally. Review it before relying on any field."
                                                    busy.value = false
                                                }
                                            } catch (_: Exception) {
                                                mainHandler.post {
                                                    status.value = "Offline OCR failed. The original encrypted document remains stored."
                                                    busy.value = false
                                                }
                                            }
                                        }
                                    },
                                    enabled = !busy.value
                                ) {
                                    Text("Recognize text offline")
                                }
                            } else {
                                Text(
                                    "PDF OCR is not available in this version.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            OutlinedButton(
                                onClick = {
                                    busy.value = true
                                    submitIo {
                                        try {
                                            repository.delete(document.id)
                                            val refreshed = repository.list()
                                            mainHandler.post {
                                                documents.clear()
                                                documents.addAll(refreshed)
                                                searchAvailable.value = repository.encryptedSearchAvailable
                                                status.value = "Document removed from the vault."
                                                busy.value = false
                                            }
                                        } catch (_: Exception) {
                                            mainHandler.post {
                                                status.value = "Could not remove the document."
                                                busy.value = false
                                            }
                                        }
                                    }
                                },
                                enabled = !busy.value
                            ) {
                                Text("Delete")
                            }
                        }
                    }
                }
            }

            Text(
                "Offline OCR: English, Hindi, and Telugu • encrypted local search • review required • no automatic actions",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(
                onClick = {
                    showLicenses.value = true
                    submitIo {
                        val notices = runCatching {
                            context.assets.open("licenses/THIRD_PARTY_NOTICES.txt")
                                .bufferedReader()
                                .use { it.readText() }
                        }.getOrDefault("Third-party license notices are packaged with this application.")
                        mainHandler.post { thirdPartyNotices.value = notices }
                    }
                }
            ) { Text("Third-party licenses") }
        }
    }

    if (showLicenses.value) {
        AlertDialog(
            onDismissRequest = { showLicenses.value = false },
            title = { Text("Third-party licenses") },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(thirdPartyNotices.value)
                }
            },
            confirmButton = {
                TextButton(onClick = { showLicenses.value = false }) { Text("Close") }
            }
        )
    }
}

private fun safeMessage(error: Exception): String = when (error) {
    is com.kaagazvault.documents.DocumentTooLargeException ->
        "This file exceeds the 31 MiB limit. Nothing was saved."
    is com.kaagazvault.documents.UnsupportedDocumentTypeException ->
        "Choose a supported PDF or image file. Nothing was saved."
    is IOException ->
        error.message?.takeIf { it in setOf(
            "The selected document is empty",
            "Could not open the selected document",
            "Could not determine the selected file type"
        ) } ?: "Import failed. No document was added."
    is GeneralSecurityException ->
        "Encryption failed. No document was added."
    else -> "Import failed. No document was added."
}

private fun formatBytes(size: Int): String =
    if (size < 1024 * 1024) "${size / 1024} KiB"
    else String.format(java.util.Locale.ROOT, "%.1f MiB", size / (1024f * 1024f))
