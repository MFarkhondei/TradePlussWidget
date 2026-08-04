package com.tradepluss.widget

import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ConfigActivity : AppCompatActivity() {

    private var testJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_config)

        val etUrl = findViewById<TextInputEditText>(R.id.et_webapp_url)
        val etUser = findViewById<TextInputEditText>(R.id.et_username)
        val etToken = findViewById<TextInputEditText>(R.id.et_token)
        val btnSave = findViewById<MaterialButton>(R.id.btn_save)
        val btnTestConnection = findViewById<MaterialButton>(R.id.btn_test_connection)
        val tvStatus = findViewById<TextView>(R.id.tv_status)

        etUrl.setText(
            Prefs.getUrl(this).ifBlank {
                "https://script.google.com/macros/s/YOUR_DEPLOYMENT_ID/exec"
            }
        )
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
            // If we already have cache from a successful test, push it now
            TradePlussWidgetProvider.applyCachedToAll(this)
            TradePlussWidgetProvider.updateAll(this)
            tvStatus.text = "✅ ذخیره شد. ویجت در حال بروزرسانی است."
        }

        btnTestConnection.setOnClickListener {
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

            testJob?.cancel()
            btnTestConnection.isEnabled = false
            btnTestConnection.text = getString(R.string.testing)
            tvStatus.text = "در حال اتصال..."

            testJob = CoroutineScope(Dispatchers.IO).launch {
                try {
                    // Always persist credentials before network call
                    Prefs.save(this@ConfigActivity, url, user, token)

                    val (response, raw) = ApiClient.fetchWidgetData(url, user, token)
                    if (response.success) {
                        Prefs.saveCache(this@ConfigActivity, raw)
                    }

                    withContext(Dispatchers.Main) {
                        if (response.success) {
                            tvStatus.text = getString(R.string.connection_success)
                            // Push live data into widget immediately
                            TradePlussWidgetProvider.applyCachedToAll(this@ConfigActivity)
                            Toast.makeText(
                                this@ConfigActivity,
                                "داده روی ویجت اعمال شد",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            tvStatus.text = getString(
                                R.string.connection_failed,
                                response.message ?: "پاسخ نامعتبر"
                            )
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        val errorMsg = e.message ?: "خطای ناشناخته"
                        tvStatus.text = getString(R.string.connection_failed, errorMsg)
                    }
                } finally {
                    withContext(Dispatchers.Main) {
                        btnTestConnection.isEnabled = true
                        btnTestConnection.text = getString(R.string.test_connection)
                    }
                }
            }
        }
    }
}
