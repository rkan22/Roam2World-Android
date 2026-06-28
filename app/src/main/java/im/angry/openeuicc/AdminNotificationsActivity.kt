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

private val NotifyBg = Color(0xFFF6F8FC)
private val NotifyNavy = Color(0xFF061A3F)
private val NotifyNavy2 = Color(0xFF123EAD)
private val NotifyBlue = Color(0xFF1263F1)
private val NotifyText = Color(0xFF101828)
private val NotifyMuted = Color(0xFF667085)
private val NotifyBorder = Color(0xFFE2E8F0)
private val NotifyGreen = Color(0xFF16A34A)
private val NotifyOrange = Color(0xFFF97316)

class AdminNotificationsActivity : ComponentActivity() {
    private val tokenStore by lazy { AuthTokenStore(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val composeScope = rememberCoroutineScope()
            var loading by remember { mutableStateOf(true) }
            var errorMessage by remember { mutableStateOf<String?>(null) }
            var notifications by remember { mutableStateOf<List<AdminNotificationUi>>(emptyList()) }
            var query by remember { mutableStateOf("") }
            var readFilter by remember { mutableStateOf("all") }

            suspend fun loadNotifications() {
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
                        val response = fetchNotifications(session.authorizationHeader)
                        parseNotifications(response)
                    }
                }

                result
                    .onSuccess { notifications = it }
                    .onFailure {
                        errorMessage = it.message ?: "Notifications API error"
                        Toast.makeText(this@AdminNotificationsActivity, errorMessage, Toast.LENGTH_LONG).show()
                    }

                loading = false
            }

            LaunchedEffect(Unit) {
                loadNotifications()
            }

            R2WTheme {
                AdminNotificationsListScreen(
                    loading = loading,
                    errorMessage = errorMessage,
                    notifications = notifications,
                    query = query,
                    readFilter = readFilter,
                    onQueryChange = { query = it },
                    onReadFilterChange = { readFilter = it },
                    onRefresh = {
                        composeScope.launch {
                            loadNotifications()
                        }
                    },
                    onOpenNotification = { item ->
                        startActivity(
                            Intent(this@AdminNotificationsActivity, AdminNotificationDetailActivity::class.java).apply {
                                putExtra(AdminNotificationDetailActivity.EXTRA_NOTIFICATION_JSON, item.rawJson)
                            }
                        )
                    },
                    onBottomNavClick = { tab ->
                        when (tab) {
                            NotifyTab.Dashboard -> startActivity(Intent(this@AdminNotificationsActivity, MobileAdminActivity::class.java))
                            NotifyTab.Partners -> startActivity(Intent(this@AdminNotificationsActivity, AdminPartnersActivity::class.java))
                            NotifyTab.Orders -> startActivity(Intent(this@AdminNotificationsActivity, AdminOrdersOverviewActivity::class.java))
                            NotifyTab.Pricing -> startActivity(Intent(this@AdminNotificationsActivity, AdminPricingOverviewActivity::class.java))
                            NotifyTab.More -> startActivity(Intent(this@AdminNotificationsActivity, AdminMoreActivity::class.java))
                        }
                    }
                )
            }
        }
    }

    private fun fetchNotifications(authorizationHeader: String): JSONObject {
        val url = URL("https://roam2world-panels-backend.onrender.com/api/v1/mobile/notifications/")
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

    private fun parseNotifications(response: JSONObject): List<AdminNotificationUi> {
        val data = response.optJSONObject("data") ?: response
        val arr = data.optJSONArray("notifications")
            ?: response.optJSONArray("notifications")
            ?: JSONArray()

        val list = mutableListOf<AdminNotificationUi>()

        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue

            list.add(
                AdminNotificationUi(
                    rawJson = item.toString(),
                    id = item.optString("id", "-"),
                    title = item.optString("title", item.optString("subject", "-")),
                    message = item.optString("message", item.optString("body", "-")),
                    type = item.optString("type", "-"),
                    isRead = item.optBoolean("is_read", false),
                    createdAt = item.optString("created_at", "-"),
                    updatedAt = item.optString("updated_at", "-")
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

private data class AdminNotificationUi(
    val rawJson: String,
    val id: String,
    val title: String,
    val message: String,
    val type: String,
    val isRead: Boolean,
    val createdAt: String,
    val updatedAt: String
)

private enum class NotifyTab {
    Dashboard,
    Partners,
    Orders,
    Pricing,
    More
}

@Composable
private fun AdminNotificationsListScreen(
    loading: Boolean,
    errorMessage: String?,
    notifications: List<AdminNotificationUi>,
    query: String,
    readFilter: String,
    onQueryChange: (String) -> Unit,
    onReadFilterChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onOpenNotification: (AdminNotificationUi) -> Unit,
    onBottomNavClick: (NotifyTab) -> Unit
) {
    val filtered by remember(notifications, query, readFilter) {
        derivedStateOf {
            val cleanQuery = query.trim().lowercase()

            notifications
                .filter {
                    when (readFilter) {
                        "read" -> it.isRead
                        "unread" -> !it.isRead
                        else -> true
                    }
                }
                .filter { item ->
                    cleanQuery.isBlank() || listOf(
                        item.id,
                        item.title,
                        item.message,
                        item.type,
                        item.createdAt
                    ).joinToString(" ").lowercase().contains(cleanQuery)
                }
        }
    }

    val unread = notifications.count { !it.isRead }
    val read = notifications.count { it.isRead }

    Scaffold(
        containerColor = NotifyBg,
        bottomBar = {
            NotifyBottomNav(
                selected = NotifyTab.More,
                onClick = onBottomNavClick
            )
        }
    ) { inner ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(NotifyBg)
                .padding(inner)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(Modifier.height(8.dp)) }

            item {
                NotifyHero(
                    shown = filtered.size.toString(),
                    total = notifications.size.toString(),
                    unread = unread.toString()
                )
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NotifyMetricCard(
                        modifier = Modifier.weight(1f),
                        icon = R.drawable.admin_icon_notifications,
                        label = "Unread",
                        value = unread.toString(),
                        sub = "needs review",
                        subColor = NotifyOrange
                    )
                    NotifyMetricCard(
                        modifier = Modifier.weight(1f),
                        icon = R.drawable.admin_icon_doc,
                        label = "Read",
                        value = read.toString(),
                        sub = "reviewed",
                        subColor = NotifyGreen
                    )
                }
            }

            item {
                NotifyFilterCard(
                    query = query,
                    readFilter = readFilter,
                    onQueryChange = onQueryChange,
                    onReadFilterChange = onReadFilterChange,
                    onRefresh = onRefresh,
                    loading = loading
                )
            }

            if (loading) {
                item {
                    NotifyInfoCard("Loading Notifications", "Retrieving recent account, system, and operational alerts.") {
                        CircularProgressIndicator(color = NotifyBlue)
                    }
                }
            }

            if (!loading && errorMessage != null) {
                item {
                    NotifyInfoCard("Notifications unavailable", errorMessage)
                }
            }

            if (!loading && errorMessage == null && filtered.isEmpty()) {
                item {
                    NotifyInfoCard("No Matching Notifications", "Clear filters or search by title, message, or notification type.")
                }
            }

            items(filtered.size) { index ->
                NotifyListCard(
                    notification = filtered[index],
                    onClick = { onOpenNotification(filtered[index]) }
                )
            }

            item { Spacer(Modifier.height(10.dp)) }
        }
    }
}

@Composable
private fun NotifyHero(
    shown: String,
    total: String,
    unread: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = NotifyNavy),
        elevation = CardDefaults.cardElevation(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(138.dp)
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF0B2B66), Color(0xFF1263F1))
                    )
                )
                .padding(18.dp)
        ) {
            Column {
                Text(
                    "Notifications",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "$shown shown • $total total • $unread unread",
                    color = Color.White.copy(alpha = 0.72f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    "Live notification center",
                    color = Color.White,
                    fontSize = 10.sp,
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
private fun NotifyMetricCard(
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
        border = BorderStroke(1.dp, NotifyBorder),
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
                Text(label, color = NotifyMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text(value, color = NotifyText, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                Text(sub, color = subColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun NotifyFilterCard(
    query: String,
    readFilter: String,
    onQueryChange: (String) -> Unit,
    onReadFilterChange: (String) -> Unit,
    onRefresh: () -> Unit,
    loading: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, NotifyBorder),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier = Modifier.padding(13.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                label = { Text("Search title, message, type") },
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
                    "unread" to "Unread",
                    "read" to "Read"
                ).forEach { (value, label) ->
                    val selected = readFilter == value
                    AssistChip(
                        onClick = { onReadFilterChange(value) },
                        label = {
                            Text(
                                label,
                                fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = if (selected) Color.White else Color.White,
                            labelColor = if (selected) NotifyBlue else NotifyMuted
                        ),
                        border = BorderStroke(1.dp, if (selected) NotifyBlue.copy(alpha = 0.35f) else NotifyBorder)
                    )
                }

                AssistChip(
                    onClick = onRefresh,
                    enabled = !loading,
                    label = { Text(if (loading) "Loading" else "Refresh", fontWeight = FontWeight.ExtraBold) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = Color.White,
                        labelColor = NotifyBlue
                    ),
                    border = BorderStroke(1.dp, NotifyBlue.copy(alpha = 0.35f))
                )
            }
        }
    }
}

@Composable
private fun NotifyListCard(
    notification: AdminNotificationUi,
    onClick: () -> Unit
) {
    val readColor = if (notification.isRead) NotifyGreen else NotifyOrange
    val readText = if (notification.isRead) "Read" else "Unread"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, NotifyBorder),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier = Modifier.padding(13.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(R.drawable.admin_icon_notifications),
                    contentDescription = null,
                    modifier = Modifier.size(38.dp)
                )

                Spacer(Modifier.size(12.dp))

                Column(Modifier.weight(1f)) {
                    Text(
                        notification.title.ifBlank { "-" },
                        color = NotifyText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        notification.type.ifBlank { "-" },
                        color = NotifyMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Surface(
                    color = readColor.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        readText,
                        color = readColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            Text(
                notification.message.ifBlank { "-" },
                color = NotifyMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                "Created: ${notification.createdAt.ifBlank { "-" }}",
                color = NotifyMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                "View details ›",
                color = NotifyBlue,
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

@Composable
private fun NotifyInfoCard(
    title: String,
    message: String,
    content: @Composable (() -> Unit)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, NotifyBorder),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier = Modifier.padding(13.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(title, color = NotifyText, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
            Text(message, color = NotifyMuted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            content?.invoke()
        }
    }
}

@Composable
private fun NotifyBottomNav(
    selected: NotifyTab,
    onClick: (NotifyTab) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        color = Color.White,
        shadowElevation = 14.dp,
        border = BorderStroke(1.dp, NotifyBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(66.dp)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            NotifyBottomItem(Icons.Default.GridView, "Dashboard", selected == NotifyTab.Dashboard) { onClick(NotifyTab.Dashboard) }
            NotifyBottomItem(Icons.Default.People, "Partners", selected == NotifyTab.Partners) { onClick(NotifyTab.Partners) }
            NotifyBottomItem(Icons.Default.ShoppingCart, "Orders", selected == NotifyTab.Orders) { onClick(NotifyTab.Orders) }
            NotifyBottomItem(Icons.Default.CreditCard, "Pricing", selected == NotifyTab.Pricing) { onClick(NotifyTab.Pricing) }
            NotifyBottomItem(Icons.Default.MoreHoriz, "More", selected == NotifyTab.More) { onClick(NotifyTab.More) }
        }
    }
}

@Composable
private fun NotifyBottomItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val color = if (selected) NotifyBlue else NotifyMuted
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
