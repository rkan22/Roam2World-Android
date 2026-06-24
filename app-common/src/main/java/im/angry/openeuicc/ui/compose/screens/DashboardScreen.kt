@file:OptIn(ExperimentalMaterial3Api::class)

package im.angry.openeuicc.ui.compose.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CheckCircle
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
import androidx.compose.material.icons.filled.Storefront
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import im.angry.openeuicc.auth.MobileDashboardData
import im.angry.openeuicc.auth.MobileDashboardOrder
import im.angry.openeuicc.common.R

private val R2WBlue = Color(0xFF1263F1)
private val R2WBlueDark = Color(0xFF0047D8)
private val R2WText = Color(0xFF111827)
private val R2WMuted = Color(0xFF6B7280)
private val R2WBorder = Color(0xFFE5E7EB)
private val R2WBackground = Color(0xFFF4F6FA)
private val R2WGreen = Color(0xFF12A150)
private val R2WOrange = Color(0xFFF97316)
private val R2WPurple = Color(0xFF7C3AED)
private val R2WTeal = Color(0xFF14B8A6)

@Composable
fun DashboardScreen(
    userName: String,
    data: MobileDashboardData?,
    onWalletClick: () -> Unit,
    onActionClick: (String) -> Unit
) {
    Scaffold(
        containerColor = Color(0xFFF4F6FA)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF4F6FA))
                .padding(padding)
        ) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item { Spacer(modifier = Modifier.height(10.dp)) }
                item { DashboardHeader(userName = userName) }
                item {
                    WalletHeroCard(
                        balance = data?.currentBalance ?: "0",
                        onAddFunds = onWalletClick,
                        onTransactions = { onActionClick("wallet") }
                    )
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            DashboardMetricCard(
                                icon = Icons.Default.Assessment,
                                iconTint = R2WBlue,
                                title = "Today Sales",
                                value = data?.todaySales ?: "0",
                                caption = "↗ 12.5%",
                                onClick = { onActionClick("transactions") },
                                modifier = Modifier.weight(1f)
                            )
                            DashboardMetricCard(
                                icon = Icons.Default.CalendarMonth,
                                iconTint = R2WBlue,
                                title = "Monthly Sales",
                                value = data?.monthlySales ?: "0",
                                caption = "↗ 18.3%",
                                onClick = { onActionClick("transactions") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            DashboardMetricCard(
                                icon = Icons.Default.SimCard,
                                iconTint = R2WTeal,
                                title = "Active eSIMs",
                                value = data?.activeEsimCount ?: "0",
                                caption = "● Active",
                                onClick = { onActionClick("esims") },
                                modifier = Modifier.weight(1f)
                            )
                            DashboardMetricCard(
                                icon = Icons.Default.Refresh,
                                iconTint = R2WOrange,
                                title = "Expiring Soon",
                                value = data?.expiredEsimCount ?: "0",
                                caption = "● Next 7 days",
                                captionColor = R2WOrange,
                                onClick = { onActionClick("esims") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
                item {
                    QuickActionsCard(onActionClick = onActionClick)
                }
                item {
                    RecentPurchasesCard(
                        orders = data?.recentOrders.orEmpty(),
                        onViewAll = { onActionClick("orders") }
                    )
                }
                item { Spacer(modifier = Modifier.height(28.dp)) }
            }

            DashboardBottomNav(onActionClick = onActionClick)
        }
    }
}

@Composable
private fun DashboardHeader(userName: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            Spacer(modifier = Modifier.weight(1f))

            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = 0.70f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Notifications,
                    contentDescription = null,
                    tint = R2WText,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "Dashboard",
            color = R2WText,
            fontSize = 34.sp,
            fontWeight = FontWeight.ExtraBold
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Manage your eSIM business in one place.",
            color = R2WMuted,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )
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
        modifier = modifier.height(44.dp),
        color = Color.White,
        shape = RoundedCornerShape(14.dp),
        border = if (selected) BorderStroke(1.dp, R2WBlue) else null
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = if (selected) R2WBlue else R2WMuted, modifier = Modifier.size(18.dp))
            Text(
                text = title,
                modifier = Modifier.padding(start = 8.dp),
                color = if (selected) R2WBlue else R2WMuted,
                fontSize = 13.sp,
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
            .height(176.dp),
        shape = RoundedCornerShape(30.dp),
        colors = CardDefaults.cardColors(containerColor = R2WBlue),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color(0xFF0F6BFF),
                            Color(0xFF0647D9),
                            Color(0xFF0736A8)
                        )
                    )
                )
                .padding(20.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.r2w_ic_wallet),
                contentDescription = null,
                modifier = Modifier
                    .size(132.dp)
                    .align(Alignment.CenterEnd),
                colorFilter = ColorFilter.tint(Color.White.copy(alpha = 0.20f))
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(end = 118.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Available Balance",
                        color = Color.White.copy(alpha = 0.88f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = balance,
                        color = Color.White,
                        fontSize = 36.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clickable(onClick = onAddFunds),
                        color = Color.White,
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = null,
                                tint = R2WBlue,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Add Funds",
                                color = R2WBlue,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clickable(onClick = onTransactions),
                        color = Color.White.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f))
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Transactions",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
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
    captionColor: Color = R2WGreen,
    onClick: () -> Unit = {}
) {
    Card(
        modifier = modifier
            .height(112.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE6EAF0)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(iconTint.copy(alpha = 0.13f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(21.dp))
            }
            Column(modifier = Modifier.padding(start = 10.dp)) {
                Text(title, color = R2WMuted, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                Text(value, color = R2WText, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
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
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE6EAF0)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Recent Activity", color = R2WText, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    "View All",
                    color = R2WBlue,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable(onClick = onViewAll)
                )
            }
            val displayOrders = orders.take(3)
            if (displayOrders.isEmpty()) {
                Text(
                    "No recent activity yet",
                    color = R2WMuted,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                displayOrders.forEach { order ->
                    PurchaseRow(order)
                }
            }
        }
    }
}

@Composable
private fun PurchaseRow(order: MobileDashboardOrder) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFFF8FAFD),
        border = BorderStroke(1.dp, R2WBorder)
    ) {
        Row(
            modifier = Modifier.padding(13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(R2WBlue.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Business,
                    contentDescription = null,
                    tint = R2WBlue,
                    modifier = Modifier.size(22.dp)
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp, end = 8.dp)
            ) {
                Text(
                    text = compactOrderTitle(order.title),
                    color = R2WText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = compactOrderSubtitle(order.subtitle),
                    color = R2WMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = normalizeAmount(order.amount),
                    color = R2WText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1
                )

                Spacer(modifier = Modifier.height(5.dp))

                Surface(
                    color = statusColor(order.status).copy(alpha = 0.11f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = (order.status ?: "Completed").lowercase(),
                        color = statusColor(order.status),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        maxLines = 1
                    )
                }
            }
        }
    }
}

private fun compactOrderTitle(value: String?): String {
    val raw = value.orEmpty().trim()
    if (raw.isBlank()) return "Order"
    return raw
        .replace("304-", "")
        .replace("905-", "")
        .replace("439-", "")
        .take(22)
}

private fun compactOrderSubtitle(value: String?): String {
    val raw = value.orEmpty().trim()
    if (raw.isBlank()) return "Recent order"

    val cleaned = raw
        .replace("T", " ")
        .replace(Regex("\\.\\d+"), "")
        .replace(Regex("\\+00:?00"), "")
        .trim()

    return formatDashboardDate(cleaned)
}

private fun formatDashboardDate(value: String): String {
    // Expected backend formats:
    // 2026-06-18 19:43:04
    // 2026-06-18T19:43:04.570455+00:00
    val datePart = value.take(10)
    val timePart = value.drop(11).take(5)

    if (!Regex("\\d{4}-\\d{2}-\\d{2}").matches(datePart)) {
        return value.take(28)
    }

    val year = datePart.substring(0, 4)
    val month = datePart.substring(5, 7)
    val day = datePart.substring(8, 10).trimStart('0').ifBlank { "0" }

    val monthName = when (month) {
        "01" -> "Jan"
        "02" -> "Feb"
        "03" -> "Mar"
        "04" -> "Apr"
        "05" -> "May"
        "06" -> "Jun"
        "07" -> "Jul"
        "08" -> "Aug"
        "09" -> "Sep"
        "10" -> "Oct"
        "11" -> "Nov"
        "12" -> "Dec"
        else -> month
    }

    return if (Regex("\\d{2}:\\d{2}").matches(timePart)) {
        "$day $monthName $year, $timePart"
    } else {
        "$day $monthName $year"
    }
}

private fun normalizeAmount(value: String?): String {
    val raw = value.orEmpty().trim()
    if (raw.isBlank()) return "USD 0.00"
    return if (raw.contains("USD", ignoreCase = true) || raw.contains("$")) raw else "USD $raw"
}

@Composable
private fun QuickActionsCard(onActionClick: (String) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE6EAF0)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Quick Actions",
                    color = R2WText,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "Manage",
                    color = R2WBlue,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickActionButton(Icons.Default.ShoppingCart, "Buy eSIM", { onActionClick("store") }, Modifier.weight(1f))
                QuickActionButton(Icons.Default.SimCard, "OpenEUICC", { onActionClick("openeuicc") }, Modifier.weight(1f))
                QuickActionButton(Icons.Default.ReceiptLong, "History", { onActionClick("orders") }, Modifier.weight(1f))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickActionButton(Icons.Default.AccountBalanceWallet, "Wallet", { onActionClick("wallet") }, Modifier.weight(1f))
                QuickActionButton(Icons.Default.CheckCircle, "Approvals", { onActionClick("wallet_approvals") }, Modifier.weight(1f))
                QuickActionButton(Icons.Default.Refresh, "Recharge", { onActionClick("recharge") }, Modifier.weight(1f))
                QuickActionButton(Icons.Default.People, "Dealers", { onActionClick("dealers") }, Modifier.weight(1f))
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
            .height(86.dp)
            .clickable(onClick = onClick),
        color = Color(0xFFF8FAFC),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, Color(0xFFE8EEF6)),
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(R2WBlue.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = R2WBlue,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = label,
                color = R2WText,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}


@Composable
private fun DashboardBottomNav(onActionClick: (String) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(30.dp),
            color = Color.White,
            border = BorderStroke(1.dp, Color(0xFFE6EAF0)),
            shadowElevation = 14.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(62.dp)
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ModernBottomNavItem(
                    icon = "⌂",
                    label = "Home",
                    selected = true,
                    onClick = { }
                )
                ModernBottomNavItem(
                    icon = "▦",
                    label = "Packages",
                    selected = false,
                    onClick = { onActionClick("packages") }
                )
                ModernBottomNavItem(
                    icon = "◉",
                    label = "eSIMs",
                    selected = false,
                    onClick = { onActionClick("esims") }
                )
                ModernBottomNavItem(
                    icon = "◈",
                    label = "Wallet",
                    selected = false,
                    onClick = { onActionClick("wallet") }
                )
                ModernBottomNavItem(
                    icon = "•••",
                    label = "More",
                    selected = false,
                    onClick = { onActionClick("more") }
                )
            }
        }
    }
}

@Composable
private fun ModernBottomNavItem(
    icon: String,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val background = if (selected) R2WBlue.copy(alpha = 0.10f) else Color.Transparent
    val textColor = if (selected) R2WBlue else R2WMuted

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = icon,
            color = textColor,
            fontSize = if (icon == "•••") 18.sp else 20.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            color = textColor,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1
        )
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
        normalized.contains("pending") || normalized.contains("process") -> R2WBlue
        else -> R2WGreen
    }
}
