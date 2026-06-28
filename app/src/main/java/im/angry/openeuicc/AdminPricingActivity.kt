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
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.People
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
import im.angry.openeuicc.ui.compose.saas.R2wActionCard
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

class AdminPricingActivity : ComponentActivity() {
    private val tokenStore by lazy { AuthTokenStore(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val scope = rememberCoroutineScope()
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
                AdminPricingSaasScreen(
                    loading = loading,
                    errorMessage = errorMessage,
                    pricingItems = pricingItems,
                    query = query,
                    statusFilter = statusFilter,
                    providerFilter = providerFilter,
                    onQueryChange = { query = it },
                    onStatusFilterChange = { statusFilter = it },
                    onProviderFilterChange = { providerFilter = it },
                    onRefresh = { scope.launch { loadPricing() } },
                    onProviderMarkups = {
                        startActivity(Intent(this@AdminPricingActivity, AdminProviderMarkupsActivity::class.java))
                    },
                    onBottomNavClick = { item ->
                        when (item) {
                            R2wSaasNavItem.Dashboard -> startActivity(Intent(this@AdminPricingActivity, MobileAdminActivity::class.java))
                            R2wSaasNavItem.Partners -> startActivity(Intent(this@AdminPricingActivity, AdminPartnersActivity::class.java))
                            R2wSaasNavItem.Orders -> startActivity(Intent(this@AdminPricingActivity, AdminOrdersOverviewActivity::class.java))
                            R2wSaasNavItem.Pricing -> Unit
                            R2wSaasNavItem.More -> startActivity(Intent(this@AdminPricingActivity, AdminMoreActivity::class.java))
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

            val provider = item.optString("provider", "-")
            val dataVolume = item.optString("data_volume", "")
            val validityDays = item.optInt("validity_days", 0)

            list.add(
                AdminPricingUi(
                    rawJson = item.toString(),
                    name = pricingPackageTitle(
                        provider = provider,
                        originalName = item.optString("name", "-"),
                        dataVolume = dataVolume,
                        validityDays = validityDays
                    ),
                    country = item.optString("country", "-"),
                    region = item.optString("region", ""),
                    provider = provider,
                    providerLabel = pricingProviderLabel(provider),
                    dataVolume = dataVolume.ifBlank { "-" },
                    validityDays = validityDays,
                    basePrice = item.optString("base_price", "0.00"),
                    dealerPrice = item.optString(
                        "dealer_price",
                        item.optString("dealerPrice", item.optString("reseller_price", "0.00"))
                    ),
                    resellerPrice = item.optString("reseller_price", "0.00"),
                    markup = item.optString("markup_percentage", "0.00"),
                    resellerMarkup = item.optString(
                        "applied_reseller_markup",
                        item.optString("markup_percentage", "0.00")
                    ),
                    dealerMarkup = item.optString(
                        "applied_dealer_markup",
                        item.optString("markup_percentage", "0.00")
                    ),
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
    val providerLabel: String,
    val dataVolume: String,
    val validityDays: Int,
    val basePrice: String,
    val resellerPrice: String,
    val dealerPrice: String,
    val markup: String,
    val resellerMarkup: String,
    val dealerMarkup: String,
    val active: Boolean,
    val status: String
)

@Composable
private fun AdminPricingSaasScreen(
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
    onBottomNavClick: (R2wSaasNavItem) -> Unit
) {
    val providers = remember(pricingItems) {
        pricingItems
            .map { it.provider }
            .filter { it.isNotBlank() && it != "-" }
            .distinctBy { it.lowercase() }
            .take(10)
    }

    val filtered by remember(pricingItems, query, statusFilter, providerFilter) {
        derivedStateOf {
            val cleanQuery = query.trim().lowercase()

            pricingItems
                .filter { item ->
                    statusFilter == "all" || item.status.lowercase() == statusFilter
                }
                .filter { item ->
                    providerFilter == "all" || item.provider.lowercase() == providerFilter.lowercase()
                }
                .filter { item ->
                    cleanQuery.isBlank() || listOf(
                        item.name,
                        item.country,
                        item.region,
                        item.provider,
                        item.providerLabel,
                        item.dataVolume,
                        item.status
                    ).joinToString(" ").lowercase().contains(cleanQuery)
                }
        }
    }

    val activeCount = pricingItems.count { it.active }
    val inactiveCount = pricingItems.count { !it.active }

    Scaffold(
        containerColor = R2wSaasColors.Background,
        bottomBar = {
            R2wSaasBottomNav(
                selected = R2wSaasNavItem.Pricing,
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
                    title = "Package Pricing",
                    subtitle = "${filtered.size} visible of ${pricingItems.size} B2B packages.",
                    badge = if (loading) "Loading" else "Live API"
                )
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    R2wMetricCard(
                        modifier = Modifier.weight(1f),
                        title = "Active",
                        value = activeCount.toString(),
                        subtitle = "$inactiveCount inactive",
                        icon = Icons.Default.CreditCard,
                        tint = R2wSaasColors.Green
                    )

                    R2wMetricCard(
                        modifier = Modifier.weight(1f),
                        title = "Providers",
                        value = providers.size.toString(),
                        subtitle = "connected",
                        icon = Icons.Default.People,
                        tint = R2wSaasColors.Primary
                    )
                }
            }

            item {
                R2wActionCard(
                    title = "Provider Markups",
                    subtitle = "Edit reseller and dealer markup defaults",
                    icon = Icons.Default.ShoppingCart,
                    onClick = onProviderMarkups,
                    tint = R2wSaasColors.Orange
                )
            }

            item {
                PricingSearchAndFilters(
                    query = query,
                    statusFilter = statusFilter,
                    providerFilter = providerFilter,
                    providers = providers,
                    onQueryChange = onQueryChange,
                    onStatusFilterChange = onStatusFilterChange,
                    onProviderFilterChange = onProviderFilterChange,
                    onRefresh = onRefresh
                )
            }

            if (errorMessage != null) {
                item {
                    R2wSaasCard {
                        Text(
                            text = "Could not load pricing",
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
                                text = "Loading pricing...",
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
                            text = "No pricing found",
                            color = R2wSaasColors.Text,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Black
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Try another search, provider or status filter.",
                            color = R2wSaasColors.Muted,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            } else {
                items(filtered.size) { index ->
                    PricingSaasCard(item = filtered[index])
                }
            }

            item { Spacer(Modifier.height(18.dp)) }
        }
    }
}

@Composable
private fun PricingSearchAndFilters(
    query: String,
    statusFilter: String,
    providerFilter: String,
    providers: List<String>,
    onQueryChange: (String) -> Unit,
    onStatusFilterChange: (String) -> Unit,
    onProviderFilterChange: (String) -> Unit,
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
            placeholder = { Text("Search packages...") },
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
            PricingChip("all", "All", statusFilter, onStatusFilterChange)
            PricingChip("active", "Active", statusFilter, onStatusFilterChange)
            PricingChip("inactive", "Inactive", statusFilter, onStatusFilterChange)

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

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ProviderChip("all", "All Providers", providerFilter, onProviderFilterChange)

            providers.forEach { provider ->
                ProviderChip(
                    key = provider,
                    label = pricingProviderLabel(provider),
                    selected = providerFilter,
                    onClick = onProviderFilterChange
                )
            }
        }
    }
}

@Composable
private fun PricingChip(
    key: String,
    label: String,
    selected: String,
    onClick: (String) -> Unit
) {
    val isSelected = selected == key

    AssistChip(
        onClick = { onClick(key) },
        label = { Text(label, fontWeight = FontWeight.ExtraBold) },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = if (isSelected) R2wSaasColors.PrimarySoft else R2wSaasColors.Card,
            labelColor = if (isSelected) R2wSaasColors.Primary else R2wSaasColors.Muted
        ),
        border = BorderStroke(1.dp, R2wSaasColors.Border)
    )
}

@Composable
private fun ProviderChip(
    key: String,
    label: String,
    selected: String,
    onClick: (String) -> Unit
) {
    val isSelected = selected.equals(key, ignoreCase = true)

    AssistChip(
        onClick = { onClick(key) },
        label = { Text(label, fontWeight = FontWeight.ExtraBold) },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = if (isSelected) R2wSaasColors.PrimarySoft else R2wSaasColors.Card,
            labelColor = if (isSelected) R2wSaasColors.Primary else R2wSaasColors.Muted
        ),
        border = BorderStroke(1.dp, R2wSaasColors.Border)
    )
}

@Composable
private fun PricingSaasCard(item: AdminPricingUi) {
    R2wSaasCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Surface(
                shape = RoundedCornerShape(17.dp),
                color = providerTint(item.provider).copy(alpha = 0.10f),
                border = BorderStroke(1.dp, R2wSaasColors.Border)
            ) {
                Icon(
                    imageVector = Icons.Default.CreditCard,
                    contentDescription = null,
                    tint = providerTint(item.provider),
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
                            text = item.name,
                            color = R2wSaasColors.Text,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(Modifier.height(4.dp))

                        ProviderLabelPill(
                            text = item.providerLabel,
                            color = providerTint(item.provider)
                        )
                    }

                    PricingStatusPill(
                        item.status,
                        if (item.active) R2wSaasColors.Green else R2wSaasColors.Orange
                    )
                }

                Spacer(Modifier.height(10.dp))

                Text(
                    text = listOf(item.country, item.region)
                        .filter { it.isNotBlank() && it != "-" }
                        .joinToString(" • ")
                        .ifBlank { "Global package" },
                    color = R2wSaasColors.Muted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(10.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PriceMiniStat(
                        title = "Cost",
                        value = moneyValue(item.basePrice),
                        modifier = Modifier.weight(1f)
                    )

                    PriceMiniStat(
                        title = "Dealer",
                        value = moneyValue(item.dealerPrice),
                        modifier = Modifier.weight(1f)
                    )

                    PriceMiniStat(
                        title = "Reseller",
                        value = moneyValue(item.resellerPrice),
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PriceMiniStat(
                        title = "Data",
                        value = item.dataVolume,
                        modifier = Modifier.weight(1f)
                    )

                    PriceMiniStat(
                        title = "Validity",
                        value = if (item.validityDays > 0) "${item.validityDays}d" else "-",
                        modifier = Modifier.weight(1f)
                    )

                    PriceMiniStat(
                        title = "Margin",
                        value = "${item.resellerMarkup}%",
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(Modifier.height(9.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Dealer markup ${item.dealerMarkup}%",
                        color = R2wSaasColors.Muted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text = "View ›",
                        color = R2wSaasColors.Primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }
    }
}

@Composable
private fun ProviderLabelPill(
    text: String,
    color: Color
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.16f))
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun PricingStatusPill(
    text: String,
    color: Color
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.18f))
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
            maxLines = 1
        )
    }
}

@Composable
private fun PriceMiniStat(
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

private fun providerTint(provider: String): Color {
    return when (provider.lowercase().trim()) {
        "tgt" -> R2wSaasColors.Orange
        "esimcard", "orange" -> R2wSaasColors.Primary
        "airhub", "airhubapp", "vodafone" -> R2wSaasColors.Red
        "flexnet", "masmovil", "mas movil" -> R2wSaasColors.Purple
        "traveroam", "travelroam", "roam2world" -> R2wSaasColors.Green
        else -> R2wSaasColors.Primary
    }
}

private fun pricingProviderLabel(provider: String): String {
    return when (provider.lowercase().trim()) {
        "traveroam", "travelroam", "roam2world" -> "TravelRoam"
        "tgt" -> "Orange Balkans"
        "flexnet", "masmovil", "mas movil" -> "Orange Big Data"
        "esimcard", "orange" -> "Orange World"
        "airhub", "airhubapp", "vodafone" -> "Vodafone"
        else -> provider.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }
}

private fun pricingPackageTitle(
    provider: String,
    originalName: String,
    dataVolume: String,
    validityDays: Int
): String {
    val cleanName = originalName
        .replace("eSIM", "", ignoreCase = true)
        .replace("Data For", "", ignoreCase = true)
        .replace("Data", "", ignoreCase = true)
        .replace("Unthrottled", "", ignoreCase = true)
        .replace("V2", "", ignoreCase = true)
        .replace(Regex("\\s+"), " ")
        .trim()
        .ifBlank { originalName }

    val providerLabel = pricingProviderLabel(provider)
    val volume = dataVolume.ifBlank { "" }
    val validity = if (validityDays > 0) "$validityDays Days" else ""
    val suffix = listOf(volume, validity).filter { it.isNotBlank() }.joinToString(" / ")

    return if (suffix.isNotBlank()) {
        "$providerLabel - $suffix"
    } else {
        "$providerLabel - $cleanName"
    }
}

private fun moneyValue(value: String): String {
    val clean = value.trim()
    if (clean.isBlank()) return "$0.00"
    return if (clean.startsWith("$") || clean.contains("€")) clean else "$" + clean
}
