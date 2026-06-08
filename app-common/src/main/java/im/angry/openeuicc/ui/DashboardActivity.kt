package im.angry.openeuicc.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import im.angry.openeuicc.auth.AuthSession
import im.angry.openeuicc.auth.AuthTokenStore
import im.angry.openeuicc.auth.JwtUtils
import im.angry.openeuicc.auth.MobileDashboardData
import im.angry.openeuicc.auth.MobileDashboardOrder
import im.angry.openeuicc.auth.MobileEsim
import im.angry.openeuicc.auth.Roam2WorldAuthApi
import im.angry.openeuicc.common.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit

class DashboardActivity : ComponentActivity() {
    private val tokenStore by lazy { AuthTokenStore(this) }
    private val authApi by lazy { Roam2WorldAuthApi(BuildConfig.ROAM2WORLD_API_BASE_URL) }

    private var uiState by mutableStateOf(DashboardUiState())

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        authApi.logMobileEndpointConfiguration()
        setContent {
            RoamDashboardTheme {
                DashboardRoute(
                    state = uiState,
                    onReload = ::loadDashboard,
                    onPackages = ::openPackagesActivity,
                    onWallet = ::openWalletActivity,
                    onEsims = ::openEsimActivity,
                    onMore = ::openMoreActivity,
                    onOrders = ::openPurchaseHistoryActivity,
                    onDealers = ::openMyDealersActivity,
                    onWalletRequest = { startActivity(Intent(this, WalletRequestActivity::class.java)) },
                    onTgtRecharge = { startActivity(Intent(this, TgtSimRechargeActivity::class.java)) },
                    onVodafoneRenewal = { startActivity(Intent(this, VodafoneRenewalActivity::class.java)) },
                    onLogout = ::logout
                )
            }
        }
        loadDashboard()
    }

    override fun onResume() {
        super.onResume()
        uiState = uiState.copy(selectedTab = DashboardTab.Dashboard)
    }

    private fun loadDashboard() {
        lifecycleScope.launch {
            uiState = uiState.copy(isLoading = true, errorMessage = null)
            val session = activeSessionOrReturnToLogin() ?: return@launch
            renderSession(session)

            val dashboardResult = runCatching { authApi.dashboard(session) }
            val esimsResult = runCatching { authApi.esims(session).esims }

            dashboardResult
                .onSuccess { renderDashboard(it) }
                .onFailure { uiState = uiState.copy(errorMessage = it.message ?: "Dashboard could not be loaded") }

            esimsResult.onSuccess { renderExpiredSoon(it) }
            uiState = uiState.copy(isLoading = false)
        }
    }

    private suspend fun activeSessionOrReturnToLogin(): AuthSession? {
        val savedSession = withContext(Dispatchers.IO) { tokenStore.getSession() } ?: return redirectToLogin()
        if (!JwtUtils.isExpired(savedSession.accessToken)) return savedSession

        val refreshed = runCatching { authApi.refresh(savedSession) }.getOrNull() ?: return redirectToLogin()
        withContext(Dispatchers.IO) { tokenStore.save(refreshed) }
        return refreshed
    }

    private fun renderSession(session: AuthSession) {
        val role = session.role?.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        uiState = uiState.copy(
            displayName = session.displayName,
            accountLabel = listOfNotNull(role, session.email).joinToString(" - "),
            isReseller = session.role?.equals("reseller", ignoreCase = true) == true
        )
    }

    private fun renderDashboard(data: MobileDashboardData) {
        uiState = uiState.copy(
            balance = data.currentBalance,
            activeEsims = data.activeEsimCount,
            recentOrdersCount = data.recentOrders.size.toString(),
            recentOrders = data.recentOrders
        )
    }

    private fun renderExpiredSoon(esimData: List<MobileEsim>) {
        val now = OffsetDateTime.now()
        val expiring = esimData.mapNotNull { esim ->
            val expires = esim.expiresAt?.let { runCatching { OffsetDateTime.parse(it) }.getOrNull() } ?: return@mapNotNull null
            val days = ChronoUnit.DAYS.between(now, expires)
            if (days in 0..7 && esim.status?.equals("expired", ignoreCase = true) != true) esim to days else null
        }.sortedBy { it.second }

        uiState = uiState.copy(
            expiringSoonCount = expiring.size.toString(),
            expiringSoonLabel = if (expiring.isEmpty()) {
                "No eSIMs expiring in 7 days"
            } else {
                expiring.take(3).joinToString("\n") { (esim, days) ->
                    val label = esim.iccid?.takeLast(6)?.let { "*$it" } ?: "eSIM"
                    "$label - ${days.coerceAtLeast(0)}d left"
                }
            }
        )
    }

    private fun openWalletActivity() {
        uiState = uiState.copy(selectedTab = DashboardTab.Wallet)
        startActivity(Intent(this, WalletActivity::class.java).apply { addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT) })
    }

    private fun openPackagesActivity() {
        uiState = uiState.copy(selectedTab = DashboardTab.Store)
        startActivity(Intent(this, PackagesActivity::class.java).apply { addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT) })
    }

    private fun openEsimActivity() {
        uiState = uiState.copy(selectedTab = DashboardTab.Esims)
        startActivity(Intent(this, MobileEsimsActivity::class.java).apply { addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT) })
    }

    private fun openMoreActivity() {
        uiState = uiState.copy(selectedTab = DashboardTab.More)
        startActivity(Intent(this, MoreActivity::class.java).apply { addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT) })
    }

    private fun openPurchaseHistoryActivity() {
        startActivity(Intent(this, PurchaseHistoryActivity::class.java))
    }

    private fun openMyDealersActivity() {
        startActivity(Intent(this, MyDealersActivity::class.java))
    }

    private fun logout() {
        lifecycleScope.launch {
            val session = withContext(Dispatchers.IO) {
                tokenStore.getSession().also { tokenStore.clear() }
            }
            session?.let { runCatching { authApi.logout(it) } }
            openLoginActivity()
        }
    }

    private fun redirectToLogin(): AuthSession? {
        tokenStore.clear()
        openLoginActivity()
        return null
    }

    private fun openLoginActivity() {
        startActivity(Intent(this, LoginActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        })
        finish()
    }

    companion object {
        const val META_ESIM_ACTIVITY = "im.angry.openeuicc.DASHBOARD_ESIM_ACTIVITY"
    }
}

private data class DashboardUiState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val displayName: String? = null,
    val accountLabel: String = "",
    val balance: String = "--",
    val activeEsims: String = "--",
    val recentOrdersCount: String = "--",
    val expiringSoonCount: String = "--",
    val expiringSoonLabel: String = "Loading expiring eSIMs",
    val recentOrders: List<MobileDashboardOrder> = emptyList(),
    val isReseller: Boolean = false,
    val selectedTab: DashboardTab = DashboardTab.Dashboard
)

private enum class DashboardTab(val label: String) {
    Dashboard("Dashboard"),
    Esims("eSIMs"),
    Store("Store"),
    Wallet("Wallet"),
    More("More")
}

private val DashboardBlue = Color(0xFF2563EB)
private val DashboardGreen = Color(0xFF16A34A)
private val DashboardOrange = Color(0xFFF97316)
private val DashboardBackground = Color(0xFFF6F8FC)
private val DashboardText = Color(0xFF111827)
private val DashboardMuted = Color(0xFF6B7280)

@Composable
private fun RoamDashboardTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = DashboardBlue,
            secondary = DashboardGreen,
            background = DashboardBackground,
            surface = Color.White,
            onPrimary = Color.White,
            onBackground = DashboardText,
            onSurface = DashboardText
        ),
        content = content
    )
}

@Composable
private fun DashboardRoute(
    state: DashboardUiState,
    onReload: () -> Unit,
    onPackages: () -> Unit,
    onWallet: () -> Unit,
    onEsims: () -> Unit,
    onMore: () -> Unit,
    onOrders: () -> Unit,
    onDealers: () -> Unit,
    onWalletRequest: () -> Unit,
    onTgtRecharge: () -> Unit,
    onVodafoneRenewal: () -> Unit,
    onLogout: () -> Unit
) {
    Surface(color = DashboardBackground) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .statusBarsPadding()
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                DashboardTopBar(state, onReload, onLogout)
                if (state.errorMessage != null) {
                    ErrorCard(state.errorMessage, onReload)
                }
                WalletHero(state.balance, onWalletRequest)
                MetricsGrid(state)
                QuickActions(
                    onPackages = onPackages,
                    onOrders = onOrders,
                    onWallet = onWallet,
                    onDealers = onDealers,
                    onTgtRecharge = onTgtRecharge,
                    onVodafoneRenewal = onVodafoneRenewal,
                    showDealers = state.isReseller
                )
                RecentOrders(state.recentOrders, onOrders)
            }
            DashboardBottomBar(
                selectedTab = state.selectedTab,
                onDashboard = onReload,
                onEsims = onEsims,
                onStore = onPackages,
                onWallet = onWallet,
                onMore = onMore
            )
        }
        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.06f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
    }
}

@Composable
private fun DashboardTopBar(
    state: DashboardUiState,
    onReload: () -> Unit,
    onLogout: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = state.displayName?.let { "Welcome, $it" } ?: "Welcome",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = DashboardText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (state.accountLabel.isNotBlank()) {
                Text(
                    text = state.accountLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = DashboardMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        TextButton(onClick = onReload) { Text("Reload") }
        TextButton(onClick = onLogout) { Text("Logout") }
    }
}

@Composable
private fun WalletHero(balance: String, onWalletRequest: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = RoundedCornerShape(28.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(listOf(Color(0xFF2563EB), Color(0xFF0EA5E9))),
                    RoundedCornerShape(28.dp)
                )
                .padding(22.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Wallet Balance", color = Color.White.copy(alpha = 0.78f), style = MaterialTheme.typography.bodyMedium)
                Text(balance, color = Color.White, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.ExtraBold)
                Button(
                    onClick = onWalletRequest,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = DashboardBlue)
                ) {
                    Text("Request Balance")
                }
            }
        }
    }
}

@Composable
private fun MetricsGrid(state: DashboardUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            MetricCard("Active eSIMs", state.activeEsims, "Live profiles", DashboardGreen, Modifier.weight(1f))
            MetricCard("Recent Orders", state.recentOrdersCount, "Latest purchases", DashboardBlue, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            MetricCard("Expiring Soon", state.expiringSoonCount, state.expiringSoonLabel, DashboardOrange, Modifier.weight(1f))
            MetricCard("Account", if (state.isReseller) "Reseller" else "Dealer", "B2B workspace", Color(0xFF7C3AED), Modifier.weight(1f))
        }
    }
}

@Composable
private fun MetricCard(title: String, value: String, subtitle: String, accent: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(accent)
            )
            Text(title, style = MaterialTheme.typography.labelLarge, color = DashboardMuted)
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = DashboardText)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = DashboardMuted, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun QuickActions(
    onPackages: () -> Unit,
    onOrders: () -> Unit,
    onWallet: () -> Unit,
    onDealers: () -> Unit,
    onTgtRecharge: () -> Unit,
    onVodafoneRenewal: () -> Unit,
    showDealers: Boolean
) {
    DashboardSection(title = "Quick Actions") {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                ActionButton("Browse Store", onPackages, Modifier.weight(1f))
                ActionButton("Order History", onOrders, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                ActionButton("Wallet", onWallet, Modifier.weight(1f))
                ActionButton(if (showDealers) "My Dealers" else "TGT Recharge", if (showDealers) onDealers else onTgtRecharge, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                ActionButton("TGT Recharge", onTgtRecharge, Modifier.weight(1f))
                ActionButton("Vodafone Renewal", onVodafoneRenewal, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ActionButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(54.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Text(text, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun RecentOrders(orders: List<MobileDashboardOrder>, onOrders: () -> Unit) {
    DashboardSection(title = "Recent Orders", action = "View all", onAction = onOrders) {
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            if (orders.isEmpty()) {
                Text(
                    text = "No recent orders yet.",
                    modifier = Modifier.padding(18.dp),
                    color = DashboardMuted,
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                Column {
                    orders.take(5).forEachIndexed { index, order ->
                        OrderRow(order)
                        if (index != orders.take(5).lastIndex) Divider(color = Color(0xFFE5E7EB))
                    }
                }
            }
        }
    }
}

@Composable
private fun OrderRow(order: MobileDashboardOrder) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(order.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(order.subtitle, style = MaterialTheme.typography.bodySmall, color = DashboardMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(horizontalAlignment = Alignment.End) {
            if (!order.amount.isNullOrBlank()) {
                Text(order.amount, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
            StatusPill(order.status.orEmpty())
        }
    }
}

@Composable
private fun StatusPill(status: String) {
    val normalized = status.lowercase()
    val color = when {
        normalized.contains("success") || normalized.contains("complete") || normalized.contains("active") -> DashboardGreen
        normalized.contains("fail") || normalized.contains("cancel") -> Color(0xFFDC2626)
        normalized.contains("pending") -> DashboardOrange
        else -> DashboardBlue
    }
    Text(
        text = status.ifBlank { "Status" },
        color = color,
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.10f))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    )
}

@Composable
private fun ErrorCard(message: String, onReload: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBEB)),
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(message, modifier = Modifier.weight(1f), color = Color(0xFF92400E), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onReload) { Text("Retry") }
        }
    }
}

@Composable
private fun DashboardSection(
    title: String,
    action: String? = null,
    onAction: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = DashboardText)
            if (action != null && onAction != null) TextButton(onClick = onAction) { Text(action) }
        }
        content()
    }
}

@Composable
private fun DashboardBottomBar(
    selectedTab: DashboardTab,
    onDashboard: () -> Unit,
    onEsims: () -> Unit,
    onStore: () -> Unit,
    onWallet: () -> Unit,
    onMore: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .background(Color.White)
            .padding(horizontal = 6.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        BottomItem(DashboardTab.Dashboard, selectedTab, onDashboard, Modifier.weight(1f))
        BottomItem(DashboardTab.Esims, selectedTab, onEsims, Modifier.weight(1f))
        BottomItem(DashboardTab.Store, selectedTab, onStore, Modifier.weight(1f))
        BottomItem(DashboardTab.Wallet, selectedTab, onWallet, Modifier.weight(1f))
        BottomItem(DashboardTab.More, selectedTab, onMore, Modifier.weight(1f))
    }
}

@Composable
private fun BottomItem(tab: DashboardTab, selectedTab: DashboardTab, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(onClick = onClick, modifier = modifier) {
        Text(
            text = tab.label,
            color = if (selectedTab == tab) DashboardBlue else DashboardMuted,
            fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
