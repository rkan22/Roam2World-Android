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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
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

private val ReportsBlue = Color(0xFF0F6BFF)
private val ReportsBg = Color(0xFFF8FAFD)
private val ReportsText = Color(0xFF20242C)
private val ReportsMuted = Color(0xFF68707C)
private val ReportsBorder = Color(0xFFE1E6EF)
private val ReportsGreen = Color(0xFF12813A)
private val ReportsRed = Color(0xFFB42336)

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
        Toast.makeText(this, if (filteredOrders().isEmpty()) "No report data to export" else "Report data ready", Toast.LENGTH_SHORT).show()
    }

    private fun buildReportSummary(): ReportSummary {
        val orders = filteredOrders()
        val revenue = orders.mapNotNull { amount(it.price) }.fold(BigDecimal.ZERO, BigDecimal::add)
        val profit = revenue.multiply(BigDecimal("0.22"))
        val completed = orders.count { it.isCompletedOrder() }.takeIf { it > 0 } ?: orders.size
        val failed = orders.count { it.isFailedOrder() }
        val providers = orders.groupingBy { providerDisplayName(it.provider) }.eachCount().toList().sortedByDescending { it.second }
        val dealers = loadedDealers.take(5).map { "${it.name} • Orders: ${it.totalOrders} • Balance: ${it.currentBalance}" }
        return ReportSummary(
            revenue = r2wMoney(currencyNumber(revenue), ""),
            sales = completed.toString(),
            profit = r2wMoney(currencyNumber(profit), ""),
            activeEsims = loadedActiveEsims ?: "--",
            providerUsage = providers,
            dealerPerformance = dealers,
            failedOrders = failed,
            profitOverview = listOf(
                "Gross revenue: ${r2wMoney(currencyNumber(revenue), "")}",
                "Net profit est.: ${r2wMoney(currencyNumber(profit), "")}",
                "Profit margin est.: 22%",
                "Wallet balance: ${loadedWalletBalance?.let { r2wMoney(it) } ?: "--"}"
            )
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
    val revenue: String,
    val sales: String,
    val profit: String,
    val activeEsims: String,
    val providerUsage: List<Pair<String, Int>>,
    val dealerPerformance: List<String>,
    val failedOrders: Int,
    val profitOverview: List<String>
)

@Composable
private fun ReportsScreen(summary: ReportSummary, range: ReportRange, loading: Boolean, error: String?, onBack: () -> Unit, onRefresh: () -> Unit, onRange: (ReportRange) -> Unit, onExport: () -> Unit) {
    MaterialTheme {
        Surface(Modifier.fillMaxSize(), color = ReportsBg) {
            Box(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize().padding(start = 18.dp, top = 14.dp, end = 18.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.ArrowBack, null, tint = Color.White, modifier = Modifier.size(28.dp).clip(CircleShape).background(ReportsBlue).padding(4.dp).clickable(onClick = onBack))
                        Text("Reports", color = ReportsText, fontSize = 29.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(start = 18.dp).weight(1f))
                        if (loading) CircularProgressIndicator(modifier = Modifier.size(21.dp), color = ReportsBlue, strokeWidth = 2.dp)
                        Icon(Icons.Default.Refresh, null, tint = ReportsBlue, modifier = Modifier.padding(start = 10.dp).size(27.dp).clickable(onClick = onRefresh))
                    }

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FilterBox("Date", range.label, Modifier.weight(1f))
                        FilterBox("Provider", "All", Modifier.weight(1f))
                    }

                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ReportRange.values().forEach { item -> RangeChip(item.label, range == item) { onRange(item) } }
                    }

                    error?.let { ErrorCard(it) }

                    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f)) {
                        item {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                MetricCard("Revenue", summary.revenue, Modifier.weight(1f))
                                MetricCard("Sales", summary.sales, Modifier.weight(1f))
                            }
                        }
                        item { ReportSection("Provider Usage Breakdown") { ProviderUsage(summary.providerUsage) } }
                        item { ReportSection("Dealer Performance") { DealerPerformance(summary.dealerPerformance) } }
                        item { ReportSection("Failed Orders") { SimpleLine("${summary.failedOrders} failed orders") } }
                        item { ReportSection("Profit Overview") { summary.profitOverview.forEach { SimpleLine(it) } } }
                        item { Button(onClick = onExport, modifier = Modifier.fillMaxWidth().height(50.dp), colors = ButtonDefaults.buttonColors(containerColor = ReportsBlue), shape = RoundedCornerShape(14.dp)) { Icon(Icons.Default.Download, null, tint = Color.White, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("Export Report", color = Color.White, fontWeight = FontWeight.ExtraBold) } }
                    }
                }
                R2wBottomNav(selected = R2wBottomTab.More, modifier = Modifier.align(Alignment.BottomCenter))
            }
        }
    }
}

@Composable
private fun FilterBox(title: String, value: String, modifier: Modifier) {
    Card(modifier.height(54.dp), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(Color.White), border = BorderStroke(1.dp, ReportsBorder)) {
        Column(Modifier.padding(horizontal = 13.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = ReportsMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text(value, color = ReportsText, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun RangeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, shape = RoundedCornerShape(50), modifier = Modifier.height(34.dp), colors = ButtonDefaults.outlinedButtonColors(containerColor = if (selected) ReportsBlue else Color.White), border = BorderStroke(1.dp, if (selected) ReportsBlue else ReportsBorder)) {
        Text(label, color = if (selected) Color.White else ReportsMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun MetricCard(title: String, value: String, modifier: Modifier) {
    Card(modifier.height(104.dp), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(Color.White), border = BorderStroke(1.dp, ReportsBorder), elevation = CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(34.dp).clip(CircleShape).background(Color(0xFFEAF0FF)), contentAlignment = Alignment.Center) { Icon(Icons.Default.TrendingUp, null, tint = ReportsBlue, modifier = Modifier.size(18.dp)) }
            Text(value, color = ReportsText, fontSize = 19.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(title, color = ReportsMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ReportSection(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(Color.White), border = BorderStroke(1.dp, ReportsBorder), elevation = CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text(title, color = ReportsText, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            HorizontalDivider(color = ReportsBorder)
            content()
        }
    }
}

@Composable
private fun ProviderUsage(providers: List<Pair<String, Int>>) {
    if (providers.isEmpty()) {
        SimpleLine("No provider usage yet")
    } else {
        providers.take(5).forEach { (provider, count) -> SimpleLine("$provider • $count orders") }
    }
}

@Composable
private fun DealerPerformance(lines: List<String>) {
    if (lines.isEmpty()) {
        SimpleLine("No dealer performance yet")
    } else {
        lines.forEach { SimpleLine(it) }
    }
}

@Composable
private fun SimpleLine(text: String) {
    Text(text, color = ReportsMuted, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun ErrorCard(message: String) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(Color.White), border = BorderStroke(1.dp, ReportsRed.copy(alpha = .25f))) {
        Text(message, color = ReportsRed, fontSize = 13.sp, modifier = Modifier.padding(14.dp))
    }
}

private fun MobileOrder.isCompletedOrder(): Boolean = statusLabel()?.lowercase()?.let { isCompletedStatus(it) } == true || status.orEmpty().lowercase().let { isCompletedStatus(it) }
private fun MobileOrder.isFailedOrder(): Boolean = status.orEmpty().lowercase().let { it.contains("fail") || it.contains("cancel") || it.contains("refund") || it.contains("error") }
private fun isCompletedStatus(value: String): Boolean = value.contains("complete") || value.contains("confirm") || value.contains("success") || value.contains("paid") || value.contains("active")
private fun amount(value: String?): BigDecimal? = value?.trim()?.replace(",", ".")?.replace(Regex("[^0-9.-]"), "")?.takeIf { it.isNotBlank() }?.toBigDecimalOrNull()
private fun currencyNumber(value: BigDecimal): String = value.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString()
private fun providerDisplayName(provider: String?): String { val p = provider.orEmpty().trim(); return when { p.isBlank() -> "Unknown"; p.equals("tgt", true) -> "Orange"; p.contains("travroam", true) -> "Roam2World"; p.contains("airhubapp", true) -> "Vodafone"; p.contains("vodafone", true) -> "Vodafone"; p.contains("orange", true) -> "Orange"; else -> p.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() } } }
