package im.angry.openeuicc.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    private var selectedFilter by mutableStateOf(EsimFilter.ACTIVE)
    private var initialFilter: String? = null
    private var query by mutableStateOf("")
    private var loading by mutableStateOf(false)
    private var errorMessage by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = android.graphics.Color.rgb(244, 246, 250)
        window.navigationBarColor = android.graphics.Color.WHITE
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        }

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

            val result = runCatching { authApi.parseMobileEsims(fetchAllMobileEsimsPages(session)) }

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


    private suspend fun fetchAllMobileEsimsPages(session: im.angry.openeuicc.auth.AuthSession): org.json.JSONObject = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val base = im.angry.openeuicc.common.BuildConfig.ROAM2WORLD_API_BASE_URL.trimEnd('/')
        val merged = org.json.JSONArray()
        var totalCount = 0
        var page = 1
        var keepGoing = true
        val seenIds = linkedSetOf<String>()

        while (keepGoing && page <= 50) {
            val url = java.net.URL("$base/api/v1/mobile/esims/?page=$page&page_size=100&limit=100")
            val connection = url.openConnection() as java.net.HttpURLConnection

            try {
                connection.requestMethod = "GET"
                connection.setRequestProperty("Accept", "application/json")
                connection.setRequestProperty("Authorization", session.authorizationHeader)
                connection.connectTimeout = 15000
                connection.readTimeout = 15000

                val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
                val body = stream.bufferedReader().use { it.readText() }

                if (connection.responseCode !in 200..299) {
                    throw IllegalStateException("HTTP ${connection.responseCode}: $body")
                }

                val response = org.json.JSONObject(body)
                val data = response.optJSONObject("data") ?: response
                val pageArray = data.optJSONArray("esims")
                    ?: data.optJSONArray("results")
                    ?: response.optJSONArray("esims")
                    ?: response.optJSONArray("results")
                    ?: org.json.JSONArray()

                totalCount = maxOf(
                    totalCount,
                    data.optInt("count", 0),
                    data.optInt("total", 0),
                    response.optInt("count", 0),
                    response.optInt("total", 0)
                )

                var addedThisPage = 0
                for (i in 0 until pageArray.length()) {
                    val item = pageArray.optJSONObject(i) ?: continue
                    val key = listOf(
                        item.optString("id"),
                        item.optString("esim_id"),
                        item.optString("iccid"),
                        item.optString("order_id")
                    ).firstOrNull { it.isNotBlank() } ?: item.toString()

                    if (seenIds.add(key)) {
                        merged.put(item)
                        addedThisPage++
                    }
                }

                val next = data.optString("next", response.optString("next", ""))
                keepGoing = when {
                    pageArray.length() == 0 -> false
                    addedThisPage == 0 -> false
                    totalCount > 0 && merged.length() >= totalCount -> false
                    next.isNotBlank() && next != "null" -> true
                    totalCount > 0 && merged.length() < totalCount -> true
                    pageArray.length() >= 35 -> true
                    else -> false
                }

                page++
            } finally {
                connection.disconnect()
            }
        }

        org.json.JSONObject().apply {
            put("success", true)
            put("data", org.json.JSONObject().apply {
                put("count", if (totalCount > 0) totalCount else merged.length())
                put("esims", merged)
            })
        }
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
    val blue = Color(0xFF1263F1)
    val bg = Color(0xFFF4F6FA)

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = bg) {
            Column(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(start = 16.dp, top = 20.dp, end = 16.dp, bottom = 118.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    B2bEsimsHeader(
                        loading = loading,
                        onRefresh = onRefresh
                    )

                    B2bSearchAndTabs(
                        query = query,
                        selectedFilter = selectedFilter,
                        initialFilter = initialFilter,
                        onQueryChange = onQueryChange,
                        onFilterChange = onFilterChange
                    )

                    B2bStatsCard(allEsims = allEsims)

                    if (!error.isNullOrBlank()) {
                        ErrorCard(error = error, onRetry = onRefresh)
                    }

                    if (loading && allEsims.isEmpty()) {
                        InfoCard(title = "Loading") {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                CircularProgressIndicator(modifier = Modifier.height(20.dp))
                                Text("Fetching eSIM profiles...")
                            }
                        }
                    }

                    if (!loading && filteredEsims.isEmpty()) {
                        EmptyCard("No eSIM profiles found.")
                    } else {
                        filteredEsims.forEach { esim ->
                            EsimListCard(
                                esim = esim,
                                blue = blue,
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
private fun B2bEsimsHeader(
    loading: Boolean,
    onRefresh: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "My eSIMs",
                color = Color(0xFF0B1533),
                fontSize = 32.sp,
                fontWeight = FontWeight.Black
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (loading) "Refreshing eSIM inventory..." else "Monitor active, pending, and expired eSIMs.",
                color = Color(0xFF64708A),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Surface(
            modifier = Modifier
                .size(44.dp)
                .clickable(onClick = onRefresh),
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            border = BorderStroke(1.dp, Color(0xFFE6EAF0)),
            shadowElevation = 1.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh",
                    tint = Color(0xFF1263F1),
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
private fun B2bSearchAndTabs(
    query: String,
    selectedFilter: EsimFilter,
    initialFilter: String?,
    onQueryChange: (String) -> Unit,
    onFilterChange: (EsimFilter) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = Color.White,
            border = BorderStroke(1.dp, Color(0xFFE1E7F0)),
            shadowElevation = 1.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = Color(0xFF64708A),
                    modifier = Modifier.size(21.dp)
                )
                Spacer(modifier = Modifier.size(10.dp))
                Box(modifier = Modifier.weight(1f)) {
                    if (query.isBlank()) {
                        Text(
                            text = "Search by number, customer or ICCID...",
                            color = Color(0xFF9CA3AF),
                            fontSize = 14.sp,
                            maxLines = 1
                        )
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        singleLine = true,
                        textStyle = TextStyle(
                            color = Color(0xFF0B1533),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(EsimFilter.ACTIVE, EsimFilter.PENDING, EsimFilter.EXPIRED).forEach { filter ->
                B2bTabButton(
                    label = filter.label,
                    selected = initialFilter != MobileEsimFilters.FILTER_EXPIRED_SOON && selectedFilter == filter,
                    onClick = { onFilterChange(filter) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun B2bTabButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bg = if (selected) Color(0xFF1263F1) else Color.White
    val fg = if (selected) Color.White else Color(0xFF0B1533)
    val border = if (selected) Color(0xFF1263F1) else Color(0xFFE1E7F0)

    Surface(
        modifier = modifier
            .height(48.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = bg,
        border = BorderStroke(1.dp, border),
        shadowElevation = if (selected) 2.dp else 0.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                color = fg,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun B2bStatsCard(
    allEsims: List<MobileEsim>
) {
    val active = allEsims.count {
        val raw = realStatus(it).raw
        raw == "active" || raw == "ready"
    }
    val pending = allEsims.count { realStatus(it).raw == "pending" }
    val expired = allEsims.count { realStatus(it).raw == "expired" }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Color(0xFFE6EAF0)),
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            B2bStatItem(Icons.Default.CheckCircle, Color(0xFFE5F9EF), Color(0xFF10B981), active.toString(), "Active", Modifier.weight(1f))
            B2bStatItem(Icons.Default.Schedule, Color(0xFFFFF3DC), Color(0xFFFF8A00), pending.toString(), "Pending", Modifier.weight(1f))
            B2bStatItem(Icons.Default.Cancel, Color(0xFFFFE8EC), Color(0xFFFF3B4F), expired.toString(), "Expired", Modifier.weight(1f))
        }
    }
}

@Composable
private fun B2bStatItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    bg: Color,
    fg: Color,
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(bg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = fg,
                modifier = Modifier.size(20.dp)
            )
        }
        Column {
            Text(value, color = Color(0xFF0B1533), fontSize = 18.sp, fontWeight = FontWeight.Black)
            Text(label, color = Color(0xFF64708A), fontSize = 11.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun HeroEsimsCard(
    total: Int,
    shown: Int,
    loading: Boolean,
    blue: Color,
    onRefresh: () -> Unit,
    onOpenNative: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(30.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1263F1))
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "eSIMs",
                color = Color.White.copy(alpha = 0.88f),
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
                text = if (loading) "Refreshing your eSIM profiles..." else "Manage active and pending eSIM profiles.",
                color = Color.White.copy(alpha = 0.74f),
                style = MaterialTheme.typography.bodyMedium
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onRefresh,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text("Refresh", color = blue, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onOpenNative,
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text("Device eSIM", color = Color.White, fontWeight = FontWeight.Bold)
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
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Color(0xFFE6EAF0)),
        shadowElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = Color(0xFFF8FAFC),
                border = BorderStroke(1.dp, Color(0xFFE1E7F0))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = Color(0xFF64708A),
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.size(10.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        if (query.isBlank()) {
                            Text(
                                text = "Search ICCID, package, provider or status",
                                color = Color(0xFF9CA3AF),
                                fontSize = 14.sp,
                                maxLines = 1
                            )
                        }
                        BasicTextField(
                            value = query,
                            onValueChange = onQueryChange,
                            singleLine = true,
                            textStyle = TextStyle(
                                color = Color(0xFF0B1533),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

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
                        label = "Expiring Soon",
                        selected = true,
                        onClick = {}
                    )
                }
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
    val bg = if (selected) Color(0xFF1263F1) else Color.White
    val fg = if (selected) Color.White else Color(0xFF0B1533)
    val border = if (selected) Color(0xFF1263F1) else Color(0xFFE1E7F0)

    Surface(
        modifier = Modifier
            .height(38.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(999.dp),
        color = bg,
        border = BorderStroke(1.dp, border)
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                color = fg,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun EsimListCard(
    esim: MobileEsim,
    blue: Color,
    onOpenDetail: () -> Unit,
    onRenew: () -> Unit
) {
    val status = realStatus(esim)
    val packageTitle = cleanEsimTitle(esim.title())
    val customerName = esim.customerName()?.takeIf { it.isNotBlank() }
    val mainTitle = customerName ?: packageTitle
    val packageLine = if (customerName != null) packageTitle else null
    val badgeRes = esimBadgeResFor(packageTitle)
    val iccid = esim.iccid.orEmpty().ifBlank { "Pending ICCID" }
    val provider = visibleProvider(esim.provider).orEmpty().ifBlank { "Provider" }
    val expires = esim.expiresAt?.takeIf { it.isNotBlank() }?.let { formatEsimDate(it) }
    val remaining = esim.dataRemaining?.takeIf { it.isNotBlank() }
    val region = regionLabelFor(packageTitle)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenDetail() },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(1.dp, Color(0xFFE6EAF0))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 13.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFFEAF2FF)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = badgeRes),
                    contentDescription = region,
                    modifier = Modifier.size(44.dp),
                    contentScale = ContentScale.Fit
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = mainTitle,
                            color = Color(0xFF0B1533),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        if (packageLine != null) {
                            Text(
                                text = packageLine,
                                color = blue,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    StatusPill(status.label)
                }

                Text(
                    text = "ICCID: $iccid",
                    color = Color(0xFF64708A),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = region,
                            color = Color(0xFF64708A),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                        Text(
                            text = customerName ?: "No customer assigned",
                            color = Color(0xFF64708A),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = remaining?.let { "$it left" } ?: "",
                            color = Color(0xFF0B1533),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Text(
                            text = expires?.let { "Valid until $it" } ?: "Validity pending",
                            color = Color(0xFF64708A),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Details",
                tint = Color(0xFF64708A),
                modifier = Modifier.size(23.dp)
            )
        }
    }
}

@Composable
private fun EsimMiniInfo(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.padding(end = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(Color(0xFFEAF2FF)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color(0xFF1263F1),
                modifier = Modifier.size(18.dp)
            )
        }

        Column {
            Text(
                text = label,
                color = Color(0xFF64708A),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
            Text(
                text = value,
                color = Color(0xFF0B1533),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
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
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .background(colors.first, RoundedCornerShape(999.dp))
            .padding(horizontal = 9.dp, vertical = 5.dp)
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
            Text("Could not load eSIM profiles", color = Color(0xFFB91C1C), fontWeight = FontWeight.Bold)
            Text(error, color = Color(0xFF7F1D1D))
            OutlinedButton(onClick = onRetry) {
                Text("Retry")
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
    ALL("All"),
    ACTIVE("Active"),
    PENDING("Pending"),
    EXPIRED("Expired");

    fun matches(status: EsimsDisplayStatus): Boolean =
        when (this) {
            ACTIVE -> status.raw == "active" || status.raw == "ready"
            PENDING -> status.raw == "pending"
            EXPIRED -> status.raw == "expired"
            ALL -> true
        }
}


private fun cleanEsimTitle(raw: String): String =
    raw
        .replace(Regex("(?i)[\\[【\u3010\uFF3B]\\s*(e\\s*sim|simcard)\\s*[\\]】\u3011\uFF3D]"), "")
        .replace(Regex("(?i)^e\\s*sim\\s*[-–—:]?\\s*"), "")
        .replace(Regex("(?i)^simcard\\s*[-–—:]?\\s*"), "")
        .replace("—", " - ")
        .replace("–", " - ")
        .replace("（", "(")
        .replace("）", ")")
        .replace(Regex("\\s+"), " ")
        .trim()
        .ifBlank { "eSIM Package" }

private fun esimBadgeFor(title: String): String =
    when {
        title.contains("europe", ignoreCase = true) ||
            title.contains("orange", ignoreCase = true) -> "🇪🇺"
        title.contains("turkey", ignoreCase = true) ||
            title.contains("türkiye", ignoreCase = true) -> "🇹🇷"
        title.contains("world", ignoreCase = true) ||
            title.contains("global", ignoreCase = true) ||
            title.contains("balkan", ignoreCase = true) -> "🌍"
        else -> "📶"
    }



private fun esimBadgeResFor(title: String): Int =
    when {
        title.contains("europe", ignoreCase = true) ||
            title.contains("orange", ignoreCase = true) ||
            title.contains("eu ", ignoreCase = true) -> R.drawable.r2w_badge_europe

        title.contains("turkey", ignoreCase = true) ||
            title.contains("türkiye", ignoreCase = true) -> R.drawable.r2w_badge_turkey

        title.contains("world", ignoreCase = true) ||
            title.contains("global", ignoreCase = true) ||
            title.contains("balkan", ignoreCase = true) -> R.drawable.r2w_badge_world

        else -> R.drawable.r2w_badge_world
    }



private fun regionLabelFor(title: String): String =
    when {
        title.contains("spain", ignoreCase = true) -> "Spain"
        title.contains("uk", ignoreCase = true) || title.contains("united kingdom", ignoreCase = true) -> "United Kingdom"
        title.contains("germany", ignoreCase = true) -> "Germany"
        title.contains("italy", ignoreCase = true) -> "Italy"
        title.contains("turkey", ignoreCase = true) || title.contains("türkiye", ignoreCase = true) -> "Turkey"
        title.contains("europe", ignoreCase = true) || title.contains("orange", ignoreCase = true) -> "Europe"
        title.contains("balkan", ignoreCase = true) -> "Balkans"
        title.contains("world", ignoreCase = true) || title.contains("global", ignoreCase = true) -> "Global"
        else -> "Global"
    }

private fun badgeEmojiFor(title: String): String =
    when {
        title.contains("spain", ignoreCase = true) -> "🇪🇸"
        title.contains("uk", ignoreCase = true) || title.contains("united kingdom", ignoreCase = true) -> "🇬🇧"
        title.contains("germany", ignoreCase = true) -> "🇩🇪"
        title.contains("italy", ignoreCase = true) -> "🇮🇹"
        title.contains("turkey", ignoreCase = true) || title.contains("türkiye", ignoreCase = true) -> "🇹🇷"
        title.contains("europe", ignoreCase = true) || title.contains("orange", ignoreCase = true) -> "🇪🇺"
        else -> "🌍"
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
