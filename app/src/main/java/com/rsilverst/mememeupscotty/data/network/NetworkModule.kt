package com.rsilverst.mememeupscotty.data.network

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

object NetworkModule {
    private const val BASE_URL = "https://api.replicate.com/"

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    // Replicate uses `Token` auth scheme, not `Bearer`.
    private val authInterceptor = okhttp3.Interceptor { chain ->
        val request = chain.request().newBuilder()
            .addHeader("Authorization", "Token ${com.rsilverst.mememeupscotty.BuildConfig.REPLICATE_API_TOKEN}")
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
