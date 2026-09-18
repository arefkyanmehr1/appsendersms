package com.example.sms.parser

import com.example.sms.model.ParsedPayment

interface PaymentSmsParser {

    /**
     * Determines whether this parser can process the given SMS.
     */
    fun canParse(
        sender: String?,
        body: String
    ): Boolean

    /**
     * Parses the SMS locally and returns a structured [ParsedPayment] candidate,
     * or null if parsing fails or candidate is invalid.
     */
    fun parse(
        sender: String?,
        body: String
    ): ParsedPayment?
}
