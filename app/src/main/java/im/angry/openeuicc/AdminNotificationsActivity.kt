package im.angry.openeuicc

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
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
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class AdminNotificationsActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val tokenStore by lazy { AuthTokenStore(this) }

    private lateinit var subtitleText: TextView
    private lateinit var refreshButton: Button
    private lateinit var listContainer: LinearLayout

    private var cachedNotifications = JSONArray()
    private var currentSearchQuery = ""
    private var currentReadFilter = "all"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_notifications)

        subtitleText = findViewById(R.id.adminNotificationsSubtitleText)
        refreshButton = findViewById(R.id.adminNotificationsRefreshButton)
        listContainer = findViewById(R.id.adminNotificationsListContainer)

        refreshButton.setOnClickListener { loadNotifications() }
        loadNotifications()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun loadNotifications() {
        subtitleText.text = "Loading notifications..."
        listContainer.removeAllViews()
        addCard("Loading Notifications\\n\\nRetrieving recent account, system, and operational alerts.")

        scope.launch {
            val session = withContext(Dispatchers.IO) { tokenStore.getSession() }

            if (session == null || JwtUtils.isExpired(session.accessToken)) {
                redirectToLogin()
                return@launch
            }

            val result = withContext(Dispatchers.IO) {
                runCatching { fetchNotifications(session.authorizationHeader) }
            }

            result.onSuccess { response ->
                renderNotifications(response)
            }.onFailure { error ->
                subtitleText.text = "Notifications unavailable"
                Toast.makeText(
                    this@AdminNotificationsActivity,
                    error.message ?: "Notifications API error",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun fetchNotifications(authorizationHeader: String): JSONObject {
        val url = URL("https://roam2world-panels-backend.onrender.com/api/v1/mobile/notifications/")
        val connection = url.openConnection() as HttpURLConnection

        return try {
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Authorization", authorizationHeader)
            connection.connectTimeout = 15000
            connection.readTimeout = 15000

            val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
            val body = stream.bufferedReader().use { it.readText() }

            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("HTTP ${connection.responseCode}: $body")
            }

            JSONObject(body)
        } finally {
            connection.disconnect()
        }
    }

    private fun renderNotifications(response: JSONObject) {
        val data = response.optJSONObject("data") ?: response
        cachedNotifications = data.optJSONArray("notifications")
            ?: response.optJSONArray("notifications")
            ?: JSONArray()

        renderFilteredNotifications()
    }

    private fun renderFilteredNotifications() {
        listContainer.removeAllViews()
        addFilterControls()

        var visibleCount = 0

        if (cachedNotifications.length() == 0) {
            subtitleText.text = "0 notification(s)"
            addCard("No Notifications Found\n\nSystem and account notifications will appear here.")
            return
        }

        for (i in 0 until cachedNotifications.length()) {
            val item = cachedNotifications.optJSONObject(i) ?: continue

            if (!matchesFilters(item)) {
                continue
            }

            visibleCount++

            val title = item.optString("title", item.optString("subject", "-"))
            val message = item.optString("message", item.optString("body", "-"))
            val type = item.optString("type", "-")
            val isRead = item.optBoolean("is_read", false)
            val createdAt = item.optString("created_at", "-")

            addCard(
                "$title\n" +
                    "Type: $type\n" +
                    "Read: ${if (isRead) "Yes" else "No"}\n" +
                    "Created: $createdAt\n\n" +
                    message.take(140) + if (message.length > 140) "..." else "" + "\n\n" +
                    "Open notification details",
                item.toString()
            )
        }

        subtitleText.text = "$visibleCount / ${cachedNotifications.length()} notification(s)"

        if (visibleCount == 0) {
            addCard("No Matching Notifications\n\nClear filters or search by title, message, or notification type.")
        }
    }

    private fun matchesFilters(item: JSONObject): Boolean {
        val isRead = item.optBoolean("is_read", false)

        if (currentReadFilter == "read" && !isRead) {
            return false
        }

        if (currentReadFilter == "unread" && isRead) {
            return false
        }

        val query = currentSearchQuery.trim().lowercase()
        if (query.isBlank()) {
            return true
        }

        val searchable = listOf(
            item.optString("id", ""),
            item.optString("title", ""),
            item.optString("subject", ""),
            item.optString("message", ""),
            item.optString("body", ""),
            item.optString("type", "")
        ).joinToString(" ").lowercase()

        return searchable.contains(query)
    }

    private fun addFilterControls() {
        addCard(
            "Search & Filters\n" +
                "Search: ${currentSearchQuery.ifBlank { "All" }}\n" +
                "Read: ${currentReadFilter.replaceFirstChar { it.uppercase() }}"
        )

        val searchInput = EditText(this)
        searchInput.hint = "Search title, message, type"
        searchInput.setText(currentSearchQuery)
        searchInput.textSize = 15f
        searchInput.setTextColor(0xFF07133D.toInt())
        searchInput.setHintTextColor(0xFF6B7280.toInt())
        searchInput.setBackgroundResource(R.drawable.admin_card_background)
        searchInput.setPadding(28, 18, 28, 18)

        val inputParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        inputParams.setMargins(0, 0, 0, 12)
        searchInput.layoutParams = inputParams
        listContainer.addView(searchInput)

        addSmallActionButton("Search") {
            currentSearchQuery = searchInput.text.toString()
            renderFilteredNotifications()
        }

        addSmallActionButton("Clear Filters") {
            currentSearchQuery = ""
            currentReadFilter = "all"
            renderFilteredNotifications()
        }

        val filters = listOf("all", "unread", "read")

        filters.forEach { filter ->
            val label = when (filter) {
                "all" -> "All Notifications"
                "unread" -> "Unread"
                "read" -> "Read"
                else -> filter
            }

            addSmallActionButton(label) {
                currentReadFilter = filter
                renderFilteredNotifications()
            }
        }
    }

    private fun addSmallActionButton(label: String, onClick: () -> Unit) {
        val button = Button(this)
        button.text = label
        button.textSize = 13f
        button.setTextColor(0xFFFFFFFF.toInt())
        button.setBackgroundResource(R.drawable.admin_primary_pill)
        button.setPadding(20, 10, 20, 10)
        button.setOnClickListener { onClick() }

        val params = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        params.setMargins(0, 0, 0, 10)
        button.layoutParams = params
        listContainer.addView(button)
    }

    private fun addCard(text: String, notificationJson: String? = null) {
        val card = TextView(this)
        card.text = text
        card.textSize = 14.5f
        card.setTextColor(0xFF081A44.toInt())
        card.setBackgroundResource(R.drawable.admin_section_card)
        card.elevation = 4f
        card.setPadding(30, 26, 30, 26)
        card.setLineSpacing(4f, 1.05f)

        val iconRes = when {
            text.startsWith("Filters") -> R.drawable.admin_icon_settings
            text.startsWith("Loading") -> R.drawable.admin_icon_health
            text.startsWith("No ") -> R.drawable.admin_icon_doc
            notificationJson != null -> R.drawable.admin_icon_notifications
            else -> R.drawable.admin_icon_doc
        }

        card.setCompoundDrawablesWithIntrinsicBounds(iconRes, 0, 0, 0)
        card.compoundDrawablePadding = 18

        val params = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        params.setMargins(0, 0, 0, 18)
        card.layoutParams = params

        if (notificationJson != null) {
            card.setOnClickListener {
                startActivity(
                    Intent(this, AdminNotificationDetailActivity::class.java).apply {
                        putExtra(AdminNotificationDetailActivity.EXTRA_NOTIFICATION_JSON, notificationJson)
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
