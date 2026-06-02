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

class WalletRequestActivity : AppCompatActivity() {
    private val tokenStore by lazy { AuthTokenStore(this) }
    private val authApi by lazy { Roam2WorldAuthApi(BuildConfig.ROAM2WORLD_API_BASE_URL) }

    private lateinit var amountLayout: TextInputLayout
    private lateinit var currencyLayout: TextInputLayout
    private lateinit var noteLayout: TextInputLayout
    private lateinit var amountInput: TextInputEditText
    private lateinit var currencyInput: TextInputEditText
    private lateinit var noteInput: TextInputEditText
    private lateinit var submit: MaterialButton
    private lateinit var history: MaterialButton
    private lateinit var progress: LinearProgressIndicator
    private lateinit var error: TextView
    private lateinit var success: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wallet_request)
        setSupportActionBar(requireViewById(R.id.toolbar))
        supportActionBar?.apply {
            title = getString(R.string.wallet_request_title)
            setDisplayHomeAsUpEnabled(true)
        }

        amountLayout = requireViewById(R.id.wallet_request_amount_layout)
        currencyLayout = requireViewById(R.id.wallet_request_currency_layout)
        noteLayout = requireViewById(R.id.wallet_request_note_layout)
        amountInput = requireViewById(R.id.wallet_request_amount)
        currencyInput = requireViewById(R.id.wallet_request_currency)
        noteInput = requireViewById(R.id.wallet_request_note)
        submit = requireViewById(R.id.wallet_request_submit)
        history = requireViewById(R.id.wallet_request_view_history)
        progress = requireViewById(R.id.wallet_request_progress)
        error = requireViewById(R.id.wallet_request_error)
        success = requireViewById(R.id.wallet_request_success)

        currencyInput.setText("USD")
        setupInsets()
        submit.setOnClickListener { submitRequest() }
        history.setOnClickListener {
            startActivity(Intent(this, WalletRequestHistoryActivity::class.java))
        }
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
                mainViewPaddingInsetHandler(requireViewById(R.id.wallet_request_scroll))
            ),
            consume = false
        )
    }

    private fun submitRequest() {
        clearMessages()
        val amount = amountInput.text?.toString()?.trim().orEmpty()
        val currency = currencyInput.text?.toString()?.trim()?.uppercase().orEmpty()
        val note = noteInput.text?.toString()?.trim().orEmpty()

        if (amount.toBigDecimalOrNull()?.let { it > BigDecimal.ZERO } != true) {
            amountLayout.error = getString(R.string.wallet_request_amount_required)
            return
        }
        if (currency.length != 3 || !currency.all { it.isLetter() }) {
            currencyLayout.error = getString(R.string.wallet_request_currency_required)
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
                authApi.createWalletRequest(session, amount, currency, note)
            }
            setLoading(false)
            result
                .onSuccess {
                    success.text = getString(R.string.wallet_request_created, it.amount, it.currency)
                    success.visibility = View.VISIBLE
                    amountInput.text?.clear()
                    noteInput.text?.clear()
                }
                .onFailure {
                    error.text = it.message ?: getString(R.string.wallet_request_failed)
                    error.visibility = View.VISIBLE
                }
        }
    }

    private fun clearMessages() {
        amountLayout.error = null
        currencyLayout.error = null
        noteLayout.error = null
        error.visibility = View.GONE
        success.visibility = View.GONE
    }

    private fun setLoading(loading: Boolean) {
        progress.visibility = if (loading) View.VISIBLE else View.GONE
        submit.isEnabled = !loading
        history.isEnabled = !loading
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
