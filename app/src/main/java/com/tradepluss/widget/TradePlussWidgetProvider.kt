package com.tradepluss.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.view.View
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
            if (ids.isNotEmpty()) {
                val intent = Intent(context, TradePlussWidgetProvider::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                }
                context.sendBroadcast(intent)
            }
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (id in appWidgetIds) {
            updateWidget(context, appWidgetManager, id)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH || intent.action == AppWidgetManager.ACTION_APPWIDGET_UPDATE) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, TradePlussWidgetProvider::class.java))
            for (id in ids) {
                updateWidget(context, mgr, id)
            }
        }
    }

    private fun updateWidget(context: Context, manager: AppWidgetManager, widgetId: Int) {
        val views = RemoteViews(context.packageName, R.layout.widget_layout)

        // Refresh button
        val refreshIntent = Intent(context, TradePlussWidgetProvider::class.java).apply {
            action = ACTION_REFRESH
        }
        val pending = PendingIntent.getBroadcast(
            context, 0, refreshIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.btn_refresh, pending)

        // Open config on header click
        val configIntent = Intent(context, ConfigActivity::class.java)
        val configPending = PendingIntent.getActivity(
            context, 1, configIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_root, configPending)

        manager.updateAppWidget(widgetId, views)

        if (!Prefs.isConfigured(context)) {
            views.setTextViewText(R.id.tv_total_assets, "تنظیم نشده")
            views.setTextViewText(R.id.tv_updated_at, "روی ویجت بزنید")
            manager.updateAppWidget(widgetId, views)
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val data = ApiClient.fetchWidgetData(
                    Prefs.getUrl(context),
                    Prefs.getUser(context),
                    Prefs.getToken(context)
                )
                withContext(Dispatchers.Main) {
                    applyData(context, manager, widgetId, data)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    val errViews = RemoteViews(context.packageName, R.layout.widget_layout)
                    errViews.setTextViewText(R.id.tv_total_assets, "خطا")
                    errViews.setTextViewText(R.id.tv_updated_at, e.message?.take(30) ?: "خطای شبکه")
                    errViews.setOnClickPendingIntent(R.id.btn_refresh, pending)
                    manager.updateAppWidget(widgetId, errViews)
                }
            }
        }
    }

    private fun applyData(
        context: Context,
        manager: AppWidgetManager,
        widgetId: Int,
        data: WidgetResponse
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_layout)

        if (!data.success) {
            views.setTextViewText(R.id.tv_total_assets, "خطا")
            views.setTextViewText(R.id.tv_updated_at, data.message ?: "ناموفق")
            manager.updateAppWidget(widgetId, views)
            return
        }

        views.setTextViewText(R.id.tv_total_assets, NumberUtils.format(data.totalAssetsToman))
        views.setTextViewText(R.id.tv_daily_buy, NumberUtils.format(data.dailyBuyToman) + " تومان")

        val pnl = data.dailyProfitToman
        val pnlColor = if (pnl >= 0) Color.parseColor("#22C55E") else Color.parseColor("#EF4444")
        views.setTextViewText(R.id.tv_daily_pnl, NumberUtils.formatSigned(pnl) + " تومان")
        views.setTextColor(R.id.tv_daily_pnl, pnlColor)
        views.setTextViewText(R.id.tv_daily_pnl_pct, "(${NumberUtils.formatPercent(data.dailyProfitPercent)})")
        views.setTextColor(R.id.tv_daily_pnl_pct, pnlColor)
        views.setImageViewResource(
            R.id.iv_pnl_icon,
            if (pnl >= 0) R.drawable.ic_arrow_up else R.drawable.ic_arrow_down
        )

        views.setTextViewText(R.id.tv_updated_at, data.updatedAt ?: "")

        // Chart
        if (data.weeklyValues.isNotEmpty()) {
            val chart = ChartHelper.drawChart(data.weeklyValues, 600, 180)
            views.setImageViewBitmap(R.id.iv_chart, chart)
        }

        // Assets (max 5)
        views.removeAllViews(R.id.assets_container)
        data.items.take(5).forEach { item ->
            val row = RemoteViews(context.packageName, R.layout.item_asset)
            row.setTextViewText(R.id.tv_asset_name, item.coinName.ifBlank { item.symbol })
            row.setTextViewText(R.id.tv_asset_value, NumberUtils.format(item.currentValue))

            val changeColor = if (item.profitPercent >= 0)
                Color.parseColor("#22C55E") else Color.parseColor("#EF4444")
            row.setTextViewText(R.id.tv_asset_change, NumberUtils.formatPercent(item.profitPercent))
            row.setTextColor(R.id.tv_asset_change, changeColor)

            // Simple icon by symbol
            val iconRes = when (item.symbol.uppercase()) {
                "BTC" -> R.drawable.ic_coin_default
                "ETH" -> R.drawable.ic_coin_default
                "GOLD", "PAXG" -> R.drawable.ic_coin_default
                else -> R.drawable.ic_coin_default
            }
            row.setImageViewResource(R.id.iv_asset_icon, iconRes)

            views.addView(R.id.assets_container, row)
        }

        // Refresh click
        val refreshIntent = Intent(context, TradePlussWidgetProvider::class.java).apply {
            action = ACTION_REFRESH
        }
        val pending = PendingIntent.getBroadcast(
            context, 0, refreshIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.btn_refresh, pending)

        val configIntent = Intent(context, ConfigActivity::class.java)
        val configPending = PendingIntent.getActivity(
            context, 1, configIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_root, configPending)

        manager.updateAppWidget(widgetId, views)
    }
}
