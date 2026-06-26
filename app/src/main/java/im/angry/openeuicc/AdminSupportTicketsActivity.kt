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

private val SupportBg = Color(0xFFF6F8FC)
private val SupportNavy = Color(0xFF061A3F)
private val SupportNavy2 = Color(0xFF123EAD)
private val SupportBlue = Color(0xFF1263F1)
private val SupportText = Color(0xFF101828)
private val SupportMuted = Color(0xFF667085)
private val SupportBorder = Color(0xFFE1E8F2)
private val SupportGreen = Color(0xFF16A34A)
private val SupportOrange = Color(0xFFF97316)
private val SupportRed = Color(0xFFEF4444)

class AdminSupportTicketsActivity : ComponentActivity() {
    private val tokenStore by lazy { AuthTokenStore(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val composeScope = rememberCoroutineScope()
            var loading by remember { mutableStateOf(true) }
            var errorMessage by remember { mutableStateOf<String?>(null) }
            var tickets by remember { mutableStateOf<List<AdminSupportTicketUi>>(emptyList()) }
            var summary by remember { mutableStateOf(AdminSupportSummaryUi()) }
            var query by remember { mutableStateOf("") }
            var statusFilter by remember { mutableStateOf("all") }

            suspend fun loadSupportTickets() {
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
                        val response = fetchSupportTickets(session.authorizationHeader)
                        parseSupportResponse(response)
                    }
                }

                result
                    .onSuccess {
                        tickets = it.tickets
                        summary = it.summary
                    }
                    .onFailure {
                        errorMessage = it.message ?: "Support Tickets API error"
                        Toast.makeText(this@AdminSupportTicketsActivity, errorMessage, Toast.LENGTH_LONG).show()
                    }

                loading = false
            }

            LaunchedEffect(Unit) {
                loadSupportTickets()
            }

            R2WTheme {
                AdminSupportTicketsListScreen(
                    loading = loading,
                    errorMessage = errorMessage,
                    tickets = tickets,
                    summary = summary,
                    query = query,
                    statusFilter = statusFilter,
                    onQueryChange = { query = it },
                    onStatusFilterChange = { statusFilter = it },
                    onRefresh = {
                        composeScope.launch {
                            loadSupportTickets()
                        }
                    },
                    onOpenTicket = { ticket ->
                        startActivity(
                            Intent(this@AdminSupportTicketsActivity, AdminSupportTicketDetailActivity::class.java).apply {
                                putExtra(AdminSupportTicketDetailActivity.EXTRA_TICKET_JSON, ticket.rawJson)
                            }
                        )
                    },
                    onBottomNavClick = { tab ->
                        when (tab) {
                            SupportTab.Dashboard -> startActivity(Intent(this@AdminSupportTicketsActivity, MobileAdminActivity::class.java))
                            SupportTab.Partners -> startActivity(Intent(this@AdminSupportTicketsActivity, AdminPartnersActivity::class.java))
                            SupportTab.Orders -> startActivity(Intent(this@AdminSupportTicketsActivity, AdminOrdersOverviewActivity::class.java))
                            SupportTab.Pricing -> startActivity(Intent(this@AdminSupportTicketsActivity, AdminPricingOverviewActivity::class.java))
                            SupportTab.More -> startActivity(Intent(this@AdminSupportTicketsActivity, AdminMoreActivity::class.java))
                        }
                    }
                )
            }
        }
    }

    private fun fetchSupportTickets(authorizationHeader: String): JSONObject {
        val url = URL("https://roam2world-panels-backend.onrender.com/api/v1/mobile/admin/support-tickets/")
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

    private fun parseSupportResponse(response: JSONObject): AdminSupportResponseUi {
        val data = response.optJSONObject("data") ?: response
        val arr = data.optJSONArray("tickets") ?: JSONArray()

        val list = mutableListOf<AdminSupportTicketUi>()

        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue

            list.add(
                AdminSupportTicketUi(
                    rawJson = item.toString(),
                    id = item.optString("id", "-"),
                    subject = item.optString("subject", "-"),
                    description = item.optString("description", "-"),
                    status = item.optString("status", "-"),
                    clientName = item.optString("client_name", "-"),
                    clientEmail = item.optString("client_email", "-"),
                    assignedTo = item.optString("assigned_to_name", ""),
                    createdAt = item.optString("created_at", "-")
                )
            )
        }

        val summary = AdminSupportSummaryUi(
            count = data.optInt("count", list.size),
            open = data.optInt("open_count", list.count { it.status.lowercase().contains("open") }),
            inProgress = data.optInt("in_progress_count", list.count { it.status.lowercase().contains("progress") }),
            resolved = data.optInt("resolved_count", list.count { it.status.lowercase().contains("resolved") }),
            closed = data.optInt("closed_count", list.count { it.status.lowercase().contains("closed") })
        )

        return AdminSupportResponseUi(tickets = list, summary = summary)
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

private data class AdminSupportResponseUi(
    val tickets: List<AdminSupportTicketUi>,
    val summary: AdminSupportSummaryUi
)

private data class AdminSupportSummaryUi(
    val count: Int = 0,
    val open: Int = 0,
    val inProgress: Int = 0,
    val resolved: Int = 0,
    val closed: Int = 0
)

private data class AdminSupportTicketUi(
    val rawJson: String,
    val id: String,
    val subject: String,
    val description: String,
    val status: String,
    val clientName: String,
    val clientEmail: String,
    val assignedTo: String,
    val createdAt: String
)

private enum class SupportTab {
    Dashboard,
    Partners,
    Orders,
    Pricing,
    More
}

@Composable
private fun AdminSupportTicketsListScreen(
    loading: Boolean,
    errorMessage: String?,
    tickets: List<AdminSupportTicketUi>,
    summary: AdminSupportSummaryUi,
    query: String,
    statusFilter: String,
    onQueryChange: (String) -> Unit,
    onStatusFilterChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onOpenTicket: (AdminSupportTicketUi) -> Unit,
    onBottomNavClick: (SupportTab) -> Unit
) {
    val filtered by remember(tickets, query, statusFilter) {
        derivedStateOf {
            val cleanQuery = query.trim().lowercase()

            tickets
                .filter { ticket ->
                    statusFilter == "all" || ticket.status.lowercase().contains(statusFilter)
                }
                .filter { ticket ->
                    cleanQuery.isBlank() || listOf(
                        ticket.id,
                        ticket.subject,
                        ticket.description,
                        ticket.status,
                        ticket.clientName,
                        ticket.clientEmail,
                        ticket.assignedTo
                    ).joinToString(" ").lowercase().contains(cleanQuery)
                }
        }
    }

    Scaffold(
        containerColor = SupportBg,
        bottomBar = {
            SupportBottomNav(
                selected = SupportTab.More,
                onClick = onBottomNavClick
            )
        }
    ) { inner ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(SupportBg)
                .padding(inner)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(Modifier.height(8.dp)) }

            item {
                SupportHero(
                    shown = filtered.size.toString(),
                    total = summary.count.toString(),
                    open = summary.open.toString()
                )
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SupportMetricCard(
                        modifier = Modifier.weight(1f),
                        icon = R.drawable.admin_icon_support,
                        label = "Open",
                        value = summary.open.toString(),
                        sub = "waiting",
                        subColor = SupportOrange
                    )
                    SupportMetricCard(
                        modifier = Modifier.weight(1f),
                        icon = R.drawable.admin_icon_health,
                        label = "Resolved",
                        value = summary.resolved.toString(),
                        sub = "completed",
                        subColor = SupportGreen
                    )
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SupportMetricCard(
                        modifier = Modifier.weight(1f),
                        icon = R.drawable.admin_icon_doc,
                        label = "In Progress",
                        value = summary.inProgress.toString(),
                        sub = "active work",
                        subColor = SupportBlue
                    )
                    SupportMetricCard(
                        modifier = Modifier.weight(1f),
                        icon = R.drawable.admin_icon_settings,
                        label = "Closed",
                        value = summary.closed.toString(),
                        sub = "archived",
                        subColor = SupportMuted
                    )
                }
            }

            item {
                SupportFilterCard(
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
                    SupportInfoCard("Loading support tickets", "Fetching open and recent support requests.") {
                        CircularProgressIndicator(color = SupportBlue)
                    }
                }
            }

            if (!loading && errorMessage != null) {
                item {
                    SupportInfoCard("Support tickets unavailable", errorMessage)
                }
            }

            if (!loading && errorMessage == null && filtered.isEmpty()) {
                item {
                    SupportInfoCard("No Matching Tickets", "Clear filters or search by subject, client name, client email, or status.")
                }
            }

            items(filtered.size) { index ->
                SupportTicketCard(
                    ticket = filtered[index],
                    onClick = { onOpenTicket(filtered[index]) }
                )
            }

            item { Spacer(Modifier.height(10.dp)) }
        }
    }
}

@Composable
private fun SupportHero(
    shown: String,
    total: String,
    open: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = SupportNavy),
        elevation = CardDefaults.cardElevation(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .background(Brush.horizontalGradient(listOf(SupportNavy, SupportNavy2)))
                .padding(18.dp)
        ) {
            Column {
                Text(
                    "Support Tickets",
                    color = Color.White,
                    fontSize = 27.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "$shown shown • $total total • $open open",
                    color = Color.White.copy(alpha = 0.72f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    "Client support operations",
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
private fun SupportMetricCard(
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
        border = BorderStroke(1.dp, SupportBorder),
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
                Text(label, color = SupportMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(value, color = SupportText, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
                Text(sub, color = subColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SupportFilterCard(
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
        border = BorderStroke(1.dp, SupportBorder),
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
                label = { Text("Search subject, client, email") },
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
                    "open" to "Open",
                    "progress" to "In Progress",
                    "resolved" to "Resolved",
                    "closed" to "Closed"
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
                            labelColor = if (selected) SupportBlue else SupportMuted
                        ),
                        border = BorderStroke(1.dp, if (selected) SupportBlue.copy(alpha = 0.35f) else SupportBorder)
                    )
                }

                AssistChip(
                    onClick = onRefresh,
                    enabled = !loading,
                    label = { Text(if (loading) "Loading" else "Refresh", fontWeight = FontWeight.ExtraBold) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = Color(0xFFEAF2FF),
                        labelColor = SupportBlue
                    ),
                    border = BorderStroke(1.dp, SupportBlue.copy(alpha = 0.35f))
                )
            }
        }
    }
}

@Composable
private fun SupportTicketCard(
    ticket: AdminSupportTicketUi,
    onClick: () -> Unit
) {
    val statusColor = supportStatusColor(ticket.status)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, SupportBorder),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(R.drawable.admin_icon_support),
                    contentDescription = null,
                    modifier = Modifier.size(42.dp)
                )

                Spacer(Modifier.size(12.dp))

                Column(Modifier.weight(1f)) {
                    Text(
                        ticket.subject.ifBlank { "-" },
                        color = SupportText,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        ticket.clientEmail.ifBlank { "-" },
                        color = SupportMuted,
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
                        ticket.status.ifBlank { "-" },
                        color = statusColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        maxLines = 1
                    )
                }
            }

            Text(
                ticket.description.ifBlank { "-" },
                color = SupportMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                "Client: ${ticket.clientName.ifBlank { "-" }} • Assigned: ${ticket.assignedTo.ifBlank { "-" }}",
                color = SupportMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                "Created: ${ticket.createdAt.ifBlank { "-" }}",
                color = SupportMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                "Tap to open details",
                color = SupportBlue,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

@Composable
private fun SupportInfoCard(
    title: String,
    message: String,
    content: @Composable (() -> Unit)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, SupportBorder),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(title, color = SupportText, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            Text(message, color = SupportMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            content?.invoke()
        }
    }
}

private fun supportStatusColor(status: String): Color {
    val clean = status.lowercase()
    return when {
        clean.contains("resolved") -> SupportGreen
        clean.contains("closed") -> SupportMuted
        clean.contains("progress") -> SupportBlue
        clean.contains("open") -> SupportOrange
        clean.contains("failed") || clean.contains("error") -> SupportRed
        else -> SupportMuted
    }
}

@Composable
private fun SupportBottomNav(
    selected: SupportTab,
    onClick: (SupportTab) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        color = Color.White,
        shadowElevation = 14.dp,
        border = BorderStroke(1.dp, SupportBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(66.dp)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SupportBottomItem(Icons.Default.GridView, "Dashboard", selected == SupportTab.Dashboard) { onClick(SupportTab.Dashboard) }
            SupportBottomItem(Icons.Default.People, "Partners", selected == SupportTab.Partners) { onClick(SupportTab.Partners) }
            SupportBottomItem(Icons.Default.ShoppingCart, "Orders", selected == SupportTab.Orders) { onClick(SupportTab.Orders) }
            SupportBottomItem(Icons.Default.CreditCard, "Pricing", selected == SupportTab.Pricing) { onClick(SupportTab.Pricing) }
            SupportBottomItem(Icons.Default.MoreHoriz, "More", selected == SupportTab.More) { onClick(SupportTab.More) }
        }
    }
}

@Composable
private fun SupportBottomItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val color = if (selected) SupportBlue else SupportMuted
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
