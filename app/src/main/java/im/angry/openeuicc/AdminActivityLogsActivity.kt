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

class AdminActivityLogsActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val tokenStore by lazy { AuthTokenStore(this) }

    private lateinit var subtitleText: TextView
    private lateinit var refreshButton: Button
    private lateinit var listContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_activity_logs)

        subtitleText = findViewById(R.id.adminActivityLogsSubtitleText)
        refreshButton = findViewById(R.id.adminActivityLogsRefreshButton)
        listContainer = findViewById(R.id.adminActivityLogsListContainer)

        refreshButton.setOnClickListener { loadActivityLogs() }
        loadActivityLogs()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun loadActivityLogs() {
        subtitleText.text = "Loading activity logs..."
        listContainer.removeAllViews()
        addCard("Loading activity logs...\\n\\nFetching recent admin and system activity.")

        scope.launch {
            val session = withContext(Dispatchers.IO) { tokenStore.getSession() }

            if (session == null || JwtUtils.isExpired(session.accessToken)) {
                redirectToLogin()
                return@launch
            }

            val result = withContext(Dispatchers.IO) {
                runCatching { fetchActivityLogs(session.authorizationHeader) }
            }

            result.onSuccess { response ->
                renderActivityLogs(response)
            }.onFailure { error ->
                subtitleText.text = "Activity logs unavailable"
                Toast.makeText(
                    this@AdminActivityLogsActivity,
                    error.message ?: "Activity Logs API error",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun fetchActivityLogs(authorizationHeader: String): JSONObject {
        val url = URL("https://roam2world-panels-backend.onrender.com/api/v1/mobile/admin/activity-logs/")
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

    private fun renderActivityLogs(response: JSONObject) {
        val data = response.optJSONObject("data") ?: response
        val count = data.optInt("count", 0)
        val logs = data.optJSONArray("logs")

        subtitleText.text = "$count activity log(s)"

        val summary = data.optJSONObject("summary")
        if (summary != null) {
            addCard(
                "Summary\n\n" +
                    "Users: ${summary.optInt("users", 0)}\n" +
                    "Orders: ${summary.optInt("orders", 0)}\n" +
                    "Resellers: ${summary.optInt("resellers", 0)}\n" +
                    "Dealers: ${summary.optInt("dealers", 0)}\n" +
                    "eSIMs: ${summary.optInt("esims", 0)}\n" +
                    "Plans: ${summary.optInt("plans", 0)}"
            )
        }

        if (logs == null || logs.length() == 0) {
            addCard("No activity logs found.\n\nAdmin and system actions will appear here after activity is recorded.")
            return
        }

        for (i in 0 until logs.length()) {
            val item = logs.optJSONObject(i) ?: continue

            val title = item.optString("title", "-")
            val type = item.optString("type", "-")
            val status = item.optString("status", "-")
            val actor = item.optString("actor", "-")
            val message = item.optString("message", "-")
            val createdAt = item.optString("created_at", "-")

            addCard(
                "$title\n" +
                    statusBadge(status) + "\n\n" +
                    "Type: $type\n" +
                    "Actor: $actor\n" +
                    "Date: $createdAt\n\n" +
                    message.take(140) + if (message.length > 140) "..." else "" + "\n\n" +
                    "Tap to open details →",
                item.toString()
            )
        }
    }

    private fun statusBadge(status: String): String {
        val normalized = status.lowercase()
        val icon = when {
            normalized.contains("active") && !normalized.contains("inactive") -> ""
            normalized.contains("completed") -> ""
            normalized.contains("resolved") -> ""
            normalized.contains("ok") -> ""
            normalized.contains("open") -> ""
            normalized.contains("pending") -> ""
            normalized.contains("progress") -> ""
            normalized.contains("inactive") -> ""
            normalized.contains("closed") -> ""
            normalized.contains("suspended") -> ""
            normalized.contains("failed") -> ""
            normalized.contains("error") -> ""
            else -> ""
        }
        return "Status: $status"
    }

    private fun addCard(text: String, logJson: String? = null) {
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

        if (logJson != null) {
            card.setOnClickListener {
                startActivity(
                    android.content.Intent(this, AdminActivityLogDetailActivity::class.java).apply {
                        putExtra(AdminActivityLogDetailActivity.EXTRA_LOG_JSON, logJson)
                    }
                )
            }
        }

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
