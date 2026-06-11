package im.angry.openeuicc.ui

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
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import im.angry.openeuicc.auth.AuthSession
import im.angry.openeuicc.auth.AuthTokenStore
import im.angry.openeuicc.auth.JwtUtils
import im.angry.openeuicc.auth.Roam2WorldAuthApi
import im.angry.openeuicc.common.BuildConfig
import im.angry.openeuicc.common.R
import im.angry.openeuicc.util.activityToolbarInsetHandler
import im.angry.openeuicc.util.mainViewPaddingInsetHandler
import im.angry.openeuicc.util.setupRootViewSystemBarInsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal

class AllocateBalanceActivity : AppCompatActivity() {
    private val tokenStore by lazy { AuthTokenStore(this) }
    private val authApi by lazy { Roam2WorldAuthApi(BuildConfig.ROAM2WORLD_API_BASE_URL) }

    private lateinit var amountLayout: TextInputLayout
    private lateinit var amountInput: TextInputEditText
    private lateinit var submit: MaterialButton
    private lateinit var progress: LinearProgressIndicator
    private lateinit var error: TextView
    private lateinit var success: TextView

    private val dealerId: String? by lazy { intent.getStringExtra(EXTRA_DEALER_ID) }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_allocate_balance)
        setSupportActionBar(requireViewById(R.id.toolbar))
        supportActionBar?.apply {
            title = getString(R.string.dealer_allocate_title)
            setDisplayHomeAsUpEnabled(true)
        }

        amountLayout = requireViewById(R.id.allocate_balance_amount_layout)
        amountInput = requireViewById(R.id.allocate_balance_amount)
        submit = requireViewById(R.id.allocate_balance_submit)
        progress = requireViewById(R.id.allocate_balance_progress)
        error = requireViewById(R.id.allocate_balance_error)
        success = requireViewById(R.id.allocate_balance_success)
        requireViewById<TextView>(R.id.allocate_balance_dealer).text =
            intent.getStringExtra(EXTRA_DEALER_NAME) ?: getString(R.string.dealer_detail_title)

        setupInsets()
        submit.setOnClickListener { submitAllocation() }
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
                mainViewPaddingInsetHandler(requireViewById(R.id.allocate_balance_scroll))
            ),
            consume = false
        )
    }

    private fun submitAllocation() {
        clearMessages()
        val id = dealerId
        if (id.isNullOrBlank()) {
            error.text = getString(R.string.dealer_detail_missing)
            error.visibility = View.VISIBLE
            return
        }

        val amount = amountInput.text?.toString()?.trim().orEmpty()
        if (amount.toBigDecimalOrNull()?.let { it > BigDecimal.ZERO } != true) {
            amountLayout.error = getString(R.string.dealer_allocate_amount_required)
            return
        }

        lifecycleScope.launch {
            setLoading(true)
            val session = activeSessionOrReturnToLogin()
            if (session == null) {
                setLoading(false)
                return@launch
            }

            val result = runCatching {
                authApi.allocateDealerBalance(session, id, amount)
            }
            setLoading(false)
            result
                .onSuccess {
                    success.text = getString(
                        R.string.dealer_allocate_success,
                        it.amount,
                        it.currency,
                        it.dealer.currentBalance
                    )
                    success.visibility = View.VISIBLE
                    amountInput.text?.clear()
                    setResult(RESULT_OK)
                    finish()
                }
                .onFailure {
                    error.text = it.message ?: getString(R.string.dealer_allocate_failed)
                    error.visibility = View.VISIBLE
                }
        }
    }

    private fun clearMessages() {
        amountLayout.error = null
        error.visibility = View.GONE
        success.visibility = View.GONE
    }

    private fun setLoading(loading: Boolean) {
        progress.visibility = if (loading) View.VISIBLE else View.GONE
        submit.isEnabled = !loading
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
