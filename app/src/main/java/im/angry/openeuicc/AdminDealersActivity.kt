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
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

class AdminDealersActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val tokenStore by lazy { AuthTokenStore(this) }

    private lateinit var subtitleText: TextView
    private lateinit var refreshButton: Button
    private lateinit var listContainer: LinearLayout

    private var cachedDealers = JSONArray()
    private var currentSearchQuery = ""
    private var currentStatusFilter = "all"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_dealers)

        subtitleText = findViewById(R.id.adminDealersSubtitleText)
        refreshButton = findViewById(R.id.adminDealersRefreshButton)
        listContainer = findViewById(R.id.adminDealersListContainer)

        refreshButton.setOnClickListener { loadDealers() }

        loadDealers()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun loadDealers() {
        subtitleText.text = "Loading dealer accounts..."
        listContainer.removeAllViews()
        addCard("Loading dealers...\\n\\nFetching dealer account status.")

        scope.launch {
            val session = withContext(Dispatchers.IO) {
                tokenStore.getSession()
            }

            if (session == null || JwtUtils.isExpired(session.accessToken)) {
                redirectToLogin()
                return@launch
            }

            val result = withContext(Dispatchers.IO) {
                runCatching {
                    fetchAdminDealers(session.authorizationHeader)
                }
            }

            result
                .onSuccess { response ->
                    renderDealers(response)
                }
                .onFailure { error ->
                    subtitleText.text = "Dealers unavailable"
                    Toast.makeText(
                        this@AdminDealersActivity,
                        error.message ?: "Dealers API error",
                        Toast.LENGTH_LONG
                    ).show()
                }
        }
    }


    private fun fetchAdminDealers(authorizationHeader: String): JSONObject {
        val url = URL("https://roam2world-panels-backend.onrender.com/api/v1/mobile/admin/dealers/")
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


    private fun renderDealers(response: JSONObject) {
        val data = response.optJSONObject("data") ?: response
        cachedDealers = data.optJSONArray("dealers") ?: JSONArray()
        renderFilteredDealers()
    }

    private fun renderFilteredDealers() {
        listContainer.removeAllViews()
        addFilterControls()

        var visibleCount = 0

        if (cachedDealers.length() == 0) {
            subtitleText.text = "0 dealer account(s)"
            addCard("No dealers found.\n\nDealer accounts will appear here once resellers create or receive dealers.")
            return
        }

        for (i in 0 until cachedDealers.length()) {
            val dealer = cachedDealers.optJSONObject(i) ?: continue

            if (!matchesFilters(dealer)) {
                continue
            }

            visibleCount++

            val name = dealer.optString("name", "-")
            val email = dealer.optString("email", "-")
            val balance = dealer.optString("current_balance", "0.00")
            val totalAllocated = dealer.optString("total_allocated", "0.00")
            val totalSpent = dealer.optString("total_spent", "0.00")
            val markup = dealer.optString("markup_percentage", "0.00")
            val resellerEmail = dealer.optString("reseller_email", "-")
            val active = dealer.optBoolean("is_active", false)
            val suspended = dealer.optBoolean("is_suspended", false)

            val status = when {
                suspended -> "Suspended"
                active -> "Active"
                else -> "Inactive"
            }

            addCard(
                "$name\n" +
                    "$email\n" +
                    statusBadge(status) + "\n\n" +
                    "Parent Reseller: $resellerEmail\n" +
                    "Balance: $balance\n" +
                    "Allocated: $totalAllocated\n" +
                    "Spent: $totalSpent\n" +
                    "Markup: $markup%\n\n" +
                    "Tap to open details →",
                dealer.toString()
            )
        }

        subtitleText.text = "$visibleCount / ${cachedDealers.length()} dealer account(s)"

        if (visibleCount == 0) {
            addCard("No dealers match the current filter.\n\nTry clearing filters or searching with another dealer or reseller email.")
        }
    }

    private fun matchesFilters(dealer: JSONObject): Boolean {
        val active = dealer.optBoolean("is_active", false)
        val suspended = dealer.optBoolean("is_suspended", false)

        val status = when {
            suspended -> "suspended"
            active -> "active"
            else -> "inactive"
        }

        if (currentStatusFilter != "all" && status != currentStatusFilter) {
            return false
        }

        val query = currentSearchQuery.trim().lowercase()
        if (query.isBlank()) {
            return true
        }

        val searchable = listOf(
            dealer.optString("id", ""),
            dealer.optString("user_id", ""),
            dealer.optString("name", ""),
            dealer.optString("email", ""),
            dealer.optString("first_name", ""),
            dealer.optString("last_name", ""),
            dealer.optString("reseller_email", "")
        ).joinToString(" ").lowercase()

        return searchable.contains(query)
    }

    private fun addFilterControls() {
        addCard(
            "Filters\n" +
                "Search: ${currentSearchQuery.ifBlank { "All" }}\n" +
                "Status: ${currentStatusFilter.replaceFirstChar { it.uppercase() }}"
        )

        val searchInput = EditText(this)
        searchInput.hint = "Search dealer name, email, reseller"
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
            renderFilteredDealers()
        }

        addSmallActionButton("Clear Filters") {
            currentSearchQuery = ""
            currentStatusFilter = "all"
            renderFilteredDealers()
        }

        val statuses = listOf("all", "active", "suspended", "inactive")

        statuses.forEach { status ->
            val label = if (status == "all") "All Statuses" else status.replaceFirstChar { it.uppercase() }
            addSmallActionButton(label) {
                currentStatusFilter = status
                renderFilteredDealers()
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

    private fun addCard(text: String, dealerJson: String? = null) {
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

        if (dealerJson != null) {
            card.setOnClickListener {
                startActivity(
                    Intent(this, AdminDealerDetailActivity::class.java).apply {
                        putExtra(AdminDealerDetailActivity.EXTRA_DEALER_JSON, dealerJson)
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
