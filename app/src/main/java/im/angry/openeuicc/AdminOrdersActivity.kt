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

class AdminOrdersActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val tokenStore by lazy { AuthTokenStore(this) }

    private lateinit var subtitleText: TextView
    private lateinit var refreshButton: Button
    private lateinit var listContainer: LinearLayout

    private var cachedOrders = JSONArray()
    private var currentSearchQuery = ""
    private var currentStatusFilter = "all"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_orders)

        subtitleText = findViewById(R.id.adminOrdersSubtitleText)
        refreshButton = findViewById(R.id.adminOrdersRefreshButton)
        listContainer = findViewById(R.id.adminOrdersListContainer)

        refreshButton.setOnClickListener { loadOrders() }

        loadOrders()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun loadOrders() {
        subtitleText.text = "Loading orders..."
        listContainer.removeAllViews()
        addCard("Loading orders...\\n\\nFetching the latest order activity.")

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
                    fetchAdminOrders(session.authorizationHeader)
                }
            }

            result
                .onSuccess { response ->
                    renderOrders(response)
                }
                .onFailure { error ->
                    subtitleText.text = "Orders unavailable"
                    Toast.makeText(
                        this@AdminOrdersActivity,
                        error.message ?: "Orders API error",
                        Toast.LENGTH_LONG
                    ).show()
                }
        }
    }


    private fun fetchAdminOrders(authorizationHeader: String): JSONObject {
        val url = URL("https://roam2world-panels-backend.onrender.com/api/v1/mobile/admin/orders/")
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


    private fun renderOrders(response: JSONObject) {
        val data = response.optJSONObject("data") ?: response
        cachedOrders = data.optJSONArray("orders") ?: JSONArray()
        renderFilteredOrders()
    }

    private fun renderFilteredOrders() {
        listContainer.removeAllViews()
        addFilterControls()

        var visibleCount = 0

        if (cachedOrders.length() == 0) {
            subtitleText.text = "0 order(s)"
            addCard("No orders found.\n\nNew orders will appear here once customers start placing orders.")
            return
        }

        for (i in 0 until cachedOrders.length()) {
            val order = cachedOrders.optJSONObject(i) ?: continue

            if (!matchesFilters(order)) {
                continue
            }

            visibleCount++

            val name = order.optString("name", "-")
            val email = order.optString("email", "-")
            val orderNumber = order.optString("order_number", "-")
            val status = order.optString("status", "-")
            val source = order.optString("order_source", "-")
            val type = order.optString("order_type", "-")
            val product = order.optString("product_name", "-")
            val quantity = order.optInt("quantity", 0)
            val total = order.optString("total_amount", "0.00")
            val customer = order.optString("customer_name", "-")
            val resellerEmail = order.optString("reseller_email", "")
            val dealerEmail = order.optString("dealer_email", "")
            val country = order.optString("delivery_country", "")

            addCard(
                "$orderNumber\n" +
                    "$product\n" +
                    statusBadge(status) + "\n\n" +
                    "Customer: $customer\n" +
                    "Name: $name\n" +
                    "Email: $email\n" +
                    "Amount: $total\n" +
                    "Quantity: $quantity\n" +
                    "Channel: $source / $type\n" +
                    "Reseller: ${resellerEmail.ifBlank { "-" }}\n" +
                    "Dealer: ${dealerEmail.ifBlank { "-" }}\n" +
                    "Country: ${country.ifBlank { "-" }}\n\n" +
                    "Tap to open details →",
                order.toString()
            )
        }

        subtitleText.text = "$visibleCount / ${cachedOrders.length()} order(s)"

        if (visibleCount == 0) {
            addCard("No orders match the current filter.\n\nTry clearing filters or searching with another order number, email, customer, or product.")
        }
    }

    private fun matchesFilters(order: JSONObject): Boolean {
        val status = order.optString("status", "").lowercase()

        if (currentStatusFilter != "all" && status != currentStatusFilter) {
            return false
        }

        val query = currentSearchQuery.trim().lowercase()
        if (query.isBlank()) {
            return true
        }

        val searchable = listOf(
            order.optString("id", ""),
            order.optString("order_id", ""),
            order.optString("order_number", ""),
            order.optString("name", ""),
            order.optString("email", ""),
            order.optString("customer_name", ""),
            order.optString("product_name", ""),
            order.optString("reseller_email", ""),
            order.optString("dealer_email", ""),
            order.optString("delivery_country", ""),
            order.optString("order_source", ""),
            order.optString("order_type", "")
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
        searchInput.hint = "Search order, email, customer, product"
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
            renderFilteredOrders()
        }

        addSmallActionButton("Clear Filters") {
            currentSearchQuery = ""
            currentStatusFilter = "all"
            renderFilteredOrders()
        }

        val statuses = listOf(
            "all",
            "pending",
            "confirmed",
            "processing",
            "dispatched",
            "delivered",
            "completed",
            "cancelled"
        )

        statuses.forEach { status ->
            val label = if (status == "all") "All Statuses" else status.replaceFirstChar { it.uppercase() }
            addSmallActionButton(label) {
                currentStatusFilter = status
                renderFilteredOrders()
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

    private fun addCard(text: String, orderJson: String? = null) {
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

        if (orderJson != null) {
            card.setOnClickListener {
                startActivity(
                    Intent(this, AdminOrderDetailActivity::class.java).apply {
                        putExtra(AdminOrderDetailActivity.EXTRA_ORDER_JSON, orderJson)
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
