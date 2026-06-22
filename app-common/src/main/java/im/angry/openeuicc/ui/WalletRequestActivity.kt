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
                            text = "Wallet Request",
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
                                        listOf(
                                            Color(0xFF1263F1),
                                            Color(0xFF0B3BAA)
                                        )
                                    )
                                )
                                .padding(22.dp)
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    R2wIconBadge(
                                        iconRes = R.drawable.r2w_ic_wallet,
                                        contentDescription = "Wallet",
                                        background = Color.White.copy(alpha = 0.18f)
                                    )

                                    Spacer(modifier = Modifier.width(14.dp))

                                    Column {
                                        Text(
                                            text = "Balance top-up",
                                            color = Color.White,
                                            fontWeight = FontWeight.Black,
                                            fontSize = 24.sp
                                        )
                                        Text(
                                            text = "Request funds from admin",
                                            color = Color.White.copy(alpha = 0.80f),
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 14.sp
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(22.dp))

                                Text(
                                    text = "${currency.ifBlank { "USD" }} ${amount.ifBlank { "0.00" }}",
                                    color = Color.White,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 34.sp
                                )

                                Text(
                                    text = "Pending approval after submission",
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
                                    iconRes = R.drawable.r2w_ic_receipt,
                                    contentDescription = "Request"
                                )

                                Spacer(modifier = Modifier.width(12.dp))

                                Column {
                                    Text(
                                        text = "Request Details",
                                        color = Color(0xFF07142F),
                                        fontWeight = FontWeight.Black,
                                        fontSize = 20.sp
                                    )
                                    Text(
                                        text = "Enter amount and optional note",
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

                            OutlinedTextField(
                                value = currency,
                                onValueChange = {
                                    currency = it.uppercase().take(3)
                                    currencyError = null
                                },
                                label = { Text("Currency") },
                                singleLine = true,
                                isError = currencyError != null,
                                supportingText = currencyError?.let { { Text(it) } },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = blue,
                                    unfocusedBorderColor = Color(0xFFDCE4F0)
                                )
                            )

                            OutlinedTextField(
                                value = note,
                                onValueChange = { note = it },
                                label = { Text("Note") },
                                minLines = 3,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = blue,
                                    unfocusedBorderColor = Color(0xFFDCE4F0)
                                )
                            )
                        }
                    }

                    errorMessage?.let {
                        Spacer(modifier = Modifier.height(14.dp))
                        StatusCard(text = it, danger = true)
                    }

                    successMessage?.let {
                        Spacer(modifier = Modifier.height(14.dp))
                        StatusCard(text = it, danger = false)
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Button(
                        onClick = { submitRequest() },
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
                            Text("Submitting")
                        } else {
                            Text("Submit Request", fontWeight = FontWeight.Black, fontSize = 18.sp)
                        }
                    }

                    TextButton(
                        onClick = {
                            startActivity(Intent(this@WalletRequestActivity, WalletRequestHistoryActivity::class.java))
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "View request history",
                            color = blue,
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

    private fun submitRequest() {
        clearMessages()

        val cleanAmount = amount.trim()
        val cleanCurrency = currency.trim().uppercase()
        val cleanNote = note.trim()

        val parsedAmount = cleanAmount.toBigDecimalOrNull()
        if (parsedAmount == null || parsedAmount <= BigDecimal.ZERO) {
            amountError = "Valid amount required"
            return
        }

        if (cleanCurrency.length != 3 || !cleanCurrency.all { it.isLetter() }) {
            currencyError = "Currency must be 3 letters"
            return
        }

        lifecycleScope.launch {
            loading = true
            try {
                val session = activeSessionOrReturnToLogin() ?: return@launch

                val response = authApi.createWalletRequest(
                    session,
                    cleanAmount,
                    cleanCurrency,
                    cleanNote
                )

                successMessage = "Request created: ${response.amount} ${response.currency}"
                amount = ""
                note = ""
                currency = "USD"

            } catch (e: Exception) {
                e.printStackTrace()
                errorMessage = e.message ?: "Wallet request failed"
            } finally {
                loading = false
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

@androidx.compose.runtime.Composable
private fun StatusCard(
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
