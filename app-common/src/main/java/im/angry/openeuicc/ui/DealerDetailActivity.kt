package im.angry.openeuicc.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.app.AlertDialog
import android.text.InputType
import android.widget.EditText
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
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
    private lateinit var refund: MaterialButton
    private lateinit var suspend: MaterialButton
    private lateinit var activate: MaterialButton
    private lateinit var orders: LinearLayout

    private val dealerId: String? by lazy { intent.getStringExtra(EXTRA_DEALER_ID) }
    private var currentDealer: MobileDealer? = null

    private val allocateBalanceLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        loadDealer()
    }

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
        refund = requireViewById(R.id.dealer_detail_refund)
        suspend = requireViewById(R.id.dealer_detail_suspend)
        activate = requireViewById(R.id.dealer_detail_activate)
        orders = requireViewById(R.id.dealer_detail_orders)

        setupInsets()
        allocate.setOnClickListener { openAllocateBalance() }
        refund.setOnClickListener { openRefundBalance() }
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
        status.applyRoamStatusChip(dealer.statusLabel(), dealer.status)
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


    private fun visibleProvider(provider: String?): String =
        provider.orEmpty()
            .replace("TGT", "Orange", ignoreCase = true)
            .replace("tgt", "Orange", ignoreCase = true)
            .ifBlank { "Orange" }

    private fun renderOrders(orderData: List<MobileOrder>) {
        orders.removeAllViews()
        if (orderData.isEmpty()) {
            TextView(this).apply {
                text = getString(R.string.dealer_recent_orders_empty)
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
                setTextColor(getColor(R.color.r2w_premium_muted))
                orders.addView(this)
            }
            return
        }

        val inflater = LayoutInflater.from(this)
        orderData.forEach { order ->
            val item = inflater.inflate(R.layout.order_history_item, orders, false)
            item.requireViewById<TextView>(R.id.history_order_number).text = order.displayNumber()
            item.requireViewById<TextView>(R.id.history_package_name).text = PackageNameCleaner.clean(order.packageName)
            item.requireViewById<TextView>(R.id.history_created_date).text = order.createdAt.orEmpty()
            item.requireViewById<TextView>(R.id.history_price).text = order.price.orEmpty()
            item.requireViewById<TextView>(R.id.history_provider).applyRoamProviderChip(visibleProvider(order.provider))
            item.requireViewById<TextView>(R.id.history_status).applyRoamStatusChip(order.statusLabel(), order.status)
            orders.addView(item)
        }
    }

    private fun openAllocateBalance() {
        val dealer = currentDealer ?: return
        val id = dealer.id ?: return
        allocateBalanceLauncher.launch(
            Intent(this, AllocateBalanceActivity::class.java)
                .putExtra(AllocateBalanceActivity.EXTRA_DEALER_ID, id)
                .putExtra(AllocateBalanceActivity.EXTRA_DEALER_NAME, dealer.name)
        )
    }

    private fun openRefundBalance() {
        val dealer = currentDealer ?: return
        val id = dealer.id ?: return

        val view = LayoutInflater.from(this).inflate(R.layout.dialog_refund_money, null, false)
        val amountLayout = view.requireViewById<TextInputLayout>(R.id.refund_amount_layout)
        val amountInput = view.requireViewById<TextInputEditText>(R.id.refund_amount_input)
        val reasonInput = view.requireViewById<TextInputEditText>(R.id.refund_reason_input)
        val message = view.requireViewById<TextView>(R.id.refund_dialog_message)
        val cancel = view.requireViewById<MaterialButton>(R.id.refund_cancel)
        val submit = view.requireViewById<MaterialButton>(R.id.refund_submit)

        message.text = getString(R.string.dealer_refund_message, dealer.name)
        reasonInput.setText(getString(R.string.dealer_refund_default_reason))

        val dialog = MaterialAlertDialogBuilder(this)
            .setView(view)
            .create()

        cancel.setOnClickListener {
            dialog.dismiss()
        }

        submit.setOnClickListener {
            val amount = amountInput.text?.toString().orEmpty().trim().replace(",", ".")
            val amountValue = amount.toDoubleOrNull()
            if (amountValue == null || amountValue <= 0.0) {
                amountLayout.error = getString(R.string.dealer_allocate_amount_required)
                return@setOnClickListener
            }

            val reason = reasonInput.text?.toString().orEmpty().trim()
                .ifBlank { getString(R.string.dealer_refund_default_reason) }

            dialog.dismiss()
            refundDealerBalance(id, amount, reason)
        }

        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        }

        dialog.show()
    }

    private fun refundDealerBalance(dealerId: String, amount: String, reason: String) {
        lifecycleScope.launch {
            error.visibility = View.GONE
            setLoading(true)
            val session = activeSessionOrReturnToLogin()
            if (session == null) {
                setLoading(false)
                return@launch
            }

            val result = runCatching {
                authApi.modifyDealerBalance(
                    session = session,
                    dealerId = dealerId,
                    amount = amount,
                    direction = "refund_to_reseller",
                    reason = reason
                )
            }

            setLoading(false)
            result
                .onSuccess {
                    renderDealer(it.dealer)
                    Toast.makeText(
                        this@DealerDetailActivity,
                        getString(R.string.dealer_refund_success, it.amount, it.currency, it.dealer.currentBalance),
                        Toast.LENGTH_LONG
                    ).show()
                }
                .onFailure {
                    error.text = it.message ?: getString(R.string.dealer_refund_failed)
                    error.visibility = View.VISIBLE
                }
        }
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

    private fun setLoading(loading: Boolean) {
        progress.visibility = if (loading) View.VISIBLE else View.GONE
        allocate.isEnabled = !loading
        refund.isEnabled = !loading
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
