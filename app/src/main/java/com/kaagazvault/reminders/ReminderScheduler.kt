package com.kaagazvault.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.kaagazvault.MainActivity
import com.kaagazvault.database.VaultDatabaseProvider
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Only the opaque reminder UUID is placed in WorkManager's separately stored database.
 * Titles, linked document IDs, and any medical/personal content stay in SQLCipher.
 */
internal object ReminderScheduler {
    const val INPUT_REMINDER_ID = "reminder_uuid"
    private const val WORK_PREFIX = "vault-reminder-"

    internal fun buildInputData(reminderId: String): Data {
        require(runCatching { UUID.fromString(reminderId) }.isSuccess) { "Invalid reminder identifier" }
        return workDataOf(INPUT_REMINDER_ID to reminderId)
    }

    fun schedule(context: Context, reminderId: String, dueAtEpochMillis: Long) {
        require(runCatching { UUID.fromString(reminderId) }.isSuccess) { "Invalid reminder identifier" }
        val delay = (dueAtEpochMillis - System.currentTimeMillis()).coerceAtLeast(0L)
        val request = OneTimeWorkRequestBuilder<ReminderNotificationWorker>()
            .setInputData(buildInputData(reminderId))
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork(WORK_PREFIX + reminderId, androidx.work.ExistingWorkPolicy.REPLACE, request)
    }

    fun cancel(context: Context, reminderId: String) {
        if (runCatching { UUID.fromString(reminderId) }.isSuccess) {
            WorkManager.getInstance(context.applicationContext).cancelUniqueWork(WORK_PREFIX + reminderId)
        }
    }
}

internal class ReminderNotificationWorker(
    appContext: Context,
    params: WorkerParameters
) : Worker(appContext, params) {
    override fun doWork(): Result {
        val reminderId = inputData.getString(ReminderScheduler.INPUT_REMINDER_ID)
            ?: return Result.failure()
        if (runCatching { UUID.fromString(reminderId) }.isFailure) return Result.failure()

        val provider = VaultDatabaseProvider(applicationContext)
        return try {
            // Deleted reminders become harmless no-ops; notification content stays generic.
            val reminder = provider.get().reminderDao().findById(reminderId) ?: return Result.success()
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) return Result.success()

            val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= 26) {
                manager.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "Document reminders", NotificationManager.IMPORTANCE_DEFAULT)
                )
            }
            // A user may revoke notification permission or disable the channel after
            // scheduling. Keep the reminder in the encrypted in-app list, but do not
            // retry a one-time job forever when Android will suppress its notification.
            if (Build.VERSION.SDK_INT >= 26 &&
                manager.getNotificationChannel(CHANNEL_ID)?.importance == NotificationManager.IMPORTANCE_NONE
            ) return Result.success()

            val openVaultIntent = PendingIntent.getActivity(
                applicationContext,
                0,
                Intent(applicationContext, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("Saved-document reminder")
                .setContentText("You have a reminder in Kaagaz Vault.")
                .setContentIntent(openVaultIntent)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build()
            NotificationManagerCompat.from(applicationContext).notify(reminder.id.hashCode(), notification)
            Result.success()
        } catch (_: SecurityException) {
            // Permission can change between the permission check and notify().
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        } finally {
            provider.close()
        }
    }

    private companion object {
        const val CHANNEL_ID = "vault_reminders"
    }
}
