package im.angry.openeuicc.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import im.angry.openeuicc.auth.AuthSession
import im.angry.openeuicc.auth.AuthTokenStore
import im.angry.openeuicc.common.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

class TgtCheckGbActivity : ComponentActivity() {
    private val tokenStore by lazy { AuthTokenStore(this) }

    private var iccid by mutableStateOf("")
    private var iccidError by mutableStateOf<String?>(null)
    private var loading by mutableStateOf(false)
    private var errorMessage by mutableStateOf<String?>(null)
    private var usage by mutableStateOf<TgtUsageUi?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        iccid = intent.getStringExtra("renew.iccid")
            ?: intent.getStringExtra("iccid")
            ?: ""

        setContent {
            TgtCheckGbScreen(
                iccid = iccid,
                iccidError = iccidError,
                loading = loading,
                errorMessage = errorMessage,
                usage = usage,
                onIccidChange = {
                    iccid = it
                    iccidError = null
                    errorMessage = null
                },
                onBack = { finish() },
                onCheckGb = { checkGb() },
                onTopUp = { openTopUp() }
            )
        }
    }

    private fun checkGb() {
        iccidError = null
        errorMessage = null

        val cleanIccid = iccid.trim()
        if (cleanIccid.length < 6) {
            iccidError = "Enter ICCID"
            return
        }

        lifecycleScope.launch {
            val session = withContext(Dispatchers.IO) { tokenStore.getSession() }
            if (session == null) {
                startActivity(
                    Intent(this@TgtCheckGbActivity, LoginActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    }
                )
                finish()
                return@launch
            }

            loading = true
            val response = runCatching {
                withContext(Dispatchers.IO) { postUsage(session, cleanIccid) }
            }
            loading = false

            response
                .onSuccess {
                    usage = parseUsage(it, cleanIccid)
                }
                .onFailure {
                    val message = it.message ?: "Orange Check GB failed"
                    errorMessage = message
                    Toast.makeText(this@TgtCheckGbActivity, message, Toast.LENGTH_LONG).show()
                }
        }
    }

    private fun openTopUp() {
        startActivity(
            Intent(this, TgtSimRechargeActivity::class.java).apply {
                putExtra("renew.iccid", iccid.trim())
            }
        )
    }

    private fun postUsage(session: AuthSession, iccid: String): JSONObject {
        val url = URL("${BuildConfig.ROAM2WORLD_API_BASE_URL.trimEnd('/')}/api/v1/mobile/tgt/check-gb/")
        val body = JSONObject().put("iccid", iccid).toString()

        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20000
            readTimeout = 20000
            doOutput = true
            setRequestProperty("Authorization", session.authorizationHeader)
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
        }

        connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

        val statusCode = connection.responseCode
        val responseText = if (statusCode in 200..299) {
            connection.inputStream.bufferedReader().use { it.readText() }
        } else {
            connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
        }

        val json = JSONObject(responseText.ifBlank { "{}" })
        if (statusCode !in 200..299 || !json.optBoolean("success", false)) {
            throw IllegalStateException(json.optString("error").ifBlank { "Orange usage check failed" })
        }

        return json
    }

    private fun parseUsage(response: JSONObject, fallbackIccid: String): TgtUsageUi {
        val usage = response.optJSONObject("usage") ?: JSONObject()
        val raw = response.optJSONObject("raw") ?: JSONObject()
        val rawData = raw.optJSONObject("data") ?: JSONObject()

        val totalMb = firstNumber(usage, rawData, "total_mb", "totalMb", "dataTotal")
        val usedMb = firstNumber(usage, rawData, "used_mb", "usedMb", "dataUsage")
        val remainingMb = firstNumber(usage, rawData, "remaining_mb", "remainingMb", "dataResidual")

        val totalText = totalMb?.let { formatGb(it) } ?: "-- GB"
        val usedText = usedMb?.let { formatGb(it) } ?: "-- GB"
        val remainingText = remainingMb?.let { formatGb(it) } ?: "-- GB"

        val percent = if (totalMb != null && usedMb != null && totalMb > 0) {
            ((usedMb / totalMb) * 100).roundToInt().coerceIn(0, 100)
        } else {
            0
        }

        val statusRaw = usage.optString("status").ifBlank { raw.optString("status") }
        val statusText = formatTgtStatus(statusRaw.ifBlank { "ACTIVE" })

        val startDate = firstNotBlank(
            usage.optString("start_date"),
            usage.optString("startDate"),
            rawData.optString("startTime"),
            rawData.optString("activated_time")
        )?.let { formatTgtDate(it) } ?: "—"

        val endDate = firstNotBlank(
            usage.optString("end_date"),
            usage.optString("endDate"),
            rawData.optString("endTime"),
            rawData.optString("expiredTime")
        )?.let { formatTgtDate(it) } ?: "—"

        val packageName = firstNotBlank(
            usage.optString("package_name"),
            usage.optString("packageName"),
            rawData.optString("productName"),
            rawData.optString("packageName")
        ) ?: "Orange Package"

        val resultIccid = response.optString("iccid").ifBlank { fallbackIccid }

        return TgtUsageUi(
            packageName = packageName,
            iccid = resultIccid.ifBlank { "ICCID unavailable" },
            status = statusText,
            totalText = totalText,
            usedText = usedText,
            remainingText = remainingText,
            percentUsed = percent,
            activatedAt = startDate,
            expiresAt = endDate
        )
    }

    private fun formatTgtDate(value: String): String {
        if (value.isBlank()) return ""
        return try {
            val formatter = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm", Locale.ENGLISH)
            Instant.parse(value).atZone(ZoneId.systemDefault()).format(formatter)
        } catch (_: Exception) {
            value
        }
    }

    private fun formatTgtStatus(value: String): String =
        when (value.uppercase(Locale.ROOT)) {
            "INUSE" -> "In Use"
            "ACTIVATED" -> "Activated"
            "NOTACTIVE" -> "Not Activated"
            "USED" -> "Used Up"
            "EXPIRED" -> "Expired"
            "ABANDON" -> "Abandoned"
            "TERMINATION" -> "Terminated"
            else -> value.ifBlank { "Unknown" }
        }

    private fun firstNotBlank(vararg values: String?): String? =
        values.firstOrNull { !it.isNullOrBlank() && it != "null" }

    private fun firstNumber(primary: JSONObject, secondary: JSONObject, vararg keys: String): Double? {
        for (key in keys) {
            val pValue = numberValue(primary.opt(key))
            if (pValue != null) return pValue

            val sValue = numberValue(secondary.opt(key))
            if (sValue != null) return sValue
        }
        return null
    }

    private fun numberValue(value: Any?): Double? =
        when (value) {
            is Number -> value.toDouble()
            is String -> value.trim().replace(",", ".").toDoubleOrNull()
            else -> null
        }

    private fun formatGb(mb: Double): String {
        val gb = mb / 1024.0
        val rounded = (gb * 100).roundToInt() / 100.0
        return "$rounded GB"
    }
}

private data class TgtUsageUi(
    val packageName: String,
    val iccid: String,
    val status: String,
    val totalText: String,
    val usedText: String,
    val remainingText: String,
    val percentUsed: Int,
    val activatedAt: String,
    val expiresAt: String
)

@Composable
private fun TgtCheckGbScreen(
    iccid: String,
    iccidError: String?,
    loading: Boolean,
    errorMessage: String?,
    usage: TgtUsageUi?,
    onIccidChange: (String) -> Unit,
    onBack: () -> Unit,
    onCheckGb: () -> Unit,
    onTopUp: () -> Unit
) {
    val bg = Color(0xFFF6F7FB)

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
                    OrangeUsageHero(
                        usage = usage,
                        loading = loading,
                        onBack = onBack
                    )

                    usage?.let {
                        UsageCard(usage = it)
                    } ?: InfoCard(title = "Orange Check GB") {
                        Text(
                            "Enter an Orange ICCID to check package allowance, remaining data and expiry details.",
                            color = Color(0xFF6B7280)
                        )
                    }

                    errorMessage?.let {
                        InfoCard(title = "Check failed") {
                            Text(it, color = Color(0xFFDC2626))
                        }
                    }

                    InfoCard(title = "Check by ICCID") {
                        OutlinedTextField(
                            value = iccid,
                            onValueChange = onIccidChange,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("ICCID") },
                            singleLine = true,
                            isError = iccidError != null,
                            supportingText = { iccidError?.let { Text(it) } },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(18.dp)
                        )

                        Button(
                            onClick = onCheckGb,
                            enabled = !loading,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF7900)),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            if (loading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White
                                )
                            } else {
                                Text("Check GB", fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    InfoCard(title = "Need more data?") {
                        Text("Open Orange recharge with this ICCID prefilled.", color = Color(0xFF6B7280))
                        OutlinedButton(
                            onClick = onTopUp,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Text("Top up / Recharge")
                        }
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
                            enabled = !loading,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Text("Back")
                        }
                        Button(
                            onClick = onCheckGb,
                            enabled = !loading,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF7900)),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Text(if (loading) "Checking..." else "Check GB", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OrangeUsageHero(
    usage: TgtUsageUi?,
    loading: Boolean,
    onBack: () -> Unit
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
                        "Orange Check GB",
                        color = Color.White,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        usage?.packageName ?: "Check remaining package data",
                        color = Color.White.copy(alpha = 0.72f),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Text(
                    "Back",
                    color = Color(0xFFFF7900),
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.clickable(enabled = !loading, onClick = onBack)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(58.dp)
                        .background(Color(0xFFFFEFE2), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("GB", color = Color(0xFFFF7900), fontWeight = FontWeight.Black)
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        usage?.remainingText ?: "-- GB",
                        color = Color.White,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        usage?.let { "Remaining of ${it.totalText}" } ?: "Remaining data will appear here",
                        color = Color.White.copy(alpha = 0.72f)
                    )
                }

                usage?.let {
                    StatusPill(it.status, statusColors(it.status))
                }
            }
        }
    }
}

@Composable
private fun UsageCard(usage: TgtUsageUi) {
    InfoCard(title = "Usage details") {
        Text(
            usage.packageName,
            color = Color(0xFF17181C),
            fontWeight = FontWeight.Black,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(usage.iccid, color = Color(0xFF6B7280), style = MaterialTheme.typography.bodySmall)

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(12.dp)
                    .background(Color(0xFFE5E7EB), RoundedCornerShape(50))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth((usage.percentUsed / 100f).coerceIn(0f, 1f))
                        .height(12.dp)
                        .background(Color(0xFFFF7900), RoundedCornerShape(50))
                )
            }
            Text("${usage.percentUsed}% Used", color = Color(0xFF6B7280), fontWeight = FontWeight.Bold)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            UsageMetric("Remaining", usage.remainingText, Modifier.weight(1f))
            UsageMetric("Used", usage.usedText, Modifier.weight(1f))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            UsageMetric("Allowance", usage.totalText, Modifier.weight(1f))
            UsageMetric("Status", usage.status, Modifier.weight(1f))
        }

        DetailLine("Activated", usage.activatedAt)
        DetailLine("Expires", usage.expiresAt)
    }
}

@Composable
private fun UsageMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC))
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(value, color = Color(0xFF17181C), fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(label, color = Color(0xFF6B7280), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color(0xFF6B7280), fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.width(12.dp))
        Text(value, color = Color(0xFF17181C), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
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

private fun statusColors(status: String): Pair<Color, Color> =
    if (status.contains("expired", ignoreCase = true) || status.contains("terminated", ignoreCase = true)) {
        Color(0xFFDC2626) to Color(0xFFFEE2E2)
    } else {
        Color(0xFF168653) to Color(0xFFE4F8EC)
    }
