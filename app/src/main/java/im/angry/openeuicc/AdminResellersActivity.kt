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

private val ResellerBg = Color(0xFFF6F8FC)
private val ResellerNavy = Color(0xFF061A3F)
private val ResellerNavy2 = Color(0xFF123EAD)
private val ResellerBlue = Color(0xFF1263F1)
private val ResellerText = Color(0xFF101828)
private val ResellerMuted = Color(0xFF667085)
private val ResellerBorder = Color(0xFFE1E8F2)
private val ResellerGreen = Color(0xFF16A34A)
private val ResellerOrange = Color(0xFFF97316)
private val ResellerRed = Color(0xFFEF4444)

class AdminResellersActivity : ComponentActivity() {
    private val tokenStore by lazy { AuthTokenStore(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val composeScope = rememberCoroutineScope()
            var loading by remember { mutableStateOf(true) }
            var errorMessage by remember { mutableStateOf<String?>(null) }
            var resellers by remember { mutableStateOf<List<AdminResellerUi>>(emptyList()) }
            var query by remember { mutableStateOf("") }
            var selectedStatus by remember { mutableStateOf("all") }

            suspend fun loadResellers() {
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
                        val response = fetchAdminResellers(session.authorizationHeader)
                        parseResellers(response)
                    }
                }

                result
                    .onSuccess { resellers = it }
                    .onFailure {
                        errorMessage = it.message ?: "Resellers API error"
                        Toast.makeText(this@AdminResellersActivity, errorMessage, Toast.LENGTH_LONG).show()
                    }

                loading = false
            }

            LaunchedEffect(Unit) {
                loadResellers()
            }

            R2WTheme {
                AdminResellersListScreen(
                    loading = loading,
                    errorMessage = errorMessage,
                    resellers = resellers,
                    query = query,
                    selectedStatus = selectedStatus,
                    onQueryChange = { query = it },
                    onStatusChange = { selectedStatus = it },
                    onRefresh = {
                        composeScope.launch {
                            loadResellers()
                        }
                    },
                    onOpenReseller = { reseller ->
                        startActivity(
                            Intent(this@AdminResellersActivity, AdminResellerDetailActivity::class.java).apply {
                                putExtra(AdminResellerDetailActivity.EXTRA_RESELLER_JSON, reseller.rawJson)
                            }
                        )
                    },
                    onBottomNavClick = { tab ->
                        when (tab) {
                            PartnerBottomTab.Dashboard -> startActivity(Intent(this@AdminResellersActivity, MobileAdminActivity::class.java))
                            PartnerBottomTab.Partners -> startActivity(Intent(this@AdminResellersActivity, AdminPartnersActivity::class.java))
                            PartnerBottomTab.Orders -> startActivity(Intent(this@AdminResellersActivity, AdminOrdersOverviewActivity::class.java))
                            PartnerBottomTab.Pricing -> startActivity(Intent(this@AdminResellersActivity, AdminPricingOverviewActivity::class.java))
                            PartnerBottomTab.More -> startActivity(Intent(this@AdminResellersActivity, AdminMoreActivity::class.java))
                        }
                    }
                )
            }
        }
    }

    private fun fetchAdminResellers(authorizationHeader: String): JSONObject {
        val url = URL("https://roam2world-panels-backend.onrender.com/api/v1/mobile/admin/resellers/")
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

    private fun parseResellers(response: JSONObject): List<AdminResellerUi> {
        val data = response.optJSONObject("data") ?: response
        val arr = data.optJSONArray("resellers") ?: JSONArray()
        val list = mutableListOf<AdminResellerUi>()

        for (i in 0 until arr.length()) {
            val reseller = arr.optJSONObject(i) ?: continue
            val active = reseller.optBoolean("is_active", false)
            val suspended = reseller.optBoolean("is_suspended", false)
            val status = when {
                suspended -> "Suspended"
                active -> "Active"
                else -> "Inactive"
            }

            list.add(
                AdminResellerUi(
                    rawJson = reseller.toString(),
                    id = reseller.optString("id", reseller.optString("user_id", "")),
                    name = reseller.optString("name", "-"),
                    email = reseller.optString("email", "-"),
                    credit = reseller.optString("current_credit", "0.00"),
                    creditLimit = reseller.optString("credit_limit", "0.00"),
                    monthlySpent = reseller.optString("current_month_spent", "0.00"),
                    monthlyLimit = reseller.optString("monthly_spend_limit", "0.00"),
                    markup = reseller.optString("markup_percentage", "0.00"),
                    active = active,
                    suspended = suspended,
                    status = status
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

private data class AdminResellerUi(
    val rawJson: String,
    val id: String,
    val name: String,
    val email: String,
    val credit: String,
    val creditLimit: String,
    val monthlySpent: String,
    val monthlyLimit: String,
    val markup: String,
    val active: Boolean,
    val suspended: Boolean,
    val status: String
)

private enum class PartnerBottomTab {
    Dashboard,
    Partners,
    Orders,
    Pricing,
    More
}

@Composable
private fun AdminResellersListScreen(
    loading: Boolean,
    errorMessage: String?,
    resellers: List<AdminResellerUi>,
    query: String,
    selectedStatus: String,
    onQueryChange: (String) -> Unit,
    onStatusChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onOpenReseller: (AdminResellerUi) -> Unit,
    onBottomNavClick: (PartnerBottomTab) -> Unit
) {
    val filtered by remember(resellers, query, selectedStatus) {
        derivedStateOf {
            val cleanQuery = query.trim().lowercase()
            resellers
                .filter { selectedStatus == "all" || it.status.lowercase() == selectedStatus }
                .filter { reseller ->
                    cleanQuery.isBlank() || listOf(
                        reseller.id,
                        reseller.name,
                        reseller.email,
                        reseller.credit,
                        reseller.creditLimit,
                        reseller.markup,
                        reseller.status
                    ).joinToString(" ").lowercase().contains(cleanQuery)
                }
        }
    }

    val active = resellers.count { it.active && !it.suspended }
    val suspended = resellers.count { it.suspended }

    Scaffold(
        containerColor = ResellerBg,
        bottomBar = {
            PartnerBottomNav(
                selected = PartnerBottomTab.Partners,
                onClick = onBottomNavClick
            )
        }
    ) { inner ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(ResellerBg)
                .padding(inner)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(Modifier.height(8.dp)) }

            item {
                PartnerListHero(
                    title = "Resellers",
                    subtitle = "${filtered.size} shown • ${resellers.size} total • $active active • $suspended suspended",
                    badge = "Live reseller accounts"
                )
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PartnerMetricCard(
                        modifier = Modifier.weight(1f),
                        icon = R.drawable.admin_icon_partners,
                        label = "Shown",
                        value = filtered.size.toString(),
                        sub = "${resellers.size} total",
                        subColor = ResellerBlue
                    )
                    PartnerMetricCard(
                        modifier = Modifier.weight(1f),
                        icon = R.drawable.admin_icon_health,
                        label = "Active",
                        value = active.toString(),
                        sub = "enabled",
                        subColor = ResellerGreen
                    )
                }
            }

            item {
                PartnerFilterCard(
                    query = query,
                    selectedStatus = selectedStatus,
                    placeholder = "Search reseller name, email, id",
                    onQueryChange = onQueryChange,
                    onStatusChange = onStatusChange,
                    onRefresh = onRefresh,
                    loading = loading
                )
            }

            if (loading) {
                item {
                    PartnerInfoCard("Loading Resellers", "Retrieving partner account status, balances, and markup data.") {
                        CircularProgressIndicator(color = ResellerBlue)
                    }
                }
            }

            if (!loading && errorMessage != null) {
                item {
                    PartnerInfoCard("Resellers unavailable", errorMessage)
                }
            }

            if (!loading && errorMessage == null && filtered.isEmpty()) {
                item {
                    PartnerInfoCard("No Matching Resellers", "Clear filters or search by reseller name, email, or account id.")
                }
            }

            items(filtered.size) { index ->
                ResellerListCard(
                    reseller = filtered[index],
                    onClick = { onOpenReseller(filtered[index]) }
                )
            }

            item { Spacer(Modifier.height(10.dp)) }
        }
    }
}

@Composable
private fun PartnerListHero(
    title: String,
    subtitle: String,
    badge: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = ResellerNavy),
        elevation = CardDefaults.cardElevation(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .background(Brush.horizontalGradient(listOf(ResellerNavy, ResellerNavy2)))
                .padding(18.dp)
        ) {
            Column {
                Text(
                    title,
                    color = Color.White,
                    fontSize = 27.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    subtitle,
                    color = Color.White.copy(alpha = 0.72f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    badge,
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
private fun PartnerMetricCard(
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
        border = BorderStroke(1.dp, ResellerBorder),
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
                Text(label, color = ResellerMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(value, color = ResellerText, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                Text(sub, color = subColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun PartnerFilterCard(
    query: String,
    selectedStatus: String,
    placeholder: String,
    onQueryChange: (String) -> Unit,
    onStatusChange: (String) -> Unit,
    onRefresh: () -> Unit,
    loading: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, ResellerBorder),
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
                label = { Text(placeholder) },
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
                    "active" to "Active",
                    "suspended" to "Suspended",
                    "inactive" to "Inactive"
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
                            labelColor = if (selected) ResellerBlue else ResellerMuted
                        ),
                        border = BorderStroke(1.dp, if (selected) ResellerBlue.copy(alpha = 0.35f) else ResellerBorder)
                    )
                }

                AssistChip(
                    onClick = onRefresh,
                    enabled = !loading,
                    label = { Text(if (loading) "Loading" else "Refresh", fontWeight = FontWeight.ExtraBold) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = Color(0xFFEAF2FF),
                        labelColor = ResellerBlue
                    ),
                    border = BorderStroke(1.dp, ResellerBlue.copy(alpha = 0.35f))
                )
            }
        }
    }
}

@Composable
private fun ResellerListCard(
    reseller: AdminResellerUi,
    onClick: () -> Unit
) {
    val statusColor = partnerStatusColor(reseller.status)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, ResellerBorder),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(R.drawable.admin_icon_partners),
                    contentDescription = null,
                    modifier = Modifier.size(42.dp)
                )

                Spacer(Modifier.size(12.dp))

                Column(Modifier.weight(1f)) {
                    Text(
                        reseller.name.ifBlank { "-" },
                        color = ResellerText,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        reseller.email.ifBlank { "-" },
                        color = ResellerMuted,
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
                        reseller.status,
                        color = statusColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PartnerMiniStat(
                    modifier = Modifier.weight(1f),
                    label = "Credit",
                    value = reseller.credit
                )
                PartnerMiniStat(
                    modifier = Modifier.weight(1f),
                    label = "Markup",
                    value = "${reseller.markup}%"
                )
            }

            Text(
                "Monthly Spend: ${reseller.monthlySpent} / ${reseller.monthlyLimit}",
                color = ResellerMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                "Open reseller details",
                color = ResellerBlue,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

@Composable
private fun PartnerMiniStat(
    modifier: Modifier,
    label: String,
    value: String
) {
    Surface(
        modifier = modifier,
        color = Color(0xFFF8FAFC),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, ResellerBorder)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(label, color = ResellerMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text(value.ifBlank { "0.00" }, color = ResellerText, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        }
    }
}

@Composable
private fun PartnerInfoCard(
    title: String,
    message: String,
    content: @Composable (() -> Unit)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, ResellerBorder),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(title, color = ResellerText, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            Text(message, color = ResellerMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            content?.invoke()
        }
    }
}

private fun partnerStatusColor(status: String): Color {
    val clean = status.lowercase()
    return when {
        clean.contains("active") && !clean.contains("inactive") -> ResellerGreen
        clean.contains("suspend") -> ResellerRed
        clean.contains("inactive") -> ResellerOrange
        else -> ResellerMuted
    }
}

@Composable
private fun PartnerBottomNav(
    selected: PartnerBottomTab,
    onClick: (PartnerBottomTab) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        color = Color.White,
        shadowElevation = 14.dp,
        border = BorderStroke(1.dp, ResellerBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(66.dp)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            PartnerBottomItem(Icons.Default.GridView, "Dashboard", selected == PartnerBottomTab.Dashboard) { onClick(PartnerBottomTab.Dashboard) }
            PartnerBottomItem(Icons.Default.People, "Partners", selected == PartnerBottomTab.Partners) { onClick(PartnerBottomTab.Partners) }
            PartnerBottomItem(Icons.Default.ShoppingCart, "Orders", selected == PartnerBottomTab.Orders) { onClick(PartnerBottomTab.Orders) }
            PartnerBottomItem(Icons.Default.CreditCard, "Pricing", selected == PartnerBottomTab.Pricing) { onClick(PartnerBottomTab.Pricing) }
            PartnerBottomItem(Icons.Default.MoreHoriz, "More", selected == PartnerBottomTab.More) { onClick(PartnerBottomTab.More) }
        }
    }
}

@Composable
private fun PartnerBottomItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val color = if (selected) ResellerBlue else ResellerMuted
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
