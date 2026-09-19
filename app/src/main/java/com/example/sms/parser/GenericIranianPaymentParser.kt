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
        val amountPattern = Pattern.compile("(?:مبلغ|واریز(?:\s*شد)?|واریزشده|دریافت(?:\s*وجه)?)[:\\s]*([0-9][0-9,\\.]{2,18})\\s*(ریال|تومان)?", Pattern.CASE_INSENSITIVE)

        fun parseCandidate(raw: String, unit: String?, sourceLine: String): Long? {
            val digits = raw.replace(",", "").replace(".", "")
            val value = digits.toLongOrNull() ?: return null
            if (value <= 0L) return null
            if (sourceLine.any { it.isDigit() } && Regex("\\b(?:13|14)\\d{2}[/-]\\d{1,2}[/-]\\d{1,2}\\b").containsMatchIn(sourceLine)) return null
            return try {
                if (unit == "تومان" || sourceLine.contains("تومان")) Math.multiplyExact(value, 10L) else value
            } catch (_: ArithmeticException) { null }
        }

        // Prefer an explicitly labelled amount. This prevents balance/card/reference numbers
        // from becoming the payment amount.
        for (line in lines) {
            if (balanceWords.any { line.contains(it, ignoreCase = true) }) continue
            val matcher = amountPattern.matcher(line)
            if (matcher.find()) {
                val amount = parseCandidate(matcher.group(1) ?: continue, matcher.group(2), line)
                if (amount != null) return amount
            }
        }

        // Conservative fallback: only accept a single plausible numeric candidate before the
        // balance section. Never guess from an arbitrary first number in the SMS.
        if (!depositKeywords.any { text.contains(it, ignoreCase = true) }) return null
        val balanceIndex = listOf("موجودی", "مانده", "باقیمانده", "balance")
            .map { text.indexOf(it, ignoreCase = true) }
            .filter { it >= 0 }
            .minOrNull() ?: text.length
        val preBalance = text.substring(0, balanceIndex)
        val candidates = Regex("(?<!\\d)([0-9][0-9,]{3,15})(?!\\d)")
            .findAll(preBalance)
            .mapNotNull { m ->
                val raw = m.groupValues[1]
                if (raw.replace(",", "").length in 4..15) parseCandidate(raw, null, m.value) else null
            }
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
