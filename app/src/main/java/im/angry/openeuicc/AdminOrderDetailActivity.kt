package im.angry.openeuicc

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.PendingActions
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import im.angry.openeuicc.ui.compose.theme.R2WTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private val DetailBg = Color(0xFFF6F8FC)
private val DetailNavy = Color(0xFF061A3F)
private val DetailNavy2 = Color(0xFF123EAD)
private val DetailBlue = Color(0xFF1263F1)
private val DetailText = Color(0xFF101828)
private val DetailMuted = Color(0xFF667085)
private val DetailBorder = Color(0xFFE2E8F0)
private val DetailGreen = Color(0xFF16A34A)
private val DetailOrange = Color(0xFFF97316)
private val DetailRed = Color(0xFFEF4444)

class AdminOrderDetailActivity : ComponentActivity() {
    companion object {
        const val EXTRA_ORDER_JSON = "extra_order_json"
    }

    private val tokenStore by lazy { AuthTokenStore(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val raw = intent.getStringExtra(EXTRA_ORDER_JSON)
        val parsedOrder = runCatching {
            if (raw.isNullOrBlank()) null else parseOrder(JSONObject(raw))
        }.getOrNull()

        setContent {
            val scope = rememberCoroutineScope()
            var updating by remember { mutableStateOf(false) }

            R2WTheme {
                AdminOrderDetailScreen(
                    order = parsedOrder,
                    updating = updating,
                    onBack = { finish() },
                    onStatusClick = { nextStatus ->
                        if (parsedOrder == null) return@AdminOrderDetailScreen

                        confirmAction(
                            title = "Set ${nextStatus.replaceFirstChar { it.uppercase() }}",
                            message = "Change order status to $nextStatus?"
                        ) {
                            scope.launch {
                                updating = true
                                updateOrderStatus(parsedOrder.id, nextStatus)
                                updating = false
                            }
                        }
                    }
                )
            }
        }
    }

    private fun parseOrder(order: JSONObject): AdminOrderDetailUi {
        return AdminOrderDetailUi(
            rawJson = order.toString(),
            id = order.optString("id", order.optString("order_id", "-")),
            orderNumber = order.optString("order_number", "-"),
            status = order.optString("status", "-"),
            name = order.optString("name", "-"),
            email = order.optString("email", "-"),
            source = order.optString("order_source", "-"),
            type = order.optString("order_type", "-"),
            product = order.optString("product_name", "-"),
            quantity = order.optInt("quantity", 0).toString(),
            total = order.optString("total_amount", "0.00"),
            customer = order.optString("customer_name", "-"),
            resellerEmail = order.optString("reseller_email", ""),
            dealerEmail = order.optString("dealer_email", ""),
            country = order.optString("delivery_country", ""),
            createdAt = order.optString("created_at", "-"),
            updatedAt = order.optString("updated_at", "-")
        )
    }

    private suspend fun updateOrderStatus(orderId: String, status: String) {
        if (orderId.isBlank() || orderId == "-") {
            Toast.makeText(this, "Missing order id", Toast.LENGTH_LONG).show()
            return
        }

        val session = withContext(Dispatchers.IO) { tokenStore.getSession() }

        if (session == null || JwtUtils.isExpired(session.accessToken)) {
            redirectToLogin()
            return
        }

        val result = withContext(Dispatchers.IO) {
            runCatching {
                postOrderStatus(orderId, status, session.authorizationHeader)
            }
        }

        result.onSuccess { response ->
            val message = response.optString("message", "Order status updated")
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            finish()
        }.onFailure { error ->
            Toast.makeText(
                this,
                error.message ?: "Order status update failed",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun postOrderStatus(
        orderId: String,
        status: String,
        authorizationHeader: String
    ): JSONObject {
        val url = URL("https://roam2world-panels-backend.onrender.com/api/v1/orders/orders/$orderId/update_status/")
        val connection = url.openConnection() as HttpURLConnection

        return try {
            val body = JSONObject()
                .put("status", status)
                .put("notes", "Updated from mobile admin")
                .toString()

            connection.requestMethod = "POST"
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", authorizationHeader)
            connection.doOutput = true
            connection.connectTimeout = 15000
            connection.readTimeout = 15000

            connection.outputStream.use { output ->
                output.write(body.toByteArray(Charsets.UTF_8))
            }

            val stream = if (connection.responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }

            val responseBody = stream.bufferedReader().use { it.readText() }

            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("HTTP ${connection.responseCode}: $responseBody")
            }

            JSONObject(responseBody)
        } finally {
            connection.disconnect()
        }
    }

    private fun redirectToLogin() {
        Toast.makeText(this, "Session expired. Please login again.", Toast.LENGTH_LONG).show()
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    private fun confirmAction(
        title: String,
        message: String,
        onConfirm: () -> Unit
    ) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Confirm") { _, _ -> onConfirm() }
            .setNegativeButton("Cancel", null)
            .show()
    }
}

private data class AdminOrderDetailUi(
    val rawJson: String,
    val id: String,
    val orderNumber: String,
    val status: String,
    val name: String,
    val email: String,
    val source: String,
    val type: String,
    val product: String,
    val quantity: String,
    val total: String,
    val customer: String,
    val resellerEmail: String,
    val dealerEmail: String,
    val country: String,
    val createdAt: String,
    val updatedAt: String
)

@Composable
private fun AdminOrderDetailScreen(
    order: AdminOrderDetailUi?,
    updating: Boolean,
    onBack: () -> Unit,
    onStatusClick: (String) -> Unit
) {
    Scaffold(
        containerColor = DetailBg
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(DetailBg)
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (order == null) {
                DetailTopBar(title = "Order Detail", onBack = onBack)
                DetailInfoCard(title = "No order data", icon = R.drawable.admin_icon_doc) {
                    DetailLine("Status", "Order data was not provided.")
                }
            } else {
                DetailHero(order = order, onBack = onBack)

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DetailMetricCard(
                        modifier = Modifier.weight(1f),
                        icon = R.drawable.admin_icon_orders,
                        label = "Quantity",
                        value = order.quantity,
                        sub = "items"
                    )
                    DetailMetricCard(
                        modifier = Modifier.weight(1f),
                        icon = R.drawable.admin_icon_money,
                        label = "Total",
                        value = order.total.ifBlank { "0.00" },
                        sub = "order amount"
                    )
                }

                DetailInfoCard(title = "Customer", icon = R.drawable.admin_icon_user) {
                    DetailLine("Customer", order.customer.ifBlank { "-" })
                    DetailLine("Name", order.name.ifBlank { "-" })
                    DetailLine("Email", order.email.ifBlank { "-" })
                    DetailLine("Country", order.country.ifBlank { "-" })
                }

                DetailInfoCard(title = "Product & Payment", icon = R.drawable.admin_icon_doc) {
                    DetailLine("Product", order.product.ifBlank { "-" })
                    DetailLine("Quantity", order.quantity)
                    DetailLine("Total", order.total.ifBlank { "0.00" })
                    DetailLine("Order ID", order.id.ifBlank { "-" })
                }

                DetailInfoCard(title = "Channel", icon = R.drawable.admin_icon_partners) {
                    DetailLine("Source", order.source.ifBlank { "-" })
                    DetailLine("Type", order.type.ifBlank { "-" })
                    DetailLine("Reseller", order.resellerEmail.ifBlank { "-" })
                    DetailLine("Dealer", order.dealerEmail.ifBlank { "-" })
                }

                DetailInfoCard(title = "Timeline", icon = R.drawable.admin_icon_health) {
                    DetailLine("Created", order.createdAt.ifBlank { "-" })
                    DetailLine("Updated", order.updatedAt.ifBlank { "-" })
                    DetailLine("Current status", order.status.ifBlank { "-" })
                }

                StatusActionsCard(
                    currentStatus = order.status,
                    updating = updating,
                    onStatusClick = onStatusClick
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun DetailTopBar(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.Default.ArrowBack, contentDescription = null, tint = DetailText)
        }
        Text(title, color = DetailText, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun DetailHero(order: AdminOrderDetailUi, onBack: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = DetailNavy),
        elevation = CardDefaults.cardElevation(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(182.dp)
                .background(Color.White)
                .padding(16.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null, tint = Color.White)
                    }

                    Spacer(Modifier.width(4.dp))

                    Text(
                        "Order Detail",
                        color = Color.White,
                        fontSize = 25.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1
                    )
                }

                Spacer(Modifier.height(8.dp))

                Text(
                    order.orderNumber.ifBlank { "-" },
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(5.dp))

                Text(
                    order.product.ifBlank { "Unknown product" },
                    color = Color.White.copy(alpha = 0.74f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(14.dp))

                Surface(
                    color = detailStatusColor(order.status).copy(alpha = 0.18f),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f))
                ) {
                    Text(
                        order.status.replaceFirstChar { it.uppercase() },
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailMetricCard(
    modifier: Modifier,
    @DrawableRes icon: Int,
    label: String,
    value: String,
    sub: String
) {
    Card(
        modifier = modifier.height(120.dp),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, DetailBorder),
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
                Text(label, color = DetailMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(value, color = DetailText, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                Text(sub, color = DetailBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun DetailInfoCard(
    title: String,
    @DrawableRes icon: Int,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, DetailBorder),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(icon), contentDescription = null, modifier = Modifier.size(32.dp))
                Spacer(Modifier.width(10.dp))
                Text(title, color = DetailText, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
            }

            content()
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            label,
            color = DetailMuted,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(0.42f)
        )
        Text(
            value.ifBlank { "-" },
            color = DetailText,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(0.58f),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun StatusActionsCard(
    currentStatus: String,
    updating: Boolean,
    onStatusClick: (String) -> Unit
) {
    val nextStatuses = nextOrderStatuses(currentStatus)

    DetailInfoCard(title = "Status Actions", icon = R.drawable.admin_icon_settings) {
        DetailLine("Current", currentStatus.ifBlank { "-" })

        if (nextStatuses.isEmpty()) {
            Text(
                "No next action available for this order.",
                color = DetailMuted,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        } else {
            nextStatuses.forEach { status ->
                Button(
                    onClick = { onStatusClick(status) },
                    enabled = !updating,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = actionColor(status),
                        disabledContainerColor = DetailMuted.copy(alpha = 0.35f)
                    )
                ) {
                    Icon(actionIcon(status), contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (updating) "Updating..." else "Set ${status.replaceFirstChar { it.uppercase() }}",
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }
    }
}

private fun nextOrderStatuses(currentStatus: String): List<String> {
    return when (currentStatus.lowercase()) {
        "pending" -> listOf("confirmed", "cancelled")
        "confirmed" -> listOf("processing", "cancelled")
        "processing" -> listOf("dispatched", "cancelled")
        "dispatched" -> listOf("delivered", "cancelled")
        "delivered" -> listOf("activated", "completed")
        "activated" -> listOf("completed")
        else -> emptyList()
    }
}

private fun detailStatusColor(status: String): Color {
    val clean = status.lowercase()
    return when {
        clean.contains("complete") || clean.contains("deliver") || clean.contains("confirm") || clean.contains("activate") -> DetailGreen
        clean.contains("process") || clean.contains("dispatch") -> DetailOrange
        clean.contains("cancel") || clean.contains("fail") -> DetailRed
        clean.contains("pending") -> DetailBlue
        else -> DetailMuted
    }
}

private fun actionColor(status: String): Color {
    val clean = status.lowercase()
    return when {
        clean.contains("cancel") -> DetailRed
        clean.contains("complete") || clean.contains("deliver") || clean.contains("confirm") || clean.contains("activate") -> DetailGreen
        clean.contains("process") || clean.contains("dispatch") -> DetailOrange
        else -> DetailBlue
    }
}

private fun actionIcon(status: String): ImageVector {
    val clean = status.lowercase()
    return when {
        clean.contains("cancel") -> Icons.Default.Close
        clean.contains("dispatch") || clean.contains("deliver") -> Icons.Default.LocalShipping
        clean.contains("process") -> Icons.Default.Sync
        clean.contains("pending") -> Icons.Default.PendingActions
        else -> Icons.Default.CheckCircle
    }
}
