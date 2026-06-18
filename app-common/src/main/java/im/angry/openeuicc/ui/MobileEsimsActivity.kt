package im.angry.openeuicc.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import im.angry.openeuicc.ui.compose.components.R2WCard
import im.angry.openeuicc.ui.compose.components.R2WPrimaryButton
import im.angry.openeuicc.ui.compose.components.R2WProgressRow
import im.angry.openeuicc.ui.compose.components.R2WSecondaryButton
import im.angry.openeuicc.ui.compose.components.R2WStatusBadge
import im.angry.openeuicc.ui.compose.theme.Background
import im.angry.openeuicc.ui.compose.theme.Border
import im.angry.openeuicc.ui.compose.theme.Danger
import im.angry.openeuicc.ui.compose.theme.Primary
import im.angry.openeuicc.ui.compose.theme.PrimaryLight
import im.angry.openeuicc.ui.compose.theme.TextPrimary
import im.angry.openeuicc.ui.compose.theme.TextSecondary
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
                onQueryChange = { query = it },
                onFilterChange = {
                    selectedFilter = it
                    initialFilter = null
                },
                onRefresh = { loadEsims() },
                onOpenDetail = { esim ->
                    startActivity(MobileEsimDetailActivity.createIntent(this, esim))
                },
                onRenew = { esim -> openRenewal(esim) },
                onOpenNative = { openNativeEsimActivity() }
            )
        }

        loadEsims()
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
                .onSuccess { allEsims = it.esims }
                .onFailure { errorMessage = it.message ?: getString(R.string.mobile_esims_load_failed) }

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
    onOpenNative: () -> Unit
) {
    val totalRemainingLabel = allEsims.mapNotNull { it.dataRemaining?.takeIf { value -> value.isNotBlank() } }.firstOrNull()

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = Background) {
            Column(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 116.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = if (initialFilter == MobileEsimFilters.FILTER_EXPIRED_SOON) "Check GB" else "My eSIMs",
                        color = TextPrimary,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Monitor purchased eSIMs, remaining data, ICCID and OpenEUICC install status.",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )

                    R2WCard {
                        Text(
                            text = "Total eSIMs",
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "${filteredEsims.size} / ${allEsims.size}",
                            color = Primary,
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (loading) "List is refreshing..." else "Total remaining: ${totalRemainingLabel ?: "check individual eSIMs"}",
                            color = TextSecondary
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            R2WSecondaryButton("Refresh", onRefresh, Modifier.weight(1f))
                            R2WPrimaryButton("Device eSIM", onOpenNative, Modifier.weight(1f))
                        }
                    }

                    error?.takeIf { it.isNotBlank() }?.let {
                        R2WCard {
                            Text("eSIM list could not be loaded", color = Danger, fontWeight = FontWeight.Bold)
                            Text(it, color = TextSecondary)
                            R2WPrimaryButton("Retry", onRefresh)
                        }
                    }

                    SearchAndFilterCard(
                        query = query,
                        selectedFilter = selectedFilter,
                        initialFilter = initialFilter,
                        onQueryChange = onQueryChange,
                        onFilterChange = onFilterChange
                    )

                    if (loading && allEsims.isEmpty()) {
                        R2WCard {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.height(20.dp), color = Primary)
                                Text("Loading eSIM list...", color = TextSecondary)
                            }
                        }
                    }

                    if (!loading && filteredEsims.isEmpty()) {
                        R2WCard {
                            Text("No eSIM found.", color = TextSecondary)
                        }
                    } else {
                        filteredEsims.forEach { esim ->
                            EsimListCard(
                                esim = esim,
                                onOpenDetail = { onOpenDetail(esim) },
                                onRenew = { onRenew(esim) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                }

                R2wBottomNav(selected = R2wBottomTab.Esims)
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
    R2WCard {
        Text("Filter", color = TextPrimary, fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Search ICCID, package, provider or status") },
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
                FilterButton(label = "Expiring Soon", selected = true, onClick = {})
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
    val bg = if (selected) Primary else Color.White
    val fg = if (selected) Color.White else TextPrimary

    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = bg),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) Primary else Border)
    ) {
        Text(label, color = fg, fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold)
    }
}

@Composable
private fun EsimListCard(
    esim: MobileEsim,
    onOpenDetail: () -> Unit,
    onRenew: () -> Unit
) {
    val status = realStatus(esim)
    val title = esim.title()
    val iccid = esim.iccid.orEmpty().ifBlank { "Pending ICCID" }
    val remaining = esim.dataRemaining?.takeIf { it.isNotBlank() }
    val meta = listOfNotNull(
        visibleProvider(esim.provider)?.takeIf { it.isNotBlank() },
        esim.expiresAt?.takeIf { it.isNotBlank() }?.let { "Expires: ${formatEsimDate(it)}" },
        remaining?.let { "Remaining: $it" }
    ).joinToString("  •  ")

    R2WCard(
        modifier = Modifier.clickable { onOpenDetail() }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = title,
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = iccid,
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            R2WStatusBadge(status.label, status.raw)
        }

        if (meta.isNotBlank()) {
            Text(text = meta, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
        }

        remaining?.let {
            R2WProgressRow("Remaining data", it, parseRemainingProgress(it))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            R2WPrimaryButton("Detail", onOpenDetail, Modifier.weight(1f))
            if (canRenew(esim, status)) {
                R2WSecondaryButton("Renew", onRenew, Modifier.weight(1f))
            }
        }
    }
}

private data class EsimsDisplayStatus(
    val label: String,
    val raw: String
)

private enum class EsimFilter(val label: String) {
    ACTIVE("Active"),
    PENDING("Pending"),
    EXPIRED("Expired"),
    ALL("All");

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

private fun parseRemainingProgress(value: String): Float {
    val numbers = Regex("""\d+(?:\.\d+)?""").findAll(value).mapNotNull { it.value.toFloatOrNull() }.toList()
    return when {
        numbers.size >= 2 && numbers[1] > 0f -> (numbers[0] / numbers[1]).coerceIn(0f, 1f)
        numbers.size == 1 -> (numbers[0] / 100f).coerceIn(0f, 1f)
        else -> 0.5f
    }
}
