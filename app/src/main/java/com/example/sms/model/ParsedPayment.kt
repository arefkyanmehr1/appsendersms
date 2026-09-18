package com.example.sms.model

enum class ConfidenceLevel {
    HIGH,
    MEDIUM,
    LOW
}

data class ParsedPayment(
    val amount: Long,
    val bankName: String?,
    val trackingCode: String?,
    val cardLast4: String?,
    val smsHash: String,
    val receivedAt: Long = System.currentTimeMillis(),
    val confidence: ConfidenceLevel = ConfidenceLevel.HIGH,
    val sender: String? = null
) {
    /**
     * Validates candidate per spec:
     * - amount > 0
     * - smsHash must be 64 hex characters
     * - cardLast4 must be 4 digits if present
     */
    fun isValidCandidate(): Boolean {
        if (amount <= 0) return false
        if (smsHash.length != 64) return false
        if (cardLast4 != null && !cardLast4.matches(Regex("^\\d{4}$"))) return false
        return true
    }
}
