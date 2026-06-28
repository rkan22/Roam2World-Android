package im.angry.openeuicc

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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

class AdminReportsActivity : ComponentActivity() {
    private val tokenStore by lazy { AuthTokenStore(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val scope = rememberCoroutineScope()
            var loading by androidx.compose.runtime.remember { mutableStateOf(true) }
            var errorMessage by androidx.compose.runtime.remember { mutableStateOf<String?>(null) }
            var report by androidx.compose.runtime.remember { mutableStateOf(AdminReportsUi()) }

            suspend fun loadReports() {
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
                        val response = fetchAdminReports(session.authorizationHeader)
                        parseReports(response)
                    }
                }

                result
                    .onSuccess { report = it }
                    .onFailure {
                        errorMessage = it.message ?: "Reports API error"
                        Toast.makeText(this@AdminReportsActivity, errorMessage, Toast.LENGTH_LONG).show()
                    }

                loading = false
            }

            LaunchedEffect(Unit) {
                loadReports()
            }

            R2WTheme {
                AdminReportsSaasScreen(
                    loading = loading,
                    errorMessage = errorMessage,
                    report = report,
                    onRefresh = {
                        scope.launch {
                            loadReports()
                        }
                    },
                    onBottomNavClick = { item ->
                        when (item) {
                            R2wSaasNavItem.Dashboard -> startActivity(Intent(this@AdminReportsActivity, MobileAdminActivity::class.java))
                            R2wSaasNavItem.Partners -> startActivity(Intent(this@AdminReportsActivity, AdminPartnersActivity::class.java))
                            R2wSaasNavItem.Orders -> startActivity(Intent(this@AdminReportsActivity, AdminOrdersOverviewActivity::class.java))
                            R2wSaasNavItem.Pricing -> startActivity(Intent(this@AdminReportsActivity, AdminPricingOverviewActivity::class.java))
                            R2wSaasNavItem.More -> startActivity(Intent(this@AdminReportsActivity, AdminMoreActivity::class.java))
                        }
                    }
                )
            }
        }
    }

    private fun fetchAdminReports(authorizationHeader: String): JSONObject {
        val url = URL("https://roam2world-panels-backend.onrender.com/api/v1/mobile/admin/reports/")
        val connection = url.openConnection() as HttpURLConnection

        return try {
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Authorization", authorizationHeader)
            connection.connectTimeout = 15000
            connection.readTimeout = 15000

            val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
            val body = stream.bufferedReader().use { it.readText() }

            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("HTTP ${connection.responseCode}: $body")
            }

            JSONObject(body)
        } finally {
            connection.disconnect()
        }
    }

    private fun parseReports(response: JSONObject): AdminReportsUi {
        val data = response.optJSONObject("data") ?: response
        val summary = data.optJSONObject("summary") ?: JSONObject()

        return AdminReportsUi(
            usersTotal = summary.optInt("users_total", 0),
            resellersTotal = summary.optInt("resellers_total", 0),
            dealersTotal = summary.optInt("dealers_total", 0),
            ordersTotal = summary.optInt("orders_total", 0),
            totalSales = summary.optString("total_sales", "0.00"),
            currency = summary.optString("currency", "USD"),
            esimsTotal = summary.optInt("esims_total", 0),
            plansTotal = summary.optInt("plans_total", 0),
            activePlans = summary.optInt("active_plans", 0),
            ordersByStatus = parseRows(data.optJSONArray("orders_by_status"), "status"),
            ordersBySource = parseRows(data.optJSONArray("orders_by_source"), "source"),
            topCountries = parseRows(data.optJSONArray("top_countries"), "country"),
            recentOrders = parseRecentOrders(data.optJSONArray("recent_orders"))
        )
    }

    private fun parseRows(array: JSONArray?, labelKey: String): List<ReportRowUi> {
        if (array == null) return emptyList()
        val list = mutableListOf<ReportRowUi>()

        for (i in 0 until array.length()) {
            val row = array.optJSONObject(i) ?: continue
            list.add(
                ReportRowUi(
                    label = row.optString(labelKey, "-"),
                    count = row.optInt("count", 0),
                    sales = row.optString("sales", "0.00")
                )
            )
        }

        return list
    }

    private fun parseRecentOrders(array: JSONArray?): List<RecentReportOrderUi> {
        if (array == null) return emptyList()
        val list = mutableListOf<RecentReportOrderUi>()

        for (i in 0 until array.length()) {
            val row = array.optJSONObject(i) ?: continue
            list.add(
                RecentReportOrderUi(
                    orderNumber = row.optString("order_number", "-"),
                    status = row.optString("status", "-"),
                    productName = row.optString("product_name", "-"),
                    totalAmount = row.optString("total_amount", "0.00")
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

private data class AdminReportsUi(
    val usersTotal: Int = 0,
    val resellersTotal: Int = 0,
    val dealersTotal: Int = 0,
    val ordersTotal: Int = 0,
    val totalSales: String = "0.00",
    val currency: String = "USD",
    val esimsTotal: Int = 0,
    val plansTotal: Int = 0,
    val activePlans: Int = 0,
    val ordersByStatus: List<ReportRowUi> = emptyList(),
    val ordersBySource: List<ReportRowUi> = emptyList(),
    val topCountries: List<ReportRowUi> = emptyList(),
    val recentOrders: List<RecentReportOrderUi> = emptyList()
)

private data class ReportRowUi(
    val label: String,
    val count: Int,
    val sales: String
)

private data class RecentReportOrderUi(
    val orderNumber: String,
    val status: String,
    val productName: String,
    val totalAmount: String
)

@Composable
private fun AdminReportsSaasScreen(
    loading: Boolean,
    errorMessage: String?,
    report: AdminReportsUi,
    onRefresh: () -> Unit,
    onBottomNavClick: (R2wSaasNavItem) -> Unit
) {
    Scaffold(
        containerColor = R2wSaasColors.Background,
        bottomBar = {
            R2wSaasBottomNav(
                selected = R2wSaasNavItem.More,
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
                    title = "Reports",
                    subtitle = "${moneyValue(report.totalSales)} ${report.currency} total sales • ${report.ordersTotal} total orders.",
                    badge = if (loading) "Loading" else "Live API"
                )
            }

            item {
                Button(
                    onClick = onRefresh,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = R2wSaasColors.Primary),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = null)
                    Text(
                        text = "Refresh Reports",
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    R2wMetricCard(
                        modifier = Modifier.weight(1f),
                        title = "Sales",
                        value = moneyValue(report.totalSales),
                        subtitle = report.currency,
                        icon = Icons.Default.AttachMoney,
                        tint = R2wSaasColors.Primary
                    )

                    R2wMetricCard(
                        modifier = Modifier.weight(1f),
                        title = "Orders",
                        value = report.ordersTotal.toString(),
                        subtitle = "total orders",
                        icon = Icons.Default.ReceiptLong,
                        tint = R2wSaasColors.Green
                    )
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    R2wMetricCard(
                        modifier = Modifier.weight(1f),
                        title = "Users",
                        value = report.usersTotal.toString(),
                        subtitle = "${report.resellersTotal} resellers",
                        icon = Icons.Default.People,
                        tint = R2wSaasColors.Purple
                    )

                    R2wMetricCard(
                        modifier = Modifier.weight(1f),
                        title = "Plans",
                        value = report.plansTotal.toString(),
                        subtitle = "${report.activePlans} active",
                        icon = Icons.Default.SimCard,
                        tint = R2wSaasColors.Orange
                    )
                }
            }

            item {
                ReportsSalesOverviewCard(
                    totalSales = moneyValue(report.totalSales),
                    currency = report.currency,
                    ordersTotal = report.ordersTotal
                )
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
                                text = "Loading reports...",
                                color = R2wSaasColors.Muted,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(start = 12.dp)
                            )
                        }
                    }
                }
            }

            if (errorMessage != null) {
                item {
                    R2wSaasCard {
                        Text(
                            text = "Could not load reports",
                            color = R2wSaasColors.Red,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Black
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = errorMessage,
                            color = R2wSaasColors.Muted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            item {
                ReportsRowsSaasCard(
                    title = "Orders by Status",
                    rows = report.ordersByStatus,
                    tint = R2wSaasColors.Primary
                )
            }

            item {
                ReportsRowsSaasCard(
                    title = "Orders by Source",
                    rows = report.ordersBySource,
                    tint = R2wSaasColors.Green
                )
            }

            item {
                ReportsRowsSaasCard(
                    title = "Top Countries",
                    rows = report.topCountries,
                    tint = R2wSaasColors.Orange
                )
            }

            item {
                ReportsRecentOrdersSaasCard(orders = report.recentOrders)
            }

            item { Spacer(Modifier.height(18.dp)) }
        }
    }
}

@Composable
private fun ReportsSalesOverviewCard(
    totalSales: String,
    currency: String,
    ordersTotal: Int
) {
    R2wSaasCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column {
                Text(
                    text = "Sales Overview",
                    color = R2wSaasColors.Text,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Black
                )

                Spacer(Modifier.height(6.dp))

                Text(
                    text = totalSales,
                    color = R2wSaasColors.Text,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black
                )

                Text(
                    text = "$ordersTotal orders • $currency",
                    color = R2wSaasColors.Green,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            Surface(
                shape = RoundedCornerShape(50),
                color = R2wSaasColors.PrimarySoft,
                border = BorderStroke(1.dp, R2wSaasColors.Border)
            ) {
                Text(
                    text = "Live",
                    color = R2wSaasColors.Primary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(105.dp)
        ) {
            val values = listOf(0.18f, 0.32f, 0.28f, 0.50f, 0.46f, 0.70f, 0.84f)
            val left = 8.dp.toPx()
            val right = size.width - 8.dp.toPx()
            val top = 8.dp.toPx()
            val bottom = size.height - 12.dp.toPx()
            val width = right - left

            repeat(4) { index ->
                val y = top + ((bottom - top) / 3f) * index
                drawLine(
                    color = Color(0xFFE9EEF7),
                    start = Offset(left, y),
                    end = Offset(right, y),
                    strokeWidth = 1.dp.toPx()
                )
            }

            val points = values.mapIndexed { index, value ->
                Offset(
                    x = left + width * (index.toFloat() / (values.size - 1).toFloat()),
                    y = bottom - ((bottom - top) * value)
                )
            }

            for (index in 0 until points.lastIndex) {
                drawLine(
                    color = R2wSaasColors.Primary,
                    start = points[index],
                    end = points[index + 1],
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            points.forEach { point ->
                drawCircle(color = R2wSaasColors.Card, radius = 5.dp.toPx(), center = point)
                drawCircle(color = R2wSaasColors.Primary, radius = 4.dp.toPx(), center = point)
            }
        }
    }
}

@Composable
private fun ReportsRowsSaasCard(
    title: String,
    rows: List<ReportRowUi>,
    tint: Color
) {
    R2wSaasCard {
        Text(
            text = title,
            color = R2wSaasColors.Text,
            fontSize = 17.sp,
            fontWeight = FontWeight.Black
        )

        Spacer(Modifier.height(10.dp))

        if (rows.isEmpty()) {
            Text(
                text = "No data yet",
                color = R2wSaasColors.Muted,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        } else {
            rows.take(6).forEach { row ->
                ReportRowLine(
                    label = row.label,
                    value = row.count.toString(),
                    subtitle = moneyValue(row.sales),
                    tint = tint
                )
            }
        }
    }
}

@Composable
private fun ReportsRecentOrdersSaasCard(
    orders: List<RecentReportOrderUi>
) {
    R2wSaasCard {
        Text(
            text = "Recent Orders",
            color = R2wSaasColors.Text,
            fontSize = 17.sp,
            fontWeight = FontWeight.Black
        )

        Spacer(Modifier.height(10.dp))

        if (orders.isEmpty()) {
            Text(
                text = "No recent orders yet",
                color = R2wSaasColors.Muted,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        } else {
            orders.take(6).forEach { order ->
                ReportRowLine(
                    label = order.orderNumber,
                    value = moneyValue(order.totalAmount),
                    subtitle = "${order.status} • ${order.productName}",
                    tint = reportStatusColor(order.status)
                )
            }
        }
    }
}

@Composable
private fun ReportRowLine(
    label: String,
    value: String,
    subtitle: String,
    tint: Color
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(18.dp),
        color = R2wSaasColors.Background,
        border = BorderStroke(1.dp, R2wSaasColors.Border)
    ) {
        Row(
            modifier = Modifier.padding(11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = tint.copy(alpha = 0.10f)
            ) {
                Icon(
                    imageVector = Icons.Default.Analytics,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.padding(9.dp)
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 10.dp)
            ) {
                Text(
                    text = label.ifBlank { "-" },
                    color = R2wSaasColors.Text,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = subtitle.ifBlank { "-" },
                    color = R2wSaasColors.Muted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Text(
                text = value,
                color = tint,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1
            )
        }
    }
}

private fun reportStatusColor(status: String): Color {
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
