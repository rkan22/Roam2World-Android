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

private val DealerBg = Color(0xFFF6F8FC)
private val DealerNavy = Color(0xFF061A3F)
private val DealerNavy2 = Color(0xFF123EAD)
private val DealerBlue = Color(0xFF1263F1)
private val DealerText = Color(0xFF101828)
private val DealerMuted = Color(0xFF667085)
private val DealerBorder = Color(0xFFE1E8F2)
private val DealerGreen = Color(0xFF16A34A)
private val DealerOrange = Color(0xFFF97316)
private val DealerRed = Color(0xFFEF4444)

class AdminDealersActivity : ComponentActivity() {
    private val tokenStore by lazy { AuthTokenStore(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val composeScope = rememberCoroutineScope()
            var loading by remember { mutableStateOf(true) }
            var errorMessage by remember { mutableStateOf<String?>(null) }
            var dealers by remember { mutableStateOf<List<AdminDealerUi>>(emptyList()) }
            var query by remember { mutableStateOf("") }
            var selectedStatus by remember { mutableStateOf("all") }

            suspend fun loadDealers() {
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
                        val response = fetchAdminDealers(session.authorizationHeader)
                        parseDealers(response)
                    }
                }

                result
                    .onSuccess { dealers = it }
                    .onFailure {
                        errorMessage = it.message ?: "Dealers API error"
                        Toast.makeText(this@AdminDealersActivity, errorMessage, Toast.LENGTH_LONG).show()
                    }

                loading = false
            }

            LaunchedEffect(Unit) {
                loadDealers()
            }

            R2WTheme {
                AdminDealersListScreen(
                    loading = loading,
                    errorMessage = errorMessage,
                    dealers = dealers,
                    query = query,
                    selectedStatus = selectedStatus,
                    onQueryChange = { query = it },
                    onStatusChange = { selectedStatus = it },
                    onRefresh = {
                        composeScope.launch {
                            loadDealers()
                        }
                    },
                    onOpenDealer = { dealer ->
                        startActivity(
                            Intent(this@AdminDealersActivity, AdminDealerDetailActivity::class.java).apply {
                                putExtra(AdminDealerDetailActivity.EXTRA_DEALER_JSON, dealer.rawJson)
                            }
                        )
                    },
                    onBottomNavClick = { tab ->
                        when (tab) {
                            DealerBottomTab.Dashboard -> startActivity(Intent(this@AdminDealersActivity, MobileAdminActivity::class.java))
                            DealerBottomTab.Partners -> startActivity(Intent(this@AdminDealersActivity, AdminPartnersActivity::class.java))
                            DealerBottomTab.Orders -> startActivity(Intent(this@AdminDealersActivity, AdminOrdersOverviewActivity::class.java))
                            DealerBottomTab.Pricing -> startActivity(Intent(this@AdminDealersActivity, AdminPricingOverviewActivity::class.java))
                            DealerBottomTab.More -> startActivity(Intent(this@AdminDealersActivity, AdminMoreActivity::class.java))
                        }
                    }
                )
            }
        }
    }

    private fun fetchAdminDealers(authorizationHeader: String): JSONObject {
        val url = URL("https://roam2world-panels-backend.onrender.com/api/v1/mobile/admin/dealers/")
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

    private fun parseDealers(response: JSONObject): List<AdminDealerUi> {
        val data = response.optJSONObject("data") ?: response
        val arr = data.optJSONArray("dealers") ?: JSONArray()
        val list = mutableListOf<AdminDealerUi>()

        for (i in 0 until arr.length()) {
            val dealer = arr.optJSONObject(i) ?: continue
            val active = dealer.optBoolean("is_active", false)
            val suspended = dealer.optBoolean("is_suspended", false)
            val status = when {
                suspended -> "Suspended"
                active -> "Active"
                else -> "Inactive"
            }

            list.add(
                AdminDealerUi(
                    rawJson = dealer.toString(),
                    id = dealer.optString("id", dealer.optString("user_id", "")),
                    name = dealer.optString("name", "-"),
                    email = dealer.optString("email", "-"),
                    balance = dealer.optString("current_balance", "0.00"),
                    totalAllocated = dealer.optString("total_allocated", "0.00"),
                    totalSpent = dealer.optString("total_spent", "0.00"),
                    markup = dealer.optString("markup_percentage", "0.00"),
                    resellerEmail = dealer.optString("reseller_email", "-"),
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

private data class AdminDealerUi(
    val rawJson: String,
    val id: String,
    val name: String,
    val email: String,
    val balance: String,
    val totalAllocated: String,
    val totalSpent: String,
    val markup: String,
    val resellerEmail: String,
    val active: Boolean,
    val suspended: Boolean,
    val status: String
)

private enum class DealerBottomTab {
    Dashboard,
    Partners,
    Orders,
    Pricing,
    More
}

@Composable
private fun AdminDealersListScreen(
    loading: Boolean,
    errorMessage: String?,
    dealers: List<AdminDealerUi>,
    query: String,
    selectedStatus: String,
    onQueryChange: (String) -> Unit,
    onStatusChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onOpenDealer: (AdminDealerUi) -> Unit,
    onBottomNavClick: (DealerBottomTab) -> Unit
) {
    val filtered by remember(dealers, query, selectedStatus) {
        derivedStateOf {
            val cleanQuery = query.trim().lowercase()
            dealers
                .filter { selectedStatus == "all" || it.status.lowercase() == selectedStatus }
                .filter { dealer ->
                    cleanQuery.isBlank() || listOf(
                        dealer.id,
                        dealer.name,
                        dealer.email,
                        dealer.resellerEmail,
                        dealer.balance,
                        dealer.markup,
                        dealer.status
                    ).joinToString(" ").lowercase().contains(cleanQuery)
                }
        }
    }

    val active = dealers.count { it.active && !it.suspended }
    val suspended = dealers.count { it.suspended }

    Scaffold(
        containerColor = DealerBg,
        bottomBar = {
            DealerBottomNav(
                selected = DealerBottomTab.Partners,
                onClick = onBottomNavClick
            )
        }
    ) { inner ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(DealerBg)
                .padding(inner)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(Modifier.height(8.dp)) }

            item {
                DealerListHero(
                    title = "Dealers",
                    subtitle = "${filtered.size} shown • ${dealers.size} total • $active active • $suspended suspended",
                    badge = "Live dealer accounts"
                )
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DealerMetricCard(
                        modifier = Modifier.weight(1f),
                        icon = R.drawable.admin_icon_user,
                        label = "Shown",
                        value = filtered.size.toString(),
                        sub = "${dealers.size} total",
                        subColor = DealerBlue
                    )
                    DealerMetricCard(
                        modifier = Modifier.weight(1f),
                        icon = R.drawable.admin_icon_health,
                        label = "Active",
                        value = active.toString(),
                        sub = "enabled",
                        subColor = DealerGreen
                    )
                }
            }

            item {
                DealerFilterCard(
                    query = query,
                    selectedStatus = selectedStatus,
                    placeholder = "Search dealer name, email, reseller",
                    onQueryChange = onQueryChange,
                    onStatusChange = onStatusChange,
                    onRefresh = onRefresh,
                    loading = loading
                )
            }

            if (loading) {
                item {
                    DealerInfoCard("Loading Dealers", "Retrieving dealer accounts, balances, and reseller relationships.") {
                        CircularProgressIndicator(color = DealerBlue)
                    }
                }
            }

            if (!loading && errorMessage != null) {
                item {
                    DealerInfoCard("Dealers unavailable", errorMessage)
                }
            }

            if (!loading && errorMessage == null && filtered.isEmpty()) {
                item {
                    DealerInfoCard("No Matching Dealers", "Clear filters or search by dealer name, dealer email, or reseller email.")
                }
            }

            items(filtered.size) { index ->
                DealerListCard(
                    dealer = filtered[index],
                    onClick = { onOpenDealer(filtered[index]) }
                )
            }

            item { Spacer(Modifier.height(10.dp)) }
        }
    }
}

@Composable
private fun DealerListHero(
    title: String,
    subtitle: String,
    badge: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = DealerNavy),
        elevation = CardDefaults.cardElevation(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .background(Brush.horizontalGradient(listOf(DealerNavy, DealerNavy2)))
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
private fun DealerMetricCard(
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
        border = BorderStroke(1.dp, DealerBorder),
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
                Text(label, color = DealerMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(value, color = DealerText, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                Text(sub, color = subColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun DealerFilterCard(
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
        border = BorderStroke(1.dp, DealerBorder),
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
                            labelColor = if (selected) DealerBlue else DealerMuted
                        ),
                        border = BorderStroke(1.dp, if (selected) DealerBlue.copy(alpha = 0.35f) else DealerBorder)
                    )
                }

                AssistChip(
                    onClick = onRefresh,
                    enabled = !loading,
                    label = { Text(if (loading) "Loading" else "Refresh", fontWeight = FontWeight.ExtraBold) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = Color(0xFFEAF2FF),
                        labelColor = DealerBlue
                    ),
                    border = BorderStroke(1.dp, DealerBlue.copy(alpha = 0.35f))
                )
            }
        }
    }
}

@Composable
private fun DealerListCard(
    dealer: AdminDealerUi,
    onClick: () -> Unit
) {
    val statusColor = dealerStatusColor(dealer.status)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, DealerBorder),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(R.drawable.admin_icon_user),
                    contentDescription = null,
                    modifier = Modifier.size(42.dp)
                )

                Spacer(Modifier.size(12.dp))

                Column(Modifier.weight(1f)) {
                    Text(
                        dealer.name.ifBlank { "-" },
                        color = DealerText,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        dealer.email.ifBlank { "-" },
                        color = DealerMuted,
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
                        dealer.status,
                        color = statusColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DealerMiniStat(
                    modifier = Modifier.weight(1f),
                    label = "Balance",
                    value = dealer.balance
                )
                DealerMiniStat(
                    modifier = Modifier.weight(1f),
                    label = "Markup",
                    value = "${dealer.markup}%"
                )
            }

            Text(
                "Parent Reseller: ${dealer.resellerEmail.ifBlank { "-" }}",
                color = DealerMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                "Allocated ${dealer.totalAllocated} • Spent ${dealer.totalSpent}",
                color = DealerMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                "Open dealer details",
                color = DealerBlue,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

@Composable
private fun DealerMiniStat(
    modifier: Modifier,
    label: String,
    value: String
) {
    Surface(
        modifier = modifier,
        color = Color(0xFFF8FAFC),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, DealerBorder)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(label, color = DealerMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text(value.ifBlank { "0.00" }, color = DealerText, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        }
    }
}

@Composable
private fun DealerInfoCard(
    title: String,
    message: String,
    content: @Composable (() -> Unit)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, DealerBorder),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(title, color = DealerText, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            Text(message, color = DealerMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            content?.invoke()
        }
    }
}

private fun dealerStatusColor(status: String): Color {
    val clean = status.lowercase()
    return when {
        clean.contains("active") && !clean.contains("inactive") -> DealerGreen
        clean.contains("suspend") -> DealerRed
        clean.contains("inactive") -> DealerOrange
        else -> DealerMuted
    }
}

@Composable
private fun DealerBottomNav(
    selected: DealerBottomTab,
    onClick: (DealerBottomTab) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        color = Color.White,
        shadowElevation = 14.dp,
        border = BorderStroke(1.dp, DealerBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(66.dp)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            DealerBottomItem(Icons.Default.GridView, "Dashboard", selected == DealerBottomTab.Dashboard) { onClick(DealerBottomTab.Dashboard) }
            DealerBottomItem(Icons.Default.People, "Partners", selected == DealerBottomTab.Partners) { onClick(DealerBottomTab.Partners) }
            DealerBottomItem(Icons.Default.ShoppingCart, "Orders", selected == DealerBottomTab.Orders) { onClick(DealerBottomTab.Orders) }
            DealerBottomItem(Icons.Default.CreditCard, "Pricing", selected == DealerBottomTab.Pricing) { onClick(DealerBottomTab.Pricing) }
            DealerBottomItem(Icons.Default.MoreHoriz, "More", selected == DealerBottomTab.More) { onClick(DealerBottomTab.More) }
        }
    }
}

@Composable
private fun DealerBottomItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val color = if (selected) DealerBlue else DealerMuted
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
