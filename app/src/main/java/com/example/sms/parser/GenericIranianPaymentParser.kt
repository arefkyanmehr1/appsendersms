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
        // Split by lines or punctuation to avoid picking up the balance ("موجودی")
        val lines = text.split("\n", "،", ";", " - ")

        // First look for lines containing explicit deposit / amount keywords
        val depositLines = lines.filter { line ->
            !line.contains("موجودی") && !line.contains("مانده") && !line.contains("باقیمانده") &&
                    (depositKeywords.any { line.contains(it) } || line.contains("مبلغ"))
        }

        val candidatesToSearch = if (depositLines.isNotEmpty()) depositLines else lines

        for (line in candidatesToSearch) {
            // Ignore balance line
            if (line.contains("موجودی") || line.contains("مانده") || line.contains("باقیمانده")) {
                continue
            }

            // Patterns like: "مبلغ: 101,000 ریال", "واریز: 101000", "واریز 10,100 تومان"
            val pattern = Pattern.compile(
                "(?:مبلغ|واریز|واریزشده|به مبلغ)?[:\\s]*([0-9][0-9,\\.]{2,15})\\s*(ریال|تومان)?"
            )
            val matcher = pattern.matcher(line)
            if (matcher.find()) {
                val rawNumberStr = matcher.group(1) ?: continue
                val unit = matcher.group(2)
                val cleanDigits = rawNumberStr.replace(",", "").replace(".", "").trim()
                val parsedNumber = cleanDigits.toLongOrNull() ?: continue

                if (parsedNumber <= 0) continue

                // Currency normalization:
                // If تومان explicitly specified, convert 1 Toman = 10 Rials (Backend operates in Rials)
                return if (unit == "تومان" || line.contains("تومان")) {
                    parsedNumber * 10L
                } else {
                    parsedNumber
                }
            }
        }

        // Fallback search across whole text without balance section
        val balanceIndex = text.indexOf("موجودی").takeIf { it >= 0 }
            ?: text.indexOf("مانده").takeIf { it >= 0 }
            ?: text.length

        val subText = text.substring(0, balanceIndex)
        val generalPattern = Pattern.compile("([0-9][0-9,]{3,15})\\s*(ریال|تومان)?")
        val generalMatcher = generalPattern.matcher(subText)
        if (generalMatcher.find()) {
            val numStr = generalMatcher.group(1)?.replace(",", "")?.trim() ?: return null
            val unit = generalMatcher.group(2)
            val parsed = numStr.toLongOrNull() ?: return null
            return if (unit == "تومان") parsed * 10L else parsed
        }

        return null
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
