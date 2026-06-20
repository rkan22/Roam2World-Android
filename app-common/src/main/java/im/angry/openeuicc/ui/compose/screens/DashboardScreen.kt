@file:OptIn(ExperimentalMaterial3Api::class)

package im.angry.openeuicc.ui.compose.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import im.angry.openeuicc.auth.MobileDashboardData
import im.angry.openeuicc.auth.MobileDashboardOrder

private val R2WBlue = Color(0xFF075BFF)
private val R2WBlueDark = Color(0xFF0045D8)
private val R2WText = Color(0xFF111A35)
private val R2WMuted = Color(0xFF60708C)
private val R2WBorder = Color(0xFFE7ECF5)
private val R2WBackground = Color(0xFFFBFCFF)
private val R2WGreen = Color(0xFF10B981)
private val R2WOrange = Color(0xFFF97316)
private val R2WPurple = Color(0xFF7C3AED)
private val R2WSoftBlue = Color(0xFF2563EB)
private val R2WTeal = Color(0xFF14B8A6)

@Composable
fun DashboardScreen(
    userName: String,
    data: MobileDashboardData?,
    onWalletClick: () -> Unit,
    onActionClick: (String) -> Unit
) {
    Scaffold(
        containerColor = R2WBackground,
        floatingActionButton = {
            Surface(
                modifier = Modifier
                    .size(64.dp)
                    .clickable { onActionClick("store") },
                color = R2WBlue,
                shape = RoundedCornerShape(999.dp),
                shadowElevation = 14.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(34.dp))
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item { Spacer(modifier = Modifier.height(8.dp)) }
                item { DashboardHeader() }
                item { DashboardTitle() }
                item {
                    WalletHeroCard(
                        balance = data?.currentBalance ?: "€12,450.80",
                        onAddFunds = onWalletClick
                    )
                }
                item { MetricGrid(data = data) }
                item { QuickActionsCard(onActionClick = onActionClick) }
                item {
                    RecentActivityCard(
                        orders = data?.recentOrders.orEmpty(),
                        onViewAll = { onActionClick("orders") }
                    )
                }
                item { Spacer(modifier = Modifier.height(16.dp)) }
            }

            DashboardBottomNav(onActionClick = onActionClick)
        }
    }
}

@Composable
private fun DashboardHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { }) {
            Icon(Icons.Default.Menu, contentDescription = null, tint = R2WText, modifier = Modifier.size(28.dp))
        }
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Roam", color = R2WText, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
            Text("2", color = R2WTeal, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
            Text("World", color = R2WText, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
            Box(
                modifier = Modifier
                    .padding(start = 7.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(R2WBlue)
                    .padding(horizontal = 7.dp, vertical = 3.dp)
            ) {
                Text("B2B", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
        IconButton(onClick = { }) {
            Box {
                Icon(Icons.Default.Notifications, contentDescription = null, tint = R2WText, modifier = Modifier.size(28.dp))
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(9.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color(0xFFEF4444))
                )
            }
        }
    }
}

@Composable
private fun DashboardTitle() {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Dashboard", color = R2WText, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
        Text("Manage your eSIM business in one place.", color = R2WMuted, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun WalletHeroCard(
    balance: String,
    onAddFunds: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(152.dp),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = R2WBlue),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.horizontalGradient(listOf(R2WBlue, R2WBlueDark)))
                .padding(22.dp)
        ) {
            Column(
                modifier = Modifier.align(Alignment.CenterStart),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Available Balance", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Icon(
                        Icons.Default.Visibility,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier
                            .padding(start = 10.dp)
                            .size(20.dp)
                    )
                }
                Text(
                    text = balance,
                    color = Color.White,
                    fontSize = 38.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text("≈ $13,482.56 USD", color = Color.White.copy(alpha = 0.88f), fontSize = 16.sp)
            }
            Surface(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .clickable(onClick = onAddFunds),
                color = Color.White,
                shape = RoundedCornerShape(22.dp),
                shadowElevation = 1.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(color = R2WBlue, shape = RoundedCornerShape(999.dp)) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    Text(
                        "Add Funds",
                        color = R2WBlue,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricGrid(data: MobileDashboardData?) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DashboardMetricCard(
                icon = Icons.Default.SimCard,
                iconTint = R2WTeal,
                title = "Active eSIMs",
                value = data?.activeEsimCount ?: "128",
                caption = "Total active",
                modifier = Modifier.weight(1f)
            )
            DashboardMetricCard(
                icon = Icons.Default.ReceiptLong,
                iconTint = R2WOrange,
                title = "Pending Orders",
                value = data?.expiredEsimCount ?: "14",
                caption = "Awaiting approval",
                modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DashboardMetricCard(
                icon = Icons.Default.Analytics,
                iconTint = R2WPurple,
                title = "Monthly Revenue",
                value = data?.monthlySales ?: "€8,420",
                caption = "This month",
                modifier = Modifier.weight(1f)
            )
            DashboardMetricCard(
                icon = Icons.Default.ConfirmationNumber,
                iconTint = R2WSoftBlue,
                title = "Support Tickets",
                value = data?.todaySales ?: "3",
                caption = "Open tickets",
                modifier = Modifier.weight(1f),
                onClick = { }
            )
        }
    }
}

@Composable
private fun DashboardMetricCard(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    value: String,
    caption: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    Card(
        modifier = modifier
            .height(110.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, R2WBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(iconTint.copy(alpha = 0.13f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(28.dp))
            }
            Column(
                modifier = Modifier.padding(start = 12.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(title, color = R2WText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(value, color = R2WText, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                Text(caption, color = R2WMuted, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            }
        }
    }
}

@Composable
private fun QuickActionsCard(onActionClick: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Quick Actions", color = R2WText, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickActionButton(Icons.Default.ShoppingBag, R2WTeal, "Buy eSIM", "Browse plans", { onActionClick("store") }, Modifier.weight(1f))
            QuickActionButton(Icons.Default.Refresh, R2WPurple, "Recharge SIM", "Top up instantly", { onActionClick("recharge") }, Modifier.weight(1f))
            QuickActionButton(Icons.Default.People, R2WSoftBlue, "Allocate Balance", "Assign to dealers", { onActionClick("crm") }, Modifier.weight(1f))
            QuickActionButton(Icons.Default.AccountBalanceWallet, R2WOrange, "Wallet Request", "Request funds", { onActionClick("wallet") }, Modifier.weight(1f))
        }
    }
}

@Composable
private fun QuickActionButton(
    icon: ImageVector,
    iconTint: Color,
    label: String,
    caption: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .height(112.dp)
            .clickable(onClick = onClick),
        color = Color.White,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, R2WBorder),
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(iconTint.copy(alpha = 0.13f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(28.dp))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(label, color = R2WText, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(caption, color = R2WMuted, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        }
    }
}

@Composable
private fun RecentActivityCard(
    orders: List<MobileDashboardOrder>,
    onViewAll: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Recent Activity", color = R2WText, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            Text(
                "View All",
                color = R2WBlue,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable(onClick = onViewAll)
            )
        }
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, R2WBorder),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                val displayOrders = orders.ifEmpty {
                    listOf(
                        MobileDashboardOrder("1", "ACT-1", "Europe 10GB purchased", "Customer: Alex Johnson", null, "Completed"),
                        MobileDashboardOrder("2", "ACT-2", "Wallet request approved", "Amount: €250.00", null, "Approved"),
                        MobileDashboardOrder("3", "ACT-3", "TGT SIM recharged", "Number ending 5678", null, "Done")
                    )
                }.take(3)
                displayOrders.forEachIndexed { index, order ->
                    ActivityRow(order = order, iconTint = if (index == 2) R2WSoftBlue else R2WTeal)
                    if (index != displayOrders.lastIndex) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(R2WBorder)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivityRow(order: MobileDashboardOrder, iconTint: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(iconTint.copy(alpha = 0.13f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.ShoppingBag, contentDescription = null, tint = iconTint, modifier = Modifier.size(26.dp))
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 14.dp)
        ) {
            Text(order.title, color = R2WText, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(order.subtitle, color = R2WMuted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("28 May 2025", color = R2WMuted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Surface(color = statusColor(order.status).copy(alpha = 0.12f), shape = RoundedCornerShape(999.dp)) {
                Text(
                    text = order.status ?: "Completed",
                    color = statusColor(order.status),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun DashboardBottomNav(onActionClick: (String) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White,
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, R2WBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BottomNavItem(Icons.Default.Home, "Dashboard", true, {})
            BottomNavItem(Icons.Default.SimCard, "eSIMs", false, { onActionClick("check_gb") })
            Spacer(modifier = Modifier.size(64.dp))
            BottomNavItem(Icons.Default.Analytics, "Analytics", false, { onActionClick("orders") })
            BottomNavItem(Icons.Default.Person, "Profile", false, { onActionClick("profile") })
        }
    }
}

@Composable
private fun BottomNavItem(icon: ImageVector, title: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = null, tint = if (selected) R2WBlue else R2WMuted, modifier = Modifier.size(24.dp))
        Text(title, color = if (selected) R2WBlue else R2WMuted, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

private fun statusColor(status: String?): Color {
    val normalized = status.orEmpty().lowercase()
    return when {
        normalized.contains("fail") || normalized.contains("cancel") -> Color(0xFFDC2626)
        normalized.contains("pending") || normalized.contains("process") -> R2WOrange
        normalized.contains("done") -> R2WSoftBlue
        else -> R2WGreen
    }
}
