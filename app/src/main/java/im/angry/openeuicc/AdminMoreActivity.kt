package im.angry.openeuicc

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Api
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PriceChange
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import im.angry.openeuicc.auth.AuthTokenStore
import im.angry.openeuicc.auth.Roam2WorldAuthApi
import im.angry.openeuicc.common.BuildConfig
import im.angry.openeuicc.ui.LoginActivity
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

class AdminMoreActivity : ComponentActivity() {
    private val tokenStore by lazy { AuthTokenStore(this) }
    private val api by lazy { Roam2WorldAuthApi(BuildConfig.ROAM2WORLD_API_BASE_URL) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val unreadNotifications = remember { mutableStateOf("0") }
            val pendingWallet = remember { mutableStateOf("0") }
            val supportTickets = remember { mutableStateOf("0") }

            LaunchedEffect(Unit) {
                val session = withContext(Dispatchers.IO) { tokenStore.getSession() }
                if (session != null) {
                    val result = withContext(Dispatchers.IO) {
                        runCatching { api.mobileAdminDashboardRaw(session) }
                    }

                    result.onSuccess { json ->
                        val metrics = json.optJSONObject("data")?.optJSONObject("metrics")
                        val wallet = metrics?.optJSONObject("wallet_requests")
                        val notifications = metrics?.optJSONObject("notifications")

                        unreadNotifications.value = (notifications?.optInt("unread", 0) ?: 0).toString()
                        pendingWallet.value = (
                            (wallet?.optInt("reseller_pending", 0) ?: 0) +
                                (wallet?.optInt("dealer_pending", 0) ?: 0)
                            ).toString()

                        supportTickets.value = metrics
                            ?.optJSONObject("support")
                            ?.optInt("open", 0)
                            ?.toString()
                            ?: "0"
                    }
                }
            }

            R2WTheme {
                AdminMoreSaasScreen(
                    unreadNotifications = unreadNotifications.value,
                    pendingWallet = pendingWallet.value,
                    supportTickets = supportTickets.value,
                    onNotificationsClick = {
                        startActivity(Intent(this@AdminMoreActivity, AdminNotificationsOverviewActivity::class.java))
                    },
                    onActivityLogsClick = {
                        startActivity(Intent(this@AdminMoreActivity, AdminActivityLogsOverviewActivity::class.java))
                    },
                    onSupportTicketsClick = {
                        startActivity(Intent(this@AdminMoreActivity, AdminSupportTicketsOverviewActivity::class.java))
                    },
                    onWalletApprovalsClick = {
                        startActivity(Intent(this@AdminMoreActivity, AdminWalletApprovalsOverviewActivity::class.java))
                    },
                    onProviderMarkupsClick = {
                        startActivity(Intent(this@AdminMoreActivity, AdminProviderMarkupsActivity::class.java))
                    },
                    onReportsClick = {
                        startActivity(Intent(this@AdminMoreActivity, AdminReportsOverviewActivity::class.java))
                    },
                    onSystemHealthClick = {
                        startActivity(Intent(this@AdminMoreActivity, AdminSystemHealthOverviewActivity::class.java))
                    },
                    onSettingsClick = {
                        startActivity(Intent(this@AdminMoreActivity, AdminProfileSettingsActivity::class.java))
                    },
                    onApiKeysClick = {
                        startActivity(Intent(this@AdminMoreActivity, AdminApiKeysActivity::class.java))
                    },
                    onWhiteLabelClick = {
                        startActivity(Intent(this@AdminMoreActivity, AdminWhiteLabelActivity::class.java))
                    },
                    onLogoutClick = { logoutAdmin() },
                    onBottomNavClick = { item ->
                        when (item) {
                            R2wSaasNavItem.Dashboard -> startActivity(Intent(this@AdminMoreActivity, MobileAdminActivity::class.java))
                            R2wSaasNavItem.Partners -> startActivity(Intent(this@AdminMoreActivity, AdminPartnersActivity::class.java))
                            R2wSaasNavItem.Orders -> startActivity(Intent(this@AdminMoreActivity, AdminOrdersOverviewActivity::class.java))
                            R2wSaasNavItem.Pricing -> startActivity(Intent(this@AdminMoreActivity, AdminPricingOverviewActivity::class.java))
                            R2wSaasNavItem.More -> Unit
                        }
                    }
                )
            }
        }
    }

    private fun logoutAdmin() {
        tokenStore.clear()
        startActivity(
            Intent(this, LoginActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
        )
        finish()
    }
}

@Composable
private fun AdminMoreSaasScreen(
    unreadNotifications: String,
    pendingWallet: String,
    supportTickets: String,
    onNotificationsClick: () -> Unit,
    onActivityLogsClick: () -> Unit,
    onSupportTicketsClick: () -> Unit,
    onWalletApprovalsClick: () -> Unit,
    onProviderMarkupsClick: () -> Unit,
    onReportsClick: () -> Unit,
    onSystemHealthClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onApiKeysClick: () -> Unit,
    onWhiteLabelClick: () -> Unit,
    onLogoutClick: () -> Unit,
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
                    title = "Settings",
                    subtitle = "Admin tools, SaaS settings and system controls.",
                    badge = "Admin"
                )
            }

            item {
                AdminMoreProfileCard(
                    pendingWallet = pendingWallet,
                    unreadNotifications = unreadNotifications,
                    supportTickets = supportTickets
                )
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    R2wMetricCard(
                        modifier = Modifier.weight(1f),
                        title = "Wallet",
                        value = pendingWallet,
                        subtitle = "pending",
                        icon = Icons.Default.AccountBalanceWallet,
                        tint = R2wSaasColors.Orange
                    )

                    R2wMetricCard(
                        modifier = Modifier.weight(1f),
                        title = "Alerts",
                        value = unreadNotifications,
                        subtitle = "unread",
                        icon = Icons.Default.Notifications,
                        tint = R2wSaasColors.Primary
                    )
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    R2wMetricCard(
                        modifier = Modifier.weight(1f),
                        title = "Support",
                        value = supportTickets,
                        subtitle = "open tickets",
                        icon = Icons.Default.SupportAgent,
                        tint = R2wSaasColors.Green
                    )

                    R2wMetricCard(
                        modifier = Modifier.weight(1f),
                        title = "System",
                        value = "Live",
                        subtitle = "health",
                        icon = Icons.Default.HealthAndSafety,
                        tint = R2wSaasColors.Purple
                    )
                }
            }

            item {
                MoreSection(title = "Operations") {
                    MoreAction(
                        title = "Wallet Approvals",
                        subtitle = "$pendingWallet pending top-up requests",
                        icon = Icons.Default.AccountBalanceWallet,
                        tint = R2wSaasColors.Orange,
                        onClick = onWalletApprovalsClick
                    )

                    MoreAction(
                        title = "Notifications",
                        subtitle = "$unreadNotifications unread admin notifications",
                        icon = Icons.Default.Notifications,
                        tint = R2wSaasColors.Primary,
                        onClick = onNotificationsClick
                    )

                    MoreAction(
                        title = "Support Tickets",
                        subtitle = "$supportTickets open tickets",
                        icon = Icons.Default.SupportAgent,
                        tint = R2wSaasColors.Green,
                        onClick = onSupportTicketsClick
                    )

                    MoreAction(
                        title = "Activity Logs",
                        subtitle = "Audit trail and admin actions",
                        icon = Icons.Default.ReceiptLong,
                        tint = R2wSaasColors.Purple,
                        onClick = onActivityLogsClick
                    )
                }
            }

            item {
                MoreSection(title = "Business Tools") {
                    MoreAction(
                        title = "Reports",
                        subtitle = "Revenue, orders and country analytics",
                        icon = Icons.Default.Analytics,
                        tint = R2wSaasColors.Primary,
                        onClick = onReportsClick
                    )

                    MoreAction(
                        title = "Provider Markups",
                        subtitle = "Reseller and dealer margin defaults",
                        icon = Icons.Default.PriceChange,
                        tint = R2wSaasColors.Orange,
                        onClick = onProviderMarkupsClick
                    )

                    MoreAction(
                        title = "API Keys",
                        subtitle = "Reseller API access and security",
                        icon = Icons.Default.Api,
                        tint = R2wSaasColors.Green,
                        onClick = onApiKeysClick
                    )

                    MoreAction(
                        title = "White-label",
                        subtitle = "Logo, brand, domain and partner app settings",
                        icon = Icons.Default.Settings,
                        tint = R2wSaasColors.Purple,
                        onClick = onWhiteLabelClick
                    )
                }
            }

            item {
                MoreSection(title = "Account & System") {
                    MoreAction(
                        title = "System Health",
                        subtitle = "Provider API, backend and queue status",
                        icon = Icons.Default.HealthAndSafety,
                        tint = R2wSaasColors.Green,
                        onClick = onSystemHealthClick
                    )

                    MoreAction(
                        title = "Profile Settings",
                        subtitle = "Admin account and security settings",
                        icon = Icons.Default.Settings,
                        tint = R2wSaasColors.Primary,
                        onClick = onSettingsClick
                    )

                    MoreAction(
                        title = "Security / Roles",
                        subtitle = "RBAC and admin permissions later",
                        icon = Icons.Default.VerifiedUser,
                        tint = R2wSaasColors.Purple,
                        onClick = {
                            /* placeholder */
                        }
                    )

                    MoreAction(
                        title = "Logout",
                        subtitle = "Clear session and return to login",
                        icon = Icons.Default.Logout,
                        tint = R2wSaasColors.Red,
                        onClick = onLogoutClick
                    )
                }
            }

            item {
                R2wSaasCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Roam2World Admin",
                                color = R2wSaasColors.Text,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                text = "B2B eSIM SaaS control center",
                                color = R2wSaasColors.Muted,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(50),
                            color = R2wSaasColors.PrimarySoft,
                            border = BorderStroke(1.dp, R2wSaasColors.Border)
                        ) {
                            Text(
                                text = "v1.0",
                                color = R2wSaasColors.Primary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(18.dp)) }
        }
    }
}

@Composable
private fun AdminMoreProfileCard(
    pendingWallet: String,
    unreadNotifications: String,
    supportTickets: String
) {
    R2wSaasCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = R2wSaasColors.PrimarySoft,
                border = BorderStroke(1.dp, R2wSaasColors.Border)
            ) {
                Box(
                    modifier = Modifier
                        .padding(14.dp)
                        .size(28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "A",
                        color = R2wSaasColors.Primary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
            ) {
                Text(
                    text = "Admin Account",
                    color = R2wSaasColors.Text,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black
                )

                Text(
                    text = "Wallet $pendingWallet • Alerts $unreadNotifications • Support $supportTickets",
                    color = R2wSaasColors.Muted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }

            Surface(
                shape = RoundedCornerShape(50),
                color = R2wSaasColors.Green.copy(alpha = 0.10f),
                border = BorderStroke(1.dp, R2wSaasColors.Green.copy(alpha = 0.16f))
            ) {
                Text(
                    text = "Active",
                    color = R2wSaasColors.Green,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                )
            }
        }
    }
}

@Composable
private fun MoreSection(
    title: String,
    content: @Composable () -> Unit
) {
    R2wSaasCard {
        Text(
            text = title,
            color = R2wSaasColors.Text,
            fontSize = 17.sp,
            fontWeight = FontWeight.Black
        )

        Spacer(Modifier.height(12.dp))

        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            content()
        }
    }
}

@Composable
private fun MoreAction(
    title: String,
    subtitle: String,
    icon: ImageVector,
    tint: Color,
    onClick: () -> Unit
) {
    R2wActionCard(
        title = title,
        subtitle = subtitle,
        icon = icon,
        onClick = onClick,
        tint = tint
    )
}
