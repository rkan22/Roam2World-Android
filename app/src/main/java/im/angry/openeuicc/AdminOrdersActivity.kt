package im.angry.openeuicc

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import im.angry.openeuicc.auth.AuthTokenStore
import im.angry.openeuicc.auth.JwtUtils
import im.angry.openeuicc.ui.LoginActivity
import im.angry.openeuicc.ui.compose.saas.R2wMetricCard
import im.angry.openeuicc.ui.compose.saas.R2wSaasBottomNav
import im.angry.openeuicc.ui.compose.saas.R2wSaasCard
import im.angry.openeuicc.ui.compose.saas.R2wSaasColors
import im.angry.openeuicc.ui.compose.saas.R2wSaasHeader
import im.angry.openeuicc.ui.compose.saas.R2wSaasNavItem
import im.angry.openeuicc.ui.compose.theme.R2WTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class AdminOrdersActivity : ComponentActivity() {
    private val tokenStore by lazy { AuthTokenStore(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            var loading by remember { mutableStateOf(true) }
            var errorMessage by remember { mutableStateOf<String?>(null) }
            var orders by remember { mutableStateOf<List<AdminOrderUi>>(emptyList()) }
            var query by remember { mutableStateOf("") }
            var selectedStatus by remember { mutableStateOf("all") }
            val scope = rememberCoroutineScope()

            fun openOrder(order: AdminOrderUi) {
                startActivity(
                    Intent(this@AdminOrdersActivity, AdminOrderDetailActivity::class.java).apply {
                        putExtra(AdminOrderDetailActivity.EXTRA_ORDER_JSON, order.rawJson)
                    }
                )
            }

            suspend fun loadOrders() {
                loading = true
                errorMessage = null

                val session = withContext(Dispatchers.IO) { tokenStore.getSession() }

                if (session == null || JwtUtils.isExpired(session.accessToken)) {
                    redirectToLogin()
                    loading = false
                    return
                }

                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        val response = fetchAdminOrders(session.authorizationHeader)
                        parseOrders(response)
                    }
                }

                result
                    .onSuccess { orders = it }
                    .onFailure {
                        errorMessage = it.message ?: "Orders API error"
                        Toast.makeText(this@AdminOrdersActivity, errorMessage, Toast.LENGTH_LONG).show()
                    }

                loading = false
            }

            LaunchedEffect(Unit) {
                loadOrders()
            }

            R2WTheme {
                AdminOrdersListSaasScreen(
                    loading = loading,
                    errorMessage = errorMessage,
                    orders = orders,
                    query = query,
                    selectedStatus = selectedStatus,
                    onQueryChange = { query = it },
                    onStatusChange = { selectedStatus = it },
                    onRefresh = { scope.launch { loadOrders() } },
                    onOpenOrder = { openOrder(it) },
                    onBottomNavClick = { item ->
                        when (item) {
                            R2wSaasNavItem.Dashboard -> startActivity(Intent(this@AdminOrdersActivity, MobileAdminActivity::class.java))
                            R2wSaasNavItem.Partners -> startActivity(Intent(this@AdminOrdersActivity, AdminPartnersActivity::class.java))
                            R2wSaasNavItem.Orders -> startActivity(Intent(this@AdminOrdersActivity, AdminOrdersOverviewActivity::class.java))
                            R2wSaasNavItem.Pricing -> startActivity(Intent(this@AdminOrdersActivity, AdminPricingOverviewActivity::class.java))
                            R2wSaasNavItem.More -> startActivity(Intent(this@AdminOrdersActivity, AdminMoreActivity::class.java))
                        }
                    }
                )
            }
        }
    }

    private fun fetchAdminOrders(authorizationHeader: String): JSONObject {
        val url = URL("https://roam2world-panels-backend.onrender.com/api/v1/mobile/admin/orders/")
        val connection = url.openConnection() as HttpURLConnection

        return try {
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Authorization", authorizationHeader)
            connection.connectTimeout = 15000
            connection.readTimeout = 15000

            val stream = if (connection.responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }

            val body = stream.bufferedReader().use { it.readText() }

            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("HTTP ${connection.responseCode}: $body")
            }

            JSONObject(body)
        } finally {
            connection.disconnect()
        }
    }

    private fun parseOrders(response: JSONObject): List<AdminOrderUi> {
        val data = response.optJSONObject("data") ?: response
        val arr = data.optJSONArray("orders") ?: JSONArray()
        val list = mutableListOf<AdminOrderUi>()

        for (i in 0 until arr.length()) {
            val order = arr.optJSONObject(i) ?: continue
            list.add(
                AdminOrderUi(
                    rawJson = order.toString(),
                    id = order.optString("id", order.optString("order_id", "")),
                    orderNumber = order.optString("order_number", "-"),
                    status = order.optString("status", "-"),
                    source = order.optString("order_source", "-"),
                    type = order.optString("order_type", "-"),
                    product = order.optString("product_name", "-"),
                    quantity = order.optInt("quantity", 0).toString(),
                    total = order.optString("total_amount", "0.00"),
                    customer = order.optString("customer_name", "-"),
                    name = order.optString("name", "-"),
                    email = order.optString("email", "-"),
                    resellerEmail = order.optString("reseller_email", ""),
                    dealerEmail = order.optString("dealer_email", ""),
                    country = order.optString("delivery_country", "")
                )
            )
        }

        return list
    }

    private fun redirectToLogin() {
        startActivity(
            Intent(this, LoginActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
        )
        finish()
    }
}

private data class AdminOrderUi(
    val rawJson: String,
    val id: String,
    val orderNumber: String,
    val status: String,
    val source: String,
    val type: String,
    val product: String,
    val quantity: String,
    val total: String,
    val customer: String,
    val name: String,
    val email: String,
    val resellerEmail: String,
    val dealerEmail: String,
    val country: String
)

@Composable
private fun AdminOrdersListSaasScreen(
    loading: Boolean,
    errorMessage: String?,
    orders: List<AdminOrderUi>,
    query: String,
    selectedStatus: String,
    onQueryChange: (String) -> Unit,
    onStatusChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onOpenOrder: (AdminOrderUi) -> Unit,
    onBottomNavClick: (R2wSaasNavItem) -> Unit
) {
    val filtered by remember(orders, query, selectedStatus) {
        derivedStateOf {
            val cleanQuery = query.trim().lowercase()
            orders
                .filter { selectedStatus == "all" || it.status.lowercase() == selectedStatus }
                .filter { order ->
                    cleanQuery.isBlank() || listOf(
                        order.id,
                        order.orderNumber,
                        order.status,
                        order.source,
                        order.type,
                        order.product,
                        order.customer,
                        order.name,
                        order.email,
                        order.resellerEmail,
                        order.dealerEmail,
                        order.country
                    ).joinToString(" ").lowercase().contains(cleanQuery)
                }
        }
    }

    val pending = orders.count { it.status.equals("pending", true) }
    val processing = orders.count { it.status.equals("processing", true) }
    val completed = orders.count {
        it.status.equals("completed", true) || it.status.equals("delivered", true) || it.status.equals("confirmed", true)
    }

    Scaffold(
        containerColor = R2wSaasColors.Background,
        bottomBar = {
            R2wSaasBottomNav(
                selected = R2wSaasNavItem.Orders,
                onClick = onBottomNavClick
            )
        }
    ) { inner ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { Spacer(Modifier.height(8.dp)) }

            item {
                R2wSaasHeader(
                    title = "Orders",
                    subtitle = "${filtered.size} visible of ${orders.size} total orders.",
                    badge = if (loading) "Loading" else "Live API"
                )
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    R2wMetricCard(
                        modifier = Modifier.weight(1f),
                        title = "Shown",
                        value = filtered.size.toString(),
                        subtitle = "${orders.size} total",
                        icon = Icons.Default.ReceiptLong,
                        tint = R2wSaasColors.Primary
                    )

                    R2wMetricCard(
                        modifier = Modifier.weight(1f),
                        title = "Completed",
                        value = completed.toString(),
                        subtitle = "$pending pending",
                        icon = Icons.Default.CheckCircle,
                        tint = R2wSaasColors.Green
                    )
                }
            }

            item {
                OrdersSearchAndFilters(
                    query = query,
                    selectedStatus = selectedStatus,
                    onQueryChange = onQueryChange,
                    onStatusChange = onStatusChange,
                    onRefresh = onRefresh
                )
            }

            if (errorMessage != null) {
                item {
                    R2wSaasCard {
                        Text(
                            text = "Could not load orders",
                            color = R2wSaasColors.Red,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = errorMessage,
                            color = R2wSaasColors.Muted,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            if (loading) {
                item {
                    R2wSaasCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(color = R2wSaasColors.Primary)
                            Text(
                                text = "Loading orders...",
                                color = R2wSaasColors.Muted,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(start = 12.dp)
                            )
                        }
                    }
                }
            } else if (filtered.isEmpty()) {
                item {
                    R2wSaasCard {
                        Text(
                            text = "No orders found",
                            color = R2wSaasColors.Text,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Black
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Try another search or status filter.",
                            color = R2wSaasColors.Muted,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            } else {
                items(filtered.size) { index ->
                    OrderSaasCard(
                        order = filtered[index],
                        onClick = { onOpenOrder(filtered[index]) }
                    )
                }
            }

            item { Spacer(Modifier.height(18.dp)) }
        }
    }
}

@Composable
private fun OrdersSearchAndFilters(
    query: String,
    selectedStatus: String,
    onQueryChange: (String) -> Unit,
    onStatusChange: (String) -> Unit,
    onRefresh: () -> Unit
) {
    R2wSaasCard {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = R2wSaasColors.Muted
                )
            },
            placeholder = { Text("Search orders...") },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = R2wSaasColors.Primary,
                unfocusedBorderColor = R2wSaasColors.Border,
                focusedContainerColor = R2wSaasColors.Card,
                unfocusedContainerColor = R2wSaasColors.Card,
                focusedTextColor = R2wSaasColors.Text,
                unfocusedTextColor = R2wSaasColors.Text,
                cursorColor = R2wSaasColors.Primary
            ),
            shape = RoundedCornerShape(18.dp)
        )

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OrderFilterChip("all", "All", selectedStatus, onStatusChange)
            OrderFilterChip("pending", "Pending", selectedStatus, onStatusChange)
            OrderFilterChip("processing", "Processing", selectedStatus, onStatusChange)
            OrderFilterChip("completed", "Completed", selectedStatus, onStatusChange)
            OrderFilterChip("cancelled", "Cancelled", selectedStatus, onStatusChange)

            AssistChip(
                onClick = onRefresh,
                label = { Text("Refresh", fontWeight = FontWeight.ExtraBold) },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = R2wSaasColors.PrimarySoft,
                    labelColor = R2wSaasColors.Primary
                ),
                border = BorderStroke(1.dp, R2wSaasColors.Border)
            )
        }
    }
}

@Composable
private fun OrderFilterChip(
    key: String,
    label: String,
    selectedStatus: String,
    onStatusChange: (String) -> Unit
) {
    val selected = selectedStatus == key

    AssistChip(
        onClick = { onStatusChange(key) },
        label = { Text(label, fontWeight = FontWeight.ExtraBold) },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = if (selected) R2wSaasColors.PrimarySoft else R2wSaasColors.Card,
            labelColor = if (selected) R2wSaasColors.Primary else R2wSaasColors.Muted
        ),
        border = BorderStroke(1.dp, R2wSaasColors.Border)
    )
}

@Composable
private fun OrderSaasCard(
    order: AdminOrderUi,
    onClick: () -> Unit
) {
    R2wSaasCard(
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Surface(
                shape = RoundedCornerShape(17.dp),
                color = orderStatusColor(order.status).copy(alpha = 0.10f),
                border = BorderStroke(1.dp, R2wSaasColors.Border)
            ) {
                Icon(
                    imageVector = Icons.Default.ShoppingCart,
                    contentDescription = null,
                    tint = orderStatusColor(order.status),
                    modifier = Modifier.padding(11.dp)
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = order.orderNumber.ifBlank { "Order #${order.id}" },
                            color = R2wSaasColors.Primary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(Modifier.height(3.dp))

                        Text(
                            text = order.product.ifBlank { order.type.ifBlank { "-" } },
                            color = R2wSaasColors.Text,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(Modifier.height(2.dp))

                        Text(
                            text = order.customer.ifBlank { order.name }.ifBlank { order.email },
                            color = R2wSaasColors.Muted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    OrderStatusPill(order.status, orderStatusColor(order.status))
                }

                Spacer(Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OrderMiniStat(
                        title = "Source",
                        value = order.source,
                        modifier = Modifier.weight(1f)
                    )
                    OrderMiniStat(
                        title = "Qty",
                        value = order.quantity,
                        modifier = Modifier.weight(1f)
                    )
                    OrderMiniStat(
                        title = "Type",
                        value = order.type,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column {
                        Text(
                            text = "View order details",
                            color = R2wSaasColors.Primary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black
                        )

                        Text(
                            text = order.country.ifBlank { "Delivery country not set" },
                            color = R2wSaasColors.Muted,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = moneyValue(order.total),
                            color = R2wSaasColors.Text,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black
                        )

                        Text(
                            text = "  ›",
                            color = R2wSaasColors.Primary,
                            fontSize = 23.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OrderStatusPill(
    text: String,
    color: Color
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.18f))
    ) {
        Text(
            text = text.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() },
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
            maxLines = 1
        )
    }
}

@Composable
private fun OrderMiniStat(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = R2wSaasColors.Background,
        border = BorderStroke(1.dp, R2wSaasColors.Border)
    ) {
        Column(modifier = Modifier.padding(9.dp)) {
            Text(
                text = title,
                color = R2wSaasColors.Muted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = value.ifBlank { "-" },
                color = R2wSaasColors.Text,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun orderStatusColor(status: String): Color {
    val s = status.lowercase()
    return when {
        s.contains("complete") || s.contains("deliver") || s.contains("confirm") -> R2wSaasColors.Green
        s.contains("pending") || s.contains("process") -> R2wSaasColors.Orange
        s.contains("cancel") || s.contains("fail") || s.contains("reject") -> R2wSaasColors.Red
        else -> R2wSaasColors.Primary
    }
}

private fun moneyValue(value: String): String {
    val clean = value.trim()
    if (clean.isBlank()) return "$0.00"
    return if (clean.startsWith("$") || clean.contains("€")) clean else "$" + clean
}
