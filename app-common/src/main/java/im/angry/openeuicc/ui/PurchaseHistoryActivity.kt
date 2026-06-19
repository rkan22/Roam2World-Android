package im.angry.openeuicc.ui

import android.content.Intent
import android.graphics.Color as AndroidColor
import android.os.Bundle
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import im.angry.openeuicc.auth.MobileOrder
import im.angry.openeuicc.auth.Roam2WorldAuthApi
import im.angry.openeuicc.common.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val OrdersBlue = Color(0xFF0F4FD7)
private val OrdersText = Color(0xFF20242C)
private val OrdersMuted = Color(0xFF68707C)
private val OrdersBg = Color(0xFFF8FAFD)
private val OrdersBorder = Color(0xFFE1E6EF)
private val OrdersGreen = Color(0xFF176C3A)
private val OrdersGreenBg = Color(0xFFE9F7EF)
private val OrdersRed = Color(0xFFB42336)
private val OrdersRedBg = Color(0xFFFFEEF2)
private val OrdersYellow = Color(0xFFB7791F)
private val OrdersYellowBg = Color(0xFFFFF8E6)
private val OrdersGray = Color(0xFF6B7280)
private val OrdersGrayBg = Color(0xFFF0F2F5)

class PurchaseHistoryActivity : ComponentActivity() {
    private val tokenStore by lazy { AuthTokenStore(this) }
    private val authApi by lazy { Roam2WorldAuthApi(BuildConfig.ROAM2WORLD_API_BASE_URL) }

    private var ordersState by mutableStateOf<List<MobileOrder>>(emptyList())
    private var loadingState by mutableStateOf(false)
    private var errorState by mutableStateOf<String?>(null)
    private var initialDateFilter: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        actionBar?.hide()
        window.statusBarColor = AndroidColor.rgb(248, 250, 253)
        window.navigationBarColor = AndroidColor.BLACK
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true
        initialDateFilter = intent.getStringExtra("order_date_filter")

        setContent {
            OrdersScreen(
                orders = ordersState,
                loading = loadingState,
                error = errorState,
                initialDateFilter = initialDateFilter,
                onRefresh = { loadOrders() },
                onOpenOrder = { startActivity(MobileOrderDetailActivity.createIntent(this, it)) },
                onOpenStore = { startActivity(Intent(this, R2wStoreActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)) },
                onBack = { finish() }
            )
        }
        loadOrders()
    }

    private fun loadOrders() {
        lifecycleScope.launch {
            loadingState = true
            errorState = null
            val session = activeSessionOrReturnToLogin()
            if (session == null) {
                loadingState = false
                return@launch
            }
            runCatching { authApi.orders(session) }
                .onSuccess { ordersState = it.orders }
                .onFailure { errorState = it.message ?: "Orders could not be loaded" }
            loadingState = false
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
private fun OrdersScreen(
    orders: List<MobileOrder>,
    loading: Boolean,
    error: String?,
    initialDateFilter: String?,
    onRefresh: () -> Unit,
    onOpenOrder: (MobileOrder) -> Unit,
    onOpenStore: () -> Unit,
    onBack: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf(OrderFilter.ALL) }
    var dateFilter by remember { mutableStateOf(initialDateFilter?.uppercase(Locale.ROOT)) }

    LaunchedEffect(initialDateFilter) { dateFilter = initialDateFilter?.uppercase(Locale.ROOT) }

    val filteredOrders by remember(orders, query, statusFilter, dateFilter) {
        derivedStateOf {
            orders
                .filter { statusFilter.matches(it.status) }
                .filter { matchesDateFilter(it, dateFilter) }
                .filter { order ->
                    val q = query.trim().lowercase(Locale.ROOT)
                    q.isBlank() || order.searchBlob().contains(q)
                }
        }
    }

    MaterialTheme {
        Surface(Modifier.fillMaxSize(), color = OrdersBg) {
            Box(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize().padding(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.ArrowBack, null, tint = OrdersText, modifier = Modifier.size(30.dp).clickable(onClick = onBack))
                        Text("Orders", color = OrdersText, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(start = 18.dp).weight(1f))
                        if (loading) CircularProgressIndicator(modifier = Modifier.size(22.dp), color = OrdersBlue, strokeWidth = 2.dp)
                        Icon(
                            Icons.Default.FilterList,
                            null,
                            tint = OrdersBlue,
                            modifier = Modifier
                                .padding(start = 12.dp)
                                .size(26.dp)
                                .clickable {
                                    statusFilter = OrderFilter.ALL
                                    dateFilter = null
                                    query = ""
                                }
                        )
                    }

                    OrderSummaryCard(total = orders.size, shown = filteredOrders.size, completed = orders.count { it.isCompleted() })

                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            modifier = Modifier.weight(1f).height(58.dp),
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Default.Search, null, tint = OrdersText, modifier = Modifier.size(24.dp)) },
                            placeholder = { Text("Search order, customer, ICCID...", color = OrdersMuted, fontSize = 13.sp) },
                            shape = RoundedCornerShape(10.dp)
                        )
                    }

                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OrderFilter.values().forEach { filter -> StatusFilterChip(filter.label, statusFilter == filter) { statusFilter = filter } }
                        DateChip("All Dates", dateFilter == null) { dateFilter = null }
                        DateChip("Today", dateFilter == "TODAY") { dateFilter = "TODAY" }
                        DateChip("This Month", dateFilter == "MONTH") { dateFilter = "MONTH" }
                    }

                    error?.let { ErrorCard(it, onRefresh) }

                    if (!loading && error.isNullOrBlank() && filteredOrders.isEmpty()) {
                        EmptyOrdersCard(onOpenStore)
                    }

                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
                        items(filteredOrders) { order -> OrderCard(order = order, onClick = { onOpenOrder(order) }) }
                    }
                }
                R2wBottomNav(selected = R2wBottomTab.More, modifier = Modifier.align(Alignment.BottomCenter))
            }
        }
    }
}

@Composable
private fun OrderSummaryCard(total: Int, shown: Int, completed: Int) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(OrdersBlue), elevation = CardDefaults.cardElevation(2.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(CircleShape).background(Color.White.copy(alpha = .16f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.ReceiptLong, null, tint = Color.White, modifier = Modifier.size(26.dp))
            }
            Column(Modifier.padding(start = 14.dp).weight(1f)) {
                Text("Order History", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                Text("$shown shown from $total orders", color = Color.White.copy(alpha = .78f), fontSize = 13.sp)
            }
            Text("$completed done", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun StatusFilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, shape = RoundedCornerShape(50), modifier = Modifier.height(36.dp), colors = ButtonDefaults.outlinedButtonColors(containerColor = if (selected) OrdersBlue else Color.White), border = BorderStroke(1.dp, if (selected) OrdersBlue else OrdersBorder)) {
        Text(label, color = if (selected) Color.White else OrdersMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun DateChip(label: String, selected: Boolean, onClick: () -> Unit) = StatusFilterChip(label, selected, onClick)

@Composable
private fun OrderCard(order: MobileOrder, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(Color.White), border = BorderStroke(1.dp, OrdersBorder), elevation = CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(Color(0xFFEAF0FF)), contentAlignment = Alignment.Center) {
                    Text(order.initials(), color = OrdersBlue, fontWeight = FontWeight.Black, fontSize = 16.sp)
                }
                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                    Text(order.displayCustomerName(), color = OrdersText, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(order.displayNumber(), color = OrdersMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                StatusBadge(order.statusLabel(), order.status)
            }

            OrderLine(Icons.Default.Inventory2, "Package", order.displayPackageName())
            OrderLine(Icons.Default.Business, "Provider", order.displayProviderName())
            OrderLine(Icons.Default.Person, "Customer", order.displayCustomerSubtitle())
            order.esim?.iccid?.takeIf { it.isNotBlank() }?.let { OrderLine(Icons.Default.SimCard, "ICCID", it.shortIccid()) }
            OrderLine(Icons.Default.CalendarMonth, "Date", formatOrderDate(order.createdAt))

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(formatOrderPrice(order.price.orEmpty()), color = OrdersBlue, fontSize = 18.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = onClick, shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, OrdersBlue), modifier = Modifier.height(38.dp)) {
                    Text("View Detail", color = OrdersBlue, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun OrderLine(icon: ImageVector, label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = OrdersMuted, modifier = Modifier.size(16.dp))
        Text(label, color = OrdersMuted, fontSize = 13.sp, modifier = Modifier.padding(start = 10.dp).width(78.dp))
        Text(value, color = OrdersText, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun StatusBadge(label: String?, rawStatus: String?) {
    val display = normalizedStatusLabel(label, rawStatus)
    val normalized = listOfNotNull(rawStatus, display).joinToString(" ").lowercase(Locale.ROOT)
    val pair = when {
        isFailedStatus(normalized) -> OrdersRedBg to OrdersRed
        isCompletedStatus(normalized) -> OrdersGreenBg to OrdersGreen
        else -> OrdersYellowBg to OrdersYellow
    }
    Text(display, color = pair.second, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.background(pair.first, RoundedCornerShape(999.dp)).padding(horizontal = 9.dp, vertical = 5.dp), maxLines = 1)
}

@Composable
private fun EmptyOrdersCard(onOpenStore: () -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(Color.White), border = BorderStroke(1.dp, OrdersBorder)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("No orders yet", color = OrdersText, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            Text("Purchased packages will appear here.", color = OrdersMuted, fontSize = 14.sp)
            Button(onClick = onOpenStore, colors = ButtonDefaults.buttonColors(containerColor = OrdersBlue), shape = RoundedCornerShape(12.dp)) { Text("Open Store") }
        }
    }
}

@Composable
private fun ErrorCard(error: String, onRefresh: () -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(Color.White), border = BorderStroke(1.dp, OrdersRed.copy(alpha = .25f))) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Orders could not be loaded", color = OrdersRed, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
            Text(error, color = OrdersMuted, fontSize = 13.sp)
            Button(onClick = onRefresh, colors = ButtonDefaults.buttonColors(containerColor = OrdersBlue), shape = RoundedCornerShape(12.dp)) { Text("Retry") }
        }
    }
}

private fun MobileOrder.searchBlob(): String = listOfNotNull(id, orderNumber, displayNumber(), packageName, displayPackageName(), price, status, statusLabel(), provider, displayProviderName(), createdAt, customerName(), customerPhone, customerEmail, esim?.customerName(), esim?.customerPhone, esim?.customerEmail, esim?.iccid).joinToString(" ").lowercase(Locale.ROOT)

private fun matchesDateFilter(order: MobileOrder, dateFilter: String?): Boolean {
    val filterValue = dateFilter?.uppercase(Locale.ROOT) ?: return true
    val created = parseOrderDate(order.createdAt) ?: return false
    val today = LocalDate.now()
    return when (filterValue) {
        "TODAY" -> created.toLocalDate() == today
        "MONTH" -> created.toLocalDate().let { it.year == today.year && it.month == today.month }
        else -> true
    }
}

private fun parseOrderDate(value: String?): OffsetDateTime? {
    val raw = value?.trim()?.takeIf { it.isNotBlank() } ?: return null
    runCatching { return OffsetDateTime.parse(raw) }
    runCatching { return LocalDateTime.parse(raw).atOffset(ZoneOffset.UTC) }
    runCatching { return LocalDate.parse(raw).atStartOfDay().atOffset(ZoneOffset.UTC) }

    val numeric = raw.toLongOrNull()
    if (numeric != null) {
        val epochMillis = if (raw.length <= 10) numeric * 1000 else numeric
        runCatching { return java.time.Instant.ofEpochMilli(epochMillis).atOffset(ZoneOffset.UTC) }
    }

    val dateTimePatterns = listOf(
        "yyyy-MM-dd HH:mm:ss",
        "yyyy-MM-dd HH:mm",
        "yyyy/MM/dd HH:mm:ss",
        "yyyy/MM/dd HH:mm"
    )
    dateTimePatterns.forEach { pattern ->
        runCatching { return LocalDateTime.parse(raw, DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH)).atOffset(ZoneOffset.UTC) }
    }

    val datePatterns = listOf("yyyy-MM-dd", "yyyy/MM/dd", "MMM d, yyyy", "MMMM d, yyyy")
    datePatterns.forEach { pattern ->
        runCatching { return LocalDate.parse(raw, DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH)).atStartOfDay().atOffset(ZoneOffset.UTC) }
    }

    return null
}

private fun formatOrderDate(value: String?): String { val raw = value?.takeIf { it.isNotBlank() } ?: return "--"; val parsed = parseOrderDate(raw) ?: return raw.take(12); return parsed.format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH)) }
private fun formatOrderPrice(value: String): String { val clean = value.trim(); if (clean.isBlank()) return "$0"; if (clean.startsWith("$") || clean.startsWith("€") || clean.startsWith("£")) return clean; if (clean.startsWith("USD", ignoreCase = true)) return clean; return "$$clean" }
private fun MobileOrder.displayProviderName(): String = providerDisplayName(provider)
private fun providerDisplayName(provider: String?): String { val p = provider.orEmpty().trim(); return when { p.isBlank() -> "--"; p.equals("tgt", true) -> "Orange"; p.contains("travroam", true) -> "Roam2World"; p.contains("airhubapp", true) -> "Vodafone"; p.contains("vodafone", true) -> "Vodafone"; p.contains("orange", true) -> "Orange"; else -> p.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() } } }
private fun MobileOrder.displayPackageName(): String { val clean = packageName.replace("【ESIM】", "", true).replace("[ESIM]", "", true).replace("ESIM", "eSIM", true).replace("  ", " ").trim(' ', '-', '|'); return clean.ifBlank { "Package" } }
private fun MobileOrder.displayCustomerName(): String = customerName() ?: esim?.customerName() ?: customerEmail?.substringBefore('@')?.replace('.', ' ')?.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() } ?: orderNumber?.let { "Order #$it" } ?: "B2B Customer"
private fun MobileOrder.displayCustomerSubtitle(): String = customerPhone?.takeIf { it.isNotBlank() } ?: customerEmail?.takeIf { it.isNotBlank() } ?: esim?.customerPhone?.takeIf { it.isNotBlank() } ?: esim?.customerEmail?.takeIf { it.isNotBlank() } ?: esim?.iccid?.shortIccid() ?: "No customer contact"
private fun MobileOrder.initials(): String = displayCustomerName().split(" ", "#").filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase(Locale.ROOT) }.take(2).ifBlank { "O" }
private fun MobileOrder.isCompleted(): Boolean = isCompletedStatus(status.orEmpty())
private fun String.shortIccid(): String = if (length > 12) "${take(6)}...${takeLast(4)}" else this
private fun normalizedStatusLabel(label: String?, rawStatus: String?): String { val display = label?.takeIf { it.isNotBlank() } ?: rawStatus.orEmpty(); val normalized = listOfNotNull(rawStatus, display).joinToString(" ").lowercase(Locale.ROOT); return when { isFailedStatus(normalized) -> "Cancelled"; isCompletedStatus(normalized) && normalized.contains("confirm") -> "Active"; isCompletedStatus(normalized) -> "Completed"; else -> "Pending" } }
private fun isCompletedStatus(status: String): Boolean = status.contains("complete") || status.contains("completed") || status.contains("confirmed") || status.contains("confirm") || status.contains("success") || status.contains("succeeded") || status.contains("installed") || status.contains("active") || status.contains("paid")
private fun isFailedStatus(status: String): Boolean = status.contains("fail") || status.contains("failed") || status.contains("cancel") || status.contains("cancelled") || status.contains("error") || status.contains("rejected") || status.contains("refund")

private enum class OrderFilter(val label: String) {
    ALL("All"), PENDING("Pending"), COMPLETED("Completed"), FAILED("Cancelled");
    fun matches(statusValue: String?): Boolean { val status = statusValue.orEmpty().lowercase(Locale.ROOT); return when (this) { ALL -> true; PENDING -> status.contains("pending") || status.contains("processing") || status.contains("waiting"); COMPLETED -> isCompletedStatus(status); FAILED -> isFailedStatus(status) } }
}
