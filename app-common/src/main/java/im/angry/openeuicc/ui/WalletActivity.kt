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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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

private val WalletBlue = Color(0xFF0F6BFF)
private val WalletBg = Color(0xFFF8FAFD)
private val WalletText = Color(0xFF20242C)
private val WalletMuted = Color(0xFF6B7280)
private val WalletBorder = Color(0xFFE1E6EF)
private val WalletGreen = Color(0xFF12813A)
private val WalletRed = Color(0xFFB42336)
private val WalletOrange = Color(0xFFFF8A00)

class WalletActivity : ComponentActivity() {
    private val tokenStore by lazy { AuthTokenStore(this) }
    private val authApi by lazy { Roam2WorldAuthApi(BuildConfig.ROAM2WORLD_API_BASE_URL) }

    private var loading by mutableStateOf(false)
    private var walletData by mutableStateOf<MobileWalletData?>(null)
    private var recentRequests by mutableStateOf<List<MobileWalletRequest>>(emptyList())
    private var errorMessage by mutableStateOf<String?>(null)

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
                requests = recentRequests,
                error = errorMessage,
                onBack = { finish() },
                onRefresh = { loadWallet() },
                onRequestBalance = { startActivity(Intent(this, WalletRequestActivity::class.java)) }
            )
        }
        loadWallet()
    }

    private fun loadWallet() {
        lifecycleScope.launch {
            loading = true
            errorMessage = null
            val session = activeSessionOrReturnToLogin()
            if (session == null) {
                loading = false
                return@launch
            }
            val walletResult = runCatching { authApi.wallet(session) }
            val requestResult = runCatching { authApi.walletRequests(session) }
            walletResult.onSuccess { walletData = it }.onFailure { errorMessage = it.message ?: "Wallet could not be loaded" }
            recentRequests = requestResult.getOrNull().orEmpty().take(5)
            loading = false
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
    requests: List<MobileWalletRequest>,
    error: String?,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onRequestBalance: () -> Unit
) {
    val transactions = walletData?.transactions.orEmpty()
    MaterialTheme {
        Surface(Modifier.fillMaxSize(), color = WalletBg) {
            Box(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize().padding(start = 18.dp, top = 14.dp, end = 18.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.ArrowBack, null, tint = WalletText, modifier = Modifier.size(30.dp).clickable(onClick = onBack))
                        Text("Wallet Request", color = WalletText, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(start = 18.dp).weight(1f))
                        if (loading) CircularProgressIndicator(modifier = Modifier.size(22.dp), color = WalletBlue, strokeWidth = 2.dp)
                        Icon(Icons.Default.Refresh, null, tint = WalletBlue, modifier = Modifier.padding(start = 12.dp).size(28.dp).clickable(onClick = onRefresh))
                    }

                    WalletBalanceCard(balance = walletData?.currentBalance ?: "0", onRequestBalance = onRequestBalance)
                    WalletKpiRow(
                        pending = requests.countStatus("pending"),
                        approved = requests.countStatus("approved", "approve", "completed", "success"),
                        rejected = requests.countStatus("rejected", "reject", "declined", "failed", "cancelled"),
                        total = requests.size
                    )

                    error?.let { ErrorCard(it) }

                    LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.weight(1f)) {
                        item { WalletRequestFormShell(onSubmit = onRequestBalance) }
                        item { TransactionListCard(transactions) }
                        item { BalanceHistoryCard(requests) }
                    }
                }
                R2wBottomNav(selected = R2wBottomTab.Wallet, modifier = Modifier.align(Alignment.BottomCenter))
            }
        }
    }
}

@Composable
private fun WalletBalanceCard(balance: String, onRequestBalance: () -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(WalletBlue), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("Available Balance", color = Color.White.copy(alpha = .92f), fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(r2wMoney(balance), color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("≈ ${r2wMoney(balance)} USD", color = Color.White.copy(alpha = .82f), fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("View Transactions >", color = Color.White.copy(alpha = .92f), fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Spacer(Modifier.weight(1f))
                Button(onClick = onRequestBalance, modifier = Modifier.width(124.dp).height(38.dp), shape = RoundedCornerShape(18.dp), colors = ButtonDefaults.buttonColors(containerColor = Color.White)) {
                    Text("+ Add Funds", color = WalletBlue, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun WalletRequestFormShell(onSubmit: () -> Unit) {
    SectionCard("Create Request") {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Amount", color = WalletMuted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(14.dp)).background(Color.White).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("$", color = WalletText, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                Text("0.00", color = WalletMuted, fontSize = 15.sp, modifier = Modifier.padding(start = 14.dp))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("$50", "$100", "$250", "$500").forEach { amount ->
                    Box(Modifier.weight(1f).height(34.dp).clip(RoundedCornerShape(18.dp)).background(Color(0xFFEAF1FF)), contentAlignment = Alignment.Center) {
                        Text(amount, color = WalletBlue, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
            Button(onClick = onSubmit, modifier = Modifier.fillMaxWidth().height(46.dp), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = WalletBlue)) {
                Text("Submit Top-up Request", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

@Composable
private fun WalletKpiRow(pending: Int, approved: Int, rejected: Int, total: Int) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(Color.White), border = BorderStroke(1.dp, WalletBorder), elevation = CardDefaults.cardElevation(1.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            WalletKpiItem(value = pending.toString(), label = "Pending", accent = WalletOrange, modifier = Modifier.weight(1f))
            WalletKpiItem(value = approved.toString(), label = "Approved", accent = WalletGreen, modifier = Modifier.weight(1f))
            WalletKpiItem(value = rejected.toString(), label = "Rejected", accent = WalletRed, modifier = Modifier.weight(1f))
            WalletKpiItem(value = total.toString(), label = "Total", accent = WalletBlue, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun WalletKpiItem(value: String, label: String, accent: Color, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Box(Modifier.size(30.dp).clip(CircleShape).background(accent.copy(alpha = .12f)), contentAlignment = Alignment.Center) {
            Text(value, color = accent, fontSize = 13.sp, fontWeight = FontWeight.Black, maxLines = 1)
        }
        Text(label, color = WalletMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun TransactionListCard(transactions: List<MobileTransaction>) {
    SectionCard("Recent Transactions") {
        if (transactions.isEmpty()) {
            EmptyText("No transactions yet")
        } else {
            transactions.take(8).forEachIndexed { index, transaction ->
                WalletTransactionRow(transaction)
                if (index != transactions.take(8).lastIndex) HorizontalDivider(color = WalletBorder)
            }
        }
    }
}

@Composable
private fun BalanceHistoryCard(requests: List<MobileWalletRequest>) {
    SectionCard("Recent Requests") {
        if (requests.isEmpty()) {
            EmptyText("No balance request history")
        } else {
            requests.take(6).forEachIndexed { index, request ->
                BalanceRequestRow(request)
                if (index != requests.take(6).lastIndex) HorizontalDivider(color = WalletBorder)
            }
        }
    }
}

@Composable
private fun WalletTransactionRow(transaction: MobileTransaction) {
    val debit = transaction.isDebitLike()
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(42.dp).clip(CircleShape).background(Color(0xFFEAF0FF)), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.SwapVert, null, tint = WalletBlue, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(transaction.title.ifBlank { if (debit) "Purchase" else "Balance" }, color = WalletText, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(transaction.subtitle.ifBlank { transaction.status.orEmpty() }, color = WalletMuted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(transaction.walletAmountDisplay(debit), color = if (debit) WalletRed else WalletGreen, fontSize = 15.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun BalanceRequestRow(request: MobileWalletRequest) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(42.dp).clip(CircleShape).background(Color(0xFFEAF0FF)), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.History, null, tint = WalletBlue, modifier = Modifier.size(21.dp))
        }
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text("${request.amount} ${request.currency}", color = WalletText, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
            Text(request.createdAt?.take(16)?.replace('T', ' ') ?: "--", color = WalletMuted, fontSize = 13.sp)
        }
        Text(request.statusLabel(), color = WalletMuted, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(Color.White), border = BorderStroke(1.dp, WalletBorder), elevation = CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, color = WalletText, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
            content()
        }
    }
}

@Composable
private fun ErrorCard(message: String) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(Color.White), border = BorderStroke(1.dp, WalletRed.copy(alpha = .25f))) {
        Text(message, color = WalletRed, fontSize = 13.sp, modifier = Modifier.padding(14.dp))
    }
}

@Composable
private fun EmptyText(text: String) {
    Text(text, color = WalletMuted, fontSize = 14.sp, modifier = Modifier.padding(vertical = 8.dp))
}

private fun List<MobileWalletRequest>.countStatus(vararg keywords: String): Int {
    return count { request ->
        val status = request.status.lowercase()
        keywords.any { keyword -> status.contains(keyword) }
    }
}

private fun MobileTransaction.isDebitLike(): Boolean {
    val combined = "${amount} ${title} ${status.orEmpty()}".lowercase()
    return amount.trim().startsWith("-") || combined.contains("debit") || combined.contains("purchase") || combined.contains("charge") || combined.contains("deduct")
}

private fun MobileTransaction.walletAmountDisplay(debit: Boolean): String {
    val raw = amount.trim()
    return when {
        raw.isBlank() -> "--"
        raw.startsWith("+") || raw.startsWith("-") -> r2wMoney(raw, "")
        debit -> "- ${r2wMoney(raw)}"
        else -> "+ ${r2wMoney(raw)}"
    }
}
