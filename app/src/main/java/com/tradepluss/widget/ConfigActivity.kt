package com.tradepluss.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import android.widget.TextView

class ConfigActivity : AppCompatActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_config)
        setResult(Activity.RESULT_CANCELED)

        // Widget configure flow
        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        val etUrl = findViewById<TextInputEditText>(R.id.et_webapp_url)
        val etUser = findViewById<TextInputEditText>(R.id.et_username)
        val etToken = findViewById<TextInputEditText>(R.id.et_token)
        val btnSave = findViewById<MaterialButton>(R.id.btn_save)
        val tvStatus = findViewById<TextView>(R.id.tv_status)

        // Pre-fill
        etUrl.setText(Prefs.getUrl(this).ifBlank {
            "https://script.google.com/macros/s/YOUR_DEPLOYMENT_ID/exec"
        })
        etUser.setText(Prefs.getUser(this))
        etToken.setText(Prefs.getToken(this))

        btnSave.setOnClickListener {
            val url = etUrl.text?.toString()?.trim().orEmpty()
            val user = etUser.text?.toString()?.trim().orEmpty()
            val token = etToken.text?.toString()?.trim().orEmpty()

            if (url.isBlank() || user.isBlank() || token.isBlank()) {
                tvStatus.text = "همه فیلدها الزامی هستند"
                return@setOnClickListener
            }
            if (!url.startsWith("http")) {
                tvStatus.text = "آدرس Web App باید با https شروع شود"
                return@setOnClickListener
            }

            Prefs.save(this, url, user, token)
            Toast.makeText(this, "ذخیره شد", Toast.LENGTH_SHORT).show()

            // If opened from widget configure
            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                setResult(Activity.RESULT_OK, result)
                TradePlussWidgetProvider.updateAll(this)
                finish()
            } else {
                // Opened as normal app → just refresh widgets
                TradePlussWidgetProvider.updateAll(this)
                tvStatus.text = "✅ تنظیمات ذخیره و ویجت‌ها بروزرسانی شدند"
            }
        }
    }
}
