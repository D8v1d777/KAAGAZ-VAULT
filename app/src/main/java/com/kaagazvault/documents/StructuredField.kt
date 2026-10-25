package com.kaagazvault.documents

/** A user-reviewable suggestion derived only from authenticated OCR text. */
data class StructuredField(
    val type: String,
    val value: String,
    val sourceExcerpt: String,
    val method: String,
    val uncertain: Boolean = true,
    val accepted: Boolean = false
)

/** Deterministic, bounded extraction; values are suggestions, never automatic actions. */
internal object StructuredFieldExtractor {
    private const val MAX_FIELDS = 8
    private const val MAX_VALUE = 120
    private const val MAX_EXCERPT = 180
    private val date = Regex("\\b(?:\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4}|\\d{4}[/-]\\d{1,2}[/-]\\d{1,2})\\b")
    private val amount = Regex("(?i)(?:₹|rs\\.?|inr)\\s*[0-9][0-9,]*(?:\\.[0-9]{1,2})?\\b|\\b[0-9][0-9,]*(?:\\.[0-9]{1,2})?\\s*(?:rupees|inr)\\b")
    private val identifier = Regex("(?i)\\b(?:id|number|no|ref|reference)\\s*[:#-]?\\s*[A-Z0-9][A-Z0-9/-]{3,24}\\b")
    private val name = Regex("(?im)^\\s*(?:name|patient|holder|applicant)\\s*[:=-]\\s*([^\\r\\n]{2,80})")

    fun extract(text: String): List<StructuredField> {
        if (text.isBlank()) return emptyList()
        val result = mutableListOf<StructuredField>()
        fun add(type: String, match: MatchResult, method: String, uncertain: Boolean = true, valueOverride: String? = null) {
            if (result.size >= MAX_FIELDS) return
            val value = (valueOverride ?: match.value).trim().replace(Regex("\\s+"), " ").take(MAX_VALUE)
            if (value.isBlank() || result.any { it.type == type && it.value == value }) return
            val start = (match.range.first - 45).coerceAtLeast(0)
            val end = (match.range.last + 46).coerceAtMost(text.length)
            result += StructuredField(type, value, text.substring(start, end).replace(Regex("\\s+"), " ").take(MAX_EXCERPT), method, uncertain)
        }
        date.findAll(text).forEach { add("date", it, "bounded date pattern") }
        amount.findAll(text).forEach { add("amount", it, "bounded currency pattern") }
        identifier.findAll(text).forEach { add("identifier", it, "labelled identifier pattern") }
        name.findAll(text).forEach { add("name", it, "labelled name pattern", valueOverride = it.groups[1]?.value) }
        return result
    }
}
