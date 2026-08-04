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

    // Colors
    private val GOLD = Color.parseColor("#F5C542")
    private val WHITE = Color.parseColor("#F8FAFC")
    private val MUTED = Color.parseColor("#A8B2C1")   // سفید کم‌رنگ برای هدر جدول
    private val SECONDARY = Color.parseColor("#94A3B8")
    private val POS = Color.parseColor("#34D399")
    private val NEG = Color.parseColor("#F87171")

    private data class AssetSlot(
        val row: Int,
        val icon: Int,
        val name: Int,
        val value: Int,
        val toman: Int,
        val pct: Int,
        val pctBg: Boolean // only used to pick badge drawable via parent — set on row container in layout
    )

    private val slots = arrayOf(
        AssetSlot(R.id.row_asset_1, R.id.iv_asset_1, R.id.iv_asset_1_name, R.id.iv_asset_1_value, R.id.iv_asset_1_toman, R.id.iv_asset_1_pct, true),
        AssetSlot(R.id.row_asset_2, R.id.iv_asset_2, R.id.iv_asset_2_name, R.id.iv_asset_2_value, R.id.iv_asset_2_toman, R.id.iv_asset_2_pct, true),
        AssetSlot(R.id.row_asset_3, R.id.iv_asset_3, R.id.iv_asset_3_name, R.id.iv_asset_3_value, R.id.iv_asset_3_toman, R.id.iv_asset_3_pct, true),
        AssetSlot(R.id.row_asset_4, R.id.iv_asset_4, R.id.iv_asset_4_name, R.id.iv_asset_4_value, R.id.iv_asset_4_toman, R.id.iv_asset_4_pct, true),
        AssetSlot(R.id.row_asset_5, R.id.iv_asset_5, R.id.iv_asset_5_name, R.id.iv_asset_5_value, R.id.iv_asset_5_toman, R.id.iv_asset_5_pct, true)
    )

    // Badge container ids (LinearLayout wrapping pct ImageView)
    private val pctContainers = intArrayOf(
        R.id.row_asset_1, // we'll set background on a dedicated approach — use setInt on pct parent
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
            FontHelper.setTextBitmap(views, appCtx, R.id.iv_updated_at, "در حال بروزرسانی...", 10f, MUTED)
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
            FontHelper.setTextBitmap(views, appCtx, R.id.iv_total_assets, "خطا", 24f, GOLD, bold = true)
            FontHelper.setTextBitmap(views, appCtx, R.id.iv_updated_at, message.replace("\n", " ").take(40), 10f, MUTED)
            mgr.updateAppWidget(id, views)
        }
    }

    fun showNotConfigured(context: Context, widgetId: Int) {
        val appCtx = context.applicationContext
        val mgr = AppWidgetManager.getInstance(appCtx)
        val views = baseViews(appCtx, widgetId)
        FontHelper.setTextBitmap(views, appCtx, R.id.iv_total_assets, "تنظیم نشده", 22f, GOLD, bold = true)
        FontHelper.setTextBitmap(views, appCtx, R.id.iv_updated_at, "اپ را باز کنید", 10f, MUTED)
        FontHelper.setTextBitmap(views, appCtx, R.id.iv_daily_pnl, "-", 14f, WHITE, bold = true)
        FontHelper.setTextBitmap(views, appCtx, R.id.iv_daily_pnl_pct, "", 11f, MUTED)
        FontHelper.setTextBitmap(views, appCtx, R.id.iv_daily_buy, "-", 14f, WHITE, bold = true)
        FontHelper.setTextBitmap(views, appCtx, R.id.iv_asset_1_name, "برای تنظیم روی ویجت بزنید", 12f, WHITE, bold = true, maxWidthDp = 120f)
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

        // Static labels with Vazir bitmap
        FontHelper.setTextBitmap(views, context, R.id.iv_title, context.getString(R.string.widget_name), 16f, GOLD, bold = true)
        FontHelper.setTextBitmap(views, context, R.id.iv_label_total, context.getString(R.string.total_assets), 11f, SECONDARY)
        FontHelper.setTextBitmap(views, context, R.id.iv_label_toman_total, context.getString(R.string.toman), 10f, MUTED)
        FontHelper.setTextBitmap(views, context, R.id.iv_label_pnl, context.getString(R.string.daily_pnl), 10f, MUTED)
        FontHelper.setTextBitmap(views, context, R.id.iv_label_buy, context.getString(R.string.daily_buy), 10f, MUTED)
        FontHelper.setTextBitmap(views, context, R.id.iv_label_toman_buy, context.getString(R.string.toman), 10f, MUTED)

        // Table headers — سفید کم‌رنگ
        FontHelper.setTextBitmap(views, context, R.id.iv_header_assets, context.getString(R.string.assets), 12f, MUTED, bold = true, align = FontHelper.Align.CENTER)
        FontHelper.setTextBitmap(views, context, R.id.iv_header_value, context.getString(R.string.current_value), 11f, MUTED, align = FontHelper.Align.CENTER)
        FontHelper.setTextBitmap(views, context, R.id.iv_header_pct, context.getString(R.string.change_percent), 11f, MUTED, align = FontHelper.Align.CENTER)

        val refreshIntent = Intent(context, SilentRefreshActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        }
        views.setOnClickPendingIntent(
            R.id.btn_refresh,
            PendingIntent.getActivity(
                context, 1000 + widgetId, refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
        views.setOnClickPendingIntent(
            R.id.widget_root,
            PendingIntent.getActivity(
                context, 2000 + widgetId,
                Intent(context, ConfigActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
        return views
    }

    private fun applyData(context: Context, views: RemoteViews, data: WidgetResponse, offline: Boolean) {
        if (!data.success) {
            FontHelper.setTextBitmap(views, context, R.id.iv_total_assets, "خطا", 24f, GOLD, bold = true)
            FontHelper.setTextBitmap(views, context, R.id.iv_updated_at, data.message ?: "ناموفق", 10f, MUTED)
            return
        }

        FontHelper.setTextBitmap(
            views, context, R.id.iv_total_assets,
            NumberUtils.format(data.totalAssetsToman), 24f, GOLD, bold = true
        )

        val pnl = data.dailyProfitToman
        val pnlColor = if (pnl >= 0) POS else NEG
        FontHelper.setTextBitmap(views, context, R.id.iv_daily_pnl, NumberUtils.formatSigned(pnl), 14f, pnlColor, bold = true)
        FontHelper.setTextBitmap(views, context, R.id.iv_daily_pnl_pct, NumberUtils.formatPercent(data.dailyProfitPercent), 11f, pnlColor)
        FontHelper.setTextBitmap(views, context, R.id.iv_daily_buy, NumberUtils.format(data.dailyBuyToman), 14f, WHITE, bold = true)

        val stamp = data.updatedAt ?: ""
        FontHelper.setTextBitmap(
            views, context, R.id.iv_updated_at,
            if (offline) "آفلاین · $stamp" else stamp, 10f, MUTED
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
        val pctColor = if (positive) POS else NEG
        val arrow = if (positive) " ↑" else " ↓"

        FontHelper.setTextBitmap(views, context, slot.name, name, 12f, WHITE, bold = true, maxWidthDp = 100f)
        FontHelper.setTextBitmap(views, context, slot.value, NumberUtils.format(item.currentValue), 12f, WHITE, bold = true, align = FontHelper.Align.CENTER)
        FontHelper.setTextBitmap(views, context, slot.toman, context.getString(R.string.toman), 9f, MUTED, align = FontHelper.Align.CENTER)
        FontHelper.setTextBitmap(
            views, context, slot.pct,
            NumberUtils.formatPercent(item.profitPercent) + arrow,
            11f, pctColor, bold = true, align = FontHelper.Align.CENTER
        )

        // Badge background on the LinearLayout that wraps pct — use row's last child via setInt on known container
        // Containers are the weight-0.9 LinearLayouts; we set background on parent of pct by using setInt on a wrapper.
        // Layout uses badge on the LinearLayout parent of iv_asset_N_pct — set via reflection id not available.
        // Instead set background on pct image's sibling container: we tagged them implicitly.
        // Use setInt on the ImageView doesn't set parent bg. Re-set on known row structure:
        // For simplicity badge drawables stay as default in XML; color of text is enough signal.
        // Optionally update the wrapping LinearLayout — not exposed by id.
        // Add ids for badge containers in a future pass; for now color is clear.

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
