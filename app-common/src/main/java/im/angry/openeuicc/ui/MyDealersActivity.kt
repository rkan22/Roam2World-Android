package im.angry.openeuicc.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.button.MaterialButton
import im.angry.openeuicc.auth.AuthSession
import im.angry.openeuicc.auth.AuthTokenStore
import im.angry.openeuicc.auth.JwtUtils
import im.angry.openeuicc.auth.MobileDealer
import im.angry.openeuicc.auth.Roam2WorldAuthApi
import im.angry.openeuicc.common.BuildConfig
import im.angry.openeuicc.common.R
import im.angry.openeuicc.util.activityToolbarInsetHandler
import im.angry.openeuicc.util.mainViewPaddingInsetHandler
import im.angry.openeuicc.util.setupRootViewSystemBarInsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MyDealersActivity : AppCompatActivity() {
    private val tokenStore by lazy { AuthTokenStore(this) }
    private val authApi by lazy { Roam2WorldAuthApi(BuildConfig.ROAM2WORLD_API_BASE_URL) }

    private lateinit var refresh: SwipeRefreshLayout
    private lateinit var dealers: LinearLayout
    private lateinit var summary: TextView
    private lateinit var empty: TextView
    private lateinit var error: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_my_dealers)
        setSupportActionBar(requireViewById(R.id.toolbar))
        supportActionBar?.apply {
            title = getString(R.string.dealers_title)
            setDisplayHomeAsUpEnabled(true)
        }

        refresh = requireViewById(R.id.my_dealers_refresh)
        dealers = requireViewById(R.id.my_dealers_items)
        summary = requireViewById(R.id.my_dealers_summary)
        empty = requireViewById(R.id.my_dealers_empty)
        error = requireViewById(R.id.my_dealers_error)

        setupInsets()
        requireViewById<MaterialButton>(R.id.my_dealers_add).setOnClickListener {
            startActivity(Intent(this, AddDealerActivity::class.java))
        }
        refresh.setOnRefreshListener { loadDealers() }
        renderDealers(emptyList())
        loadDealers()
    }

    override fun onResume() {
        super.onResume()
        if (::refresh.isInitialized) loadDealers()
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

    private fun loadDealers() {
        lifecycleScope.launch {
            error.visibility = View.GONE
            empty.visibility = View.GONE
            setLoading(true)
            val session = activeSessionOrReturnToLogin()
            if (session == null) {
                setLoading(false)
                return@launch
            }
            if (session.role?.lowercase() != "reseller") {
                setLoading(false)
                error.text = getString(R.string.dealers_reseller_only)
                error.visibility = View.VISIBLE
                renderDealers(emptyList())
                return@launch
            }

            val result = runCatching { authApi.dealers(session) }
            setLoading(false)
            result
                .onSuccess { renderDealers(it.dealers) }
                .onFailure {
                    error.text = it.message ?: getString(R.string.dealers_load_failed)
                    error.visibility = View.VISIBLE
                }
        }
    }

    private fun renderDealers(dealerData: List<MobileDealer>) {
        dealers.removeAllViews()
        summary.text = getString(R.string.dealers_summary_format, dealerData.size.toString())
        empty.visibility = if (dealerData.isEmpty()) View.VISIBLE else View.GONE
        if (dealerData.isEmpty()) return

        val inflater = LayoutInflater.from(this)
        dealerData.forEach { dealer ->
            val item = inflater.inflate(R.layout.dealer_list_item, dealers, false)
            item.requireViewById<TextView>(R.id.dealer_item_name).text = dealer.name
            item.requireViewById<TextView>(R.id.dealer_item_balance).text =
                getString(R.string.dealer_balance_format, dealer.currentBalance)
            item.requireViewById<TextView>(R.id.dealer_item_orders).text =
                getString(R.string.dealer_orders_format, dealer.totalOrders)
            item.requireViewById<TextView>(R.id.dealer_item_revenue).text =
                getString(R.string.dealer_revenue_format, dealer.revenue)
            item.requireViewById<TextView>(R.id.dealer_item_status)
                .applyRoamStatusChip(dealer.statusLabel(), dealer.status)
            item.setOnClickListener { openDealerDetail(dealer) }
            dealers.addView(item)
        }
    }

    private fun openDealerDetail(dealer: MobileDealer) {
        val id = dealer.id ?: return
        startActivity(
            Intent(this, DealerDetailActivity::class.java)
                .putExtra(DealerDetailActivity.EXTRA_DEALER_ID, id)
                .putExtra(DealerDetailActivity.EXTRA_DEALER_NAME, dealer.name)
        )
    }

    private fun setLoading(loading: Boolean) {
        refresh.isRefreshing = loading
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
}
