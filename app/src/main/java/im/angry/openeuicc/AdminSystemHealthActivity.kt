package im.angry.openeuicc

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import im.angry.openeuicc.auth.AuthTokenStore
import im.angry.openeuicc.auth.JwtUtils
import im.angry.openeuicc.ui.LoginActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class AdminSystemHealthActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val tokenStore by lazy { AuthTokenStore(this) }

    private lateinit var subtitleText: TextView
    private lateinit var refreshButton: Button
    private lateinit var listContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_system_health)

        subtitleText = findViewById(R.id.adminSystemHealthSubtitleText)
        refreshButton = findViewById(R.id.adminSystemHealthRefreshButton)
        listContainer = findViewById(R.id.adminSystemHealthListContainer)

        refreshButton.setOnClickListener { loadSystemHealth() }
        loadSystemHealth()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun loadSystemHealth() {
        subtitleText.text = "Loading system health..."
        listContainer.removeAllViews()
        addCard("⏳ Loading system health...\\n\\nChecking API, database, and operational status.")

        scope.launch {
            val session = withContext(Dispatchers.IO) { tokenStore.getSession() }

            if (session == null || JwtUtils.isExpired(session.accessToken)) {
                redirectToLogin()
                return@launch
            }

            val result = withContext(Dispatchers.IO) {
                runCatching { fetchSystemHealth(session.authorizationHeader) }
            }

            result.onSuccess { response ->
                renderSystemHealth(response)
            }.onFailure { error ->
                subtitleText.text = "System health unavailable"
                Toast.makeText(
                    this@AdminSystemHealthActivity,
                    error.message ?: "System Health API error",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun fetchSystemHealth(authorizationHeader: String): JSONObject {
        val url = URL("https://roam2world-panels-backend.onrender.com/api/v1/mobile/admin/system-health/")
        val connection = url.openConnection() as HttpURLConnection

        return try {
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Authorization", authorizationHeader)
            connection.connectTimeout = 15000
            connection.readTimeout = 15000

            val stream = if (connection.responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }

            val body = stream.bufferedReader().use { it.readText() }

            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("HTTP ${connection.responseCode}: $body")
            }

            JSONObject(body)
        } finally {
            connection.disconnect()
        }
    }

    private fun renderSystemHealth(response: JSONObject) {
        val data = response.optJSONObject("data") ?: response
        val overall = data.optString("overall_status", "-")
        val timestamp = data.optString("timestamp", "-")
        val counts = data.optJSONObject("counts") ?: JSONObject()

        subtitleText.text = statusBadge(overall)

        addCard(
            "Overall\n\n" +
                statusBadge(overall) + "\n" +
                "Timestamp: $timestamp"
        )

        addCard(
            "Counts\n\n" +
                "Users: ${counts.optInt("users", 0)}\n" +
                "Resellers: ${counts.optInt("resellers", 0)}\n" +
                "Dealers: ${counts.optInt("dealers", 0)}\n" +
                "Orders: ${counts.optInt("orders", 0)}\n" +
                "eSIMs: ${counts.optInt("esims", 0)}\n" +
                "Plans: ${counts.optInt("plans", 0)}\n" +
                "Active Plans: ${counts.optInt("active_plans", 0)}"
        )

        val checks = data.optJSONArray("checks")
        if (checks != null && checks.length() > 0) {
            val text = StringBuilder("Checks\n\n")
            for (i in 0 until checks.length()) {
                val item = checks.optJSONObject(i) ?: continue
                text.append(item.optString("name", "-"))
                    .append(": ")
                    .append(item.optString("status", "-"))
                    .append("\n")
                    .append(item.optString("message", ""))
                    .append("\n\n")
            }
            addCard(text.toString().trim())
        }
    }

    private fun statusBadge(status: String): String {
        val normalized = status.lowercase()
        val icon = when {
            normalized.contains("active") && !normalized.contains("inactive") -> "🟢"
            normalized.contains("completed") -> "🟢"
            normalized.contains("resolved") -> "🟢"
            normalized.contains("ok") -> "🟢"
            normalized.contains("open") -> "🔵"
            normalized.contains("pending") -> "🟠"
            normalized.contains("progress") -> "🟠"
            normalized.contains("inactive") -> "⚪"
            normalized.contains("closed") -> "⚪"
            normalized.contains("suspended") -> "🔴"
            normalized.contains("failed") -> "🔴"
            normalized.contains("error") -> "🔴"
            else -> "🔘"
        }
        return "$icon Status: $status"
    }

    private fun addCard(text: String) {
        val card = TextView(this)
        card.text = text
        card.textSize = 15.5f
        card.setTextColor(0xFF07133D.toInt())
        card.setBackgroundResource(R.drawable.admin_card_background)
        card.elevation = 3f
        card.setPadding(28, 24, 28, 24)

        val params = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        params.setMargins(0, 0, 0, 20)
        card.layoutParams = params
        listContainer.addView(card)
    }

    private fun redirectToLogin() {
        startActivity(
            Intent(this, LoginActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
        )
        finish()
    }
}
