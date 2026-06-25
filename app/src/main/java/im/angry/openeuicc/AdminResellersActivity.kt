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

class AdminResellersActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val tokenStore by lazy { AuthTokenStore(this) }

    private lateinit var subtitleText: TextView
    private lateinit var refreshButton: Button
    private lateinit var listContainer: LinearLayout

    private var cachedResellers = JSONArray()
    private var currentSearchQuery = ""
    private var currentStatusFilter = "all"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_resellers)

        subtitleText = findViewById(R.id.adminResellersSubtitleText)
        refreshButton = findViewById(R.id.adminResellersRefreshButton)
        listContainer = findViewById(R.id.adminResellersListContainer)

        refreshButton.setOnClickListener { loadResellers() }

        loadResellers()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun loadResellers() {
        subtitleText.text = "Loading reseller accounts..."
        listContainer.removeAllViews()
        addCard("Loading Resellers\\n\\nRetrieving partner account status, balances, and markup data.")

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
                    fetchAdminResellers(session.authorizationHeader)
                }
            }

            result
                .onSuccess { response ->
                    renderResellers(response)
                }
                .onFailure { error ->
                    subtitleText.text = "Resellers unavailable"
                    Toast.makeText(
                        this@AdminResellersActivity,
                        error.message ?: "Resellers API error",
                        Toast.LENGTH_LONG
                    ).show()
                }
        }
    }


    private fun fetchAdminResellers(authorizationHeader: String): JSONObject {
        val url = URL("https://roam2world-panels-backend.onrender.com/api/v1/mobile/admin/resellers/")
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


    private fun renderResellers(response: JSONObject) {
        val data = response.optJSONObject("data") ?: response
        cachedResellers = data.optJSONArray("resellers") ?: JSONArray()
        renderFilteredResellers()
    }

    private fun renderFilteredResellers() {
        listContainer.removeAllViews()
        addFilterControls()

        var visibleCount = 0

        if (cachedResellers.length() == 0) {
            subtitleText.text = "0 reseller account(s)"
            addCard("No Resellers Found\n\nNew reseller accounts will appear here after onboarding.")
            return
        }

        for (i in 0 until cachedResellers.length()) {
            val reseller = cachedResellers.optJSONObject(i) ?: continue

            if (!matchesFilters(reseller)) {
                continue
            }

            visibleCount++

            val name = reseller.optString("name", "-")
            val email = reseller.optString("email", "-")
            val credit = reseller.optString("current_credit", "0.00")
            val creditLimit = reseller.optString("credit_limit", "0.00")
            val monthlySpent = reseller.optString("current_month_spent", "0.00")
            val monthlyLimit = reseller.optString("monthly_spend_limit", "0.00")
            val markup = reseller.optString("markup_percentage", "0.00")
            val active = reseller.optBoolean("is_active", false)
            val suspended = reseller.optBoolean("is_suspended", false)

            val status = when {
                suspended -> "Suspended"
                active -> "Active"
                else -> "Inactive"
            }

            addCard(
                "$name\n" +
                    "$email\n" +
                    statusBadge(status) + "\n\n" +
                    "Credit: $credit\n" +
                    "Credit Limit: $creditLimit\n" +
                    "Monthly Spend: $monthlySpent / $monthlyLimit\n" +
                    "Markup: $markup%\n\n" +
                    "Open reseller details",
                reseller.toString()
            )
        }

        subtitleText.text = "$visibleCount / ${cachedResellers.length()} reseller account(s)"

        if (visibleCount == 0) {
            addCard("No Matching Resellers\n\nClear filters or search by reseller name, email, or account id.")
        }
    }

    private fun matchesFilters(reseller: JSONObject): Boolean {
        val active = reseller.optBoolean("is_active", false)
        val suspended = reseller.optBoolean("is_suspended", false)

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
            reseller.optString("id", ""),
            reseller.optString("user_id", ""),
            reseller.optString("name", ""),
            reseller.optString("email", ""),
            reseller.optString("first_name", ""),
            reseller.optString("last_name", "")
        ).joinToString(" ").lowercase()

        return searchable.contains(query)
    }

    private fun addFilterControls() {
        addCard(
            "Search & Filters\n" +
                "Search: ${currentSearchQuery.ifBlank { "All" }}\n" +
                "Status: ${currentStatusFilter.replaceFirstChar { it.uppercase() }}"
        )

        val searchInput = EditText(this)
        searchInput.hint = "Search reseller name, email, id"
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
            renderFilteredResellers()
        }

        addSmallActionButton("Clear Filters") {
            currentSearchQuery = ""
            currentStatusFilter = "all"
            renderFilteredResellers()
        }

        val statuses = listOf("all", "active", "suspended", "inactive")

        statuses.forEach { status ->
            val label = if (status == "all") "All Statuses" else status.replaceFirstChar { it.uppercase() }
            addSmallActionButton(label) {
                currentStatusFilter = status
                renderFilteredResellers()
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
        return "Status: $status"
    }

    private fun addCard(text: String, resellerJson: String? = null) {
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
            resellerJson != null -> R.drawable.admin_icon_partners
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

        if (resellerJson != null) {
            card.setOnClickListener {
                startActivity(
                    Intent(this, AdminResellerDetailActivity::class.java).apply {
                        putExtra(AdminResellerDetailActivity.EXTRA_RESELLER_JSON, resellerJson)
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
