package im.angry.openeuicc.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.lifecycleScope
import im.angry.openeuicc.auth.AuthSession
import im.angry.openeuicc.auth.AuthTokenStore
import im.angry.openeuicc.auth.JwtUtils
import im.angry.openeuicc.auth.Roam2WorldAuthApi
import im.angry.openeuicc.common.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal

class WalletRequestActivity : ComponentActivity() {
    private val tokenStore by lazy { AuthTokenStore(this) }
    private val authApi by lazy { Roam2WorldAuthApi(BuildConfig.ROAM2WORLD_API_BASE_URL) }

    private var amount by mutableStateOf("")
    private var currency by mutableStateOf("USD")
    private var note by mutableStateOf("")
    private var loading by mutableStateOf(false)
    private var errorMessage by mutableStateOf<String?>(null)
    private var successMessage by mutableStateOf<String?>(null)
    private var amountError by mutableStateOf<String?>(null)
    private var currencyError by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            R2wMockScreen(
                title = "Wallet Request",
                subtitle = "Request balance top-up",
                onBack = { finish() },
                bottomTab = R2wBottomTab.Wallet
            ) {
                R2wMockHero(
                    eyebrow = "Wallet",
                    title = "Top up request",
                    body = "Submit balance requests to admin and track approval status from history.",
                    amount = "${currency.ifBlank { "USD" }} ${amount.ifBlank { "0.00" }}",
                    badge = "Pending"
                )

                R2wMockCard("Request details") {
                    R2wMockTextField(
                        value = amount,
                        onValueChange = {
                            amount = it
                            amountError = null
                            errorMessage = null
                            successMessage = null
                        },
                        label = "Amount",
                        isError = amountError != null,
                        error = amountError
                    )
                    R2wMockTextField(
                        value = currency,
                        onValueChange = {
                            currency = it.uppercase().take(3)
                            currencyError = null
                        },
                        label = "Currency",
                        isError = currencyError != null,
                        error = currencyError
                    )
                    R2wMockTextField(value = note, onValueChange = { note = it }, label = "Note", singleLine = false, minLines = 3)
                }

                R2wMockCard("Approval flow") {
                    R2wMockStep("1", "Create", "You submit the amount, currency and optional note.")
                    R2wMockStep("2", "Review", "Admin checks the request and payment status.")
                    R2wMockStep("3", "Wallet updated", "Approved amount is added to your wallet balance.")
                }

                errorMessage?.let { R2wMockCard("Request failed") { Text(it, color = R2wMockColors.Danger, fontWeight = FontWeight.Bold) } }
                successMessage?.let { R2wMockCard("Request created") { Text(it, color = R2wMockColors.Success, fontWeight = FontWeight.Bold) } }

                R2wMockPrimaryButton(if (loading) "Submitting..." else "Submit request", enabled = !loading, onClick = { submitRequest() })
                R2wMockSecondaryButton("View request history", enabled = !loading) {
                    startActivity(Intent(this@WalletRequestActivity, WalletRequestHistoryActivity::class.java))
                }
            }
        }
    }

    private fun submitRequest() {
        clearMessages()
        val cleanAmount = amount.trim()
        val cleanCurrency = currency.trim().uppercase()
        val cleanNote = note.trim()

        if (cleanAmount.toBigDecimalOrNull()?.let { it > BigDecimal.ZERO } != true) {
            amountError = "Amount is required"
            return
        }
        if (cleanCurrency.length != 3 || !cleanCurrency.all { it.isLetter() }) {
            currencyError = "Currency must be 3 letters"
            return
        }

        lifecycleScope.launch {
            loading = true
            val session = activeSessionOrReturnToLogin()
            if (session == null) {
                loading = false
                return@launch
            }

            val result = runCatching { authApi.createWalletRequest(session, cleanAmount, cleanCurrency, cleanNote) }
            loading = false
            result
                .onSuccess {
                    successMessage = "Wallet request created: ${it.amount} ${it.currency}"
                    amount = ""
                    note = ""
                    currency = cleanCurrency
                }
                .onFailure { errorMessage = it.message ?: "Wallet request failed" }
        }
    }

    private fun clearMessages() {
        amountError = null
        currencyError = null
        errorMessage = null
        successMessage = null
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
        startActivity(Intent(this, LoginActivity::class.java).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK) })
        finish()
        return null
    }
}
