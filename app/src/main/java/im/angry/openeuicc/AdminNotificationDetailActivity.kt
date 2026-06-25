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

class AdminNotificationDetailActivity : Activity() {
    companion object {
        const val EXTRA_NOTIFICATION_JSON = "extra_notification_json"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val tokenStore by lazy { AuthTokenStore(this) }

    private lateinit var subtitleText: TextView
    private lateinit var refreshButton: Button
    private lateinit var listContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_notification_detail)

        subtitleText = findViewById(R.id.adminNotificationDetailSubtitleText)
        refreshButton = findViewById(R.id.adminNotificationDetailRefreshButton)
        listContainer = findViewById(R.id.adminNotificationDetailListContainer)

        refreshButton.text = "Close"
        refreshButton.setOnClickListener { finish() }

        val raw = intent.getStringExtra(EXTRA_NOTIFICATION_JSON)
        if (raw.isNullOrBlank()) {
            subtitleText.text = "No notification data"
            addCard("Notification data was not provided.")
            return
        }

        val notification = JSONObject(raw)
        render(notification)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun render(notification: JSONObject) {
        val id = notification.optString("id", "-")
        val title = notification.optString("title", notification.optString("subject", "-"))
        val message = notification.optString("message", notification.optString("body", "-"))
        val type = notification.optString("type", "-")
        val isRead = notification.optBoolean("is_read", false)
        val createdAt = notification.optString("created_at", "-")
        val updatedAt = notification.optString("updated_at", "-")

        subtitleText.text = "$title / $type"

        addCard(
            "Overview\n" +
                "ID: $id\n" +
                "Title: $title\n" +
                "Type: $type\n" +
                "Read: ${if (isRead) "Yes" else "No"}\n" +
                "Created: $createdAt\n" +
                "Updated: $updatedAt"
        )

        addCard(
            "Message\n" +
                message
        )

        if (isRead) {
            addActionButton("Mark as Unread", id, "unread")
        } else {
            addActionButton("Mark as Read", id, "read")
        }
    }

    private fun addActionButton(label: String, notificationId: String, action: String) {
        val button = Button(this)
        button.text = label
        button.textSize = 14f
        button.setTextColor(0xFFFFFFFF.toInt())
        button.setBackgroundResource(R.drawable.admin_primary_pill)
        button.setPadding(24, 14, 24, 14)
        button.setOnClickListener {
            updateNotificationReadState(notificationId, action)
        }

        val params = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        params.setMargins(0, 0, 0, 16)
        button.layoutParams = params
        listContainer.addView(button)
    }

    private fun updateNotificationReadState(notificationId: String, action: String) {
        if (notificationId.isBlank() || notificationId == "-") {
            Toast.makeText(this, "Missing notification id", Toast.LENGTH_LONG).show()
            return
        }

        scope.launch {
            val session = withContext(Dispatchers.IO) { tokenStore.getSession() }

            if (session == null || JwtUtils.isExpired(session.accessToken)) {
                redirectToLogin()
                return@launch
            }

            val result = withContext(Dispatchers.IO) {
                runCatching {
                    postNotificationAction(notificationId, action, session.authorizationHeader)
                }
            }

            result.onSuccess {
                Toast.makeText(
                    this@AdminNotificationDetailActivity,
                    "Notification updated",
                    Toast.LENGTH_LONG
                ).show()
                finish()
            }.onFailure { error ->
                Toast.makeText(
                    this@AdminNotificationDetailActivity,
                    error.message ?: "Notification action failed",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun postNotificationAction(
        notificationId: String,
        action: String,
        authorizationHeader: String
    ): JSONObject {
        val url = URL("https://roam2world-panels-backend.onrender.com/api/v1/mobile/notifications/$notificationId/$action/")
        val connection = url.openConnection() as HttpURLConnection

        return try {
            connection.requestMethod = "POST"
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", authorizationHeader)
            connection.doOutput = true
            connection.connectTimeout = 15000
            connection.readTimeout = 15000

            connection.outputStream.use { output ->
                output.write("{}".toByteArray(Charsets.UTF_8))
            }

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

    private fun redirectToLogin() {
        Toast.makeText(this, "Session expired. Please login again.", Toast.LENGTH_LONG).show()
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
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
