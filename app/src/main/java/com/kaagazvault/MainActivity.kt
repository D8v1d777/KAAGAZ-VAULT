package com.kaagazvault

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kaagazvault.documents.DocumentRepository
import com.kaagazvault.documents.ImportedDocument
import com.kaagazvault.security.AndroidKeystoreDocumentKeyProvider
import com.kaagazvault.security.EncryptedDocumentStore
import java.io.IOException
import java.security.GeneralSecurityException
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    private val ioExecutor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = EncryptedDocumentStore(
            FileDirectory.documents(filesDir),
            AndroidKeystoreDocumentKeyProvider()
        )
        val repository = DocumentRepository(contentResolver, store)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    VaultHomeScreen(
                        repository = repository,
                        submitIo = { work -> ioExecutor.execute(work) }
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        ioExecutor.shutdown()
        super.onDestroy()
    }
}

private object FileDirectory {
    fun documents(filesDir: java.io.File): java.io.File = java.io.File(filesDir, "encrypted_documents")
}

@Composable
private fun VaultHomeScreen(
    repository: DocumentRepository,
    submitIo: (() -> Unit) -> Unit
) {
    val documents = remember { mutableStateListOf<ImportedDocument>() }
    val status = remember { mutableStateOf("Your documents stay on this device.") }
    val busy = remember { mutableStateOf(false) }
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
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        documents.clear()
                        documents.addAll(refreshed)
                        status.value = "Saved encrypted: ${imported.displayName}"
                        busy.value = false
                    }
                } catch (error: Exception) {
                    android.os.Handler(mainLooper).post {
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
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    documents.clear()
                    documents.addAll(refreshed)
                    status.value = if (refreshed.isEmpty()) {
                        "No documents yet. Import a PDF or image to begin."
                    } else {
                        "${refreshed.size} encrypted document(s) on this device."
                    }
                }
            } catch (_: Exception) {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    status.value = "Could not unlock stored documents. Existing data was left untouched."
                }
            }
        }
    }

    Scaffold { contentPadding ->
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
                        "Imported files are encrypted before being written to app-private storage.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Button(
                        onClick = { picker.launch(arrayOf("application/pdf", "image/*")) },
                        enabled = !busy.value,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Import PDF or image")
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

            Text("YOUR DOCUMENTS", style = MaterialTheme.typography.titleMedium)
            if (documents.isEmpty()) {
                Text(
                    "Your vault is empty. Imported documents will appear here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                documents.forEach { document ->
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
                            OutlinedButton(
                                onClick = {
                                    busy.value = true
                                    submitIo {
                                        try {
                                            repository.delete(document.id)
                                            val refreshed = repository.list()
                                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                                documents.clear()
                                                documents.addAll(refreshed)
                                                status.value = "Document removed from the vault."
                                                busy.value = false
                                            }
                                        } catch (_: Exception) {
                                            android.os.Handler(android.os.Looper.getMainLooper()).post {
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
                "Offline by design • No cloud sync • No OCR yet",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun safeMessage(error: Exception): String = when (error) {
    is com.kaagazvault.documents.DocumentTooLargeException ->
        "This file exceeds the 31 MiB limit. Nothing was saved."
    is com.kaagazvault.documents.UnsupportedDocumentTypeException ->
        "Choose a PDF or image file. Nothing was saved."
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
