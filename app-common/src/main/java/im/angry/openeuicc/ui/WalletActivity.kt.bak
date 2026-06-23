package im.angry.openeuicc.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import im.angry.openeuicc.auth.AuthSession
import im.angry.openeuicc.auth.AuthTokenStore
import im.angry.openeuicc.auth.JwtUtils
import im.angry.openeuicc.auth.MobileTransaction
import im.angry.openeuicc.auth.MobileWalletData
import im.angry.openeuicc.auth.MobileWalletRequest
import im.angry.openeuicc.auth.Roam2WorldAuthApi
import im.angry.openeuicc.common.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val WalletBlue = Color(0xFF0F6BFF)
private val WalletMuted = Color(0xFF6B7280)

class WalletActivity : ComponentActivity() {
    private val tokenStore by lazy { AuthTokenStore(this) }
    private val authApi by lazy { Roam2WorldAuthApi(BuildConfig.ROAM2WORLD_API_BASE_URL) }

    private var loading by mutableStateOf(false)
    private var walletData by mutableStateOf<MobileWalletData?>(null)
    private var recentRequests by mutableStateOf<List<MobileWalletRequest>>(emptyList())
    private var errorMessage by mutableStateOf<String?>(null)
    private var requestError by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {

            var showBalance by remember { mutableStateOf(true) }

            WalletScreen(
                loading = loading,
                walletData = walletData,
                recentRequests = recentRequests,
                errorMessage = errorMessage,
                requestError = requestError,
                showBalance = showBalance,
                onToggleBalance = { showBalance = !showBalance },
                onRefresh = { loadWallet() },
                onRequestBalance = { startActivity(Intent(this, WalletRequestActivity::class.java)) },
                onRequestHistory = { startActivity(Intent(this, WalletRequestHistoryActivity::class.java)) },
                onTransactions = { startActivity(Intent(this, TransactionsActivity::class.java)) },
                onPurchaseHistory = { startActivity(Intent(this, PurchaseHistoryActivity::class.java)) },
                onLogout = { },
                onDashboard = { },
                onPackages = { },
                onEsims = { },
                onMore = { }
            )
        }

        loadWallet()
    }

    private fun loadWallet() {
        lifecycleScope.launch {
            loading = true
            val session = activeSessionOrReturnToLogin() ?: return@launch

            val walletResult = runCatching { authApi.wallet(session) }
            val requestResult = runCatching { authApi.walletRequests(session) }

            walletResult.onSuccess { walletData = it }
                .onFailure { errorMessage = it.message }

            requestResult.onSuccess { recentRequests = it.take(3) }
                .onFailure { requestError = it.message }

            loading = false
        }
    }

    private suspend fun activeSessionOrReturnToLogin(): AuthSession? {
        val saved = withContext(Dispatchers.IO) { tokenStore.getSession() } ?: return null
        if (!JwtUtils.isExpired(saved.accessToken)) return saved
        return null
    }
}

@Composable
private fun WalletScreen(
    loading: Boolean,
    walletData: MobileWalletData?,
    recentRequests: List<MobileWalletRequest>,
    errorMessage: String?,
    requestError: String?,
    showBalance: Boolean,
    onToggleBalance: () -> Unit,
    onRefresh: () -> Unit,
    onRequestBalance: () -> Unit,
    onRequestHistory: () -> Unit,
    onTransactions: () -> Unit,
    onPurchaseHistory: () -> Unit,
    onLogout: () -> Unit,
    onDashboard: () -> Unit,
    onPackages: () -> Unit,
    onEsims: () -> Unit,
    onMore: () -> Unit
) {
    val transactions = walletData?.transactions.orEmpty()

    var amount by remember { mutableStateOf("") }
    var selectedAmount by remember { mutableStateOf<Int?>(null) }

    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
                    .clickable { onToggleBalance() },
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {

                // WalletHero was referenced but not defined in this source snapshot.
                // Temporarily removed to allow debug APK build.

                WalletQuickRequest(
                    amount = amount,
                    selectedAmount = selectedAmount,
                    onAmountChange = { value ->
                        amount = value
                        selectedAmount = value.toIntOrNull()
                    },
                    onSubmit = onRequestBalance
                )
            }
        }
    }
}

@Composable
private fun WalletHero(
    balance: String,
    loading: Boolean,
    onRefresh: () -> Unit,
    onRequestBalance: () -> Unit,
    onRequestHistory: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = WalletBlue)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Total Balance", color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp)
                    Text(
                        text = balance,
                        color = Color.White,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(onClick = onRefresh, enabled = !loading) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = Color.White
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onRequestBalance,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Request", color = WalletBlue, fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = onRequestHistory,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    border = BorderStroke(1.dp, Color.White),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("History", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun WalletQuickRequest(
    amount: String,
    selectedAmount: Int?,
    onAmountChange: (String) -> Unit,
    onSubmit: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

            Text("Quick Request", fontWeight = FontWeight.Bold)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(50, 100, 250, 500).forEach { value ->
                    val isSelected = selectedAmount == value

                    Box(
                        Modifier
                            .weight(1f)
                            .height(36.dp)
                            .background(
                                if (isSelected) WalletBlue else Color(0xFFF2F4F8),
                                RoundedCornerShape(10.dp)
                            )
                            .clickable { onAmountChange(value.toString()) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "$value",
                            color = if (isSelected) Color.White else WalletBlue,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            OutlinedTextField(
                value = amount,
                onValueChange = { onAmountChange(it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Enter amount") },
                singleLine = true
            )

            Button(
                onClick = onSubmit,
                enabled = amount.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (amount.isEmpty()) "Request" else "Request $amount")
            }
        }
    }
}
