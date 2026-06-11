package im.angry.openeuicc.ui

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
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
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class TransactionsActivity : AppCompatActivity() {

    private fun formatTransactionDate(value: String?): String {
        val raw = value?.trim().orEmpty()
        if (raw.isBlank()) return ""
        return try {
            val formatter = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm", Locale.ENGLISH)
            Instant.parse(raw).atZone(ZoneId.systemDefault()).format(formatter)
        } catch (_: Exception) {
            raw
        }
    }

    private fun formatTransactionStatus(value: String?): String {
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

    private var allOrders: List<MobileOrder> = emptyList()
    private var selectedFilter = TransactionFilter.ALL

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_transactions)
        setSupportActionBar(requireViewById(R.id.toolbar))
        supportActionBar?.title = "Transactions"

        refresh = requireViewById(R.id.transactions_refresh)
        bottomNav = requireViewById(R.id.transactions_bottom_nav)
        summary = requireViewById(R.id.transactions_summary)
        empty = requireViewById(R.id.transactions_empty)
        error = requireViewById(R.id.transactions_error)
        list = requireViewById(R.id.transactions_list)
        search = requireViewById(R.id.transactions_search)

        setupInsets()
        setupBottomNavigation()
        setupTabs()
        setupSearch()
        refresh.setOnRefreshListener { loadOrders() }
        loadOrders()
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
                R.id.nav_dashboard -> { startActivity(Intent(this, DashboardActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)); false }
                R.id.nav_packages -> { startActivity(Intent(this, PackagesActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)); false }
                R.id.nav_wallet -> { startActivity(Intent(this, WalletActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)); false }
                R.id.nav_esims -> { startActivity(Intent(this, MobileEsimsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)); false }
                R.id.nav_more -> true
                else -> false
            }
        }
        bottomNav.menu.findItem(R.id.nav_more)?.isChecked = true
    }

    private fun setupTabs() {
        requireViewById<Chip>(R.id.transactions_tab_all).setOnClickListener { selectedFilter = TransactionFilter.ALL; applyFilters() }
        requireViewById<Chip>(R.id.transactions_tab_pending).setOnClickListener { selectedFilter = TransactionFilter.PENDING; applyFilters() }
        requireViewById<Chip>(R.id.transactions_tab_completed).setOnClickListener { selectedFilter = TransactionFilter.COMPLETED; applyFilters() }
        requireViewById<Chip>(R.id.transactions_tab_failed).setOnClickListener { selectedFilter = TransactionFilter.FAILED; applyFilters() }
    }

    private fun setupSearch() {
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
            refresh.isRefreshing = true
            val session = activeSessionOrReturnToLogin()
            if (session == null) {
                refresh.isRefreshing = false
                return@launch
            }
            val result = runCatching { authApi.orders(session).orders }
            refresh.isRefreshing = false
            result.onSuccess {
                allOrders = it
                applyFilters()
            }.onFailure {
                error.text = it.message ?: "Orders could not be loaded"
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
        startActivity(Intent(this, LoginActivity::class.java).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK) })
        finish()
        return null
    }

    private fun applyFilters() {
        val query = search.text?.toString()?.trim().orEmpty().lowercase()
        val filtered = allOrders.filter { selectedFilter.matches(it.status) }.filter { order ->
            query.isBlank() || listOfNotNull(order.orderNumber, order.id, order.packageName, order.price, order.status, order.provider, order.createdAt, order.esimId, order.customerEmail, order.esim?.customerName(), order.esim?.customerPhone, order.esim?.customerEmail, order.esim?.iccid)
                .joinToString(" ")
                .lowercase()
                .contains(query)
        }
        renderOrders(filtered)
    }

    private fun renderOrders(orders: List<MobileOrder>) {
        list.removeAllViews()
        empty.visibility = if (orders.isEmpty()) View.VISIBLE else View.GONE
        val pending = allOrders.count { TransactionFilter.PENDING.matches(it.status) }
        val completed = allOrders.count { TransactionFilter.COMPLETED.matches(it.status) }
        val failed = allOrders.count { TransactionFilter.FAILED.matches(it.status) }
        summary.text = "${orders.size} shown • ${allOrders.size} total • $pending pending • $completed completed • $failed failed"
        orders.forEach { list.addView(createOrderCard(it)) }
    }


    private fun visibleProvider(provider: String?): String =
        provider.orEmpty()
            .replace("TGT", "Orange", ignoreCase = true)
            .replace("tgt", "Orange", ignoreCase = true)
            .ifBlank { "Unknown" }

    private fun createOrderCard(order: MobileOrder): View {
        val card = MaterialCardView(this).apply {
            radius = dp(18).toFloat()
            cardElevation = dp(4).toFloat()
            strokeWidth = dp(1)
            setStrokeColor(getColor(R.color.r2w_premium_border))
            setCardBackgroundColor(getColor(R.color.r2w_premium_surface))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(14) }
        }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(16))
        }
        body.addView(label(order.displayNumber(), true, com.google.android.material.R.style.TextAppearance_Material3_TitleLarge))
        body.addView(label(PackageNameCleaner.clean(order.packageName), true, com.google.android.material.R.style.TextAppearance_Material3_TitleMedium).apply { setPadding(0, dp(6), 0, 0) })
        body.addView(label(
            listOfNotNull(
                order.esim?.customerName()?.let { "Customer: $it" },
                order.esim?.customerPhone?.let { "Phone: $it" },
                order.esim?.iccid?.let { "ICCID: $it" },
                order.provider?.let { "Provider: ${visibleProvider(it)}" },
                order.price?.let { "Amount: $it" },
                order.createdAt?.let { "Date: ${formatTransactionDate(it)}" }
            ).joinToString("\n").ifBlank { "No extra order details" },
            false,
            com.google.android.material.R.style.TextAppearance_Material3_BodySmall
        ).apply { setPadding(0, dp(8), 0, 0) })
        body.addView(label("Status: ${formatTransactionStatus(order.statusLabel().orEmpty().ifBlank { order.status.orEmpty() })}", false).apply { setPadding(0, dp(10), 0, 0) })
        body.addView(button("View Order Detail") {
            startActivity(MobileOrderDetailActivity.createIntent(this, order))
        }.apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48)).apply { topMargin = dp(14) }
        })
        card.addView(body)
        return card
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
            gravity = Gravity.CENTER
            cornerRadius = dp(14)
            setTextColor(getColor(R.color.r2w_premium_primary))
            strokeColor = ColorStateList.valueOf(getColor(R.color.r2w_premium_border))
            strokeWidth = dp(1)
            backgroundTintList = ColorStateList.valueOf(getColor(R.color.r2w_premium_surface))
            setOnClickListener { action() }
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private enum class TransactionFilter {
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
