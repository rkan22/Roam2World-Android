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

class AdminReportsActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val tokenStore by lazy { AuthTokenStore(this) }

    private lateinit var subtitleText: TextView
    private lateinit var refreshButton: Button
    private lateinit var listContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_reports)

        subtitleText = findViewById(R.id.adminReportsSubtitleText)
        refreshButton = findViewById(R.id.adminReportsRefreshButton)
        listContainer = findViewById(R.id.adminReportsListContainer)

        refreshButton.setOnClickListener { loadReports() }
        loadReports()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun loadReports() {
        subtitleText.text = "Loading reports..."
        listContainer.removeAllViews()
        addCard("⏳ Loading reports...\\n\\nPreparing revenue, order, and operational summaries.")

        scope.launch {
            val session = withContext(Dispatchers.IO) { tokenStore.getSession() }

            if (session == null || JwtUtils.isExpired(session.accessToken)) {
                redirectToLogin()
                return@launch
            }

            val result = withContext(Dispatchers.IO) {
                runCatching { fetchAdminReports(session.authorizationHeader) }
            }

            result.onSuccess { response ->
                renderReports(response)
            }.onFailure { error ->
                subtitleText.text = "Reports unavailable"
                Toast.makeText(
                    this@AdminReportsActivity,
                    error.message ?: "Reports API error",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun fetchAdminReports(authorizationHeader: String): JSONObject {
        val url = URL("https://roam2world-panels-backend.onrender.com/api/v1/mobile/admin/reports/")
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

    private fun renderReports(response: JSONObject) {
        val data = response.optJSONObject("data") ?: response
        val summary = data.optJSONObject("summary") ?: JSONObject()

        subtitleText.text = "Admin reports"

        addCard(
            "Summary\n\n" +
                "Users: ${summary.optInt("users_total", 0)}\n" +
                "Resellers: ${summary.optInt("resellers_total", 0)}\n" +
                "Dealers: ${summary.optInt("dealers_total", 0)}\n" +
                "Orders: ${summary.optInt("orders_total", 0)}\n" +
                "Total Sales: ${summary.optString("total_sales", "0.00")} ${summary.optString("currency", "USD")}\n" +
                "eSIMs: ${summary.optInt("esims_total", 0)}\n" +
                "Plans: ${summary.optInt("plans_total", 0)}\n" +
                "Active Plans: ${summary.optInt("active_plans", 0)}"
        )

        val byStatus = data.optJSONArray("orders_by_status")
        if (byStatus != null && byStatus.length() > 0) {
            val text = StringBuilder("Orders by Status\n\n")
            for (i in 0 until byStatus.length()) {
                val row = byStatus.optJSONObject(i) ?: continue
                text.append(row.optString("status", "-"))
                    .append(": ")
                    .append(row.optInt("count", 0))
                    .append(" / ")
                    .append(row.optString("sales", "0.00"))
                    .append("\n")
            }
            addCard(text.toString().trim())
        }

        val bySource = data.optJSONArray("orders_by_source")
        if (bySource != null && bySource.length() > 0) {
            val text = StringBuilder("Orders by Source\n\n")
            for (i in 0 until bySource.length()) {
                val row = bySource.optJSONObject(i) ?: continue
                text.append(row.optString("source", "-"))
                    .append(": ")
                    .append(row.optInt("count", 0))
                    .append(" / ")
                    .append(row.optString("sales", "0.00"))
                    .append("\n")
            }
            addCard(text.toString().trim())
        }

        val topCountries = data.optJSONArray("top_countries")
        if (topCountries != null && topCountries.length() > 0) {
            val text = StringBuilder("Top Countries\n\n")
            for (i in 0 until topCountries.length()) {
                val row = topCountries.optJSONObject(i) ?: continue
                text.append(row.optString("country", "-"))
                    .append(": ")
                    .append(row.optInt("count", 0))
                    .append(" / ")
                    .append(row.optString("sales", "0.00"))
                    .append("\n")
            }
            addCard(text.toString().trim())
        }

        val recentOrders = data.optJSONArray("recent_orders")
        if (recentOrders != null && recentOrders.length() > 0) {
            val text = StringBuilder("Recent Orders\n\n")
            for (i in 0 until recentOrders.length()) {
                val row = recentOrders.optJSONObject(i) ?: continue
                text.append(row.optString("order_number", "-"))
                    .append(" - ")
                    .append(row.optString("status", "-"))
                    .append("\n")
                    .append(row.optString("product_name", "-"))
                    .append(" / ")
                    .append(row.optString("total_amount", "0.00"))
                    .append("\n\n")
            }
            addCard(text.toString().trim())
        }
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
