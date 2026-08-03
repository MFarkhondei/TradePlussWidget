package com.tradepluss.widget

import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * Placeholder for future JobScheduler / WorkManager periodic updates.
 * Current update is driven by AppWidgetProvider + manual refresh.
 */
class WidgetUpdateService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        TradePlussWidgetProvider.updateAll(applicationContext)
        stopSelf()
        return START_NOT_STICKY
    }
}
