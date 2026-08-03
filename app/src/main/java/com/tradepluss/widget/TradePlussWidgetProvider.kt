package com.tradepluss.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews
import com.tradepluss.widget.model.WidgetResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TradePlussWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_REFRESH = "com.tradepluss.widget.ACTION_REFRESH"

        fun updateAll(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, TradePlussWidgetProvider::class.java))
            if (ids.isEmpty()) return
            val intent = Intent(context, TradePlussWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            }
            context.sendBroadcast(intent)
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (id in appWidgetIds) {
            render(context, appWidgetManager, id)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH || intent.action == AppWidgetManager.ACTION_APPWIDGET_UPDATE) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS)
                ?: mgr.getAppWidgetIds(ComponentName(context, TradePlussWidgetProvider::class.java))
            for (id in ids) {
                render(context, mgr, id)
            }
        }
    }

    private fun baseViews(context: Context): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_layout)

        val refreshIntent = Intent(context, TradePlussWidgetProvider::class.java).apply {
            action = ACTION_REFRESH
        }
        val refreshPi = PendingIntent.getBroadcast(
            context, 100, refreshIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.btn_refresh, refreshPi)

        val cfgIntent = Intent(context, ConfigActivity::class.java)
        val cfgPi = PendingIntent.getActivity(
            context, 101, cfgIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_root, cfgPi)

        return views
    }

    private fun render(context: Context, manager: AppWidgetManager, widgetId: Int) {
        // Always show a basic layout first so Samsung can place the widget
        val views = baseViews(context)
        if (!Prefs.isConfigured(context)) {
            views.setTextViewText(R.id.tv_total_assets, "تنظیم نشده")
            views.setTextViewText(R.id.tv_updated_at, "اپ را باز کنید")
            views.setTextViewText(R.id.tv_daily_pnl, "-")
            views.setTextViewText(R.id.tv_daily_pnl_pct, "")
            views.setTextViewText(R.id.tv_daily_buy, "-")
            views.setTextViewText(R.id.tv_asset_1, "برای تنظیم روی ویجت بزنید")
            manager.updateAppWidget(widgetId, views)
            return
        }

        views.setTextViewText(R.id.tv_total_assets, "...")
        views.setTextViewText(R.id.tv_updated_at, "در حال بارگذاری")
        manager.updateAppWidget(widgetId, views)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = Prefs.getUrl(context)
                val user = Prefs.getUser(context)
                val token = Prefs.getToken(context)
                
                // Log for debugging
                android.util.Log.d("WidgetDebug", "Fetching from: $url, user: $user")
                
                val data = ApiClient.fetchWidgetData(url, user, token)
                
                android.util.Log.d("WidgetDebug", "Response success: ${data.success}")
                
                withContext(Dispatchers.Main) {
                    val ready = baseViews(context)
                    applyData(ready, data)
                    manager.updateAppWidget(widgetId, ready)
                }
            } catch (e: Exception) {
                android.util.Log.e("WidgetError", "Fetch failed", e)
                withContext(Dispatchers.Main) {
                    val err = baseViews(context)
                    err.setTextViewText(R.id.tv_total_assets, "خطا")
                    val msg = e.message ?: "شبکه"
                    err.setTextViewText(R.id.tv_updated_at, msg.take(30))
                    manager.updateAppWidget(widgetId, err)
                }
            }
        }
    }

    private fun applyData(views: RemoteViews, data: WidgetResponse) {
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

        views.setTextViewText(R.id.tv_updated_at, data.updatedAt ?: "")

        val slots = intArrayOf(
            R.id.tv_asset_1, R.id.tv_asset_2, R.id.tv_asset_3, R.id.tv_asset_4, R.id.tv_asset_5
        )
        for (i in slots.indices) {
            if (i < data.items.size) {
                val it = data.items[i]
                val name = it.coinName.ifBlank { it.symbol }.ifBlank { "-" }
                val line = "$name  ${NumberUtils.format(it.currentValue)}  ${NumberUtils.formatPercent(it.profitPercent)}"
                views.setTextViewText(slots[i], line)
                val c = if (it.profitPercent >= 0) Color.parseColor("#22C55E") else Color.parseColor("#EF4444")
                views.setTextColor(slots[i], c)
            } else {
                views.setTextViewText(slots[i], "")
            }
        }
    }
}
