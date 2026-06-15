package im.angry.openeuicc.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment

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
            WalletRequestScreen(
                amount = amount,
                currency = currency,
                note = note,
                loading = loading,
                errorMessage = errorMessage,
                successMessage = successMessage,
                amountError = amountError,
                currencyError = currencyError,
                onAmountChange = { amount = it },
                onCurrencyChange = { currency = it.uppercase().take(3) },
                onNoteChange = { note = it },
                onSubmit = { submitRequest() },
                onHistory = { startActivity(Intent(this, WalletRequestHistoryActivity::class.java)) },
                onBack = { finish() }
            )
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

            val result = runCatching {
                authApi.createWalletRequest(session, cleanAmount, cleanCurrency, cleanNote)
            }

            loading = false
            result
                .onSuccess {
                    successMessage = "Wallet request created: ${it.amount} ${it.currency}"
                    amount = ""
                    note = ""
                    currency = cleanCurrency
                }
                .onFailure {
                    errorMessage = it.message ?: "Wallet request failed"
                }
        }
    }

    private fun clearMessages() {
        amountError = null
        currencyError = null
        errorMessage = null
        successMessage = null
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

@Composable
private fun WalletRequestScreen(
    amount: String,
    currency: String,
    note: String,
    loading: Boolean,
    errorMessage: String?,
    successMessage: String?,
    amountError: String?,
    currencyError: String?,
    onAmountChange: (String) -> Unit,
    onCurrencyChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onHistory: () -> Unit,
    onBack: () -> Unit
) {
    val orange = Color(0xFFFF7900)
    val bg = Color(0xFFF7F7FA)

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = bg) {
            Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 116.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedButton(onClick = onBack, shape = RoundedCornerShape(16.dp)) {
                    Text("Geri")
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(30.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF17181C))
                ) {
                    Column(
                        modifier = Modifier.padding(22.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Wallet",
                            color = orange,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Request Balance",
                            color = Color.White,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = "Create a wallet balance request for your Roam2World account.",
                            color = Color.White.copy(alpha = 0.72f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                WalletRequestCard(title = "Request details") {
                    OutlinedTextField(
                        value = amount,
                        onValueChange = onAmountChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Amount") },
                        isError = amountError != null,
                        supportingText = { amountError?.let { Text(it) } },
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = currency,
                        onValueChange = onCurrencyChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Currency") },
                        isError = currencyError != null,
                        supportingText = { currencyError?.let { Text(it) } },
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = note,
                        onValueChange = onNoteChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Note") },
                        minLines = 3
                    )

                    errorMessage?.let {
                        Text(
                            text = it,
                            color = Color(0xFFDC2626),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    successMessage?.let {
                        Text(
                            text = it,
                            color = Color(0xFF15803D),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    if (loading) {
                        CircularProgressIndicator()
                    }

                    Button(
                        onClick = onSubmit,
                        enabled = !loading,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = orange),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Text(
                            text = if (loading) "Submitting..." else "Submit request",
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }

                    OutlinedButton(
                        onClick = onHistory,
                        enabled = !loading,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Text("View request history")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
            }
        
                R2wBottomNav(
                    selected = R2wBottomTab.Wallet,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }
    }
}

@Composable
private fun WalletRequestCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title,
                color = Color(0xFF17181C),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            HorizontalDivider()
            content()
        }
    }
}
