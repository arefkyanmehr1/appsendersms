package com.example.sms.parser

import com.example.core.security.HashUtils
import com.example.core.util.CurrencyUtils
import com.example.sms.model.ConfidenceLevel
import com.example.sms.model.ParsedPayment
import java.util.regex.Pattern

class GenericIranianPaymentParser : PaymentSmsParser {

    private val withdrawalKeywords = listOf(
        "برداشت",
        "کسر",
        "خرید",
        "انتقال از",
        "بدهکار شد",
        "debited",
        "withdrawal"
    )

    private val depositKeywords = listOf(
        "واریز",
        "واریز شد",
        "انتقال به",
        "شارژ شد",
        "بستانکار شد",
        "پایا",
        "ساتنا",
        "پل",
        "دریافت وجه",
        "credited",
        "deposit"
    )

    private val bankDictionary = listOf(
        "ملت" to "بانک ملت",
        "ملی" to "بانک ملی",
        "تجارت" to "بانک تجارت",
        "صادرات" to "بانک صادرات",
        "سامان" to "بانک سامان",
        "پاسارگاد" to "بانک پاسارگاد",
        "پارسیان" to "بانک پارسیان",
        "سپه" to "بانک سپه",
        "کشاورزی" to "بانک کشاورزی",
        "مسکن" to "بانک مسکن",
        "رسالت" to "بانک قرض‌الحسنه رسالت",
        "مهر ایران" to "بانک مهر ایران",
        "آینده" to "بانک آینده",
        "بلوبانک" to "بلوبانک",
        "بلو" to "بلوبانک",
        "شهر" to "بانک شهر",
        "رفاه" to "بانک رفاه",
        "سرمایه" to "بانک سرمایه",
        "دی" to "بانک دی",
        "سینا" to "بانک سینا",
        "اقتصاد نوین" to "بانک اقتصاد نوین"
    )

    override fun canParse(sender: String?, body: String): Boolean {
        val normalized = CurrencyUtils.normalizePersianArabicDigits(body.lowercase())
        val hasDeposit = depositKeywords.any { normalized.contains(it) }
        val hasWithdrawal = withdrawalKeywords.any { normalized.contains(it) }

        // Must indicate deposit and NOT be an obvious withdrawal
        return hasDeposit && !hasWithdrawal
    }

    override fun parse(sender: String?, body: String): ParsedPayment? {
        val normalized = CurrencyUtils.normalizePersianArabicDigits(body)

        // Safety check: skip withdrawals
        if (withdrawalKeywords.any { normalized.contains(it) }) {
            return null
        }

        val amount = extractAmount(normalized) ?: return null
        if (amount <= 0) return null

        val trackingCode = extractTrackingCode(normalized)
        val cardLast4 = extractCardLast4(normalized)
        val bankName = detectBank(sender, normalized)
        val smsHash = HashUtils.sha256Hex(body)

        val confidence = when {
            trackingCode != null && cardLast4 != null -> ConfidenceLevel.HIGH
            trackingCode != null -> ConfidenceLevel.HIGH
            else -> ConfidenceLevel.MEDIUM
        }

        return ParsedPayment(
            amount = amount,
            bankName = bankName,
            trackingCode = trackingCode,
            cardLast4 = cardLast4,
            smsHash = smsHash,
            receivedAt = System.currentTimeMillis(),
            confidence = confidence,
            sender = sender
        )
    }

    private fun extractAmount(text: String): Long? {
        val lines = text.split('\n', '،', ';').map { it.trim() }.filter { it.isNotBlank() }
        val balanceWords = listOf("موجودی", "مانده", "باقیمانده", "balance", "available")
        val labelled = Pattern.compile(
            "(?:مبلغ|مبلغ واریز|واریز(?: شد|شده)?|دریافت وجه|بستانکار)[:\\s]*([0-9][0-9,\\.]{2,18})\\s*(ریال|تومان)?",
            Pattern.CASE_INSENSITIVE
        )

        fun parse(raw: String, unit: String?, source: String): Long? {
            val digits = raw.replace(",", "").replace(".", "")
            val value = digits.toLongOrNull() ?: return null
            if (value <= 0L) return null
            if (balanceWords.any { source.contains(it, ignoreCase = true) }) return null
            return try {
                if (unit == "تومان" || source.contains("تومان")) Math.multiplyExact(value, 10L) else value
            } catch (_: ArithmeticException) { null }
        }

        // Prefer an explicitly labelled amount. This avoids card, reference, date and balance numbers.
        for (line in lines) {
            if (balanceWords.any { line.contains(it, ignoreCase = true) }) continue
            val matcher = labelled.matcher(line)
            if (matcher.find()) {
                parse(matcher.group(1) ?: continue, matcher.group(2), line)?.let { return it }
            }
        }

        // Conservative fallback: only accept exactly one plausible amount before the balance section.
        if (!depositKeywords.any { text.contains(it, ignoreCase = true) }) return null
        val balanceIndex = balanceWords.map { text.indexOf(it, ignoreCase = true) }.filter { it >= 0 }.minOrNull() ?: text.length
        val beforeBalance = text.substring(0, balanceIndex)
        val candidates = Regex("(?<!\\d)([0-9][0-9,]{3,15})(?!\\d)")
            .findAll(beforeBalance)
            .mapNotNull { match -> parse(match.groupValues[1], null, match.value) }
            .distinct()
            .toList()
        return candidates.singleOrNull()
    }

    private fun extractTrackingCode(text: String): String? {
        val pattern = Pattern.compile(
            "(?:پیگیری|پیگیر|کد پیگیری|شماره پیگیری|شماره ارجاع|کد ارجاع|ارجاع|مرجع|رهگیری|Ref|Trn)[:\\s]+([0-9A-Za-z]{4,20})"
        )
        val matcher = pattern.matcher(text)
        if (matcher.find()) {
            return matcher.group(1)?.trim()
        }
        return null
    }

    private fun extractCardLast4(text: String): String? {
        // Look for 4 digits associated with card/account, e.g. "کارت ...1234", "*1234", "1234*"
        val cardPattern1 = Pattern.compile("(?:کارت|حساب)[:\\s]*(?:[0-9*\\s]+)?([0-9]{4})\\b")
        val matcher1 = cardPattern1.matcher(text)
        if (matcher1.find()) {
            val digits = matcher1.group(1)
            if (digits != null && digits.length == 4) return digits
        }

        val cardPattern2 = Pattern.compile("\\*{2,}([0-9]{4})\\b")
        val matcher2 = cardPattern2.matcher(text)
        if (matcher2.find()) {
            return matcher2.group(1)
        }

        return null
    }

    private fun detectBank(sender: String?, text: String): String {
        for ((keyword, bankName) in bankDictionary) {
            if (text.contains(keyword) || (sender != null && sender.contains(keyword))) {
                return bankName
            }
        }
        return sender ?: "بانک"
    }
}
