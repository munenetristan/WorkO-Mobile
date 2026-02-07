package com.towmech.app.api

import android.content.Context
import com.google.gson.GsonBuilder
import com.towmech.app.BuildConfig
import com.towmech.app.data.TokenManager
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.Locale
import java.util.concurrent.TimeUnit

object ApiClient {

    /**
     * ✅ Base URL comes from BuildConfig (set in app/build.gradle.kts)
     * ⚠️ Retrofit requires a trailing "/" — we enforce it below anyway.
     */
    private val BASE_URL: String = BuildConfig.BASE_URL

    // ✅ App context for interceptors (set from TowMechApp)
    @Volatile
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    // ✅ Register custom Gson adapters here
    private val gson = GsonBuilder()
        .registerTypeAdapter(FlexibleUser::class.java, FlexibleUserAdapter())
        .create()

    private fun headersInterceptor(): Interceptor {
        return Interceptor { chain ->
            val ctx = appContext
            val original = chain.request()
            val builder: Request.Builder = original.newBuilder()

            // ✅ Country + Language headers for multi-country routing
            val countryCode = ctx?.let { TokenManager.getCountryCode(it) } ?: "ZA"
            val language = ctx?.let { TokenManager.getLanguageCode(it) }
                ?: Locale.getDefault().language

            // ✅ Send both casings to avoid edge-case middleware/header handling
            builder.header("X-COUNTRY-CODE", countryCode)
            builder.header("x-country-code", countryCode)
            builder.header("Accept-Language", language)

            // ✅ Attach token if present
            val token = ctx?.let { TokenManager.getToken(it) }
            if (!token.isNullOrBlank()) {
                builder.header("Authorization", "Bearer $token")
            }

            chain.proceed(builder.build())
        }
    }

    private val client: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            // ✅ Avoid leaking secrets in logs for release builds
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
            else HttpLoggingInterceptor.Level.BASIC
        }

        OkHttpClient.Builder()
            .addInterceptor(headersInterceptor())
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    private val retrofit: Retrofit by lazy {
        // ✅ Ensure trailing slash (Retrofit requires this)
        val safeBase = if (BASE_URL.endsWith("/")) BASE_URL else "$BASE_URL/"

        Retrofit.Builder()
            .baseUrl(safeBase)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .client(client)
            .build()
    }

    val apiService: ApiService by lazy {
        retrofit.create(ApiService::class.java)
    }
}