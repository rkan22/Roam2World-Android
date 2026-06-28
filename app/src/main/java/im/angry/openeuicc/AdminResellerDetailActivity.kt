package im.angry.openeuicc

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.EditText
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
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Percent
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

private val ResellerDetailBg = Color(0xFFF6F8FC)
private val ResellerDetailNavy = Color(0xFF061A3F)
private val ResellerDetailNavy2 = Color(0xFF123EAD)
private val ResellerDetailBlue = Color(0xFF1263F1)
private val ResellerDetailText = Color(0xFF101828)
private val ResellerDetailMuted = Color(0xFF667085)
private val ResellerDetailBorder = Color(0xFFE2E8F0)
private val ResellerDetailGreen = Color(0xFF16A34A)
private val ResellerDetailOrange = Color(0xFFF97316)
private val ResellerDetailRed = Color(0xFFEF4444)

class AdminResellerDetailActivity : ComponentActivity() {
    companion object {
        const val EXTRA_RESELLER_JSON = "extra_reseller_json"
    }

    private val tokenStore by lazy { AuthTokenStore(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val raw = intent.getStringExtra(EXTRA_RESELLER_JSON)
        val parsed = runCatching {
            if (raw.isNullOrBlank()) null else parseReseller(JSONObject(raw))
        }.getOrNull()

        setContent {
            val scope = rememberCoroutineScope()
            var busy by remember { mutableStateOf(false) }

            R2WTheme {
                AdminResellerDetailScreen(
                    reseller = parsed,
                    busy = busy,
                    onBack = { finish() },
                    onUpdateMarkup = { resellerId, markup ->
                        confirmMarkup(resellerId, markup) {
                            scope.launch {
                                busy = true
                                updateResellerMarkup(resellerId, markup)
                                busy = false
                            }
                        }
                    },
                    onAction = { resellerId, action ->
                        val label = action.replace("_", " ").replaceFirstChar { it.uppercase() }
                        confirmAction(
                            title = label,
                            message = "Run action: $label?"
                        ) {
                            scope.launch {
                                busy = true
                                updateResellerStatus(resellerId, action)
                                busy = false
                            }
                        }
                    }
                )
            }
        }
    }

    private fun parseReseller(reseller: JSONObject): AdminResellerDetailUi {
        val active = reseller.optBoolean("is_active", false)
        val suspended = reseller.optBoolean("is_suspended", false)
        val status = when {
            suspended -> "Suspended"
            active -> "Active"
            else -> "Inactive"
        }

        return AdminResellerDetailUi(
            rawJson = reseller.toString(),
            id = reseller.optString("id", "-"),
            name = reseller.optString("name", "-"),
            email = reseller.optString("email", "-"),
            active = active,
            suspended = suspended,
            status = status,
            credit = reseller.optString("current_credit", "0.00"),
            creditLimit = reseller.optString("credit_limit", "0.00"),
            monthlySpent = reseller.optString("current_month_spent", "0.00"),
            monthlyLimit = reseller.optString("monthly_spend_limit", "0.00"),
            markup = reseller.optString("markup_percentage", "0.00")
        )
    }

    private suspend fun updateResellerMarkup(resellerId: String, markup: String) {
        if (resellerId.isBlank() || resellerId == "-") {
            Toast.makeText(this, "Missing reseller id", Toast.LENGTH_LONG).show()
            return
        }

        val cleanMarkup = markup.trim()
        if (cleanMarkup.toDoubleOrNull() == null) {
            Toast.makeText(this, "Invalid markup percentage", Toast.LENGTH_LONG).show()
            return
        }

        val session = withContext(Dispatchers.IO) { tokenStore.getSession() }
        if (session == null || JwtUtils.isExpired(session.accessToken)) {
            redirectToLogin()
            return
        }

        val result = withContext(Dispatchers.IO) {
            runCatching { postResellerMarkup(resellerId, cleanMarkup, session.authorizationHeader) }
        }

        result.onSuccess { response ->
            Toast.makeText(this, response.optString("message", "Reseller markup updated"), Toast.LENGTH_LONG).show()
            finish()
        }.onFailure {
            Toast.makeText(this, it.message ?: "Markup update failed", Toast.LENGTH_LONG).show()
        }
    }

    private fun postResellerMarkup(
        resellerId: String,
        markup: String,
        authorizationHeader: String
    ): JSONObject {
        val url = URL("https://roam2world-panels-backend.onrender.com/api/v1/mobile/admin/resellers/${resellerId}/markup/")
        val connection = url.openConnection() as HttpURLConnection

        return try {
            val body = JSONObject()
                .put("markup_percentage", markup)
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

            val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
            val responseBody = stream.bufferedReader().use { it.readText() }

            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("HTTP ${connection.responseCode}: $responseBody")
            }

            JSONObject(responseBody)
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun updateResellerStatus(resellerId: String, action: String) {
        if (resellerId.isBlank() || resellerId == "-") {
            Toast.makeText(this, "Missing reseller id", Toast.LENGTH_LONG).show()
            return
        }

        val session = withContext(Dispatchers.IO) { tokenStore.getSession() }
        if (session == null || JwtUtils.isExpired(session.accessToken)) {
            redirectToLogin()
            return
        }

        val result = withContext(Dispatchers.IO) {
            runCatching { postResellerAction(resellerId, action, session.authorizationHeader) }
        }

        result.onSuccess { response ->
            Toast.makeText(this, response.optString("message", "Reseller updated"), Toast.LENGTH_LONG).show()
            finish()
        }.onFailure {
            Toast.makeText(this, it.message ?: "Reseller update failed", Toast.LENGTH_LONG).show()
        }
    }

    private fun postResellerAction(
        resellerId: String,
        action: String,
        authorizationHeader: String
    ): JSONObject {
        val url = URL("https://roam2world-panels-backend.onrender.com/api/v1/resellers/resellers/$resellerId/$action/")
        val connection = url.openConnection() as HttpURLConnection

        return try {
            connection.requestMethod = "POST"
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Authorization", authorizationHeader)
            connection.connectTimeout = 15000
            connection.readTimeout = 15000

            val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
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

    private fun confirmMarkup(resellerId: String, markup: String, onConfirm: () -> Unit) {
        confirmAction(
            title = "Update Markup",
            message = "Update reseller $resellerId markup to $markup%?",
            onConfirm = onConfirm
        )
    }

    private fun confirmAction(title: String, message: String, onConfirm: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Confirm") { _, _ -> onConfirm() }
            .setNegativeButton("Cancel", null)
            .show()
    }
}

private data class AdminResellerDetailUi(
    val rawJson: String,
    val id: String,
    val name: String,
    val email: String,
    val active: Boolean,
    val suspended: Boolean,
    val status: String,
    val credit: String,
    val creditLimit: String,
    val monthlySpent: String,
    val monthlyLimit: String,
    val markup: String
)

@Composable
private fun AdminResellerDetailScreen(
    reseller: AdminResellerDetailUi?,
    busy: Boolean,
    onBack: () -> Unit,
    onUpdateMarkup: (String, String) -> Unit,
    onAction: (String, String) -> Unit
) {
    var markupInput by remember(reseller?.markup) { mutableStateOf(reseller?.markup ?: "") }

    Scaffold(containerColor = ResellerDetailBg) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(ResellerDetailBg)
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (reseller == null) {
                ResellerDetailTopBar("Reseller Detail", onBack)
                ResellerDetailInfoCard("No reseller data", R.drawable.admin_icon_doc) {
                    ResellerDetailLine("Status", "Reseller data was not provided.")
                }
            } else {
                ResellerDetailHero(reseller, onBack)

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ResellerDetailMetricCard(
                        modifier = Modifier.weight(1f),
                        icon = R.drawable.admin_icon_money,
                        label = "Credit",
                        value = reseller.credit,
                        sub = "limit ${reseller.creditLimit}"
                    )
                    ResellerDetailMetricCard(
                        modifier = Modifier.weight(1f),
                        icon = R.drawable.admin_icon_tag,
                        label = "Markup",
                        value = "${reseller.markup}%",
                        sub = "reseller rate"
                    )
                }

                ResellerDetailInfoCard("Account", R.drawable.admin_icon_partners) {
                    ResellerDetailLine("ID", reseller.id)
                    ResellerDetailLine("Name", reseller.name)
                    ResellerDetailLine("Email", reseller.email)
                    ResellerDetailLine("Status", reseller.status)
                }

                ResellerDetailInfoCard("Wallet & Limits", R.drawable.admin_icon_money) {
                    ResellerDetailLine("Current Credit", reseller.credit)
                    ResellerDetailLine("Credit Limit", reseller.creditLimit)
                    ResellerDetailLine("Monthly Spend", reseller.monthlySpent)
                    ResellerDetailLine("Monthly Limit", reseller.monthlyLimit)
                }

                ResellerMarkupCard(
                    markupInput = markupInput,
                    busy = busy,
                    onMarkupChange = { markupInput = it },
                    onSave = { onUpdateMarkup(reseller.id, markupInput) }
                )

                ResellerActionsCard(
                    reseller = reseller,
                    busy = busy,
                    onAction = { action -> onAction(reseller.id, action) }
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ResellerDetailTopBar(title: String, onBack: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(Icons.Default.ArrowBack, contentDescription = null, tint = ResellerDetailText)
        }
        Text(title, color = ResellerDetailText, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun ResellerDetailHero(reseller: AdminResellerDetailUi, onBack: () -> Unit) {
    val statusColor = resellerDetailStatusColor(reseller.status)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = ResellerDetailNavy),
        elevation = CardDefaults.cardElevation(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(184.dp)
                .background(Color.White)
                .padding(16.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null, tint = Color.White)
                    }
                    Spacer(Modifier.width(4.dp))
                    Text("Reseller Detail", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold)
                }

                Spacer(Modifier.height(8.dp))

                Text(
                    reseller.name.ifBlank { "-" },
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(5.dp))

                Text(
                    reseller.email.ifBlank { "-" },
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
                        reseller.status,
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
private fun ResellerDetailMetricCard(
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
        border = BorderStroke(1.dp, ResellerDetailBorder),
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
                Text(label, color = ResellerDetailMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(value, color = ResellerDetailText, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                Text(sub, color = ResellerDetailBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }
    }
}

@Composable
private fun ResellerDetailInfoCard(
    title: String,
    @DrawableRes icon: Int,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, ResellerDetailBorder),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(icon), contentDescription = null, modifier = Modifier.size(32.dp))
                Spacer(Modifier.width(10.dp))
                Text(title, color = ResellerDetailText, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
            }
            content()
        }
    }
}

@Composable
private fun ResellerDetailLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
        Text(label, color = ResellerDetailMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.42f))
        Text(
            value.ifBlank { "-" },
            color = ResellerDetailText,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(0.58f),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ResellerMarkupCard(
    markupInput: String,
    busy: Boolean,
    onMarkupChange: (String) -> Unit,
    onSave: () -> Unit
) {
    ResellerDetailInfoCard("Markup Editor", R.drawable.admin_icon_tag) {
        androidx.compose.material3.OutlinedTextField(
            value = markupInput,
            onValueChange = onMarkupChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Markup percentage") },
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Percent, contentDescription = null) }
        )

        Button(
            onClick = onSave,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ResellerDetailBlue)
        ) {
            Text(if (busy) "Updating..." else "Update Markup", fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
private fun ResellerActionsCard(
    reseller: AdminResellerDetailUi,
    busy: Boolean,
    onAction: (String) -> Unit
) {
    val action = if (reseller.active && !reseller.suspended) "suspend_reseller" else "activate_reseller"
    val danger = action.contains("suspend")
    val title = if (danger) "Suspend Reseller" else "Activate Reseller"

    ResellerDetailInfoCard("Account Actions", R.drawable.admin_icon_settings) {
        Button(
            onClick = { onAction(action) },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (danger) ResellerDetailRed else ResellerDetailGreen,
                disabledContainerColor = ResellerDetailMuted.copy(alpha = 0.35f)
            )
        ) {
            Icon(if (danger) Icons.Default.Block else Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (busy) "Updating..." else title, fontWeight = FontWeight.ExtraBold)
        }
    }
}

private fun resellerDetailStatusColor(status: String): Color {
    val clean = status.lowercase()
    return when {
        clean.contains("active") && !clean.contains("inactive") -> ResellerDetailGreen
        clean.contains("suspend") -> ResellerDetailRed
        clean.contains("inactive") -> ResellerDetailOrange
        else -> ResellerDetailMuted
    }
}
