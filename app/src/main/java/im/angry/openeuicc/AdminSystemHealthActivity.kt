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

private val HealthBg = Color(0xFFF6F8FC)
private val HealthNavy = Color(0xFF061A3F)
private val HealthNavy2 = Color(0xFF123EAD)
private val HealthBlue = Color(0xFF1263F1)
private val HealthText = Color(0xFF101828)
private val HealthMuted = Color(0xFF667085)
private val HealthBorder = Color(0xFFE2E8F0)
private val HealthGreen = Color(0xFF16A34A)
private val HealthOrange = Color(0xFFF97316)
private val HealthRed = Color(0xFFEF4444)

class AdminSystemHealthActivity : ComponentActivity() {
    private val tokenStore by lazy { AuthTokenStore(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val composeScope = rememberCoroutineScope()
            var loading by remember { mutableStateOf(true) }
            var errorMessage by remember { mutableStateOf<String?>(null) }
            var health by remember { mutableStateOf(AdminSystemHealthUi()) }

            suspend fun loadSystemHealth() {
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
                        val response = fetchSystemHealth(session.authorizationHeader)
                        parseSystemHealth(response)
                    }
                }

                result
                    .onSuccess { health = it }
                    .onFailure {
                        errorMessage = it.message ?: "System Health API error"
                        Toast.makeText(this@AdminSystemHealthActivity, errorMessage, Toast.LENGTH_LONG).show()
                    }

                loading = false
            }

            LaunchedEffect(Unit) {
                loadSystemHealth()
            }

            R2WTheme {
                AdminSystemHealthScreen(
                    loading = loading,
                    errorMessage = errorMessage,
                    health = health,
                    onRefresh = {
                        composeScope.launch {
                            loadSystemHealth()
                        }
                    },
                    onBottomNavClick = { tab ->
                        when (tab) {
                            HealthTab.Dashboard -> startActivity(Intent(this@AdminSystemHealthActivity, MobileAdminActivity::class.java))
                            HealthTab.Partners -> startActivity(Intent(this@AdminSystemHealthActivity, AdminPartnersActivity::class.java))
                            HealthTab.Orders -> startActivity(Intent(this@AdminSystemHealthActivity, AdminOrdersOverviewActivity::class.java))
                            HealthTab.Pricing -> startActivity(Intent(this@AdminSystemHealthActivity, AdminPricingOverviewActivity::class.java))
                            HealthTab.More -> startActivity(Intent(this@AdminSystemHealthActivity, AdminMoreActivity::class.java))
                        }
                    }
                )
            }
        }
    }

    private fun fetchSystemHealth(authorizationHeader: String): JSONObject {
        val url = URL("https://roam2world-panels-backend.onrender.com/api/v1/mobile/admin/system-health/")
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

    private fun parseSystemHealth(response: JSONObject): AdminSystemHealthUi {
        val data = response.optJSONObject("data") ?: response
        val counts = data.optJSONObject("counts") ?: JSONObject()

        return AdminSystemHealthUi(
            overallStatus = data.optString("overall_status", "-"),
            timestamp = data.optString("timestamp", "-"),
            counts = HealthCountsUi(
                users = counts.optInt("users", 0),
                resellers = counts.optInt("resellers", 0),
                dealers = counts.optInt("dealers", 0),
                orders = counts.optInt("orders", 0),
                esims = counts.optInt("esims", 0),
                plans = counts.optInt("plans", 0),
                activePlans = counts.optInt("active_plans", 0)
            ),
            checks = parseChecks(data.optJSONArray("checks"))
        )
    }

    private fun parseChecks(array: JSONArray?): List<HealthCheckUi> {
        if (array == null) return emptyList()
        val list = mutableListOf<HealthCheckUi>()

        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            list.add(
                HealthCheckUi(
                    name = item.optString("name", "-"),
                    status = item.optString("status", "-"),
                    message = item.optString("message", "")
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

private data class AdminSystemHealthUi(
    val overallStatus: String = "-",
    val timestamp: String = "-",
    val counts: HealthCountsUi = HealthCountsUi(),
    val checks: List<HealthCheckUi> = emptyList()
)

private data class HealthCountsUi(
    val users: Int = 0,
    val resellers: Int = 0,
    val dealers: Int = 0,
    val orders: Int = 0,
    val esims: Int = 0,
    val plans: Int = 0,
    val activePlans: Int = 0
)

private data class HealthCheckUi(
    val name: String,
    val status: String,
    val message: String
)

private enum class HealthTab {
    Dashboard,
    Partners,
    Orders,
    Pricing,
    More
}

@Composable
private fun AdminSystemHealthScreen(
    loading: Boolean,
    errorMessage: String?,
    health: AdminSystemHealthUi,
    onRefresh: () -> Unit,
    onBottomNavClick: (HealthTab) -> Unit
) {
    Scaffold(
        containerColor = HealthBg,
        bottomBar = {
            HealthBottomNav(
                selected = HealthTab.More,
                onClick = onBottomNavClick
            )
        }
    ) { inner ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(HealthBg)
                .padding(inner)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(Modifier.height(8.dp)) }

            item {
                HealthHero(
                    status = health.overallStatus,
                    timestamp = health.timestamp,
                    onRefresh = onRefresh
                )
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HealthMetricCard(
                        modifier = Modifier.weight(1f),
                        icon = R.drawable.admin_icon_user,
                        label = "Users",
                        value = health.counts.users.toString(),
                        sub = "${health.counts.resellers} resellers",
                        subColor = HealthBlue
                    )
                    HealthMetricCard(
                        modifier = Modifier.weight(1f),
                        icon = R.drawable.admin_icon_partners,
                        label = "Dealers",
                        value = health.counts.dealers.toString(),
                        sub = "partners",
                        subColor = HealthOrange
                    )
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HealthMetricCard(
                        modifier = Modifier.weight(1f),
                        icon = R.drawable.admin_icon_orders,
                        label = "Orders",
                        value = health.counts.orders.toString(),
                        sub = "total",
                        subColor = HealthGreen
                    )
                    HealthMetricCard(
                        modifier = Modifier.weight(1f),
                        icon = R.drawable.admin_icon_doc,
                        label = "eSIMs",
                        value = health.counts.esims.toString(),
                        sub = "inventory",
                        subColor = HealthBlue
                    )
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HealthMetricCard(
                        modifier = Modifier.weight(1f),
                        icon = R.drawable.admin_icon_tag,
                        label = "Plans",
                        value = health.counts.plans.toString(),
                        sub = "${health.counts.activePlans} active",
                        subColor = HealthGreen
                    )
                    HealthMetricCard(
                        modifier = Modifier.weight(1f),
                        icon = R.drawable.admin_icon_health,
                        label = "Checks",
                        value = health.checks.size.toString(),
                        sub = "system checks",
                        subColor = HealthOrange
                    )
                }
            }

            if (loading) {
                item {
                    HealthInfoCard("Loading system health", "Checking API, database, and operational status.") {
                        CircularProgressIndicator(color = HealthBlue)
                    }
                }
            }

            if (!loading && errorMessage != null) {
                item {
                    HealthInfoCard("System health unavailable", errorMessage)
                }
            }

            if (!loading && errorMessage == null) {
                item {
                    HealthInfoCard("Overall", "Current system state") {
                        HealthLine("Status", health.overallStatus)
                        HealthLine("Timestamp", health.timestamp)
                    }
                }

                item {
                    HealthChecksCard(health.checks)
                }
            }

            item { Spacer(Modifier.height(10.dp)) }
        }
    }
}

@Composable
private fun HealthHero(
    status: String,
    timestamp: String,
    onRefresh: () -> Unit
) {
    val statusColor = healthStatusColor(status)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = HealthNavy),
        elevation = CardDefaults.cardElevation(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(170.dp)
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF0B2B66), Color(0xFF1263F1))
                    )
                )
                .padding(18.dp)
        ) {
            Column {
                Text("System Health", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(6.dp))
                Text(
                    timestamp.ifBlank { "-" },
                    color = Color.White.copy(alpha = 0.72f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(14.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = statusColor.copy(alpha = 0.25f),
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f))
                    ) {
                        Text(
                            "Status: ${status.ifBlank { "-" }}",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                        )
                    }

                    Surface(
                        modifier = Modifier.clickable(onClick = onRefresh),
                        color = Color.White.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Text(
                            "Refresh",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HealthMetricCard(
    modifier: Modifier,
    @DrawableRes icon: Int,
    label: String,
    value: String,
    sub: String,
    subColor: Color
) {
    Card(
        modifier = modifier.height(108.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, HealthBorder),
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
                Text(label, color = HealthMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text(value, color = HealthText, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                Text(sub, color = subColor, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }
    }
}

@Composable
private fun HealthChecksCard(checks: List<HealthCheckUi>) {
    HealthInfoCard(
        title = "Checks",
        message = if (checks.isEmpty()) "No health checks available." else "${checks.size} check(s)"
    ) {
        checks.forEach { check ->
            val color = healthStatusColor(check.status)

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFFF6F8FC),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, HealthBorder)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            check.name.ifBlank { "-" },
                            color = HealthText,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Surface(
                            color = color.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text(
                                check.status.ifBlank { "-" },
                                color = color,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                maxLines = 1
                            )
                        }
                    }

                    Text(
                        check.message.ifBlank { "-" },
                        color = HealthMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun HealthInfoCard(
    title: String,
    message: String,
    content: @Composable (() -> Unit)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, HealthBorder),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, color = HealthText, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
            Text(message, color = HealthMuted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            content?.invoke()
        }
    }
}

@Composable
private fun HealthLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
        Text(
            label,
            color = HealthMuted,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(0.42f)
        )
        Text(
            value.ifBlank { "-" },
            color = HealthText,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(0.58f),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun healthStatusColor(status: String): Color {
    val clean = status.lowercase()
    return when {
        clean.contains("ok") || clean.contains("healthy") || clean.contains("active") || clean.contains("success") -> HealthGreen
        clean.contains("warning") || clean.contains("pending") || clean.contains("attention") -> HealthOrange
        clean.contains("error") || clean.contains("failed") || clean.contains("down") || clean.contains("critical") -> HealthRed
        else -> HealthMuted
    }
}

@Composable
private fun HealthBottomNav(
    selected: HealthTab,
    onClick: (HealthTab) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        color = Color.White,
        shadowElevation = 14.dp,
        border = BorderStroke(1.dp, HealthBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(66.dp)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            HealthBottomItem(Icons.Default.GridView, "Dashboard", selected == HealthTab.Dashboard) { onClick(HealthTab.Dashboard) }
            HealthBottomItem(Icons.Default.People, "Partners", selected == HealthTab.Partners) { onClick(HealthTab.Partners) }
            HealthBottomItem(Icons.Default.ShoppingCart, "Orders", selected == HealthTab.Orders) { onClick(HealthTab.Orders) }
            HealthBottomItem(Icons.Default.CreditCard, "Pricing", selected == HealthTab.Pricing) { onClick(HealthTab.Pricing) }
            HealthBottomItem(Icons.Default.MoreHoriz, "More", selected == HealthTab.More) { onClick(HealthTab.More) }
        }
    }
}

@Composable
private fun HealthBottomItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val color = if (selected) HealthBlue else HealthMuted
    val bg = if (selected) Color.White else Color.Transparent

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
