package im.angry.openeuicc.ui

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
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
    private lateinit var search: TextInputEditText

    private var allCustomers: List<CustomerSummary> = emptyList()

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
        search = requireViewById(R.id.customers_search)

        setupInsets()
        setupBottomNavigation()
        setupSearch()
        refresh.setOnRefreshListener { loadCustomers() }
        loadCustomers()
    }

    override fun onResume() {
        super.onResume()
        bottomNav.menu.findItem(R.id.nav_more)?.isChecked = true
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
                R.id.nav_more -> true
                else -> false
            }
        }
        bottomNav.menu.findItem(R.id.nav_more)?.isChecked = true
    }

    private fun setupSearch() {
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = applySearch()
            override fun afterTextChanged(s: Editable?) = Unit
        })
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
                .onSuccess {
                    allCustomers = buildCustomers(it)
                    applySearch()
                }
                .onFailure {
                    error.text = it.message ?: "Customers could not be loaded"
                    error.visibility = View.VISIBLE
                }
        }
    }

    private fun applySearch() {
        val query = search.text?.toString()?.trim().orEmpty().lowercase()
        val filtered = if (query.isBlank()) allCustomers else allCustomers.filter { it.searchText().lowercase().contains(query) }
        renderCustomers(filtered)
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
        val activation = json.optJSONObject("activation") ?: json.optJSONObject("activation_details")
        return CustomerEsimRecord(
            id = firstNotBlank(json.optString("id"), json.optString("esim_id"), json.optString("esimId")),
            iccid = firstNotBlank(json.optString("iccid"), activation?.optString("iccid")),
            provider = firstNotBlank(json.optString("display_provider"), json.optString("provider"), json.optString("source")),
            packageName = firstNotBlank(json.optString("package_name"), json.optString("packageName"), json.optString("plan_name"), json.optString("planName"), json.optString("product_name")),
            status = firstNotBlank(json.optString("status"), json.optString("state")),
            createdAt = firstNotBlank(json.optString("created_at"), json.optString("createdAt"), json.optString("purchased_at"), json.optString("purchasedAt")),
            expiresAt = firstNotBlank(json.optString("expires_at"), json.optString("expiresAt"), json.optString("expiry_date"), json.optString("expiryDate")),
            dataRemaining = firstNotBlank(json.optString("data_remaining"), json.optString("dataRemaining"), json.optString("remaining_data"), json.optString("remainingData")),
            dataUsed = firstNotBlank(json.optString("data_used"), json.optString("dataUsed"), json.optString("used_data"), json.optString("usedData")),
            activationCode = firstNotBlank(json.optString("activation_code"), json.optString("activationCode"), activation?.optString("activation_code"), activation?.optString("activationCode")),
            lpaCode = firstNotBlank(json.optString("lpa_code"), json.optString("lpaCode"), activation?.optString("lpa_code"), activation?.optString("lpaCode")),
            smdpAddress = firstNotBlank(json.optString("smdp_address"), json.optString("smdpAddress"), activation?.optString("smdp_address"), activation?.optString("smdpAddress")),
            matchingId = firstNotBlank(json.optString("matching_id"), json.optString("matchingId"), activation?.optString("matching_id"), activation?.optString("matchingId")),
            qrCode = firstNotBlank(json.optString("qr_code"), json.optString("qrCode"), activation?.optString("qr_code"), activation?.optString("qrCode")),
            qrCodeUrl = firstNotBlank(json.optString("qr_code_url"), json.optString("qrCodeUrl"), json.optString("qr_url"), activation?.optString("qr_code_url")),
            orderNumber = firstNotBlank(json.optString("order_number"), json.optString("orderNumber")),
            orderId = firstNotBlank(json.optString("order_id"), json.optString("orderId")),
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
                val sorted = records.sortedByDescending { it.createdAt.orEmpty() }
                val latest = sorted.first()
                CustomerSummary(
                    name = latest.customerName() ?: "Customer",
                    phone = latest.customerPhone,
                    email = latest.customerEmail,
                    totalEsims = records.size,
                    activeEsims = records.count { !isExpired(it) },
                    expiredEsims = records.count { isExpired(it) },
                    latestEsim = latest,
                    esims = sorted
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
        val latest = customer.latestEsim
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
        body.addView(label(customer.name, true, com.google.android.material.R.style.TextAppearance_Material3_TitleLarge))
        body.addView(label(listOfNotNull(customer.phone, customer.email).filter { it.isNotBlank() }.joinToString(" • ").ifBlank { "No contact info" }, false))
        body.addView(label("${customer.totalEsims} eSIMs • ${customer.activeEsims} active • ${customer.expiredEsims} expired", false).apply { setPadding(0, dp(12), 0, 0) })
        body.addView(label(
            listOfNotNull(
                latest.packageName?.let { "Last package: $it" },
                latest.dataRemaining?.let { "Remaining: $it" },
                latest.iccid?.let { "ICCID: $it" },
                latest.expiresAt?.let { "Expires: $it" },
                latest.provider?.let { "Provider: $it" }
            ).joinToString("\n").ifBlank { "No eSIM details available" },
            false,
            com.google.android.material.R.style.TextAppearance_Material3_BodySmall
        ).apply { setPadding(0, dp(10), 0, 0) })

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBaselineAligned(false)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(14) }
        }
        row.addView(button("View Details") { showCustomerDetails(customer) }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { rightMargin = dp(6) })
        row.addView(button("Last eSIM") { openEsimDetail(latest) }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { leftMargin = dp(6) })
        body.addView(row)
        card.addView(body)
        return card
    }

    private fun showCustomerDetails(customer: CustomerSummary) {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(6), dp(8), dp(6))
        }
        content.addView(label("${customer.totalEsims} eSIMs • ${customer.activeEsims} active • ${customer.expiredEsims} expired", false))
        customer.esims.forEach { record ->
            val card = MaterialCardView(this).apply {
                radius = dp(16).toFloat()
                strokeWidth = dp(1)
                setStrokeColor(getColor(R.color.r2w_border))
                setCardBackgroundColor(getColor(R.color.r2w_card))
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12) }
            }
            val body = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), dp(12), dp(14), dp(12))
            }
            body.addView(label(record.packageName ?: "eSIM Package", true, com.google.android.material.R.style.TextAppearance_Material3_TitleMedium))
            body.addView(label(
                listOfNotNull(
                    record.iccid?.let { "ICCID: $it" },
                    record.dataRemaining?.let { "Remaining: $it" },
                    record.dataUsed?.let { "Used: $it" },
                    record.expiresAt?.let { "Expires: $it" },
                    record.status?.let { "Status: $it" },
                    record.provider?.let { "Provider: $it" }
                ).joinToString("\n"),
                false,
                com.google.android.material.R.style.TextAppearance_Material3_BodySmall
            ).apply { setPadding(0, dp(8), 0, 0) })
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setBaselineAligned(false)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10) }
            }
            row.addView(button("QR / Detail") { openEsimDetail(record) }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { rightMargin = dp(5) })
            row.addView(button("Renew") { openRenewal(record) }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { leftMargin = dp(5) })
            body.addView(row)
            card.addView(body)
            content.addView(card)
        }
        AlertDialog.Builder(this)
            .setTitle(customer.name)
            .setView(content)
            .setPositiveButton("Close", null)
            .show()
    }

    private fun label(textValue: String, bold: Boolean, appearance: Int = com.google.android.material.R.style.TextAppearance_Material3_BodyMedium): TextView =
        TextView(this).apply {
            text = textValue
            setTextAppearance(appearance)
            setTextColor(getColor(if (bold) R.color.r2w_text_primary else R.color.r2w_text_secondary))
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        }

    private fun button(textValue: String, action: () -> Unit): MaterialButton =
        MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = textValue
            gravity = Gravity.CENTER
            cornerRadius = dp(14)
            setOnClickListener { action() }
        }

    private fun openEsimDetail(record: CustomerEsimRecord) {
        startActivity(MobileEsimDetailActivity.createIntent(this, record.toMobileEsim()))
    }

    private fun openRenewal(record: CustomerEsimRecord) {
        val provider = record.provider.orEmpty().lowercase()
        val target = if (provider.contains("airhub") || provider.contains("vodafone")) {
            VodafoneRenewalActivity::class.java
        } else {
            TgtSimRechargeActivity::class.java
        }
        startActivity(Intent(this, target).apply { putExtra("renew.iccid", record.iccid) })
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
        val dataRemaining: String?,
        val dataUsed: String?,
        val activationCode: String?,
        val lpaCode: String?,
        val smdpAddress: String?,
        val matchingId: String?,
        val qrCode: String?,
        val qrCodeUrl: String?,
        val orderNumber: String?,
        val orderId: String?,
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

        fun searchText(): String = listOfNotNull(
            customerName(), customerPhone, customerEmail, iccid, packageName, provider, dataRemaining, orderNumber
        ).joinToString(" ")

        fun toMobileEsim(): MobileEsim = MobileEsim(
            id = id,
            iccid = iccid,
            provider = provider,
            packageName = packageName,
            status = status,
            activationCode = activationCode,
            lpaCode = lpaCode,
            smdpAddress = smdpAddress,
            matchingId = matchingId,
            confirmationCodeRequired = false,
            qrCode = qrCode,
            qrCodeUrl = qrCodeUrl,
            createdAt = createdAt,
            orderNumber = orderNumber,
            expiresAt = expiresAt,
            dataRemaining = dataRemaining,
            dataUsed = dataUsed,
            orderId = orderId,
            customerFirstName = customerFirstName,
            customerLastName = customerLastName,
            customerPhone = customerPhone,
            customerEmail = customerEmail
        )
    }

    private data class CustomerSummary(
        val name: String,
        val phone: String?,
        val email: String?,
        val totalEsims: Int,
        val activeEsims: Int,
        val expiredEsims: Int,
        val latestEsim: CustomerEsimRecord,
        val esims: List<CustomerEsimRecord>
    ) {
        fun searchText(): String = listOfNotNull(name, phone, email).joinToString(" ") + " " + esims.joinToString(" ") { it.searchText() }
    }

    private companion object {
        fun firstNotBlankStatic(vararg values: String?): String? =
            values.firstOrNull { !it.isNullOrBlank() && it != "null" }
    }
}
