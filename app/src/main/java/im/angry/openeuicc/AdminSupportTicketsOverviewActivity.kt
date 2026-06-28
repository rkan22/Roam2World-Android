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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.VerifiedUser
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

class AdminSupportTicketsOverviewActivity : ComponentActivity() {
    private val tokenStore by lazy { AuthTokenStore(this) }
    private val api by lazy { Roam2WorldAuthApi(BuildConfig.ROAM2WORLD_API_BASE_URL) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val openTickets = remember { mutableStateOf("0") }
            val pendingTickets = remember { mutableStateOf("0") }
            val resolvedTickets = remember { mutableStateOf("0") }

            LaunchedEffect(Unit) {
                val session = withContext(Dispatchers.IO) { tokenStore.getSession() }
                if (session != null) {
                    val result = withContext(Dispatchers.IO) {
                        runCatching { api.mobileAdminDashboardRaw(session) }
                    }

                    result.onSuccess { json ->
                        val support = json
                            .optJSONObject("data")
                            ?.optJSONObject("metrics")
                            ?.optJSONObject("support")

                        openTickets.value = (support?.optInt("open", 0) ?: 0).toString()
                        pendingTickets.value = (support?.optInt("pending", 0) ?: 0).toString()
                        resolvedTickets.value = (support?.optInt("resolved", 0) ?: 0).toString()
                    }
                }
            }

            R2WTheme {
                AdminSupportTicketsOverviewSaasScreen(
                    openTickets = openTickets.value,
                    pendingTickets = pendingTickets.value,
                    resolvedTickets = resolvedTickets.value,
                    onOpenSupportClick = {
                        startActivity(Intent(this@AdminSupportTicketsOverviewActivity, AdminSupportTicketsActivity::class.java))
                    },
                    onBottomNavClick = { item ->
                        when (item) {
                            R2wSaasNavItem.Dashboard -> startActivity(Intent(this@AdminSupportTicketsOverviewActivity, MobileAdminActivity::class.java))
                            R2wSaasNavItem.Partners -> startActivity(Intent(this@AdminSupportTicketsOverviewActivity, AdminPartnersActivity::class.java))
                            R2wSaasNavItem.Orders -> startActivity(Intent(this@AdminSupportTicketsOverviewActivity, AdminOrdersOverviewActivity::class.java))
                            R2wSaasNavItem.Pricing -> startActivity(Intent(this@AdminSupportTicketsOverviewActivity, AdminPricingOverviewActivity::class.java))
                            R2wSaasNavItem.More -> startActivity(Intent(this@AdminSupportTicketsOverviewActivity, AdminMoreActivity::class.java))
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun AdminSupportTicketsOverviewSaasScreen(
    openTickets: String,
    pendingTickets: String,
    resolvedTickets: String,
    onOpenSupportClick: () -> Unit,
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
                    title = "Support Tickets",
                    subtitle = "$openTickets open tickets and $pendingTickets pending replies.",
                    badge = "Live API"
                )
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    R2wMetricCard(
                        modifier = Modifier.weight(1f),
                        title = "Open",
                        value = openTickets,
                        subtitle = "tickets",
                        icon = Icons.Default.SupportAgent,
                        tint = R2wSaasColors.Primary
                    )

                    R2wMetricCard(
                        modifier = Modifier.weight(1f),
                        title = "Pending",
                        value = pendingTickets,
                        subtitle = "waiting",
                        icon = Icons.Default.HourglassTop,
                        tint = R2wSaasColors.Orange
                    )
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    R2wMetricCard(
                        modifier = Modifier.weight(1f),
                        title = "Resolved",
                        value = resolvedTickets,
                        subtitle = "closed",
                        icon = Icons.Default.CheckCircle,
                        tint = R2wSaasColors.Green
                    )

                    R2wMetricCard(
                        modifier = Modifier.weight(1f),
                        title = "SLA",
                        value = "Live",
                        subtitle = "support",
                        icon = Icons.Default.VerifiedUser,
                        tint = R2wSaasColors.Purple
                    )
                }
            }

            item {
                R2wActionCard(
                    title = "View Support Queue",
                    subtitle = "Review customer, reseller and provider support tickets",
                    icon = Icons.Default.SupportAgent,
                    onClick = onOpenSupportClick,
                    tint = R2wSaasColors.Primary
                )
            }

            item {
                R2wSaasCard {
                    R2wActionCard(
                        title = "Support Automation",
                        subtitle = "Auto-tagging, priority routing and SLA rules later",
                        icon = Icons.Default.VerifiedUser,
                        onClick = {},
                        tint = R2wSaasColors.Green
                    )
                }
            }

            item { Spacer(Modifier.height(18.dp)) }
        }
    }
}
