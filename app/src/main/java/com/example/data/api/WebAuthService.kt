package com.example.data.api

import com.example.core.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

sealed class WebAuthResult {
    data class Success(val apiKey: String, val merchantTitle: String? = null) : WebAuthResult()
    data class Error(val message: String) : WebAuthResult()
}

class WebAuthService {

    private val cookieStore = ConcurrentHashMap<String, MutableList<Cookie>>()

    private val cookieJar = object : CookieJar {
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            val existing = cookieStore.getOrPut(url.host) { mutableListOf() }
            synchronized(existing) {
                // Remove existing cookies with same name
                val names = cookies.map { it.name }.toSet()
                existing.removeAll { it.name in names }
                existing.addAll(cookies)
            }
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            val list = cookieStore[url.host] ?: return emptyList()
            synchronized(list) {
                return list.toList()
            }
        }
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .cookieJar(cookieJar)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    suspend fun loginWithCredentials(username: String, password: String): WebAuthResult =
        withContext(Dispatchers.IO) {
            val trimmedUsername = username.trim()
            if (trimmedUsername.isBlank()) {
                return@withContext WebAuthResult.Error("لطفاً نام کاربری را وارد کنید.")
            }
            if (password.isBlank()) {
                return@withContext WebAuthResult.Error("لطفاً رمز عبور را وارد کنید.")
            }

            cookieStore.clear()

            try {
                // Step 1: GET login page to retrieve CSRF token and establish session
                val loginUrl = "https://pay.arefkyanmehr.ir/login.php"
                val getRequest = Request.Builder()
                    .url(loginUrl)
                    .header("User-Agent", "Mozilla/5.0 (Android; Mobile; PayLinkApp)")
                    .build()

                val getResponse = httpClient.newCall(getRequest).execute()
                val getHtml = getResponse.body?.string() ?: ""

                val csrfRegex = Regex("""name=["']csrf_token["']\s+value=["']([^"']+)["']""")
                val csrfToken = csrfRegex.find(getHtml)?.groupValues?.get(1) ?: ""

                // Step 2: POST credentials
                val formBodyBuilder = FormBody.Builder()
                    .add("username", trimmedUsername)
                    .add("password", password)

                if (csrfToken.isNotBlank()) {
                    formBodyBuilder.add("csrf_token", csrfToken)
                }

                val postRequest = Request.Builder()
                    .url(loginUrl)
                    .header("User-Agent", "Mozilla/5.0 (Android; Mobile; PayLinkApp)")
                    .header("Referer", loginUrl)
                    .post(formBodyBuilder.build())
                    .build()

                val postResponse = httpClient.newCall(postRequest).execute()
                val postHtml = postResponse.body?.string() ?: ""

                // Check for login errors in response HTML
                val errorRegex = Regex("""class=["']error["'][^>]*>([^<]+)<""")
                val errorMatch = errorRegex.find(postHtml)
                if (errorMatch != null) {
                    val errorMsg = errorMatch.groupValues[1].trim()
                    return@withContext WebAuthResult.Error(
                        if (errorMsg.isNotBlank()) errorMsg else "نام کاربری یا رمز عبور اشتباه است."
                    )
                }

                // If redirected or still on login without explicit error, check if logged in
                // Step 3: Fetch panel.php and api.php to extract merchant API key
                var extractedApiKey = extractApiKeyFromHtml(postHtml)

                if (extractedApiKey == null) {
                    val panelRequest = Request.Builder()
                        .url("https://pay.arefkyanmehr.ir/panel.php")
                        .header("User-Agent", "Mozilla/5.0 (Android; Mobile; PayLinkApp)")
                        .build()
                    val panelResponse = httpClient.newCall(panelRequest).execute()
                    val panelHtml = panelResponse.body?.string() ?: ""
                    extractedApiKey = extractApiKeyFromHtml(panelHtml)
                }

                if (extractedApiKey == null) {
                    val apiPageRequest = Request.Builder()
                        .url("https://pay.arefkyanmehr.ir/api.php")
                        .header("User-Agent", "Mozilla/5.0 (Android; Mobile; PayLinkApp)")
                        .build()
                    val apiPageResponse = httpClient.newCall(apiPageRequest).execute()
                    val apiPageHtml = apiPageResponse.body?.string() ?: ""
                    extractedApiKey = extractApiKeyFromHtml(apiPageHtml)
                }

                if (!extractedApiKey.isNullOrBlank()) {
                    AppLogger.i("Successfully extracted API key from web panel")
                    return@withContext WebAuthResult.Success(apiKey = extractedApiKey)
                }

                // Fallback: If login was successful (session cookie set and not redirected to login.php)
                // but API key format differs, check if username can be used or report helpful message
                val cookies = cookieStore["pay.arefkyanmehr.ir"] ?: emptyList()
                val hasSession = cookies.any { it.name == "paylink_session" }

                if (hasSession && !postHtml.contains("ورود به حساب")) {
                    // Logged in successfully!
                    AppLogger.i("Web login succeeded, but API key needs to be confirmed")
                    return@withContext WebAuthResult.Error(
                        "ورود به پنل با موفقیت انجام شد، اما کلید API یافت نشد. لطفاً کلید API را از منوی API پنل کپی و در تب کلید API وارد کنید."
                    )
                }

                return@withContext WebAuthResult.Error("نام کاربری یا رمز عبور اشتباه است.")
            } catch (e: Exception) {
                AppLogger.e("Web auth error", e)
                return@withContext WebAuthResult.Error("خطا در برقراری ارتباط با سرور: ${e.message ?: "خطای ناشناخته"}")
            } finally {
                cookieStore.clear()
            }
        }

    private fun extractApiKeyFromHtml(html: String): String? {
        if (html.isBlank()) return null

        // Pattern 1: pl_usr_...
        val plUsrRegex = Regex("""\b(pl_usr_[a-zA-Z0-9_]{10,64})\b""")
        plUsrRegex.find(html)?.groupValues?.get(1)?.let { return it }

        // Pattern 2: pl_...
        val plRegex = Regex("""\b(pl_[a-zA-Z0-9_]{16,64})\b""")
        plRegex.find(html)?.groupValues?.get(1)?.let { return it }

        // Pattern 3: input with api_key / key
        val inputRegex = Regex("""<input[^>]+(?:name|id)=["'][^"']*(?:api_key|token|apiKey|secret)[^"']*["'][^>]+value=["']([^"']{16,})["']""", RegexOption.IGNORE_CASE)
        inputRegex.find(html)?.groupValues?.get(1)?.let { return it }

        // Pattern 4: label/text followed by key
        val labelRegex = Regex("""(?:کلید|API[-_ ]?Key)[^<>\n\r]{0,35}[:=][ ]*["']?([a-zA-Z0-9_-]{20,64})["']?""", RegexOption.IGNORE_CASE)
        labelRegex.find(html)?.groupValues?.get(1)?.let { return it }

        return null
    }
}
