package com.example.sms.parser

import com.example.sms.model.ParsedPayment

object ParserRegistry {

    private val parsers = mutableListOf<PaymentSmsParser>(
        GenericIranianPaymentParser()
    )

    @Synchronized
    fun registerParser(parser: PaymentSmsParser) {
        parsers.add(0, parser) // Custom parsers take priority
    }

    fun parse(sender: String?, body: String): ParsedPayment? {
        for (parser in parsers) {
            if (parser.canParse(sender, body)) {
                val candidate = parser.parse(sender, body)
                if (candidate != null && candidate.isValidCandidate()) {
                    return candidate
                }
            }
        }
        return null
    }
}
