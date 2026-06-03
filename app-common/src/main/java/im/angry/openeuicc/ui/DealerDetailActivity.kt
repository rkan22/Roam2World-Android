package im.angry.openeuicc.ui

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.google.android.material.progressindicator.LinearProgressIndicator
import im.angry.openeuicc.auth.AuthSession
import im.angry.openeuicc.auth.AuthTokenStore
import im.angry.openeuicc.auth.JwtUtils
import im.angry.openeuicc.auth.MobileDealer
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

class DealerDetailActivity : AppCompatActivity() {
    private val tokenStore by lazy { AuthTokenStore(this) }
    private val authApi by lazy { Roam2WorldAuthApi(BuildConfig.ROAM2WORLD_API_BASE_URL) }

    private lateinit var scroll: View
    private lateinit var progress: LinearProgressIndicator
    private lateinit var error: TextView
    private lateinit var name: TextView
    private lateinit var email: TextView
    private lateinit var status: TextView
    private lateinit var balance: TextView
    private lateinit var stats: TextView
    private lateinit var allocate: MaterialButton
    private lateinit var suspend: MaterialButton
    private lateinit var activate: MaterialButton
    private lateinit var orders: LinearLayout

    private val dealerId: String? by lazy { intent.getStringExtra(EXTRA_DEALER_ID) }
    private var currentDealer: MobileDealer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dealer_detail)
        setSupportActionBar(requireViewById(R.id.toolbar))
        supportActionBar?.apply {
            title = intent.getStringExtra(EXTRA_DEALER_NAME) ?: getString(R.string.dealer_detail_title)
            setDisplayHomeAsUpEnabled(true)
        }

        scroll = requireViewById(R.id.dealer_detail_scroll)
        progress = requireViewById(R.id.dealer_detail_progress)
        error = requireViewById(R.id.dealer_detail_error)
        name = requireViewById(R.id.dealer_detail_name)
        email = requireViewById(R.id.dealer_detail_email)
        status = requireViewById(R.id.dealer_detail_status)
        balance = requireViewById(R.id.dealer_detail_balance)
        stats = requireViewById(R.id.dealer_detail_stats)
        allocate = requireViewById(R.id.dealer_detail_allocate)
        suspend = requireViewById(R.id.dealer_detail_suspend)
        activate = requireViewById(R.id.dealer_detail_activate)
        orders = requireViewById(R.id.dealer_detail_orders)

        setupInsets()
        allocate.setOnClickListener { openAllocateBalance() }
        suspend.setOnClickListener { updateDealerStatus(suspendDealer = true) }
        activate.setOnClickListener { updateDealerStatus(suspendDealer = false) }
        loadDealer()
    }

    override fun onResume() {
        super.onResume()
        if (::progress.isInitialized) loadDealer()
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
                mainViewPaddingInsetHandler(scroll)
            ),
            consume = false
        )
    }

    private fun loadDealer() {
        val id = dealerId
        if (id.isNullOrBlank()) {
            error.text = getString(R.string.dealer_detail_missing)
            error.visibility = View.VISIBLE
            return
        }

        lifecycleScope.launch {
            error.visibility = View.GONE
            setLoading(true)
            val session = activeSessionOrReturnToLogin()
            if (session == null) {
                setLoading(false)
                return@launch
            }

            val result = runCatching { authApi.dealer(session, id) }
            setLoading(false)
            result
                .onSuccess { renderDealer(it) }
                .onFailure {
                    error.text = it.message ?: getString(R.string.dealer_detail_load_failed)
                    error.visibility = View.VISIBLE
                }
        }
    }

    private fun renderDealer(dealer: MobileDealer) {
        currentDealer = dealer
        supportActionBar?.title = dealer.name
        name.text = dealer.name
        email.text = dealer.email.orEmpty()
        status.text = dealer.statusLabel()
        status.backgroundTintList = ColorStateList.valueOf(statusColor(dealer.status))
        balance.text = getString(R.string.dealer_balance_format, dealer.currentBalance)
        stats.text = getString(
            R.string.dealer_detail_stats_format,
            dealer.totalOrders,
            dealer.revenue,
            dealer.totalAllocated ?: "0",
            dealer.totalSpent ?: "0"
        )

        val isSuspended = dealer.status.equals("suspended", ignoreCase = true)
        suspend.visibility = if (isSuspended) View.GONE else View.VISIBLE
        activate.visibility = if (isSuspended) View.VISIBLE else View.GONE
        renderOrders(dealer.recentOrders)
    }

    private fun renderOrders(orderData: List<MobileOrder>) {
        orders.removeAllViews()
        if (orderData.isEmpty()) {
            TextView(this).apply {
                text = getString(R.string.dealer_recent_orders_empty)
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
                setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant))
                orders.addView(this)
            }
            return
        }

        val inflater = LayoutInflater.from(this)
        orderData.forEach { order ->
            val item = inflater.inflate(R.layout.order_history_item, orders, false)
            item.requireViewById<TextView>(R.id.history_order_number).text = order.displayNumber()
            item.requireViewById<TextView>(R.id.history_package_name).text = order.packageName
            item.requireViewById<TextView>(R.id.history_created_date).text = order.createdAt.orEmpty()
            item.requireViewById<TextView>(R.id.history_price).text = order.price.orEmpty()
            item.requireViewById<TextView>(R.id.history_status).text = order.statusLabel().orEmpty()
            orders.addView(item)
        }
    }

    private fun openAllocateBalance() {
        val dealer = currentDealer ?: return
        val id = dealer.id ?: return
        startActivity(
            Intent(this, AllocateBalanceActivity::class.java)
                .putExtra(AllocateBalanceActivity.EXTRA_DEALER_ID, id)
                .putExtra(AllocateBalanceActivity.EXTRA_DEALER_NAME, dealer.name)
        )
    }

    private fun updateDealerStatus(suspendDealer: Boolean) {
        val id = dealerId ?: return
        lifecycleScope.launch {
            error.visibility = View.GONE
            setLoading(true)
            val session = activeSessionOrReturnToLogin()
            if (session == null) {
                setLoading(false)
                return@launch
            }

            val result = runCatching {
                if (suspendDealer) authApi.suspendDealer(session, id) else authApi.activateDealer(session, id)
            }
            setLoading(false)
            result
                .onSuccess { renderDealer(it) }
                .onFailure {
                    error.text = it.message ?: getString(R.string.dealer_status_update_failed)
                    error.visibility = View.VISIBLE
                }
        }
    }

    private fun statusColor(status: String): Int =
        when (status.lowercase()) {
            "active" -> MaterialColors.getColor(window.decorView, com.google.android.material.R.attr.colorPrimaryContainer)
            "suspended" -> MaterialColors.getColor(window.decorView, com.google.android.material.R.attr.colorErrorContainer)
            else -> MaterialColors.getColor(window.decorView, com.google.android.material.R.attr.colorSecondaryContainer)
        }

    private fun setLoading(loading: Boolean) {
        progress.visibility = if (loading) View.VISIBLE else View.GONE
        allocate.isEnabled = !loading
        suspend.isEnabled = !loading
        activate.isEnabled = !loading
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

    companion object {
        const val EXTRA_DEALER_ID = "dealer_id"
        const val EXTRA_DEALER_NAME = "dealer_name"
    }
}
