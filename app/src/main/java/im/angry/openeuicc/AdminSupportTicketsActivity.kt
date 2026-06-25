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

class AdminSupportTicketsActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val tokenStore by lazy { AuthTokenStore(this) }

    private lateinit var subtitleText: TextView
    private lateinit var refreshButton: Button
    private lateinit var listContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_support_tickets)

        subtitleText = findViewById(R.id.adminSupportTicketsSubtitleText)
        refreshButton = findViewById(R.id.adminSupportTicketsRefreshButton)
        listContainer = findViewById(R.id.adminSupportTicketsListContainer)

        refreshButton.setOnClickListener { loadSupportTickets() }
        loadSupportTickets()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun loadSupportTickets() {
        subtitleText.text = "Loading support tickets..."
        listContainer.removeAllViews()
        addCard("Loading support tickets...\\n\\nFetching open and recent support requests.")

        scope.launch {
            val session = withContext(Dispatchers.IO) { tokenStore.getSession() }

            if (session == null || JwtUtils.isExpired(session.accessToken)) {
                redirectToLogin()
                return@launch
            }

            val result = withContext(Dispatchers.IO) {
                runCatching { fetchSupportTickets(session.authorizationHeader) }
            }

            result.onSuccess { response ->
                renderSupportTickets(response)
            }.onFailure { error ->
                subtitleText.text = "Support tickets unavailable"
                Toast.makeText(
                    this@AdminSupportTicketsActivity,
                    error.message ?: "Support Tickets API error",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun fetchSupportTickets(authorizationHeader: String): JSONObject {
        val url = URL("https://roam2world-panels-backend.onrender.com/api/v1/mobile/admin/support-tickets/")
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

    private fun renderSupportTickets(response: JSONObject) {
        val data = response.optJSONObject("data") ?: response
        val count = data.optInt("count", 0)
        val tickets = data.optJSONArray("tickets")

        subtitleText.text = "$count support ticket(s)"

        addCard(
            "Summary\n\n" +
                "Open: ${data.optInt("open_count", 0)}\n" +
                "In Progress: ${data.optInt("in_progress_count", 0)}\n" +
                "Resolved: ${data.optInt("resolved_count", 0)}\n" +
                "Closed: ${data.optInt("closed_count", 0)}"
        )

        if (tickets == null || tickets.length() == 0) {
            addCard("No support tickets found.\n\nClient support tickets will appear here. B2B reseller/dealer tickets require the next support model upgrade.")
            return
        }

        for (i in 0 until tickets.length()) {
            val item = tickets.optJSONObject(i) ?: continue

            val subject = item.optString("subject", "-")
            val description = item.optString("description", "-")
            val status = item.optString("status", "-")
            val clientName = item.optString("client_name", "-")
            val clientEmail = item.optString("client_email", "-")
            val assignedTo = item.optString("assigned_to_name", "")
            val createdAt = item.optString("created_at", "-")

            addCard(
                "$subject\n" +
                    statusBadge(status) + "\n\n" +
                    "Client: $clientName\n" +
                    "Email: $clientEmail\n" +
                    "Assigned: ${if (assignedTo.isBlank()) "-" else assignedTo}\n" +
                    "Created: $createdAt\n\n" +
                    description.take(120) + if (description.length > 120) "..." else "" + "\n\n" +
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

    private fun addCard(text: String, ticketJson: String? = null) {
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

        if (ticketJson != null) {
            card.setOnClickListener {
                startActivity(
                    android.content.Intent(this, AdminSupportTicketDetailActivity::class.java).apply {
                        putExtra(AdminSupportTicketDetailActivity.EXTRA_TICKET_JSON, ticketJson)
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
