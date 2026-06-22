package im.angry.openeuicc.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import im.angry.openeuicc.auth.AuthSession
import im.angry.openeuicc.auth.AuthTokenStore
import im.angry.openeuicc.auth.JwtUtils
import im.angry.openeuicc.auth.Roam2WorldAuthApi
import im.angry.openeuicc.common.BuildConfig
import im.angry.openeuicc.common.R
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
            val blue = Color(0xFF1263F1)
            val bg = Color(0xFFF4F8FD)

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(bg)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(WindowInsets.statusBars.asPaddingValues())
                        .padding(horizontal = 18.dp)
                        .padding(bottom = 96.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { finish() }) {
                            Text("Back", color = blue, fontWeight = FontWeight.Bold)
                        }

                        Text(
                            text = "Allocate Balance",
                            modifier = Modifier.weight(1f),
                            color = Color(0xFF07142F),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.width(64.dp))
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(30.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    Brush.linearGradient(
                                        listOf(Color(0xFF1263F1), Color(0xFF0B3BAA))
                                    )
                                )
                                .padding(22.dp)
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    R2wIconBadge(
                                        iconRes = R.drawable.r2w_ic_wallet,
                                        contentDescription = "Dealer Wallet",
                                        background = Color.White.copy(alpha = 0.18f)
                                    )

                                    Spacer(modifier = Modifier.width(14.dp))

                                    Column {
                                        Text(
                                            text = "Dealer wallet transfer",
                                            color = Color.White,
                                            fontWeight = FontWeight.Black,
                                            fontSize = 23.sp
                                        )
                                        Text(
                                            text = "Allocate balance to selected dealer",
                                            color = Color.White.copy(alpha = 0.82f),
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 14.sp
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(22.dp))

                                Text(
                                    text = if (amount.isBlank()) "EUR 0.00" else "EUR ${amount.trim()}",
                                    color = Color.White,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 34.sp
                                )

                                Text(
                                    text = "Transfer is processed against real wallet balance",
                                    color = Color.White.copy(alpha = 0.82f),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(26.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                R2wIconBadge(
                                    iconRes = R.drawable.r2w_ic_provider,
                                    contentDescription = "Dealer"
                                )

                                Spacer(modifier = Modifier.width(12.dp))

                                Column {
                                    Text(
                                        text = "Dealer",
                                        color = Color(0xFF07142F),
                                        fontWeight = FontWeight.Black,
                                        fontSize = 20.sp
                                    )
                                    Text(
                                        text = "Review destination account",
                                        color = Color(0xFF738099),
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp
                                    )
                                }
                            }

                            HorizontalDivider(color = Color(0xFFE8EDF5))

                            AllocateLine("Dealer name", dealerName)
                            AllocateLine("Dealer ID", dealerId ?: "--")
                            AllocateLine("Transfer type", "Wallet allocation", strong = true)
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(26.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                R2wIconBadge(
                                    iconRes = R.drawable.r2w_ic_payment,
                                    contentDescription = "Amount"
                                )

                                Spacer(modifier = Modifier.width(12.dp))

                                Column {
                                    Text(
                                        text = "Allocation Amount",
                                        color = Color(0xFF07142F),
                                        fontWeight = FontWeight.Black,
                                        fontSize = 20.sp
                                    )
                                    Text(
                                        text = "Enter the dealer balance amount",
                                        color = Color(0xFF738099),
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp
                                    )
                                }
                            }

                            HorizontalDivider(color = Color(0xFFE8EDF5))

                            OutlinedTextField(
                                value = amount,
                                onValueChange = {
                                    amount = it
                                    amountError = null
                                    errorMessage = null
                                    successMessage = null
                                },
                                label = { Text("Amount") },
                                singleLine = true,
                                isError = amountError != null,
                                supportingText = amountError?.let { { Text(it) } },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = blue,
                                    unfocusedBorderColor = Color(0xFFDCE4F0)
                                )
                            )

                            AllocateLine("Currency", "EUR")
                            AllocateLine(
                                "Estimated dealer balance",
                                if (amount.isBlank()) "--" else "+ EUR ${amount.trim()}",
                                strong = true
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(26.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Safety Check",
                                color = Color(0xFF07142F),
                                fontWeight = FontWeight.Black,
                                fontSize = 20.sp
                            )

                            HorizontalDivider(color = Color(0xFFE8EDF5))

                            SafetyStep("1", "Review dealer", "Make sure this is the correct dealer account.")
                            SafetyStep("2", "Enter amount", "Use a positive amount only.")
                            SafetyStep("3", "Allocate", "The wallet transfer is processed against real balance.")
                        }
                    }

                    errorMessage?.let {
                        Spacer(modifier = Modifier.height(14.dp))
                        AllocateStatusCard(it, danger = true)
                    }

                    successMessage?.let {
                        Spacer(modifier = Modifier.height(14.dp))
                        AllocateStatusCard(it, danger = false)
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Button(
                        onClick = { submitAllocation() },
                        enabled = !loading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(58.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = blue),
                        shape = RoundedCornerShape(22.dp)
                    ) {
                        if (loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Allocating")
                        } else {
                            Text("Allocate Balance", fontWeight = FontWeight.Black, fontSize = 18.sp)
                        }
                    }

                    TextButton(
                        onClick = { finish() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Cancel",
                            color = Color(0xFF738099),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }

                R2wBottomNav(
                    selected = R2wBottomTab.Wallet,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(WindowInsets.navigationBars.asPaddingValues())
                )
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

@androidx.compose.runtime.Composable
private fun AllocateLine(
    label: String,
    value: String,
    strong: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = Color(0xFF738099),
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            modifier = Modifier.weight(1f)
        )

        Text(
            text = value,
            color = if (strong) Color(0xFF1263F1) else Color(0xFF07142F),
            fontWeight = if (strong) FontWeight.Black else FontWeight.Bold,
            fontSize = 15.sp,
            textAlign = TextAlign.End
        )
    }
}

@androidx.compose.runtime.Composable
private fun SafetyStep(
    number: String,
    title: String,
    body: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .background(Color(0xFFEAF2FF), RoundedCornerShape(14.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number,
                color = Color(0xFF1263F1),
                fontWeight = FontWeight.Black,
                fontSize = 13.sp
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color(0xFF07142F),
                fontWeight = FontWeight.Black,
                fontSize = 15.sp
            )
            Text(
                text = body,
                color = Color(0xFF738099),
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp
            )
        }
    }
}

@androidx.compose.runtime.Composable
private fun AllocateStatusCard(
    text: String,
    danger: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (danger) Color(0xFFFFEEF0) else Color(0xFFEFFFF6)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(16.dp),
            color = if (danger) Color(0xFFB42318) else Color(0xFF087443),
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp
        )
    }
}
