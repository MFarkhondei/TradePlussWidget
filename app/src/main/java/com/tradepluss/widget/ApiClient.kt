package com.tradepluss.widget

import com.google.gson.Gson
import com.tradepluss.widget.model.WidgetResponse
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

object ApiClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    fun fetchWidgetData(baseUrl: String, username: String, token: String): WidgetResponse {
        val cleanUrl = baseUrl.trimEnd('/')
        val url = "$cleanUrl?action=widget&user=${java.net.URLEncoder.encode(username, "UTF-8")}&token=${java.net.URLEncoder.encode(token, "UTF-8")}"

        val request = Request.Builder()
            .url(url)
            .get()
            .header("User-Agent", "TradePlussWidget/1.0")
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: throw Exception("پاسخ خالی از سرور")
            if (!response.isSuccessful) {
                throw Exception("خطای شبکه: ${response.code}")
            }
            return gson.fromJson(body, WidgetResponse::class.java)
                ?: throw Exception("پاسخ نامعتبر")
        }
    }
}
