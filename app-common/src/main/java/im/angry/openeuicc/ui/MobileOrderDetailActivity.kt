package im.angry.openeuicc.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
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

class MobileOrderDetailActivity : AppCompatActivity() {
    private val tokenStore by lazy { AuthTokenStore(this) }
    private val authApi by lazy { Roam2WorldAuthApi(BuildConfig.ROAM2WORLD_API_BASE_URL) }

    private lateinit var progress: LinearProgressIndicator
    private lateinit var error: TextView
    private lateinit var viewEsimButton: MaterialButton
    private lateinit var esimUnavailable: TextView

    private var currentOrder: MobileOrder? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_order_detail)
        setSupportActionBar(requireViewById(R.id.toolbar))
        supportActionBar?.apply {
            title = getString(R.string.order_detail_title)
            setDisplayHomeAsUpEnabled(true)
        }

        progress = requireViewById(R.id.order_detail_progress)
        error = requireViewById(R.id.order_detail_error)
        viewEsimButton = requireViewById(R.id.order_detail_view_esim)
        esimUnavailable = requireViewById(R.id.order_detail_esim_unavailable)

        setupInsets()
        renderOrder(readIntentOrder())
        loadLatestDetails()
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
                mainViewPaddingInsetHandler(requireViewById(R.id.order_detail_scroll))
            ),
            consume = false
        )
    }

    private fun loadLatestDetails() {
        lifecycleScope.launch {
            val selected = currentOrder ?: return@launch
            val orderId = selected.id ?: return@launch
            error.visibility = View.GONE
            setLoading(true)
            val session = activeSessionOrReturnToLogin()
            if (session == null) {
                setLoading(false)
                return@launch
            }
            val result = runCatching { authApi.order(session, orderId) }
            setLoading(false)

            result
                .onSuccess { renderOrder(it) }
                .onFailure {
                    error.text = it.message ?: getString(R.string.order_detail_load_failed)
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

    private fun renderOrder(order: MobileOrder?) {
        currentOrder = order
        if (order == null) {
            error.text = getString(R.string.order_detail_missing)
            error.visibility = View.VISIBLE
            return
        }

        requireViewById<TextView>(R.id.order_detail_heading).text = order.displayNumber()
        setOptionalText(R.id.order_detail_number, order.displayNumber(), R.string.order_detail_number_format)
        setOptionalText(R.id.order_detail_package, order.packageName, R.string.order_detail_package_format)
        setOptionalText(R.id.order_detail_provider, order.provider, R.string.order_detail_provider_format)
        setOptionalText(R.id.order_detail_price, order.price, R.string.order_detail_price_format)
        setOptionalText(R.id.order_detail_status, order.statusLabel(), R.string.order_detail_status_format)
        setOptionalText(R.id.order_detail_created, order.createdAt, R.string.order_detail_created_format)
        setOptionalText(R.id.order_detail_esim_id, order.esimId, R.string.order_detail_esim_id_format)
        renderActions(order)
    }

    private fun renderActions(order: MobileOrder) {
        val esim = order.esim
        val esimId = order.esimId
        val hasEsim = esim != null || !esimId.isNullOrBlank()
        viewEsimButton.visibility = if (hasEsim) View.VISIBLE else View.GONE
        esimUnavailable.visibility = if (hasEsim) View.GONE else View.VISIBLE
        viewEsimButton.setOnClickListener {
            when {
                esim != null -> startActivity(MobileEsimDetailActivity.createIntent(this, esim))
                !esimId.isNullOrBlank() -> startActivity(MobileEsimDetailActivity.createIntent(this, esimId))
            }
        }
    }

    private fun setOptionalText(viewId: Int, value: String?, formatResId: Int) {
        requireViewById<TextView>(viewId).apply {
            text = value?.let { getString(formatResId, it) }.orEmpty()
            visibility = if (value.isNullOrBlank()) View.GONE else View.VISIBLE
        }
    }

    private fun setLoading(loading: Boolean) {
        progress.visibility = if (loading) View.VISIBLE else View.GONE
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

    private fun readIntentOrder(): MobileOrder? {
        if (listOf(EXTRA_ID, EXTRA_ORDER_NUMBER, EXTRA_PACKAGE_NAME, EXTRA_ESIM_ID).none {
                !intent.getStringExtra(it).isNullOrBlank()
            }
        ) {
            return null
        }

        return MobileOrder(
            id = intent.getStringExtra(EXTRA_ID),
            orderNumber = intent.getStringExtra(EXTRA_ORDER_NUMBER),
            packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: getString(R.string.order_detail_package_fallback),
            price = intent.getStringExtra(EXTRA_PRICE),
            status = intent.getStringExtra(EXTRA_STATUS),
            createdAt = intent.getStringExtra(EXTRA_CREATED_AT),
            provider = intent.getStringExtra(EXTRA_PROVIDER),
            esimId = intent.getStringExtra(EXTRA_ESIM_ID),
        )
    }

    companion object {
        private const val EXTRA_ID = "mobile_order.id"
        private const val EXTRA_ORDER_NUMBER = "mobile_order.order_number"
        private const val EXTRA_PACKAGE_NAME = "mobile_order.package_name"
        private const val EXTRA_PROVIDER = "mobile_order.provider"
        private const val EXTRA_PRICE = "mobile_order.price"
        private const val EXTRA_STATUS = "mobile_order.status"
        private const val EXTRA_CREATED_AT = "mobile_order.created_at"
        private const val EXTRA_ESIM_ID = "mobile_order.esim_id"

        fun createIntent(context: Context, order: MobileOrder): Intent =
            Intent(context, MobileOrderDetailActivity::class.java).apply {
                putExtra(EXTRA_ID, order.id)
                putExtra(EXTRA_ORDER_NUMBER, order.orderNumber)
                putExtra(EXTRA_PACKAGE_NAME, order.packageName)
                putExtra(EXTRA_PROVIDER, order.provider)
                putExtra(EXTRA_PRICE, order.price)
                putExtra(EXTRA_STATUS, order.status)
                putExtra(EXTRA_CREATED_AT, order.createdAt)
                putExtra(EXTRA_ESIM_ID, order.esimId)
            }
    }
}
