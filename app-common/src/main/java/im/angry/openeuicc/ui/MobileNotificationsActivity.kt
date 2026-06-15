package im.angry.openeuicc.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import im.angry.openeuicc.auth.AuthSession
import im.angry.openeuicc.auth.AuthTokenStore
import im.angry.openeuicc.auth.JwtUtils
import im.angry.openeuicc.auth.MobileNotification
import im.angry.openeuicc.auth.Roam2WorldAuthApi
import im.angry.openeuicc.common.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

class MobileNotificationsActivity : ComponentActivity() {
    private val tokenStore by lazy { AuthTokenStore(this) }
    private val authApi by lazy { Roam2WorldAuthApi(BuildConfig.ROAM2WORLD_API_BASE_URL) }

    private var notifications by mutableStateOf<List<MobileNotification>>(emptyList())
    private var unreadCount by mutableIntStateOf(0)
    private var loading by mutableStateOf(false)
    private var errorMessage by mutableStateOf<String?>(null)
    private var refreshKey by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MobileNotificationsScreen(
                notifications = notifications,
                unreadCount = unreadCount,
                loading = loading,
                errorMessage = errorMessage,
                refreshKey = refreshKey,
                onBack = { finish() },
                onRefresh = { loadNotifications() },
                onMarkAllRead = { markAllRead() },
                onOpenNotification = { openNotification(it) }
            )
        }

        loadNotifications()
    }

    override fun onResume() {
        super.onResume()
        refreshKey += 1
    }

    private fun loadNotifications() {
        lifecycleScope.launch {
            loading = true
            errorMessage = null

            val session = activeSessionOrReturnToLogin()
            if (session == null) {
                loading = false
                return@launch
            }

            val result = runCatching { authApi.mobileNotifications(session) }
            loading = false

            result
                .onSuccess {
                    notifications = it.notifications
                    unreadCount = it.unreadCount
                    refreshKey += 1
                }
                .onFailure {
                    errorMessage = it.message ?: "Notifications could not be loaded"
                }
        }
    }

    private fun openNotification(notification: MobileNotification) {
        lifecycleScope.launch {
            val session = activeSessionOrReturnToLogin() ?: return@launch
            val notificationId = notification.id

            if (!notification.isRead && !notificationId.isNullOrBlank()) {
                runCatching { authApi.markNotificationRead(session, notificationId) }
            }

            val orderId = notification.relatedOrderId
            val esimId = notification.relatedEsimId

            when {
                !orderId.isNullOrBlank() -> {
                    startActivity(
                        Intent(this@MobileNotificationsActivity, MobileOrderDetailActivity::class.java)
                            .putExtra("mobile_order.id", orderId)
                    )
                }
                !esimId.isNullOrBlank() -> {
                    startActivity(MobileEsimDetailActivity.createIntent(this@MobileNotificationsActivity, esimId))
                }
                else -> {
                    loadNotifications()
                }
            }
        }
    }

    private fun markAllRead() {
        lifecycleScope.launch {
            loading = true
            errorMessage = null

            val session = activeSessionOrReturnToLogin()
            if (session == null) {
                loading = false
                return@launch
            }

            val result = runCatching { authApi.markAllNotificationsRead(session) }
            loading = false

            result
                .onSuccess { loadNotifications() }
                .onFailure {
                    errorMessage = it.message ?: "Notifications could not be updated"
                }
        }
    }

    private suspend fun activeSessionOrReturnToLogin(): AuthSession? {
        val savedSession = withContext(Dispatchers.IO) { tokenStore.getSession() } ?: return redirectToLogin()

        if (!JwtUtils.isExpired(savedSession.accessToken)) return savedSession

        val refreshed = runCatching { authApi.refresh(savedSession) }.getOrNull() ?: return redirectToLogin()

        withContext(Dispatchers.IO) { tokenStore.save(refreshed) }
        return refreshed
    }

    private fun redirectToLogin(): AuthSession? {
        tokenStore.clear()
        startActivity(
            Intent(this, LoginActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
        )
        finish()
        return null
    }
}

@Composable
private fun MobileNotificationsScreen(
    notifications: List<MobileNotification>,
    unreadCount: Int,
    loading: Boolean,
    errorMessage: String?,
    refreshKey: Int,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onMarkAllRead: () -> Unit,
    onOpenNotification: (MobileNotification) -> Unit
) {
    val bg = Color(0xFFF6F7FB)
    val newCount = remember(refreshKey, notifications) { notifications.count { !it.isRead } }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = bg) {
            Column(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    NotificationsHero(
                        total = notifications.size,
                        unread = unreadCount.coerceAtLeast(newCount),
                        loading = loading,
                        onBack = onBack,
                        onRefresh = onRefresh,
                        onMarkAllRead = onMarkAllRead
                    )

                    errorMessage?.let {
                        InfoCard(title = "Notifications could not be loaded") {
                            Text(it, color = Color(0xFFDC2626))
                            OutlinedButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                                Text("Try again")
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${notifications.size} notifications • ${unreadCount.coerceAtLeast(newCount)} unread",
                            color = Color(0xFF6B7280),
                            fontWeight = FontWeight.Bold
                        )
                        if (loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.dp,
                                color = Color(0xFFFF7900)
                            )
                        }
                    }

                    if (!loading && notifications.isEmpty()) {
                        InfoCard(title = "No notifications") {
                            Text(
                                "Mobile order and eSIM updates will appear here.",
                                color = Color(0xFF6B7280)
                            )
                        }
                    }

                    notifications.forEach { notification ->
                        NotificationCard(
                            notification = notification,
                            onClick = { onOpenNotification(notification) }
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))
                }

                Surface(shadowElevation = 8.dp, color = Color.White) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = onBack,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Text("Back")
                        }
                        Button(
                            onClick = onRefresh,
                            enabled = !loading,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF7900)),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Text(if (loading) "Loading..." else "Refresh", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationsHero(
    total: Int,
    unread: Int,
    loading: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onMarkAllRead: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF17181C))
    ) {
        Column(modifier = Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Notifications",
                        color = Color.White,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        "$total mobile notifications • $unread unread",
                        color = Color.White.copy(alpha = 0.72f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Back",
                        color = Color(0xFFFF7900),
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.clickable(onClick = onBack)
                    )
                    Text(
                        if (loading) "Loading..." else "Refresh",
                        color = Color.White.copy(alpha = 0.78f),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable(enabled = !loading, onClick = onRefresh)
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .background(Color(0xFFFFEFE2), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("🔔", fontWeight = FontWeight.Black)
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text("Order and eSIM alerts", color = Color.White, fontWeight = FontWeight.Black)
                    Text("Tap a notification to open the related order or eSIM", color = Color.White.copy(alpha = 0.72f))
                }
            }

            if (unread > 0) {
                Button(
                    onClick = onMarkAllRead,
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF7900)),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text("Mark all as read", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun NotificationCard(
    notification: MobileNotification,
    onClick: () -> Unit
) {
    val read = notification.isRead
    val statusColors = if (read) {
        Color(0xFF64748B) to Color(0xFFF1F5F9)
    } else {
        Color(0xFF168653) to Color(0xFFE4F8EC)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = if (read) Color.White.copy(alpha = 0.76f) else Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(if (read) Color(0xFFF1F5F9) else Color(0xFFFFEFE2), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(notificationIcon(notification.type), fontWeight = FontWeight.Black)
                }

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(
                        notification.title.orEmpty().ifBlank { "Notification" },
                        color = Color(0xFF17181C),
                        fontWeight = FontWeight.Black,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    notification.message?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            it,
                            color = Color(0xFF6B7280),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                StatusPill(if (read) "Read" else "New", statusColors)
            }

            HorizontalDivider(color = Color(0xFFE5E7EB))

            Text(
                notificationMeta(notification),
                color = Color(0xFF6B7280),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                notificationActionText(notification),
                color = Color(0xFFFF7900),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

@Composable
private fun StatusPill(label: String, colors: Pair<Color, Color>) {
    Box(
        modifier = Modifier
            .background(colors.second, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text("●  $label", color = colors.first, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun InfoCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, color = Color(0xFF17181C), fontWeight = FontWeight.Black)
            HorizontalDivider()
            content()
        }
    }
}

private fun notificationIcon(type: String?): String =
    when (type.orEmpty().lowercase(Locale.ROOT)) {
        "order", "order_created", "order_completed" -> "🧾"
        "esim", "esim_ready", "esim_expired" -> "SIM"
        "wallet", "balance" -> "€"
        else -> "🔔"
    }

private fun notificationMeta(notification: MobileNotification): String =
    listOfNotNull(
        notification.type?.replace("_", " ")?.uppercase(Locale.ROOT),
        formatNotificationDate(notification.createdAt),
        notification.relatedOrderId?.let { "Order #$it" },
        notification.relatedEsimId?.let { "eSIM #$it" }
    ).joinToString(" • ").ifBlank { "Mobile notification" }

private fun notificationActionText(notification: MobileNotification): String =
    when {
        !notification.relatedOrderId.isNullOrBlank() -> "Open related order"
        !notification.relatedEsimId.isNullOrBlank() -> "Open related eSIM"
        else -> "Mark as read"
    }

private fun formatNotificationDate(value: String?): String? {
    val raw = value?.trim().orEmpty()
    if (raw.isBlank()) return null
    return runCatching {
        OffsetDateTime.parse(raw).format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm", Locale.ENGLISH))
    }.getOrElse { raw }
}
