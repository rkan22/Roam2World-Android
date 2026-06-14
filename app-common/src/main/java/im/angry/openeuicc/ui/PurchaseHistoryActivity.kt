package im.angry.openeuicc.ui

import com.google.android.material.bottomnavigation.BottomNavigationView

import com.google.android.material.bottomsheet.BottomSheetDialog


import android.graphics.drawable.GradientDrawable

import android.graphics.Color


import android.widget.ImageView

import java.time.OffsetDateTime
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.chip.Chip
import com.google.android.material.textfield.TextInputEditText
import im.angry.openeuicc.auth.AuthSession
import im.angry.openeuicc.auth.AuthTokenStore
import im.angry.openeuicc.auth.JwtUtils
import im.angry.openeuicc.auth.MobileOrder
import im.angry.openeuicc.auth.Roam2WorldAuthApi
import im.angry.openeuicc.common.BuildConfig
import im.angry.openeuicc.common.R
import im.angry.openeuicc.util.activityToolbarInsetHandler
import im.angry.openeuicc.util.mainViewPaddingInsetHandler
import im.angry.openeuicc.util.setupRootViewSystemBarInsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PurchaseHistoryActivity : AppCompatActivity() {
    private val tokenStore by lazy { AuthTokenStore(this) }
    private val authApi by lazy { Roam2WorldAuthApi(BuildConfig.ROAM2WORLD_API_BASE_URL) }

    private lateinit var refresh: SwipeRefreshLayout
    private lateinit var orders: LinearLayout
    private lateinit var empty: TextView
    private lateinit var error: TextView
    private lateinit var summary: TextView
    private lateinit var search: TextInputEditText

    private var allOrders: List<MobileOrder> = emptyList()
    private var filter: OrderFilter = OrderFilter.ALL
    private var dateFilter: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_purchase_history)
        setSupportActionBar(requireViewById(R.id.toolbar))
        supportActionBar?.apply {
            title = ""
            setDisplayHomeAsUpEnabled(false)
        }

        refresh = requireViewById(R.id.purchase_history_refresh)
        orders = requireViewById(R.id.purchase_history_orders)
        empty = requireViewById(R.id.purchase_history_empty)
        error = requireViewById(R.id.purchase_history_error)
        summary = requireViewById(R.id.purchase_history_summary)
        search = requireViewById(R.id.purchase_history_search)

        setupBottomNavigation()

        requireViewById<View>(R.id.purchase_history_search_icon).setOnClickListener {
            requireViewById<View>(R.id.purchase_history_search_layout).visibility = View.VISIBLE
            search.requestFocus()
            val imm = getSystemService(android.view.inputmethod.InputMethodManager::class.java)
            imm?.showSoftInput(search, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }

        requireViewById<View>(R.id.purchase_history_filter_icon).setOnClickListener {
            showOrderFilterSheet()
        }
        requireViewById<View>(R.id.purchase_history_filter_button).setOnClickListener {
            showOrderFilterSheet()
        }

        requireViewById<View>(R.id.purchase_history_search_layout).setOnClickListener {
            search.requestFocus()
            val imm = getSystemService(android.view.inputmethod.InputMethodManager::class.java)
            imm?.showSoftInput(search, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }
        search.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                val imm = getSystemService(android.view.inputmethod.InputMethodManager::class.java)
                imm?.showSoftInput(search, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
            }
        }

        setupInsets()
        dateFilter = intent.getStringExtra("order_date_filter")
        setupFilters()
        refresh.setOnRefreshListener { loadOrders() }
        renderOrders(emptyList())
        loadOrders()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }

    private fun setupInsets() {
        setupRootViewSystemBarInsets(
            window.decorView.rootView,
            arrayOf(
                this::activityToolbarInsetHandler,
                mainViewPaddingInsetHandler(refresh)
            ),
            consume = false
        )
    }

    private fun setupFilters() {
        requireViewById<TextView>(R.id.purchase_history_tab_all).setOnClickListener {
            filter = OrderFilter.ALL
            applyFilters()
        }
        requireViewById<TextView>(R.id.purchase_history_tab_pending).setOnClickListener {
            filter = OrderFilter.PENDING
            applyFilters()
        }
        requireViewById<TextView>(R.id.purchase_history_tab_completed).setOnClickListener {
            filter = OrderFilter.COMPLETED
            applyFilters()
        }
        requireViewById<TextView>(R.id.purchase_history_tab_failed).setOnClickListener {
            filter = OrderFilter.FAILED
            applyFilters()
        }
        updateOrderTabsModernUi()

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = applyFilters()
            override fun afterTextChanged(s: Editable?) = Unit
        })
    }

    private fun loadOrders() {
        lifecycleScope.launch {
            error.visibility = View.GONE
            empty.visibility = View.GONE
            setLoading(true)
            val session = activeSessionOrReturnToLogin() ?: return@launch
            val result = runCatching { authApi.orders(session) }
            setLoading(false)

            result
                .onSuccess {
                    allOrders = it.orders
                    applyFilters()
                }
                .onFailure {
                    error.text = it.message ?: getString(R.string.purchase_history_load_failed)
                    error.visibility = View.VISIBLE
                }
        }
    }

    private suspend fun activeSessionOrReturnToLogin(): AuthSession? {
        val savedSession = withContext(Dispatchers.IO) {
            tokenStore.getSession()
        } ?: return redirectToLogin()

        if (!JwtUtils.isExpired(savedSession.accessToken)) return savedSession

        val refreshed = runCatching {
            authApi.refresh(savedSession)
        }.getOrNull() ?: return redirectToLogin()

        withContext(Dispatchers.IO) {
            tokenStore.save(refreshed)
        }
        return refreshed
    }



    private fun setupBottomNavigation() {
        val bottomNav = requireViewById<BottomNavigationView>(R.id.purchase_history_bottom_nav)
        bottomNav.selectedItemId = R.id.nav_more
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
    }

    private fun showOrderFilterSheet() {
        val dialog = BottomSheetDialog(this)
        val content = layoutInflater.inflate(R.layout.order_filter_bottom_sheet, null)
        dialog.setContentView(content)

        content.findViewById<TextView>(R.id.order_filter_all).setOnClickListener {
            dateFilter = null
            applyFilters()
            dialog.dismiss()
        }

        content.findViewById<TextView>(R.id.order_filter_today).setOnClickListener {
            dateFilter = "TODAY"
            applyFilters()
            dialog.dismiss()
        }

        content.findViewById<TextView>(R.id.order_filter_month).setOnClickListener {
            dateFilter = "MONTH"
            applyFilters()
            dialog.dismiss()
        }

        dialog.show()
    }


    private fun updateOrderTabsModernUi() {
        val density = resources.displayMetrics.density

        fun styleTab(viewId: Int, selected: Boolean) {
            val tv = requireViewById<TextView>(viewId)

            tv.setPadding(
                (8 * density).toInt(),
                (8 * density).toInt(),
                (8 * density).toInt(),
                (8 * density).toInt()
            )
            tv.textSize = 12f
            tv.maxLines = 1
            tv.isSingleLine = true
            tv.includeFontPadding = false
            tv.gravity = android.view.Gravity.CENTER

            if (selected) {
                tv.setTextColor(Color.parseColor("#2F5BFF"))
                tv.setTypeface(tv.typeface, android.graphics.Typeface.BOLD)
                tv.background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 16f * density
                    setColor(Color.parseColor("#F1F5FF"))
                    setStroke((1 * density).toInt(), Color.parseColor("#D6E2FF"))
                }
            } else {
                tv.setTextColor(Color.parseColor("#667085"))
                tv.setTypeface(tv.typeface, android.graphics.Typeface.NORMAL)
                tv.background = null
            }
        }

        styleTab(R.id.purchase_history_tab_all, filter == OrderFilter.ALL)
        styleTab(R.id.purchase_history_tab_pending, filter == OrderFilter.PENDING)
        styleTab(R.id.purchase_history_tab_completed, filter == OrderFilter.COMPLETED)
        styleTab(R.id.purchase_history_tab_failed, filter == OrderFilter.FAILED)
    }

    private fun applyFilters() {
        updateOrderTabsModernUi()
        val q = search.text?.toString()?.trim().orEmpty().lowercase()
        val filtered = allOrders
            .filter { filter.matches(it.status) }
            .filter { matchesDateFilter(it) }
            .filter { order ->
                q.isBlank() || listOfNotNull(
                    order.id,
                    order.orderNumber,
                    order.displayNumber(),
                    order.packageName,
                    PackageNameCleaner.clean(order.packageName),
                    order.price,
                    order.status,
                    order.statusLabel(),
                    order.provider,
                    providerDisplayName(order.provider),
                    order.createdAt,
                    order.customerName(),
                    order.customerPhone,
                    order.customerEmail,
                    order.esim?.customerName(),
                    order.esim?.customerPhone,
                    order.esim?.customerEmail,
                    order.esim?.iccid
                ).joinToString(" ").lowercase().contains(q)
            }
        renderOrders(filtered)
    }


    private fun matchesDateFilter(order: MobileOrder): Boolean {
        val filterValue = dateFilter?.uppercase() ?: return true
        val created = parseOrderDate(order.createdAt) ?: return false
        val today = LocalDate.now()

        return when (filterValue) {
            "TODAY" -> created.toLocalDate() == today
            "MONTH" -> {
                val d = created.toLocalDate()
                d.year == today.year && d.month == today.month
            }
            else -> true
        }
    }

    private fun parseOrderDate(value: String?): OffsetDateTime? {
        if (value.isNullOrBlank()) return null
        return runCatching { OffsetDateTime.parse(value) }.getOrNull()
    }


    private fun formatOrderDate(value: String): String {
        val parsed = runCatching { OffsetDateTime.parse(value) }.getOrNull() ?: return value
        return parsed.format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm", Locale.ENGLISH))
    }


    private fun renderOrders(orderData: List<MobileOrder>) {
        orders.removeAllViews()
        empty.visibility = if (orderData.isEmpty()) View.VISIBLE else View.GONE

        requireViewById<TextView>(R.id.purchase_history_tab_all).text = "All"
        requireViewById<TextView>(R.id.purchase_history_tab_pending).text = "Pending"
        requireViewById<TextView>(R.id.purchase_history_tab_completed).text = "Done"
        requireViewById<TextView>(R.id.purchase_history_tab_failed).text = "Failed"

        val inflater = layoutInflater

        orderData.forEach { order ->
            val item = inflater.inflate(R.layout.order_history_item, orders, false)

            item.requireViewById<TextView>(R.id.history_order_number).text =
                order.displayNumber()?.takeIf { it.isNotBlank() } ?: order.orderNumber ?: order.id ?: "Order"

            val customerName = order.customerName()?.takeIf { it.isNotBlank() }
            val customerPhone = order.customerPhone?.takeIf { it.isNotBlank() }
            val customerEmail = order.customerEmail?.takeIf { it.isNotBlank() }
            item.requireViewById<TextView>(R.id.history_customer_phone).text =
                listOfNotNull(customerName, customerPhone, customerEmail).joinToString(" • ").ifBlank { "Customer details unavailable" }

            item.requireViewById<TextView>(R.id.history_package_name).text =
                PackageNameCleaner.clean(order.packageName).orEmpty().ifBlank { order.packageName ?: "Package" }

            item.requireViewById<TextView>(R.id.history_created_date).text =
                order.createdAt?.takeIf { it.isNotBlank() }?.let { formatOrderDate(it) }.orEmpty()

            item.requireViewById<TextView>(R.id.history_price).text =
                formatOrderPrice(order.price.orEmpty())

            val logoView = item.requireViewById<android.widget.ImageView>(R.id.history_provider_logo)
            val providerText = item.requireViewById<TextView>(R.id.history_provider)
            logoView.setImageResource(R.drawable.r2w_order_doc_icon)
            logoView.visibility = View.VISIBLE
            providerText.text = ""
            providerText.visibility = View.GONE

            item.requireViewById<TextView>(R.id.history_status).applyOrderStatusBadge(
                order.statusLabel(),
                order.status
            )

            item.setOnClickListener {
                startActivity(MobileOrderDetailActivity.createIntent(this, order))
            }
            orders.addView(item)
        }
    }

    private fun setLoading(loading: Boolean) {
        refresh.isRefreshing = loading
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





    private fun TextView.applyOrderStatusBadge(label: String?, rawStatus: String?) {
        val display = label?.takeIf { it.isNotBlank() } ?: rawStatus.orEmpty()
        val normalized = listOfNotNull(rawStatus, display).joinToString(" ").lowercase()

        val isCompleted =
            normalized.contains("complete") ||
                normalized.contains("completed") ||
                normalized.contains("confirmed") ||
                normalized.contains("confirm") ||
                normalized.contains("success") ||
                normalized.contains("succeeded") ||
                normalized.contains("installed") ||
                normalized.contains("active")

        val isFailed =
            normalized.contains("fail") ||
                normalized.contains("failed") ||
                normalized.contains("cancel") ||
                normalized.contains("cancelled") ||
                normalized.contains("error") ||
                normalized.contains("rejected")

        val backgroundColor: Int
        val textColor: Int

        when {
            isFailed -> {
                backgroundColor = Color.rgb(254, 226, 226)
                textColor = Color.rgb(185, 28, 28)
            }
            isCompleted -> {
                backgroundColor = Color.rgb(220, 252, 231)
                textColor = Color.rgb(22, 101, 52)
            }
            else -> {
                backgroundColor = Color.rgb(254, 249, 195)
                textColor = Color.rgb(133, 77, 14)
            }
        }

        text = when {
            isFailed -> "Cancelled"
            isCompleted && normalized.contains("confirm") -> "Active"
            isCompleted -> "Completed"
            else -> "Pending"
        }
        setTextColor(textColor)
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 999f
            setColor(backgroundColor)
        }
    }

    private fun formatOrderPrice(value: String): String {
        val clean = value.trim()
        if (clean.isBlank()) return "$0"
        if (clean.startsWith("$") || clean.startsWith("€") || clean.startsWith("£")) return clean
        return "$$clean"
    }

    private fun providerLogoRes(provider: String?): Int {
        val p = provider.orEmpty().lowercase()
        return when {
            p.contains("tgt") -> R.drawable.order_orange_logo
            p.contains("esimcard") -> R.drawable.order_orange_logo
            p.contains("orange") -> R.drawable.order_orange_logo
            p.contains("airhubapp") -> R.drawable.vodafone_logo
            p.contains("vodafone") -> R.drawable.vodafone_logo
            p.contains("airalo") -> R.drawable.airalo_logo
            else -> 0
        }
    }

    private fun providerDisplayName(provider: String?): String {
        val p = provider.orEmpty().trim()
        return when {
            p.isBlank() -> ""
            p.equals("tgt", ignoreCase = true) -> "Orange"
            p.contains("orange", ignoreCase = true) -> "Orange"
            p.contains("esimcard", ignoreCase = true) -> "Orange"
            p.contains("airhubapp", ignoreCase = true) -> "Vodafone"
            p.contains("vodafone", ignoreCase = true) -> "Vodafone"
            p.contains("airalo", ignoreCase = true) -> "Airalo"
            else -> p
        }
    }

    private enum class OrderFilter {
        ALL,
        PENDING,
        COMPLETED,
        FAILED;

        fun matches(statusValue: String?): Boolean {
            val status = statusValue.orEmpty().lowercase()
            return when (this) {
                ALL -> true
                PENDING -> status.contains("pending") || status.contains("processing") || status.contains("waiting")
                COMPLETED -> status.contains("completed") || status.contains("complete") || status.contains("confirmed") || status.contains("success") || status.contains("paid")
                FAILED -> status.contains("failed") || status.contains("failure") || status.contains("cancel") || status.contains("refund") || status.contains("error")
            }
        }
    }
}
