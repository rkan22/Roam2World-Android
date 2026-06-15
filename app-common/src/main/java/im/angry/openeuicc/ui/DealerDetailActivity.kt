package im.angry.openeuicc.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import im.angry.openeuicc.auth.AuthSession
import im.angry.openeuicc.auth.AuthTokenStore
import im.angry.openeuicc.auth.JwtUtils
import im.angry.openeuicc.auth.MobileDealer
import im.angry.openeuicc.auth.MobileOrder
import im.angry.openeuicc.auth.Roam2WorldAuthApi
import im.angry.openeuicc.common.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.util.Locale

class DealerDetailActivity : ComponentActivity() {
    private val tokenStore by lazy { AuthTokenStore(this) }
    private val authApi by lazy { Roam2WorldAuthApi(BuildConfig.ROAM2WORLD_API_BASE_URL) }

    private val dealerId: String? by lazy { intent.getStringExtra(EXTRA_DEALER_ID) }

    private var dealer by mutableStateOf<MobileDealer?>(null)
    private var loading by mutableStateOf(false)
    private var errorMessage by mutableStateOf<String?>(null)
    private var showRefundDialog by mutableStateOf(false)
    private var refreshKey by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            DealerDetailScreen(
                dealer = dealer,
                fallbackName = intent.getStringExtra(EXTRA_DEALER_NAME).orEmpty().ifBlank { "Dealer detail" },
                loading = loading,
                errorMessage = errorMessage,
                showRefundDialog = showRefundDialog,
                refreshKey = refreshKey,
                onBack = { finish() },
                onRefresh = { loadDealer() },
                onAllocate = { openAllocateBalance() },
                onRefund = { showRefundDialog = true },
                onDismissRefund = { showRefundDialog = false },
                onSubmitRefund = { amount, reason ->
                    showRefundDialog = false
                    val id = dealer?.id ?: dealerId ?: return@DealerDetailScreen
                    refundDealerBalance(id, amount, reason)
                },
                onSuspend = { updateDealerStatus(suspendDealer = true) },
                onActivate = { updateDealerStatus(suspendDealer = false) }
            )
        }

        loadDealer()
    }

    override fun onResume() {
        super.onResume()
        loadDealer()
    }

    private fun loadDealer() {
        val id = dealerId
        if (id.isNullOrBlank()) {
            errorMessage = "Dealer detail is unavailable."
            return
        }

        lifecycleScope.launch {
            loading = true
            errorMessage = null

            val session = activeSessionOrReturnToLogin()
            if (session == null) {
                loading = false
                return@launch
            }

            val result = runCatching { authApi.dealer(session, id) }
            loading = false

            result
                .onSuccess {
                    dealer = it
                    refreshKey += 1
                }
                .onFailure {
                    errorMessage = it.message ?: "Could not load dealer detail."
                }
        }
    }

    private fun openAllocateBalance() {
        val current = dealer ?: return
        val id = current.id ?: return

        startActivity(
            Intent(this, AllocateBalanceActivity::class.java)
                .putExtra(AllocateBalanceActivity.EXTRA_DEALER_ID, id)
                .putExtra(AllocateBalanceActivity.EXTRA_DEALER_NAME, current.name)
        )
    }

    private fun refundDealerBalance(dealerId: String, amount: String, reason: String) {
        lifecycleScope.launch {
            loading = true
            errorMessage = null

            val session = activeSessionOrReturnToLogin()
            if (session == null) {
                loading = false
                return@launch
            }

            val result = runCatching {
                authApi.modifyDealerBalance(
                    session = session,
                    dealerId = dealerId,
                    amount = amount.trim().replace(",", "."),
                    direction = "refund_to_reseller",
                    reason = reason.trim().ifBlank { "Wrong allocation correction" }
                )
            }

            loading = false

            result
                .onSuccess {
                    dealer = it.dealer
                    refreshKey += 1
                    Toast.makeText(
                        this@DealerDetailActivity,
                        "Refunded ${it.amount} ${it.currency}. Dealer balance is now ${it.dealer.currentBalance}.",
                        Toast.LENGTH_LONG
                    ).show()
                }
                .onFailure {
                    errorMessage = it.message ?: "Could not refund dealer balance."
                }
        }
    }

    private fun updateDealerStatus(suspendDealer: Boolean) {
        val id = dealerId ?: dealer?.id ?: return

        lifecycleScope.launch {
            loading = true
            errorMessage = null

            val session = activeSessionOrReturnToLogin()
            if (session == null) {
                loading = false
                return@launch
            }

            val result = runCatching {
                if (suspendDealer) authApi.suspendDealer(session, id) else authApi.activateDealer(session, id)
            }

            loading = false

            result
                .onSuccess {
                    dealer = it
                    refreshKey += 1
                }
                .onFailure {
                    errorMessage = it.message ?: "Could not update dealer status."
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
private fun DealerDetailScreen(
    dealer: MobileDealer?,
    fallbackName: String,
    loading: Boolean,
    errorMessage: String?,
    showRefundDialog: Boolean,
    refreshKey: Int,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onAllocate: () -> Unit,
    onRefund: () -> Unit,
    onDismissRefund: () -> Unit,
    onSubmitRefund: (String, String) -> Unit,
    onSuspend: () -> Unit,
    onActivate: () -> Unit
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
                    DealerHero(
                        dealer = dealer,
                        fallbackName = fallbackName,
                        loading = loading,
                        onBack = onBack,
                        onRefresh = onRefresh
                    )

                    errorMessage?.let {
                        InfoCard(title = "Dealer detail error") {
                            Text(it, color = Color(0xFFDC2626))
                            OutlinedButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                                Text("Try again")
                            }
                        }
                    }

                    if (loading && dealer == null) {
                        InfoCard(title = "Loading dealer") {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = Color(0xFFFF7900))
                                Text("Fetching latest dealer data...", color = Color(0xFF6B7280))
                            }
                        }
                    }

                    dealer?.let { current ->
                        DealerBalanceCard(
                            dealer = current,
                            loading = loading,
                            onAllocate = onAllocate,
                            onRefund = onRefund,
                            onSuspend = onSuspend,
                            onActivate = onActivate
                        )

                        DealerStatsCard(current)

                        RecentOrdersCard(
                            orders = current.recentOrders,
                            refreshKey = refreshKey
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
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Text("Back")
                        }
                        Button(
                            onClick = onRefresh,
                            enabled = !loading,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF7900)),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Text(if (loading) "Loading..." else "Refresh", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                if (showRefundDialog && dealer != null) {
                    RefundDialog(
                        dealerName = dealer.name,
                        onDismiss = onDismissRefund,
                        onSubmit = onSubmitRefund
                    )
                }
            }
        }
    }
}

@Composable
private fun DealerHero(
    dealer: MobileDealer?,
    fallbackName: String,
    loading: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit
) {
    val title = dealer?.name ?: fallbackName
    val status = dealer?.statusLabel() ?: "Loading"
    val statusColors = dealerStatusColors(dealer?.status.orEmpty())

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
                        text = title,
                        color = Color.White,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = dealer?.email.orEmpty().ifBlank { "Dealer account" },
                        color = Color.White.copy(alpha = 0.72f),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Back",
                        color = Color(0xFFFF7900),
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.clickable(onClick = onBack)
                    )
                    Text(
                        text = if (loading) "Loading..." else "Refresh",
                        color = Color.White.copy(alpha = 0.78f),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable(enabled = !loading, onClick = onRefresh)
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                DealerAvatar(title)
                Column(modifier = Modifier.weight(1f)) {
                    Text("Status", color = Color.White.copy(alpha = 0.72f), style = MaterialTheme.typography.bodySmall)
                    Text(status, color = Color.White, fontWeight = FontWeight.Black)
                }
                StatusPill(status, statusColors)
            }
        }
    }
}

@Composable
private fun DealerBalanceCard(
    dealer: MobileDealer,
    loading: Boolean,
    onAllocate: () -> Unit,
    onRefund: () -> Unit,
    onSuspend: () -> Unit,
    onActivate: () -> Unit
) {
    val suspended = dealer.status.equals("suspended", ignoreCase = true)

    InfoCard(title = "Balance actions") {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SmallMetric("Balance", r2wMoney(dealer.currentBalance), Modifier.weight(1f))
            SmallMetric("Currency", dealer.currency.ifBlank { "USD" }, Modifier.weight(1f))
        }

        Button(
            onClick = onAllocate,
            enabled = !loading,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF7900)),
            shape = RoundedCornerShape(18.dp)
        ) {
            Text("Allocate balance", fontWeight = FontWeight.Bold)
        }

        OutlinedButton(
            onClick = onRefund,
            enabled = !loading,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp)
        ) {
            Text("Refund money")
        }

        if (suspended) {
            Button(
                onClick = onActivate,
                enabled = !loading,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF168653)),
                shape = RoundedCornerShape(18.dp)
            ) {
                Text("Activate dealer", fontWeight = FontWeight.Bold)
            }
        } else {
            OutlinedButton(
                onClick = onSuspend,
                enabled = !loading,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp)
            ) {
                Text("Suspend dealer")
            }
        }
    }
}

@Composable
private fun DealerStatsCard(dealer: MobileDealer) {
    InfoCard(title = "Dealer performance") {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SmallMetric("Orders", dealer.totalOrders.ifBlank { "0" }, Modifier.weight(1f))
            SmallMetric("Revenue", dealer.revenue.ifBlank { "0" }, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SmallMetric("Allocated", dealer.totalAllocated ?: "0", Modifier.weight(1f))
            SmallMetric("Spent", dealer.totalSpent ?: "0", Modifier.weight(1f))
        }

        dealer.phoneNumber?.takeIf { it.isNotBlank() }?.let {
            DetailLine("Phone", it)
        }
        dealer.countryCode?.takeIf { it.isNotBlank() }?.let {
            DetailLine("Country", it)
        }
        dealer.createdAt?.takeIf { it.isNotBlank() }?.let {
            DetailLine("Created", it)
        }
        dealer.suspensionReason?.takeIf { it.isNotBlank() }?.let {
            DetailLine("Suspension reason", it)
        }
    }
}

@Composable
private fun RecentOrdersCard(
    orders: List<MobileOrder>,
    refreshKey: Int
) {
    InfoCard(title = "Recent orders") {
        if (orders.isEmpty()) {
            Text("No recent orders yet.", color = Color(0xFF6B7280))
        } else {
            orders.forEachIndexed { index, order ->
                if (index > 0) HorizontalDivider(color = Color(0xFFE5E7EB))
                DealerOrderRow(order = order, refreshKey = refreshKey)
            }
        }
    }
}

@Composable
private fun DealerOrderRow(order: MobileOrder, refreshKey: Int) {
    val statusColors = orderStatusColors(order.status.orEmpty())

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(Color(0xFFFFEFE2), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("#", color = Color(0xFFFF7900), fontWeight = FontWeight.Black)
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    order.displayNumber(),
                    color = Color(0xFF17181C),
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    PackageNameCleaner.clean(order.packageName).orEmpty().ifBlank { "Package" },
                    color = Color(0xFF6B7280),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    listOf(
                        visibleProvider(order.provider),
                        order.createdAt.orEmpty()
                    ).filter { it.isNotBlank() }.joinToString(" • "),
                    color = Color(0xFF6B7280),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(r2wMoney(order.price, ""), color = Color(0xFF17181C), fontWeight = FontWeight.Black)
                StatusPill(order.statusLabel().orEmpty().ifBlank { "Unknown" }, statusColors)
            }
        }
    }
}

@Composable
private fun RefundDialog(
    dealerName: String,
    onDismiss: () -> Unit,
    onSubmit: (String, String) -> Unit
) {
    var amount by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("Wrong allocation correction") }
    var amountError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Refund money", fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Return money from $dealerName back to your reseller wallet.", color = Color(0xFF6B7280))
                OutlinedTextField(
                    value = amount,
                    onValueChange = {
                        amount = it
                        amountError = null
                    },
                    label = { Text("Refund amount") },
                    singleLine = true,
                    isError = amountError != null,
                    supportingText = { amountError?.let { Text(it) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Reason") },
                    minLines = 2
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val clean = amount.trim().replace(",", ".")
                    val value = clean.toDoubleOrNull()
                    if (value == null || value <= 0.0) {
                        amountError = "Enter an amount greater than 0."
                        return@Button
                    }
                    onSubmit(clean, reason)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF7900))
            ) {
                Text("Refund money")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun DealerAvatar(name: String) {
    Box(
        modifier = Modifier
            .size(54.dp)
            .background(Color(0xFFFFEFE2), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(dealerInitials(name), color = Color(0xFF17181C), fontWeight = FontWeight.Black)
    }
}

@Composable
private fun SmallMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC))
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(value, color = Color(0xFF17181C), fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(label, color = Color(0xFF6B7280), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color(0xFF6B7280), fontWeight = FontWeight.SemiBold)
        Text(value, color = Color(0xFF17181C), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun StatusPill(label: String, colors: Pair<Color, Color>) {
    Box(
        modifier = Modifier
            .background(colors.second, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text("●  $label", color = colors.first, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black)
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
            Text(title, color = Color(0xFF17181C), fontWeight = FontWeight.Black)
            HorizontalDivider()
            content()
        }
    }
}

private fun dealerStatusColors(status: String): Pair<Color, Color> =
    when (status.lowercase(Locale.ROOT)) {
        "suspended" -> Color(0xFFDC2626) to Color(0xFFFEE2E2)
        "inactive" -> Color(0xFFF59E0B) to Color(0xFFFEF3C7)
        "active" -> Color(0xFF168653) to Color(0xFFE4F8EC)
        else -> Color(0xFF6B7280) to Color(0xFFF3F4F6)
    }

private fun orderStatusColors(status: String): Pair<Color, Color> =
    when (status.lowercase(Locale.ROOT)) {
        "completed", "success", "paid" -> Color(0xFF168653) to Color(0xFFE4F8EC)
        "pending", "processing" -> Color(0xFFF59E0B) to Color(0xFFFEF3C7)
        "failed", "cancelled", "canceled", "refunded" -> Color(0xFFDC2626) to Color(0xFFFEE2E2)
        else -> Color(0xFF6B7280) to Color(0xFFF3F4F6)
    }

private fun dealerInitials(name: String): String =
    name.split(" ")
        .filter { it.isNotBlank() }
        .take(2)
        .map { it.first().uppercaseChar() }
        .joinToString("")
        .ifBlank { "DL" }

private fun visibleProvider(provider: String?): String =
    provider.orEmpty()
        .replace("TGT", "Orange", ignoreCase = true)
        .replace("tgt", "Orange", ignoreCase = true)
        .ifBlank { "Orange" }
