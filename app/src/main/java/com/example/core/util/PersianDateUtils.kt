package com.example.core.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * ابزار تبدیل دقیق و بسیار سریع تاریخ میلادی به تقویم هجری شمسی (جلالی)
 * بدون نیاز به کتابخانه‌های سنگین خارجی
 */
object PersianDateUtils {

    private val persianMonths = arrayOf(
        "فروردین", "اردیبهشت", "خرداد",
        "تیر", "مرداد", "شهریور",
        "مهر", "آبان", "آذر",
        "دی", "بهمن", "اسفند"
    )

    private val persianDigits = arrayOf('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹')

    fun toPersianDigits(text: String): String {
        val sb = java.lang.StringBuilder()
        for (char in text) {
            if (char in '0'..'9') {
                sb.append(persianDigits[char - '0'])
            } else {
                sb.append(char)
            }
        }
        return sb.toString()
    }

    /**
     * تبدیل تایم‌استمپ یا رشته تاریخ به فرمت شمسی کوتاه:
     * مثال: ۱۴۰۵/۰۶/۲۹ - ۱۵:۳۰
     */
    fun formatToPersianDateTime(raw: Any?): String {
        if (raw == null) return ""
        return try {
            val timestamp = parseToMillis(raw) ?: return raw.toString()
            val (year, month, day, hour, minute) = getPersianDateParts(timestamp)
            val formatted = String.format(
                Locale.US,
                "%04d/%02d/%02d - %02d:%02d",
                year, month, day, hour, minute
            )
            toPersianDigits(formatted)
        } catch (_: Exception) {
            raw.toString()
        }
    }

    /**
     * فرمت خوانا با نام ماه:
     * مثال: ۲۹ شهریور ۱۴۰۵، ساعت ۱۵:۳۰
     */
    fun formatToPersianFriendly(raw: Any?): String {
        if (raw == null) return ""
        return try {
            val timestamp = parseToMillis(raw) ?: return raw.toString()
            val (year, month, day, hour, minute) = getPersianDateParts(timestamp)
            val monthName = persianMonths.getOrElse(month - 1) { "" }
            val formatted = String.format(
                Locale.US,
                "%d %s %d، ساعت %02d:%02d",
                day, monthName, year, hour, minute
            )
            toPersianDigits(formatted)
        } catch (_: Exception) {
            raw.toString()
        }
    }

    /**
     * فرمت زمان نسبی هوشمند:
     * مثال: «لحظاتی پیش»، «۵ دقیقه پیش»، «۲ ساعت پیش» یا تاریخ کامل شمسی
     */
    fun formatRelativeTime(raw: Any?): String {
        if (raw == null) return ""
        return try {
            val timestamp = parseToMillis(raw) ?: return raw.toString()
            val now = System.currentTimeMillis()
            val diff = now - timestamp

            when {
                diff < 0 -> formatToPersianDateTime(timestamp)
                diff < 60_000L -> "لحظاتی پیش"
                diff < 3600_000L -> {
                    val mins = (diff / 60_000L).toInt()
                    toPersianDigits("$mins دقیقه پیش")
                }
                diff < 86400_000L -> {
                    val hours = (diff / 3600_000L).toInt()
                    toPersianDigits("$hours ساعت پیش")
                }
                else -> formatToPersianDateTime(timestamp)
            }
        } catch (_: Exception) {
            raw.toString()
        }
    }

    private fun parseToMillis(raw: Any): Long? {
        return when (raw) {
            is Long -> if (raw < 10_000_000_000L) raw * 1000L else raw
            is Number -> {
                val v = raw.toLong()
                if (v < 10_000_000_000L) v * 1000L else v
            }
            is String -> parseDateStringToMillis(raw)
            else -> null
        }
    }

    private fun parseDateStringToMillis(dateStr: String): Long? {
        val trimmed = dateStr.trim()
        if (trimmed.isEmpty()) return null

        // اگر رشته عددی تایم‌استمپ باشد
        trimmed.toLongOrNull()?.let {
            return if (it < 10_000_000_000L) it * 1000L else it
        }

        val patterns = arrayOf(
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy/MM/dd HH:mm:ss",
            "yyyy-MM-dd"
        )

        for (pattern in patterns) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.US)
                if (pattern.endsWith("'Z'")) {
                    sdf.timeZone = TimeZone.getTimeZone("UTC")
                } else {
                    sdf.timeZone = TimeZone.getTimeZone("Asia/Tehran")
                }
                val date = sdf.parse(trimmed)
                if (date != null) return date.time
            } catch (_: Exception) {}
        }
        return null
    }

    data class PersianDateParts(
        val year: Int,
        val month: Int,
        val day: Int,
        val hour: Int,
        val minute: Int,
        val second: Int
    )

    private fun getPersianDateParts(timestamp: Long): PersianDateParts {
        return try {
            val calendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Tehran"))
            calendar.timeInMillis = timestamp

            val gYear = calendar.get(Calendar.YEAR)
            val gMonth = calendar.get(Calendar.MONTH) + 1
            val gDay = calendar.get(Calendar.DAY_OF_MONTH)
            val hour = calendar.get(Calendar.HOUR_OF_DAY)
            val minute = calendar.get(Calendar.MINUTE)
            val second = calendar.get(Calendar.SECOND)

            val (pYear, pMonth, pDay) = gregorianToJalali(gYear, gMonth, gDay)
            PersianDateParts(pYear, pMonth, pDay, hour, minute, second)
        } catch (_: Exception) {
            PersianDateParts(1403, 1, 1, 0, 0, 0)
        }
    }

    /**
     * الگوریتم استاندارد و فوق سریع تبدیل میلادی به جلالی
     */
    private fun gregorianToJalali(gy: Int, gm: Int, gd: Int): Triple<Int, Int, Int> {
        val gDaysInMonth = intArrayOf(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        val jDaysInMonth = intArrayOf(31, 31, 31, 31, 31, 31, 30, 30, 30, 30, 30, 29)

        val gy2 = gy - 1600
        val gm2 = (gm - 1).coerceIn(0, 11)
        val gd2 = gd - 1

        var gDayNo = 365 * gy2 + (gy2 + 3) / 4 - (gy2 + 99) / 100 + (gy2 + 399) / 400
        for (i in 0 until gm2) {
            gDayNo += gDaysInMonth[i]
        }
        if (gm2 > 1 && ((gy % 4 == 0 && gy % 100 != 0) || (gy % 400 == 0))) {
            gDayNo++
        }
        gDayNo += gd2

        var jDayNo = gDayNo - 79
        val jNp = jDayNo / 12053
        jDayNo %= 12053

        var jy = 979 + 33 * jNp + 4 * (jDayNo / 1461)
        jDayNo %= 1461

        if (jDayNo >= 366) {
            jy += (jDayNo - 1) / 365
            jDayNo = (jDayNo - 1) % 365
        }

        var jm = 1
        var jd = 1
        for (i in 0..11) {
            if (jDayNo < jDaysInMonth[i]) {
                jm = i + 1
                jd = maxOf(1, jDayNo + 1)
                break
            }
            jDayNo -= jDaysInMonth[i]
        }

        return Triple(jy, jm, jd)
    }
}
