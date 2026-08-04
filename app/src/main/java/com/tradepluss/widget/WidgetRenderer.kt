package com.tradepluss.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.view.View
import android.widget.RemoteViews
import com.google.gson.Gson
import com.tradepluss.widget.model.AssetItem
import com.tradepluss.widget.model.WidgetResponse

object WidgetRenderer {

    private val gson = Gson()

    private data class AssetSlot(
        val row: Int,
        val icon: Int,
        val name: Int,
        val value: Int,
        val pct: Int
    )

    private val slots = arrayOf(
        AssetSlot(R.id.row_asset_1, R.id.iv_asset_1, R.id.tv_asset_1_name, R.id.tv_asset_1_value, R.id.tv_asset_1_pct),
        AssetSlot(R.id.row_asset_2, R.id.iv_asset_2, R.id.tv_asset_2_name, R.id.tv_asset_2_value, R.id.tv_asset_2_pct),
        AssetSlot(R.id.row_asset_3, R.id.iv_asset_3, R.id.tv_asset_3_name, R.id.tv_asset_3_value, R.id.tv_asset_3_pct),
        AssetSlot(R.id.row_asset_4, R.id.iv_asset_4, R.id.tv_asset_4_name, R.id.tv_asset_4_value, R.id.tv_asset_4_pct),
        AssetSlot(R.id.row_asset_5, R.id.iv_asset_5, R.id.tv_asset_5_name, R.id.tv_asset_5_value, R.id.tv_asset_5_pct)
    )

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
        views.setTextViewText(R.id.tv_asset_1_name, "برای تنظیم روی ویجت بزنید")
        views.setTextViewText(R.id.tv_asset_1_value, "")
        views.setTextViewText(R.id.tv_asset_1_pct, "")
        for (i in 1 until slots.size) {
            views.setViewVisibility(slots[i].row, View.GONE)
        }
        mgr.updateAppWidget(widgetId, views)
    }

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
        val pnlColor = if (pnl >= 0) Color.parseColor("#34D399") else Color.parseColor("#F87171")
        views.setTextViewText(R.id.tv_daily_pnl, NumberUtils.formatSigned(pnl))
        views.setTextColor(R.id.tv_daily_pnl, pnlColor)
        views.setTextViewText(R.id.tv_daily_pnl_pct, NumberUtils.formatPercent(data.dailyProfitPercent))
        views.setTextColor(R.id.tv_daily_pnl_pct, pnlColor)

        val stamp = data.updatedAt ?: ""
        views.setTextViewText(
            R.id.tv_updated_at,
            if (offline) "آفلاین · $stamp" else stamp
        )

        for (i in slots.indices) {
            val slot = slots[i]
            if (i < data.items.size) {
                views.setViewVisibility(slot.row, View.VISIBLE)
                bindAsset(views, slot, data.items[i])
            } else {
                views.setViewVisibility(slot.row, View.GONE)
            }
        }
    }

    private fun bindAsset(views: RemoteViews, slot: AssetSlot, item: AssetItem) {
        val name = item.coinName.ifBlank { item.symbol }.ifBlank { "-" }
        val positive = item.profitPercent >= 0
        val pctColor = if (positive) Color.parseColor("#34D399") else Color.parseColor("#F87171")
        val arrow = if (positive) " ↑" else " ↓"

        views.setTextViewText(slot.name, name)
        views.setTextViewText(slot.value, NumberUtils.format(item.currentValue))
        views.setTextViewText(slot.pct, NumberUtils.formatPercent(item.profitPercent) + arrow)
        views.setTextColor(slot.pct, pctColor)
        views.setInt(
            slot.pct,
            "setBackgroundResource",
            if (positive) R.drawable.bg_badge_pos else R.drawable.bg_badge_neg
        )
        views.setImageViewResource(slot.icon, iconForSymbol(item.symbol, item.coinName))
    }

    private fun iconForSymbol(symbol: String, coinName: String): Int {
        val s = symbol.uppercase()
        val n = coinName
        return when {
            s.contains("BTC") || n.contains("بیت") -> R.drawable.ic_asset_btc
            s.contains("ETH") || n.contains("اتری") -> R.drawable.ic_asset_eth
            s.contains("USDC") || s.contains("USDT") || n.contains("یو اس") || n.contains("تتر") -> R.drawable.ic_asset_usdc
            s.contains("GOLD") || n.contains("طلا") -> R.drawable.ic_asset_gold
            s.contains("SILVER") || n.contains("نقره") -> R.drawable.ic_asset_silver
            else -> R.drawable.ic_coin_default
        }
    }
}
