package im.angry.openeuicc

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import im.angry.openeuicc.auth.AuthTokenStore
import im.angry.openeuicc.auth.Roam2WorldAuthApi
import im.angry.openeuicc.common.BuildConfig
import im.angry.openeuicc.ui.compose.saas.R2wActionCard
import im.angry.openeuicc.ui.compose.saas.R2wMetricCard
import im.angry.openeuicc.ui.compose.saas.R2wSaasBottomNav
import im.angry.openeuicc.ui.compose.saas.R2wSaasCard
import im.angry.openeuicc.ui.compose.saas.R2wSaasColors
import im.angry.openeuicc.ui.compose.saas.R2wSaasHeader
import im.angry.openeuicc.ui.compose.saas.R2wSaasNavItem
import im.angry.openeuicc.ui.compose.theme.R2WTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AdminSystemHealthOverviewActivity : ComponentActivity() {
    private val tokenStore by lazy { AuthTokenStore(this) }
    private val api by lazy { Roam2WorldAuthApi(BuildConfig.ROAM2WORLD_API_BASE_URL) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val status = remember { mutableStateOf("unknown") }
            val riskScore = remember { mutableStateOf("0") }
            val unreadAlerts = remember { mutableStateOf("0") }

            LaunchedEffect(Unit) {
                val session = withContext(Dispatchers.IO) { tokenStore.getSession() }
                if (session != null) {
                    val result = withContext(Dispatchers.IO) {
                        runCatching { api.mobileAdminDashboardRaw(session) }
                    }

                    result.onSuccess { json ->
                        val metrics = json.optJSONObject("data")?.optJSONObject("metrics")
                        val system = metrics?.optJSONObject("system")
                        val notifications = metrics?.optJSONObject("notifications")

                        status.value = system?.optString("status", "unknown") ?: "unknown"
                        riskScore.value = (system?.optInt("risk_score", 0) ?: 0).toString()
                        unreadAlerts.value = (notifications?.optInt("unread", 0) ?: 0).toString()
                    }
                }
            }

            R2WTheme {
                AdminSystemHealthOverviewSaasScreen(
                    status = status.value,
                    riskScore = riskScore.value,
                    unreadAlerts = unreadAlerts.value,
                    onOpenSystemHealthClick = {
                        startActivity(Intent(this@AdminSystemHealthOverviewActivity, AdminSystemHealthActivity::class.java))
                    },
                    onBottomNavClick = { item ->
                        when (item) {
                            R2wSaasNavItem.Dashboard -> startActivity(Intent(this@AdminSystemHealthOverviewActivity, MobileAdminActivity::class.java))
                            R2wSaasNavItem.Partners -> startActivity(Intent(this@AdminSystemHealthOverviewActivity, AdminPartnersActivity::class.java))
                            R2wSaasNavItem.Orders -> startActivity(Intent(this@AdminSystemHealthOverviewActivity, AdminOrdersOverviewActivity::class.java))
                            R2wSaasNavItem.Pricing -> startActivity(Intent(this@AdminSystemHealthOverviewActivity, AdminPricingOverviewActivity::class.java))
                            R2wSaasNavItem.More -> startActivity(Intent(this@AdminSystemHealthOverviewActivity, AdminMoreActivity::class.java))
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun AdminSystemHealthOverviewSaasScreen(
    status: String,
    riskScore: String,
    unreadAlerts: String,
    onOpenSystemHealthClick: () -> Unit,
    onBottomNavClick: (R2wSaasNavItem) -> Unit
) {
    Scaffold(
        containerColor = R2wSaasColors.Background,
        bottomBar = {
            R2wSaasBottomNav(
                selected = R2wSaasNavItem.More,
                onClick = onBottomNavClick
            )
        }
    ) { inner ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { Spacer(Modifier.height(8.dp)) }

            item {
                R2wSaasHeader(
                    title = "System Health",
                    subtitle = "Backend, provider API and queue monitoring.",
                    badge = status
                )
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    R2wMetricCard(
                        modifier = Modifier.weight(1f),
                        title = "Status",
                        value = status,
                        subtitle = "platform",
                        icon = Icons.Default.HealthAndSafety,
                        tint = R2wSaasColors.Green
                    )

                    R2wMetricCard(
                        modifier = Modifier.weight(1f),
                        title = "Risk",
                        value = riskScore,
                        subtitle = "score",
                        icon = Icons.Default.Security,
                        tint = R2wSaasColors.Orange
                    )
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    R2wMetricCard(
                        modifier = Modifier.weight(1f),
                        title = "Alerts",
                        value = unreadAlerts,
                        subtitle = "unread",
                        icon = Icons.Default.Notifications,
                        tint = R2wSaasColors.Primary
                    )

                    R2wMetricCard(
                        modifier = Modifier.weight(1f),
                        title = "API",
                        value = "Live",
                        subtitle = "providers",
                        icon = Icons.Default.Speed,
                        tint = R2wSaasColors.Purple
                    )
                }
            }

            item {
                R2wActionCard(
                    title = "View System Health",
                    subtitle = "Check backend, provider integrations and operational status",
                    icon = Icons.Default.HealthAndSafety,
                    onClick = onOpenSystemHealthClick,
                    tint = R2wSaasColors.Green
                )
            }

            item {
                R2wSaasCard {
                    R2wActionCard(
                        title = "Provider Monitor",
                        subtitle = "Balance alerts, API latency and fallback queue later",
                        icon = Icons.Default.Speed,
                        onClick = {},
                        tint = R2wSaasColors.Primary
                    )
                }
            }

            item { Spacer(Modifier.height(18.dp)) }
        }
    }
}
