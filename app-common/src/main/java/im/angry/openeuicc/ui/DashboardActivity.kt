package im.angry.openeuicc.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.progressindicator.LinearProgressIndicator
import im.angry.openeuicc.auth.AuthSession
import im.angry.openeuicc.auth.AuthTokenStore
import im.angry.openeuicc.auth.JwtUtils
import im.angry.openeuicc.auth.MobileDashboardData
import im.angry.openeuicc.auth.MobileDashboardOrder
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
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit

class DashboardActivity : AppCompatActivity() {
    private val tokenStore by lazy { AuthTokenStore(this) }
    private val authApi by lazy { Roam2WorldAuthApi(BuildConfig.ROAM2WORLD_API_BASE_URL) }

    private lateinit var scroll: View
    private lateinit var content: LinearLayout
    private lateinit var bottomNav: BottomNavigationView
    private lateinit var progress: LinearProgressIndicator
    private lateinit var greeting: TextView
    private lateinit var account: TextView
    private lateinit var balance: TextView
    private lateinit var activeEsims: TextView
    private lateinit var ordersSummary: TextView
    private lateinit var expiredSoonCard: MaterialCardView
    private lateinit var expiredSoonValue: TextView
    private lateinit var expiredSoonSubtitle: TextView
    private lateinit var dealerSummaryCard: View
    private lateinit var dealerSummary: TextView
    private lateinit var manageDealers: MaterialButton
    private lateinit var error: TextView
    private lateinit var orders: LinearLayout
    private var currentRole: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)
        setSupportActionBar(requireViewById(R.id.toolbar))
        supportActionBar?.title = getString(R.string.dashboard_title)

        scroll = requireViewById(R.id.dashboard_scroll)
        content = requireViewById(R.id.dashboard_content)
        bottomNav = requireViewById(R.id.dashboard_bottom_nav)
        progress = requireViewById(R.id.dashboard_progress)
        greeting = requireViewById(R.id.dashboard_greeting)
        account = requireViewById(R.id.dashboard_account)
        balance = requireViewById(R.id.dashboard_balance)
        activeEsims = requireViewById(R.id.dashboard_active_esims)
        ordersSummary = requireViewById(R.id.dashboard_orders_summary)
        dealerSummaryCard = requireViewById(R.id.dashboard_dealer_summary_card)
        dealerSummary = requireViewById(R.id.dashboard_dealer_summary)
        manageDealers = requireViewById(R.id.dashboard_manage_dealers)
        error = requireViewById(R.id.dashboard_error)
        orders = requireViewById(R.id.dashboard_orders)

        setupInsets()
        setupBottomNavigation()
        setupDashboardAdditions()
        setupQuickActions()
        renderPlaceholders()
        authApi.logMobileEndpointConfiguration()
        loadDashboard()
    }

    override fun onResume() {
        super.onResume()
        bottomNav.selectedItemId = R.id.nav_dashboard
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.activity_dashboard, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.my_dealers)?.isVisible = currentRole?.lowercase() == "reseller"
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
            R.id.reload -> {
                loadDashboard()
                true
            }

            R.id.purchase_history -> {
                openPurchaseHistoryActivity()
                true
            }

            R.id.my_dealers -> {
                openMyDealersActivity()
                true
            }

            R.id.logout -> {
                logout()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }

    private fun setupInsets() {
        setupRootViewSystemBarInsets(
            window.decorView.rootView,
            arrayOf(
                this::activityToolbarInsetHandler,
                mainViewPaddingInsetHandler(scroll),
                { insets ->
                    bottomNav.updatePadding(
                        insets.left,
                        bottomNav.paddingTop,
                        insets.right,
                        insets.bottom
                    )
                }
            ),
            consume = false
        )
    }

    private fun setupBottomNavigation() {
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_dashboard -> true
                R.id.nav_packages -> {
                    openPackagesActivity()
                    false
                }
                R.id.nav_wallet -> {
                    openWalletActivity()
                    false
                }
                R.id.nav_esims -> {
                    openEsimActivity()
                    false
                }
                R.id.nav_more -> {
                    openMoreActivity()
                    false
                }
                else -> false
            }
        }
        bottomNav.selectedItemId = R.id.nav_dashboard
    }

    private fun setupDashboardAdditions() {
        addExpiredSoonKpiCard()
        addRenewalQuickActions()
    }

    private fun setupQuickActions() {
        requireViewById<MaterialButton>(R.id.dashboard_browse_packages).setOnClickListener {
            openPackagesActivity()
        }
        requireViewById<MaterialButton>(R.id.dashboard_request_balance).setOnClickListener {
            startActivity(Intent(this, WalletRequestActivity::class.java))
        }
        requireViewById<MaterialButton>(R.id.dashboard_view_history).setOnClickListener {
            openPurchaseHistoryActivity()
        }
        manageDealers.setOnClickListener {
            openMyDealersActivity()
        }
    }

    private fun renderPlaceholders() {
        greeting.text = getString(R.string.dashboard_greeting)
        account.text = ""
        balance.text = "--"
        activeEsims.text = "--"
        ordersSummary.text = "--"
        expiredSoonValue.text = "--"
        expiredSoonSubtitle.text = "Loading expiring eSIMs"
        dealerSummary.text = getString(R.string.dashboard_dealer_summary_value)
        dealerSummaryCard.visibility = View.GONE
        manageDealers.visibility = View.GONE
        renderOrders(emptyList())
    }

    private fun loadDashboard() {
        lifecycleScope.launch {
            error.visibility = View.GONE
            setLoading(true)
            val session = activeSessionOrReturnToLogin() ?: return@launch
            renderSession(session)

            val result = runCatching {
                authApi.dashboard(session)
            }
            val esimsResult = runCatching {
                authApi.esims(session).esims
            }
            setLoading(false)

            result
                .onSuccess { renderDashboard(it) }
                .onFailure {
                    error.text = it.message ?: getString(R.string.dashboard_load_failed)
                    error.visibility = View.VISIBLE
                }
            esimsResult.onSuccess { renderExpiredSoon(it) }
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

    private fun renderSession(session: AuthSession) {
        currentRole = session.role
        invalidateOptionsMenu()
        val isReseller = session.role?.lowercase() == "reseller"
        dealerSummaryCard.visibility = if (isReseller) View.VISIBLE else View.GONE
        manageDealers.visibility = if (isReseller) View.VISIBLE else View.GONE
        greeting.text = session.displayName?.let { "Welcome, $it" }
            ?: getString(R.string.dashboard_greeting)
        account.text = listOfNotNull(
            session.role?.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() },
            session.email
        ).joinToString(" - ")
    }

    private fun renderDashboard(data: MobileDashboardData) {
        balance.text = data.currentBalance
        activeEsims.text = data.activeEsimCount
        ordersSummary.text = data.recentOrders.size.toString()
        renderOrders(data.recentOrders)
    }

    private fun renderExpiredSoon(esimData: List<MobileEsim>) {
        val now = OffsetDateTime.now()
        val expiring = esimData.mapNotNull { esim ->
            val expires = esim.expiresAt?.let { runCatching { OffsetDateTime.parse(it) }.getOrNull() } ?: return@mapNotNull null
            val days = ChronoUnit.DAYS.between(now, expires)
            if (days in 0..7 && esim.status?.equals("expired", ignoreCase = true) != true) esim to days else null
        }.sortedBy { it.second }

        expiredSoonValue.text = expiring.size.toString()
        expiredSoonSubtitle.text = if (expiring.isEmpty()) {
            "No eSIMs expiring in 7 days"
        } else {
            expiring.take(3).joinToString("\n") { (esim, days) ->
                val label = esim.iccid?.takeLast(6)?.let { "*$it" } ?: esim.title()
                "$label - ${days.coerceAtLeast(0)}d left"
            }
        }
    }

    private fun renderOrders(orderData: List<MobileDashboardOrder>) {
        orders.removeAllViews()
        if (orderData.isEmpty()) {
            TextView(this).apply {
                text = getString(R.string.dashboard_empty_orders)
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
                setTextColor(com.google.android.material.color.MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant))
                orders.addView(this)
            }
            return
        }

        val inflater = LayoutInflater.from(this)
        orderData.forEach { order ->
            val item = inflater.inflate(R.layout.dashboard_order_item, orders, false)
            item.requireViewById<TextView>(R.id.order_title).text = order.title
            item.requireViewById<TextView>(R.id.order_subtitle).text = order.subtitle
            item.requireViewById<TextView>(R.id.order_amount).apply {
                text = order.amount.orEmpty()
                visibility = if (order.amount.isNullOrBlank()) View.GONE else View.VISIBLE
            }
            item.requireViewById<TextView>(R.id.order_status).apply {
                applyRoamStatusChip(order.status, order.status)
            }
            orders.addView(item)
        }
    }

    private fun addExpiredSoonKpiCard() {
        val parent = activeEsims.parent?.parent?.parent as? LinearLayout ?: return
        expiredSoonCard = MaterialCardView(this).apply {
            radius = dp(18).toFloat()
            cardElevation = dp(3).toFloat()
            strokeWidth = dp(1)
            setStrokeColor(getColor(R.color.r2w_border))
            setCardBackgroundColor(getColor(R.color.r2w_card))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(14)
            }
        }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(16))
        }
        body.addView(TextView(this).apply {
            text = "Expired Soon"
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelLarge)
            setTextColor(getColor(R.color.r2w_text_secondary))
        })
        expiredSoonValue = TextView(this).apply {
            text = "--"
            setPadding(0, dp(6), 0, 0)
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_HeadlineMedium)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(getColor(R.color.r2w_text_primary))
        }
        body.addView(expiredSoonValue)
        expiredSoonSubtitle = TextView(this).apply {
            text = "Loading expiring eSIMs"
            setPadding(0, dp(4), 0, 0)
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
            setTextColor(getColor(R.color.r2w_text_secondary))
        }
        body.addView(expiredSoonSubtitle)
        expiredSoonCard.addView(body)
        parent.addView(expiredSoonCard)
    }

    private fun addRenewalQuickActions() {
        val quickActions = requireViewById<MaterialButton>(R.id.dashboard_view_history).parent?.parent as? LinearLayout ?: return
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBaselineAligned(false)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(12) }
        }
        row.addView(createQuickActionButton("TGT\nRecharge") {
            startActivity(Intent(this, TgtSimRechargeActivity::class.java))
        }, LinearLayout.LayoutParams(0, dp(82), 1f).apply { rightMargin = dp(7) })
        row.addView(createQuickActionButton("Vodafone\nRenewal") {
            startActivity(Intent(this, VodafoneRenewalActivity::class.java))
        }, LinearLayout.LayoutParams(0, dp(82), 1f).apply { leftMargin = dp(7) })
        quickActions.addView(row)
    }

    private fun createQuickActionButton(label: String, action: () -> Unit): MaterialButton =
        MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = label
            gravity = android.view.Gravity.CENTER
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            setTextColor(getColor(R.color.r2w_text_primary))
            cornerRadius = dp(20)
            icon = getDrawable(R.drawable.ic_packages)
            iconGravity = MaterialButton.ICON_GRAVITY_TOP
            iconPadding = dp(6)
            strokeColor = android.content.res.ColorStateList.valueOf(getColor(R.color.r2w_border))
            setOnClickListener { action() }
        }

    private fun setLoading(loading: Boolean) {
        progress.visibility = if (loading) View.VISIBLE else View.GONE
    }

    private fun openWalletActivity() {
        startActivity(
            Intent(this, WalletActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            }
        )
    }

    private fun openPackagesActivity() {
        startActivity(
            Intent(this, PackagesActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            }
        )
    }

    private fun openEsimActivity() {
        startActivity(
            Intent(this, MobileEsimsActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            }
        )
    }

    private fun openMoreActivity() {
        startActivity(
            Intent(this, MoreActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            }
        )
    }

    private fun openPurchaseHistoryActivity() {
        startActivity(Intent(this, PurchaseHistoryActivity::class.java))
    }

    private fun openMyDealersActivity() {
        startActivity(Intent(this, MyDealersActivity::class.java))
    }

    private fun logout() {
        lifecycleScope.launch {
            val session = withContext(Dispatchers.IO) {
                tokenStore.getSession().also {
                    tokenStore.clear()
                }
            }
            session?.let {
                runCatching {
                    authApi.logout(it)
                }
            }
            openLoginActivity()
        }
    }

    private fun redirectToLogin(): AuthSession? {
        tokenStore.clear()
        openLoginActivity()
        return null
    }

    private fun openLoginActivity() {
        startActivity(
            Intent(this, LoginActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
        )
        finish()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val META_ESIM_ACTIVITY = "im.angry.openeuicc.DASHBOARD_ESIM_ACTIVITY"
    }
}
