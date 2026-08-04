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
            FontHelper.setText(views, appCtx, R.id.tv_updated_at, "در حال بروزرسانی...")
            mgr.updateAppWidget(id, views)
        }
    }

    fun applyResponse(context: Context, data: WidgetResponse, offline: Boolean = false, widgetIds: IntArray? = null) {
        val appCtx = context.applicationContext
        val mgr = AppWidgetManager.getInstance(appCtx)
        val ids = widgetIds ?: allIds(appCtx)
        for (id in ids) {
            val views = baseViews(appCtx, id)
            applyData(appCtx, views, data, offline)
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
            FontHelper.setText(views, appCtx, R.id.tv_total_assets, "خطا", bold = true)
            FontHelper.setText(views, appCtx, R.id.tv_updated_at, message.replace("\n", " ").take(40))
            mgr.updateAppWidget(id, views)
        }
    }

    fun showNotConfigured(context: Context, widgetId: Int) {
        val appCtx = context.applicationContext
        val mgr = AppWidgetManager.getInstance(appCtx)
        val views = baseViews(appCtx, widgetId)
        FontHelper.setText(views, appCtx, R.id.tv_total_assets, "تنظیم نشده", bold = true)
        FontHelper.setText(views, appCtx, R.id.tv_updated_at, "اپ را باز کنید")
        FontHelper.setText(views, appCtx, R.id.tv_daily_pnl, "-", bold = true)
        FontHelper.setText(views, appCtx, R.id.tv_daily_pnl_pct, "")
        FontHelper.setText(views, appCtx, R.id.tv_daily_buy, "-", bold = true)
        FontHelper.setText(views, appCtx, R.id.tv_asset_1_name, "برای تنظیم روی ویجت بزنید", bold = true)
        FontHelper.setText(views, appCtx, R.id.tv_asset_1_value, "", bold = true)
        FontHelper.setText(views, appCtx, R.id.tv_asset_1_pct, "", bold = true)
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

        // Re-apply static labels with Vazir (XML fontFamily is ignored by many launchers)
        FontHelper.setText(views, context, R.id.tv_title, context.getString(R.string.widget_name), bold = true)

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

    private fun applyData(context: Context, views: RemoteViews, data: WidgetResponse, offline: Boolean) {
        if (!data.success) {
            FontHelper.setText(views, context, R.id.tv_total_assets, "خطا", bold = true)
            FontHelper.setText(views, context, R.id.tv_updated_at, data.message ?: "ناموفق")
            return
        }

        FontHelper.setText(views, context, R.id.tv_total_assets, NumberUtils.format(data.totalAssetsToman), bold = true)
        FontHelper.setText(views, context, R.id.tv_daily_buy, NumberUtils.format(data.dailyBuyToman), bold = true)

        val pnl = data.dailyProfitToman
        val pnlColor = if (pnl >= 0) Color.parseColor("#34D399") else Color.parseColor("#F87171")
        FontHelper.setText(views, context, R.id.tv_daily_pnl, NumberUtils.formatSigned(pnl), bold = true)
        views.setTextColor(R.id.tv_daily_pnl, pnlColor)
        FontHelper.setText(views, context, R.id.tv_daily_pnl_pct, NumberUtils.formatPercent(data.dailyProfitPercent))
        views.setTextColor(R.id.tv_daily_pnl_pct, pnlColor)

        val stamp = data.updatedAt ?: ""
        FontHelper.setText(
            views,
            context,
            R.id.tv_updated_at,
            if (offline) "آفلاین · $stamp" else stamp
        )

        for (i in slots.indices) {
            val slot = slots[i]
            if (i < data.items.size) {
                views.setViewVisibility(slot.row, View.VISIBLE)
                bindAsset(context, views, slot, data.items[i])
            } else {
                views.setViewVisibility(slot.row, View.GONE)
            }
        }
    }

    private fun bindAsset(context: Context, views: RemoteViews, slot: AssetSlot, item: AssetItem) {
        val name = item.coinName.ifBlank { item.symbol }.ifBlank { "-" }
        val positive = item.profitPercent >= 0
        val pctColor = if (positive) Color.parseColor("#34D399") else Color.parseColor("#F87171")
        val arrow = if (positive) " ↑" else " ↓"

        FontHelper.setText(views, context, slot.name, name, bold = true)
        FontHelper.setText(views, context, slot.value, NumberUtils.format(item.currentValue), bold = true)
        FontHelper.setText(views, context, slot.pct, NumberUtils.formatPercent(item.profitPercent) + arrow, bold = true)
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
