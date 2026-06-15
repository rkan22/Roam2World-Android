package im.angry.openeuicc.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import im.angry.openeuicc.auth.AuthSession
import im.angry.openeuicc.auth.AuthTokenStore
import im.angry.openeuicc.auth.JwtUtils
import im.angry.openeuicc.auth.MobileOrder
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

class TransactionsActivity : ComponentActivity() {
    private val tokenStore by lazy { AuthTokenStore(this) }
    private val authApi by lazy { Roam2WorldAuthApi(BuildConfig.ROAM2WORLD_API_BASE_URL) }

    private var loading by mutableStateOf(false)
    private var orders by mutableStateOf<List<MobileOrder>>(emptyList())
    private var errorMessage by mutableStateOf<String?>(null)
    private var query by mutableStateOf("")
    private var selectedFilter by mutableStateOf(TransactionFilter.ALL)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            TransactionsScreen(
                loading = loading,
                orders = orders,
                errorMessage = errorMessage,
                query = query,
                selectedFilter = selectedFilter,
                onQueryChange = { query = it },
                onFilterChange = { selectedFilter = it },
                onRefresh = { loadOrders() },
                onOpenOrder = { order ->
                    startActivity(MobileOrderDetailActivity.createIntent(this, order))
                },
                onDashboard = { startActivity(Intent(this, DashboardActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)) },
                onPackages = { startActivity(Intent(this, PackagesActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)) },
                onWallet = { startActivity(Intent(this, WalletActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)) },
                onEsims = { startActivity(Intent(this, MobileEsimsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)) },
                onMore = { finish() }
            )
        }

        loadOrders()
    }

    private fun loadOrders() {
        lifecycleScope.launch {
            loading = true
            errorMessage = null

            val session = activeSessionOrReturnToLogin()
            if (session == null) {
                loading = false
                return@launch
            }

            val result = runCatching {
                authApi.orders(session).orders
            }

            loading = false
            result
                .onSuccess { orders = it }
                .onFailure { errorMessage = it.message ?: "Orders could not be loaded" }
        }
    }

    private suspend fun activeSessionOrReturnToLogin(): AuthSession? {
        val savedSession = withContext(Dispatchers.IO) { tokenStore.getSession() }
            ?: return redirectToLogin()

        if (!JwtUtils.isExpired(savedSession.accessToken)) return savedSession

        val refreshed = runCatching { authApi.refresh(savedSession) }.getOrNull()
            ?: return redirectToLogin()

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
}

@Composable
private fun TransactionsScreen(
    loading: Boolean,
    orders: List<MobileOrder>,
    errorMessage: String?,
    query: String,
    selectedFilter: TransactionFilter,
    onQueryChange: (String) -> Unit,
    onFilterChange: (TransactionFilter) -> Unit,
    onRefresh: () -> Unit,
    onOpenOrder: (MobileOrder) -> Unit,
    onDashboard: () -> Unit,
    onPackages: () -> Unit,
    onWallet: () -> Unit,
    onEsims: () -> Unit,
    onMore: () -> Unit
) {
    val orange = Color(0xFFFF7900)
    val bg = Color(0xFFF7F7FA)

    val filtered by remember(orders, query, selectedFilter) {
        derivedStateOf {
            val cleanQuery = query.trim().lowercase()
            orders
                .filter { selectedFilter.matches(it.status) }
                .filter { order ->
                    cleanQuery.isBlank() || listOfNotNull(
                        order.orderNumber,
                        order.id,
                        order.packageName,
                        order.price,
                        order.status,
                        order.provider,
                        order.createdAt,
                        order.esimId,
                        order.customerEmail,
                        order.esim?.customerName(),
                        order.esim?.customerPhone,
                        order.esim?.customerEmail,
                        order.esim?.iccid
                    ).joinToString(" ").lowercase().contains(cleanQuery)
                }
        }
    }

    val pending = orders.count { TransactionFilter.PENDING.matches(it.status) }
    val completed = orders.count { TransactionFilter.COMPLETED.matches(it.status) }
    val failed = orders.count { TransactionFilter.FAILED.matches(it.status) }

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
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(30.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF17181C))
                    ) {
                        Column(
                            modifier = Modifier.padding(22.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Roam2World",
                                color = orange,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Transactions",
                                color = Color.White,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                text = "${filtered.size} shown • ${orders.size} total • $pending pending • $completed completed • $failed failed",
                                color = Color.White.copy(alpha = 0.72f),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    OutlinedTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Search order, package, customer, ICCID") },
                        singleLine = true
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TransactionFilter.entries.forEach { filter ->
                            AssistChip(
                                onClick = { onFilterChange(filter) },
                                label = {
                                    Text(
                                        filter.label,
                                        fontWeight = if (filter == selectedFilter) FontWeight.Black else FontWeight.SemiBold
                                    )
                                }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Button(
                            onClick = onRefresh,
                            enabled = !loading,
                            colors = ButtonDefaults.buttonColors(containerColor = orange),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text(if (loading) "Loading..." else "Refresh", fontWeight = FontWeight.Bold)
                        }
                    }

                    errorMessage?.let {
                        InfoCard(title = "Error") {
                            Text(
                                text = it,
                                color = Color(0xFFDC2626),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    if (loading) {
                        InfoCard(title = "Loading") {
                            CircularProgressIndicator()
                        }
                    }

                    if (!loading && filtered.isEmpty() && errorMessage == null) {
                        InfoCard(title = "No transactions") {
                            Text(
                                text = "No matching orders found.",
                                color = Color(0xFF6B7280)
                            )
                        }
                    }

                    filtered.forEach { order ->
                        TransactionOrderCard(
                            order = order,
                            onOpen = { onOpenOrder(order) }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                }

                TransactionsBottomNav(
                    onDashboard = onDashboard,
                    onPackages = onPackages,
                    onWallet = onWallet,
                    onEsims = onEsims,
                    onMore = onMore
                )
            }
        }
    }
}

@Composable
private fun TransactionOrderCard(
    order: MobileOrder,
    onOpen: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = order.displayNumber(),
                color = Color(0xFF17181C),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black
            )

            Text(
                text = PackageNameCleaner.clean(order.packageName),
                color = Color(0xFF17181C),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold
            )

            val details = listOfNotNull(
                order.esim?.customerName()?.let { "Customer: $it" },
                order.esim?.customerPhone?.let { "Phone: $it" },
                order.esim?.iccid?.let { "ICCID: $it" },
                order.provider?.let { "Provider: ${visibleProvider(it)}" },
                order.price?.let { "Amount: ${r2wMoney(it)}" },
                order.createdAt?.let { "Date: ${formatTransactionDate(it)}" }
            ).joinToString("\n").ifBlank { "No extra order details" }

            Text(
                text = details,
                color = Color(0xFF6B7280),
                style = MaterialTheme.typography.bodySmall
            )

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Status: ${formatTransactionStatus(order.statusLabel().orEmpty().ifBlank { order.status.orEmpty() })}",
                    color = statusColor(order.status),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "View detail",
                    color = Color(0xFFFF7900),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Black
                )
            }
        }
    }
}

@Composable
private fun InfoCard(
    title: String,
    content: @Composable () -> Unit
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

@Composable
private fun TransactionsBottomNav(
    onDashboard: () -> Unit,
    onPackages: () -> Unit,
    onWallet: () -> Unit,
    onEsims: () -> Unit,
    onMore: () -> Unit
) {
    Surface(shadowElevation = 8.dp, color = Color.White) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BottomNavText("Home", onDashboard)
            BottomNavText("Packages", onPackages)
            BottomNavText("Wallet", onWallet)
            BottomNavText("eSIMs", onEsims)
            BottomNavText("More", onMore, selected = true)
        }
    }
}

@Composable
private fun BottomNavText(
    text: String,
    onClick: () -> Unit,
    selected: Boolean = false
) {
    Text(
        text = text,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(8.dp),
        color = if (selected) Color(0xFFFF7900) else Color(0xFF6B7280),
        fontWeight = if (selected) FontWeight.Black else FontWeight.SemiBold,
        style = MaterialTheme.typography.labelMedium
    )
}

private fun formatTransactionDate(value: String?): String {
    val raw = value?.trim().orEmpty()
    if (raw.isBlank()) return ""
    return runCatching {
        val formatter = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm", Locale.ENGLISH)
        Instant.parse(raw).atZone(ZoneId.systemDefault()).format(formatter)
    }.getOrElse { raw }
}

private fun formatTransactionStatus(value: String?): String {
    val raw = value?.trim().orEmpty()
    if (raw.isBlank()) return "Unknown"
    return raw
        .replace("_", " ")
        .replace("-", " ")
        .split(" ")
        .filter { it.isNotBlank() }
        .joinToString(" ") { word ->
            word.lowercase(Locale.ROOT).replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString()
            }
        }
        .ifBlank { "Unknown" }
}

private fun visibleProvider(provider: String?): String =
    provider.orEmpty()
        .replace("TGT", "Orange", ignoreCase = true)
        .replace("tgt", "Orange", ignoreCase = true)
        .ifBlank { "Unknown" }

private fun statusColor(statusValue: String?): Color {
    val status = statusValue.orEmpty().lowercase()
    return when {
        status.contains("completed") || status.contains("complete") || status.contains("confirmed") || status.contains("success") || status.contains("paid") -> Color(0xFF15803D)
        status.contains("pending") || status.contains("processing") || status.contains("waiting") -> Color(0xFFD97706)
        status.contains("failed") || status.contains("failure") || status.contains("cancel") || status.contains("refund") || status.contains("error") -> Color(0xFFDC2626)
        else -> Color(0xFF6B7280)
    }
}

private enum class TransactionFilter(val label: String) {
    ALL("All"),
    PENDING("Pending"),
    COMPLETED("Completed"),
    FAILED("Failed");

    fun matches(statusValue: String?): Boolean {
        val status = statusValue.orEmpty().lowercase()
        return when (this) {
            ALL -> true
            PENDING -> status.contains("pending") || status.contains("processing") || status.contains("waiting")
            COMPLETED -> status.contains("completed") || status.contains("complete") || status.contains("confirmed") || status.contains("success") || status.contains("paid")
            FAILED -> status.contains("failed") || status.contains("failure") || status.contains("cancel") || status.contains("refund") || status.contains("error")
        }
    }
}
