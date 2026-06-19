package im.angry.openeuicc.ui

import android.content.Intent
import android.graphics.Color as AndroidColor
import android.os.Bundle
import java.math.BigDecimal
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
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
    private var amount by mutableStateOf("")
    private var currency by mutableStateOf("EUR")
    private var note by mutableStateOf("")
    private var submittingRequest by mutableStateOf(false)
    private var formMessage by mutableStateOf<String?>(null)
    private var formError by mutableStateOf<String?>(null)
    private var amountError by mutableStateOf<String?>(null)

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
                amount = amount,
                currency = currency,
                note = note,
                submittingRequest = submittingRequest,
                formMessage = formMessage,
                formError = formError,
                amountError = amountError,
                onAmountChange = { amount = it; amountError = null; formError = null; formMessage = null },
                onCurrencyChange = { currency = it.uppercase().take(3); formError = null; formMessage = null },
                onNoteChange = { note = it.take(120); formError = null; formMessage = null },
                onQuickAmount = { amount = it; amountError = null; formError = null; formMessage = null },
                onSubmitRequest = { submitWalletRequest() },
                onViewAllRequests = { startActivity(Intent(this, WalletRequestHistoryActivity::class.java)) }
            )
        }
        loadWallet()
    }

    private fun submitWalletRequest() {
        amountError = null
        formError = null
        formMessage = null

        val cleanAmount = amount.trim().replace(',', '.')
        val cleanCurrency = currency.trim().uppercase().ifBlank { "EUR" }
        val cleanNote = note.trim()

        if (cleanAmount.toBigDecimalOrNull()?.let { it > BigDecimal.ZERO } != true) {
            amountError = "Enter an amount greater than 0"
            return
        }

        if (cleanCurrency.length != 3 || !cleanCurrency.all { it.isLetter() }) {
            formError = "Currency must be a 3-letter code"
            return
        }

        lifecycleScope.launch {
            submittingRequest = true
            val session = activeSessionOrReturnToLogin()
            if (session == null) {
                submittingRequest = false
                return@launch
            }

            runCatching { authApi.createWalletRequest(session, cleanAmount, cleanCurrency, cleanNote) }
                .onSuccess { request ->
                    formMessage = "Top-up request submitted for ${request.amount} ${request.currency}"
                    amount = ""
                    note = ""
                    currency = request.currency.ifBlank { cleanCurrency }
                    loadWallet()
                }
                .onFailure { formError = it.message ?: "Could not submit top-up request" }

            submittingRequest = false
        }
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
    amount: String,
    currency: String,
    note: String,
    submittingRequest: Boolean,
    formMessage: String?,
    formError: String?,
    amountError: String?,
    onAmountChange: (String) -> Unit,
    onCurrencyChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onQuickAmount: (String) -> Unit,
    onSubmitRequest: () -> Unit,
    onViewAllRequests: () -> Unit
) {
    MaterialTheme {
        Surface(Modifier.fillMaxSize(), color = WalletBg) {
            Box(Modifier.fillMaxSize()) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(start = 18.dp, top = 14.dp, end = 18.dp, bottom = 106.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    WalletTopBar(loading = loading, onBack = onBack, onRefresh = onRefresh)

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Wallet Request", color = WalletText, fontSize = 29.sp, fontWeight = FontWeight.ExtraBold)
                        Text("Request funds to top up your wallet balance.", color = WalletMuted, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    }

                    WalletBalanceCard(balance = walletData?.currentBalance ?: "0", onAddFunds = { onQuickAmount("50") })
                    WalletKpiRow(
                        pending = requests.countStatus("pending"),
                        approved = requests.countStatus("approved", "approve", "completed", "success"),
                        rejected = requests.countStatus("rejected", "reject", "declined", "failed", "cancelled"),
                        total = requests.size
                    )

                    error?.let { ErrorCard(it) }
                    WalletRequestFormShell(
                        amount = amount,
                        currency = currency,
                        note = note,
                        submittingRequest = submittingRequest,
                        formMessage = formMessage,
                        formError = formError,
                        amountError = amountError,
                        onAmountChange = onAmountChange,
                        onCurrencyChange = onCurrencyChange,
                        onNoteChange = onNoteChange,
                        onQuickAmount = onQuickAmount,
                        onSubmit = onSubmitRequest
                    )
                    BalanceHistoryCard(requests = requests, onViewAll = onViewAllRequests)
                }
                R2wBottomNav(selected = R2wBottomTab.Wallet, modifier = Modifier.align(Alignment.BottomCenter))
            }
        }
    }
}

@Composable
private fun WalletTopBar(loading: Boolean, onBack: () -> Unit, onRefresh: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.ArrowBack, null, tint = WalletText, modifier = Modifier.size(30.dp).clickable(onClick = onBack))
        Text(
            buildAnnotatedString {
                append("Roam")
                withStyle(SpanStyle(color = Color(0xFF18B7A7))) { append("2") }
                append("World")
            },
            color = WalletText,
            fontSize = 25.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(start = 18.dp)
        )
        Box(Modifier.padding(start = 8.dp).clip(RoundedCornerShape(6.dp)).background(WalletBlue).padding(horizontal = 7.dp, vertical = 3.dp)) {
            Text("B2B", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.weight(1f))
        if (loading) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = WalletBlue, strokeWidth = 2.dp)
        Icon(Icons.Default.Refresh, null, tint = WalletText, modifier = Modifier.padding(start = 12.dp).size(26.dp).clickable(onClick = onRefresh))
    }
}

@Composable
private fun WalletBalanceCard(balance: String, onAddFunds: () -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(WalletBlue), elevation = CardDefaults.cardElevation(4.dp)) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 17.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Available Balance", color = Color.White.copy(alpha = .92f), fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Icon(Icons.Default.Visibility, null, tint = Color.White, modifier = Modifier.padding(start = 8.dp).size(18.dp))
            }
            Text(r2wMoney(balance), color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("≈ ${r2wMoney(balance)} USD", color = Color.White.copy(alpha = .88f), fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Button(onClick = onAddFunds, modifier = Modifier.width(150.dp).height(48.dp), shape = RoundedCornerShape(18.dp), colors = ButtonDefaults.buttonColors(containerColor = Color.White)) {
                    Icon(Icons.Default.AddCircle, null, tint = WalletBlue, modifier = Modifier.size(18.dp))
                    Text("Add Funds", color = WalletBlue, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, modifier = Modifier.padding(start = 7.dp))
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                Text("View Transactions", color = Color.White.copy(alpha = .94f), fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Icon(Icons.Default.ArrowForwardIos, null, tint = Color.White, modifier = Modifier.padding(start = 8.dp).size(16.dp))
            }
        }
    }
}

@Composable
private fun WalletRequestFormShell(
    amount: String,
    currency: String,
    note: String,
    submittingRequest: Boolean,
    formMessage: String?,
    formError: String?,
    amountError: String?,
    onAmountChange: (String) -> Unit,
    onCurrencyChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onQuickAmount: (String) -> Unit,
    onSubmit: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Create Request", color = WalletText, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(Color.White), border = BorderStroke(1.dp, WalletBorder), elevation = CardDefaults.cardElevation(1.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = amount,
                    onValueChange = { value -> onAmountChange(value.filter { it.isDigit() || it == '.' || it == ',' }) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Amount") },
                    leadingIcon = { Text("€", color = WalletText, fontSize = 17.sp, fontWeight = FontWeight.Bold) },
                    placeholder = { Text("0.00", color = WalletMuted) },
                    singleLine = true,
                    isError = amountError != null,
                    supportingText = { amountError?.let { Text(it, color = WalletRed) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(12.dp)
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("50", "100", "250", "500").forEach { quickAmount ->
                        Box(
                            Modifier
                                .weight(1f)
                                .height(32.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFFEAF1FF))
                                .clickable { onQuickAmount(quickAmount) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("€$quickAmount", color = WalletBlue, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
                WalletSelectRow(label = "Currency", iconText = "€", value = currency.ifBlank { "EUR" }, iconColor = WalletBlue)
                OutlinedTextField(
                    value = currency,
                    onValueChange = onCurrencyChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Currency code") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
                WalletSelectRow(label = "Payment Method", iconText = "▦", value = "Bank Transfer", iconColor = WalletBlue)
                OutlinedTextField(
                    value = note,
                    onValueChange = onNoteChange,
                    modifier = Modifier.fillMaxWidth().height(92.dp),
                    label = { Text("Note (Optional)") },
                    placeholder = { Text("Add note for this top-up request...") },
                    supportingText = { Text("${note.length}/120", color = WalletMuted, modifier = Modifier.fillMaxWidth()) },
                    shape = RoundedCornerShape(12.dp)
                )
                formError?.let { Text(it, color = WalletRed, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                formMessage?.let { Text(it, color = WalletGreen, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                Button(
                    onClick = onSubmit,
                    enabled = !submittingRequest,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = WalletBlue)
                ) {
                    if (submittingRequest) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text("Submit Top-up Request", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun WalletSelectRow(label: String, iconText: String, value: String, iconColor: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, color = WalletMuted, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Row(Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(12.dp)).background(Color.White).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(28.dp).clip(CircleShape).background(iconColor), contentAlignment = Alignment.Center) {
                Text(iconText, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Black)
            }
            Text(value, color = WalletText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 12.dp).weight(1f))
            Icon(Icons.Default.ExpandMore, null, tint = WalletMuted, modifier = Modifier.size(22.dp))
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
private fun BalanceHistoryCard(requests: List<MobileWalletRequest>, onViewAll: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Recent Requests", color = WalletText, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
            Text("View All", color = WalletBlue, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.clickable(onClick = onViewAll))
        }
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(Color.White), border = BorderStroke(1.dp, WalletBorder), elevation = CardDefaults.cardElevation(1.dp)) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
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
