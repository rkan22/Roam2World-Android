package im.angry.openeuicc

import android.app.AlertDialog
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
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
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

private val DealerDetailBg = Color(0xFFF6F8FC)
private val DealerDetailNavy = Color(0xFF061A3F)
private val DealerDetailNavy2 = Color(0xFF123EAD)
private val DealerDetailBlue = Color(0xFF1263F1)
private val DealerDetailText = Color(0xFF101828)
private val DealerDetailMuted = Color(0xFF667085)
private val DealerDetailBorder = Color(0xFFE1E8F2)
private val DealerDetailGreen = Color(0xFF16A34A)
private val DealerDetailOrange = Color(0xFFF97316)
private val DealerDetailRed = Color(0xFFEF4444)

class AdminDealerDetailActivity : ComponentActivity() {
    companion object {
        const val EXTRA_DEALER_JSON = "extra_dealer_json"
    }

    private val tokenStore by lazy { AuthTokenStore(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val raw = intent.getStringExtra(EXTRA_DEALER_JSON)
        val parsed = runCatching {
            if (raw.isNullOrBlank()) null else parseDealer(JSONObject(raw))
        }.getOrNull()

        setContent {
            val scope = rememberCoroutineScope()
            var busy by remember { mutableStateOf(false) }

            R2WTheme {
                AdminDealerDetailScreen(
                    dealer = parsed,
                    busy = busy,
                    onBack = { finish() },
                    onUpdateMarkup = { dealerId, markup ->
                        confirmMarkup(dealerId, markup) {
                            scope.launch {
                                busy = true
                                updateDealerMarkup(dealerId, markup)
                                busy = false
                            }
                        }
                    },
                    onAction = { dealerId, action ->
                        val label = action.replace("_", " ").replaceFirstChar { it.uppercase() }
                        confirmAction(
                            title = label,
                            message = "Run action: $label?"
                        ) {
                            scope.launch {
                                busy = true
                                updateDealerStatus(dealerId, action)
                                busy = false
                            }
                        }
                    }
                )
            }
        }
    }

    private fun parseDealer(dealer: JSONObject): AdminDealerDetailUi {
        val active = dealer.optBoolean("is_active", false)
        val suspended = dealer.optBoolean("is_suspended", false)
        val status = when {
            suspended -> "Suspended"
            active -> "Active"
            else -> "Inactive"
        }

        return AdminDealerDetailUi(
            rawJson = dealer.toString(),
            id = dealer.optString("id", "-"),
            name = dealer.optString("name", "-"),
            email = dealer.optString("email", "-"),
            balance = dealer.optString("current_balance", "0.00"),
            totalAllocated = dealer.optString("total_allocated", "0.00"),
            totalSpent = dealer.optString("total_spent", "0.00"),
            markup = dealer.optString("markup_percentage", "0.00"),
            resellerEmail = dealer.optString("reseller_email", "-"),
            active = active,
            suspended = suspended,
            status = status
        )
    }

    private suspend fun updateDealerMarkup(dealerId: String, markup: String) {
        if (dealerId.isBlank() || dealerId == "-") {
            Toast.makeText(this, "Missing dealer id", Toast.LENGTH_LONG).show()
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
            runCatching { postDealerMarkup(dealerId, cleanMarkup, session.authorizationHeader) }
        }

        result.onSuccess { response ->
            Toast.makeText(this, response.optString("message", "Dealer markup updated"), Toast.LENGTH_LONG).show()
            finish()
        }.onFailure {
            Toast.makeText(this, it.message ?: "Markup update failed", Toast.LENGTH_LONG).show()
        }
    }

    private fun postDealerMarkup(
        dealerId: String,
        markup: String,
        authorizationHeader: String
    ): JSONObject {
        val url = URL("https://roam2world-panels-backend.onrender.com/api/v1/mobile/admin/dealers/${dealerId}/markup/")
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

    private suspend fun updateDealerStatus(dealerId: String, action: String) {
        if (dealerId.isBlank() || dealerId == "-") {
            Toast.makeText(this, "Missing dealer id", Toast.LENGTH_LONG).show()
            return
        }

        val session = withContext(Dispatchers.IO) { tokenStore.getSession() }
        if (session == null || JwtUtils.isExpired(session.accessToken)) {
            redirectToLogin()
            return
        }

        val result = withContext(Dispatchers.IO) {
            runCatching { postDealerAction(dealerId, action, session.authorizationHeader) }
        }

        result.onSuccess { response ->
            Toast.makeText(this, response.optString("message", "Dealer updated"), Toast.LENGTH_LONG).show()
            finish()
        }.onFailure {
            Toast.makeText(this, it.message ?: "Dealer update failed", Toast.LENGTH_LONG).show()
        }
    }

    private fun postDealerAction(
        dealerId: String,
        action: String,
        authorizationHeader: String
    ): JSONObject {
        val url = URL("https://roam2world-panels-backend.onrender.com/api/v1/resellers/dealers/$dealerId/$action/")
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

    private fun confirmMarkup(dealerId: String, markup: String, onConfirm: () -> Unit) {
        confirmAction(
            title = "Update Markup",
            message = "Update dealer $dealerId markup to $markup%?",
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

private data class AdminDealerDetailUi(
    val rawJson: String,
    val id: String,
    val name: String,
    val email: String,
    val balance: String,
    val totalAllocated: String,
    val totalSpent: String,
    val markup: String,
    val resellerEmail: String,
    val active: Boolean,
    val suspended: Boolean,
    val status: String
)

@Composable
private fun AdminDealerDetailScreen(
    dealer: AdminDealerDetailUi?,
    busy: Boolean,
    onBack: () -> Unit,
    onUpdateMarkup: (String, String) -> Unit,
    onAction: (String, String) -> Unit
) {
    var markupInput by remember(dealer?.markup) { mutableStateOf(dealer?.markup ?: "") }

    Scaffold(containerColor = DealerDetailBg) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(DealerDetailBg)
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (dealer == null) {
                DealerDetailTopBar("Dealer Detail", onBack)
                DealerDetailInfoCard("No dealer data", R.drawable.admin_icon_doc) {
                    DealerDetailLine("Status", "Dealer data was not provided.")
                }
            } else {
                DealerDetailHero(dealer, onBack)

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DealerDetailMetricCard(
                        modifier = Modifier.weight(1f),
                        icon = R.drawable.admin_icon_money,
                        label = "Balance",
                        value = dealer.balance,
                        sub = "current wallet"
                    )
                    DealerDetailMetricCard(
                        modifier = Modifier.weight(1f),
                        icon = R.drawable.admin_icon_tag,
                        label = "Markup",
                        value = "${dealer.markup}%",
                        sub = "dealer rate"
                    )
                }

                DealerDetailInfoCard("Account", R.drawable.admin_icon_user) {
                    DealerDetailLine("ID", dealer.id)
                    DealerDetailLine("Name", dealer.name)
                    DealerDetailLine("Email", dealer.email)
                    DealerDetailLine("Status", dealer.status)
                }

                DealerDetailInfoCard("Wallet & Spend", R.drawable.admin_icon_money) {
                    DealerDetailLine("Current Balance", dealer.balance)
                    DealerDetailLine("Total Allocated", dealer.totalAllocated)
                    DealerDetailLine("Total Spent", dealer.totalSpent)
                    DealerDetailLine("Parent Reseller", dealer.resellerEmail)
                }

                DealerMarkupCard(
                    markupInput = markupInput,
                    busy = busy,
                    onMarkupChange = { markupInput = it },
                    onSave = { onUpdateMarkup(dealer.id, markupInput) }
                )

                DealerActionsCard(
                    dealer = dealer,
                    busy = busy,
                    onAction = { action -> onAction(dealer.id, action) }
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun DealerDetailTopBar(title: String, onBack: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(Icons.Default.ArrowBack, contentDescription = null, tint = DealerDetailText)
        }
        Text(title, color = DealerDetailText, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun DealerDetailHero(dealer: AdminDealerDetailUi, onBack: () -> Unit) {
    val statusColor = dealerDetailStatusColor(dealer.status)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = DealerDetailNavy),
        elevation = CardDefaults.cardElevation(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(184.dp)
                .background(Brush.horizontalGradient(listOf(DealerDetailNavy, DealerDetailNavy2)))
                .padding(16.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null, tint = Color.White)
                    }
                    Spacer(Modifier.width(4.dp))
                    Text("Dealer Detail", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold)
                }

                Spacer(Modifier.height(8.dp))

                Text(
                    dealer.name.ifBlank { "-" },
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(5.dp))

                Text(
                    dealer.email.ifBlank { "-" },
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
                        dealer.status,
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
private fun DealerDetailMetricCard(
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
        border = BorderStroke(1.dp, DealerDetailBorder),
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
                Text(label, color = DealerDetailMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(value, color = DealerDetailText, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                Text(sub, color = DealerDetailBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }
    }
}

@Composable
private fun DealerDetailInfoCard(
    title: String,
    @DrawableRes icon: Int,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, DealerDetailBorder),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(icon), contentDescription = null, modifier = Modifier.size(32.dp))
                Spacer(Modifier.width(10.dp))
                Text(title, color = DealerDetailText, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
            }
            content()
        }
    }
}

@Composable
private fun DealerDetailLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
        Text(label, color = DealerDetailMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.42f))
        Text(
            value.ifBlank { "-" },
            color = DealerDetailText,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(0.58f),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun DealerMarkupCard(
    markupInput: String,
    busy: Boolean,
    onMarkupChange: (String) -> Unit,
    onSave: () -> Unit
) {
    DealerDetailInfoCard("Markup Editor", R.drawable.admin_icon_tag) {
        OutlinedTextField(
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
            colors = ButtonDefaults.buttonColors(containerColor = DealerDetailBlue)
        ) {
            Text(if (busy) "Updating..." else "Update Markup", fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
private fun DealerActionsCard(
    dealer: AdminDealerDetailUi,
    busy: Boolean,
    onAction: (String) -> Unit
) {
    val action = if (dealer.active && !dealer.suspended) "suspend_dealer" else "activate_dealer"
    val danger = action.contains("suspend")
    val title = if (danger) "Suspend Dealer" else "Activate Dealer"

    DealerDetailInfoCard("Account Actions", R.drawable.admin_icon_settings) {
        Button(
            onClick = { onAction(action) },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (danger) DealerDetailRed else DealerDetailGreen,
                disabledContainerColor = DealerDetailMuted.copy(alpha = 0.35f)
            )
        ) {
            Icon(if (danger) Icons.Default.Block else Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (busy) "Updating..." else title, fontWeight = FontWeight.ExtraBold)
        }
    }
}

private fun dealerDetailStatusColor(status: String): Color {
    val clean = status.lowercase()
    return when {
        clean.contains("active") && !clean.contains("inactive") -> DealerDetailGreen
        clean.contains("suspend") -> DealerDetailRed
        clean.contains("inactive") -> DealerDetailOrange
        else -> DealerDetailMuted
    }
}
