package com.kaagazvault.reminders

/** Deterministic domain rules shared by the repository and JVM tests. */
internal object ReminderPolicy {
    const val MAX_TITLE_LENGTH = 160

    fun normalizeTitle(value: String): String {
        val normalized = value
            .filterNot(Char::isISOControl)
            .trim()
            .take(MAX_TITLE_LENGTH)
        require(normalized.isNotBlank()) { "Enter a reminder title." }
        return normalized
    }

    fun requireFutureDueTime(dueAtEpochMillis: Long, nowEpochMillis: Long) {
        require(dueAtEpochMillis > nowEpochMillis) { "Choose a future date and time." }
    }
}
