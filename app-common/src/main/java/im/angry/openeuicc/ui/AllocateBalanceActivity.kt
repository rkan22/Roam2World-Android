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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

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
            AllocateBalanceScreen(
                dealerName = dealerName,
                amount = amount,
                amountError = amountError,
                loading = loading,
                errorMessage = errorMessage,
                successMessage = successMessage,
                onAmountChange = {
                    amount = it
                    amountError = null
                    errorMessage = null
                    successMessage = null
                },
                onBack = { finish() },
                onSubmit = { submitAllocation() }
            )
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

            val result = runCatching {
                authApi.allocateDealerBalance(session, id, cleanAmount)
            }

            loading = false

            result
                .onSuccess {
                    successMessage = "Allocated ${it.amount} ${it.currency}. Dealer balance is now ${it.dealer.currentBalance}."
                    amount = ""
                    setResult(RESULT_OK)
                    finish()
                }
                .onFailure {
                    errorMessage = it.message ?: "Could not allocate dealer balance."
                }
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

@Composable
private fun AllocateBalanceScreen(
    dealerName: String,
    amount: String,
    amountError: String?,
    loading: Boolean,
    errorMessage: String?,
    successMessage: String?,
    onAmountChange: (String) -> Unit,
    onBack: () -> Unit,
    onSubmit: () -> Unit
) {
    val bg = Color(0xFFF6F7FB)

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = bg) {
            Column(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    AllocateHero(
                        dealerName = dealerName,
                        loading = loading,
                        onBack = onBack
                    )

                    errorMessage?.let {
                        InfoCard(title = "Allocation failed") {
                            Text(it, color = Color(0xFFDC2626))
                        }
                    }

                    successMessage?.let {
                        InfoCard(title = "Allocation successful") {
                            Text(it, color = Color(0xFF168653), fontWeight = FontWeight.Bold)
                        }
                    }

                    InfoCard(title = "Allocation amount") {
                        Text(
                            "Move balance from your reseller wallet to this dealer. This action affects real wallet balance.",
                            color = Color(0xFF6B7280)
                        )

                        OutlinedTextField(
                            value = amount,
                            onValueChange = onAmountChange,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Amount") },
                            singleLine = true,
                            isError = amountError != null,
                            supportingText = { amountError?.let { Text(it) } },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            shape = RoundedCornerShape(18.dp)
                        )
                    }

                    InfoCard(title = "Safety check") {
                        Text(
                            "Only press Allocate balance when you are ready to send this amount to the dealer.",
                            color = Color(0xFF6B7280)
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))
                }

                Surface(shadowElevation = 8.dp, color = Color.White) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = onBack,
                            enabled = !loading,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Text("Back")
                        }

                        Button(
                            onClick = onSubmit,
                            enabled = !loading,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF7900)),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            if (loading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White
                                )
                            } else {
                                Text("Allocate balance", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AllocateHero(
    dealerName: String,
    loading: Boolean,
    onBack: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF17181C))
    ) {
        Column(modifier = Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Allocate balance",
                        color = Color.White,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        dealerName,
                        color = Color.White.copy(alpha = 0.72f),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Text(
                    text = "Back",
                    color = Color(0xFFFF7900),
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.clickable(enabled = !loading, onClick = onBack)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .background(Color(0xFFFFEFE2), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("$", color = Color(0xFFFF7900), fontWeight = FontWeight.Black)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Dealer wallet top-up", color = Color.White, fontWeight = FontWeight.Black)
                    Text("Funds are transferred from reseller balance", color = Color.White.copy(alpha = 0.72f))
                }
            }
        }
    }
}

@Composable
private fun InfoCard(title: String, content: @Composable ColumnScope.() -> Unit) {
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
                title,
                color = Color(0xFF17181C),
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            HorizontalDivider()
            content()
        }
    }
}
