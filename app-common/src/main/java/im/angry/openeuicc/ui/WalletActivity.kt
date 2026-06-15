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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
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
                onLogout = { logout() },
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

    private fun logout() {
        lifecycleScope.launch {
            val session = withContext(Dispatchers.IO) {
                tokenStore.getSession().also {
                    tokenStore.clear()
                }
            }
            session?.let {
                runCatching {
                    authApi.logout(it)
                }
            }
            openLoginActivity()
        }
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
    onLogout: () -> Unit,
    onDashboard: () -> Unit,
    onPackages: () -> Unit,
    onEsims: () -> Unit,
    onMore: () -> Unit
) {
    val bg = Color(0xFFF7F7FA)
    val balanceText = walletData?.currentBalance
    val transactions = walletData?.transactions.orEmpty()
    val lowBalance = balanceText
        ?.replace(Regex("[^0-9.,-]"), "")
        ?.replace(",", ".")
        ?.toDoubleOrNull()
        ?.let { it < 20.0 } == true

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = bg) {
            Column(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 116.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    WalletHero(
                        balance = walletData?.currentBalance?.let { r2wMoney(it) } ?: "--",
                        loading = loading,
                        onRefresh = onRefresh,
                        onRequestBalance = onRequestBalance,
                        onRequestHistory = onRequestHistory
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onTransactions,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text("Transactions")
                        }
                        OutlinedButton(
                            onClick = onPurchaseHistory,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text("Orders")
                        }
                    }

                    errorMessage?.let {
                        WalletCard(title = "Error") {
                            Text(it, color = Color(0xFFDC2626), fontWeight = FontWeight.SemiBold)
                        }
                    }

                    if (loading) {
                        WalletCard(title = "Loading") {
                            CircularProgressIndicator()
                        }
                    }

                    if (lowBalance) {
                        WalletCard(title = "Low balance") {
                            Text(
                                text = "Your wallet balance is low. Request balance or contact support before new purchases.",
                                color = Color(0xFFD97706),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    WalletCard(title = "Quick stats") {
                        val total = transactions.size
                        val completed = transactions.count {
                            val status = it.status.orEmpty().lowercase()
                            status.contains("complete") || status.contains("success") || status.contains("approved")
                        }
                        val last = transactions.firstOrNull()
                        val lastActivity = listOfNotNull(
                            last?.title?.takeIf { it.isNotBlank() },
                            last?.subtitle?.takeIf { it.isNotBlank() }?.let { formatWalletText(it) }
                        ).joinToString(" • ").ifBlank { "No activity yet" }

                        Text(
                            text = "Recent transactions: $total\nCompleted: $completed\nLast activity: $lastActivity",
                            color = Color(0xFF6B7280),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    WalletCard(title = "Recent balance requests") {
                        requestError?.let {
                            Text(it, color = Color(0xFFDC2626), fontWeight = FontWeight.SemiBold)
                        }

                        if (recentRequests.isEmpty() && requestError == null) {
                            Text(
                                text = "No wallet requests yet.",
                                color = Color(0xFF6B7280)
                            )
                        } else {
                            recentRequests.forEach { request ->
                                WalletRequestPreview(request)
                                HorizontalDivider()
                            }
                        }
                    }

                    WalletCard(title = "Recent transactions") {
                        if (transactions.isEmpty()) {
                            Text(
                                text = "No wallet transactions yet.",
                                color = Color(0xFF6B7280)
                            )
                        } else {
                            transactions.take(8).forEach { transaction ->
                                WalletTransactionPreview(transaction)
                                HorizontalDivider()
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                R2wBottomNav(
                    selected = R2wBottomTab.Wallet
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
    val orange = Color(0xFFFF7900)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF17181C))
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Wallet",
                        color = orange,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = balance,
                        color = Color.White,
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = "Available balance",
                        color = Color.White.copy(alpha = 0.72f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Text(
                    text = if (loading) "Loading" else "Refresh",
                    color = orange,
                    modifier = Modifier.clickable(enabled = !loading, onClick = onRefresh),
                    fontWeight = FontWeight.Black
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onRequestBalance,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = orange),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Request", fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onRequestHistory,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("History")
                }
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
                color = Color(0xFF17181C),
                fontWeight = FontWeight.Bold
            )
            Text(
                text = formatWalletDate(request.createdAt).ifBlank { "No date" },
                color = Color(0xFF6B7280),
                style = MaterialTheme.typography.bodySmall
            )
            if (!request.note.isNullOrBlank()) {
                Text(
                    text = request.note,
                    color = Color(0xFF6B7280),
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
                color = Color(0xFF17181C),
                fontWeight = FontWeight.Bold
            )
            Text(
                text = formatWalletText(transaction.title).ifBlank { formatWalletText(transaction.subtitle) },
                color = Color(0xFF6B7280),
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
            color = if (isDebit) Color(0xFFDC2626) else Color(0xFF15803D),
            fontWeight = FontWeight.Black
        )
    }
}

@Composable
private fun WalletCard(
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
        status.contains("failed") || status.contains("failure") || status.contains("cancel") || status.contains("reject") || status.contains("error") -> Color(0xFFDC2626)
        status.contains("pending") || status.contains("review") || status.contains("processing") || status.contains("waiting") -> Color(0xFFD97706)
        else -> Color(0xFF15803D)
    }
}
