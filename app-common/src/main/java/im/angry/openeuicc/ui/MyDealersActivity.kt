package im.angry.openeuicc.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import im.angry.openeuicc.auth.AuthSession
import im.angry.openeuicc.auth.AuthTokenStore
import im.angry.openeuicc.auth.JwtUtils
import im.angry.openeuicc.auth.MobileDealer
import im.angry.openeuicc.auth.Roam2WorldAuthApi
import im.angry.openeuicc.common.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.util.Locale

class MyDealersActivity : ComponentActivity() {
    private val tokenStore by lazy { AuthTokenStore(this) }
    private val authApi by lazy { Roam2WorldAuthApi(BuildConfig.ROAM2WORLD_API_BASE_URL) }

    private var dealers by mutableStateOf<List<MobileDealer>>(emptyList())
    private var loading by mutableStateOf(false)
    private var errorMessage by mutableStateOf<String?>(null)
    private var refreshKey by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MyDealersScreen(
                dealers = dealers,
                loading = loading,
                errorMessage = errorMessage,
                refreshKey = refreshKey,
                onBack = { finish() },
                onRefresh = { loadDealers() },
                onAddDealer = { startActivity(Intent(this, AddDealerActivity::class.java)) },
                onOpenDealer = { openDealerDetail(it) }
            )
        }

        loadDealers()
    }

    override fun onResume() {
        super.onResume()
        loadDealers()
    }

    private fun loadDealers() {
        lifecycleScope.launch {
            loading = true
            errorMessage = null

            val session = activeSessionOrReturnToLogin()
            if (session == null) {
                loading = false
                return@launch
            }

            if (session.role?.lowercase(Locale.ROOT) != "reseller") {
                loading = false
                dealers = emptyList()
                errorMessage = "Dealer management is available for reseller accounts only"
                return@launch
            }

            val result = runCatching { authApi.dealers(session) }
            loading = false

            result
                .onSuccess {
                    dealers = it.dealers
                    refreshKey += 1
                }
                .onFailure {
                    errorMessage = it.message ?: "Dealers could not be loaded"
                }
        }
    }

    private fun openDealerDetail(dealer: MobileDealer) {
        val id = dealer.id ?: return
        startActivity(
            Intent(this, DealerDetailActivity::class.java)
                .putExtra(DealerDetailActivity.EXTRA_DEALER_ID, id)
                .putExtra(DealerDetailActivity.EXTRA_DEALER_NAME, dealer.name)
        )
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
        startActivity(
            Intent(this, LoginActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
        )
        finish()
        return null
    }
}

@Composable
private fun MyDealersScreen(
    dealers: List<MobileDealer>,
    loading: Boolean,
    errorMessage: String?,
    refreshKey: Int,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onAddDealer: () -> Unit,
    onOpenDealer: (MobileDealer) -> Unit
) {
    val bg = Color(0xFFF6F7FB)
    val totalBalance = remember(refreshKey, dealers) {
        dealers.sumOf { parseDealerMoney(it.currentBalance) }
    }
    val activeCount = remember(refreshKey, dealers) {
        dealers.count { it.status.equals("active", ignoreCase = true) }
    }

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
                    DealersHero(
                        dealerCount = dealers.size,
                        activeCount = activeCount,
                        totalBalance = "€" + String.format(Locale.US, "%.2f", totalBalance).replace(".", ","),
                        loading = loading,
                        onBack = onBack,
                        onRefresh = onRefresh,
                        onAddDealer = onAddDealer
                    )

                    errorMessage?.let {
                        InfoCard(title = "Dealers could not be loaded") {
                            Text(it, color = Color(0xFFDC2626))
                            OutlinedButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                                Text("Try again")
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Showing ${dealers.size} dealers",
                            color = Color(0xFF6B7280),
                            fontWeight = FontWeight.Bold
                        )
                        if (loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.dp,
                                color = Color(0xFFFF7900)
                            )
                        }
                    }

                    if (!loading && dealers.isEmpty()) {
                        InfoCard(title = "No dealers yet") {
                            Text("Create your first dealer to start allocating balance and tracking reseller performance.", color = Color(0xFF6B7280))
                            Button(
                                onClick = onAddDealer,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF7900)),
                                shape = RoundedCornerShape(18.dp)
                            ) {
                                Text("+ Add Dealer", fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    dealers.forEach { dealer ->
                        DealerCard(dealer = dealer, onClick = { onOpenDealer(dealer) })
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
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Text("Back")
                        }
                        Button(
                            onClick = onAddDealer,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF7900)),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Text("+ Add Dealer", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DealersHero(
    dealerCount: Int,
    activeCount: Int,
    totalBalance: String,
    loading: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onAddDealer: () -> Unit
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
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "My Dealers",
                        color = Color.White,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = "Balance, orders and reseller performance",
                        color = Color.White.copy(alpha = 0.72f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Back",
                        color = Color(0xFFFF7900),
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.clickable(onClick = onBack)
                    )
                    Text(
                        text = if (loading) "Loading..." else "Refresh",
                        color = Color.White.copy(alpha = 0.78f),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable(enabled = !loading, onClick = onRefresh)
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HeroMetric("Dealers", dealerCount.toString(), Modifier.weight(1f))
                HeroMetric("Active", activeCount.toString(), Modifier.weight(1f))
            }

            HeroMetric("Total dealer balance", totalBalance, Modifier.fillMaxWidth())

            Button(
                onClick = onAddDealer,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF7900)),
                shape = RoundedCornerShape(18.dp)
            ) {
                Text("+ Add Dealer", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun HeroMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.10f))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(value, color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            Text(label, color = Color.White.copy(alpha = 0.72f), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun DealerCard(dealer: MobileDealer, onClick: () -> Unit) {
    val statusColors = dealerStatusColors(dealer.status)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                DealerAvatar(dealer.name)

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        dealer.name,
                        color = Color(0xFF17181C),
                        fontWeight = FontWeight.Black,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        dealer.email.orEmpty().ifBlank { "No email" },
                        color = Color(0xFF6B7280),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                StatusPill(dealer.statusLabel(), statusColors)
            }

            HorizontalDivider(color = Color(0xFFE5E7EB))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DealerStat("Balance", r2wMoney(dealer.currentBalance), Modifier.weight(1f))
                DealerStat("Orders", dealer.totalOrders.ifBlank { "0" }, Modifier.weight(1f))
                DealerStat("Revenue", dealer.revenue.ifBlank { "0" }, Modifier.weight(1f))
            }

            Text(
                text = "Tap to view dealer details",
                color = Color(0xFFFF7900),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

@Composable
private fun DealerAvatar(name: String) {
    Box(
        modifier = Modifier
            .size(54.dp)
            .background(Color(0xFFFFEFE2), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            dealerInitials(name),
            color = Color(0xFF17181C),
            fontWeight = FontWeight.Black
        )
    }
}

@Composable
private fun DealerStat(label: String, value: String, modifier: Modifier = Modifier) {
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
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(title, color = Color(0xFF17181C), fontWeight = FontWeight.Black)
            HorizontalDivider()
            content()
        }
    }
}

private fun dealerStatusColors(status: String): Pair<Color, Color> =
    when (status.lowercase(Locale.ROOT)) {
        "suspended" -> Color(0xFFDC2626) to Color(0xFFFEE2E2)
        "inactive" -> Color(0xFFF59E0B) to Color(0xFFFEF3C7)
        "active" -> Color(0xFF168653) to Color(0xFFE4F8EC)
        else -> Color(0xFF6B7280) to Color(0xFFF3F4F6)
    }

private fun dealerInitials(name: String): String =
    name.split(" ")
        .filter { it.isNotBlank() }
        .take(2)
        .map { it.first().uppercaseChar() }
        .joinToString("")
        .ifBlank { "DL" }

private fun parseDealerMoney(value: String): Double =
    value
        .replace(",", ".")
        .replace(Regex("[^0-9.]"), "")
        .toDoubleOrNull() ?: 0.0
