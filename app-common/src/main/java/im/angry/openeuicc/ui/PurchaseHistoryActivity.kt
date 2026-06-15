package im.angry.openeuicc.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment

class PurchaseHistoryActivity : ComponentActivity() {
    private val tokenStore by lazy { AuthTokenStore(this) }
    private val authApi by lazy { Roam2WorldAuthApi(BuildConfig.ROAM2WORLD_API_BASE_URL) }

    private var ordersState by mutableStateOf<List<MobileOrder>>(emptyList())
    private var loadingState by mutableStateOf(false)
    private var errorState by mutableStateOf<String?>(null)
    private var initialDateFilter: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initialDateFilter = intent.getStringExtra("order_date_filter")

        setContent {
            PurchaseHistoryScreen(
                orders = ordersState,
                loading = loadingState,
                error = errorState,
                initialDateFilter = initialDateFilter,
                onRefresh = { loadOrders() },
                onOpenOrder = { order ->
                    startActivity(MobileOrderDetailActivity.createIntent(this, order))
                },
                onOpenHome = {
                    startActivity(
                        Intent(this, R2wComposeHomeActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                    )
                },
                onOpenStore = {
                    startActivity(
                        Intent(this, R2wStoreActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                    )
                },
                onOpenEsims = {
                    startActivity(
                        Intent(this, MobileEsimsActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                    )
                }
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
                .onSuccess {
                    ordersState = it.orders
                }
                .onFailure {
                    errorState = it.message ?: "Siparişler yüklenemedi"
                }

            loadingState = false
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
private fun PurchaseHistoryScreen(
    orders: List<MobileOrder>,
    loading: Boolean,
    error: String?,
    initialDateFilter: String?,
    onRefresh: () -> Unit,
    onOpenOrder: (MobileOrder) -> Unit,
    onOpenHome: () -> Unit,
    onOpenStore: () -> Unit,
    onOpenEsims: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf(OrderFilter.ALL) }
    var dateFilter by remember { mutableStateOf(initialDateFilter?.uppercase()) }

    LaunchedEffect(initialDateFilter) {
        dateFilter = initialDateFilter?.uppercase()
    }

    val filteredOrders by remember(orders, query, statusFilter, dateFilter) {
        derivedStateOf {
            orders
                .filter { statusFilter.matches(it.status) }
                .filter { matchesDateFilter(it, dateFilter) }
                .filter { order ->
                    val q = query.trim().lowercase()
                    q.isBlank() || order.searchBlob().contains(q)
                }
        }
    }

    val bg = Color(0xFFF7F7FA)
    val orange = Color(0xFFFF6A00)
    val scroll = rememberScrollState()

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = bg) {
            Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scroll)
                    .padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 116.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(30.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF17181C)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(22.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Siparişlerim",
                            color = orange,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = "${filteredOrders.size} / ${orders.size} sipariş",
                            color = Color.White,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = "Satın aldığın paketleri, durumlarını ve eSIM bilgilerini buradan takip et.",
                            color = Color.White.copy(alpha = 0.74f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onOpenHome,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Text("Ana Sayfa")
                    }

                    OutlinedButton(
                        onClick = onOpenEsims,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Text("eSIM’lerim")
                    }
                }

                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Sipariş, paket, müşteri veya ICCID ara") },
                    shape = RoundedCornerShape(18.dp)
                )

                FilterRows(
                    statusFilter = statusFilter,
                    onStatusFilter = { statusFilter = it },
                    dateFilter = dateFilter,
                    onDateFilter = { dateFilter = it }
                )

                if (loading) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Row(
                            modifier = Modifier.padding(20.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator()
                            Text("Siparişler yükleniyor...")
                        }
                    }
                }

                if (!error.isNullOrBlank()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEAEA))
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Siparişler yüklenemedi",
                                color = Color(0xFFB91C1C),
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = error,
                                color = Color(0xFF7F1D1D)
                            )
                            Button(
                                onClick = onRefresh,
                                colors = ButtonDefaults.buttonColors(containerColor = orange),
                                shape = RoundedCornerShape(18.dp)
                            ) {
                                Text("Tekrar Dene")
                            }
                        }
                    }
                }

                if (!loading && error.isNullOrBlank() && filteredOrders.isEmpty()) {
                    EmptyOrdersCard(onOpenStore = onOpenStore)
                }

                filteredOrders.forEach { order ->
                    OrderCard(
                        order = order,
                        onClick = { onOpenOrder(order) }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
            }
        
                R2wBottomNav(
                    selected = R2wBottomTab.More,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }
    }
}

@Composable
private fun FilterRows(
    statusFilter: OrderFilter,
    onStatusFilter: (OrderFilter) -> Unit,
    dateFilter: String?,
    onDateFilter: (String?) -> Unit
) {
    val hScroll = rememberScrollState()
    val hScroll2 = rememberScrollState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(hScroll),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OrderFilter.entries.forEach { filter ->
            FilterChip(
                selected = statusFilter == filter,
                onClick = { onStatusFilter(filter) },
                label = { Text(filter.label) }
            )
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(hScroll2),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = dateFilter == null,
            onClick = { onDateFilter(null) },
            label = { Text("Tüm Tarihler") }
        )
        FilterChip(
            selected = dateFilter == "TODAY",
            onClick = { onDateFilter("TODAY") },
            label = { Text("Bugün") }
        )
        FilterChip(
            selected = dateFilter == "MONTH",
            onClick = { onDateFilter("MONTH") },
            label = { Text("Bu Ay") }
        )
    }
}

@Composable
private fun OrderCard(
    order: MobileOrder,
    onClick: () -> Unit
) {
    val displayNumber = order.displayNumber()?.takeIf { it.isNotBlank() }
        ?: order.orderNumber
        ?: order.id
        ?: "Sipariş"

    val packageName = PackageNameCleaner.clean(order.packageName)
        .orEmpty()
        .ifBlank { order.packageName ?: "Paket" }

    val customerLine = listOfNotNull(
        order.customerName()?.takeIf { it.isNotBlank() },
        order.customerPhone?.takeIf { it.isNotBlank() },
        order.customerEmail?.takeIf { it.isNotBlank() }
    ).joinToString(" • ").ifBlank { "Müşteri bilgisi yok" }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = displayNumber,
                        color = Color(0xFF17181C),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text = formatOrderDate(order.createdAt),
                        color = Color(0xFF7A7D86),
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                StatusBadge(order.statusLabel(), order.status)
            }

            Text(
                text = packageName,
                color = Color(0xFF17181C),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                text = customerLine,
                color = Color(0xFF686B73),
                style = MaterialTheme.typography.bodySmall
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = providerDisplayName(order.provider).ifBlank { "Provider" },
                    color = Color(0xFF686B73),
                    style = MaterialTheme.typography.bodyMedium
                )

                Text(
                    text = formatOrderPrice(order.price.orEmpty()),
                    color = Color(0xFFFF6A00),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            order.esim?.iccid?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = "ICCID: $it",
                    color = Color(0xFF686B73),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun StatusBadge(label: String?, rawStatus: String?) {
    val display = normalizedStatusLabel(label, rawStatus)
    val normalized = listOfNotNull(rawStatus, display).joinToString(" ").lowercase()

    val colors = when {
        isFailedStatus(normalized) -> Color(0xFFFEE2E2) to Color(0xFFB91C1C)
        isCompletedStatus(normalized) -> Color(0xFFDCFCE7) to Color(0xFF166534)
        else -> Color(0xFFFEF9C3) to Color(0xFF854D0E)
    }

    Text(
        text = display,
        color = colors.second,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .background(colors.first, RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    )
}

@Composable
private fun EmptyOrdersCard(onOpenStore: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Henüz sipariş yok",
                color = Color(0xFF17181C),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Paket satın aldığında siparişlerin burada görünecek.",
                color = Color(0xFF686B73)
            )
            Button(
                onClick = onOpenStore,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6A00)),
                shape = RoundedCornerShape(18.dp)
            ) {
                Text("Paketlere Git")
            }
        }
    }
}

private fun MobileOrder.searchBlob(): String =
    listOfNotNull(
        id,
        orderNumber,
        displayNumber(),
        packageName,
        PackageNameCleaner.clean(packageName),
        price,
        status,
        statusLabel(),
        provider,
        providerDisplayName(provider),
        createdAt,
        customerName(),
        customerPhone,
        customerEmail,
        esim?.customerName(),
        esim?.customerPhone,
        esim?.customerEmail,
        esim?.iccid
    ).joinToString(" ").lowercase()

private fun matchesDateFilter(order: MobileOrder, dateFilter: String?): Boolean {
    val filterValue = dateFilter?.uppercase() ?: return true
    val created = parseOrderDate(order.createdAt) ?: return false
    val today = LocalDate.now()

    return when (filterValue) {
        "TODAY" -> created.toLocalDate() == today
        "MONTH" -> {
            val d = created.toLocalDate()
            d.year == today.year && d.month == today.month
        }
        else -> true
    }
}

private fun parseOrderDate(value: String?): OffsetDateTime? {
    if (value.isNullOrBlank()) return null
    return runCatching { OffsetDateTime.parse(value) }.getOrNull()
}

private fun formatOrderDate(value: String?): String {
    val raw = value?.takeIf { it.isNotBlank() } ?: return ""
    val parsed = runCatching { OffsetDateTime.parse(raw) }.getOrNull() ?: return raw
    return parsed.format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm", Locale.ENGLISH))
}

private fun formatOrderPrice(value: String): String {
    val clean = value.trim()
    if (clean.isBlank()) return "$0"
    if (clean.startsWith("$") || clean.startsWith("€") || clean.startsWith("£")) return clean
    if (clean.startsWith("USD", ignoreCase = true)) return clean
    return "$$clean"
}

private fun providerDisplayName(provider: String?): String {
    val p = provider.orEmpty().trim()
    return when {
        p.isBlank() -> ""
        p.equals("tgt", ignoreCase = true) -> "Orange"
        p.contains("orange", ignoreCase = true) -> "Orange"
        p.contains("esimcard", ignoreCase = true) -> "Orange"
        p.contains("airhubapp", ignoreCase = true) -> "Vodafone"
        p.contains("vodafone", ignoreCase = true) -> "Vodafone"
        p.contains("airalo", ignoreCase = true) -> "Airalo"
        else -> p
    }
}

private fun normalizedStatusLabel(label: String?, rawStatus: String?): String {
    val display = label?.takeIf { it.isNotBlank() } ?: rawStatus.orEmpty()
    val normalized = listOfNotNull(rawStatus, display).joinToString(" ").lowercase()

    return when {
        isFailedStatus(normalized) -> "Cancelled"
        isCompletedStatus(normalized) && normalized.contains("confirm") -> "Active"
        isCompletedStatus(normalized) -> "Completed"
        else -> "Pending"
    }
}

private fun isCompletedStatus(status: String): Boolean =
    status.contains("complete") ||
        status.contains("completed") ||
        status.contains("confirmed") ||
        status.contains("confirm") ||
        status.contains("success") ||
        status.contains("succeeded") ||
        status.contains("installed") ||
        status.contains("active") ||
        status.contains("paid")

private fun isFailedStatus(status: String): Boolean =
    status.contains("fail") ||
        status.contains("failed") ||
        status.contains("cancel") ||
        status.contains("cancelled") ||
        status.contains("error") ||
        status.contains("rejected") ||
        status.contains("refund")

private enum class OrderFilter(val label: String) {
    ALL("Tümü"),
    PENDING("Bekleyen"),
    COMPLETED("Tamamlanan"),
    FAILED("İptal/Hata");

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
