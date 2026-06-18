@file:OptIn(ExperimentalMaterial3Api::class)

package im.angry.openeuicc.ui.compose.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AddBox
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import im.angry.openeuicc.auth.MobileDashboardData
import im.angry.openeuicc.ui.compose.components.R2WActionCard
import im.angry.openeuicc.ui.compose.components.R2WCard
import im.angry.openeuicc.ui.compose.components.R2WHeroCard
import im.angry.openeuicc.ui.compose.components.R2WProgressRow
import im.angry.openeuicc.ui.compose.components.R2WQuickActionGrid
import im.angry.openeuicc.ui.compose.components.R2WStatCard
import im.angry.openeuicc.ui.compose.theme.Background
import im.angry.openeuicc.ui.compose.theme.Primary
import im.angry.openeuicc.ui.compose.theme.TextPrimary
import im.angry.openeuicc.ui.compose.theme.TextSecondary

@Composable
fun DashboardScreen(
    userName: String,
    data: MobileDashboardData?,
    onWalletClick: () -> Unit,
    onActionClick: (String) -> Unit
) {
    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Roam2World",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = Primary
                        )
                        Text(
                            text = "Welcome back, $userName",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { }) {
                        Icon(Icons.Default.Notifications, contentDescription = null, tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                R2WHeroCard(
                    title = "Wallet Balance",
                    amount = data?.currentBalance ?: "$0.00",
                    subtitle = "View Wallet",
                    onActionClick = onWalletClick
                )
            }

            item {
                R2WQuickActionGrid(
                    onAddEsim = { onActionClick("orders") },
                    onRecharge = { onActionClick("orange") },
                    onCheckGb = { onActionClick("check_gb") },
                    onOpenEuicc = { onActionClick("openeuicc") },
                    onReports = { onActionClick("reports") }
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    R2WStatCard(
                        title = "Today's Sales",
                        value = data?.todaySales ?: "$0.00",
                        trend = "up 18.6%",
                        isPositive = true,
                        modifier = Modifier.weight(1f)
                    )
                    R2WStatCard(
                        title = "Monthly Sales",
                        value = data?.monthlySales ?: "$0.00",
                        trend = "up 14.2%",
                        isPositive = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    R2WStatCard(
                        title = "Active eSIMs",
                        value = data?.activeEsimCount ?: "0",
                        trend = "up 6.3%",
                        isPositive = true,
                        modifier = Modifier.weight(1f)
                    )
                    R2WStatCard(
                        title = "Expiring Soon",
                        value = "128",
                        trend = "in 7 days",
                        isPositive = false,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                R2WCard {
                    Text(
                        text = "Usage Overview",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "680.4 GB",
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                        color = Primary
                    )
                    R2WProgressRow("Global Business 100GB", "68%", 0.68f)
                    R2WProgressRow("Europe Pool", "57%", 0.57f)
                    R2WProgressRow("Asia Pacific Pool", "46%", 0.46f)
                    R2WProgressRow("North America Pool", "35%", 0.35f)
                }
            }

            item {
                Text(
                    text = "Legacy Actions",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        R2WActionCard(Icons.Default.ShoppingCart, "Orders", { onActionClick("orders") }, Modifier.weight(1f))
                        R2WActionCard(Icons.Default.AccountBalanceWallet, "Wallet", { onActionClick("wallet") }, Modifier.weight(1f))
                        R2WActionCard(Icons.Default.AddBox, "TGT Recharge", { onActionClick("orange") }, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        R2WActionCard(Icons.Default.Refresh, "Vodafone", { onActionClick("vodafone") }, Modifier.weight(1f))
                        R2WActionCard(Icons.Default.People, "Dealers", { onActionClick("crm") }, Modifier.weight(1f))
                        R2WActionCard(Icons.Default.Assessment, "Reports", { onActionClick("reports") }, Modifier.weight(1f))
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}
