package com.rsilverst.mememeupscotty.data.network

import com.rsilverst.mememeupscotty.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

object NetworkModule {
    // Sourced from BuildConfig so a future proxy (see CODE_REVIEW.md A1) can
    // be swapped in via local.properties without touching code. Defaults to
    // api.replicate.com.
    private val BASE_URL = BuildConfig.REPLICATE_BASE_URL

    // Body-level logging includes request/response bodies and is too noisy
    // (and a small perf hit) for release. Authorization headers are also
    // safer to keep out of logcat.
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.BODY
        } else {
            HttpLoggingInterceptor.Level.NONE
        }
    }

    // Replicate uses `Token` auth scheme, not `Bearer`. When BASE_URL is
    // pointed at a proxy, the proxy is expected to either ignore this header
    // and inject its own auth, or rewrite it on the way to Replicate.
    private val authInterceptor = okhttp3.Interceptor { chain ->
        val request = chain.request().newBuilder()
            .addHeader("Authorization", "Token ${BuildConfig.REPLICATE_API_TOKEN}")
            .build()
        chain.proceed(request)
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .addInterceptor(loggingInterceptor)
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create())
        .build()

    val replicateApi: ReplicateApi = retrofit.create(ReplicateApi::class.java)

    // Separate client without the Replicate auth header, used to download the final
    // image from replicate.delivery (a presigned CDN URL that rejects the Token header).
    val imageDownloadClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
}
