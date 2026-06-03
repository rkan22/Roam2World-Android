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
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import im.angry.openeuicc.auth.AuthSession
import im.angry.openeuicc.auth.AuthTokenStore
import im.angry.openeuicc.auth.JwtUtils
import im.angry.openeuicc.auth.MobileTransaction
import im.angry.openeuicc.auth.MobileWalletData
import im.angry.openeuicc.auth.MobileWalletRequest
import im.angry.openeuicc.auth.Roam2WorldAuthApi
import im.angry.openeuicc.common.BuildConfig
import im.angry.openeuicc.common.R
import im.angry.openeuicc.util.activityToolbarInsetHandler
import im.angry.openeuicc.util.mainViewPaddingInsetHandler
import im.angry.openeuicc.util.setupRootViewSystemBarInsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WalletActivity : AppCompatActivity() {
    private val tokenStore by lazy { AuthTokenStore(this) }
    private val authApi by lazy { Roam2WorldAuthApi(BuildConfig.ROAM2WORLD_API_BASE_URL) }

    private lateinit var refresh: SwipeRefreshLayout
    private lateinit var bottomNav: BottomNavigationView
    private lateinit var balance: TextView
    private lateinit var error: TextView
    private lateinit var requests: LinearLayout
    private lateinit var transactions: LinearLayout
    private lateinit var requestBalance: MaterialButton
    private lateinit var requestHistory: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wallet)
        setSupportActionBar(requireViewById(R.id.toolbar))
        supportActionBar?.title = getString(R.string.wallet_title)

        refresh = requireViewById(R.id.wallet_refresh)
        bottomNav = requireViewById(R.id.wallet_bottom_nav)
        balance = requireViewById(R.id.wallet_balance)
        error = requireViewById(R.id.wallet_error)
        requests = requireViewById(R.id.wallet_requests)
        transactions = requireViewById(R.id.wallet_transactions)
        requestBalance = requireViewById(R.id.wallet_request_balance)
        requestHistory = requireViewById(R.id.wallet_request_history)

        setupInsets()
        setupBottomNavigation()
        setupRefresh()
        setupActions()
        renderPlaceholders()
        loadWallet()
    }

    override fun onResume() {
        super.onResume()
        bottomNav.selectedItemId = R.id.nav_wallet
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.activity_dashboard, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
            R.id.reload -> {
                loadWallet()
                true
            }

            R.id.purchase_history -> {
                openPurchaseHistoryActivity()
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
                mainViewPaddingInsetHandler(refresh),
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
                R.id.nav_dashboard -> {
                    openDashboardActivity()
                    false
                }
                R.id.nav_packages -> {
                    openPackagesActivity()
                    false
                }
                R.id.nav_wallet -> true
                R.id.nav_esims -> {
                    openEsimActivity()
                    false
                }
                else -> false
            }
        }
        bottomNav.selectedItemId = R.id.nav_wallet
    }

    private fun setupRefresh() {
        refresh.setOnRefreshListener {
            loadWallet()
        }
    }

    private fun setupActions() {
        requestBalance.setOnClickListener {
            startActivity(Intent(this, WalletRequestActivity::class.java))
        }
        requestHistory.setOnClickListener {
            startActivity(Intent(this, WalletRequestHistoryActivity::class.java))
        }
    }

    private fun renderPlaceholders() {
        balance.text = "--"
        renderRequests(emptyList())
        renderTransactions(emptyList())
    }

    private fun loadWallet() {
        lifecycleScope.launch {
            error.visibility = View.GONE
            setLoading(true)
            val session = activeSessionOrReturnToLogin() ?: return@launch
            val walletResult = runCatching {
                authApi.wallet(session)
            }
            val requestResult = runCatching {
                authApi.walletRequests(session)
            }
            setLoading(false)

            walletResult
                .onSuccess { renderWallet(it) }
                .onFailure {
                    error.text = it.message ?: getString(R.string.wallet_load_failed)
                    error.visibility = View.VISIBLE
                }
            requestResult
                .onSuccess { renderRequests(it.take(3)) }
                .onFailure { renderRequests(emptyList(), it.message ?: getString(R.string.wallet_request_history_failed)) }
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

    private fun renderWallet(data: MobileWalletData) {
        balance.text = data.currentBalance
        renderTransactions(data.transactions)
    }

    private fun renderRequests(requestData: List<MobileWalletRequest>, errorText: String? = null) {
        requests.removeAllViews()
        if (!errorText.isNullOrBlank()) {
            TextView(this).apply {
                text = errorText
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
                setTextColor(com.google.android.material.color.MaterialColors.getColor(this, com.google.android.material.R.attr.colorError))
                requests.addView(this)
            }
            return
        }
        if (requestData.isEmpty()) {
            TextView(this).apply {
                text = getString(R.string.wallet_request_history_empty)
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
                setTextColor(com.google.android.material.color.MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant))
                requests.addView(this)
            }
            return
        }

        val inflater = LayoutInflater.from(this)
        requestData.forEach { request ->
            val item = inflater.inflate(R.layout.wallet_request_history_item, requests, false)
            item.requireViewById<TextView>(R.id.wallet_request_item_amount).text =
                getString(R.string.wallet_request_amount_currency, request.amount, request.currency)
            item.requireViewById<TextView>(R.id.wallet_request_item_status)
                .applyRoamStatusChip(request.statusLabel(), request.status)
            item.requireViewById<TextView>(R.id.wallet_request_item_created).apply {
                text = request.createdAt.orEmpty()
                visibility = if (request.createdAt.isNullOrBlank()) View.GONE else View.VISIBLE
            }
            item.requireViewById<TextView>(R.id.wallet_request_item_note).apply {
                text = request.note.orEmpty()
                visibility = if (request.note.isNullOrBlank()) View.GONE else View.VISIBLE
            }
            item.requireViewById<TextView>(R.id.wallet_request_item_reviewed).apply {
                text = request.reviewedAt?.let { getString(R.string.wallet_request_reviewed_at, it) }.orEmpty()
                visibility = if (request.reviewedAt.isNullOrBlank()) View.GONE else View.VISIBLE
            }
            requests.addView(item)
        }
    }

    private fun renderTransactions(transactionData: List<MobileTransaction>) {
        transactions.removeAllViews()
        if (transactionData.isEmpty()) {
            TextView(this).apply {
                text = getString(R.string.wallet_empty_transactions)
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
                setTextColor(com.google.android.material.color.MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant))
                transactions.addView(this)
            }
            return
        }

        val inflater = LayoutInflater.from(this)
        transactionData.forEach { transaction ->
            val item = inflater.inflate(R.layout.wallet_transaction_item, transactions, false)
            item.requireViewById<TextView>(R.id.transaction_title).text = transaction.title
            item.requireViewById<TextView>(R.id.transaction_subtitle).text = transaction.subtitle
            item.requireViewById<TextView>(R.id.transaction_amount).text = transaction.amount
            item.requireViewById<TextView>(R.id.transaction_status).apply {
                applyRoamStatusChip(transaction.status, transaction.status)
            }
            transactions.addView(item)
        }
    }

    private fun setLoading(loading: Boolean) {
        refresh.isRefreshing = loading
    }

    private fun openDashboardActivity() {
        startActivity(
            Intent(this, DashboardActivity::class.java).apply {
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

    private fun openPurchaseHistoryActivity() {
        startActivity(Intent(this, PurchaseHistoryActivity::class.java))
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

}
