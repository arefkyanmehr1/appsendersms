package com.example.sms.matcher

import com.example.data.model.PendingInvoice
import com.example.sms.model.ParsedPayment

sealed class MatchResult {
    data class Matched(val invoice: PendingInvoice, val payment: ParsedPayment) : MatchResult()
    data class ExpiredInvoice(val invoice: PendingInvoice, val payment: ParsedPayment) : MatchResult()
    data class NoMatch(val reason: String, val payment: ParsedPayment) : MatchResult()
}

object PaymentMatcher {

    /**
     * Matches a parsed payment candidate against active pending invoices.
     * Implements intelligent multi-tier matching:
     * 1. Exact amount match (Rials)
     * 2. Toman/Rial conversion fallback (factor of 10)
     * 3. Tracking code & Card last-4 verification
     * 4. FIFO (First-In First-Out) chronological priority for identical amounts
     * 5. 300-second (5-minute) grace window for network/SMS carrier delays
     */
    fun match(payment: ParsedPayment, pendingInvoices: List<PendingInvoice>): MatchResult {
        if (pendingInvoices.isEmpty()) {
            return MatchResult.NoMatch("هیچ فاکتور فعالی در انتظار پرداخت نیست.", payment)
        }

        // Tier 1: Try exact amount match
        var candidateInvoices = pendingInvoices.filter { invoice ->
            invoice.effectiveAmount > 0L &&
                invoice.effectiveAmount == payment.amount
        }

        // Tier 2: If no exact match, check Toman/Rial factor of 10
        if (candidateInvoices.isEmpty() && payment.amount > 0) {
            candidateInvoices = pendingInvoices.filter { invoice ->
                val eff = invoice.effectiveAmount
                if (eff <= 0L || payment.amount <= 0L) {
                    false
                } else {
                    (eff <= Long.MAX_VALUE / 10 && eff * 10 == payment.amount) ||
                        (payment.amount <= Long.MAX_VALUE / 10 && payment.amount * 10 == eff)
                }
            }
        }

        if (candidateInvoices.isEmpty()) {
            return MatchResult.NoMatch(
                "مبلغ واریز (${payment.amount} ریال) با هیچ فاکتور در انتظاری مطابقت ندارد.",
                payment
            )
        }

        // Check expiration with generous 300-second (5-minute) grace window for carrier SMS latency
        val validActiveInvoices = candidateInvoices.filter { invoice ->
            invoice.status.lowercase() == "pending" && invoice.remainingSeconds > -300
        }

        if (validActiveInvoices.isEmpty()) {
            // All candidates are strictly expired (passed 5-minute grace window)
            return MatchResult.ExpiredInvoice(candidateInvoices.first(), payment)
        }

        // Exactly one active matching invoice
        if (validActiveInvoices.size == 1) {
            return MatchResult.Matched(validActiveInvoices.first(), payment)
        }

        // Multiple invoices have identical amount:
        // Priority A: Tracking code match if available
        if (!payment.trackingCode.isNullOrBlank()) {
            val trackingMatched = validActiveInvoices.firstOrNull { inv ->
                val desc = inv.description ?: ""
                val extra = inv.extraData ?: ""
                desc.contains(payment.trackingCode) || extra.contains(payment.trackingCode) || inv.orderId.contains(payment.trackingCode)
            }
            if (trackingMatched != null) {
                return MatchResult.Matched(trackingMatched, payment)
            }
        }

        // Priority B: Last 4 digits of card if customer specified in description or extra data
        if (!payment.cardLast4.isNullOrBlank()) {
            val cardMatched = validActiveInvoices.firstOrNull { inv ->
                val desc = inv.description ?: ""
                val extra = inv.extraData ?: ""
                desc.contains(payment.cardLast4) || extra.contains(payment.cardLast4)
            }
            if (cardMatched != null) {
                return MatchResult.Matched(cardMatched, payment)
            }
        }

        // Priority C: Strict FIFO (First-In First-Out) Chronological Priority:
        // Oldest invoice (created first) gets matched and satisfied first
        val sortedByOldest = validActiveInvoices.sortedWith(
            compareBy<PendingInvoice> { it.createdAt ?: "" }
                .thenBy { it.id }
                .thenBy { it.remainingSeconds }
        )

        val oldestInvoice = sortedByOldest.first()
        return MatchResult.Matched(oldestInvoice, payment)
    }
}
