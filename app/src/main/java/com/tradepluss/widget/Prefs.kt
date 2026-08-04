package com.tradepluss.widget

import android.content.Context

object Prefs {
    private const val NAME = "tradepluss_widget_prefs"
    private const val KEY_URL = "webapp_url"
    private const val KEY_USER = "username"
    private const val KEY_TOKEN = "token"
    private const val KEY_CACHE_JSON = "cache_json"
    private const val KEY_CACHE_AT = "cache_at"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun save(context: Context, url: String, user: String, token: String) {
        // commit = synchronous so widget sees values immediately
        prefs(context).edit()
            .putString(KEY_URL, url.trim())
            .putString(KEY_USER, user.trim())
            .putString(KEY_TOKEN, token.trim())
            .commit()
    }

    fun getUrl(context: Context): String =
        prefs(context).getString(KEY_URL, "") ?: ""

    fun getUser(context: Context): String =
        prefs(context).getString(KEY_USER, "") ?: ""

    fun getToken(context: Context): String =
        prefs(context).getString(KEY_TOKEN, "") ?: ""

    fun isConfigured(context: Context): Boolean {
        return getUrl(context).isNotBlank() &&
                getUser(context).isNotBlank() &&
                getToken(context).isNotBlank()
    }

    fun saveCache(context: Context, json: String) {
        prefs(context).edit()
            .putString(KEY_CACHE_JSON, json)
            .putLong(KEY_CACHE_AT, System.currentTimeMillis())
            .commit()
    }

    fun getCacheJson(context: Context): String? =
        prefs(context).getString(KEY_CACHE_JSON, null)

    fun getCacheAt(context: Context): Long =
        prefs(context).getLong(KEY_CACHE_AT, 0L)
}
