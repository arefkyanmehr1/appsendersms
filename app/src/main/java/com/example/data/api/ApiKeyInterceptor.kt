package com.example.data.api

import com.example.core.security.SecureApiKeyStorage
import okhttp3.Interceptor
import okhttp3.Response

class ApiKeyInterceptor(
    private val secureStorage: SecureApiKeyStorage
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val apiKey = secureStorage.getApiKey()

        val requestBuilder = originalRequest.newBuilder()
            .header("Accept", "application/json")

        if (!apiKey.isNullOrBlank()) {
            requestBuilder.header("X-API-Key", apiKey)
        }

        return chain.proceed(requestBuilder.build())
    }
}
