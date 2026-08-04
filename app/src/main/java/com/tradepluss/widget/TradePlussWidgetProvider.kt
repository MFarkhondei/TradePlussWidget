package com.tradepluss.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews
import com.google.gson.Gson
import com.tradepluss.widget.model.WidgetResponse
import java.util.concurrent.Executors

class TradePlussWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_REFRESH = "com.tradepluss.widget.ACTION_REFRESH"
        const val ACTION_AUTO_UPDATE = "com.tradepluss.widget.ACTION_AUTO_UPDATE"
        private val executor = Executors.newSingleThreadExecutor()
        private val gson = Gson()

        fun updateAll(context: Context) {
            val appCtx = context.applicationContext
            val mgr = AppWidgetManager.getInstance(appCtx)
            val ids = mgr.getAppWidgetIds(ComponentName(appCtx, TradePlussWidgetProvider::class.java))
            if (ids.isEmpty()) return
            for (id in ids) {
                // Direct call avoids broadcast race on some Samsung devices
                renderStatic(appCtx, mgr, id, forceNetwork = true)
            }
        }

        fun applyCachedToAll(context: Context) {
            val appCtx = context.applicationContext
            val json = Prefs.getCacheJson(appCtx) ?: return
            val data = try {
                gson.fromJson(json, WidgetResponse::class.java)
            } catch (_: Exception) {
                null
            } ?: return
            val mgr = AppWidgetManager.getInstance(appCtx)
            val ids = mgr.getAppWidgetIds(ComponentName(appCtx, TradePlussWidgetProvider::class.java))
            for (id in ids) {
                val views = RemoteViews(appCtx.packageName, R.layout.widget_layout)
                wireClicks(appCtx, views, id)
                applyData(views, data, offline = false)
                mgr.updateAppWidget(id, views)
            }
        }

        private fun wireClicks(context: Context, views: RemoteViews, widgetId: Int) {
            val refreshIntent = Intent(context, TradePlussWidgetProvider::class.java).apply {
                action = ACTION_REFRESH
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            }
            val refreshPi = PendingIntent.getBroadcast(
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

        fun renderStatic(
            context: Context,
            manager: AppWidgetManager,
            widgetId: Int,
            forceNetwork: Boolean
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_layout)
            wireClicks(context, views, widgetId)

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

            // Immediate feedback on manual refresh
            if (forceNetwork) {
                views.setTextViewText(R.id.tv_updated_at, "در حال بروزرسانی...")
                manager.updateAppWidget(widgetId, views)
            } else {
                val cachedJson = Prefs.getCacheJson(context)
                if (!cachedJson.isNullOrBlank()) {
                    try {
                        val cached = gson.fromJson(cachedJson, WidgetResponse::class.java)
                        if (cached != null && cached.success) {
                            applyData(views, cached, offline = true)
                            manager.updateAppWidget(widgetId, views)
                        }
                    } catch (_: Exception) {
                    }
                } else {
                    views.setTextViewText(R.id.tv_total_assets, "...")
                    views.setTextViewText(R.id.tv_updated_at, "در حال بارگذاری")
                    manager.updateAppWidget(widgetId, views)
                }
            }

            executor.execute {
                try {
                    val (data, raw) = ApiClient.fetchWidgetData(
                        Prefs.getUrl(context),
                        Prefs.getUser(context),
                        Prefs.getToken(context)
                    )
                    if (data.success) {
                        Prefs.saveCache(context, raw)
                    }
                    val ready = RemoteViews(context.packageName, R.layout.widget_layout)
                    wireClicks(context, ready, widgetId)
                    applyData(ready, data, offline = false)
                    manager.updateAppWidget(widgetId, ready)
                } catch (e: Exception) {
                    val hasCache = !Prefs.getCacheJson(context).isNullOrBlank()
                    if (!hasCache || forceNetwork) {
                        // On forced refresh failure, still try to show cache with offline label
                        val cachedJson = Prefs.getCacheJson(context)
                        if (!cachedJson.isNullOrBlank()) {
                            try {
                                val cached = gson.fromJson(cachedJson, WidgetResponse::class.java)
                                if (cached != null && cached.success) {
                                    val offlineViews =
                                        RemoteViews(context.packageName, R.layout.widget_layout)
                                    wireClicks(context, offlineViews, widgetId)
                                    applyData(offlineViews, cached, offline = true)
                                    manager.updateAppWidget(widgetId, offlineViews)
                                    return@execute
                                }
                            } catch (_: Exception) {
                            }
                        }
                        val err = RemoteViews(context.packageName, R.layout.widget_layout)
                        wireClicks(context, err, widgetId)
                        err.setTextViewText(R.id.tv_total_assets, "خطا")
                        val msg = (e.message ?: "شبکه").replace("\n", " ")
                        err.setTextViewText(R.id.tv_updated_at, msg.take(40))
                        manager.updateAppWidget(widgetId, err)
                    }
                }
            }
        }
    }

    override fun onEnabled(context: Context) {
        UpdateScheduler.schedule(context)
    }

    override fun onDisabled(context: Context) {
        UpdateScheduler.cancel(context)
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        UpdateScheduler.schedule(context)
        for (id in appWidgetIds) {
            renderStatic(context.applicationContext, appWidgetManager, id, forceNetwork = false)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action ?: return
        val appCtx = context.applicationContext
        val mgr = AppWidgetManager.getInstance(appCtx)

        when (action) {
            ACTION_REFRESH -> {
                val id = intent.getIntExtra(
                    AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID
                )
                val ids = if (id != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    intArrayOf(id)
                } else {
                    mgr.getAppWidgetIds(ComponentName(appCtx, TradePlussWidgetProvider::class.java))
                }
                for (wid in ids) {
                    renderStatic(appCtx, mgr, wid, forceNetwork = true)
                }
            }
            ACTION_AUTO_UPDATE -> {
                val ids = mgr.getAppWidgetIds(ComponentName(appCtx, TradePlussWidgetProvider::class.java))
                for (wid in ids) {
                    renderStatic(appCtx, mgr, wid, forceNetwork = true)
                }
                // Chain next alarm
                UpdateScheduler.reschedule(appCtx)
            }
            AppWidgetManager.ACTION_APPWIDGET_UPDATE -> {
                val ids = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS)
                    ?: mgr.getAppWidgetIds(ComponentName(appCtx, TradePlussWidgetProvider::class.java))
                for (wid in ids) {
                    renderStatic(appCtx, mgr, wid, forceNetwork = true)
                }
            }
        }
    }
}
