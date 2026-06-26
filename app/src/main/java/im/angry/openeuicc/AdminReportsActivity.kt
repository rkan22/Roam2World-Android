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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private val ReportsBg = Color(0xFFF6F8FC)
private val ReportsNavy = Color(0xFF061A3F)
private val ReportsNavy2 = Color(0xFF123EAD)
private val ReportsBlue = Color(0xFF1263F1)
private val ReportsText = Color(0xFF101828)
private val ReportsMuted = Color(0xFF667085)
private val ReportsBorder = Color(0xFFE1E8F2)
private val ReportsGreen = Color(0xFF16A34A)
private val ReportsOrange = Color(0xFFF97316)

class AdminReportsActivity : ComponentActivity() {
    private val tokenStore by lazy { AuthTokenStore(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val composeScope = rememberCoroutineScope()
            var loading by remember { mutableStateOf(true) }
            var errorMessage by remember { mutableStateOf<String?>(null) }
            var report by remember { mutableStateOf(AdminReportsUi()) }

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
                AdminReportsScreen(
                    loading = loading,
                    errorMessage = errorMessage,
                    report = report,
                    onRefresh = {
                        composeScope.launch {
                            loadReports()
                        }
                    },
                    onBottomNavClick = { tab ->
                        when (tab) {
                            ReportsTab.Dashboard -> startActivity(Intent(this@AdminReportsActivity, MobileAdminActivity::class.java))
                            ReportsTab.Partners -> startActivity(Intent(this@AdminReportsActivity, AdminPartnersActivity::class.java))
                            ReportsTab.Orders -> startActivity(Intent(this@AdminReportsActivity, AdminOrdersOverviewActivity::class.java))
                            ReportsTab.Pricing -> startActivity(Intent(this@AdminReportsActivity, AdminPricingOverviewActivity::class.java))
                            ReportsTab.More -> startActivity(Intent(this@AdminReportsActivity, AdminMoreActivity::class.java))
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

private enum class ReportsTab {
    Dashboard,
    Partners,
    Orders,
    Pricing,
    More
}

@Composable
private fun AdminReportsScreen(
    loading: Boolean,
    errorMessage: String?,
    report: AdminReportsUi,
    onRefresh: () -> Unit,
    onBottomNavClick: (ReportsTab) -> Unit
) {
    Scaffold(
        containerColor = ReportsBg,
        bottomBar = {
            ReportsBottomNav(
                selected = ReportsTab.More,
                onClick = onBottomNavClick
            )
        }
    ) { inner ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(ReportsBg)
                .padding(inner)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(Modifier.height(8.dp)) }

            item {
                ReportsHero(
                    sales = "${report.totalSales} ${report.currency}",
                    orders = report.ordersTotal.toString(),
                    onRefresh = onRefresh
                )
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ReportsMetricCard(
                        modifier = Modifier.weight(1f),
                        icon = R.drawable.admin_icon_user,
                        label = "Users",
                        value = report.usersTotal.toString(),
                        sub = "${report.resellersTotal} resellers",
                        subColor = ReportsBlue
                    )
                    ReportsMetricCard(
                        modifier = Modifier.weight(1f),
                        icon = R.drawable.admin_icon_orders,
                        label = "Orders",
                        value = report.ordersTotal.toString(),
                        sub = "total orders",
                        subColor = ReportsGreen
                    )
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ReportsMetricCard(
                        modifier = Modifier.weight(1f),
                        icon = R.drawable.admin_icon_partners,
                        label = "Dealers",
                        value = report.dealersTotal.toString(),
                        sub = "partners",
                        subColor = ReportsOrange
                    )
                    ReportsMetricCard(
                        modifier = Modifier.weight(1f),
                        icon = R.drawable.admin_icon_tag,
                        label = "Plans",
                        value = report.plansTotal.toString(),
                        sub = "${report.activePlans} active",
                        subColor = ReportsBlue
                    )
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ReportsMetricCard(
                        modifier = Modifier.weight(1f),
                        icon = R.drawable.admin_icon_doc,
                        label = "eSIMs",
                        value = report.esimsTotal.toString(),
                        sub = "inventory",
                        subColor = ReportsGreen
                    )
                    ReportsMetricCard(
                        modifier = Modifier.weight(1f),
                        icon = R.drawable.admin_icon_money,
                        label = "Sales",
                        value = report.totalSales,
                        sub = report.currency,
                        subColor = ReportsOrange
                    )
                }
            }

            if (loading) {
                item {
                    ReportsInfoCard("Loading reports", "Preparing revenue, order, and operational summaries.") {
                        CircularProgressIndicator(color = ReportsBlue)
                    }
                }
            }

            if (!loading && errorMessage != null) {
                item {
                    ReportsInfoCard("Reports unavailable", errorMessage)
                }
            }

            if (!loading && errorMessage == null) {
                item {
                    ReportsRowsCard(
                        title = "Orders by Status",
                        icon = R.drawable.admin_icon_orders,
                        rows = report.ordersByStatus
                    )
                }

                item {
                    ReportsRowsCard(
                        title = "Orders by Source",
                        icon = R.drawable.admin_icon_reports,
                        rows = report.ordersBySource
                    )
                }

                item {
                    ReportsRowsCard(
                        title = "Top Countries",
                        icon = R.drawable.admin_icon_reports,
                        rows = report.topCountries
                    )
                }

                item {
                    ReportsRecentOrdersCard(report.recentOrders)
                }
            }

            item { Spacer(Modifier.height(10.dp)) }
        }
    }
}

@Composable
private fun ReportsHero(
    sales: String,
    orders: String,
    onRefresh: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = ReportsNavy),
        elevation = CardDefaults.cardElevation(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(165.dp)
                .background(Brush.horizontalGradient(listOf(ReportsNavy, ReportsNavy2)))
                .padding(18.dp)
        ) {
            Column {
                Text("Reports", color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(6.dp))
                Text(
                    "$sales total sales • $orders orders",
                    color = Color.White.copy(alpha = 0.72f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(16.dp))
                Surface(
                    modifier = Modifier.clickable(onClick = onRefresh),
                    color = Color.White.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text(
                        "Refresh reports",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ReportsMetricCard(
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
        border = BorderStroke(1.dp, ReportsBorder),
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
                Text(label, color = ReportsMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(value, color = ReportsText, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                Text(sub, color = subColor, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }
    }
}

@Composable
private fun ReportsRowsCard(
    title: String,
    @DrawableRes icon: Int,
    rows: List<ReportRowUi>
) {
    ReportsInfoCard(title, if (rows.isEmpty()) "No data available." else "${rows.size} row(s)") {
        if (rows.isNotEmpty()) {
            rows.forEach { row ->
                ReportsRowLine(
                    label = row.label,
                    value = "${row.count} / ${row.sales}"
                )
            }
        }
    }
}

@Composable
private fun ReportsRecentOrdersCard(orders: List<RecentReportOrderUi>) {
    ReportsInfoCard("Recent Orders", if (orders.isEmpty()) "No recent orders available." else "${orders.size} recent order(s)") {
        orders.forEach { order ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFFF8FAFC),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, ReportsBorder)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        order.orderNumber.ifBlank { "-" },
                        color = ReportsText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1
                    )
                    Text(
                        order.productName.ifBlank { "-" },
                        color = ReportsMuted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${order.status.ifBlank { "-" }} • ${order.totalAmount.ifBlank { "0.00" }}",
                        color = ReportsBlue,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }
    }
}

@Composable
private fun ReportsInfoCard(
    title: String,
    message: String,
    content: @Composable (() -> Unit)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, ReportsBorder),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, color = ReportsText, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            Text(message, color = ReportsMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            content?.invoke()
        }
    }
}

@Composable
private fun ReportsRowLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            label.ifBlank { "-" },
            color = ReportsMuted,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            value,
            color = ReportsText,
            fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1
        )
    }
}

@Composable
private fun ReportsBottomNav(selected: ReportsTab, onClick: (ReportsTab) -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        color = Color.White,
        shadowElevation = 14.dp,
        border = BorderStroke(1.dp, ReportsBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(66.dp)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ReportsBottomItem(Icons.Default.GridView, "Dashboard", selected == ReportsTab.Dashboard) { onClick(ReportsTab.Dashboard) }
            ReportsBottomItem(Icons.Default.People, "Partners", selected == ReportsTab.Partners) { onClick(ReportsTab.Partners) }
            ReportsBottomItem(Icons.Default.ShoppingCart, "Orders", selected == ReportsTab.Orders) { onClick(ReportsTab.Orders) }
            ReportsBottomItem(Icons.Default.CreditCard, "Pricing", selected == ReportsTab.Pricing) { onClick(ReportsTab.Pricing) }
            ReportsBottomItem(Icons.Default.MoreHoriz, "More", selected == ReportsTab.More) { onClick(ReportsTab.More) }
        }
    }
}

@Composable
private fun ReportsBottomItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val color = if (selected) ReportsBlue else ReportsMuted
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
