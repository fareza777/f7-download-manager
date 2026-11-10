package com.f7.core.download.net

import com.f7.core.model.AppSettings
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

object Http {
    const val USER_AGENT = "F7DownloadManager/1.0 (Android)"

    fun client(settings: AppSettings): OkHttpClient = client(
        connectSeconds = settings.connectTimeoutSec,
        readSeconds = settings.readTimeoutSec,
        userAgent = settings.userAgent,
    )

    fun client(
        connectSeconds: Int = 20,
        readSeconds: Int = 45,
        userAgent: String = "",
    ): OkHttpClient {
        val ua = userAgent.trim().ifBlank { USER_AGENT }
        return OkHttpClient.Builder()
            .connectTimeout(connectSeconds.coerceIn(5, 120).toLong(), TimeUnit.SECONDS)
            .readTimeout(readSeconds.coerceIn(10, 300).toLong(), TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .followSslRedirects(true)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", ua)
                    .build()
                chain.proceed(request)
            }
            .build()
    }

    fun baseRequest(url: String, headers: Map<String, String> = emptyMap()): Request.Builder {
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "*/*")
            .header("Accept-Encoding", "identity")
        headers.forEach { (key, value) ->
            if (key.isNotBlank() && value.isNotBlank()) {
                runCatching { builder.header(key, value) }
            }
        }
        return builder
    }
}
