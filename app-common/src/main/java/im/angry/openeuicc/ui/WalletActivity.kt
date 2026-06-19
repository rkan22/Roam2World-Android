package im.angry.openeuicc.ui

import android.content.Intent
import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowInsetsControllerCompat
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

private val WalletBlue = Color(0xFF0F4FD7)
private val WalletDark = Color(0xFF06103A)
private val WalletBg = Color(0xFFF8FAFD)
private val WalletText = Color(0xFF20242C)
private val WalletMuted = Color(0xFF68707C)
private val WalletBorder = Color(0xFFE1E6EF)
private val WalletGreen = Color(0xFF12813A)
private val WalletGreenBg = Color(0xFFE9F7EF)
private val WalletRed = Color(0xFFB42336)
private val WalletRedBg = Color(0xFFFFEEF2)
private val WalletYellow = Color(0xFFB7791F)
private val WalletYellowBg = Color(0xFFFFF8E6)

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
        actionBar?.hide()
        window.statusBarColor = AndroidColor.rgb(248, 250, 253)
        window.navigationBarColor = AndroidColor.BLACK
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true

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
                onBack = { finish() }
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
            walletResult.onSuccess { walletData = it }.onFailure { errorMessage = it.message ?: "Wallet could not be loaded" }
            requestResult.onSuccess { recentRequests = it.take(4) }.onFailure {
                recentRequests = emptyList()
                requestError = it.message ?: "Wallet request history failed"
            }
        }
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
    onBack: () -> Unit
) {
    val transactions = walletData?.transactions.orEmpty()
    MaterialTheme {
        Surface(Modifier.fillMaxSize(), color = WalletBg) {
            Column(Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(start = 18.dp, top = 16.dp, end = 18.dp, bottom = 100.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.ArrowBack, null, tint = WalletText, modifier = Modifier.size(30.dp).clickable(onClick = onBack))
                        Text("Wallet", color = WalletText, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(start = 18.dp).weight(1f))
                        if (loading) CircularProgressIndicator(modifier = Modifier.size(22.dp), color = WalletBlue, strokeWidth = 2.dp)
                        Icon(Icons.Default.Refresh, null, tint = WalletBlue, modifier = Modifier.padding(start = 12.dp).size(26.dp).clickable(onClick = onRefresh))
                    }

                    BalanceHero(walletData?.currentBalance, transactions, onRequestBalance, onRequestHistory)

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        WalletQuickButton("Transactions", Icons.Default.SwapVert, Modifier.weight(1f), onTransactions)
                        WalletQuickButton("Orders", Icons.Default.ShoppingCart, Modifier.weight(1f), onPurchaseHistory)
                    }

                    errorMessage?.let { InfoCard("Wallet error", it, WalletRed) }
                    requestError?.let { InfoCard("Request warning", it, WalletYellow) }

                    WalletStats(transactions)
                    WalletRequests(recentRequests)
                    WalletTransactions(transactions)
                }

                R2wBottomNav(selected = R2wBottomTab.Wallet)
            }
        }
    }
}

@Composable
private fun BalanceHero(balance: String?, transactions: List<MobileTransaction>, onRequestBalance: () -> Unit, onRequestHistory: () -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(WalletDark), elevation = CardDefaults.cardElevation(4.dp)) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("Available Balance", color = Color.White.copy(alpha = .74f), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Text(r2wMoney(balance ?: "0"), color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("Ready for new package purchases", color = Color.White.copy(alpha = .68f), fontSize = 13.sp)
                }
                Box(Modifier.size(58.dp).clip(CircleShape).background(WalletBlue), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.AccountBalanceWallet, null, tint = Color.White, modifier = Modifier.size(30.dp))
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onRequestBalance, modifier = Modifier.weight(1f).height(50.dp), colors = ButtonDefaults.buttonColors(containerColor = WalletBlue), shape = RoundedCornerShape(14.dp)) {
                    Icon(Icons.Default.Add, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Top-up", fontWeight = FontWeight.ExtraBold)
                }
                OutlinedButton(onClick = onRequestHistory, modifier = Modifier.weight(1f).height(50.dp), shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, Color.White.copy(alpha = .5f))) {
                    Icon(Icons.Default.History, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Requests", color = Color.White, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}

@Composable
private fun WalletQuickButton(title: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = modifier.height(52.dp), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, WalletBorder)) {
        Icon(icon, null, tint = WalletBlue, modifier = Modifier.size(21.dp))
        Spacer(Modifier.width(7.dp))
        Text(title, color = WalletText, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

@Composable
private fun WalletStats(transactions: List<MobileTransaction>) {
    val completed = transactions.count { it.status.orEmpty().lowercase().let { s -> s.contains("complete") || s.contains("success") || s.contains("approved") } }
    val purchases = transactions.count { it.isDebitLike() }
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(Color.White), border = BorderStroke(1.dp, WalletBorder)) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatBox("Tx", transactions.size.toString(), Modifier.weight(1f))
            StatBox("Paid", purchases.toString(), Modifier.weight(1f))
            StatBox("Done", completed.toString(), Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatBox(label: String, value: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, color = WalletText, fontSize = 20.sp, fontWeight = FontWeight.Black)
        Text(label, color = WalletMuted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun WalletRequests(requests: List<MobileWalletRequest>) {
    SectionCard("Recent balance requests") {
        if (requests.isEmpty()) {
            Text("No wallet requests yet.", color = WalletMuted, fontSize = 14.sp)
        } else {
            requests.forEachIndexed { index, request ->
                RequestRow(request)
                if (index != requests.lastIndex) HorizontalDivider(color = WalletBorder)
            }
        }
    }
}

@Composable
private fun WalletTransactions(transactions: List<MobileTransaction>) {
    SectionCard("Transaction history") {
        if (transactions.isEmpty()) {
            Text("No wallet transactions yet.", color = WalletMuted, fontSize = 14.sp)
        } else {
            transactions.take(8).forEachIndexed { index, tx ->
                TransactionRow(tx)
                if (index != transactions.take(8).lastIndex) HorizontalDivider(color = WalletBorder)
            }
        }
    }
}

@Composable
private fun RequestRow(request: MobileWalletRequest) {
    Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        CircleIcon(Icons.Default.Add, WalletBlue)
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text("${request.amount} ${request.currency}", color = WalletText, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
            Text(formatWalletDate(request.createdAt).ifBlank { "No date" }, color = WalletMuted, fontSize = 12.sp)
            request.note?.takeIf { it.isNotBlank() }?.let { Text(it, color = WalletMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        StatusChip(request.statusLabel(), request.status)
    }
}

@Composable
private fun TransactionRow(transaction: MobileTransaction) {
    val debit = transaction.isDebitLike()
    val amount = transaction.walletAmountDisplay(debit)
    Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        CircleIcon(if (debit) Icons.Default.ShoppingCart else Icons.Default.Add, if (debit) WalletRed else WalletGreen)
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(if (debit) "Purchase" else "Top-up", color = WalletText, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
            Text(formatWalletText(transaction.title).ifBlank { formatWalletText(transaction.subtitle) }, color = WalletMuted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(transaction.cleanStatus(), color = statusColor(transaction.status), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Text(r2wMoney(amount, ""), color = if (debit) WalletRed else WalletGreen, fontSize = 15.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun CircleIcon(icon: ImageVector, color: Color) {
    Box(Modifier.size(40.dp).clip(CircleShape).background(color.copy(alpha = .13f)), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = color, modifier = Modifier.size(21.dp))
    }
}

@Composable
private fun StatusChip(label: String, status: String?) {
    val color = statusColor(status)
    val bg = when (color) {
        WalletRed -> WalletRedBg
        WalletYellow -> WalletYellowBg
        else -> WalletGreenBg
    }
    Box(Modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 9.dp, vertical = 5.dp)) {
        Text(label, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(Color.White), border = BorderStroke(1.dp, WalletBorder), elevation = CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text(title, color = WalletText, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            content()
        }
    }
}

@Composable
private fun InfoCard(title: String, message: String, color: Color) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(Color.White), border = BorderStroke(1.dp, color.copy(alpha = .25f))) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = color, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
            Text(message, color = WalletMuted, fontSize = 13.sp)
        }
    }
}

private fun MobileTransaction.isDebitLike(): Boolean {
    val combined = "${amount} ${title} ${status.orEmpty()}".lowercase(Locale.ENGLISH)
    return amount.trim().startsWith("-") || combined.contains("debit") || combined.contains("purchase") || combined.contains("charge") || combined.contains("deduct") || combined.contains("vendor")
}

private fun MobileTransaction.walletAmountDisplay(debit: Boolean): String {
    val raw = amount.trim()
    return when {
        raw.isBlank() -> ""
        raw.startsWith("+") || raw.startsWith("-") -> raw
        debit -> "- $raw"
        else -> "+ $raw"
    }
}

private fun MobileTransaction.cleanStatus(): String {
    val raw = status.orEmpty().trim()
    return raw.replace("_", " ").replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ENGLISH) else it.toString() }.ifBlank { "Completed" }
}

private fun formatWalletDate(value: String?): String {
    val raw = value?.trim().orEmpty()
    if (raw.isBlank()) return ""
    val normalized = raw.replace(" ", "T").let { if (it.endsWith("Z", true) || it.contains(Regex("[+-]\\d{2}:?\\d{2}$"))) it else "${it}Z" }
    return runCatching { Instant.parse(normalized).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm", Locale.ENGLISH)) }.getOrElse { raw }
}

private fun formatWalletText(value: String?): String = value.orEmpty().replace(Regex("\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}:\\d{2}(?:\\.\\d+)?(?:Z|[+-]\\d{2}:?\\d{2})?")) { formatWalletDate(it.value) }

private fun statusColor(statusValue: String?): Color {
    val status = statusValue.orEmpty().lowercase(Locale.ENGLISH)
    return when {
        status.contains("fail") || status.contains("cancel") || status.contains("reject") || status.contains("error") -> WalletRed
        status.contains("pending") || status.contains("review") || status.contains("processing") || status.contains("waiting") -> WalletYellow
        else -> WalletGreen
    }
}
