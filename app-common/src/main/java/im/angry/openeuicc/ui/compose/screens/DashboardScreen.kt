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
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.SimCard
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
import java.text.NumberFormat
import java.util.Locale

private val R2WBlue = Color(0xFF1263F1)
private val R2WBlueDark = Color(0xFF0047D8)
private val R2WText = Color(0xFF111827)
private val R2WMuted = Color(0xFF6B7280)
private val R2WBorder = Color(0xFFE5E7EB)
private val R2WBackground = Color(0xFFF8FAFF)
private val R2WGreen = Color(0xFF12A150)
private val R2WOrange = Color(0xFFF97316)
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
                modifier = Modifier.size(54.dp),
                color = R2WBlue,
                shape = RoundedCornerShape(999.dp),
                shadowElevation = 10.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(30.dp))
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
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item { Spacer(modifier = Modifier.height(2.dp)) }
                item { DashboardHeader(userName = userName) }
                item { RoleTabs() }
                item {
                    WalletHeroCard(
                        balance = formatDashboardMoney(data?.currentBalance, "$2,450.50"),
                        onAddFunds = onWalletClick,
                        onTransactions = { onActionClick("wallet") }
                    )
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            DashboardMetricCard(
                                icon = Icons.Default.Assessment,
                                iconTint = R2WBlue,
                                title = "Today Sales",
                                value = formatDashboardMoney(data?.todaySales, "$1,234"),
                                caption = "↗ 12.5%",
                                modifier = Modifier.weight(1f)
                            )
                            DashboardMetricCard(
                                icon = Icons.Default.CalendarMonth,
                                iconTint = R2WBlue,
                                title = "Monthly Sales",
                                value = formatDashboardMoney(data?.monthlySales, "$15,678"),
                                caption = "↗ 18.3%",
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            DashboardMetricCard(
                                icon = Icons.Default.SimCard,
                                iconTint = R2WTeal,
                                title = "Active eSIMs",
                                value = data?.activeEsimCount ?: "245",
                                caption = "● Active",
                                modifier = Modifier.weight(1f)
                            )
                            DashboardMetricCard(
                                icon = Icons.Default.Refresh,
                                iconTint = R2WOrange,
                                title = "Expiring Soon",
                                value = data?.expiredEsimCount ?: "12",
                                caption = "● Next 7 days",
                                captionColor = R2WOrange,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
                item {
                    RecentPurchasesCard(
                        orders = data?.recentOrders.orEmpty(),
                        onViewAll = { onActionClick("orders") }
                    )
                }
                item {
                    QuickActionsCard(onActionClick = onActionClick)
                }
                item { Spacer(modifier = Modifier.height(76.dp)) }
            }

            DashboardBottomNav(onActionClick = onActionClick)
        }
    }
}

@Composable
private fun DashboardHeader(userName: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Roam", color = R2WText, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold)
                Text("2", color = R2WTeal, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold)
                Text("World", color = R2WText, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold)
                Box(
                    modifier = Modifier
                        .padding(start = 7.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(R2WBlue)
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                ) {
                    Text("B2B", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Welcome back, ${userName.ifBlank { "Admin" }}",
                color = R2WText,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "Here's what's happening with your business today.",
                color = R2WMuted,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2
            )
        }
        IconButton(onClick = { }, modifier = Modifier.size(40.dp)) {
            Box {
                Icon(Icons.Default.Notifications, contentDescription = null, tint = R2WText, modifier = Modifier.size(25.dp))
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(8.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color(0xFFEF4444))
                )
            }
        }
    }
}

@Composable
private fun RoleTabs() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, R2WBorder)
    ) {
        Row(modifier = Modifier.padding(3.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            RoleTab("Admin", Icons.Default.Person, true, Modifier.weight(1f))
            RoleTab("Reseller", Icons.Default.Person, false, Modifier.weight(1f))
            RoleTab("Dealer", Icons.Default.Person, false, Modifier.weight(1f))
        }
    }
}

@Composable
private fun RoleTab(title: String, icon: ImageVector, selected: Boolean, modifier: Modifier) {
    Surface(
        modifier = modifier.height(38.dp),
        color = Color.White,
        shape = RoundedCornerShape(13.dp),
        border = if (selected) BorderStroke(1.dp, R2WBlue) else null
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = if (selected) R2WBlue else R2WMuted, modifier = Modifier.size(16.dp))
            Text(
                text = title,
                modifier = Modifier.padding(start = 7.dp),
                color = if (selected) R2WBlue else R2WMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun WalletHeroCard(
    balance: String,
    onAddFunds: () -> Unit,
    onTransactions: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(134.dp),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = R2WBlue),
        elevation = CardDefaults.cardElevation(defaultElevation = 5.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.horizontalGradient(listOf(R2WBlue, R2WBlueDark)))
                .padding(18.dp)
        ) {
            Column(modifier = Modifier.align(Alignment.CenterStart)) {
                Text("Wallet Balance", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text(
                    text = balance,
                    color = Color.White,
                    fontSize = 33.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text("≈ $13,482.56 USD", color = Color.White.copy(alpha = 0.86f), fontSize = 12.sp)
            }
            Surface(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .clickable(onClick = onAddFunds),
                color = Color.White.copy(alpha = 0.92f),
                shape = RoundedCornerShape(22.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = R2WBlue, modifier = Modifier.size(17.dp))
                    Text("Add Funds", color = R2WBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
            Text(
                text = "View Transactions ›",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .clickable(onClick = onTransactions)
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
    captionColor: Color = R2WGreen
) {
    Card(
        modifier = modifier.height(96.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, R2WBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(iconTint.copy(alpha = 0.13f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(25.dp))
            }
            Column(modifier = Modifier.padding(start = 10.dp)) {
                Text(title, color = R2WMuted, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 2)
                Text(value, color = R2WText, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                Surface(color = captionColor.copy(alpha = 0.10f), shape = RoundedCornerShape(10.dp)) {
                    Text(
                        text = caption,
                        color = captionColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentPurchasesCard(
    orders: List<MobileDashboardOrder>,
    onViewAll: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, R2WBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Recent Purchases", color = R2WText, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    "View All",
                    color = R2WBlue,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable(onClick = onViewAll)
                )
            }
            val displayOrders = orders.ifEmpty {
                listOf(
                    MobileDashboardOrder("1", "ORD-2025-0512-1021", "Global Connect LLC", "10 × Europe eSIM • May 25, 2025", "$200.00", "Completed")
                )
            }.take(1)
            displayOrders.forEach { order ->
                PurchaseRow(order)
            }
        }
    }
}

@Composable
private fun PurchaseRow(order: MobileDashboardOrder) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = Color.White,
        border = BorderStroke(1.dp, R2WBorder)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(R2WBlue.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Business, contentDescription = null, tint = R2WBlue, modifier = Modifier.size(23.dp))
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
            ) {
                Text(order.title, color = R2WText, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(order.subtitle, color = R2WMuted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(formatDashboardMoney(order.amount, "$200.00"), color = R2WText, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                Surface(color = statusColor(order.status).copy(alpha = 0.11f), shape = RoundedCornerShape(10.dp)) {
                    Text(
                        text = order.status ?: "Completed",
                        color = statusColor(order.status),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickActionsCard(onActionClick: (String) -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, R2WBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Quick Actions", color = R2WText, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickActionButton(Icons.Default.ShoppingCart, "Buy eSIM", { onActionClick("store") }, Modifier.weight(1f))
                QuickActionButton(Icons.Default.GridView, "OpenEUICC", { onActionClick("openeuicc") }, Modifier.weight(1f))
                QuickActionButton(Icons.Default.ReceiptLong, "eSIM History", { onActionClick("orders") }, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickActionButton(Icons.Default.AccountBalanceWallet, "Wallet", { onActionClick("wallet") }, Modifier.weight(1f))
                QuickActionButton(Icons.Default.Refresh, "TGT Recharge", { onActionClick("recharge") }, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun QuickActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .height(82.dp)
            .clickable(onClick = onClick),
        color = Color.White,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, R2WBorder),
        shadowElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = null, tint = R2WBlue, modifier = Modifier.size(26.dp))
            Spacer(modifier = Modifier.height(7.dp))
            Text(label, color = R2WText, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
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
                .height(70.dp)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BottomNavItem(Icons.Default.GridView, "Dashboard", true, {})
            BottomNavItem(Icons.Default.SimCard, "eSIMs", false, { onActionClick("check_gb") })
            BottomNavItem(Icons.Default.People, "Customers", false, { onActionClick("crm") })
            BottomNavItem(Icons.Default.ReceiptLong, "Transactions", false, { onActionClick("orders") })
            BottomNavItem(Icons.Default.Add, "More", false, { onActionClick("more") })
        }
    }
}

@Composable
private fun BottomNavItem(icon: ImageVector, title: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 5.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = null, tint = if (selected) R2WBlue else R2WMuted, modifier = Modifier.size(23.dp))
        Text(title, color = if (selected) R2WBlue else R2WMuted, fontSize = 10.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

private fun statusColor(status: String?): Color {
    val normalized = status.orEmpty().lowercase()
    return when {
        normalized.contains("fail") || normalized.contains("cancel") -> Color(0xFFDC2626)
        normalized.contains("pending") || normalized.contains("process") -> R2WBlue
        else -> R2WGreen
    }
}

private fun formatDashboardMoney(value: String?, fallback: String): String {
    val raw = value?.trim().orEmpty()
    if (raw.isBlank()) return fallback
    if (raw.any { it == '$' || it == '€' || it == '₺' || it.isLetter() }) return raw
    val numeric = raw.replace(",", "").toDoubleOrNull() ?: return raw
    val formatter = NumberFormat.getCurrencyInstance(Locale.US)
    return formatter.format(numeric)
}
