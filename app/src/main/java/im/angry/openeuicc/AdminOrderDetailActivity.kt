package im.angry.openeuicc

import android.app.Activity
import android.app.AlertDialog
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

class AdminOrderDetailActivity : Activity() {
    companion object {
        const val EXTRA_ORDER_JSON = "extra_order_json"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val tokenStore by lazy { AuthTokenStore(this) }

    private lateinit var subtitleText: TextView
    private lateinit var refreshButton: Button
    private lateinit var listContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_order_detail)

        subtitleText = findViewById(R.id.adminOrderDetailSubtitleText)
        refreshButton = findViewById(R.id.adminOrderDetailRefreshButton)
        listContainer = findViewById(R.id.adminOrderDetailListContainer)

        refreshButton.text = "Close"
        refreshButton.setOnClickListener { finish() }

        val raw = intent.getStringExtra(EXTRA_ORDER_JSON)
        if (raw.isNullOrBlank()) {
            subtitleText.text = "No order data"
            addCard("Order data was not provided.")
            return
        }

        val order = JSONObject(raw)
        render(order)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun render(order: JSONObject) {
        val id = order.optString("id", order.optString("order_id", "-"))
        val orderNumber = order.optString("order_number", "-")
        val status = order.optString("status", "-")
        subtitleText.text = "$orderNumber / $status"

        val name = order.optString("name", "-")
        val email = order.optString("email", "-")
        val source = order.optString("order_source", "-")
        val type = order.optString("order_type", "-")
        val product = order.optString("product_name", "-")
        val quantity = order.optInt("quantity", 0)
        val total = order.optString("total_amount", "0.00")
        val customer = order.optString("customer_name", "-")
        val resellerEmail = order.optString("reseller_email", "")
        val dealerEmail = order.optString("dealer_email", "")
        val country = order.optString("delivery_country", "")
        val createdAt = order.optString("created_at", "-")
        val updatedAt = order.optString("updated_at", "-")

        addCard(
            "Overview\n" +
                "Order: $orderNumber\n" +
                statusBadge(status) + "\n" +
                "Created: $createdAt\n" +
                "Updated: $updatedAt"
        )

        addCard(
            "Customer\n" +
                "Name: ${name.ifBlank { "-" }}\n" +
                "Email: ${email.ifBlank { "-" }}\n" +
                "Customer: ${customer.ifBlank { "-" }}\n" +
                "Country: ${country.ifBlank { "-" }}"
        )

        addCard(
            "Product & Payment\n" +
                "Product: ${product.ifBlank { "-" }}\n" +
                "Quantity: $quantity\n" +
                "Total: $total"
        )

        addCard(
            "Channel\n" +
                "Source: $source\n" +
                "Type: $type\n" +
                "Reseller: ${resellerEmail.ifBlank { "-" }}\n" +
                "Dealer: ${dealerEmail.ifBlank { "-" }}"
        )

        addOrderStatusActions(id, status)
    }

    private fun addOrderStatusActions(orderId: String, currentStatus: String) {
        val nextStatuses = when (currentStatus.lowercase()) {
            "pending" -> listOf("confirmed", "cancelled")
            "confirmed" -> listOf("processing", "cancelled")
            "processing" -> listOf("dispatched", "cancelled")
            "dispatched" -> listOf("delivered", "cancelled")
            "delivered" -> listOf("activated", "completed")
            "activated" -> listOf("completed")
            else -> emptyList()
        }

        if (nextStatuses.isEmpty()) {
            addCard("Status Actions\nNo next action available for this order.")
            return
        }

        addCard("Status Actions\nCurrent: $currentStatus")

        nextStatuses.forEach { nextStatus ->
            addActionButton("Set ${nextStatus.replaceFirstChar { it.uppercase() }}", orderId, nextStatus)
        }
    }

    private fun addActionButton(label: String, orderId: String, status: String) {
        val button = Button(this)
        button.text = label
        button.textSize = 14f
        button.setTextColor(0xFFFFFFFF.toInt())
        button.setBackgroundResource(actionButtonBackground(label))
        button.setPadding(24, 14, 24, 14)
        button.setOnClickListener {
            confirmAction(
                title = label,
                message = "Change order status to $status?",
            ) {
                updateOrderStatus(orderId, status)
            }
        }

        val params = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        params.setMargins(0, 0, 0, 16)
        button.layoutParams = params
        listContainer.addView(button)
    }

    private fun updateOrderStatus(orderId: String, status: String) {
        if (orderId.isBlank() || orderId == "-") {
            Toast.makeText(this, "Missing order id", Toast.LENGTH_LONG).show()
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
                    postOrderStatus(orderId, status, session.authorizationHeader)
                }
            }

            result.onSuccess { response ->
                val message = response.optString("message", "Order status updated")
                Toast.makeText(
                    this@AdminOrderDetailActivity,
                    message,
                    Toast.LENGTH_LONG
                ).show()
                finish()
            }.onFailure { error ->
                Toast.makeText(
                    this@AdminOrderDetailActivity,
                    error.message ?: "Order status update failed",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun postOrderStatus(
        orderId: String,
        status: String,
        authorizationHeader: String
    ): JSONObject {
        val url = URL("https://roam2world-panels-backend.onrender.com/api/v1/orders/orders/$orderId/update_status/")
        val connection = url.openConnection() as HttpURLConnection

        return try {
            val body = JSONObject()
                .put("status", status)
                .put("notes", "Updated from mobile admin")
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

    private fun actionButtonBackground(label: String): Int {
        val normalized = label.lowercase()
        return when {
            normalized.contains("suspend") -> R.drawable.admin_danger_pill
            normalized.contains("cancel") -> R.drawable.admin_danger_pill
            normalized.contains("delete") -> R.drawable.admin_danger_pill
            normalized.contains("activate") -> R.drawable.admin_success_pill
            normalized.contains("complete") -> R.drawable.admin_success_pill
            normalized.contains("delivered") -> R.drawable.admin_success_pill
            normalized.contains("confirmed") -> R.drawable.admin_success_pill
            normalized.contains("processing") -> R.drawable.admin_warning_pill
            normalized.contains("dispatched") -> R.drawable.admin_warning_pill
            else -> R.drawable.admin_primary_pill
        }
    }

    private fun confirmAction(
        title: String,
        message: String,
        onConfirm: () -> Unit
    ) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Confirm") { _, _ -> onConfirm() }
            .setNegativeButton("Cancel", null)
            .show()
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
