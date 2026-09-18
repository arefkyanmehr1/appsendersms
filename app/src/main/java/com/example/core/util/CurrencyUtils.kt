package com.example.core.util

import java.text.NumberFormat
import java.util.Locale

object CurrencyUtils {

    fun normalizePersianArabicDigits(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input) {
            when (ch) {
                in '۰'..'۹' -> sb.append('0' + (ch - '۰'))
                in '٠'..'٩' -> sb.append('0' + (ch - '٠'))
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    fun formatRials(amount: Long): String {
        val formatted = NumberFormat.getNumberInstance(Locale.US).format(amount)
        return "$formatted ریال"
    }

    fun formatTomans(amountInRials: Long): String {
        val tomans = amountInRials / 10
        val formatted = NumberFormat.getNumberInstance(Locale.US).format(tomans)
        return "$formatted تومان"
    }

    fun toPersianDigits(text: String): String {
        val sb = StringBuilder(text.length)
        for (ch in text) {
            when (ch) {
                in '0'..'9' -> sb.append('۰' + (ch - '0'))
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }
}
