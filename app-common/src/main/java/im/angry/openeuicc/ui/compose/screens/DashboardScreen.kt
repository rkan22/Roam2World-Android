package im.angry.openeuicc.ui.compose.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import im.angry.openeuicc.auth.MobileDashboardData
import im.angry.openeuicc.ui.compose.components.*
import im.angry.openeuicc.ui.compose.theme.*

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
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Welcome back, $userName 👋",
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
                    subtitle = "Request Balance",
                    onActionClick = onWalletClick
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
                        trend = "↑ 18.6%",
                        isPositive = true,
                        modifier = Modifier.weight(1f)
                    )
                    R2WStatCard(
                        title = "Monthly Sales",
                        value = data?.monthlySales ?: "$0.00",
                        trend = "↑ 14.2%",
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
                        trend = "↑ 6.3%",
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
                Text(
                    text = "Quick Actions",
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
                        R2WActionCard(Icons.Default.AddBox, "Orange Recharge", { onActionClick("orange") }, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        R2WActionCard(Icons.Default.Refresh, "Vodafone Recharge", { onActionClick("vodafone") }, Modifier.weight(1f))
                        R2WActionCard(Icons.Default.People, "Customer CRM", { onActionClick("crm") }, Modifier.weight(1f))
                        R2WActionCard(Icons.Default.Assessment, "Reports", { onActionClick("reports") }, Modifier.weight(1f))
                    }
                }
            }
            
            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}
