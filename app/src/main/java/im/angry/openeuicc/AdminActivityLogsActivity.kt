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
import im.angry.openeuicc.ui.compose.theme.R2WTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private val LogsBg = Color(0xFFF6F8FC)
private val LogsNavy = Color(0xFF061A3F)
private val LogsNavy2 = Color(0xFF123EAD)
private val LogsBlue = Color(0xFF1263F1)
private val LogsText = Color(0xFF101828)
private val LogsMuted = Color(0xFF667085)
private val LogsBorder = Color(0xFFE1E8F2)
private val LogsGreen = Color(0xFF16A34A)
private val LogsOrange = Color(0xFFF97316)
private val LogsRed = Color(0xFFEF4444)

class AdminActivityLogsActivity : ComponentActivity() {
    private val tokenStore by lazy { AuthTokenStore(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val composeScope = rememberCoroutineScope()
            var loading by remember { mutableStateOf(true) }
            var errorMessage by remember { mutableStateOf<String?>(null) }
            var logs by remember { mutableStateOf<List<AdminActivityLogUi>>(emptyList()) }
            var summary by remember { mutableStateOf(AdminActivitySummaryUi()) }
            var query by remember { mutableStateOf("") }
            var statusFilter by remember { mutableStateOf("all") }

            suspend fun loadActivityLogs() {
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
                        val response = fetchActivityLogs(session.authorizationHeader)
                        parseActivityResponse(response)
                    }
                }

                result
                    .onSuccess {
                        logs = it.logs
                        summary = it.summary
                    }
                    .onFailure {
                        errorMessage = it.message ?: "Activity Logs API error"
                        Toast.makeText(this@AdminActivityLogsActivity, errorMessage, Toast.LENGTH_LONG).show()
                    }

                loading = false
            }

            LaunchedEffect(Unit) {
                loadActivityLogs()
            }

            R2WTheme {
                AdminActivityLogsListScreen(
                    loading = loading,
                    errorMessage = errorMessage,
                    logs = logs,
                    summary = summary,
                    query = query,
                    statusFilter = statusFilter,
                    onQueryChange = { query = it },
                    onStatusFilterChange = { statusFilter = it },
                    onRefresh = {
                        composeScope.launch {
                            loadActivityLogs()
                        }
                    },
                    onOpenLog = { log ->
                        startActivity(
                            Intent(this@AdminActivityLogsActivity, AdminActivityLogDetailActivity::class.java).apply {
                                putExtra(AdminActivityLogDetailActivity.EXTRA_LOG_JSON, log.rawJson)
                            }
                        )
                    },
                    onBottomNavClick = { tab ->
                        when (tab) {
                            LogsTab.Dashboard -> startActivity(Intent(this@AdminActivityLogsActivity, MobileAdminActivity::class.java))
                            LogsTab.Partners -> startActivity(Intent(this@AdminActivityLogsActivity, AdminPartnersActivity::class.java))
                            LogsTab.Orders -> startActivity(Intent(this@AdminActivityLogsActivity, AdminOrdersOverviewActivity::class.java))
                            LogsTab.Pricing -> startActivity(Intent(this@AdminActivityLogsActivity, AdminPricingOverviewActivity::class.java))
                            LogsTab.More -> startActivity(Intent(this@AdminActivityLogsActivity, AdminMoreActivity::class.java))
                        }
                    }
                )
            }
        }
    }

    private fun fetchActivityLogs(authorizationHeader: String): JSONObject {
        val url = URL("https://roam2world-panels-backend.onrender.com/api/v1/mobile/admin/activity-logs/")
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

    private fun parseActivityResponse(response: JSONObject): AdminActivityResponseUi {
        val data = response.optJSONObject("data") ?: response
        val arr = data.optJSONArray("logs") ?: JSONArray()
        val list = mutableListOf<AdminActivityLogUi>()

        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue

            list.add(
                AdminActivityLogUi(
                    rawJson = item.toString(),
                    title = item.optString("title", "-"),
                    type = item.optString("type", "-"),
                    status = item.optString("status", "-"),
                    actor = item.optString("actor", "-"),
                    message = item.optString("message", "-"),
                    createdAt = item.optString("created_at", "-")
                )
            )
        }

        val rawSummary = data.optJSONObject("summary")
        val summary = AdminActivitySummaryUi(
            count = data.optInt("count", list.size),
            users = rawSummary?.optInt("users", 0) ?: 0,
            orders = rawSummary?.optInt("orders", 0) ?: 0,
            resellers = rawSummary?.optInt("resellers", 0) ?: 0,
            dealers = rawSummary?.optInt("dealers", 0) ?: 0,
            esims = rawSummary?.optInt("esims", 0) ?: 0,
            plans = rawSummary?.optInt("plans", 0) ?: 0
        )

        return AdminActivityResponseUi(logs = list, summary = summary)
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

private data class AdminActivityResponseUi(
    val logs: List<AdminActivityLogUi>,
    val summary: AdminActivitySummaryUi
)

private data class AdminActivitySummaryUi(
    val count: Int = 0,
    val users: Int = 0,
    val orders: Int = 0,
    val resellers: Int = 0,
    val dealers: Int = 0,
    val esims: Int = 0,
    val plans: Int = 0
)

private data class AdminActivityLogUi(
    val rawJson: String,
    val title: String,
    val type: String,
    val status: String,
    val actor: String,
    val message: String,
    val createdAt: String
)

private enum class LogsTab {
    Dashboard,
    Partners,
    Orders,
    Pricing,
    More
}

@Composable
private fun AdminActivityLogsListScreen(
    loading: Boolean,
    errorMessage: String?,
    logs: List<AdminActivityLogUi>,
    summary: AdminActivitySummaryUi,
    query: String,
    statusFilter: String,
    onQueryChange: (String) -> Unit,
    onStatusFilterChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onOpenLog: (AdminActivityLogUi) -> Unit,
    onBottomNavClick: (LogsTab) -> Unit
) {
    val filtered by remember(logs, query, statusFilter) {
        derivedStateOf {
            val cleanQuery = query.trim().lowercase()

            logs
                .filter { log ->
                    statusFilter == "all" || log.status.lowercase().contains(statusFilter)
                }
                .filter { log ->
                    cleanQuery.isBlank() || listOf(
                        log.title,
                        log.type,
                        log.status,
                        log.actor,
                        log.message,
                        log.createdAt
                    ).joinToString(" ").lowercase().contains(cleanQuery)
                }
        }
    }

    Scaffold(
        containerColor = LogsBg,
        bottomBar = {
            LogsBottomNav(
                selected = LogsTab.More,
                onClick = onBottomNavClick
            )
        }
    ) { inner ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(LogsBg)
                .padding(inner)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(Modifier.height(8.dp)) }

            item {
                LogsHero(
                    shown = filtered.size.toString(),
                    total = summary.count.toString()
                )
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LogsMetricCard(
                        modifier = Modifier.weight(1f),
                        icon = R.drawable.admin_icon_user,
                        label = "Users",
                        value = summary.users.toString(),
                        sub = "activity",
                        subColor = LogsBlue
                    )
                    LogsMetricCard(
                        modifier = Modifier.weight(1f),
                        icon = R.drawable.admin_icon_orders,
                        label = "Orders",
                        value = summary.orders.toString(),
                        sub = "events",
                        subColor = LogsGreen
                    )
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LogsMetricCard(
                        modifier = Modifier.weight(1f),
                        icon = R.drawable.admin_icon_partners,
                        label = "Partners",
                        value = (summary.resellers + summary.dealers).toString(),
                        sub = "resellers/dealers",
                        subColor = LogsOrange
                    )
                    LogsMetricCard(
                        modifier = Modifier.weight(1f),
                        icon = R.drawable.admin_icon_reports,
                        label = "Plans/eSIMs",
                        value = (summary.plans + summary.esims).toString(),
                        sub = "catalog events",
                        subColor = LogsMuted
                    )
                }
            }

            item {
                LogsFilterCard(
                    query = query,
                    statusFilter = statusFilter,
                    onQueryChange = onQueryChange,
                    onStatusFilterChange = onStatusFilterChange,
                    onRefresh = onRefresh,
                    loading = loading
                )
            }

            if (loading) {
                item {
                    LogsInfoCard("Loading activity logs", "Fetching recent admin and system activity.") {
                        CircularProgressIndicator(color = LogsBlue)
                    }
                }
            }

            if (!loading && errorMessage != null) {
                item {
                    LogsInfoCard("Activity logs unavailable", errorMessage)
                }
            }

            if (!loading && errorMessage == null && filtered.isEmpty()) {
                item {
                    LogsInfoCard("No Matching Logs", "Clear filters or search by title, type, actor, message, or status.")
                }
            }

            items(filtered.size) { index ->
                ActivityLogCard(
                    log = filtered[index],
                    onClick = { onOpenLog(filtered[index]) }
                )
            }

            item { Spacer(Modifier.height(10.dp)) }
        }
    }
}

@Composable
private fun LogsHero(
    shown: String,
    total: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = LogsNavy),
        elevation = CardDefaults.cardElevation(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .background(Brush.horizontalGradient(listOf(LogsNavy, LogsNavy2)))
                .padding(18.dp)
        ) {
            Column {
                Text(
                    "Activity Logs",
                    color = Color.White,
                    fontSize = 27.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "$shown shown • $total total events",
                    color = Color.White.copy(alpha = 0.72f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    "Admin & system audit trail",
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
private fun LogsMetricCard(
    modifier: Modifier,
    @DrawableRes icon: Int,
    label: String,
    value: String,
    sub: String,
    subColor: Color
) {
    Card(
        modifier = modifier.height(112.dp),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, LogsBorder),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Image(painterResource(icon), contentDescription = null, modifier = Modifier.size(34.dp))
            Column {
                Text(label, color = LogsMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(value, color = LogsText, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
                Text(sub, color = subColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun LogsFilterCard(
    query: String,
    statusFilter: String,
    onQueryChange: (String) -> Unit,
    onStatusFilterChange: (String) -> Unit,
    onRefresh: () -> Unit,
    loading: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, LogsBorder),
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
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                label = { Text("Search title, actor, message") },
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
                    "ok" to "OK",
                    "completed" to "Completed",
                    "pending" to "Pending",
                    "failed" to "Failed",
                    "error" to "Error"
                ).forEach { (value, label) ->
                    val selected = statusFilter == value
                    AssistChip(
                        onClick = { onStatusFilterChange(value) },
                        label = {
                            Text(
                                label,
                                fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = if (selected) Color(0xFFEAF2FF) else Color.White,
                            labelColor = if (selected) LogsBlue else LogsMuted
                        ),
                        border = BorderStroke(1.dp, if (selected) LogsBlue.copy(alpha = 0.35f) else LogsBorder)
                    )
                }

                AssistChip(
                    onClick = onRefresh,
                    enabled = !loading,
                    label = { Text(if (loading) "Loading" else "Refresh", fontWeight = FontWeight.ExtraBold) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = Color(0xFFEAF2FF),
                        labelColor = LogsBlue
                    ),
                    border = BorderStroke(1.dp, LogsBlue.copy(alpha = 0.35f))
                )
            }
        }
    }
}

@Composable
private fun ActivityLogCard(
    log: AdminActivityLogUi,
    onClick: () -> Unit
) {
    val statusColor = logStatusColor(log.status)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, LogsBorder),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(R.drawable.admin_icon_reports),
                    contentDescription = null,
                    modifier = Modifier.size(42.dp)
                )

                Spacer(Modifier.size(12.dp))

                Column(Modifier.weight(1f)) {
                    Text(
                        log.title.ifBlank { "-" },
                        color = LogsText,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        log.type.ifBlank { "-" },
                        color = LogsMuted,
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
                        log.status.ifBlank { "-" },
                        color = statusColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        maxLines = 1
                    )
                }
            }

            Text(
                log.message.ifBlank { "-" },
                color = LogsMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                "Actor: ${log.actor.ifBlank { "-" }} • Date: ${log.createdAt.ifBlank { "-" }}",
                color = LogsMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                "Tap to open details",
                color = LogsBlue,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

@Composable
private fun LogsInfoCard(
    title: String,
    message: String,
    content: @Composable (() -> Unit)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, LogsBorder),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(title, color = LogsText, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            Text(message, color = LogsMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            content?.invoke()
        }
    }
}

private fun logStatusColor(status: String): Color {
    val clean = status.lowercase()
    return when {
        clean.contains("completed") || clean.contains("resolved") || clean.contains("ok") -> LogsGreen
        clean.contains("pending") || clean.contains("progress") -> LogsOrange
        clean.contains("failed") || clean.contains("error") -> LogsRed
        else -> LogsMuted
    }
}

@Composable
private fun LogsBottomNav(
    selected: LogsTab,
    onClick: (LogsTab) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        color = Color.White,
        shadowElevation = 14.dp,
        border = BorderStroke(1.dp, LogsBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(66.dp)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            LogsBottomItem(Icons.Default.GridView, "Dashboard", selected == LogsTab.Dashboard) { onClick(LogsTab.Dashboard) }
            LogsBottomItem(Icons.Default.People, "Partners", selected == LogsTab.Partners) { onClick(LogsTab.Partners) }
            LogsBottomItem(Icons.Default.ShoppingCart, "Orders", selected == LogsTab.Orders) { onClick(LogsTab.Orders) }
            LogsBottomItem(Icons.Default.CreditCard, "Pricing", selected == LogsTab.Pricing) { onClick(LogsTab.Pricing) }
            LogsBottomItem(Icons.Default.MoreHoriz, "More", selected == LogsTab.More) { onClick(LogsTab.More) }
        }
    }
}

@Composable
private fun LogsBottomItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val color = if (selected) LogsBlue else LogsMuted
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
