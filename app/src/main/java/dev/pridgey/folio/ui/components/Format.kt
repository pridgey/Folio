package dev.pridgey.folio.ui.components

import eu.kanade.tachiyomi.source.model.SManga
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

object Format {
    private val zone get() = ZoneId.systemDefault()

    /** "Just now", "5 min ago", "3 h ago", "Yesterday", "Mon", "Nov 3", "Nov 3, 2024". */
    fun relative(millis: Long, now: Long = System.currentTimeMillis()): String {
        if (millis <= 0) return ""
        val diff = now - millis
        val minutes = diff / 60_000
        return when {
            minutes < 1 -> "Just now"
            minutes < 60 -> "$minutes min ago"
            minutes < 60 * 24 && day(millis) == LocalDate.now(zone) -> "${minutes / 60} h ago"
            else -> date(millis)
        }
    }

    fun date(millis: Long): String {
        if (millis <= 0) return ""
        val d = day(millis)
        val today = LocalDate.now(zone)
        return when {
            d == today -> "Today"
            d == today.minusDays(1) -> "Yesterday"
            ChronoUnit.DAYS.between(d, today) in 2..6 -> d.format(DateTimeFormatter.ofPattern("EEEE", Locale.getDefault()))
            d.year == today.year -> d.format(DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()))
            else -> d.format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault()))
        }
    }

    fun day(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

    fun status(status: Int): String? = when (status) {
        SManga.ONGOING -> "Ongoing"
        SManga.COMPLETED -> "Completed"
        SManga.LICENSED -> "Licensed"
        SManga.PUBLISHING_FINISHED -> "Finished"
        SManga.CANCELLED -> "Cancelled"
        SManga.ON_HIATUS -> "On hiatus"
        else -> null
    }

    fun bytes(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "%.0f KB".format(bytes / 1024.0)
        bytes < 1024L * 1024 * 1024 -> "%.1f MB".format(bytes / 1024.0 / 1024)
        else -> "%.2f GB".format(bytes / 1024.0 / 1024 / 1024)
    }

    fun plural(n: Int, one: String, many: String = one + "s") = if (n == 1) "1 $one" else "$n $many"

    fun languageName(code: String): String = when (code) {
        "es-419" -> "Spanish (Latin America)"
        "pt-BR" -> "Portuguese (Brazil)"
        else -> Locale.forLanguageTag(code).getDisplayLanguage(Locale.getDefault()).replaceFirstChar { it.uppercase() }.ifEmpty { code }
    }
}
