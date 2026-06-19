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
        configureSystemBars()
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

    private fun configureSystemBars() {
        window.statusBarColor = AndroidColor.rgb(248, 250, 253)
        window.navigationBarColor = AndroidColor.BLACK
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = false
        }
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
        return esims
            .filter { selectedFilter.matches(it.historyStatus()) }
            .filter { esim ->
                q.isBlank() || listOf(
                    esim.customerName(),
                    esim.customerPhone,
                    esim.iccid,
                    esim.packageName,
                    esim.provider,
                    esim.orderNumber,
                    esim.statusLabel()
                ).any { it.orEmpty().lowercase().contains(q) }
            }
            .sortedByDescending { it.createdAt.orEmpty() }
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

internal enum class HistoryStatus(val label: String) {
    ACTIVE("Active"), READY("Activated"), PENDING("Pending"), EXPIRED("Expired"), DISABLED("Cancelled")
}

@Composable
private fun EsimHistoryMockupScreen(
    esims: List<MobileEsim>,
    selectedFilter: HistoryFilter,
    query: String,
    loading: Boolean,
    error: String?,
    onBack: () -> Unit,
    onFilterChange: (HistoryFilter) -> Unit,
    onQueryChange: (String) -> Unit,
    onReset: () -> Unit,
    onRefresh: () -> Unit,
    onOpenDetail: (MobileEsim) -> Unit
) {
    MaterialTheme {
        Surface(Modifier.fillMaxSize(), color = HistoryBg) {
            Column(Modifier.fillMaxSize().padding(start = 18.dp, top = 18.dp, end = 18.dp, bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ArrowBack, null, tint = HistoryText, modifier = Modifier.size(32.dp).clickable(onClick = onBack))
                    Text("eSIM History", color = HistoryText, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(start = 28.dp).weight(1f))
                    if (loading) Text("Loading", color = HistoryBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Search, null, tint = HistoryText, modifier = Modifier.size(34.dp)) },
                        placeholder = { Text("Search by customer, number, ICCID...", color = HistoryMuted) },
                        shape = RoundedCornerShape(10.dp)
                    )
                    OutlinedButton(onClick = onRefresh, shape = RoundedCornerShape(10.dp), modifier = Modifier.height(58.dp), contentPadding = ButtonDefaults.ContentPadding) {
                        Icon(Icons.Default.FilterList, null, tint = HistoryText)
                    }
                }

                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterBox(Icons.Default.CalendarMonth, "Date range")
                    FilterBox(Icons.Default.SignalCellularAlt, "Provider")
                    FilterBox(Icons.Default.SimCard, selectedFilter.label) {
                        val next = when (selectedFilter) {
                            HistoryFilter.ALL -> HistoryFilter.ACTIVE
                            HistoryFilter.ACTIVE -> HistoryFilter.PENDING
                            HistoryFilter.PENDING -> HistoryFilter.EXPIRED
                            HistoryFilter.EXPIRED -> HistoryFilter.ALL
                        }
                        onFilterChange(next)
                    }
                    Text("Reset", color = HistoryBlue, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 12.dp).clickable(onClick = onReset))
                }

                if (!error.isNullOrBlank()) {
                    InfoCard("Unable to load history", error)
                }

                if (!loading && esims.isEmpty()) {
                    InfoCard("No eSIM history", "No matching purchase records found.")
                }

                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f)) {
                    items(esims) { esim -> HistoryMockupCard(esim, onOpenDetail) }
                }
            }
        }
    }
}

@Composable
private fun FilterBox(icon: ImageVector, label: String, onClick: (() -> Unit)? = null) {
    OutlinedButton(onClick = { onClick?.invoke() }, shape = RoundedCornerShape(10.dp), modifier = Modifier.height(48.dp)) {
        Icon(icon, null, tint = HistoryMuted, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, color = HistoryMuted, fontWeight = FontWeight.Medium)
        Text("⌄", color = HistoryMuted, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun HistoryMockupCard(esim: MobileEsim, onOpenDetail: (MobileEsim) -> Unit) {
    val status = esim.historyStatus()
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(Color.White),
        border = BorderStroke(1.dp, HistoryBorder),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            CustomerAvatar(esim)
            Column(Modifier.padding(start = 14.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Text(esim.customerName() ?: "Customer", color = HistoryText, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(esim.customerPhone ?: esim.orderNumber?.let { "Order #$it" } ?: "No phone number", color = HistoryMuted, fontSize = 14.sp)
                    }
                    StatusPill(status.label, status)
                    Icon(Icons.Default.MoreVert, null, tint = HistoryMuted, modifier = Modifier.padding(start = 8.dp).size(24.dp))
                }

                CardLine(Icons.Default.Business, "Provider", esim.provider ?: "--")
                CardLine(Icons.Default.Inventory2, "Package", esim.packageName ?: "--")
                CardLine(Icons.Default.SimCard, "ICCID", esim.iccid ?: "--")
                CardLine(Icons.Default.CalendarMonth, "Purchase date", esim.createdAt.prettyDate())

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    OutlinedButton(onClick = { onOpenDetail(esim) }, shape = RoundedCornerShape(7.dp), border = BorderStroke(1.dp, HistoryBlue)) {
                        Text("View Detail", color = HistoryBlue, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomerAvatar(esim: MobileEsim) {
    val initials = (esim.customerName() ?: esim.provider ?: esim.packageName ?: "R2W")
        .split(" ")
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercase() }
        .ifBlank { "R2" }
    Box(Modifier.size(54.dp).clip(CircleShape).background(Color(0xFFEAF0FF)), contentAlignment = Alignment.Center) {
        Text(initials.take(2), color = HistoryBlue, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun CardLine(icon: ImageVector, label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = HistoryMuted, modifier = Modifier.size(18.dp))
        Text(label, color = HistoryMuted, fontSize = 14.sp, modifier = Modifier.padding(start = 14.dp).width(140.dp))
        Text(value, color = HistoryText, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun StatusPill(label: String, status: HistoryStatus) {
    val bg = when (status) {
        HistoryStatus.ACTIVE, HistoryStatus.READY -> HistoryGreenBg
        HistoryStatus.PENDING -> HistoryYellowBg
        HistoryStatus.EXPIRED -> HistoryRedBg
        HistoryStatus.DISABLED -> HistoryGrayBg
    }
    val fg = when (status) {
        HistoryStatus.ACTIVE, HistoryStatus.READY -> HistoryGreen
        HistoryStatus.PENDING -> HistoryYellow
        HistoryStatus.EXPIRED -> HistoryRed
        HistoryStatus.DISABLED -> HistoryGray
    }
    Box(Modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 12.dp, vertical = 7.dp)) {
        Text(label, color = fg, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun InfoCard(title: String, subtitle: String) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(Color.White), border = BorderStroke(1.dp, HistoryBorder)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = HistoryText, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
            Text(subtitle, color = HistoryMuted, fontSize = 13.sp)
        }
    }
}

internal fun MobileEsim.historyStatus(): HistoryStatus {
    val label = statusLabel()?.lowercase().orEmpty()
    return when {
        label.contains("active") -> HistoryStatus.ACTIVE
        label.contains("ready") || label.contains("activated") -> HistoryStatus.READY
        label.contains("pending") || label.contains("processing") -> HistoryStatus.PENDING
        label.contains("expired") || label.contains("depleted") || label.contains("terminated") -> HistoryStatus.EXPIRED
        else -> HistoryStatus.DISABLED
    }
}

internal fun HistoryStatus.historyColor(): Color = when (this) {
    HistoryStatus.ACTIVE, HistoryStatus.READY -> HistoryGreen
    HistoryStatus.PENDING -> HistoryYellow
    HistoryStatus.EXPIRED -> HistoryRed
    HistoryStatus.DISABLED -> HistoryGray
}

internal fun MobileEsim.shortIccid(): String? {
    val digits = iccid?.trim().orEmpty()
    if (digits.isBlank()) return null
    return if (digits.length > 12) "${digits.take(6)}...${digits.takeLast(4)}" else digits
}

internal fun String?.prettyDate(): String {
    val raw = this?.trim().orEmpty()
    if (raw.isBlank()) return "--"
    return runCatching {
        OffsetDateTime.parse(raw).format(DateTimeFormatter.ofPattern("MMM d, yyyy hh:mm a", Locale.US))
    }.getOrDefault(raw.take(16))
}
