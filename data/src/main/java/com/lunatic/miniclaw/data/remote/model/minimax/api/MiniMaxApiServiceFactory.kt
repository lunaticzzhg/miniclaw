package com.lunatic.miniclaw.data.remote.model.minimax.api

import java.util.concurrent.ConcurrentHashMap
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit

class MiniMaxApiServiceFactory {
    private val serviceCache = ConcurrentHashMap<String, MiniMaxApiService>()

    fun get(baseUrl: String): MiniMaxApiService {
        val normalizedBaseUrl = normalizeBaseUrl(baseUrl)
        return serviceCache.getOrPut(normalizedBaseUrl) {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }
            val client = OkHttpClient.Builder()
                .addInterceptor(logging)
                .build()
            Retrofit.Builder()
                .baseUrl(normalizedBaseUrl)
                .client(client)
                .build()
                .create(MiniMaxApiService::class.java)
        }
    }

    fun normalizeBaseUrl(baseUrl: String): String {
        return if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
    }
}
