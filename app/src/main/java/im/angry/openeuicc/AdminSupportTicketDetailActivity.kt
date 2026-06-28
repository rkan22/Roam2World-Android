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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
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

private val TicketDetailBg = Color(0xFFF6F8FC)
private val TicketDetailNavy = Color(0xFF061A3F)
private val TicketDetailNavy2 = Color(0xFF123EAD)
private val TicketDetailBlue = Color(0xFF1263F1)
private val TicketDetailText = Color(0xFF101828)
private val TicketDetailMuted = Color(0xFF667085)
private val TicketDetailBorder = Color(0xFFE2E8F0)
private val TicketDetailGreen = Color(0xFF16A34A)
private val TicketDetailOrange = Color(0xFFF97316)
private val TicketDetailRed = Color(0xFFEF4444)

class AdminSupportTicketDetailActivity : ComponentActivity() {
    companion object {
        const val EXTRA_TICKET_JSON = "extra_ticket_json"
    }

    private val tokenStore by lazy { AuthTokenStore(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val raw = intent.getStringExtra(EXTRA_TICKET_JSON)
        val parsed = runCatching {
            if (raw.isNullOrBlank()) null else parseTicket(JSONObject(raw))
        }.getOrNull()

        setContent {
            val scope = rememberCoroutineScope()
            var busy by remember { mutableStateOf(false) }

            R2WTheme {
                AdminSupportTicketDetailScreen(
                    ticket = parsed,
                    busy = busy,
                    onBack = { finish() },
                    onStatusAction = { ticketId, status ->
                        scope.launch {
                            busy = true
                            updateTicketStatus(ticketId, status)
                            busy = false
                        }
                    }
                )
            }
        }
    }

    private fun parseTicket(ticket: JSONObject): AdminSupportTicketDetailUi {
        return AdminSupportTicketDetailUi(
            rawJson = ticket.toString(),
            id = ticket.optString("id", "-"),
            subject = ticket.optString("subject", "-"),
            description = ticket.optString("description", "-"),
            status = ticket.optString("status", "-"),
            clientName = ticket.optString("client_name", "-"),
            clientEmail = ticket.optString("client_email", "-"),
            assignedTo = ticket.optString("assigned_to_name", ""),
            createdAt = ticket.optString("created_at", "-"),
            updatedAt = ticket.optString("updated_at", "-"),
            resolvedAt = ticket.optString("resolved_at", "")
        )
    }

    private suspend fun updateTicketStatus(ticketId: String, status: String) {
        if (ticketId.isBlank() || ticketId == "-") {
            Toast.makeText(this, "Missing ticket id", Toast.LENGTH_LONG).show()
            return
        }

        val session = withContext(Dispatchers.IO) { tokenStore.getSession() }

        if (session == null || JwtUtils.isExpired(session.accessToken)) {
            redirectToLogin()
            return
        }

        val result = withContext(Dispatchers.IO) {
            runCatching {
                postTicketStatus(ticketId, status, session.authorizationHeader)
            }
        }

        result.onSuccess { response ->
            Toast.makeText(
                this,
                response.optString("message", "Ticket updated"),
                Toast.LENGTH_LONG
            ).show()
            finish()
        }.onFailure { error ->
            Toast.makeText(
                this,
                error.message ?: "Ticket status update failed",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun postTicketStatus(
        ticketId: String,
        status: String,
        authorizationHeader: String
    ): JSONObject {
        val url = URL("https://roam2world-panels-backend.onrender.com/api/v1/clients/support-tickets/$ticketId/status/")
        val connection = url.openConnection() as HttpURLConnection

        return try {
            val body = JSONObject()
                .put("status", status)
                .toString()

            connection.requestMethod = "POST"
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", authorizationHeader)
            connection.doOutput = true
            connection.connectTimeout = 15000
            connection.readTimeout = 15000

            connection.outputStream.use { output ->
                output.write(body.toByteArray(Charsets.UTF_8))
            }

            val stream = if (connection.responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }

            val responseBody = stream.bufferedReader().use { it.readText() }

            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("HTTP ${connection.responseCode}: $responseBody")
            }

            JSONObject(responseBody)
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

private data class AdminSupportTicketDetailUi(
    val rawJson: String,
    val id: String,
    val subject: String,
    val description: String,
    val status: String,
    val clientName: String,
    val clientEmail: String,
    val assignedTo: String,
    val createdAt: String,
    val updatedAt: String,
    val resolvedAt: String
)

@Composable
private fun AdminSupportTicketDetailScreen(
    ticket: AdminSupportTicketDetailUi?,
    busy: Boolean,
    onBack: () -> Unit,
    onStatusAction: (String, String) -> Unit
) {
    Scaffold(containerColor = TicketDetailBg) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(TicketDetailBg)
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (ticket == null) {
                TicketDetailTopBar("Support Ticket", onBack)
                TicketDetailInfoCard("No ticket data", R.drawable.admin_icon_doc) {
                    TicketDetailLine("Status", "Support ticket data was not provided.")
                }
            } else {
                TicketDetailHero(ticket, onBack)

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TicketDetailMetricCard(
                        modifier = Modifier.weight(1f),
                        icon = R.drawable.admin_icon_support,
                        label = "Ticket",
                        value = "#${ticket.id}",
                        sub = "support id"
                    )
                    TicketDetailMetricCard(
                        modifier = Modifier.weight(1f),
                        icon = R.drawable.admin_icon_health,
                        label = "Status",
                        value = ticket.status.ifBlank { "-" },
                        sub = "current state"
                    )
                }

                TicketDetailInfoCard("Overview", R.drawable.admin_icon_support) {
                    TicketDetailLine("Ticket ID", ticket.id)
                    TicketDetailLine("Subject", ticket.subject)
                    TicketDetailLine("Status", ticket.status)
                    TicketDetailLine("Created", ticket.createdAt)
                    TicketDetailLine("Updated", ticket.updatedAt)
                    TicketDetailLine("Resolved", ticket.resolvedAt.cleanNullable())
                }

                TicketDetailInfoCard("Client", R.drawable.admin_icon_user) {
                    TicketDetailLine("Name", ticket.clientName)
                    TicketDetailLine("Email", ticket.clientEmail)
                }

                TicketDetailInfoCard("Assignment", R.drawable.admin_icon_partners) {
                    TicketDetailLine("Assigned To", ticket.assignedTo.cleanNullable())
                }

                TicketDetailInfoCard("Description", R.drawable.admin_icon_doc) {
                    Text(
                        ticket.description.ifBlank { "-" },
                        color = TicketDetailText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 19.sp
                    )
                }

                TicketActionsCard(
                    ticket = ticket,
                    busy = busy,
                    onStatusAction = onStatusAction
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun TicketDetailTopBar(title: String, onBack: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(Icons.Default.ArrowBack, contentDescription = null, tint = TicketDetailText)
        }
        Text(title, color = TicketDetailText, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun TicketDetailHero(ticket: AdminSupportTicketDetailUi, onBack: () -> Unit) {
    val statusColor = ticketStatusColor(ticket.status)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = TicketDetailNavy),
        elevation = CardDefaults.cardElevation(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(190.dp)
                .background(Color.White)
                .padding(16.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null, tint = Color.White)
                    }
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "Ticket Detail",
                        color = Color.White,
                        fontSize = 25.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1
                    )
                }

                Spacer(Modifier.height(8.dp))

                Text(
                    ticket.subject.ifBlank { "-" },
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(5.dp))

                Text(
                    ticket.clientEmail.ifBlank { "-" },
                    color = Color.White.copy(alpha = 0.74f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(14.dp))

                Surface(
                    color = statusColor.copy(alpha = 0.22f),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f))
                ) {
                    Text(
                        ticket.status.ifBlank { "-" },
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
private fun TicketDetailMetricCard(
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
        border = BorderStroke(1.dp, TicketDetailBorder),
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
                Text(label, color = TicketDetailMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(value, color = TicketDetailText, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                Text(sub, color = TicketDetailBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun TicketDetailInfoCard(
    title: String,
    @DrawableRes icon: Int,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, TicketDetailBorder),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(icon), contentDescription = null, modifier = Modifier.size(32.dp))
                Spacer(Modifier.width(10.dp))
                Text(title, color = TicketDetailText, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
            }

            content()
        }
    }
}

@Composable
private fun TicketDetailLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
        Text(
            label,
            color = TicketDetailMuted,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(0.42f)
        )
        Text(
            value.ifBlank { "-" },
            color = TicketDetailText,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(0.58f),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun TicketActionsCard(
    ticket: AdminSupportTicketDetailUi,
    busy: Boolean,
    onStatusAction: (String, String) -> Unit
) {
    TicketDetailInfoCard("Status Actions", R.drawable.admin_icon_settings) {
        TicketActionButton(
            label = "Set In Progress",
            status = "in_progress",
            icon = Icons.Default.PlayArrow,
            color = TicketDetailBlue,
            busy = busy,
            ticketId = ticket.id,
            onStatusAction = onStatusAction
        )

        TicketActionButton(
            label = "Close Ticket",
            status = "closed",
            icon = Icons.Default.Lock,
            color = TicketDetailRed,
            busy = busy,
            ticketId = ticket.id,
            onStatusAction = onStatusAction
        )

        TicketActionButton(
            label = "Reopen Ticket",
            status = "open",
            icon = Icons.Default.CheckCircle,
            color = TicketDetailGreen,
            busy = busy,
            ticketId = ticket.id,
            onStatusAction = onStatusAction
        )
    }
}

@Composable
private fun TicketActionButton(
    label: String,
    status: String,
    icon: ImageVector,
    color: Color,
    busy: Boolean,
    ticketId: String,
    onStatusAction: (String, String) -> Unit
) {
    Button(
        onClick = { onStatusAction(ticketId, status) },
        enabled = !busy,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = color,
            disabledContainerColor = TicketDetailMuted.copy(alpha = 0.35f)
        )
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(if (busy) "Updating..." else label, fontWeight = FontWeight.ExtraBold)
    }
}

private fun ticketStatusColor(status: String): Color {
    val clean = status.lowercase()
    return when {
        clean.contains("resolved") -> TicketDetailGreen
        clean.contains("closed") -> TicketDetailMuted
        clean.contains("progress") -> TicketDetailBlue
        clean.contains("open") -> TicketDetailOrange
        clean.contains("failed") || clean.contains("error") -> TicketDetailRed
        else -> TicketDetailMuted
    }
}

private fun String.cleanNullable(): String {
    val clean = trim()
    return if (clean.isBlank() || clean == "null") "-" else clean
}
