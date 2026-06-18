package im.angry.openeuicc.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import im.angry.openeuicc.auth.AuthSession
import im.angry.openeuicc.auth.AuthTokenStore
import im.angry.openeuicc.auth.JwtUtils
import im.angry.openeuicc.auth.MobileTransaction
import im.angry.openeuicc.auth.MobileWalletData
import im.angry.openeuicc.auth.MobileWalletRequest
import im.angry.openeuicc.auth.Roam2WorldAuthApi
import im.angry.openeuicc.common.BuildConfig
import im.angry.openeuicc.ui.compose.components.R2WActionCard
import im.angry.openeuicc.ui.compose.components.R2WCard
import im.angry.openeuicc.ui.compose.components.R2WPrimaryButton
import im.angry.openeuicc.ui.compose.components.R2WSecondaryButton
import im.angry.openeuicc.ui.compose.components.R2WStatCard
import im.angry.openeuicc.ui.compose.components.R2WWalletBalanceCard
import im.angry.openeuicc.ui.compose.theme.Background
import im.angry.openeuicc.ui.compose.theme.Danger
import im.angry.openeuicc.ui.compose.theme.Primary
import im.angry.openeuicc.ui.compose.theme.Success
import im.angry.openeuicc.ui.compose.theme.TextPrimary
import im.angry.openeuicc.ui.compose.theme.TextSecondary
import im.angry.openeuicc.ui.compose.theme.Warning
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

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
                onDashboard = { startActivity(Intent(this, DashboardActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)) },
                onPackages = { startActivity(Intent(this, PackagesActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)) },
                onEsims = { startActivity(Intent(this, MobileEsimsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)) },
                onMore = { startActivity(Intent(this, MoreActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)) }
            )
        }

        loadWallet()
    }

    private fun loadWallet() {
        lifecycleScope.launch {
            loading = true
            errorMessage = null
            requestError = null

            val session = activeSessionOrReturnToLogin()
            if (session == null) {
                loading = false
                return@launch
            }

            val walletResult = runCatching { authApi.wallet(session) }
            val requestResult = runCatching { authApi.walletRequests(session) }

            loading = false

            walletResult
                .onSuccess { walletData = it }
                .onFailure { errorMessage = it.message ?: "Wallet could not be loaded" }

            requestResult
                .onSuccess { recentRequests = it.take(3) }
                .onFailure {
                    recentRequests = emptyList()
                    requestError = it.message ?: "Wallet request history failed"
                }
        }
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
        openLoginActivity()
        return null
    }

    private fun openLoginActivity() {
        startActivity(
            Intent(this, LoginActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
        )
        finish()
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
    onDashboard: () -> Unit,
    onPackages: () -> Unit,
    onEsims: () -> Unit,
    onMore: () -> Unit
) {
    val balanceText = walletData?.currentBalance
    val balance = balanceText?.let { r2wMoney(it) } ?: "--"
    val transactions = walletData?.transactions.orEmpty()
    val lowBalance = balanceText
        ?.replace(Regex("[^0-9.,-]"), "")
        ?.replace(",", ".")
        ?.toDoubleOrNull()
        ?.let { it < 20.0 } == true

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = Background) {
            Column(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 116.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Wallet",
                        color = TextPrimary,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Balance, top-up requests and business transactions",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )

                    R2WWalletBalanceCard(
                        balance = balance,
                        mainBalance = balance,
                        bonus = "$200.00",
                        pending = "$100.00",
                        onRechargeClick = onRequestBalance
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        R2WActionCard(
                            icon = Icons.Default.AccountBalanceWallet,
                            title = "Recharge",
                            onClick = onRequestBalance,
                            modifier = Modifier.weight(1f)
                        )
                        R2WActionCard(
                            icon = Icons.Default.Refresh,
                            title = "Requests",
                            onClick = onRequestHistory,
                            modifier = Modifier.weight(1f)
                        )
                        R2WActionCard(
                            icon = Icons.Default.History,
                            title = "History",
                            onClick = onTransactions,
                            modifier = Modifier.weight(1f)
                        )
                        R2WActionCard(
                            icon = Icons.Default.CreditCard,
                            title = "Payment",
                            onClick = onPurchaseHistory,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        R2WStatCard(
                            title = "Transactions",
                            value = transactions.size.toString(),
                            trend = "recent activity",
                            modifier = Modifier.weight(1f)
                        )
                        R2WStatCard(
                            title = "Requests",
                            value = recentRequests.size.toString(),
                            trend = "latest 3 shown",
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        R2WSecondaryButton("Transactions", onTransactions, Modifier.weight(1f))
                        R2WSecondaryButton("Orders", onPurchaseHistory, Modifier.weight(1f))
                    }

                    errorMessage?.let {
                        R2WCard {
                            Text("Wallet could not be loaded", color = Danger, fontWeight = FontWeight.Bold)
                            Text(it, color = TextSecondary)
                            R2WPrimaryButton("Retry", onRefresh)
                        }
                    }

                    if (loading) {
                        R2WCard {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.height(22.dp), color = Primary)
                                Text("Loading wallet...", color = TextSecondary)
                            }
                        }
                    }

                    if (lowBalance) {
                        R2WCard {
                            Text("Low balance", color = Warning, fontWeight = FontWeight.Bold)
                            Text(
                                text = "Your wallet balance is low. Request balance or contact support before new purchases.",
                                color = TextSecondary
                            )
                            R2WPrimaryButton("Request Balance", onRequestBalance)
                        }
                    }

                    R2WCard {
                        Text("Recent balance requests", color = TextPrimary, fontWeight = FontWeight.Bold)
                        requestError?.let {
                            Text(it, color = Danger, fontWeight = FontWeight.SemiBold)
                        }

                        if (recentRequests.isEmpty() && requestError == null) {
                            Text("No wallet requests yet.", color = TextSecondary)
                        } else {
                            recentRequests.forEach { request ->
                                WalletRequestPreview(request)
                                HorizontalDivider(color = im.angry.openeuicc.ui.compose.theme.Border)
                            }
                        }
                    }

                    R2WCard {
                        Text("Recent transactions", color = TextPrimary, fontWeight = FontWeight.Bold)
                        if (transactions.isEmpty()) {
                            Text("No wallet transactions yet.", color = TextSecondary)
                        } else {
                            transactions.take(8).forEach { transaction ->
                                WalletTransactionPreview(transaction)
                                HorizontalDivider(color = im.angry.openeuicc.ui.compose.theme.Border)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                R2wBottomNav(selected = R2wBottomTab.Wallet)
            }
        }
    }
}

@Composable
private fun WalletRequestPreview(request: MobileWalletRequest) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "${request.amount} ${request.currency}",
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = formatWalletDate(request.createdAt).ifBlank { "No date" },
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall
            )
            if (!request.note.isNullOrBlank()) {
                Text(
                    text = request.note,
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Text(
            text = request.statusLabel(),
            color = statusColor(request.status),
            fontWeight = FontWeight.Black,
            style = MaterialTheme.typography.labelLarge
        )
    }
}

@Composable
private fun WalletTransactionPreview(transaction: MobileTransaction) {
    val amountRaw = transaction.amount.trim()
    val titleRaw = transaction.title.trim()
    val statusRaw = transaction.status.orEmpty().trim()
    val combined = "$amountRaw $titleRaw $statusRaw".lowercase(Locale.ENGLISH)

    val isDebit =
        amountRaw.startsWith("-") ||
            combined.contains("debit") ||
            combined.contains("purchase") ||
            combined.contains("charge") ||
            combined.contains("deduct") ||
            combined.contains("vendor")

    val amountDisplay = when {
        amountRaw.isBlank() -> ""
        amountRaw.startsWith("+") || amountRaw.startsWith("-") -> amountRaw
        isDebit -> "- $amountRaw"
        else -> "+ $amountRaw"
    }

    val statusKey = statusRaw.lowercase(Locale.ENGLISH)
    val cleanStatus = statusRaw
        .replace("_", " ")
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ENGLISH) else it.toString() }
        .ifBlank {
            if (
                statusKey.contains("fail") ||
                statusKey.contains("cancel") ||
                statusKey.contains("reject")
            ) {
                "Failed"
            } else {
                "Completed"
            }
        }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = if (isDebit) "Debit" else "Credit",
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = formatWalletText(transaction.title).ifBlank { formatWalletText(transaction.subtitle) },
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = cleanStatus,
                color = statusColor(statusRaw),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold
            )
        }

        Text(
            text = r2wMoney(amountDisplay, ""),
            color = if (isDebit) Danger else Success,
            fontWeight = FontWeight.Black
        )
    }
}

private fun formatWalletDate(value: String?): String {
    val raw = value?.trim().orEmpty()
    if (raw.isBlank()) return ""

    val normalized = raw
        .replace(" ", "T")
        .replace(Regex("""(\.\d{3})\d+"""), "$1")
        .let {
            if (it.endsWith("Z", ignoreCase = true) || it.contains(Regex("""[+-]\d{2}:?\d{2}$"""))) {
                it
            } else {
                "${it}Z"
            }
        }

    return runCatching {
        val formatter = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm", Locale.ENGLISH)
        Instant.parse(normalized).atZone(ZoneId.systemDefault()).format(formatter)
    }.getOrElse { raw }
}

private fun formatWalletText(value: String?): String {
    val raw = value.orEmpty()
    val isoPattern = Regex("""\d{4}-\d{2}-\d{2}[T ]\d{2}:\d{2}:\d{2}(?:\.\d+)?(?:Z|[+-]\d{2}:?\d{2})?""")
    return isoPattern.replace(raw) { matchResult -> formatWalletDate(matchResult.value) }
}

private fun statusColor(statusValue: String?): Color {
    val status = statusValue.orEmpty().lowercase(Locale.ENGLISH)
    return when {
        status.contains("failed") || status.contains("failure") || status.contains("cancel") || status.contains("reject") || status.contains("error") -> Danger
        status.contains("pending") || status.contains("review") || status.contains("processing") || status.contains("waiting") -> Warning
        else -> Success
    }
}
