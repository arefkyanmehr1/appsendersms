package com.example.core.security

import java.security.MessageDigest

object HashUtils {
    /**
     * Computes the SHA-256 hash of a string and returns a 64-character lowercase hex string.
     */
    fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(input.trim().toByteArray(Charsets.UTF_8))
        val hexString = StringBuilder(64)
        for (b in hashBytes) {
            val hex = Integer.toHexString(0xff and b.toInt())
            if (hex.length == 1) {
                hexString.append('0')
            }
            hexString.append(hex)
        }
        return hexString.toString()
    }
}
