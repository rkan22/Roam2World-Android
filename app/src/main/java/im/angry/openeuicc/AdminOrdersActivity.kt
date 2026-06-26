package im.angry.openeuicc

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import im.angry.openeuicc.auth.AuthTokenStore
import im.angry.openeuicc.auth.JwtUtils
import im.angry.openeuicc.ui.LoginActivity
import im.angry.openeuicc.ui.compose.screens.admin.AdminBottomNavItem
import im.angry.openeuicc.ui.compose.theme.R2WTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private val AdminOrderBg = Color(0xFFF6F8FC)
private val AdminOrderNavy = Color(0xFF061A3F)
private val AdminOrderNavy2 = Color(0xFF123EAD)
private val AdminOrderBlue = Color(0xFF1263F1)
private val AdminOrderText = Color(0xFF101828)
private val AdminOrderMuted = Color(0xFF667085)
private val AdminOrderBorder = Color(0xFFE1E8F2)
private val AdminOrderGreen = Color(0xFF16A34A)
private val AdminOrderOrange = Color(0xFFF97316)
private val AdminOrderRed = Color(0xFFEF4444)

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
            val composeScope = rememberCoroutineScope()

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
                AdminOrdersListScreen(
                    loading = loading,
                    errorMessage = errorMessage,
                    orders = orders,
                    query = query,
                    selectedStatus = selectedStatus,
                    onQueryChange = { query = it },
                    onStatusChange = { selectedStatus = it },
                    onRefresh = {
                        composeScope.launch {
                            loadOrders()
                        }
                    },
                    onOpenOrder = { openOrder(it) },
                    onBottomNavClick = { item ->
                        when (item) {
                            AdminBottomNavItem.Dashboard -> startActivity(Intent(this@AdminOrdersActivity, MobileAdminActivity::class.java))
                            AdminBottomNavItem.Partners -> startActivity(Intent(this@AdminOrdersActivity, AdminPartnersActivity::class.java))
                            AdminBottomNavItem.Orders -> startActivity(Intent(this@AdminOrdersActivity, AdminOrdersOverviewActivity::class.java))
                            AdminBottomNavItem.Pricing -> startActivity(Intent(this@AdminOrdersActivity, AdminPricingOverviewActivity::class.java))
                            AdminBottomNavItem.More -> startActivity(Intent(this@AdminOrdersActivity, AdminMoreActivity::class.java))
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
private fun AdminOrdersListScreen(
    loading: Boolean,
    errorMessage: String?,
    orders: List<AdminOrderUi>,
    query: String,
    selectedStatus: String,
    onQueryChange: (String) -> Unit,
    onStatusChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onOpenOrder: (AdminOrderUi) -> Unit,
    onBottomNavClick: (AdminBottomNavItem) -> Unit
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
        containerColor = AdminOrderBg,
        bottomBar = {
            AdminOrdersBottomNav(
                selected = AdminBottomNavItem.Orders,
                onClick = onBottomNavClick
            )
        }
    ) { inner ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(AdminOrderBg)
                .padding(inner)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(Modifier.height(8.dp)) }

            item {
                AdminOrdersHero(
                    shown = filtered.size.toString(),
                    total = orders.size.toString(),
                    pending = pending.toString(),
                    completed = completed.toString()
                )
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AdminOrderMetric(
                        modifier = Modifier.weight(1f),
                        icon = R.drawable.admin_icon_orders,
                        label = "Shown",
                        value = filtered.size.toString(),
                        sub = "${orders.size} total",
                        subColor = AdminOrderBlue
                    )
                    AdminOrderMetric(
                        modifier = Modifier.weight(1f),
                        icon = R.drawable.admin_icon_health,
                        label = "Processing",
                        value = processing.toString(),
                        sub = "in progress",
                        subColor = AdminOrderOrange
                    )
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, AdminOrderBorder),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = onQueryChange,
                            modifier = Modifier.fillMaxWidth(),
                            leadingIcon = {
                                Icon(Icons.Default.Search, contentDescription = null)
                            },
                            label = { Text("Search order, email, customer, product") },
                            singleLine = true
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                "all" to "All",
                                "pending" to "Pending",
                                "confirmed" to "Confirmed",
                                "processing" to "Processing",
                                "dispatched" to "Dispatched",
                                "delivered" to "Delivered",
                                "completed" to "Completed",
                                "cancelled" to "Cancelled"
                            ).forEach { (value, label) ->
                                val selected = selectedStatus == value
                                AssistChip(
                                    onClick = { onStatusChange(value) },
                                    label = {
                                        Text(
                                            label,
                                            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold
                                        )
                                    },
                                    colors = AssistChipDefaults.assistChipColors(
                                        containerColor = if (selected) Color(0xFFEAF2FF) else Color.White,
                                        labelColor = if (selected) AdminOrderBlue else AdminOrderMuted
                                    ),
                                    border = BorderStroke(1.dp, if (selected) AdminOrderBlue.copy(alpha = 0.35f) else AdminOrderBorder)
                                )
                            }
                        }
                    }
                }
            }

            if (loading) {
                item {
                    AdminOrdersInfoCard("Loading Orders", "Retrieving latest order activity and fulfilment status.") {
                        CircularProgressIndicator(color = AdminOrderBlue)
                    }
                }
            }

            if (!loading && errorMessage != null) {
                item {
                    AdminOrdersInfoCard("Orders unavailable", errorMessage)
                }
            }

            if (!loading && errorMessage == null && filtered.isEmpty()) {
                item {
                    AdminOrdersInfoCard("No Matching Orders", "Clear filters or search by order number, email, customer, product, reseller, or dealer.")
                }
            }

            items(filtered.size) { index ->
                AdminOrderListCard(
                    order = filtered[index],
                    onClick = { onOpenOrder(filtered[index]) }
                )
            }

            item { Spacer(Modifier.height(10.dp)) }
        }
    }
}

@Composable
private fun AdminOrdersHero(
    shown: String,
    total: String,
    pending: String,
    completed: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = AdminOrderNavy),
        elevation = CardDefaults.cardElevation(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .background(Brush.horizontalGradient(listOf(AdminOrderNavy, AdminOrderNavy2)))
                .padding(18.dp)
        ) {
            Column {
                Text(
                    "Orders List",
                    color = Color.White,
                    fontSize = 27.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "$shown shown • $total total • $pending pending • $completed completed",
                    color = Color.White.copy(alpha = 0.72f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    "Live admin order feed",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .background(Color.White.copy(alpha = 0.14f), RoundedCornerShape(18.dp))
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                )
            }
        }
    }
}

@Composable
private fun AdminOrderMetric(
    modifier: Modifier,
    @DrawableRes icon: Int,
    label: String,
    value: String,
    sub: String,
    subColor: Color
) {
    Card(
        modifier = modifier.height(120.dp),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, AdminOrderBorder),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Image(painterResource(icon), contentDescription = null, modifier = Modifier.size(38.dp))
            Column {
                Text(label, color = AdminOrderMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(value, color = AdminOrderText, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                Text(sub, color = subColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun AdminOrderListCard(
    order: AdminOrderUi,
    onClick: () -> Unit
) {
    val statusColor = adminStatusColor(order.status)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, AdminOrderBorder),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(R.drawable.admin_icon_orders),
                    contentDescription = null,
                    modifier = Modifier.size(42.dp)
                )

                Spacer(Modifier.width(12.dp))

                Column(Modifier.weight(1f)) {
                    Text(
                        order.orderNumber.ifBlank { "-" },
                        color = AdminOrderText,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1
                    )
                    Text(
                        order.product.ifBlank { "Unknown product" },
                        color = AdminOrderMuted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Surface(
                    color = statusColor.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        order.status.replaceFirstChar { it.uppercase() },
                        color = statusColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            Text(
                "Customer: ${order.customer.ifBlank { order.name.ifBlank { "-" } }}",
                color = AdminOrderText,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                listOf(
                    order.email.ifBlank { null },
                    order.country.ifBlank { null },
                    "Qty ${order.quantity}",
                    "Amount ${order.total}"
                ).filterNotNull().joinToString(" • "),
                color = AdminOrderMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                "Channel: ${order.source} / ${order.type}",
                color = AdminOrderMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                "View order details",
                color = AdminOrderBlue,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

@Composable
private fun AdminOrdersInfoCard(
    title: String,
    message: String,
    content: @Composable (() -> Unit)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, AdminOrderBorder),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(title, color = AdminOrderText, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            Text(message, color = AdminOrderMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            content?.invoke()
        }
    }
}

private fun adminStatusColor(status: String): Color {
    val clean = status.lowercase()
    return when {
        clean.contains("complete") || clean.contains("deliver") || clean.contains("confirm") -> AdminOrderGreen
        clean.contains("process") || clean.contains("dispatch") -> AdminOrderOrange
        clean.contains("cancel") || clean.contains("fail") -> AdminOrderRed
        clean.contains("pending") -> AdminOrderBlue
        else -> AdminOrderMuted
    }
}

@Composable
private fun AdminOrdersBottomNav(
    selected: AdminBottomNavItem,
    onClick: (AdminBottomNavItem) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        color = Color.White,
        shadowElevation = 14.dp,
        border = BorderStroke(1.dp, AdminOrderBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(66.dp)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            AdminOrdersBottomItem(Icons.Default.GridView, "Dashboard", selected == AdminBottomNavItem.Dashboard) { onClick(AdminBottomNavItem.Dashboard) }
            AdminOrdersBottomItem(Icons.Default.People, "Partners", selected == AdminBottomNavItem.Partners) { onClick(AdminBottomNavItem.Partners) }
            AdminOrdersBottomItem(Icons.Default.ShoppingCart, "Orders", selected == AdminBottomNavItem.Orders) { onClick(AdminBottomNavItem.Orders) }
            AdminOrdersBottomItem(Icons.Default.CreditCard, "Pricing", selected == AdminBottomNavItem.Pricing) { onClick(AdminBottomNavItem.Pricing) }
            AdminOrdersBottomItem(Icons.Default.MoreHoriz, "More", selected == AdminBottomNavItem.More) { onClick(AdminBottomNavItem.More) }
        }
    }
}

@Composable
private fun AdminOrdersBottomItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val color = if (selected) AdminOrderBlue else AdminOrderMuted
    val bg = if (selected) Color(0xFFEAF2FF) else Color.Transparent

    Column(
        modifier = Modifier
            .size(width = 74.dp, height = 54.dp)
            .background(bg, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(21.dp))
        Spacer(Modifier.height(3.dp))
        Text(label, color = color, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
    }
}
