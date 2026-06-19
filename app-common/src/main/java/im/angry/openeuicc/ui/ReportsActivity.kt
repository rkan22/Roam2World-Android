package im.angry.openeuicc.ui

import android.content.Intent
import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.filled.TrendingUp
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
import im.angry.openeuicc.auth.MobileDealer
import im.angry.openeuicc.auth.MobileOrder
import im.angry.openeuicc.auth.Roam2WorldAuthApi
import im.angry.openeuicc.common.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ReportsBlue = Color(0xFF0F4FD7)
private val ReportsDark = Color(0xFF06103A)
private val ReportsBg = Color(0xFFF8FAFD)
private val ReportsText = Color(0xFF20242C)
private val ReportsMuted = Color(0xFF68707C)
private val ReportsBorder = Color(0xFFE1E6EF)
private val ReportsGreen = Color(0xFF12813A)
private val ReportsGreenBg = Color(0xFFE9F7EF)
private val ReportsRed = Color(0xFFB42336)
private val ReportsRedBg = Color(0xFFFFEEF2)
private val ReportsYellow = Color(0xFFB7791F)
private val ReportsYellowBg = Color(0xFFFFF8E6)

private enum class ReportRange(val label: String) {
    TODAY("Today"), SEVEN_DAYS("7 days"), THIRTY_DAYS("30 days"), ALL("All")
}

class ReportsActivity : ComponentActivity() {
    private val tokenStore by lazy { AuthTokenStore(this) }
    private val authApi by lazy { Roam2WorldAuthApi(BuildConfig.ROAM2WORLD_API_BASE_URL) }

    private var reportRange by mutableStateOf(ReportRange.THIRTY_DAYS)
    private var loadedOrders by mutableStateOf<List<MobileOrder>>(emptyList())
    private var loadedDealers by mutableStateOf<List<MobileDealer>>(emptyList())
    private var loadedWalletBalance by mutableStateOf<String?>(null)
    private var loadedActiveEsims by mutableStateOf<String?>(null)
    private var loading by mutableStateOf(false)
    private var errorMessage by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        actionBar?.hide()
        window.statusBarColor = AndroidColor.rgb(248, 250, 253)
        window.navigationBarColor = AndroidColor.BLACK
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true

        setContent {
            ReportsScreen(
                summary = buildReportSummary(),
                range = reportRange,
                loading = loading,
                error = errorMessage,
                onBack = { finish() },
                onRefresh = { loadReports() },
                onRange = { reportRange = it },
                onExport = { exportReportNotice() }
            )
        }
        loadReports()
    }

    private fun loadReports() {
        lifecycleScope.launch {
            loading = true
            errorMessage = null
            val session = activeSessionOrReturnToLogin()
            if (session == null) {
                loading = false
                return@launch
            }

            val dashboardRequest = async { runCatching { authApi.dashboard(session) } }
            val ordersRequest = async { runCatching { authApi.orders(session) } }
            val walletRequest = async { runCatching { authApi.wallet(session) } }
            val dealersRequest = async { runCatching { authApi.dealers(session) } }

            val dashboard = dashboardRequest.await().getOrNull()
            val wallet = walletRequest.await().getOrNull()
            val ordersResult = ordersRequest.await()
            val dealersResult = dealersRequest.await()

            loadedOrders = ordersResult.getOrNull()?.orders.orEmpty()
            loadedDealers = dealersResult.getOrNull()?.dealers.orEmpty()
            loadedWalletBalance = wallet?.currentBalance
            loadedActiveEsims = dashboard?.activeEsimCount
            errorMessage = listOfNotNull(
                ordersResult.exceptionOrNull()?.message?.let { "Orders: $it" },
                dealersResult.exceptionOrNull()?.message?.let { "Dealers: $it" }
            ).joinToString("\n").ifBlank { null }
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

    private fun exportReportNotice() {
        if (filteredOrders().isEmpty()) {
            Toast.makeText(this, "No report data to export", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Report data ready", Toast.LENGTH_SHORT).show()
        }
    }

    private fun buildReportSummary(): ReportSummary {
        val orders = filteredOrders()
        val revenue = orders.mapNotNull { amount(it.price) }.fold(BigDecimal.ZERO, BigDecimal::add)
        val profit = revenue.multiply(BigDecimal("0.22"))
        val completed = orders.count { it.isCompletedOrder() }.takeIf { it > 0 } ?: orders.size
        val pending = orders.count { it.isPendingOrder() }
        val failed = orders.count { it.isFailedOrder() }
        val providers = orders.groupingBy { providerDisplayName(it.provider) }.eachCount().toList().sortedByDescending { it.second }

        return ReportSummary(
            status = if (orders.isEmpty()) "No report data for ${reportRange.label}" else "${orders.size} orders • ${loadedDealers.size} dealers • ${reportRange.label}",
            revenue = r2wMoney(currencyNumber(revenue), ""),
            profit = r2wMoney(currencyNumber(profit), ""),
            sales = completed.toString(),
            activeEsims = loadedActiveEsims ?: "--",
            wallet = loadedWalletBalance?.let { r2wMoney(it) } ?: "--",
            pending = pending,
            failed = failed,
            providers = providers,
            recentOrders = orders.take(5),
            dealerCount = loadedDealers.size
        )
    }

    private fun filteredOrders(): List<MobileOrder> {
        if (reportRange == ReportRange.ALL) return loadedOrders
        val now = Instant.now()
        val cutoff = when (reportRange) {
            ReportRange.TODAY -> now.minusSeconds(24 * 60 * 60)
            ReportRange.SEVEN_DAYS -> now.minusSeconds(7 * 24 * 60 * 60)
            ReportRange.THIRTY_DAYS -> now.minusSeconds(30 * 24 * 60 * 60)
            ReportRange.ALL -> Instant.EPOCH
        }
        return loadedOrders.filter { parseReportInstant(it.createdAt)?.isAfter(cutoff) == true }
    }

    private fun parseReportInstant(value: String?): Instant? {
        val raw = value?.trim().orEmpty()
        if (raw.isBlank()) return null
        val normalized = raw.replace(" ", "T").let { if (it.endsWith("Z", true) || it.contains(Regex("[+-]\\d{2}:?\\d{2}$"))) it else "${it}Z" }
        return runCatching { Instant.parse(normalized) }.getOrNull()
    }
}

private data class ReportSummary(
    val status: String,
    val revenue: String,
    val profit: String,
    val sales: String,
    val activeEsims: String,
    val wallet: String,
    val pending: Int,
    val failed: Int,
    val providers: List<Pair<String, Int>>,
    val recentOrders: List<MobileOrder>,
    val dealerCount: Int
)

@Composable
private fun ReportsScreen(summary: ReportSummary, range: ReportRange, loading: Boolean, error: String?, onBack: () -> Unit, onRefresh: () -> Unit, onRange: (ReportRange) -> Unit, onExport: () -> Unit) {
    MaterialTheme {
        Surface(Modifier.fillMaxSize(), color = ReportsBg) {
            Box(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize().padding(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.ArrowBack, null, tint = ReportsText, modifier = Modifier.size(30.dp).clickable(onClick = onBack))
                        Text("Reports", color = ReportsText, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(start = 18.dp).weight(1f))
                        if (loading) CircularProgressIndicator(modifier = Modifier.size(22.dp), color = ReportsBlue, strokeWidth = 2.dp)
                        Icon(Icons.Default.Refresh, null, tint = ReportsBlue, modifier = Modifier.padding(start = 12.dp).size(26.dp).clickable(onClick = onRefresh))
                    }

                    ReportsHero(summary.status, onExport)

                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ReportRange.values().forEach { item -> RangeChip(item.label, range == item) { onRange(item) } }
                    }

                    error?.let { ErrorCard(it) }

                    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f)) {
                        item {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                MetricCard("Revenue", summary.revenue, Icons.Default.TrendingUp, Modifier.weight(1f))
                                MetricCard("Sales", summary.sales, Icons.Default.Assessment, Modifier.weight(1f))
                            }
                        }
                        item {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                MetricCard("Profit", summary.profit, Icons.Default.Assessment, Modifier.weight(1f))
                                MetricCard("Active eSIMs", summary.activeEsims, Icons.Default.SimCard, Modifier.weight(1f))
                            }
                        }
                        item { MetricWideCard("Wallet Balance", summary.wallet, "Current reseller/dealer wallet balance", Icons.Default.AccountBalanceWallet) }
                        item { OrderHealthCard(summary.pending, summary.failed, summary.dealerCount) }
                        item { ProviderUsageCard(summary.providers) }
                        item { RecentOrdersCard(summary.recentOrders) }
                    }
                }
                R2wBottomNav(selected = R2wBottomTab.More, modifier = Modifier.align(Alignment.BottomCenter))
            }
        }
    }
}

@Composable
private fun ReportsHero(status: String, onExport: () -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(ReportsDark), elevation = CardDefaults.cardElevation(3.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(52.dp).clip(CircleShape).background(ReportsBlue), contentAlignment = Alignment.Center) { Icon(Icons.Default.Assessment, null, tint = Color.White, modifier = Modifier.size(28.dp)) }
                Column(Modifier.padding(start = 14.dp).weight(1f)) {
                    Text("Business Intelligence", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                    Text(status, color = Color.White.copy(alpha = .72f), fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            Button(onClick = onExport, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = ReportsBlue)) {
                Icon(Icons.Default.Download, null, tint = Color.White, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Export Report", color = Color.White, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

@Composable
private fun RangeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, shape = RoundedCornerShape(50), modifier = Modifier.height(36.dp), colors = ButtonDefaults.outlinedButtonColors(containerColor = if (selected) ReportsBlue else Color.White), border = BorderStroke(1.dp, if (selected) ReportsBlue else ReportsBorder)) {
        Text(label, color = if (selected) Color.White else ReportsMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun MetricCard(title: String, value: String, icon: ImageVector, modifier: Modifier) {
    Card(modifier, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(Color.White), border = BorderStroke(1.dp, ReportsBorder)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null, tint = ReportsBlue, modifier = Modifier.size(24.dp))
            Text(value, color = ReportsText, fontSize = 21.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(title, color = ReportsMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun MetricWideCard(title: String, value: String, subtitle: String, icon: ImageVector) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(Color.White), border = BorderStroke(1.dp, ReportsBorder)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(Color(0xFFEAF0FF)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = ReportsBlue, modifier = Modifier.size(24.dp)) }
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text(title, color = ReportsMuted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(subtitle, color = ReportsMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(value, color = ReportsText, fontSize = 19.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun OrderHealthCard(pending: Int, failed: Int, dealers: Int) {
    SectionCard("Order Health") {
        HealthRow("Pending orders", pending.toString(), ReportsYellow, ReportsYellowBg)
        HorizontalDivider(color = ReportsBorder)
        HealthRow("Failed / cancelled", failed.toString(), ReportsRed, ReportsRedBg)
        HorizontalDivider(color = ReportsBorder)
        HealthRow("Active dealers", dealers.toString(), ReportsGreen, ReportsGreenBg)
    }
}

@Composable
private fun HealthRow(label: String, value: String, fg: Color, bg: Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = ReportsMuted, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Box(Modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 10.dp, vertical = 5.dp)) { Text(value, color = fg, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun ProviderUsageCard(providers: List<Pair<String, Int>>) {
    SectionCard("Provider Usage") {
        if (providers.isEmpty()) Text("No provider usage yet", color = ReportsMuted, fontSize = 14.sp)
        providers.take(5).forEachIndexed { index, pair ->
            Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Business, null, tint = ReportsBlue, modifier = Modifier.size(18.dp))
                Text(pair.first, color = ReportsText, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 10.dp).weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${pair.second} orders", color = ReportsMuted, fontSize = 13.sp)
            }
            if (index != providers.take(5).lastIndex) HorizontalDivider(color = ReportsBorder)
        }
    }
}

@Composable
private fun RecentOrdersCard(orders: List<MobileOrder>) {
    SectionCard("Recent Orders") {
        if (orders.isEmpty()) Text("No orders in this range", color = ReportsMuted, fontSize = 14.sp)
        orders.forEachIndexed { index, order ->
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(36.dp).clip(CircleShape).background(Color(0xFFEAF0FF)), contentAlignment = Alignment.Center) { Icon(Icons.Default.Inventory2, null, tint = ReportsBlue, modifier = Modifier.size(19.dp)) }
                Column(Modifier.padding(start = 10.dp).weight(1f)) {
                    Text(cleanPackageName(order.packageName), color = ReportsText, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${providerDisplayName(order.provider)} • ${formatReportDate(order.createdAt)}", color = ReportsMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(formatOrderPrice(order.price.orEmpty()), color = ReportsBlue, fontSize = 14.sp, fontWeight = FontWeight.Black)
            }
            if (index != orders.lastIndex) HorizontalDivider(color = ReportsBorder)
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(Color.White), border = BorderStroke(1.dp, ReportsBorder), elevation = CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, color = ReportsText, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
            content()
        }
    }
}

@Composable
private fun ErrorCard(message: String) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(Color.White), border = BorderStroke(1.dp, ReportsRed.copy(alpha = .25f))) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Some reports could not be loaded", color = ReportsRed, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
            Text(message, color = ReportsMuted, fontSize = 13.sp)
        }
    }
}

private fun MobileOrder.isCompletedOrder(): Boolean = statusLabel()?.lowercase()?.let { isCompletedStatus(it) } == true || status.orEmpty().lowercase().let { isCompletedStatus(it) }
private fun MobileOrder.isPendingOrder(): Boolean = status.orEmpty().lowercase().let { it.contains("pending") || it.contains("processing") || it.contains("waiting") }
private fun MobileOrder.isFailedOrder(): Boolean = status.orEmpty().lowercase().let { it.contains("fail") || it.contains("cancel") || it.contains("refund") || it.contains("error") }
private fun isCompletedStatus(value: String): Boolean = value.contains("complete") || value.contains("confirm") || value.contains("success") || value.contains("paid") || value.contains("active")
private fun amount(value: String?): BigDecimal? = value?.trim()?.replace(",", ".")?.replace(Regex("[^0-9.-]"), "")?.takeIf { it.isNotBlank() }?.toBigDecimalOrNull()
private fun currencyNumber(value: BigDecimal): String = value.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString()
private fun parseReportInstant(value: String?): Instant? { val raw = value?.trim().orEmpty(); if (raw.isBlank()) return null; val normalized = raw.replace(" ", "T").let { if (it.endsWith("Z", true) || it.contains(Regex("[+-]\\d{2}:?\\d{2}$"))) it else "${it}Z" }; return runCatching { Instant.parse(normalized) }.getOrNull() }
private fun formatReportDate(value: String?): String = parseReportInstant(value)?.atZone(ZoneId.systemDefault())?.format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH)) ?: "--"
private fun providerDisplayName(provider: String?): String { val p = provider.orEmpty().trim(); return when { p.isBlank() -> "Unknown"; p.equals("tgt", true) -> "Orange"; p.contains("travroam", true) -> "Roam2World"; p.contains("airhubapp", true) -> "Vodafone"; p.contains("vodafone", true) -> "Vodafone"; p.contains("orange", true) -> "Orange"; else -> p.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() } } }
private fun cleanPackageName(value: String): String = value.replace("【ESIM】", "", true).replace("[ESIM]", "", true).replace("ESIM", "eSIM", true).replace("  ", " ").trim(' ', '-', '|').ifBlank { "Package" }
private fun formatOrderPrice(value: String): String { val clean = value.trim(); if (clean.isBlank()) return "$0"; if (clean.startsWith("$") || clean.startsWith("€") || clean.startsWith("£")) return clean; if (clean.startsWith("USD", ignoreCase = true)) return clean; return "$$clean" }
