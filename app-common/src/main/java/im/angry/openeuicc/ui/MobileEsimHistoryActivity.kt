package im.angry.openeuicc.ui

import android.content.Intent
import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import im.angry.openeuicc.auth.AuthSession
import im.angry.openeuicc.auth.AuthTokenStore
import im.angry.openeuicc.auth.JwtUtils
import im.angry.openeuicc.auth.MobileEsim
import im.angry.openeuicc.auth.Roam2WorldAuthApi
import im.angry.openeuicc.common.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val HistoryBlue = Color(0xFF0F4FD7)
private val HistoryText = Color(0xFF20242C)
private val HistoryMuted = Color(0xFF68707C)
private val HistoryBg = Color(0xFFF8FAFD)
private val HistoryBorder = Color(0xFFE1E6EF)
private val HistoryGreen = Color(0xFF176C3A)
private val HistoryGreenBg = Color(0xFFE9F7EF)
private val HistoryRed = Color(0xFFB42336)
private val HistoryRedBg = Color(0xFFFFEEF2)
private val HistoryYellow = Color(0xFF0F4FD7)
private val HistoryYellowBg = Color(0xFFEFF5FF)
private val HistoryGray = Color(0xFF6B7280)
private val HistoryGrayBg = Color(0xFFF0F2F5)

class MobileEsimHistoryActivity : ComponentActivity() {
    private val tokenStore by lazy { AuthTokenStore(this) }
    private val authApi by lazy { Roam2WorldAuthApi(BuildConfig.ROAM2WORLD_API_BASE_URL) }

    private var esims by mutableStateOf<List<MobileEsim>>(emptyList())
    private var selectedFilter by mutableStateOf(HistoryFilter.ALL)
    private var query by mutableStateOf("")
    private var loading by mutableStateOf(false)
    private var error by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        actionBar?.hide()
        window.statusBarColor = AndroidColor.rgb(248, 250, 253)
        window.navigationBarColor = AndroidColor.BLACK
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true
        setContent {
            EsimHistoryMockupScreen(
                esims = filteredEsims(),
                selectedFilter = selectedFilter,
                query = query,
                loading = loading,
                error = error,
                onBack = { finish() },
                onFilterChange = { selectedFilter = it },
                onQueryChange = { query = it },
                onReset = { selectedFilter = HistoryFilter.ALL; query = "" },
                onRefresh = { loadEsims() },
                onOpenDetail = { startActivity(MobileEsimHistoryDetailActivity.createIntent(this, it)) }
            )
        }
        loadEsims()
    }

    private fun loadEsims() {
        lifecycleScope.launch {
            error = null
            loading = true
            val session = activeSessionOrReturnToLogin()
            if (session == null) {
                loading = false
                return@launch
            }
            runCatching { authApi.esims(session) }
                .onSuccess { esims = it.esims }
                .onFailure { error = it.message ?: "eSIM history could not be loaded" }
            loading = false
        }
    }

    private fun filteredEsims(): List<MobileEsim> {
        val q = query.trim().lowercase()
        return esims.filter { selectedFilter.matches(it.historyStatus()) }.filter { esim ->
            q.isBlank() || listOf(esim.displayCustomerName(), esim.displayCustomerSubtitle(), esim.iccid, esim.displayPackageName(), esim.displayProviderName(), esim.orderNumber, esim.statusLabel()).any { it.orEmpty().lowercase().contains(q) }
        }.sortedByDescending { it.createdAt.orEmpty() }
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
        startActivity(Intent(this, LoginActivity::class.java).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK) })
        finish()
        return null
    }
}

private enum class HistoryFilter(val label: String) {
    ALL("All"), ACTIVE("Active"), PENDING("Pending"), EXPIRED("Expired");
    fun matches(status: HistoryStatus): Boolean = when (this) {
        ALL -> true
        ACTIVE -> status == HistoryStatus.ACTIVE || status == HistoryStatus.READY
        PENDING -> status == HistoryStatus.PENDING
        EXPIRED -> status == HistoryStatus.EXPIRED
    }
}

internal enum class HistoryStatus(val label: String) { ACTIVE("Active"), READY("Activated"), PENDING("Pending"), EXPIRED("Expired"), DISABLED("Cancelled") }

@Composable
private fun EsimHistoryMockupScreen(esims: List<MobileEsim>, selectedFilter: HistoryFilter, query: String, loading: Boolean, error: String?, onBack: () -> Unit, onFilterChange: (HistoryFilter) -> Unit, onQueryChange: (String) -> Unit, onReset: () -> Unit, onRefresh: () -> Unit, onOpenDetail: (MobileEsim) -> Unit) {
    MaterialTheme {
        Surface(Modifier.fillMaxSize(), color = HistoryBg) {
            Column(Modifier.fillMaxSize().padding(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ArrowBack, null, tint = HistoryText, modifier = Modifier.size(30.dp).clickable(onClick = onBack))
                    Text("eSIM History", color = HistoryText, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(start = 18.dp).weight(1f))
                    if (loading) Text("Loading", color = HistoryBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = query, onValueChange = onQueryChange, modifier = Modifier.weight(1f).height(58.dp), singleLine = true, leadingIcon = { Icon(Icons.Default.Search, null, tint = HistoryText, modifier = Modifier.size(26.dp)) }, placeholder = { Text("Search customer, ICCID...", color = HistoryMuted, fontSize = 14.sp) }, shape = RoundedCornerShape(10.dp))
                    OutlinedButton(onClick = onRefresh, shape = RoundedCornerShape(10.dp), modifier = Modifier.size(width = 58.dp, height = 58.dp), contentPadding = ButtonDefaults.ContentPadding) { Icon(Icons.Default.FilterList, null, tint = HistoryText, modifier = Modifier.size(22.dp)) }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterBox(Icons.Default.CalendarMonth, "Date", Modifier.weight(1f))
                    FilterBox(Icons.Default.SignalCellularAlt, "Provider", Modifier.weight(1.2f))
                    Text("Reset", color = HistoryBlue, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp).clickable(onClick = onReset))
                }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    HistoryFilter.values().forEach { filter -> StatusFilterChip(filter.label, selectedFilter == filter) { onFilterChange(filter) } }
                }
                if (!error.isNullOrBlank()) InfoCard("Unable to load history", error)
                if (!loading && esims.isEmpty()) InfoCard("No eSIM history", "No matching purchase records found.")
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) { items(esims) { esim -> HistoryMockupCard(esim, onOpenDetail) } }
            }
        }
    }
}

@Composable
private fun StatusFilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, shape = RoundedCornerShape(50), modifier = Modifier.height(36.dp), colors = ButtonDefaults.outlinedButtonColors(containerColor = if (selected) HistoryBlue else Color.White), border = BorderStroke(1.dp, if (selected) HistoryBlue else HistoryBorder)) { Text(label, color = if (selected) Color.White else HistoryMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
}

@Composable
private fun FilterBox(icon: ImageVector, label: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    OutlinedButton(onClick = { onClick?.invoke() }, shape = RoundedCornerShape(10.dp), modifier = modifier.height(42.dp), contentPadding = ButtonDefaults.ContentPadding) {
        Icon(icon, null, tint = HistoryMuted, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(4.dp)); Text(label, color = HistoryMuted, fontWeight = FontWeight.Medium, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis); Text("⌄", color = HistoryMuted, fontSize = 12.sp, modifier = Modifier.padding(start = 2.dp))
    }
}

@Composable
private fun HistoryMockupCard(esim: MobileEsim, onOpenDetail: (MobileEsim) -> Unit) {
    val status = esim.historyStatus()
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(Color.White), border = BorderStroke(1.dp, HistoryBorder), elevation = CardDefaults.cardElevation(2.dp)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            CustomerAvatar(esim)
            Column(Modifier.padding(start = 12.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) { Text(esim.displayCustomerName(), color = HistoryText, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(esim.displayCustomerSubtitle(), color = HistoryMuted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    StatusPill(status.label, status)
                    Icon(Icons.Default.MoreVert, null, tint = HistoryMuted, modifier = Modifier.padding(start = 4.dp).size(20.dp))
                }
                CardLine(Icons.Default.Business, "Provider", esim.displayProviderName())
                CardLine(Icons.Default.Inventory2, "Package", esim.displayPackageName())
                CardLine(Icons.Default.SimCard, "ICCID", esim.shortIccid() ?: "--")
                CardLine(Icons.Default.CalendarMonth, "Purchase", esim.createdAt.prettyDate())
                CardLine(Icons.Default.SignalCellularAlt, "Data", esim.displayDataLabel())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { OutlinedButton(onClick = { onOpenDetail(esim) }, shape = RoundedCornerShape(7.dp), border = BorderStroke(1.dp, HistoryBlue), modifier = Modifier.height(42.dp)) { Text("View Detail", color = HistoryBlue, fontWeight = FontWeight.Bold, fontSize = 14.sp) } }
            }
        }
    }
}

@Composable
private fun CustomerAvatar(esim: MobileEsim) {
    val initials = esim.displayCustomerName().split(" ", "#").filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }.ifBlank { "R2" }
    Box(Modifier.size(46.dp).clip(CircleShape).background(Color(0xFFEAF0FF)), contentAlignment = Alignment.Center) { Text(initials.take(2), color = HistoryBlue, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold) }
}

@Composable
private fun CardLine(icon: ImageVector, label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = HistoryMuted, modifier = Modifier.size(16.dp)); Text(label, color = HistoryMuted, fontSize = 13.sp, modifier = Modifier.padding(start = 10.dp).width(84.dp)); Text(value, color = HistoryText, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)) }
}

@Composable
private fun StatusPill(label: String, status: HistoryStatus) {
    val bg = when (status) { HistoryStatus.ACTIVE, HistoryStatus.READY -> HistoryGreenBg; HistoryStatus.PENDING -> HistoryYellowBg; HistoryStatus.EXPIRED -> HistoryRedBg; HistoryStatus.DISABLED -> HistoryGrayBg }
    val fg = when (status) { HistoryStatus.ACTIVE, HistoryStatus.READY -> HistoryGreen; HistoryStatus.PENDING -> HistoryYellow; HistoryStatus.EXPIRED -> HistoryRed; HistoryStatus.DISABLED -> HistoryGray }
    Box(Modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 9.dp, vertical = 5.dp)) { Text(label, color = fg, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1) }
}

@Composable
private fun InfoCard(title: String, subtitle: String) { Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(Color.White), border = BorderStroke(1.dp, HistoryBorder)) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { Text(title, color = HistoryText, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold); Text(subtitle, color = HistoryMuted, fontSize = 13.sp) } } }

internal fun MobileEsim.historyStatus(): HistoryStatus { val label = statusLabel()?.lowercase().orEmpty(); return when { label.contains("active") -> HistoryStatus.ACTIVE; label.contains("ready") || label.contains("activated") -> HistoryStatus.READY; label.contains("pending") || label.contains("processing") -> HistoryStatus.PENDING; label.contains("expired") || label.contains("depleted") || label.contains("terminated") -> HistoryStatus.EXPIRED; else -> HistoryStatus.DISABLED } }
internal fun HistoryStatus.historyColor(): Color = when (this) { HistoryStatus.ACTIVE, HistoryStatus.READY -> HistoryGreen; HistoryStatus.PENDING -> HistoryYellow; HistoryStatus.EXPIRED -> HistoryRed; HistoryStatus.DISABLED -> HistoryGray }
internal fun MobileEsim.displayProviderName(): String = when (provider?.trim()?.lowercase()) { "tgt" -> "Orange"; "travroam" -> "Roam2World"; "airhubapp" -> "Vodafone"; else -> provider?.trim()?.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }?.ifBlank { "--" } ?: "--" }
internal fun MobileEsim.displayPackageName(): String { val cleaned = packageName.orEmpty().replace("【ESIM】", "", true).replace("[ESIM]", "", true).replace("ESIM", "eSIM", true).replace("  ", " ").trim(' ', '-', '|'); val data = displayDataLabel().takeIf { it != "--" }; val validity = displayValidityLabel().takeIf { it != "--" }; return listOfNotNull(cleaned.ifBlank { displayProviderName() }, data, validity).distinct().joinToString(" - ") }
internal fun MobileEsim.displayDataLabel(): String = formatMbToGb(dataRemaining ?: dataUsed ?: packageName) ?: "--"
internal fun MobileEsim.displayValidityLabel(): String { val raw = packageName.orEmpty().lowercase(); val days = raw.split(" ").firstOrNull { it.toIntOrNull() != null && raw.contains("day") }; return days?.let { "$it Days" } ?: expiresAt.prettyDate().takeIf { it != "--" } ?: "--" }
internal fun MobileEsim.displayCustomerName(): String = customerName() ?: customerEmail?.substringBefore('@')?.replace('.', ' ')?.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() } ?: orderNumber?.let { "Order #$it" } ?: displayProviderName().takeIf { it != "--" }?.let { "$it Customer" } ?: "B2B Customer"
internal fun MobileEsim.displayCustomerSubtitle(): String = customerPhone?.takeIf { it.isNotBlank() && it != "null" } ?: customerEmail?.takeIf { it.isNotBlank() && it != "null" } ?: orderNumber?.let { "Order #$it" } ?: shortIccid()?.let { "ICCID $it" } ?: "No customer contact"
internal fun MobileEsim.shortIccid(): String? { val digits = iccid?.trim().orEmpty(); if (digits.isBlank()) return null; return if (digits.length > 12) "${digits.take(6)}...${digits.takeLast(4)}" else digits }
private fun formatMbToGb(rawValue: String?): String? { val raw = rawValue?.trim().orEmpty(); if (raw.isBlank()) return null; val number = raw.replace(',', '.').split(" ").firstOrNull { it.replace(".", "").all { c -> c.isDigit() } }?.toDoubleOrNull() ?: return null; val gb = if (number >= 1024.0) number / 1024.0 else number; val text = if (gb % 1.0 == 0.0) gb.toInt().toString() else String.format(Locale.US, "%.1f", gb); return "${text}GB" }
internal fun String?.prettyDate(): String { val raw = this?.trim().orEmpty(); if (raw.isBlank()) return "--"; return runCatching { OffsetDateTime.parse(raw).format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)) }.getOrDefault(raw.take(12)) }
