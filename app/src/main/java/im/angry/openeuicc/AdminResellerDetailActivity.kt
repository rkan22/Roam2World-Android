package im.angry.openeuicc

import android.app.Activity
import android.app.AlertDialog
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
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class AdminResellerDetailActivity : Activity() {
    companion object {
        const val EXTRA_RESELLER_JSON = "extra_reseller_json"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val tokenStore by lazy { AuthTokenStore(this) }

    private lateinit var subtitleText: TextView
    private lateinit var refreshButton: Button
    private lateinit var listContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_reseller_detail)

        subtitleText = findViewById(R.id.adminResellerDetailSubtitleText)
        refreshButton = findViewById(R.id.adminResellerDetailRefreshButton)
        listContainer = findViewById(R.id.adminResellerDetailListContainer)

        refreshButton.text = "Close"
        refreshButton.setOnClickListener { finish() }

        val raw = intent.getStringExtra(EXTRA_RESELLER_JSON)
        if (raw.isNullOrBlank()) {
            subtitleText.text = "No reseller data"
            addCard("Reseller data was not provided.")
            return
        }

        val reseller = JSONObject(raw)
        render(reseller)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun render(reseller: JSONObject) {
        val id = reseller.optString("id", "-")
        val name = reseller.optString("name", "-")
        val email = reseller.optString("email", "-")
        val active = reseller.optBoolean("is_active", false)
        val suspended = reseller.optBoolean("is_suspended", false)

        val credit = reseller.optString("current_credit", "0.00")
        val creditLimit = reseller.optString("credit_limit", "0.00")
        val monthlySpent = reseller.optString("current_month_spent", "0.00")
        val monthlyLimit = reseller.optString("monthly_spend_limit", "0.00")
        val markup = reseller.optString("markup_percentage", "0.00")

        val status = when {
            suspended -> "Suspended"
            active -> "Active"
            else -> "Inactive"
        }

        subtitleText.text = "$name / $status"

        addCard(
            "Overview\n" +
                "Name: $name\n" +
                "Email: $email\n" +
                statusBadge(status) + "\n" +
                "Active: ${if (active) "Yes" else "No"}\n" +
                "Suspended: ${if (suspended) "Yes" else "No"}"
        )

        addCard(
            "Credit\n" +
                "Current Credit: $credit\n" +
                "Credit Limit: $creditLimit"
        )

        addCard(
            "Monthly Spend\n" +
                "Spent: $monthlySpent\n" +
                "Limit: $monthlyLimit"
        )

        addCard(
            "Pricing\n" +
                "Markup: $markup%"
        )

        addMarkupEditor(id, markup)

        if (suspended) {
            addActionButton("Activate Reseller", id, "activate_reseller")
        } else {
            addActionButton("Suspend Reseller", id, "suspend_reseller")
        }
    }

    private fun addMarkupEditor(resellerId: String, currentMarkup: String) {
        val input = EditText(this)
        input.hint = "Markup percentage"
        input.setText(currentMarkup)
        input.inputType = android.text.InputType.TYPE_CLASS_NUMBER or
            android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        input.textSize = 15f
        input.setTextColor(0xFF07133D.toInt())
        input.setHintTextColor(0xFF6B7280.toInt())
        input.setBackgroundResource(R.drawable.admin_card_background)
        input.setPadding(28, 18, 28, 18)

        val inputParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        inputParams.setMargins(0, 0, 0, 12)
        input.layoutParams = inputParams
        listContainer.addView(input)

        val button = Button(this)
        button.text = "Update Markup"
        button.textSize = 14f
        button.setTextColor(0xFFFFFFFF.toInt())
        button.setBackgroundResource(R.drawable.admin_primary_pill)
        button.setPadding(24, 14, 24, 14)
        button.setOnClickListener {
            confirmAction(
                title = "Update Markup",
                message = "Update reseller markup to ${input.text}%",
            ) {
                updateResellerMarkup(resellerId, input.text.toString())
            }
        }

        val buttonParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        buttonParams.setMargins(0, 0, 0, 20)
        button.layoutParams = buttonParams
        listContainer.addView(button)
    }

    private fun updateResellerMarkup(resellerId: String, markup: String) {
        if (resellerId.isBlank() || resellerId == "-") {
            Toast.makeText(this, "Missing reseller id", Toast.LENGTH_LONG).show()
            return
        }

        val cleanMarkup = markup.trim()
        if (cleanMarkup.isBlank()) {
            Toast.makeText(this, "Markup is required", Toast.LENGTH_LONG).show()
            return
        }

        val value = cleanMarkup.toDoubleOrNull()
        if (value == null || value < 0.0 || value > 100.0) {
            Toast.makeText(this, "Markup must be between 0 and 100", Toast.LENGTH_LONG).show()
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
                    postResellerMarkup(resellerId, cleanMarkup, session.authorizationHeader)
                }
            }

            result.onSuccess { response ->
                Toast.makeText(
                    this@AdminResellerDetailActivity,
                    response.optString("message", "Reseller markup updated"),
                    Toast.LENGTH_LONG
                ).show()
                finish()
            }.onFailure { error ->
                Toast.makeText(
                    this@AdminResellerDetailActivity,
                    error.message ?: "Reseller markup update failed",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun postResellerMarkup(
        resellerId: String,
        markup: String,
        authorizationHeader: String
    ): JSONObject {
        val url = URL("https://roam2world-panels-backend.onrender.com/api/v1/mobile/admin/resellers/${resellerId}/markup/")
        val connection = url.openConnection() as HttpURLConnection

        return try {
            val body = JSONObject()
                .put("markup_percentage", markup)
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

    private fun addActionButton(label: String, resellerId: String, action: String) {
        val button = Button(this)
        button.text = label
        button.textSize = 14f
        button.setTextColor(0xFFFFFFFF.toInt())
        button.setBackgroundResource(actionButtonBackground(label))
        button.setPadding(24, 14, 24, 14)
        button.setOnClickListener {
            confirmAction(
                title = label,
                message = "Are you sure you want to continue?",
            ) {
                updateResellerStatus(resellerId, action)
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

    private fun updateResellerStatus(resellerId: String, action: String) {
        if (resellerId.isBlank() || resellerId == "-") {
            Toast.makeText(this, "Missing reseller id", Toast.LENGTH_LONG).show()
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
                    postResellerAction(resellerId, action, session.authorizationHeader)
                }
            }

            result.onSuccess { response ->
                Toast.makeText(
                    this@AdminResellerDetailActivity,
                    response.optString("message", "Reseller updated"),
                    Toast.LENGTH_LONG
                ).show()
                finish()
            }.onFailure { error ->
                Toast.makeText(
                    this@AdminResellerDetailActivity,
                    error.message ?: "Reseller action failed",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun postResellerAction(
        resellerId: String,
        action: String,
        authorizationHeader: String
    ): JSONObject {
        val url = URL("https://roam2world-panels-backend.onrender.com/api/v1/resellers/resellers/$resellerId/$action/")
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
