package im.angry.openeuicc.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.google.android.material.color.MaterialColors
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import im.angry.openeuicc.auth.AuthTokenStore
import im.angry.openeuicc.auth.JwtUtils
import im.angry.openeuicc.auth.Roam2WorldAuthApi
import im.angry.openeuicc.common.BuildConfig
import im.angry.openeuicc.common.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LoginActivity : AppCompatActivity() {
    private val tokenStore by lazy { AuthTokenStore(this) }
    private val authApi by lazy { Roam2WorldAuthApi(BuildConfig.ROAM2WORLD_API_BASE_URL) }

    private lateinit var emailLayout: TextInputLayout
    private lateinit var passwordLayout: TextInputLayout
    private lateinit var emailInput: TextInputEditText
    private lateinit var passwordInput: TextInputEditText
    private lateinit var loginButton: MaterialButton
    private lateinit var progress: LinearProgressIndicator
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)
        setupInsets()

        emailLayout = requireViewById(R.id.email_layout)
        passwordLayout = requireViewById(R.id.password_layout)
        emailInput = requireViewById(R.id.email_input)
        passwordInput = requireViewById(R.id.password_input)
        loginButton = requireViewById(R.id.login_button)
        progress = requireViewById(R.id.login_progress)
        statusText = requireViewById(R.id.login_status)

        loginButton.setOnClickListener { submitLogin() }
        passwordInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submitLogin()
                true
            } else {
                false
            }
        }

        restoreSession()
    }

    private fun setupInsets() {
        val root = requireViewById<View>(R.id.login_root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            view.updatePadding(bars.left, bars.top, bars.right, bars.bottom)
            WindowInsetsCompat.CONSUMED
        }
    }

    private fun restoreSession() {
        lifecycleScope.launch {
            val savedSession = withContext(Dispatchers.IO) {
                tokenStore.getSession()
            } ?: return@launch

            setBusy(true, getString(R.string.login_checking_session))
            val activeSession = if (!JwtUtils.isExpired(savedSession.accessToken)) {
                savedSession
            } else {
                runCatching {
                    authApi.refresh(savedSession).also { refreshed ->
                        withContext(Dispatchers.IO) {
                            tokenStore.save(refreshed)
                        }
                    }
                }.getOrNull()
            }

            if (activeSession != null) {
                openMainActivity()
            } else {
                withContext(Dispatchers.IO) {
                    tokenStore.clear()
                }
                setBusy(false)
                showStatus(getString(R.string.login_session_expired), isError = true)
            }
        }
    }

    private fun submitLogin() {
        val email = emailInput.text?.toString()?.trim().orEmpty()
        val password = passwordInput.text?.toString().orEmpty()

        emailLayout.error = null
        passwordLayout.error = null

        var valid = true
        if (email.isBlank()) {
            emailLayout.error = getString(R.string.login_email_required)
            valid = false
        }
        if (password.isBlank()) {
            passwordLayout.error = getString(R.string.login_password_required)
            valid = false
        }
        if (!valid) return

        lifecycleScope.launch {
            setBusy(true, getString(R.string.login_signing_in))
            val result = runCatching {
                authApi.login(email, password).also { session ->
                    withContext(Dispatchers.IO) {
                        tokenStore.save(session)
                    }
                }
            }

            result
                .onSuccess { openMainActivity() }
                .onFailure {
                    setBusy(false)
                    showStatus(it.message ?: getString(R.string.login_session_expired), isError = true)
                }
        }
    }

    private fun openMainActivity() {
        val target = targetActivityName()
        if (target.isNullOrBlank()) {
            setBusy(false)
            showStatus(getString(R.string.login_missing_target), isError = true)
            return
        }

        startActivity(
            Intent().setClassName(this, target).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
        )
        finish()
    }

    private fun setBusy(busy: Boolean, message: String? = null) {
        loginButton.isEnabled = !busy
        emailInput.isEnabled = !busy
        passwordInput.isEnabled = !busy
        progress.visibility = if (busy) View.VISIBLE else View.GONE
        if (message == null) {
            statusText.visibility = View.INVISIBLE
        } else {
            showStatus(message, isError = false)
        }
    }

    private fun showStatus(message: String, isError: Boolean) {
        statusText.text = message
        statusText.setTextColor(
            if (isError) {
                MaterialColors.getColor(statusText, com.google.android.material.R.attr.colorError)
            } else {
                MaterialColors.getColor(statusText, com.google.android.material.R.attr.colorOnSurfaceVariant)
            }
        )
        statusText.visibility = View.VISIBLE
    }

    private fun targetActivityName(): String? {
        val appInfo = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
        return appInfo.metaData?.getString(META_TARGET_ACTIVITY)
    }

    companion object {
        const val META_TARGET_ACTIVITY = "im.angry.openeuicc.LOGIN_TARGET_ACTIVITY"
    }
}
