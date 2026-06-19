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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

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
            WalletScreen(
                loading = loading,
                walletData = walletData,
                recentRequests = recentRequests,
                errorMessage = errorMessage,
                requestError = requestError,
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
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                WalletHero(
                    balance = walletData?.currentBalance ?: "--",
                    loading = loading,
                    onRefresh = onRefresh,
                    onRequestBalance = onRequestBalance,
                    onRequestHistory = onRequestHistory
                )

                WalletQuickRequest(
                    amount = amount,
                    selectedAmount = selectedAmount,
                    onAmountChange = {
                        amount = it
                        selectedAmount = it.toIntOrNull()
                    },
                    onSubmit = onRequestBalance
                )
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
