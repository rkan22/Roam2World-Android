package im.angry.openeuicc

import android.os.Bundle
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import im.angry.openeuicc.ui.compose.theme.R2WTheme
import org.json.JSONObject

private val LogDetailBg = Color(0xFFF6F8FC)
private val LogDetailNavy = Color(0xFF061A3F)
private val LogDetailNavy2 = Color(0xFF123EAD)
private val LogDetailBlue = Color(0xFF1263F1)
private val LogDetailText = Color(0xFF101828)
private val LogDetailMuted = Color(0xFF667085)
private val LogDetailBorder = Color(0xFFE1E8F2)
private val LogDetailGreen = Color(0xFF16A34A)
private val LogDetailOrange = Color(0xFFF97316)
private val LogDetailRed = Color(0xFFEF4444)

class AdminActivityLogDetailActivity : ComponentActivity() {
    companion object {
        const val EXTRA_LOG_JSON = "extra_log_json"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val raw = intent.getStringExtra(EXTRA_LOG_JSON)
        val parsed = runCatching {
            if (raw.isNullOrBlank()) null else parseLog(JSONObject(raw))
        }.getOrNull()

        setContent {
            R2WTheme {
                AdminActivityLogDetailScreen(
                    log = parsed,
                    onBack = { finish() }
                )
            }
        }
    }

    private fun parseLog(log: JSONObject): AdminActivityLogDetailUi {
        return AdminActivityLogDetailUi(
            rawJson = log.toString(),
            title = log.optString("title", "-"),
            type = log.optString("type", "-"),
            status = log.optString("status", "-"),
            actor = log.optString("actor", "-"),
            message = log.optString("message", "-"),
            createdAt = log.optString("created_at", "-")
        )
    }
}

private data class AdminActivityLogDetailUi(
    val rawJson: String,
    val title: String,
    val type: String,
    val status: String,
    val actor: String,
    val message: String,
    val createdAt: String
)

@Composable
private fun AdminActivityLogDetailScreen(
    log: AdminActivityLogDetailUi?,
    onBack: () -> Unit
) {
    Scaffold(containerColor = LogDetailBg) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(LogDetailBg)
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (log == null) {
                LogDetailTopBar("Activity Log", onBack)
                LogDetailInfoCard("No log data", R.drawable.admin_icon_doc) {
                    LogDetailLine("Status", "Activity log data was not provided.")
                }
            } else {
                LogDetailHero(log, onBack)

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LogDetailMetricCard(
                        modifier = Modifier.weight(1f),
                        icon = R.drawable.admin_icon_reports,
                        label = "Type",
                        value = log.type.ifBlank { "-" },
                        sub = "event group"
                    )
                    LogDetailMetricCard(
                        modifier = Modifier.weight(1f),
                        icon = R.drawable.admin_icon_health,
                        label = "Status",
                        value = log.status.ifBlank { "-" },
                        sub = "result"
                    )
                }

                LogDetailInfoCard("Overview", R.drawable.admin_icon_reports) {
                    LogDetailLine("Title", log.title)
                    LogDetailLine("Type", log.type)
                    LogDetailLine("Status", log.status)
                    LogDetailLine("Actor", log.actor)
                    LogDetailLine("Date", log.createdAt)
                }

                LogDetailInfoCard("Message", R.drawable.admin_icon_doc) {
                    Text(
                        log.message.ifBlank { "-" },
                        color = LogDetailText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 19.sp
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun LogDetailTopBar(title: String, onBack: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(Icons.Default.ArrowBack, contentDescription = null, tint = LogDetailText)
        }
        Text(title, color = LogDetailText, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun LogDetailHero(log: AdminActivityLogDetailUi, onBack: () -> Unit) {
    val statusColor = logDetailStatusColor(log.status)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = LogDetailNavy),
        elevation = CardDefaults.cardElevation(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(190.dp)
                .background(Brush.horizontalGradient(listOf(LogDetailNavy, LogDetailNavy2)))
                .padding(16.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null, tint = Color.White)
                    }
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "Activity Log",
                        color = Color.White,
                        fontSize = 25.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1
                    )
                }

                Spacer(Modifier.height(8.dp))

                Text(
                    log.title.ifBlank { "-" },
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(5.dp))

                Text(
                    "Actor: ${log.actor.ifBlank { "-" }}",
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
                        log.status.ifBlank { "-" },
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
private fun LogDetailMetricCard(
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
        border = BorderStroke(1.dp, LogDetailBorder),
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
                Text(label, color = LogDetailMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(value, color = LogDetailText, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                Text(sub, color = LogDetailBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun LogDetailInfoCard(
    title: String,
    @DrawableRes icon: Int,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, LogDetailBorder),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(icon), contentDescription = null, modifier = Modifier.size(32.dp))
                Spacer(Modifier.width(10.dp))
                Text(title, color = LogDetailText, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
            }

            content()
        }
    }
}

@Composable
private fun LogDetailLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
        Text(
            label,
            color = LogDetailMuted,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(0.42f)
        )
        Text(
            value.ifBlank { "-" },
            color = LogDetailText,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(0.58f),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun logDetailStatusColor(status: String): Color {
    val clean = status.lowercase()
    return when {
        clean.contains("completed") || clean.contains("resolved") || clean.contains("ok") -> LogDetailGreen
        clean.contains("pending") || clean.contains("progress") -> LogDetailOrange
        clean.contains("failed") || clean.contains("error") -> LogDetailRed
        else -> LogDetailMuted
    }
}
