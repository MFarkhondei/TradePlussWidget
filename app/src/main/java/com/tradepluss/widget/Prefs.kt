package com.tradepluss.widget

import android.content.Context

object Prefs {
    private const val NAME = "tradepluss_widget_prefs"
    private const val KEY_URL = "webapp_url"
    private const val KEY_USER = "username"
    private const val KEY_TOKEN = "token"

    fun save(context: Context, url: String, user: String, token: String) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_URL, url.trim())
            .putString(KEY_USER, user.trim())
            .putString(KEY_TOKEN, token.trim())
            .apply()
    }

    fun getUrl(context: Context): String =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getString(KEY_URL, "") ?: ""

    fun getUser(context: Context): String =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getString(KEY_USER, "") ?: ""

    fun getToken(context: Context): String =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getString(KEY_TOKEN, "") ?: ""

    fun isConfigured(context: Context): Boolean {
        return getUrl(context).isNotBlank() &&
                getUser(context).isNotBlank() &&
                getToken(context).isNotBlank()
    }
}
