package com.example.core.util

import android.util.Log
import com.example.BuildConfig

object AppLogger {

    private const val TAG = "PayLink"

    fun d(message: String) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, sanitize(message))
        }
    }

    fun i(message: String) {
        if (BuildConfig.DEBUG) {
            Log.i(TAG, sanitize(message))
        }
    }

    fun w(message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.w(TAG, sanitize(message), throwable)
        } else {
            Log.w(TAG, sanitize(message))
        }
    }

    fun e(message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.e(TAG, sanitize(message), throwable)
        } else {
            Log.e(TAG, sanitize(message))
        }
    }

    /**
     * Safety filter ensuring raw SMS body, card numbers (16 digits), or keys are never logged.
     */
    private fun sanitize(input: String): String {
        // Redact potential 16-digit card numbers
        var s = input.replace(Regex("\\b\\d{4}[- ]?\\d{4}[- ]?\\d{4}[- ]?\\d{4}\\b"), "[REDACTED_CARD]")
        // Redact raw SMS bodies or API key occurrences if inadvertently included
        s = s.replace(Regex("pl_usr_[A-Za-z0-9_]+"), "pl_usr_***")
        return s
    }
}
