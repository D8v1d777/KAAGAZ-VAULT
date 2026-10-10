package com.kaagazvault.reminders

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.kaagazvault.database.ReminderEntity
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

@Composable
internal fun ReminderSection(
    context: Context,
    repository: ReminderRepository,
    submitIo: (() -> Unit) -> Unit,
    mainHandler: Handler
) {
    val reminders = remember { mutableStateListOf<ReminderEntity>() }
    val title = remember { mutableStateOf("") }
    val dueAt = remember { mutableStateOf(0L) }
    val showCreate = remember { mutableStateOf(false) }
    val status = remember { mutableStateOf("Reminders are created only when you confirm them.") }
    val busy = remember { mutableStateOf(false) }

    fun refresh() {
        submitIo {
            try {
                val list = repository.list()
                mainHandler.post { reminders.clear(); reminders.addAll(list) }
            } catch (_: Exception) {
                mainHandler.post { status.value = "Could not read encrypted reminders." }
            }
        }
    }

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        status.value = if (granted) "Notifications enabled. Reminder text remains hidden on the lock screen."
        else "Notifications are off; reminders remain saved in the vault."
    }

    androidx.compose.runtime.LaunchedEffect(repository) { refresh() }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("REMINDERS", style = MaterialTheme.typography.titleMedium)
            Text(
                "Reminder details stay in encrypted storage. Notifications use generic text and never reveal document names.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(onClick = { title.value = ""; dueAt.value = 0L; showCreate.value = true }) {
                Text("Add reminder")
            }
            Text(status.value, style = MaterialTheme.typography.bodySmall)
            reminders.forEach { reminder ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(reminder.title, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            DateFormat.getDateTimeInstance().format(Date(reminder.dueAtEpochMillis)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = {
                        busy.value = true
                        submitIo {
                            try {
                                repository.delete(reminder.id)
                                val list = repository.list()
                                mainHandler.post {
                                    reminders.clear(); reminders.addAll(list)
                                    status.value = "Reminder deleted and its scheduled work cancelled."
                                    busy.value = false
                                }
                            } catch (_: Exception) {
                                mainHandler.post { status.value = "Could not delete reminder."; busy.value = false }
                            }
                        }
                    }, enabled = !busy.value) { Text("Delete") }
                }
            }
        }
    }

    if (showCreate.value) {
        AlertDialog(
            onDismissRequest = { showCreate.value = false },
            title = { Text("Create a reminder") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = title.value,
                        onValueChange = { title.value = it.take(160) },
                        label = { Text("Reminder title") },
                        singleLine = true
                    )
                    OutlinedButton(onClick = {
                        val now = Calendar.getInstance()
                        DatePickerDialog(
                            context,
                            { _, year, month, day ->
                                val selected = Calendar.getInstance().apply {
                                    set(Calendar.YEAR, year); set(Calendar.MONTH, month); set(Calendar.DAY_OF_MONTH, day)
                                    set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                                }
                                TimePickerDialog(
                                    context,
                                    { _, hour, minute ->
                                        selected.set(Calendar.HOUR_OF_DAY, hour)
                                        selected.set(Calendar.MINUTE, minute)
                                        dueAt.value = selected.timeInMillis
                                    },
                                    now.get(Calendar.HOUR_OF_DAY),
                                    now.get(Calendar.MINUTE),
                                    android.text.format.DateFormat.is24HourFormat(context)
                                ).show()
                            },
                            now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH)
                        ).show()
                    }) { Text(if (dueAt.value == 0L) "Choose date and time" else DateFormat.getDateTimeInstance().format(Date(dueAt.value))) }
                    Text(
                        "Only create reminders for actions you choose yourself. Text found by OCR is never converted into reminders automatically.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                TextButton(enabled = !busy.value, onClick = {
                    busy.value = true
                    submitIo {
                        try {
                            repository.create(title.value, dueAt.value)
                            val list = repository.list()
                            mainHandler.post {
                                reminders.clear(); reminders.addAll(list)
                                status.value = "Reminder saved. Notification content will remain generic."
                                busy.value = false
                                showCreate.value = false
                                if (Build.VERSION.SDK_INT >= 33 &&
                                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                                ) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        } catch (error: IllegalArgumentException) {
                            mainHandler.post { status.value = error.message ?: "Choose a title and future date."; busy.value = false }
                        } catch (_: Exception) {
                            mainHandler.post { status.value = "Could not save the reminder."; busy.value = false }
                        }
                    }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showCreate.value = false }) { Text("Cancel") }
            }
        )
    }
}
