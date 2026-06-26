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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.MarkEmailUnread
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private val NotifyDetailBg = Color(0xFFF6F8FC)
private val NotifyDetailNavy = Color(0xFF061A3F)
private val NotifyDetailNavy2 = Color(0xFF123EAD)
private val NotifyDetailBlue = Color(0xFF1263F1)
private val NotifyDetailText = Color(0xFF101828)
private val NotifyDetailMuted = Color(0xFF667085)
private val NotifyDetailBorder = Color(0xFFE1E8F2)
private val NotifyDetailGreen = Color(0xFF16A34A)
private val NotifyDetailOrange = Color(0xFFF97316)

class AdminNotificationDetailActivity : ComponentActivity() {
    companion object {
        const val EXTRA_NOTIFICATION_JSON = "extra_notification_json"
    }

    private val tokenStore by lazy { AuthTokenStore(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val raw = intent.getStringExtra(EXTRA_NOTIFICATION_JSON)
        val parsed = runCatching {
            if (raw.isNullOrBlank()) null else parseNotification(JSONObject(raw))
        }.getOrNull()

        setContent {
            val scope = rememberCoroutineScope()
            var busy by remember { mutableStateOf(false) }

            R2WTheme {
                AdminNotificationDetailScreen(
                    notification = parsed,
                    busy = busy,
                    onBack = { finish() },
                    onReadAction = { notificationId, action ->
                        scope.launch {
                            busy = true
                            updateNotificationReadState(notificationId, action)
                            busy = false
                        }
                    }
                )
            }
        }
    }

    private fun parseNotification(notification: JSONObject): AdminNotificationDetailUi {
        return AdminNotificationDetailUi(
            rawJson = notification.toString(),
            id = notification.optString("id", "-"),
            title = notification.optString("title", notification.optString("subject", "-")),
            message = notification.optString("message", notification.optString("body", "-")),
            type = notification.optString("type", "-"),
            isRead = notification.optBoolean("is_read", false),
            createdAt = notification.optString("created_at", "-"),
            updatedAt = notification.optString("updated_at", "-")
        )
    }

    private suspend fun updateNotificationReadState(notificationId: String, action: String) {
        if (notificationId.isBlank() || notificationId == "-") {
            Toast.makeText(this, "Missing notification id", Toast.LENGTH_LONG).show()
            return
        }

        val session = withContext(Dispatchers.IO) { tokenStore.getSession() }

        if (session == null || JwtUtils.isExpired(session.accessToken)) {
            redirectToLogin()
            return
        }

        val result = withContext(Dispatchers.IO) {
            runCatching {
                postNotificationAction(notificationId, action, session.authorizationHeader)
            }
        }

        result.onSuccess {
            Toast.makeText(this, "Notification updated", Toast.LENGTH_LONG).show()
            finish()
        }.onFailure { error ->
            Toast.makeText(
                this,
                error.message ?: "Notification action failed",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun postNotificationAction(
        notificationId: String,
        action: String,
        authorizationHeader: String
    ): JSONObject {
        val url = URL("https://roam2world-panels-backend.onrender.com/api/v1/mobile/notifications/$notificationId/$action/")
        val connection = url.openConnection() as HttpURLConnection

        return try {
            connection.requestMethod = "POST"
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", authorizationHeader)
            connection.doOutput = true
            connection.connectTimeout = 15000
            connection.readTimeout = 15000

            connection.outputStream.use { output ->
                output.write("{}".toByteArray(Charsets.UTF_8))
            }

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

    private fun redirectToLogin() {
        Toast.makeText(this, "Session expired. Please login again.", Toast.LENGTH_LONG).show()
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }
}

private data class AdminNotificationDetailUi(
    val rawJson: String,
    val id: String,
    val title: String,
    val message: String,
    val type: String,
    val isRead: Boolean,
    val createdAt: String,
    val updatedAt: String
)

@Composable
private fun AdminNotificationDetailScreen(
    notification: AdminNotificationDetailUi?,
    busy: Boolean,
    onBack: () -> Unit,
    onReadAction: (String, String) -> Unit
) {
    Scaffold(containerColor = NotifyDetailBg) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(NotifyDetailBg)
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (notification == null) {
                NotifyDetailTopBar("Notification Detail", onBack)
                NotifyDetailInfoCard("No notification data", R.drawable.admin_icon_doc) {
                    NotifyDetailLine("Status", "Notification data was not provided.")
                }
            } else {
                NotifyDetailHero(notification, onBack)

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NotifyDetailMetricCard(
                        modifier = Modifier.weight(1f),
                        icon = R.drawable.admin_icon_notifications,
                        label = "State",
                        value = if (notification.isRead) "Read" else "Unread",
                        sub = "notification"
                    )
                    NotifyDetailMetricCard(
                        modifier = Modifier.weight(1f),
                        icon = R.drawable.admin_icon_doc,
                        label = "Type",
                        value = notification.type.ifBlank { "-" },
                        sub = "category"
                    )
                }

                NotifyDetailInfoCard("Overview", R.drawable.admin_icon_notifications) {
                    NotifyDetailLine("ID", notification.id)
                    NotifyDetailLine("Title", notification.title)
                    NotifyDetailLine("Type", notification.type)
                    NotifyDetailLine("Read", if (notification.isRead) "Yes" else "No")
                    NotifyDetailLine("Created", notification.createdAt)
                    NotifyDetailLine("Updated", notification.updatedAt)
                }

                NotifyDetailInfoCard("Message", R.drawable.admin_icon_doc) {
                    Text(
                        notification.message.ifBlank { "-" },
                        color = NotifyDetailText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 19.sp
                    )
                }

                NotifyActionCard(
                    notification = notification,
                    busy = busy,
                    onReadAction = onReadAction
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun NotifyDetailTopBar(title: String, onBack: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(Icons.Default.ArrowBack, contentDescription = null, tint = NotifyDetailText)
        }
        Text(title, color = NotifyDetailText, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun NotifyDetailHero(notification: AdminNotificationDetailUi, onBack: () -> Unit) {
    val statusColor = if (notification.isRead) NotifyDetailGreen else NotifyDetailOrange
    val statusText = if (notification.isRead) "Read" else "Unread"

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = NotifyDetailNavy),
        elevation = CardDefaults.cardElevation(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(184.dp)
                .background(Brush.horizontalGradient(listOf(NotifyDetailNavy, NotifyDetailNavy2)))
                .padding(16.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null, tint = Color.White)
                    }
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "Notification Detail",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1
                    )
                }

                Spacer(Modifier.height(8.dp))

                Text(
                    notification.title.ifBlank { "-" },
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(5.dp))

                Text(
                    notification.type.ifBlank { "-" },
                    color = Color.White.copy(alpha = 0.74f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(14.dp))

                Surface(
                    color = statusColor.copy(alpha = 0.20f),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f))
                ) {
                    Text(
                        statusText,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun NotifyDetailMetricCard(
    modifier: Modifier,
    @DrawableRes icon: Int,
    label: String,
    value: String,
    sub: String
) {
    Card(
        modifier = modifier.height(120.dp),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, NotifyDetailBorder),
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
                Text(label, color = NotifyDetailMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(value, color = NotifyDetailText, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                Text(sub, color = NotifyDetailBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun NotifyDetailInfoCard(
    title: String,
    @DrawableRes icon: Int,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, NotifyDetailBorder),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(icon), contentDescription = null, modifier = Modifier.size(32.dp))
                Spacer(Modifier.width(10.dp))
                Text(title, color = NotifyDetailText, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
            }

            content()
        }
    }
}

@Composable
private fun NotifyDetailLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
        Text(
            label,
            color = NotifyDetailMuted,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(0.42f)
        )
        Text(
            value.ifBlank { "-" },
            color = NotifyDetailText,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(0.58f),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun NotifyActionCard(
    notification: AdminNotificationDetailUi,
    busy: Boolean,
    onReadAction: (String, String) -> Unit
) {
    val action = if (notification.isRead) "unread" else "read"
    val label = if (notification.isRead) "Mark as Unread" else "Mark as Read"
    val icon: ImageVector = if (notification.isRead) Icons.Default.MarkEmailUnread else Icons.Default.DoneAll

    NotifyDetailInfoCard("Actions", R.drawable.admin_icon_settings) {
        Button(
            onClick = { onReadAction(notification.id, action) },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = NotifyDetailBlue,
                disabledContainerColor = NotifyDetailMuted.copy(alpha = 0.35f)
            )
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (busy) "Updating..." else label, fontWeight = FontWeight.ExtraBold)
        }
    }
}
