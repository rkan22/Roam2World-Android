package im.angry.openeuicc.ui

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

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_purchase_history)
        setSupportActionBar(requireViewById(R.id.toolbar))
        supportActionBar?.apply {
            title = "Orders"
            setDisplayHomeAsUpEnabled(true)
        }

        refresh = requireViewById(R.id.purchase_history_refresh)
        orders = requireViewById(R.id.purchase_history_orders)
        empty = requireViewById(R.id.purchase_history_empty)
        error = requireViewById(R.id.purchase_history_error)
        summary = requireViewById(R.id.purchase_history_summary)
        search = requireViewById(R.id.purchase_history_search)

        setupInsets()
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
        requireViewById<Chip>(R.id.purchase_history_tab_all).setOnClickListener {
            filter = OrderFilter.ALL
            applyFilters()
        }
        requireViewById<Chip>(R.id.purchase_history_tab_pending).setOnClickListener {
            filter = OrderFilter.PENDING
            applyFilters()
        }
        requireViewById<Chip>(R.id.purchase_history_tab_completed).setOnClickListener {
            filter = OrderFilter.COMPLETED
            applyFilters()
        }
        requireViewById<Chip>(R.id.purchase_history_tab_failed).setOnClickListener {
            filter = OrderFilter.FAILED
            applyFilters()
        }
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

    private fun applyFilters() {
        val q = search.text?.toString()?.trim().orEmpty().lowercase()
        val filtered = allOrders
            .filter { filter.matches(it.status) }
            .filter { order ->
                q.isBlank() || listOfNotNull(
                    order.id,
                    order.orderNumber,
                    order.packageName,
                    order.price,
                    order.status,
                    order.provider,
                    order.createdAt,
                    order.esim?.customerName(),
                    order.esim?.customerPhone,
                    order.esim?.iccid
                ).joinToString(" ").lowercase().contains(q)
            }
        renderOrders(filtered)
    }

    private fun renderOrders(orderData: List<MobileOrder>) {
        orders.removeAllViews()
        empty.visibility = if (orderData.isEmpty()) View.VISIBLE else View.GONE
        val pending = allOrders.count { OrderFilter.PENDING.matches(it.status) }
        val completed = allOrders.count { OrderFilter.COMPLETED.matches(it.status) }
        val failed = allOrders.count { OrderFilter.FAILED.matches(it.status) }
        summary.text = "${allOrders.size} orders - $pending pending - $completed completed - $failed failed"
        if (orderData.isEmpty()) return

        val inflater = LayoutInflater.from(this)
        orderData.forEach { order ->
            val item = inflater.inflate(R.layout.order_history_item, orders, false)
            item.requireViewById<TextView>(R.id.history_order_number).text = order.displayNumber()
            item.requireViewById<TextView>(R.id.history_package_name).text = PackageNameCleaner.clean(order.packageName)
            item.requireViewById<TextView>(R.id.history_created_date).apply {
                text = listOfNotNull(
                    order.createdAt,
                    order.esim?.customerName()?.let { "Customer: $it" },
                    order.esim?.iccid?.let { "ICCID: $it" }
                ).joinToString("\n")
                visibility = if (text.isNullOrBlank()) View.GONE else View.VISIBLE
            }
            item.requireViewById<TextView>(R.id.history_price).apply {
                text = order.price.orEmpty()
                visibility = if (order.price.isNullOrBlank()) View.GONE else View.VISIBLE
            }
            item.requireViewById<TextView>(R.id.history_provider).applyRoamProviderChip(order.provider)
            item.requireViewById<TextView>(R.id.history_status)
                .applyRoamStatusChip(order.statusLabel(), order.status)
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
