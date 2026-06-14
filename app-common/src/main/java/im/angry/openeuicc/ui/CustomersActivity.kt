package im.angry.openeuicc.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.bottomsheet.BottomSheetDialog
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
import java.time.format.DateTimeFormatter
import java.util.Locale

class CustomersActivity : AppCompatActivity() {

    private fun formatCustomerDate(value: String?): String {
        val raw = value?.trim().orEmpty()
        if (raw.isBlank()) return ""
        return try {
            val formatter = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm", Locale.ENGLISH)
            OffsetDateTime.parse(raw).format(formatter)
        } catch (_: Exception) {
            raw
        }
    }


    private fun visibleProvider(provider: String?): String =
        formatCustomerStatus(
            provider?.replace("TGT", "Orange", ignoreCase = true)
                ?.replace("tgt", "Orange", ignoreCase = true)
        )

    private fun formatCustomerStatus(value: String?): String {
        val raw = value?.trim().orEmpty()
        if (raw.isBlank()) return "Unknown"
        return raw
            .replace("_", " ")
            .replace("-", " ")
            .split(" ")
            .filter { it.isNotBlank() }
            .joinToString(" ") { word ->
                word.lowercase(Locale.ROOT).replaceFirstChar {
                    if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString()
                }
            }
            .ifBlank { "Unknown" }
    }


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
        supportActionBar?.title = ""

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


    private fun initialsForCustomer(name: String): String =
        name.split(" ")
            .filter { it.isNotBlank() }
            .take(2)
            .map { it.first().uppercaseChar() }
            .joinToString("")
            .ifBlank { "CU" }

    private fun statusLabel(record: CustomerEsimRecord): String =
        when {
            isExpired(record) -> "Expired"
            record.status?.contains("pending", ignoreCase = true) == true -> "Pending"
            else -> "Active"
        }

    private fun statusBadgeBg(label: String): Int =
        when (label.lowercase(Locale.ENGLISH)) {
            "expired" -> R.drawable.r2w_customer_expired_badge
            "pending", "expiring" -> R.drawable.r2w_customer_expiring_badge
            else -> R.drawable.r2w_customer_active_badge
        }

    private fun statusTextColor(label: String): Int =
        when (label.lowercase(Locale.ENGLISH)) {
            "expired" -> android.graphics.Color.parseColor("#DC2626")
            "pending", "expiring" -> android.graphics.Color.parseColor("#F59E0B")
            else -> android.graphics.Color.parseColor("#168653")
        }


    private fun renderCustomers(customers: List<CustomerSummary>) {
        list.removeAllViews()
        empty.visibility = if (customers.isEmpty()) View.VISIBLE else View.GONE

        val totalCustomers = allCustomers.size
        val totalActive = allCustomers.sumOf { it.activeEsims }

        findViewById<TextView>(R.id.customers_total_count)?.text = totalCustomers.toString()
        findViewById<TextView>(R.id.customers_active_count)?.text = totalActive.toString()

        summary.text = "Showing ${customers.size} customers"
        customers.forEach { customer -> list.addView(createCustomerCard(customer)) }
    }

    private fun createCustomerCard(customer: CustomerSummary): View {
        val latest = customer.latestEsim
        val status = statusLabel(latest)

        val card = MaterialCardView(this).apply {
            radius = dp(16).toFloat()
            cardElevation = 0f
            strokeWidth = dp(1)
            setStrokeColor(android.graphics.Color.parseColor("#E2E8F0"))
            setCardBackgroundColor(android.graphics.Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(10)
            }
            setOnClickListener { showCustomerDetails(customer) }
            isClickable = true
            isFocusable = true
        }

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(14), dp(10), dp(14))
        }

        val avatarWrap = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(50), dp(50))
        }

        avatarWrap.addView(TextView(this).apply {
            text = initialsForCustomer(customer.name)
            gravity = Gravity.CENTER
            background = getDrawable(R.drawable.r2w_customer_avatar_bg)
            setTextColor(getColor(R.color.r2w_text_primary))
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleLarge)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            layoutParams = FrameLayout.LayoutParams(dp(46), dp(46), Gravity.CENTER)
        })

        val info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                leftMargin = dp(12)
            }
        }

        info.addView(TextView(this).apply {
            text = customer.name
            setTextColor(getColor(R.color.r2w_text_primary))
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        })

        info.addView(TextView(this).apply {
            text = listOfNotNull(customer.email, customer.phone).firstOrNull { it.isNotBlank() } ?: "No contact info"
            setPadding(0, dp(4), 0, 0)
            setTextColor(getColor(R.color.r2w_text_secondary))
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        })

        info.addView(TextView(this).apply {
            text = PackageNameCleaner.clean(latest.packageName).orEmpty().ifBlank { "Package unavailable" }
            setPadding(0, dp(6), 0, 0)
            setTextColor(getColor(R.color.r2w_premium_primary))
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        })

        val right = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        right.addView(TextView(this).apply {
            text = "●  $status"
            gravity = Gravity.CENTER
            minWidth = dp(82)
            minHeight = dp(28)
            setPadding(dp(8), 0, dp(8), 0)
            setTextColor(statusTextColor(status))
            background = getDrawable(statusBadgeBg(status))
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelLarge)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })

        // Keep right side compact; date below status is enough.
        // Date hidden on compact cards to keep the CRM list clean.

        row.addView(avatarWrap)
        row.addView(info)
        row.addView(right)
        row.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_more_chevron)
            setColorFilter(getColor(R.color.r2w_text_secondary))
            layoutParams = LinearLayout.LayoutParams(dp(24), dp(24)).apply {
                leftMargin = dp(8)
            }
        })

        card.addView(row)
        return card
    }


    private fun showCustomerDetails(customer: CustomerSummary) {
        val dialog = BottomSheetDialog(this)

        fun chip(textValue: String): TextView =
            TextView(this).apply {
                text = textValue
                gravity = Gravity.CENTER
                setPadding(dp(12), 0, dp(12), 0)
                minHeight = dp(32)
                background = getDrawable(R.drawable.r2w_crm_sheet_chip)
                setTextColor(getColor(R.color.r2w_text_primary))
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelMedium)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            }

        fun smallMeta(textValue: String): TextView =
            TextView(this).apply {
                text = textValue
                setTextColor(getColor(R.color.r2w_text_secondary))
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(10), dp(20), dp(18))
            background = getDrawable(R.drawable.r2w_crm_soft_panel)
        }

        root.addView(View(this).apply {
            background = getDrawable(R.drawable.r2w_crm_sheet_handle)
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(5)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = dp(16)
            }
        })

        val headerCard = MaterialCardView(this).apply {
            radius = dp(22).toFloat()
            cardElevation = 0f
            strokeWidth = dp(1)
            setStrokeColor(android.graphics.Color.parseColor("#E4E9F2"))
            setCardBackgroundColor(android.graphics.Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }

        header.addView(FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(64), dp(64))
            addView(TextView(this@CustomersActivity).apply {
                text = initialsForCustomer(customer.name)
                gravity = Gravity.CENTER
                background = getDrawable(R.drawable.r2w_customer_avatar_bg)
                setTextColor(getColor(R.color.r2w_text_primary))
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleLarge)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                layoutParams = FrameLayout.LayoutParams(dp(58), dp(58), Gravity.CENTER)
            })
        })

        header.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                leftMargin = dp(14)
            }

            addView(TextView(this@CustomersActivity).apply {
                text = customer.name
                setTextColor(getColor(R.color.r2w_text_primary))
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_HeadlineSmall)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            })

            addView(TextView(this@CustomersActivity).apply {
                text = listOfNotNull(customer.email, customer.phone).firstOrNull { it.isNotBlank() } ?: "No contact information"
                setPadding(0, dp(4), 0, 0)
                setTextColor(getColor(R.color.r2w_text_secondary))
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            })

            addView(LinearLayout(this@CustomersActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(10), 0, 0)

                addView(chip("${customer.totalEsims} eSIMs"))
                addView(chip("${customer.activeEsims} active").apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { leftMargin = dp(8) }
                })
                addView(chip("${customer.expiredEsims} expired").apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { leftMargin = dp(8) }
                })
            })
        })

        headerCard.addView(header)
        root.addView(headerCard)

        root.addView(TextView(this).apply {
            text = "Customer eSIMs"
            setPadding(0, dp(18), 0, dp(10))
            setTextColor(getColor(R.color.r2w_text_primary))
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })

        val scroll = androidx.core.widget.NestedScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            isFillViewport = false
        }

        val listContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        customer.esims.forEach { record ->
            val status = statusLabel(record)

            val card = MaterialCardView(this).apply {
                radius = dp(18).toFloat()
                cardElevation = 0f
                strokeWidth = dp(1)
                setStrokeColor(android.graphics.Color.parseColor("#E4E9F2"))
                setCardBackgroundColor(android.graphics.Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(12) }
            }

            val body = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), dp(14), dp(14), dp(14))
            }

            val topRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            topRow.addView(FrameLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(46), dp(46))
                background = getDrawable(R.drawable.r2w_crm_sheet_icon_bg)
                addView(ImageView(this@CustomersActivity).apply {
                    setImageResource(R.drawable.ic_task_sim_card_download)
                    setColorFilter(getColor(R.color.r2w_premium_primary))
                    layoutParams = FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER)
                })
            })

            topRow.addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    leftMargin = dp(12)
                }

                addView(TextView(this@CustomersActivity).apply {
                    text = PackageNameCleaner.clean(record.packageName).orEmpty().ifBlank { "eSIM Package" }
                    setTextColor(getColor(R.color.r2w_text_primary))
                    setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium)
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                })

                addView(TextView(this@CustomersActivity).apply {
                    text = listOfNotNull(
                        record.provider?.let { visibleProvider(it) },
                        record.createdAt?.let { formatCustomerDate(it) }
                    ).filter { it.isNotBlank() }.joinToString(" • ").ifBlank { "Provider unavailable" }
                    setPadding(0, dp(4), 0, 0)
                    setTextColor(getColor(R.color.r2w_text_secondary))
                    setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                })
            })

            topRow.addView(TextView(this).apply {
                text = "●  $status"
                gravity = Gravity.CENTER
                minWidth = dp(84)
                minHeight = dp(30)
                setPadding(dp(10), 0, dp(10), 0)
                setTextColor(statusTextColor(status))
                background = getDrawable(statusBadgeBg(status))
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelMedium)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })

            body.addView(topRow)

            body.addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(58), dp(10), 0, 0)

                addView(smallMeta(record.iccid?.let { "ICCID: $it" } ?: "ICCID unavailable"))
                addView(smallMeta(record.dataRemaining?.let { "Remaining: $it" } ?: "Remaining: —").apply {
                    setPadding(0, dp(3), 0, 0)
                })
                addView(smallMeta(record.expiresAt?.let { "Expires: ${formatCustomerDate(it)}" } ?: "Expires: —").apply {
                    setPadding(0, dp(3), 0, 0)
                })
            })

            body.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setBaselineAligned(false)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(12) }

                addView(button("QR / Detail") { openEsimDetail(record) }, LinearLayout.LayoutParams(0, dp(44), 1f).apply {
                    rightMargin = dp(5)
                })
                addView(button("Copy ICCID") { copyIccid(record) }, LinearLayout.LayoutParams(0, dp(44), 1f).apply {
                    leftMargin = dp(5)
                })
            })

            body.addView(button("Renew") { openRenewal(record) }.apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(44)
                ).apply { topMargin = dp(8) }
            })

            card.addView(body)
            listContent.addView(card)
        }

        scroll.addView(listContent)
        root.addView(scroll)

        val closeButton = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "Close"
            isAllCaps = false
            cornerRadius = dp(16)
            setTextColor(getColor(R.color.r2w_text_primary))
            strokeColor = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#E4E9F2"))
            strokeWidth = dp(1)
            backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.WHITE)
            stateListAnimator = null
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(46)
            ).apply { topMargin = dp(12) }
            setOnClickListener { dialog.dismiss() }
        }
        root.addView(closeButton)

        dialog.setContentView(root)
        dialog.show()
    }


    private fun label(textValue: String, bold: Boolean, appearance: Int = com.google.android.material.R.style.TextAppearance_Material3_BodyMedium): TextView =
        TextView(this).apply {
            text = textValue
            setTextAppearance(appearance)
            setTextColor(getColor(if (bold) R.color.r2w_premium_text else R.color.r2w_premium_muted))
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        }

    private fun button(textValue: String, action: () -> Unit): MaterialButton =
        MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = textValue
            isAllCaps = false
            gravity = Gravity.CENTER
            minHeight = dp(44)
            cornerRadius = dp(16)
            insetTop = 0
            insetBottom = 0
            iconPadding = dp(6)
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelLarge)
            setTypeface(typeface, android.graphics.Typeface.BOLD)

            val isPrimary = textValue.equals("QR / Detail", ignoreCase = true) ||
                textValue.equals("Renew", ignoreCase = true)

            if (isPrimary) {
                setTextColor(getColor(R.color.r2w_premium_primary))
                strokeColor = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#D6E4FF"))
                strokeWidth = dp(1)
                backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#F3F7FF"))
            } else {
                setTextColor(getColor(R.color.r2w_text_primary))
                strokeColor = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#E4E9F2"))
                strokeWidth = dp(1)
                backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.WHITE)
            }

            stateListAnimator = null
            setOnClickListener { action() }
        }

    private fun openEsimDetail(record: CustomerEsimRecord) {
        startActivity(MobileEsimDetailActivity.createIntent(this, record.toMobileEsim()))
    }

    private fun openRenewal(record: CustomerEsimRecord) {
        copyIccid(record, forRenewal = true)
        val provider = record.provider.orEmpty().lowercase()
        val target = if (provider.contains("airhub") || provider.contains("vodafone")) {
            VodafoneRenewalActivity::class.java
        } else {
            TgtSimRechargeActivity::class.java
        }
        startActivity(Intent(this, target).apply { putExtra("renew.iccid", record.iccid) })
    }

    private fun copyIccid(record: CustomerEsimRecord, forRenewal: Boolean = false) {
        val value = record.iccid?.takeIf { it.isNotBlank() }
        if (value == null) {
            Toast.makeText(this, "ICCID not available", Toast.LENGTH_SHORT).show()
            return
        }
        getSystemService(ClipboardManager::class.java)
            .setPrimaryClip(ClipData.newPlainText("ICCID", value))
        Toast.makeText(
            this,
            if (forRenewal) "ICCID copied for renewal" else "ICCID copied",
            Toast.LENGTH_SHORT
        ).show()
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
            customerName(), customerPhone, customerEmail, iccid, packageName, provider, dataRemaining, dataUsed, orderNumber, orderId, status, createdAt, expiresAt
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
