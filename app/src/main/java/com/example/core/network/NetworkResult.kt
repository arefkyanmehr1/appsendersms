package com.example.core.network

sealed class NetworkResult<out T> {
    data class Success<out T>(val data: T) : NetworkResult<T>()
    data class Error(
        val code: Int,
        val message: String,
        val errorType: ErrorType = ErrorType.UNKNOWN
    ) : NetworkResult<Nothing>()
    data class Exception(val throwable: Throwable) : NetworkResult<Nothing>()
}

enum class ErrorType {
    UNAUTHORIZED,       // 401: Invalid API Key
    ACCOUNT_DISABLED,   // 403: Account Disabled
    NOT_FOUND,          // 404: Not Found
    CONFLICT,           // 409: Duplicate / Already Paid
    VALIDATION_ERROR,   // 422: Validation Error
    SERVER_ERROR,       // 500: Server Error
    NO_INTERNET,        // Offline
    TIMEOUT,            // Request Timeout
    UNKNOWN             // Other
}

fun ErrorType.toPersianMessage(fallbackMessage: String? = null): String {
    val cleanFallback = fallbackMessage?.let { raw ->
        if (raw.trim().startsWith("{") && raw.contains("\"message\"")) {
            try {
                val json = org.json.JSONObject(raw)
                val msg = json.optString("message", "")
                if (msg.isNotBlank()) msg else raw
            } catch (_: Exception) {
                raw
            }
        } else {
            raw
        }
    }

    return when (this) {
        ErrorType.UNAUTHORIZED -> "کلید API فعلی معتبر نیست. لطفاً API Key جدید را وارد کنید."
        ErrorType.ACCOUNT_DISABLED -> "حساب کاربری غیرفعال است."
        ErrorType.NO_INTERNET -> "اتصال به اینترنت برقرار نیست."
        ErrorType.TIMEOUT -> "مهلت برقراری ارتباط با سرور به پایان رسید."
        ErrorType.CONFLICT -> "این پرداخت قبلاً ثبت یا فاکتور تسویه شده است."
        ErrorType.SERVER_ERROR -> "خطای داخلی سرور رخ داده است."
        ErrorType.VALIDATION_ERROR -> cleanFallback ?: "اطلاعات ارسالی معتبر نمی‌باشد."
        ErrorType.NOT_FOUND -> cleanFallback ?: "موردی یافت نشد."
        ErrorType.UNKNOWN -> cleanFallback ?: "خطای ناشناخته در ارتباط با درگاه."
    }
}
