package im.angry.openeuicc.ui

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
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

class AddDealerActivity : AppCompatActivity() {
    private val tokenStore by lazy { AuthTokenStore(this) }
    private val authApi by lazy { Roam2WorldAuthApi(BuildConfig.ROAM2WORLD_API_BASE_URL) }

    private lateinit var firstNameLayout: TextInputLayout
    private lateinit var lastNameLayout: TextInputLayout
    private lateinit var emailLayout: TextInputLayout
    private lateinit var passwordLayout: TextInputLayout
    private lateinit var firstNameInput: TextInputEditText
    private lateinit var lastNameInput: TextInputEditText
    private lateinit var emailInput: TextInputEditText
    private lateinit var phoneInput: TextInputEditText
    private lateinit var countryCodeInput: TextInputEditText
    private lateinit var passwordInput: TextInputEditText
    private lateinit var initialBalanceInput: TextInputEditText
    private lateinit var error: TextView
    private lateinit var success: TextView
    private lateinit var progress: LinearProgressIndicator
    private lateinit var submit: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_dealer)
        setSupportActionBar(requireViewById(R.id.toolbar))
        supportActionBar?.apply {
            title = getString(R.string.dealer_add_title)
            setDisplayHomeAsUpEnabled(true)
        }

        firstNameLayout = requireViewById(R.id.add_dealer_first_name_layout)
        lastNameLayout = requireViewById(R.id.add_dealer_last_name_layout)
        emailLayout = requireViewById(R.id.add_dealer_email_layout)
        passwordLayout = requireViewById(R.id.add_dealer_password_layout)
        firstNameInput = requireViewById(R.id.add_dealer_first_name)
        lastNameInput = requireViewById(R.id.add_dealer_last_name)
        emailInput = requireViewById(R.id.add_dealer_email)
        phoneInput = requireViewById(R.id.add_dealer_phone)
        countryCodeInput = requireViewById(R.id.add_dealer_country_code)
        passwordInput = requireViewById(R.id.add_dealer_password)
        initialBalanceInput = requireViewById(R.id.add_dealer_initial_balance)
        error = requireViewById(R.id.add_dealer_error)
        success = requireViewById(R.id.add_dealer_success)
        progress = requireViewById(R.id.add_dealer_progress)
        submit = requireViewById(R.id.add_dealer_submit)

        setupInsets()
        submit.setOnClickListener { createDealer() }
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
                mainViewPaddingInsetHandler(requireViewById(R.id.add_dealer_scroll))
            ),
            consume = false
        )
    }

    private fun createDealer() {
        clearMessages()

        val firstName = firstNameInput.text?.toString()?.trim().orEmpty()
        val lastName = lastNameInput.text?.toString()?.trim().orEmpty()
        val email = emailInput.text?.toString()?.trim().orEmpty()
        val phone = phoneInput.text?.toString()?.trim().orEmpty()
        val countryCode = countryCodeInput.text?.toString()?.trim().orEmpty()
        val password = passwordInput.text?.toString()?.trim().orEmpty()
        val initialBalance = initialBalanceInput.text?.toString()?.trim().orEmpty()

        var valid = true
        if (firstName.isBlank() || lastName.isBlank()) {
            firstNameLayout.error = getString(R.string.dealer_add_name_required)
            lastNameLayout.error = getString(R.string.dealer_add_name_required)
            valid = false
        }
        if (email.isBlank() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailLayout.error = getString(R.string.dealer_add_email_required)
            valid = false
        }
        if (password.isBlank()) {
            passwordLayout.error = getString(R.string.dealer_add_password_required)
            valid = false
        }
        if (!valid) return

        lifecycleScope.launch {
            setLoading(true)
            val session = activeSessionOrReturnToLogin()
            if (session == null) {
                setLoading(false)
                return@launch
            }

            val result = runCatching {
                authApi.createDealer(
                    session = session,
                    firstName = firstName,
                    lastName = lastName,
                    email = email,
                    phoneNumber = phone,
                    countryCode = countryCode,
                    password = password,
                    initialBalance = initialBalance
                )
            }
            setLoading(false)

            result
                .onSuccess { dealer ->
                    success.text = getString(R.string.dealer_add_created)
                    success.visibility = View.VISIBLE
                    val id = dealer.id
                    if (!id.isNullOrBlank()) {
                        startActivity(
                            Intent(this@AddDealerActivity, DealerDetailActivity::class.java)
                                .putExtra(DealerDetailActivity.EXTRA_DEALER_ID, id)
                                .putExtra(DealerDetailActivity.EXTRA_DEALER_NAME, dealer.name)
                        )
                    }
                    finish()
                }
                .onFailure {
                    error.text = it.message ?: getString(R.string.dealer_add_failed)
                    error.visibility = View.VISIBLE
                }
        }
    }

    private fun clearMessages() {
        firstNameLayout.error = null
        lastNameLayout.error = null
        emailLayout.error = null
        passwordLayout.error = null
        error.visibility = View.GONE
        success.visibility = View.GONE
    }

    private fun setLoading(loading: Boolean) {
        progress.visibility = if (loading) View.VISIBLE else View.GONE
        submit.isEnabled = !loading
    }

    private suspend fun activeSessionOrReturnToLogin(): AuthSession? {
        val savedSession = withContext(Dispatchers.IO) { tokenStore.getSession() } ?: return redirectToLogin()
        if (!JwtUtils.isExpired(savedSession.accessToken)) return savedSession

        val refreshed = runCatching { authApi.refresh(savedSession) }.getOrNull() ?: return redirectToLogin()
        withContext(Dispatchers.IO) { tokenStore.save(refreshed) }
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
