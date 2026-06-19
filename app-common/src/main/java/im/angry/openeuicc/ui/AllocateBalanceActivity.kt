package im.angry.openeuicc.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
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

class AllocateBalanceActivity : ComponentActivity() {
    private val tokenStore by lazy { AuthTokenStore(this) }
    private val authApi by lazy { Roam2WorldAuthApi(BuildConfig.ROAM2WORLD_API_BASE_URL) }

    private val dealerId: String? by lazy { intent.getStringExtra(EXTRA_DEALER_ID) }
    private val dealerName: String by lazy {
        intent.getStringExtra(EXTRA_DEALER_NAME).orEmpty().ifBlank { "Dealer detail" }
    }

    private var amount by mutableStateOf("")
    private var amountError by mutableStateOf<String?>(null)
    private var loading by mutableStateOf(false)
    private var errorMessage by mutableStateOf<String?>(null)
    private var successMessage by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            R2wMockScreen(
                title = "Allocate Balance",
                subtitle = dealerName,
                onBack = { finish() },
                bottomTab = R2wBottomTab.Wallet
            ) {
                R2wMockHero(
                    eyebrow = "Dealer wallet",
                    title = "Send balance",
                    body = "Transfer funds from your reseller wallet to the selected dealer instantly.",
                    amount = "€ 1,240.00",
                    badge = "Secure"
                )

                R2wMockCard("Dealer") {
                    R2wMockLine("Dealer name", dealerName)
                    R2wMockLine("Dealer ID", dealerId ?: "--")
                    R2wMockLine("Transfer type", "Wallet allocation", strong = true)
                }

                R2wMockCard("Allocation amount") {
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
                    R2wMockLine("Currency", "EUR")
                    R2wMockLine("Estimated dealer balance", if (amount.isBlank()) "--" else "+€ ${amount.trim()}", strong = true)
                }

                errorMessage?.let {
                    R2wMockCard("Allocation failed") { Text(it, color = R2wMockColors.Danger, fontWeight = FontWeight.Bold) }
                }
                successMessage?.let {
                    R2wMockCard("Allocation successful") { Text(it, color = R2wMockColors.Success, fontWeight = FontWeight.Bold) }
                }

                R2wMockCard("Safety check") {
                    R2wMockStep("1", "Review dealer", "Make sure this is the correct dealer account.")
                    R2wMockStep("2", "Enter amount", "Use a positive amount only.")
                    R2wMockStep("3", "Allocate", "The wallet transfer is processed against real balance.")
                }

                R2wMockPrimaryButton(if (loading) "Allocating..." else "Allocate balance", enabled = !loading, onClick = { submitAllocation() })
                R2wMockSecondaryButton("Cancel", enabled = !loading, onClick = { finish() })
            }
        }
    }

    private fun submitAllocation() {
        clearMessages()
        val id = dealerId
        if (id.isNullOrBlank()) {
            errorMessage = "Dealer detail is unavailable."
            return
        }

        val cleanAmount = amount.trim().replace(",", ".")
        if (cleanAmount.toBigDecimalOrNull()?.let { it > BigDecimal.ZERO } != true) {
            amountError = "Enter an amount greater than 0."
            return
        }

        lifecycleScope.launch {
            loading = true
            val session = activeSessionOrReturnToLogin()
            if (session == null) {
                loading = false
                return@launch
            }

            val result = runCatching { authApi.allocateDealerBalance(session, id, cleanAmount) }
            loading = false
            result
                .onSuccess {
                    successMessage = "Allocated ${it.amount} ${it.currency}. Dealer balance is now ${it.dealer.currentBalance}."
                    amount = ""
                    setResult(RESULT_OK)
                    finish()
                }
                .onFailure { errorMessage = it.message ?: "Could not allocate dealer balance." }
        }
    }

    private fun clearMessages() {
        amountError = null
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

    companion object {
        const val EXTRA_DEALER_ID = "dealer_id"
        const val EXTRA_DEALER_NAME = "dealer_name"
    }
}
