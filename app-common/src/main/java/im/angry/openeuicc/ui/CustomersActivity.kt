package im.angry.openeuicc.ui

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import im.angry.openeuicc.auth.AuthSession
import im.angry.openeuicc.auth.AuthTokenStore
import im.angry.openeuicc.auth.JwtUtils
import im.angry.openeuicc.auth.MobileEsim
import im.angry.openeuicc.auth.Roam2WorldAuthApi
import im.angry.openeuicc.common.BuildConfig
import im.angry.openeuicc.common.R
import im.angry.openeuicc.util.activityToolbarInsetHandler
import im.angry.openeuicc.util.mainViewPaddingInsetHandler
import im.angry.openeuicc.util.setupRootViewSystemBarInsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.OffsetDateTime

class CustomersActivity : AppCompatActivity() {
    private val tokenStore by lazy { AuthTokenStore(this) }
    private val authApi by lazy { Roam2WorldAuthApi(BuildConfig.ROAM2WORLD_API_BASE_URL) }

    private lateinit var refresh: SwipeRefreshLayout
    private lateinit var bottomNav: BottomNavigationView
    private lateinit var summary: TextView
    private lateinit var empty: TextView
    private lateinit var error: TextView
    private lateinit var list: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_customers)
        setSupportActionBar(requireViewById(R.id.toolbar))
        supportActionBar?.title = "Customers"

        refresh = requireViewById(R.id.customers_refresh)
        bottomNav = requireViewById(R.id.customers_bottom_nav)
        summary = requireViewById(R.id.customers_summary)
        empty = requireViewById(R.id.customers_empty)
        error = requireViewById(R.id.customers_error)
        list = requireViewById(R.id.customers_list)

        setupInsets()
        setupBottomNavigation()
        refresh.setOnRefreshListener { loadCustomers() }
        loadCustomers()
    }

    override fun onResume() {
        super.onResume()
        bottomNav.selectedItemId = R.id.nav_more
    }

    private fun setupInsets() {
        setupRootViewSystemBarInsets(
            window.decorView.rootView,
            arrayOf(
                this::activityToolbarInsetHandler,
                mainViewPaddingInsetHandler(refresh),
                { insets -> bottomNav.updatePadding(insets.left, bottomNav.paddingTop, insets.right, insets.bottom) }
            ),
            consume = false
        )
    }

    private fun setupBottomNavigation() {
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_dashboard -> {
                    startActivity(Intent(this, DashboardActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
                    false
                }
                R.id.nav_packages -> {
                    startActivity(Intent(this, PackagesActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
                    false
                }
                R.id.nav_wallet -> {
                    startActivity(Intent(this, WalletActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
                    false
                }
                R.id.nav_esims -> {
                    startActivity(Intent(this, MobileEsimsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
                    false
                }
                R.id.nav_more -> {
                    startActivity(Intent(this, MoreActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
                    false
                }
                else -> false
            }
        }
        bottomNav.selectedItemId = R.id.nav_more
    }

    private fun loadCustomers() {
        lifecycleScope.launch {
            error.visibility = View.GONE
            empty.visibility = View.GONE
            refresh.isRefreshing = true
            val session = activeSessionOrReturnToLogin()
            if (session == null) {
                refresh.isRefreshing = false
                return@launch
            }
            val result = runCatching { fetchEsimsWithCustomers(session) }
            refresh.isRefreshing = false

            result
                .onSuccess { renderCustomers(buildCustomers(it)) }
                .onFailure {
                    error.text = it.message ?: "Customers could not be loaded"
                    error.visibility = View.VISIBLE
                }
        }
    }

    private suspend fun activeSessionOrReturnToLogin(): AuthSession? {
        val savedSession = withContext(Dispatchers.IO) { tokenStore.getSession() } ?: return redirectToLogin()
        if (!JwtUtils.isExpired(savedSession.accessToken)) return savedSession
        val refreshed = runCatching { authApi.refresh(savedSession) }.getOrNull() ?: return redirectToLogin()
        withContext(Dispatchers.IO) { tokenStore.save(refreshed) }
        return refreshed
    }

    private fun redirectToLogin(): AuthSession? {
        tokenStore.clear()
        startActivity(
            Intent(this, LoginActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
        )
        finish()
        return null
    }

    private suspend fun fetchEsimsWithCustomers(session: AuthSession): List<CustomerEsimRecord> = withContext(Dispatchers.IO) {
        val url = "${BuildConfig.ROAM2WORLD_API_BASE_URL.trimEnd('/')}/api/v1/mobile/esims/"
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 30_000
            readTimeout = 30_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Authorization", session.authorizationHeader)
        }
        try {
            val status = connection.responseCode
            val text = ((if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()
                ?.use { it.readText() }).orEmpty()
            if (status !in 200..299) throw IllegalStateException("Customers API failed with HTTP $status")
            val root = JSONObject(text)
            val data = root.optJSONObject("data") ?: root
            val array = data.optJSONArray("esims") ?: data.optJSONArray("results") ?: root.optJSONArray("data") ?: JSONArray()
            (0 until array.length()).mapNotNull { index -> parseCustomerEsim(array.optJSONObject(index)) }
        } finally {
            connection.disconnect()
        }
    }

    private fun parseCustomerEsim(json: JSONObject?): CustomerEsimRecord? {
        json ?: return null
        val customer = json.optJSONObject("customer")
        return CustomerEsimRecord(
            id = firstNotBlank(json.optString("id"), json.optString("esim_id"), json.optString("esimId")),
            iccid = firstNotBlank(json.optString("iccid")),
            provider = firstNotBlank(json.optString("display_provider"), json.optString("provider")),
            packageName = firstNotBlank(json.optString("package_name"), json.optString("packageName"), json.optString("plan_name"), json.optString("planName")),
            status = firstNotBlank(json.optString("status")),
            createdAt = firstNotBlank(json.optString("created_at"), json.optString("createdAt"), json.optString("purchased_at"), json.optString("purchasedAt")),
            expiresAt = firstNotBlank(json.optString("expires_at"), json.optString("expiresAt"), json.optString("expiry_date"), json.optString("expiryDate")),
            customerFirstName = firstNotBlank(json.optString("customer_first_name"), json.optString("customerFirstName"), customer?.optString("first_name"), customer?.optString("firstName")),
            customerLastName = firstNotBlank(json.optString("customer_last_name"), json.optString("customerLastName"), customer?.optString("last_name"), customer?.optString("lastName")),
            customerPhone = firstNotBlank(json.optString("customer_phone"), json.optString("customerPhone"), json.optString("phone_number"), json.optString("phoneNumber"), customer?.optString("phone")),
            customerEmail = firstNotBlank(json.optString("customer_email"), json.optString("customerEmail"), customer?.optString("email"))
        )
    }

    private fun buildCustomers(esims: List<CustomerEsimRecord>): List<CustomerSummary> =
        esims
            .filter { !it.customerName().isNullOrBlank() || !it.customerPhone.isNullOrBlank() || !it.customerEmail.isNullOrBlank() }
            .groupBy { it.customerKey() }
            .map { (_, records) ->
                val latest = records.maxByOrNull { it.createdAt.orEmpty() } ?: records.first()
                CustomerSummary(
                    name = latest.customerName() ?: "Customer",
                    phone = latest.customerPhone,
                    email = latest.customerEmail,
                    totalEsims = records.size,
                    activeEsims = records.count { !isExpired(it) },
                    expiredEsims = records.count { isExpired(it) },
                    latestEsim = latest
                )
            }
            .sortedBy { it.name.lowercase() }

    private fun renderCustomers(customers: List<CustomerSummary>) {
        list.removeAllViews()
        empty.visibility = if (customers.isEmpty()) View.VISIBLE else View.GONE
        summary.text = "${customers.size} customers • ${customers.sumOf { it.totalEsims }} eSIM records"
        customers.forEach { customer -> list.addView(createCustomerCard(customer)) }
    }

    private fun createCustomerCard(customer: CustomerSummary): View {
        val card = MaterialCardView(this).apply {
            radius = dp(22).toFloat()
            cardElevation = dp(5).toFloat()
            strokeWidth = dp(1)
            setStrokeColor(getColor(R.color.r2w_border))
            setCardBackgroundColor(getColor(R.color.r2w_card))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(14)
            }
        }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(16))
        }
        body.addView(TextView(this).apply {
            text = customer.name
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleLarge)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(getColor(R.color.r2w_text_primary))
        })
        body.addView(TextView(this).apply {
            text = listOfNotNull(customer.phone, customer.email).filter { it.isNotBlank() }.joinToString(" • ").ifBlank { "No contact info" }
            setPadding(0, dp(4), 0, 0)
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
            setTextColor(getColor(R.color.r2w_text_secondary))
        })
        body.addView(TextView(this).apply {
            text = "${customer.totalEsims} eSIMs • ${customer.activeEsims} active • ${customer.expiredEsims} expired"
            setPadding(0, dp(12), 0, 0)
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge)
            setTextColor(getColor(R.color.r2w_text_primary))
        })
        body.addView(TextView(this).apply {
            text = listOfNotNull(
                customer.latestEsim.packageName?.let { "Latest: $it" },
                customer.latestEsim.iccid?.let { "ICCID: $it" },
                customer.latestEsim.provider
            ).joinToString("\n")
            setPadding(0, dp(10), 0, 0)
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
            setTextColor(getColor(R.color.r2w_text_secondary))
        })
        body.addView(MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "View eSIMs"
            gravity = Gravity.CENTER
            cornerRadius = dp(14)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48)).apply {
                topMargin = dp(14)
            }
            setOnClickListener {
                startActivity(Intent(this@CustomersActivity, MobileEsimsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
            }
        })
        card.addView(body)
        return card
    }

    private fun isExpired(record: CustomerEsimRecord): Boolean {
        if (record.status?.contains("expired", ignoreCase = true) == true) return true
        val expires = record.expiresAt ?: return false
        return runCatching { OffsetDateTime.parse(expires).isBefore(OffsetDateTime.now()) }.getOrDefault(false)
    }

    private fun firstNotBlank(vararg values: String?): String? =
        values.firstOrNull { !it.isNullOrBlank() && it != "null" }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private data class CustomerEsimRecord(
        val id: String?,
        val iccid: String?,
        val provider: String?,
        val packageName: String?,
        val status: String?,
        val createdAt: String?,
        val expiresAt: String?,
        val customerFirstName: String?,
        val customerLastName: String?,
        val customerPhone: String?,
        val customerEmail: String?
    ) {
        fun customerName(): String? = listOfNotNull(customerFirstName, customerLastName)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .takeIf { it.isNotBlank() }

        fun customerKey(): String = firstNotBlankStatic(customerPhone, customerEmail, customerName(), iccid, id) ?: "unknown"
    }

    private data class CustomerSummary(
        val name: String,
        val phone: String?,
        val email: String?,
        val totalEsims: Int,
        val activeEsims: Int,
        val expiredEsims: Int,
        val latestEsim: CustomerEsimRecord
    )

    private companion object {
        fun firstNotBlankStatic(vararg values: String?): String? =
            values.firstOrNull { !it.isNullOrBlank() && it != "null" }
    }
}
