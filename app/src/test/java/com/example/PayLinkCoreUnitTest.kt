package com.example

import com.example.core.network.ErrorType
import com.example.core.network.toPersianMessage
import com.example.core.security.HashUtils
import com.example.core.util.CurrencyUtils
import com.example.data.model.PendingInvoice
import com.example.sms.matcher.MatchResult
import com.example.sms.matcher.PaymentMatcher
import com.example.sms.model.ParsedPayment
import com.example.sms.parser.GenericIranianPaymentParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PayLinkCoreUnitTest {

    @Test
    fun testPersianArabicDigitNormalization() {
        val persian = "واریز مبلغ ۱۲۳۴۵۶ ریال"
        val normalized = CurrencyUtils.normalizePersianArabicDigits(persian)
        assertEquals("واریز مبلغ 123456 ریال", normalized)
    }

    @Test
    fun testCurrencyFormatting() {
        assertEquals("1,010,000 ریال", CurrencyUtils.formatRials(1010000L))
        assertEquals("101,000 تومان", CurrencyUtils.formatTomans(1010000L))
    }

    @Test
    fun testSmsSha256Hash() {
        val sms = "واریز: 101000 ریال - بانک سامان"
        val hash = HashUtils.sha256Hex(sms)
        assertEquals(64, hash.length)
        assertTrue(hash.matches(Regex("^[0-9a-f]{64}$")))
    }

    @Test
    fun testSmsParserWithRials() {
        val parser = GenericIranianPaymentParser()
        val body = """
            بانک سامان
            واریز: 101,000 ریال
            به کارت: *1234
            شماره پیگیری: 987654321
            موجودی: 5,000,000 ریال
        """.trimIndent()

        assertTrue(parser.canParse(sender = "9820000", body = body))
        val candidate = parser.parse(sender = "9820000", body = body)

        assertNotNull(candidate)
        assertEquals(101000L, candidate?.amount)
        assertEquals("1234", candidate?.cardLast4)
        assertEquals("987654321", candidate?.trackingCode)
        assertEquals("بانک سامان", candidate?.bankName)
    }

    @Test
    fun testSmsParserWithTomansConversion() {
        val parser = GenericIranianPaymentParser()
        val body = """
            واریز 10,100 تومان
            بانک ملت
            کد پیگیری: 456789
            مانده حساب: 100,000 تومان
        """.trimIndent()

        val candidate = parser.parse(sender = "BankMellat", body = body)
        assertNotNull(candidate)
        // 10,100 Toman must convert to 101,000 Rial per spec
        assertEquals(101000L, candidate?.amount)
        assertEquals("456789", candidate?.trackingCode)
    }

    @Test
    fun testSmsParserRejectsWithdrawal() {
        val parser = GenericIranianPaymentParser()
        val body = """
            برداشت: 50,000 ریال
            خرید از فروشگاه
            مانده: 1,000,000 ریال
        """.trimIndent()

        assertNull(parser.parse(sender = "Bank", body = body))
    }

    @Test
    fun testPaymentMatcherSingleExactMatch() {
        val payment = ParsedPayment(
            amount = 101000L,
            bankName = "بانک سامان",
            trackingCode = "123456",
            cardLast4 = "1234",
            smsHash = "a".repeat(64),
            receivedAt = System.currentTimeMillis()
        )

        val invoices = listOf(
            PendingInvoice(
                id = 1,
                orderId = "ORDER-123",
                baseAmount = 100000L,
                payableAmount = 101000L,
                expectedAmount = 101000L,
                status = "pending",
                createdAt = "2026-09-16 10:00:00",
                expiresAt = "2026-09-16 10:15:00",
                remainingSeconds = 600
            ),
            PendingInvoice(
                id = 2,
                orderId = "ORDER-124",
                baseAmount = 200000L,
                payableAmount = 202000L,
                expectedAmount = 202000L,
                status = "pending",
                createdAt = "2026-09-16 10:00:00",
                expiresAt = "2026-09-16 10:15:00",
                remainingSeconds = 600
            )
        )

        val result = PaymentMatcher.match(payment, invoices)
        assertTrue(result is MatchResult.Matched)
        assertEquals("ORDER-123", (result as MatchResult.Matched).invoice.orderId)
    }

    @Test
    fun testPaymentMatcherAmbiguousMultipleMatches() {
        val payment = ParsedPayment(
            amount = 101000L,
            bankName = "بانک سامان",
            trackingCode = "123456",
            cardLast4 = "1234",
            smsHash = "b".repeat(64),
            receivedAt = System.currentTimeMillis()
        )

        val invoices = listOf(
            PendingInvoice(
                id = 1,
                orderId = "ORDER-101",
                baseAmount = 100000L,
                payableAmount = 101000L,
                expectedAmount = 101000L,
                status = "pending",
                createdAt = null,
                expiresAt = null,
                remainingSeconds = 500
            ),
            PendingInvoice(
                id = 2,
                orderId = "ORDER-102",
                baseAmount = 100000L,
                payableAmount = 101000L,
                expectedAmount = 101000L,
                status = "pending",
                createdAt = null,
                expiresAt = null,
                remainingSeconds = 450
            )
        )

        val result = PaymentMatcher.match(payment, invoices)
        assertTrue("When multiple invoices have identical expected amount, FIFO must match oldest", result is MatchResult.Matched)
        assertEquals("ORDER-101", (result as MatchResult.Matched).invoice.orderId)
    }

    @Test
    fun testPaymentMatcherExpiredInvoice() {
        val payment = ParsedPayment(
            amount = 101000L,
            bankName = "بانک سامان",
            trackingCode = "123456",
            cardLast4 = "1234",
            smsHash = "c".repeat(64),
            receivedAt = System.currentTimeMillis()
        )

        val invoices = listOf(
            PendingInvoice(
                id = 1,
                orderId = "ORDER-EXPIRED",
                baseAmount = 100000L,
                payableAmount = 101000L,
                expectedAmount = 101000L,
                status = "pending",
                createdAt = null,
                expiresAt = null,
                remainingSeconds = 0 // Expired
            )
        )

        val result = PaymentMatcher.match(payment, invoices)
        assertTrue(result is MatchResult.ExpiredInvoice)
    }

    @Test
    fun testErrorMappingToPersian() {
        assertEquals("کلید API فعلی معتبر نیست. لطفاً API Key جدید را وارد کنید.", ErrorType.UNAUTHORIZED.toPersianMessage())
        assertEquals("حساب کاربری غیرفعال است.", ErrorType.ACCOUNT_DISABLED.toPersianMessage())
        assertEquals("اتصال به اینترنت برقرار نیست.", ErrorType.NO_INTERNET.toPersianMessage())
    }
}
