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
import androidx.compose.material.icons.filled.Storefront
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

class AdminResellersActivity : ComponentActivity() {
    private val tokenStore by lazy { AuthTokenStore(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val scope = rememberCoroutineScope()
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
                        scope.launch { loadResellers() }
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
                            R2wSaasNavItem.Dashboard -> startActivity(Intent(this@AdminResellersActivity, MobileAdminActivity::class.java))
                            R2wSaasNavItem.Partners -> startActivity(Intent(this@AdminResellersActivity, AdminPartnersActivity::class.java))
                            R2wSaasNavItem.Orders -> startActivity(Intent(this@AdminResellersActivity, AdminOrdersOverviewActivity::class.java))
                            R2wSaasNavItem.Pricing -> startActivity(Intent(this@AdminResellersActivity, AdminPricingOverviewActivity::class.java))
                            R2wSaasNavItem.More -> startActivity(Intent(this@AdminResellersActivity, AdminMoreActivity::class.java))
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
    onBottomNavClick: (R2wSaasNavItem) -> Unit
) {
    val filtered by remember(resellers, query, selectedStatus) {
        derivedStateOf {
            resellers.filter { reseller ->
                val matchesQuery = query.isBlank() ||
                    reseller.name.contains(query, ignoreCase = true) ||
                    reseller.email.contains(query, ignoreCase = true) ||
                    reseller.id.contains(query, ignoreCase = true)

                val matchesStatus = when (selectedStatus) {
                    "active" -> reseller.active && !reseller.suspended
                    "suspended" -> reseller.suspended
                    "inactive" -> !reseller.active && !reseller.suspended
                    else -> true
                }

                matchesQuery && matchesStatus
            }
        }
    }

    Scaffold(
        containerColor = R2wSaasColors.Background,
        bottomBar = {
            R2wSaasBottomNav(
                selected = R2wSaasNavItem.Partners,
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
                    title = "Resellers",
                    subtitle = "${filtered.size} visible of ${resellers.size} reseller accounts.",
                    badge = if (loading) "Loading" else "Live API"
                )
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    R2wMetricCard(
                        modifier = Modifier.weight(1f),
                        title = "Active",
                        value = resellers.count { it.active && !it.suspended }.toString(),
                        subtitle = "selling now",
                        icon = Icons.Default.People,
                        tint = R2wSaasColors.Green
                    )

                    R2wMetricCard(
                        modifier = Modifier.weight(1f),
                        title = "Suspended",
                        value = resellers.count { it.suspended }.toString(),
                        subtitle = "blocked",
                        icon = Icons.Default.Storefront,
                        tint = R2wSaasColors.Red
                    )
                }
            }

            item {
                ResellerSearchAndFilters(
                    query = query,
                    selectedStatus = selectedStatus,
                    onQueryChange = onQueryChange,
                    onStatusChange = onStatusChange,
                    onRefresh = onRefresh
                )
            }

            if (errorMessage != null) {
                item {
                    R2wSaasCard {
                        Text(
                            text = "Could not load resellers",
                            color = R2wSaasColors.Red,
                            fontSize = 16.sp,
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
                                text = "Loading resellers...",
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
                            text = "No resellers found",
                            color = R2wSaasColors.Text,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Black
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Try another search or status filter.",
                            color = R2wSaasColors.Muted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            } else {
                items(filtered.size) { index ->
                    ResellerSaasCard(
                        reseller = filtered[index],
                        onClick = { onOpenReseller(filtered[index]) }
                    )
                }
            }

            item { Spacer(Modifier.height(18.dp)) }
        }
    }
}

@Composable
private fun ResellerSearchAndFilters(
    query: String,
    selectedStatus: String,
    onQueryChange: (String) -> Unit,
    onStatusChange: (String) -> Unit,
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
            placeholder = {
                Text("Search resellers...")
            },
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
            ResellerFilterChip("all", "All", selectedStatus, onStatusChange)
            ResellerFilterChip("active", "Active", selectedStatus, onStatusChange)
            ResellerFilterChip("inactive", "Inactive", selectedStatus, onStatusChange)
            ResellerFilterChip("suspended", "Suspended", selectedStatus, onStatusChange)

            AssistChip(
                onClick = onRefresh,
                label = {
                    Text(
                        text = "Refresh",
                        fontWeight = FontWeight.ExtraBold
                    )
                },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = R2wSaasColors.PrimarySoft,
                    labelColor = R2wSaasColors.Primary
                ),
                border = BorderStroke(1.dp, R2wSaasColors.Border)
            )
        }
    }
}

@Composable
private fun ResellerFilterChip(
    key: String,
    label: String,
    selectedStatus: String,
    onStatusChange: (String) -> Unit
) {
    val selected = selectedStatus == key

    AssistChip(
        onClick = { onStatusChange(key) },
        label = {
            Text(
                text = label,
                fontWeight = FontWeight.ExtraBold
            )
        },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = if (selected) R2wSaasColors.PrimarySoft else R2wSaasColors.Card,
            labelColor = if (selected) R2wSaasColors.Primary else R2wSaasColors.Muted
        ),
        border = BorderStroke(1.dp, R2wSaasColors.Border)
    )
}

@Composable
private fun ResellerSaasCard(
    reseller: AdminResellerUi,
    onClick: () -> Unit
) {
    R2wSaasCard(
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = statusColor(reseller).copy(alpha = 0.10f),
                border = BorderStroke(1.dp, R2wSaasColors.Border)
            ) {
                Icon(
                    imageVector = Icons.Default.People,
                    contentDescription = null,
                    tint = statusColor(reseller),
                    modifier = Modifier.padding(12.dp)
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
                            text = reseller.name.ifBlank { "Reseller" },
                            color = R2wSaasColors.Text,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = reseller.email,
                            color = R2wSaasColors.Muted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    StatusPill(reseller.status, statusColor(reseller))
                }

                Spacer(Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ResellerMiniStat(
                        title = "Credit",
                        value = moneyValue(reseller.credit),
                        modifier = Modifier.weight(1f)
                    )
                    ResellerMiniStat(
                        title = "Markup",
                        value = "${reseller.markup}%",
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ResellerMiniStat(
                        title = "Monthly",
                        value = moneyValue(reseller.monthlySpent),
                        modifier = Modifier.weight(1f)
                    )
                    ResellerMiniStat(
                        title = "Limit",
                        value = moneyValue(reseller.monthlyLimit),
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "View reseller details",
                        color = R2wSaasColors.Primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black
                    )

                    Text(
                        text = "›",
                        color = R2wSaasColors.Primary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusPill(
    text: String,
    color: androidx.compose.ui.graphics.Color
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.18f))
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
            maxLines = 1
        )
    }
}

@Composable
private fun ResellerMiniStat(
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
                text = value,
                color = R2wSaasColors.Text,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun statusColor(reseller: AdminResellerUi): androidx.compose.ui.graphics.Color =
    when {
        reseller.suspended -> R2wSaasColors.Red
        reseller.active -> R2wSaasColors.Green
        else -> R2wSaasColors.Orange
    }

private fun moneyValue(value: String): String {
    val clean = value.trim()
    if (clean.isBlank()) return "$0.00"
    return if (clean.startsWith("$") || clean.contains("€")) clean else "$$clean"
}
