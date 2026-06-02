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
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.color.MaterialColors
import im.angry.openeuicc.auth.AuthSession
import im.angry.openeuicc.auth.AuthTokenStore
import im.angry.openeuicc.auth.JwtUtils
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

class WalletRequestHistoryActivity : AppCompatActivity() {
    private val tokenStore by lazy { AuthTokenStore(this) }
    private val authApi by lazy { Roam2WorldAuthApi(BuildConfig.ROAM2WORLD_API_BASE_URL) }

    private lateinit var refresh: SwipeRefreshLayout
    private lateinit var requests: LinearLayout
    private lateinit var empty: TextView
    private lateinit var error: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wallet_request_history)
        setSupportActionBar(requireViewById(R.id.toolbar))
        supportActionBar?.apply {
            title = getString(R.string.wallet_request_history_title)
            setDisplayHomeAsUpEnabled(true)
        }

        refresh = requireViewById(R.id.wallet_request_history_refresh)
        requests = requireViewById(R.id.wallet_request_history_items)
        empty = requireViewById(R.id.wallet_request_history_empty)
        error = requireViewById(R.id.wallet_request_history_error)

        setupInsets()
        refresh.setOnRefreshListener { loadRequests() }
        renderRequests(emptyList())
        loadRequests()
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

    private fun loadRequests() {
        lifecycleScope.launch {
            error.visibility = View.GONE
            empty.visibility = View.GONE
            setLoading(true)
            val session = activeSessionOrReturnToLogin()
            if (session == null) {
                setLoading(false)
                return@launch
            }

            val result = runCatching { authApi.walletRequests(session) }
            setLoading(false)
            result
                .onSuccess { renderRequests(it) }
                .onFailure {
                    error.text = it.message ?: getString(R.string.wallet_request_history_failed)
                    error.visibility = View.VISIBLE
                }
        }
    }

    private fun renderRequests(requestData: List<MobileWalletRequest>) {
        requests.removeAllViews()
        empty.visibility = if (requestData.isEmpty()) View.VISIBLE else View.GONE
        if (requestData.isEmpty()) return

        val inflater = LayoutInflater.from(this)
        requestData.forEach { request ->
            val item = inflater.inflate(R.layout.wallet_request_history_item, requests, false)
            item.requireViewById<TextView>(R.id.wallet_request_item_amount).text =
                getString(R.string.wallet_request_amount_currency, request.amount, request.currency)
            item.requireViewById<TextView>(R.id.wallet_request_item_created).text =
                request.createdAt.orEmpty()
            item.requireViewById<TextView>(R.id.wallet_request_item_note).apply {
                text = request.note.orEmpty()
                visibility = if (request.note.isNullOrBlank()) View.GONE else View.VISIBLE
            }
            item.requireViewById<TextView>(R.id.wallet_request_item_reviewed).apply {
                text = request.reviewedAt?.let { getString(R.string.wallet_request_reviewed_at, it) }.orEmpty()
                visibility = if (request.reviewedAt.isNullOrBlank()) View.GONE else View.VISIBLE
            }
            item.requireViewById<TextView>(R.id.wallet_request_item_status).apply {
                text = request.statusLabel()
                backgroundTintList = ColorStateList.valueOf(statusColor(request.status))
            }
            requests.addView(item)
        }
    }

    private fun statusColor(status: String): Int =
        when (status.lowercase()) {
            "approved", "completed" -> MaterialColors.getColor(window.decorView, com.google.android.material.R.attr.colorPrimaryContainer)
            "rejected", "cancelled" -> MaterialColors.getColor(window.decorView, com.google.android.material.R.attr.colorErrorContainer)
            else -> MaterialColors.getColor(window.decorView, com.google.android.material.R.attr.colorSecondaryContainer)
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
