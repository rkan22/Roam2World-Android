package im.angry.openeuicc.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import im.angry.openeuicc.auth.AuthSession
import im.angry.openeuicc.auth.AuthTokenStore
import im.angry.openeuicc.auth.JwtUtils
import im.angry.openeuicc.auth.MobileEsim
import im.angry.openeuicc.auth.MobileEsimFilters
import im.angry.openeuicc.auth.Roam2WorldAuthApi
import im.angry.openeuicc.common.BuildConfig
import im.angry.openeuicc.common.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class MobileEsimsActivity : ComponentActivity() {
    private val tokenStore by lazy { AuthTokenStore(this) }
    private val authApi by lazy { Roam2WorldAuthApi(BuildConfig.ROAM2WORLD_API_BASE_URL) }

    private var allEsims by mutableStateOf<List<MobileEsim>>(emptyList())
    private var selectedFilter by mutableStateOf(EsimFilter.ALL)
    private var initialFilter: String? = null
    private var query by mutableStateOf("")
    private var loading by mutableStateOf(false)
    private var errorMessage by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        initialFilter = intent.getStringExtra(MobileEsimFilters.FILTER_EXTRA_KEY)?.trim()

        setContent {
            MobileEsimsScreen(
                allEsims = allEsims,
                filteredEsims = filteredEsims(),
                query = query,
                selectedFilter = selectedFilter,
                initialFilter = initialFilter,
                loading = loading,
                error = errorMessage,
                onQueryChange = {
                    query = it
                },
                onFilterChange = {
                    selectedFilter = it
                    initialFilter = null
                },
                onRefresh = { loadEsims() },
                onOpenDetail = { esim ->
                    startActivity(MobileEsimDetailActivity.createIntent(this, esim))
                },
                onRenew = { esim -> openRenewal(esim) },
                onOpenDashboard = { openDashboardActivity() },
                onOpenPackages = { openPackagesActivity() },
                onOpenWallet = { openWalletActivity() },
                onOpenMore = { openMoreActivity() },
                onOpenNative = { openNativeEsimActivity() }
            )
        }

        loadEsims()
    }

    override fun onResume() {
        super.onResume()
    }

    private fun loadEsims() {
        lifecycleScope.launch {
            errorMessage = null
            loading = true

            val session = activeSessionOrReturnToLogin()
            if (session == null) {
                loading = false
                return@launch
            }

            val result = runCatching { authApi.esims(session) }

            result
                .onSuccess {
                    allEsims = it.esims
                }
                .onFailure {
                    errorMessage = it.message ?: getString(R.string.mobile_esims_load_failed)
                }

            loading = false
        }
    }

    private fun filteredEsims(): List<MobileEsim> {
        val normalizedQuery = query.trim().lowercase()
        val baseEsims = if (initialFilter == MobileEsimFilters.FILTER_EXPIRED_SOON) {
            allEsims.filter { MobileEsimFilters.isExpiredSoon(it) }
        } else {
            allEsims
        }

        val statusFiltered = if (initialFilter == MobileEsimFilters.FILTER_EXPIRED_SOON) {
            baseEsims
        } else {
            baseEsims.filter { selectedFilter.matches(realStatus(it)) }
        }

        return statusFiltered.filter { esim ->
            val displayStatus = realStatus(esim)
            normalizedQuery.isBlank() || listOfNotNull(
                esim.iccid,
                esim.provider,
                PackageNameCleaner.clean(esim.packageName),
                esim.orderNumber,
                esim.status,
                displayStatus.label
            ).any { it.lowercase().contains(normalizedQuery) }
        }
    }

    private suspend fun activeSessionOrReturnToLogin(): AuthSession? {
        val savedSession = withContext(Dispatchers.IO) {
            tokenStore.getSession()
        } ?: return redirectToLogin()

        if (!JwtUtils.isExpired(savedSession.accessToken)) return savedSession

        val refreshed = runCatching {
            authApi.refresh(savedSession)
        }.getOrNull() ?: return redirectToLogin()

        withContext(Dispatchers.IO) {
            tokenStore.save(refreshed)
        }
        return refreshed
    }

    private fun openRenewal(esim: MobileEsim) {
        val provider = esim.provider.orEmpty().lowercase()
        if (provider.contains("airhub") || provider.contains("vodafone")) {
            startActivity(Intent(this, VodafoneRenewalActivity::class.java).apply {
                putExtra("renew.iccid", esim.iccid)
            })
        } else {
            startActivity(Intent(this, TgtSimRechargeActivity::class.java).apply {
                putExtra("renew.iccid", esim.iccid)
            })
        }
    }

    private fun openDashboardActivity() {
        startActivity(
            Intent(this, DashboardActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            }
        )
    }

    private fun openWalletActivity() {
        startActivity(
            Intent(this, WalletActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            }
        )
    }

    private fun openPackagesActivity() {
        startActivity(
            Intent(this, PackagesActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            }
        )
    }

    private fun openMoreActivity() {
        startActivity(
            Intent(this, MoreActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            }
        )
    }

    private fun openNativeEsimActivity() {
        val target = targetActivityName(DashboardActivity.META_ESIM_ACTIVITY)
        if (target.isNullOrBlank()) {
            errorMessage = getString(R.string.dashboard_missing_esim_target)
            return
        }
        startActivity(Intent().setClassName(this, target))
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

    private fun targetActivityName(key: String): String? {
        val appInfo = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
        return appInfo.metaData?.getString(key)
    }
}

@Composable
private fun MobileEsimsScreen(
    allEsims: List<MobileEsim>,
    filteredEsims: List<MobileEsim>,
    query: String,
    selectedFilter: EsimFilter,
    initialFilter: String?,
    loading: Boolean,
    error: String?,
    onQueryChange: (String) -> Unit,
    onFilterChange: (EsimFilter) -> Unit,
    onRefresh: () -> Unit,
    onOpenDetail: (MobileEsim) -> Unit,
    onRenew: (MobileEsim) -> Unit,
    onOpenDashboard: () -> Unit,
    onOpenPackages: () -> Unit,
    onOpenWallet: () -> Unit,
    onOpenMore: () -> Unit,
    onOpenNative: () -> Unit
) {
    val orange = Color(0xFFFF6A00)
    val bg = Color(0xFFF7F7FA)

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = bg) {
            Column(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.weight(1f)) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                HeroEsimsCard(
                    total = allEsims.size,
                    shown = filteredEsims.size,
                    loading = loading,
                    orange = orange,
                    onRefresh = onRefresh,
                    onOpenNative = onOpenNative
                )

                if (!error.isNullOrBlank()) {
                    ErrorCard(error = error, onRetry = onRefresh)
                }

                SearchAndFilterCard(
                    query = query,
                    selectedFilter = selectedFilter,
                    initialFilter = initialFilter,
                    onQueryChange = onQueryChange,
                    onFilterChange = onFilterChange
                )

                if (loading && allEsims.isEmpty()) {
                    InfoCard(title = "Yükleniyor") {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CircularProgressIndicator(modifier = Modifier.height(20.dp))
                            Text("eSIM listesi alınıyor...")
                        }
                    }
                }

                if (!loading && filteredEsims.isEmpty()) {
                    EmptyCard("eSIM bulunamadı.")
                } else {
                    filteredEsims.forEach { esim ->
                        EsimListCard(
                            esim = esim,
                            orange = orange,
                            onOpenDetail = { onOpenDetail(esim) },
                            onRenew = { onRenew(esim) }
                        )
                    }
                }

Spacer(modifier = Modifier.height(12.dp))
            }
        }
    

                BottomNavCard(
                    orange = orange,
                    onOpenDashboard = onOpenDashboard,
                    onOpenPackages = onOpenPackages,
                    onOpenWallet = onOpenWallet,
                    onOpenMore = onOpenMore
                )
            }
        }
}
}

@Composable
private fun HeroEsimsCard(
    total: Int,
    shown: Int,
    loading: Boolean,
    orange: Color,
    onRefresh: () -> Unit,
    onOpenNative: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(30.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF17181C))
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "eSIM’lerim",
                color = orange,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "$shown / $total eSIM",
                color = Color.White,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = if (loading) "Liste güncelleniyor..." else "Satın aldığın eSIM profillerini yönet.",
                color = Color.White.copy(alpha = 0.74f),
                style = MaterialTheme.typography.bodyMedium
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onRefresh,
                    colors = ButtonDefaults.buttonColors(containerColor = orange),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text("Yenile")
                }

                OutlinedButton(
                    onClick = onOpenNative,
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text("Cihaz eSIM")
                }
            }
        }
    }
}

@Composable
private fun SearchAndFilterCard(
    query: String,
    selectedFilter: EsimFilter,
    initialFilter: String?,
    onQueryChange: (String) -> Unit,
    onFilterChange: (EsimFilter) -> Unit
) {
    InfoCard(title = "Filtrele") {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("ICCID, paket, sağlayıcı veya durum ara") },
            singleLine = true
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            EsimFilter.entries.forEach { filter ->
                FilterButton(
                    label = filter.label,
                    selected = initialFilter != MobileEsimFilters.FILTER_EXPIRED_SOON && selectedFilter == filter,
                    onClick = { onFilterChange(filter) }
                )
            }

            if (initialFilter == MobileEsimFilters.FILTER_EXPIRED_SOON) {
                FilterButton(
                    label = "Yakında Bitecek",
                    selected = true,
                    onClick = {}
                )
            }
        }
    }
}

@Composable
private fun FilterButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val bg = if (selected) Color(0xFFFF6A00) else Color.White
    val fg = if (selected) Color.White else Color(0xFF17181C)

    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = bg)
    ) {
        Text(label, color = fg)
    }
}

@Composable
private fun EsimListCard(
    esim: MobileEsim,
    orange: Color,
    onOpenDetail: () -> Unit,
    onRenew: () -> Unit
) {
    val status = realStatus(esim)
    val title = esim.title()
    val iccid = esim.iccid.orEmpty().ifBlank { "Pending ICCID" }
    val meta = listOfNotNull(
        visibleProvider(esim.provider)?.takeIf { it.isNotBlank() },
        esim.expiresAt?.takeIf { it.isNotBlank() }?.let { "Expires: ${formatEsimDate(it)}" },
        esim.dataRemaining?.takeIf { it.isNotBlank() }?.let { "Remaining: $it" }
    ).joinToString("  •  ")

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenDetail() },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        color = Color(0xFF17181C),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = iccid,
                        color = Color(0xFF686B73),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                StatusPill(status.label)
            }

            if (meta.isNotBlank()) {
                Text(
                    text = meta,
                    color = Color(0xFF686B73),
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onOpenDetail,
                    colors = ButtonDefaults.buttonColors(containerColor = orange),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Detay")
                }

                if (canRenew(esim, status)) {
                    OutlinedButton(
                        onClick = onRenew,
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("Renew")
                    }
                }
            }
        }
    }
}

@Composable
private fun BottomNavCard(
    orange: Color,
    onOpenDashboard: () -> Unit,
    onOpenPackages: () -> Unit,
    onOpenWallet: () -> Unit,
    onOpenMore: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            EsimsBottomNavText("Home", onOpenDashboard)
            EsimsBottomNavText("Packages", onOpenPackages)
            EsimsBottomNavText("Wallet", onOpenWallet)
            Text(
                text = "eSIMs",
                color = orange,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Black
            )
            EsimsBottomNavText("More", onOpenMore)
        }
    }
}

@Composable
private fun EsimsBottomNavText(
    text: String,
    onClick: () -> Unit
) {
    Text(
        text = text,
        color = Color(0xFF6B7280),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 8.dp)
    )
}





@Composable
private fun InfoCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = title,
                color = Color(0xFF17181C),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            HorizontalDivider()
            content()
        }
    }
}

@Composable
private fun StatusPill(label: String) {
    val normalized = label.lowercase()
    val colors = when {
        normalized.contains("active") || normalized.contains("ready") || normalized.contains("provision") ->
            Color(0xFFDCFCE7) to Color(0xFF166534)
        normalized.contains("expired") || normalized.contains("used") || normalized.contains("terminated") ->
            Color(0xFFFEE2E2) to Color(0xFFB91C1C)
        else -> Color(0xFFFEF9C3) to Color(0xFF854D0E)
    }

    Text(
        text = label,
        color = colors.second,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .background(colors.first, RoundedCornerShape(999.dp))
            .padding(horizontal = 12.dp, vertical = 7.dp)
    )
}

@Composable
private fun ErrorCard(error: String, onRetry: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEAEA))
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("eSIM listesi yüklenemedi", color = Color(0xFFB91C1C), fontWeight = FontWeight.Bold)
            Text(error, color = Color(0xFF7F1D1D))
            OutlinedButton(onClick = onRetry) {
                Text("Tekrar Dene")
            }
        }
    }
}

@Composable
private fun EmptyCard(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(18.dp),
            color = Color(0xFF686B73)
        )
    }
}

private data class EsimsDisplayStatus(
    val label: String,
    val raw: String
)

private enum class EsimFilter(val label: String) {
    ACTIVE("Aktif"),
    PENDING("Bekleyen"),
    EXPIRED("Süresi Dolan"),
    ALL("Tümü");

    fun matches(status: EsimsDisplayStatus): Boolean =
        when (this) {
            ACTIVE -> status.raw == "active" || status.raw == "ready"
            PENDING -> status.raw == "pending"
            EXPIRED -> status.raw == "expired"
            ALL -> true
        }
}

private fun visibleProvider(provider: String?): String? =
    provider?.replace("TGT", "Orange", ignoreCase = true)
        ?.replace("tgt", "Orange", ignoreCase = true)

private fun formatEsimDate(value: String): String {
    val parsed = runCatching { OffsetDateTime.parse(value) }.getOrNull() ?: return value
    return parsed.format(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.getDefault()))
}

private fun realStatus(esim: MobileEsim): EsimsDisplayStatus {
    val raw = esim.status.orEmpty().trim()
    val normalized = raw.lowercase()
    val expiresAt = parseDate(esim.expiresAt)
    val isExpiredByDate = expiresAt?.isBefore(OffsetDateTime.now()) == true
    val hasIccid = !esim.iccid.isNullOrBlank()
    val hasInstallCode = !esim.installCode().isNullOrBlank() || !esim.qrPayload().isNullOrBlank()

    return when {
        normalized.contains("expired") || normalized.contains("depleted") || normalized.contains("terminated") || isExpiredByDate ->
            EsimsDisplayStatus("Expired", "expired")
        normalized.contains("active") || normalized.contains("activated") || normalized.contains("enabled") ->
            EsimsDisplayStatus("Active", "active")
        normalized.contains("pending") || normalized.contains("processing") || normalized.contains("waiting") || normalized.contains("ordered") ->
            EsimsDisplayStatus("Pending", "pending")
        hasIccid && hasInstallCode && expiresAt != null ->
            EsimsDisplayStatus("Ready", "ready")
        hasIccid && expiresAt != null ->
            EsimsDisplayStatus("Active", "active")
        hasIccid && hasInstallCode ->
            EsimsDisplayStatus("Ready", "ready")
        hasInstallCode ->
            EsimsDisplayStatus("Ready to Install", "ready")
        hasIccid ->
            EsimsDisplayStatus("Provisioned", raw.ifBlank { "provisioned" })
        else ->
            EsimsDisplayStatus("Pending", "pending")
    }
}

private fun parseDate(value: String?): OffsetDateTime? =
    value?.takeIf { it.isNotBlank() }?.let { runCatching { OffsetDateTime.parse(it) }.getOrNull() }

private fun canRenew(esim: MobileEsim, displayStatus: EsimsDisplayStatus = realStatus(esim)): Boolean {
    val provider = esim.provider.orEmpty().lowercase()
    if (displayStatus.raw == "expired") return false
    return provider.contains("tgt") || provider.contains("airhub") || provider.contains("vodafone")
}
