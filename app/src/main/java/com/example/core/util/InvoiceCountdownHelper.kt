package com.example.core.util

import com.example.data.model.PendingInvoice
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object InvoiceCountdownHelper {

    private val dateFormatters = listOf(
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("Asia/Tehran")
        },
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("Asia/Tehran")
        },
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    )

    /**
     * Safely calculate remaining seconds for an invoice:
     * - Prevents crazy remainingSeconds like 200 hours due to server/database timezone skew
     * - Respects the merchant's configured timeout in minutes (e.g. 10 mins, 15 mins)
     */
    fun calculateSafeRemainingSeconds(
        invoice: PendingInvoice,
        configuredTimeoutMinutes: Int
    ): Long {
        val maxAllowedSeconds = (configuredTimeoutMinutes.coerceIn(2, 180)) * 60L
        val rawSeconds = invoice.remainingSeconds

        // If createdAt is available, attempt to calculate elapsed time precisely
        val createdAtStr = invoice.createdAt
        if (!createdAtStr.isNullOrBlank()) {
            for (formatter in dateFormatters) {
                try {
                    val createdDate: Date? = formatter.parse(createdAtStr.trim())
                    if (createdDate != null) {
                        val elapsedMs = System.currentTimeMillis() - createdDate.time
                        val elapsedSeconds = (elapsedMs / 1000L).coerceAtLeast(0L)
                        if (elapsedSeconds in 0L..maxAllowedSeconds) {
                            val computedRemaining = maxAllowedSeconds - elapsedSeconds
                            return computedRemaining.coerceAtLeast(0L)
                        } else if (elapsedSeconds > maxAllowedSeconds) {
                            return 0L // Expired
                        }
                    }
                } catch (_: Exception) {
                }
            }
        }

        // If remainingSeconds from server is higher than the merchant's timeout (or unreasonably high e.g. > 2 hours)
        return when {
            rawSeconds <= 0L -> 0L
            rawSeconds > maxAllowedSeconds -> maxAllowedSeconds
            else -> rawSeconds
        }
    }

    /**
     * Formats seconds into clean MM:SS or HH:MM:SS with Persian digits
     */
    fun formatRemainingTime(totalSeconds: Long): String {
        if (totalSeconds <= 0L) {
            return "منقضی شده"
        }
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60

        val formatted = if (hours > 0) {
            String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }

        return CurrencyUtils.toPersianDigits(formatted)
    }
}
