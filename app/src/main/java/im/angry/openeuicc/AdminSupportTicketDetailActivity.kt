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

class AdminSupportTicketDetailActivity : Activity() {
    companion object {
        const val EXTRA_TICKET_JSON = "extra_ticket_json"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val tokenStore by lazy { AuthTokenStore(this) }

    private lateinit var subtitleText: TextView
    private lateinit var refreshButton: Button
    private lateinit var listContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_support_ticket_detail)

        subtitleText = findViewById(R.id.adminSupportTicketDetailSubtitleText)
        refreshButton = findViewById(R.id.adminSupportTicketDetailRefreshButton)
        listContainer = findViewById(R.id.adminSupportTicketDetailListContainer)

        refreshButton.text = "Close"
        refreshButton.setOnClickListener { finish() }

        val raw = intent.getStringExtra(EXTRA_TICKET_JSON)
        if (raw.isNullOrBlank()) {
            subtitleText.text = "No ticket data"
            addCard("Support ticket data was not provided.")
            return
        }

        val ticket = JSONObject(raw)
        render(ticket)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun render(ticket: JSONObject) {
        listContainer.removeAllViews()

        val id = ticket.optString("id", "-")
        val subject = ticket.optString("subject", "-")
        val description = ticket.optString("description", "-")
        val status = ticket.optString("status", "-")
        val clientName = ticket.optString("client_name", "-")
        val clientEmail = ticket.optString("client_email", "-")
        val assignedTo = ticket.optString("assigned_to_name", "")
        val createdAt = ticket.optString("created_at", "-")
        val updatedAt = ticket.optString("updated_at", "-")
        val resolvedAt = ticket.optString("resolved_at", "")

        subtitleText.text = "#$id / $status"

        addCard(
            "Overview\n" +
                "Ticket ID: $id\n" +
                "Subject: $subject\n" +
                statusBadge(status) + "\n" +
                "Created: $createdAt\n" +
                "Updated: $updatedAt\n" +
                "Resolved: ${if (resolvedAt.isBlank() || resolvedAt == "null") "-" else resolvedAt}"
        )

        addCard(
            "Client\n" +
                "Name: $clientName\n" +
                "Email: $clientEmail"
        )

        addCard(
            "Assignment\n" +
                "Assigned To: ${if (assignedTo.isBlank()) "-" else assignedTo}"
        )

        addCard(
            "Description\n" +
                description
        )

        addActionButton("Close Ticket", id, "closed")
        addActionButton("Reopen Ticket", id, "open")
        addActionButton("Set In Progress", id, "in_progress")
    }

    private fun addActionButton(label: String, ticketId: String, status: String) {
        val button = Button(this)
        button.text = label
        button.textSize = 14f
        button.setTextColor(0xFFFFFFFF.toInt())
        button.setBackgroundResource(R.drawable.admin_primary_pill)
        button.setPadding(24, 14, 24, 14)
        button.setOnClickListener {
            updateTicketStatus(ticketId, status)
        }

        val params = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        params.setMargins(0, 0, 0, 16)
        button.layoutParams = params

        listContainer.addView(button)
    }

    private fun updateTicketStatus(ticketId: String, status: String) {
        scope.launch {
            val session = withContext(Dispatchers.IO) { tokenStore.getSession() }

            if (session == null || JwtUtils.isExpired(session.accessToken)) {
                redirectToLogin()
                return@launch
            }

            val result = withContext(Dispatchers.IO) {
                runCatching {
                    postTicketStatus(ticketId, status, session.authorizationHeader)
                }
            }

            result.onSuccess { response ->
                Toast.makeText(
                    this@AdminSupportTicketDetailActivity,
                    response.optString("message", "Ticket updated"),
                    Toast.LENGTH_LONG
                ).show()
                finish()
            }.onFailure { error ->
                Toast.makeText(
                    this@AdminSupportTicketDetailActivity,
                    error.message ?: "Ticket status update failed",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun postTicketStatus(
        ticketId: String,
        status: String,
        authorizationHeader: String
    ): JSONObject {
        val url = URL("https://roam2world-panels-backend.onrender.com/api/v1/clients/support-tickets/$ticketId/status/")
        val connection = url.openConnection() as HttpURLConnection

        return try {
            val body = JSONObject()
                .put("status", status)
                .toString()

            connection.requestMethod = "POST"
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", authorizationHeader)
            connection.doOutput = true
            connection.connectTimeout = 15000
            connection.readTimeout = 15000

            connection.outputStream.use { output ->
                output.write(body.toByteArray(Charsets.UTF_8))
            }

            val stream = if (connection.responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }

            val responseBody = stream.bufferedReader().use { it.readText() }

            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("HTTP ${connection.responseCode}: $responseBody")
            }

            JSONObject(responseBody)
        } finally {
            connection.disconnect()
        }
    }

    private fun redirectToLogin() {
        Toast.makeText(this, "Session expired. Please login again.", Toast.LENGTH_LONG).show()
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
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
}
