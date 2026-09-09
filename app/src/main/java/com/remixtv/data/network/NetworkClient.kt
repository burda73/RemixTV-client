package com.remixtv.data.network

import com.google.gson.GsonBuilder
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Фабрика Retrofit/OkHttp.
 *
 * ВАЖНО: укажите IP/хост сервера по умолчанию ниже или задайте URL в настройках приложения.
 * Базовый URL должен заканчиваться на `/`, например: `http://192.168.0.10:8080/`
 */
object NetworkClient {

    /** Смените на адрес вашего Video Playlist Manager */
    const val DEFAULT_BASE_URL: String = "http://tv.remixgold.ru/"

    private val gson = GsonBuilder()
        .setLenient()
        .create()

    fun createOkHttpClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.MINUTES)
            .writeTimeout(5, TimeUnit.MINUTES)
            .addInterceptor(logging)
            .build()
    }

    fun createApiService(baseUrl: String, client: OkHttpClient): ApiService {
        val normalized = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        return Retrofit.Builder()
            .baseUrl(normalized)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(ApiService::class.java)
    }
}
