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
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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

private val HistoryOrange = Color(0xFFFF7900)
private val HistoryText = Color(0xFF17181C)
private val HistoryMuted = Color(0xFF6B7280)
private val HistoryBg = Color(0xFFF7F9FC)
private val HistoryBorder = Color(0xFFE5E7EB)
private val HistoryGreen = Color(0xFF16A34A)
private val HistoryRed = Color(0xFFDC2626)
private val HistoryYellow = Color(0xFFF59E0B)

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
            MobileEsimHistoryScreen(
                esims = filteredEsims(),
                allEsims = esims,
                selectedFilter = selectedFilter,
                query = query,
                loading = loading,
                error = error,
                onBack = { finish() },
                onRefresh = { loadEsims() },
                onFilterChange = { selectedFilter = it },
                onQueryChange = { query = it },
                onOpenDetail = { startActivity(MobileEsimHistoryDetailActivity.createIntent(this, it)) }
            )
        }
        loadEsims()
    }

    private fun configureSystemBars() {
        window.statusBarColor = AndroidColor.rgb(247, 249, 252)
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

private enum class HistoryStatus(val label: String) {
    ACTIVE("Active"), READY("Ready"), PENDING("Pending"), EXPIRED("Expired"), DISABLED("Disabled")
}

@Composable
private fun MobileEsimHistoryScreen(
    esims: List<MobileEsim>,
    allEsims: List<MobileEsim>,
    selectedFilter: HistoryFilter,
    query: String,
    loading: Boolean,
    error: String?,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onFilterChange: (HistoryFilter) -> Unit,
    onQueryChange: (String) -> Unit,
    onOpenDetail: (MobileEsim) -> Unit
) {
    MaterialTheme {
        Surface(Modifier.fillMaxSize(), color = HistoryBg) {
            Column(Modifier.fillMaxSize().padding(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ArrowBack, null, tint = HistoryText, modifier = Modifier.size(30.dp).clickable(onClick = onBack))
                    Text("eSIM History", color = HistoryText, fontSize = 27.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(start = 16.dp).weight(1f))
                    if (loading) CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = HistoryOrange)
                    Icon(Icons.Default.Refresh, null, tint = HistoryOrange, modifier = Modifier.padding(start = 12.dp).size(28.dp).clickable(onClick = onRefresh))
                }

                HistorySummaryCard(allEsims)

                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    label = { Text("Search ICCID, package, provider") },
                    shape = RoundedCornerShape(18.dp)
                )

                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HistoryFilter.values().forEach { filter ->
                        val selected = selectedFilter == filter
                        if (selected) {
                            Button(onClick = { onFilterChange(filter) }, shape = RoundedCornerShape(50), colors = ButtonDefaults.buttonColors(containerColor = HistoryOrange)) {
                                Text(filter.label, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            OutlinedButton(onClick = { onFilterChange(filter) }, shape = RoundedCornerShape(50)) {
                                Text(filter.label, color = HistoryText)
                            }
                        }
                    }
                }

                if (!error.isNullOrBlank()) {
                    HistoryInfoCard("Unable to load history", error)
                }

                if (!loading && esims.isEmpty()) {
                    HistoryInfoCard("No eSIM history found", "Purchases and installed profiles will appear here.")
                }

                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f)) {
                    items(esims) { esim ->
                        HistoryEsimCard(esim, onOpenDetail)
                    }
                }
            }
        }
    }
}

@Composable
private fun HistorySummaryCard(esims: List<MobileEsim>) {
    val active = esims.count { it.historyStatus() == HistoryStatus.ACTIVE }
    val pending = esims.count { it.historyStatus() == HistoryStatus.PENDING }
    val expired = esims.count { it.historyStatus() == HistoryStatus.EXPIRED }
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(Color(0xFF17181C))) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(52.dp).clip(CircleShape).background(HistoryOrange), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.ReceiptLong, null, tint = Color.White)
                }
                Column(Modifier.padding(start = 14.dp)) {
                    Text("Profile lifecycle", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                    Text("Purchases, activation status and renewal history", color = Color.White.copy(alpha = .72f), fontSize = 13.sp)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HistoryMetric("Total", esims.size.toString(), Color.White, Modifier.weight(1f))
                HistoryMetric("Active", active.toString(), HistoryGreen, Modifier.weight(1f))
                HistoryMetric("Pending", pending.toString(), HistoryYellow, Modifier.weight(1f))
                HistoryMetric("Expired", expired.toString(), HistoryRed, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun HistoryMetric(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Card(modifier, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(Color.White.copy(alpha = .10f))) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, color = color, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            Text(label, color = Color.White.copy(alpha = .75f), fontSize = 11.sp)
        }
    }
}

@Composable
private fun HistoryEsimCard(esim: MobileEsim, onOpenDetail: (MobileEsim) -> Unit) {
    val status = esim.historyStatus()
    Card(
        Modifier.fillMaxWidth().clickable { onOpenDetail(esim) },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(Color.White),
        border = BorderStroke(1.dp, HistoryBorder),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(HistoryOrange.copy(alpha = .12f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.SimCard, null, tint = HistoryOrange)
                }
                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                    Text(esim.packageName ?: "Roam2World eSIM", color = HistoryText, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(listOfNotNull(esim.provider, esim.shortIccid()).joinToString(" • "), color = HistoryMuted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                StatusChip(status.label, status.historyColor())
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Order ${esim.orderNumber?.let { "#$it" } ?: "--"}", color = HistoryMuted, fontSize = 12.sp)
                Text(esim.createdAt.prettyDate(), color = HistoryMuted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun HistoryInfoCard(title: String, subtitle: String) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(Color.White), border = BorderStroke(1.dp, HistoryBorder)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = HistoryText, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
            Text(subtitle, color = HistoryMuted, fontSize = 13.sp)
        }
    }
}

@Composable
private fun StatusChip(label: String, color: Color) {
    Box(Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = .12f)).padding(horizontal = 11.dp, vertical = 6.dp)) {
        Text(label, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

internal fun MobileEsim.historyStatus(): HistoryStatus {
    val label = statusLabel()?.lowercase().orEmpty()
    return when {
        label.contains("active") -> HistoryStatus.ACTIVE
        label.contains("ready") -> HistoryStatus.READY
        label.contains("pending") || label.contains("processing") -> HistoryStatus.PENDING
        label.contains("expired") || label.contains("depleted") || label.contains("terminated") -> HistoryStatus.EXPIRED
        else -> HistoryStatus.DISABLED
    }
}

internal fun HistoryStatus.historyColor(): Color = when (this) {
    HistoryStatus.ACTIVE, HistoryStatus.READY -> HistoryGreen
    HistoryStatus.PENDING -> HistoryYellow
    HistoryStatus.EXPIRED -> HistoryRed
    HistoryStatus.DISABLED -> HistoryMuted
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
        OffsetDateTime.parse(raw).format(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.US))
    }.getOrDefault(raw.take(16))
}
