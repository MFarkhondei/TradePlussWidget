package com.tradepluss.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews
import com.google.gson.Gson
import com.tradepluss.widget.model.WidgetResponse

/**
 * Shared widget UI rendering used by Config, SilentRefresh, and Service.
 */
object WidgetRenderer {

    private val gson = Gson()

    fun allIds(context: Context): IntArray {
        val mgr = AppWidgetManager.getInstance(context)
        return mgr.getAppWidgetIds(ComponentName(context, TradePlussWidgetProvider::class.java))
    }

    fun showLoading(context: Context, widgetIds: IntArray? = null) {
        val appCtx = context.applicationContext
        val mgr = AppWidgetManager.getInstance(appCtx)
        val ids = widgetIds ?: allIds(appCtx)
        for (id in ids) {
            val views = baseViews(appCtx, id)
            views.setTextViewText(R.id.tv_updated_at, "در حال بروزرسانی...")
            mgr.updateAppWidget(id, views)
        }
    }

    fun applyResponse(context: Context, data: WidgetResponse, offline: Boolean = false, widgetIds: IntArray? = null) {
        val appCtx = context.applicationContext
        val mgr = AppWidgetManager.getInstance(appCtx)
        val ids = widgetIds ?: allIds(appCtx)
        for (id in ids) {
            val views = baseViews(appCtx, id)
            applyData(views, data, offline)
            mgr.updateAppWidget(id, views)
        }
    }

    fun applyCache(context: Context, offline: Boolean = true, widgetIds: IntArray? = null): Boolean {
        val json = Prefs.getCacheJson(context) ?: return false
        val data = try {
            gson.fromJson(json, WidgetResponse::class.java)
        } catch (_: Exception) {
            null
        } ?: return false
        if (!data.success) return false
        applyResponse(context, data, offline, widgetIds)
        return true
    }

    fun showError(context: Context, message: String, widgetIds: IntArray? = null) {
        val appCtx = context.applicationContext
        val mgr = AppWidgetManager.getInstance(appCtx)
        val ids = widgetIds ?: allIds(appCtx)
        // Prefer cache with offline label; only pure error if no cache
        if (applyCache(appCtx, offline = true, widgetIds = ids)) return
        for (id in ids) {
            val views = baseViews(appCtx, id)
            views.setTextViewText(R.id.tv_total_assets, "خطا")
            views.setTextViewText(R.id.tv_updated_at, message.replace("\n", " ").take(40))
            mgr.updateAppWidget(id, views)
        }
    }

    fun showNotConfigured(context: Context, widgetId: Int) {
        val mgr = AppWidgetManager.getInstance(context)
        val views = baseViews(context, widgetId)
        views.setTextViewText(R.id.tv_total_assets, "تنظیم نشده")
        views.setTextViewText(R.id.tv_updated_at, "اپ را باز کنید")
        views.setTextViewText(R.id.tv_daily_pnl, "-")
        views.setTextViewText(R.id.tv_daily_pnl_pct, "")
        views.setTextViewText(R.id.tv_daily_buy, "-")
        views.setTextViewText(R.id.tv_asset_1, "برای تنظیم روی ویجت بزنید")
        mgr.updateAppWidget(widgetId, views)
    }

    /**
     * Same network call as ConfigActivity "تست اتصال".
     * Returns true on success.
     */
    fun fetchAndApply(context: Context, widgetIds: IntArray? = null): Boolean {
        val appCtx = context.applicationContext
        if (!Prefs.isConfigured(appCtx)) return false
        return try {
            val (data, raw) = ApiClient.fetchWidgetData(
                Prefs.getUrl(appCtx),
                Prefs.getUser(appCtx),
                Prefs.getToken(appCtx)
            )
            if (data.success) {
                Prefs.saveCache(appCtx, raw)
                applyResponse(appCtx, data, offline = false, widgetIds = widgetIds)
                true
            } else {
                showError(appCtx, data.message ?: "ناموفق", widgetIds)
                false
            }
        } catch (e: Exception) {
            showError(appCtx, e.message ?: "شبکه", widgetIds)
            false
        }
    }

    private fun baseViews(context: Context, widgetId: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_layout)

        // Refresh → open SilentRefreshActivity (foreground, same as Config)
        val refreshIntent = Intent(context, SilentRefreshActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        }
        val refreshPi = PendingIntent.getActivity(
            context,
            1000 + widgetId,
            refreshIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.btn_refresh, refreshPi)

        val cfgIntent = Intent(context, ConfigActivity::class.java)
        val cfgPi = PendingIntent.getActivity(
            context,
            2000 + widgetId,
            cfgIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_root, cfgPi)
        return views
    }

    private fun applyData(views: RemoteViews, data: WidgetResponse, offline: Boolean) {
        if (!data.success) {
            views.setTextViewText(R.id.tv_total_assets, "خطا")
            views.setTextViewText(R.id.tv_updated_at, data.message ?: "ناموفق")
            return
        }

        views.setTextViewText(R.id.tv_total_assets, NumberUtils.format(data.totalAssetsToman))
        views.setTextViewText(R.id.tv_daily_buy, NumberUtils.format(data.dailyBuyToman))

        val pnl = data.dailyProfitToman
        val pnlColor = if (pnl >= 0) Color.parseColor("#22C55E") else Color.parseColor("#EF4444")
        views.setTextViewText(R.id.tv_daily_pnl, NumberUtils.formatSigned(pnl))
        views.setTextColor(R.id.tv_daily_pnl, pnlColor)
        views.setTextViewText(R.id.tv_daily_pnl_pct, NumberUtils.formatPercent(data.dailyProfitPercent))
        views.setTextColor(R.id.tv_daily_pnl_pct, pnlColor)

        val stamp = data.updatedAt ?: ""
        views.setTextViewText(
            R.id.tv_updated_at,
            if (offline) "آفلاین · $stamp" else stamp
        )

        val slots = intArrayOf(
            R.id.tv_asset_1, R.id.tv_asset_2, R.id.tv_asset_3, R.id.tv_asset_4, R.id.tv_asset_5
        )
        for (i in slots.indices) {
            if (i < data.items.size) {
                val it = data.items[i]
                val name = it.coinName.ifBlank { it.symbol }.ifBlank { "-" }
                val line =
                    "$name  ${NumberUtils.format(it.currentValue)}  ${NumberUtils.formatPercent(it.profitPercent)}"
                views.setTextViewText(slots[i], line)
                val c =
                    if (it.profitPercent >= 0) Color.parseColor("#22C55E") else Color.parseColor("#EF4444")
                views.setTextColor(slots[i], c)
            } else {
                views.setTextViewText(slots[i], "")
            }
        }
    }
}
