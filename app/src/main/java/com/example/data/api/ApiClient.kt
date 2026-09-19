package com.example.data.api

import com.example.BuildConfig
import com.example.core.security.SecureApiKeyStorage
import com.squareup.moshi.FromJson
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.ToJson
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

object FlexibleIntAdapter {
    @FromJson
    fun fromJson(reader: JsonReader): Int {
        val token = reader.peek()
        return when (token) {
            JsonReader.Token.NUMBER -> reader.nextInt()
            JsonReader.Token.STRING -> {
                val str = reader.nextString().trim()
                str.toIntOrNull() ?: if (str.equals("true", true)) 1 else 0
            }
            JsonReader.Token.BOOLEAN -> if (reader.nextBoolean()) 1 else 0
            JsonReader.Token.NULL -> {
                reader.nextNull<Any>()
                1
            }
            else -> {
                reader.skipValue()
                1
            }
        }
    }

    @ToJson
    fun toJson(writer: JsonWriter, value: Int?) {
        if (value == null) {
            writer.nullValue()
        } else {
            writer.value(value)
        }
    }
}

object FlexibleLongAdapter {
    @FromJson
    fun fromJson(reader: JsonReader): Long {
        val token = reader.peek()
        return when (token) {
            JsonReader.Token.NUMBER -> reader.nextLong()
            JsonReader.Token.STRING -> {
                val str = reader.nextString().trim()
                str.toLongOrNull() ?: if (str.equals("true", true)) 1L else 0L
            }
            JsonReader.Token.BOOLEAN -> if (reader.nextBoolean()) 1L else 0L
            JsonReader.Token.NULL -> {
                reader.nextNull<Any>()
                0L
            }
            else -> {
                reader.skipValue()
                0L
            }
        }
    }

    @ToJson
    fun toJson(writer: JsonWriter, value: Long?) {
        if (value == null) {
            writer.nullValue()
        } else {
            writer.value(value)
        }
    }
}

/**
 * Flexible Adapter to safely parse string fields even if server sends JSON Object, Array, Number or Boolean.
 * Especially prevents crashes on extra_data which PHP sends as array/object.
 */
object SafeStringAdapter {
    @FromJson
    fun fromJson(reader: JsonReader): String? {
        val token = reader.peek()
        return when (token) {
            JsonReader.Token.STRING -> reader.nextString()
            JsonReader.Token.NUMBER -> reader.nextString()
            JsonReader.Token.BOOLEAN -> reader.nextBoolean().toString()
            JsonReader.Token.NULL -> {
                reader.nextNull<Any>()
                null
            }
            JsonReader.Token.BEGIN_OBJECT, JsonReader.Token.BEGIN_ARRAY -> {
                // Read and serialize the raw object/array back into string
                val rawValue = reader.readJsonValue()
                rawValue?.toString()
            }
            else -> {
                reader.skipValue()
                null
            }
        }
    }

    @ToJson
    fun toJson(writer: JsonWriter, value: String?) {
        if (value == null) {
            writer.nullValue()
        } else {
            writer.value(value)
        }
    }
}

object ApiClient {

    // Default base URL pointing to the user's specific domain
    val BASE_URL: String = BuildConfig.API_BASE_URL.let {
        if (it.endsWith("/")) it else "$it/"
    }

    private var retrofitInstance: Retrofit? = null
    private var apiInstance: PayLinkApi? = null

    fun getApi(secureStorage: SecureApiKeyStorage): PayLinkApi {
        if (apiInstance == null) {
            val logging = HttpLoggingInterceptor().apply {
                level = if (BuildConfig.DEBUG) {
                    HttpLoggingInterceptor.Level.BASIC
                } else {
                    HttpLoggingInterceptor.Level.NONE
                }
            }

            val connectionPool = okhttp3.ConnectionPool(10, 5, TimeUnit.MINUTES)

            val okHttpClient = OkHttpClient.Builder()
                .connectionPool(connectionPool)
                .connectTimeout(8, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .retryOnConnectionFailure(false)
                .addInterceptor(ApiKeyInterceptor(secureStorage))
                .addInterceptor(logging)
                .build()

            val moshi = Moshi.Builder()
                .add(FlexibleIntAdapter)
                .add(FlexibleLongAdapter)
                .add(SafeStringAdapter)
                .addLast(KotlinJsonAdapterFactory())
                .build()

            val retrofit = Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(okHttpClient)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()

            retrofitInstance = retrofit
            apiInstance = retrofit.create(PayLinkApi::class.java)
        }
        return apiInstance!!
    }
}
