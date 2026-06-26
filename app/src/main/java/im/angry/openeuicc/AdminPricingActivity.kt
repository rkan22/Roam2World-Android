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

private val PricingBg = Color(0xFFF6F8FC)
private val PricingNavy = Color(0xFF061A3F)
private val PricingNavy2 = Color(0xFF123EAD)
private val PricingBlue = Color(0xFF1263F1)
private val PricingText = Color(0xFF101828)
private val PricingMuted = Color(0xFF667085)
private val PricingBorder = Color(0xFFE1E8F2)
private val PricingGreen = Color(0xFF16A34A)
private val PricingOrange = Color(0xFFF97316)
private val PricingRed = Color(0xFFEF4444)

class AdminPricingActivity : ComponentActivity() {
    private val tokenStore by lazy { AuthTokenStore(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val composeScope = rememberCoroutineScope()
            var loading by remember { mutableStateOf(true) }
            var errorMessage by remember { mutableStateOf<String?>(null) }
            var pricingItems by remember { mutableStateOf<List<AdminPricingUi>>(emptyList()) }
            var query by remember { mutableStateOf("") }
            var statusFilter by remember { mutableStateOf("all") }
            var providerFilter by remember { mutableStateOf("all") }

            suspend fun loadPricing() {
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
                        val response = fetchAdminPricing(session.authorizationHeader)
                        parsePricing(response)
                    }
                }

                result
                    .onSuccess { pricingItems = it }
                    .onFailure {
                        errorMessage = it.message ?: "Pricing API error"
                        Toast.makeText(this@AdminPricingActivity, errorMessage, Toast.LENGTH_LONG).show()
                    }

                loading = false
            }

            LaunchedEffect(Unit) {
                loadPricing()
            }

            R2WTheme {
                AdminPricingScreen(
                    loading = loading,
                    errorMessage = errorMessage,
                    pricingItems = pricingItems,
                    query = query,
                    statusFilter = statusFilter,
                    providerFilter = providerFilter,
                    onQueryChange = { query = it },
                    onStatusFilterChange = { statusFilter = it },
                    onProviderFilterChange = { providerFilter = it },
                    onRefresh = {
                        composeScope.launch {
                            loadPricing()
                        }
                    },
                    onProviderMarkups = {
                        startActivity(Intent(this@AdminPricingActivity, AdminProviderMarkupsActivity::class.java))
                    },
                    onBottomNavClick = { tab ->
                        when (tab) {
                            PricingTab.Dashboard -> startActivity(Intent(this@AdminPricingActivity, MobileAdminActivity::class.java))
                            PricingTab.Partners -> startActivity(Intent(this@AdminPricingActivity, AdminPartnersActivity::class.java))
                            PricingTab.Orders -> startActivity(Intent(this@AdminPricingActivity, AdminOrdersOverviewActivity::class.java))
                            PricingTab.Pricing -> Unit
                            PricingTab.More -> startActivity(Intent(this@AdminPricingActivity, AdminMoreActivity::class.java))
                        }
                    }
                )
            }
        }
    }

    private fun fetchAdminPricing(authorizationHeader: String): JSONObject {
        val url = URL("https://roam2world-panels-backend.onrender.com/api/v1/mobile/admin/pricing/")
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

    private fun parsePricing(response: JSONObject): List<AdminPricingUi> {
        val data = response.optJSONObject("data") ?: response
        val pricing = data.optJSONArray("pricing") ?: JSONArray()
        val list = mutableListOf<AdminPricingUi>()

        for (i in 0 until pricing.length()) {
            val item = pricing.optJSONObject(i) ?: continue
            val active = item.optBoolean("is_active", false)

            list.add(
                AdminPricingUi(
                    rawJson = item.toString(),
                    name = item.optString("name", "-"),
                    country = item.optString("country", "-"),
                    region = item.optString("region", ""),
                    provider = item.optString("provider", "-"),
                    dataVolume = item.optString("data_volume", "-"),
                    validityDays = item.optInt("validity_days", 0),
                    basePrice = item.optString("base_price", "0.00"),
                    dealerPrice = item.optString("dealer_price", item.optString("dealerPrice", item.optString("reseller_price", "0.00"))),
                    resellerPrice = item.optString("reseller_price", "0.00"),
                    publicPrice = item.optString("public_price", "0.00"),
                    markup = item.optString("markup_percentage", "0.00"),
                    active = active,
                    status = if (active) "Active" else "Inactive"
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

private data class AdminPricingUi(
    val rawJson: String,
    val name: String,
    val country: String,
    val region: String,
    val provider: String,
    val dataVolume: String,
    val validityDays: Int,
    val basePrice: String,
    val resellerPrice: String,
    val dealerPrice: String,
    val publicPrice: String,
    val markup: String,
    val active: Boolean,
    val status: String
)

private enum class PricingTab {
    Dashboard,
    Partners,
    Orders,
    Pricing,
    More
}

@Composable
private fun AdminPricingScreen(
    loading: Boolean,
    errorMessage: String?,
    pricingItems: List<AdminPricingUi>,
    query: String,
    statusFilter: String,
    providerFilter: String,
    onQueryChange: (String) -> Unit,
    onStatusFilterChange: (String) -> Unit,
    onProviderFilterChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onProviderMarkups: () -> Unit,
    onBottomNavClick: (PricingTab) -> Unit
) {
    val filtered by remember(pricingItems, query, statusFilter, providerFilter) {
        derivedStateOf {
            val cleanQuery = query.trim().lowercase()

            pricingItems
                .filter { item ->
                    statusFilter == "all" || item.status.lowercase() == statusFilter
                }
                .filter { item ->
                    providerFilter == "all" || item.provider.lowercase() == providerFilter
                }
                .filter { item ->
                    cleanQuery.isBlank() || listOf(
                        item.name,
                        item.country,
                        item.region,
                        item.provider,
                        item.dataVolume,
                        item.status,
                        item.markup
                    ).joinToString(" ").lowercase().contains(cleanQuery)
                }
        }
    }

    val active = pricingItems.count { it.active }
    val inactive = pricingItems.count { !it.active }
    val providerCounts = pricingItems
        .groupingBy { it.provider.lowercase().ifBlank { "unknown" } }
        .eachCount()
    val fixedProviders = listOf("esimcard", "airhub", "flexnet", "tgt", "traveroam")
    val providerOptions = listOf("all") + (fixedProviders + providerCounts.keys).distinct().sorted()

    Scaffold(
        containerColor = PricingBg,
        bottomBar = {
            PricingBottomNav(
                selected = PricingTab.Pricing,
                onClick = onBottomNavClick
            )
        }
    ) { inner ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(PricingBg)
                .padding(inner)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(Modifier.height(8.dp)) }

            item {
                PricingHero(
                    shown = filtered.size.toString(),
                    total = pricingItems.size.toString(),
                    active = active.toString()
                )
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PricingMetricCard(
                        modifier = Modifier.weight(1f),
                        icon = R.drawable.admin_icon_tag,
                        label = "Active Plans",
                        value = active.toString(),
                        sub = "$inactive inactive",
                        subColor = PricingGreen
                    )
                    PricingMetricCard(
                        modifier = Modifier.weight(1f),
                        icon = R.drawable.admin_icon_money,
                        label = "Pricing",
                        value = pricingItems.size.toString(),
                        sub = "catalog items",
                        subColor = PricingBlue
                    )
                }
            }

            item {
                PricingActionCard(
                    title = "Provider Markups",
                    subtitle = "Default provider markup and scope rule controls",
                    onClick = onProviderMarkups
                )
            }

            item {
                PricingFilterCard(
                    query = query,
                    statusFilter = statusFilter,
                    providerFilter = providerFilter,
                    providerOptions = providerOptions,
                    providerCounts = providerCounts,
                    onQueryChange = onQueryChange,
                    onStatusFilterChange = onStatusFilterChange,
                    onProviderFilterChange = onProviderFilterChange,
                    onRefresh = onRefresh,
                    loading = loading
                )
            }

            if (loading) {
                item {
                    PricingInfoCard("Loading pricing", "Fetching provider plans and price overview.") {
                        CircularProgressIndicator(color = PricingBlue)
                    }
                }
            }

            if (!loading && errorMessage != null) {
                item {
                    PricingInfoCard("Pricing unavailable", errorMessage)
                }
            }

            if (!loading && errorMessage == null && filtered.isEmpty()) {
                item {
                    PricingInfoCard("No Matching Pricing", "Clear filters or search by plan, country, provider, region, or data volume.")
                }
            }

            items(filtered.size) { index ->
                PricingItemCard(filtered[index])
            }

            item { Spacer(Modifier.height(10.dp)) }
        }
    }
}

@Composable
private fun PricingHero(
    shown: String,
    total: String,
    active: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = PricingNavy),
        elevation = CardDefaults.cardElevation(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .background(Brush.horizontalGradient(listOf(PricingNavy, PricingNavy2)))
                .padding(18.dp)
        ) {
            Column {
                Text(
                    "Pricing",
                    color = Color.White,
                    fontSize = 27.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "$shown shown • $total total • $active active",
                    color = Color.White.copy(alpha = 0.72f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    "Provider plan pricing overview",
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
private fun PricingMetricCard(
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
        border = BorderStroke(1.dp, PricingBorder),
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
                Text(label, color = PricingMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(value, color = PricingText, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                Text(sub, color = subColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun PricingActionCard(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, PricingBorder),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(painterResource(R.drawable.admin_icon_tag), contentDescription = null, modifier = Modifier.size(44.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp)
            ) {
                Text(title, color = PricingText, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(4.dp))
                Text(subtitle, color = PricingMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }

            Surface(
                color = PricingOrange.copy(alpha = 0.12f),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    "Open",
                    color = PricingOrange,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                )
            }
        }
    }
}

private fun Int?.orZero(): Int = this ?: 0

@Composable
private fun PricingFilterCard(
    query: String,
    statusFilter: String,
    providerFilter: String,
    providerOptions: List<String>,
    providerCounts: Map<String, Int>,
    onQueryChange: (String) -> Unit,
    onStatusFilterChange: (String) -> Unit,
    onProviderFilterChange: (String) -> Unit,
    onRefresh: () -> Unit,
    loading: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, PricingBorder),
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
                label = { Text("Search plan, country, provider") },
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
                    "inactive" to "Inactive"
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
                            labelColor = if (selected) PricingBlue else PricingMuted
                        ),
                        border = BorderStroke(1.dp, if (selected) PricingBlue.copy(alpha = 0.35f) else PricingBorder)
                    )
                }

                AssistChip(
                    onClick = onRefresh,
                    enabled = !loading,
                    label = { Text(if (loading) "Loading" else "Refresh", fontWeight = FontWeight.ExtraBold) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = Color(0xFFEAF2FF),
                        labelColor = PricingBlue
                    ),
                    border = BorderStroke(1.dp, PricingBlue.copy(alpha = 0.35f))
                )
            }

            Text(
                text = "Provider",
                color = PricingMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                providerOptions.forEach { provider ->
                    val selected = providerFilter == provider
                    val count = if (provider == "all") providerCounts.values.sum() else providerCounts[provider].orZero()
                    val label = if (provider == "all") "All $count" else "${provider.uppercase()} $count"

                    AssistChip(
                        onClick = { onProviderFilterChange(provider) },
                        label = {
                            Text(
                                label,
                                fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = if (selected) Color(0xFFEAF2FF) else Color.White,
                            labelColor = if (selected) PricingBlue else PricingMuted
                        ),
                        border = BorderStroke(1.dp, if (selected) PricingBlue.copy(alpha = 0.35f) else PricingBorder)
                    )
                }
            }
        }
    }
}

@Composable
private fun PricingItemCard(item: AdminPricingUi) {
    val statusColor = if (item.active) PricingGreen else PricingRed

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, PricingBorder),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(R.drawable.admin_icon_tag),
                    contentDescription = null,
                    modifier = Modifier.size(42.dp)
                )

                Spacer(Modifier.size(12.dp))

                Column(Modifier.weight(1f)) {
                    Text(
                        item.name.ifBlank { "-" },
                        color = PricingText,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        listOf(item.country, item.region).filter { it.isNotBlank() && it != "-" }.joinToString(" • ").ifBlank { "-" },
                        color = PricingMuted,
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
                        item.status,
                        color = statusColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    PricingMiniStat(Modifier.weight(1f), "Cost", item.basePrice)
                    PricingMiniStat(Modifier.weight(1f), "Dealer", item.dealerPrice)
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    PricingMiniStat(Modifier.weight(1f), "Reseller", item.resellerPrice)
                    PricingMiniStat(Modifier.weight(1f), "Public", item.publicPrice)
                }
            }

            Text(
                "Provider: ${item.provider.ifBlank { "-" }} • Data: ${item.dataVolume.ifBlank { "-" }} / ${item.validityDays} days",
                color = PricingMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                "Markup: ${item.markup.ifBlank { "0.00" }}%",
                color = PricingBlue,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

@Composable
private fun PricingMiniStat(
    modifier: Modifier,
    label: String,
    value: String
) {
    Surface(
        modifier = modifier,
        color = Color(0xFFF8FAFC),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, PricingBorder)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(label, color = PricingMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text(value.ifBlank { "0.00" }, color = PricingText, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        }
    }
}

@Composable
private fun PricingInfoCard(
    title: String,
    message: String,
    content: @Composable (() -> Unit)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, PricingBorder),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(title, color = PricingText, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            Text(message, color = PricingMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            content?.invoke()
        }
    }
}

@Composable
private fun PricingBottomNav(
    selected: PricingTab,
    onClick: (PricingTab) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        color = Color.White,
        shadowElevation = 14.dp,
        border = BorderStroke(1.dp, PricingBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(66.dp)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            PricingBottomItem(Icons.Default.GridView, "Dashboard", selected == PricingTab.Dashboard) { onClick(PricingTab.Dashboard) }
            PricingBottomItem(Icons.Default.People, "Partners", selected == PricingTab.Partners) { onClick(PricingTab.Partners) }
            PricingBottomItem(Icons.Default.ShoppingCart, "Orders", selected == PricingTab.Orders) { onClick(PricingTab.Orders) }
            PricingBottomItem(Icons.Default.CreditCard, "Pricing", selected == PricingTab.Pricing) { onClick(PricingTab.Pricing) }
            PricingBottomItem(Icons.Default.MoreHoriz, "More", selected == PricingTab.More) { onClick(PricingTab.More) }
        }
    }
}

@Composable
private fun PricingBottomItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val color = if (selected) PricingBlue else PricingMuted
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
