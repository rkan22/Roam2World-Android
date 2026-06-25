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
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class AdminPricingActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val tokenStore by lazy { AuthTokenStore(this) }

    private lateinit var subtitleText: TextView
    private lateinit var refreshButton: Button
    private lateinit var providerMarkupsButton: Button
    private lateinit var listContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_pricing)

        subtitleText = findViewById(R.id.adminPricingSubtitleText)
        refreshButton = findViewById(R.id.adminPricingRefreshButton)
        listContainer = findViewById(R.id.adminPricingListContainer)

        providerMarkupsButton = Button(this)
        providerMarkupsButton.text = "Provider Markups"
        providerMarkupsButton.setTextColor(0xFFFFFFFF.toInt())
        providerMarkupsButton.setBackgroundResource(R.drawable.admin_warning_pill)

        val providerButtonParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        providerButtonParams.setMargins(0, 16, 0, 0)
        providerMarkupsButton.layoutParams = providerButtonParams

        val refreshParent = refreshButton.parent
        if (refreshParent is LinearLayout) {
            refreshParent.addView(providerMarkupsButton)
        }

        refreshButton.setOnClickListener { loadPricing() }
        providerMarkupsButton.setOnClickListener {
            startActivity(Intent(this, AdminProviderMarkupsActivity::class.java))
        }

        loadPricing()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun loadPricing() {
        subtitleText.text = "Loading pricing..."
        listContainer.removeAllViews()
        addCard("Loading pricing...\\n\\nFetching provider plans and price overview.")

        scope.launch {
            val session = withContext(Dispatchers.IO) { tokenStore.getSession() }

            if (session == null || JwtUtils.isExpired(session.accessToken)) {
                redirectToLogin()
                return@launch
            }

            val result = withContext(Dispatchers.IO) {
                runCatching { fetchAdminPricing(session.authorizationHeader) }
            }

            result.onSuccess { response ->
                renderPricing(response)
            }.onFailure { error ->
                subtitleText.text = "Pricing unavailable"
                Toast.makeText(
                    this@AdminPricingActivity,
                    error.message ?: "Pricing API error",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun fetchAdminPricing(authorizationHeader: String): JSONObject {
        val url = URL("https://roam2world-panels-backend.onrender.com/api/v1/mobile/admin/pricing/")
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

    private fun renderPricing(response: JSONObject) {
        val data = response.optJSONObject("data") ?: response
        val count = data.optInt("count", 0)
        val pricing = data.optJSONArray("pricing") ?: JSONArray()

        subtitleText.text = "$count pricing item(s)"

        if (pricing.length() == 0) {
            addCard("No pricing found.\n\nPricing plans will appear here once provider catalog data is available.")
            return
        }

        for (i in 0 until pricing.length()) {
            val item = pricing.optJSONObject(i) ?: continue

            val planName = item.optString("name", "-")
            val country = item.optString("country", "-")
            val region = item.optString("region", "")
            val provider = item.optString("provider", "-")
            val dataVolume = item.optString("data_volume", "-")
            val validityDays = item.optInt("validity_days", 0)
            val basePrice = item.optString("base_price", "0.00")
            val resellerPrice = item.optString("reseller_price", "0.00")
            val publicPrice = item.optString("public_price", "0.00")
            val markup = item.optString("markup_percentage", "0.00")
            val active = item.optBoolean("is_active", false)

            val status = if (active) "Active" else "Inactive"

            addCard(
                "$planName\n" +
                    "$country $region\n\n" +
                    "Provider: $provider\n" +
                    "Data: $dataVolume / $validityDays days\n" +
                    statusBadge(status) + "\n" +
                    "Base: $basePrice\n" +
                    "Reseller: $resellerPrice\n" +
                    "Public: $publicPrice\n" +
                    "Markup: $markup%"
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
