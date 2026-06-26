@file:OptIn(ExperimentalMaterial3Api::class)

package im.angry.openeuicc.ui.compose.screens.admin

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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

private val AdminBlue = Color(0xFF1263F1)
private val AdminNavy = Color(0xFF071633)
private val AdminBackground = Color(0xFFF4F7FB)
private val AdminCard = Color.White
private val AdminText = Color(0xFF101828)
private val AdminMuted = Color(0xFF667085)
private val AdminBorder = Color(0xFFE5EAF2)
private val AdminGreen = Color(0xFF16A34A)
private val AdminOrange = Color(0xFFF97316)
private val AdminRed = Color(0xFFDC2626)
private val AdminPurple = Color(0xFF7C3AED)

enum class AdminBottomNavItem {
    Dashboard,
    Partners,
    Orders,
    Pricing,
    More
}

@Composable
fun AdminDashboardScreen(
    totalRevenue: String = "$24,850.00",
    totalOrders: String = "1,248",
    totalPartners: String = "156",
    pendingActions: String = "23",
    onMenuClick: () -> Unit = {},
    onNotificationsClick: () -> Unit = {},
    onOrdersClick: () -> Unit = {},
    onPartnersClick: () -> Unit = {},
    onWalletApprovalsClick: () -> Unit = {},
    onReportsClick: () -> Unit = {},
    onBottomNavClick: (AdminBottomNavItem) -> Unit = {}
) {
    Scaffold(
        containerColor = AdminBackground,
        bottomBar = {
            AdminBottomNavigation(
                selected = AdminBottomNavItem.Dashboard,
                onClick = onBottomNavClick
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(AdminBackground)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item { Spacer(modifier = Modifier.height(8.dp)) }

            item {
                AdminDashboardHero(
                    onMenuClick = onMenuClick,
                    onNotificationsClick = onNotificationsClick
                )
            }

            item {
                AdminKpiGrid(
                    totalRevenue = totalRevenue,
                    totalOrders = totalOrders,
                    totalPartners = totalPartners,
                    pendingActions = pendingActions
                )
            }

            item {
                AdminSalesPerformanceCard(totalRevenue = totalRevenue)
            }

            item {
                AdminQuickActionsGrid(
                    onOrdersClick = onOrdersClick,
                    onPartnersClick = onPartnersClick,
                    onWalletApprovalsClick = onWalletApprovalsClick,
                    onReportsClick = onReportsClick
                )
            }

            item {
                AdminRecentActivityCard()
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun AdminDashboardHero(
    onMenuClick: () -> Unit,
    onNotificationsClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = AdminNavy,
        shadowElevation = 8.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color(0xFF071633),
                            Color(0xFF08245F),
                            Color(0xFF0D3CA6)
                        )
                    )
                )
                .padding(18.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onMenuClick,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Menu,
                            contentDescription = null,
                            tint = Color.White
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Roam2World",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "Admin Console",
                            color = Color.White.copy(alpha = 0.70f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Box {
                        IconButton(
                            onClick = onNotificationsClick,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.Notifications,
                                contentDescription = null,
                                tint = Color.White
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .align(Alignment.TopEnd)
                                .clip(RoundedCornerShape(50))
                                .background(AdminRed),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "5",
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(22.dp))

                Text(
                    text = "Admin Dashboard",
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Overview & operations summary",
                    color = Color.White.copy(alpha = 0.72f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun AdminKpiGrid(
    totalRevenue: String,
    totalOrders: String,
    totalPartners: String,
    pendingActions: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AdminKpiCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.CreditCard,
                iconTint = AdminBlue,
                title = "Revenue",
                value = totalRevenue,
                caption = "+12.5%",
                captionColor = AdminGreen
            )
            AdminKpiCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.ShoppingCart,
                iconTint = AdminGreen,
                title = "Orders",
                value = totalOrders,
                caption = "+8.2%",
                captionColor = AdminGreen
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AdminKpiCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.People,
                iconTint = AdminPurple,
                title = "Partners",
                value = totalPartners,
                caption = "Resellers & dealers",
                captionColor = AdminMuted
            )
            AdminKpiCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Assessment,
                iconTint = AdminOrange,
                title = "Pending",
                value = pendingActions,
                caption = "Needs review",
                captionColor = AdminOrange
            )
        }
    }
}

@Composable
private fun AdminKpiCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    iconTint: Color,
    title: String,
    value: String,
    caption: String,
    captionColor: Color
) {
    Card(
        modifier = modifier.height(116.dp),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = AdminCard),
        border = BorderStroke(1.dp, AdminBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(iconTint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column {
                Text(
                    text = title,
                    color = AdminMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                Text(
                    text = value,
                    color = AdminText,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = caption,
                    color = captionColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun AdminSalesPerformanceCard(totalRevenue: String) {
    AdminSectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column {
                Text(
                    text = "Sales Performance",
                    color = AdminText,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = totalRevenue,
                    color = AdminText,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "Current period",
                    color = AdminMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = AdminBlue.copy(alpha = 0.08f)
            ) {
                Text(
                    text = "This Week",
                    color = AdminBlue,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ChartBar("Mon", 0.42f)
            ChartBar("Tue", 0.68f)
            ChartBar("Wed", 0.54f)
            ChartBar("Thu", 0.76f)
            ChartBar("Fri", 0.61f)
            ChartBar("Sat", 0.88f)
            ChartBar("Sun", 1f)
        }
    }
}

@Composable
private fun ChartBar(label: String, progress: Float) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = AdminMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(34.dp)
        )

        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .weight(1f)
                .height(7.dp)
                .clip(RoundedCornerShape(50)),
            color = AdminBlue,
            trackColor = AdminBlue.copy(alpha = 0.10f)
        )
    }
}

@Composable
private fun AdminQuickActionsGrid(
    onOrdersClick: () -> Unit,
    onPartnersClick: () -> Unit,
    onWalletApprovalsClick: () -> Unit,
    onReportsClick: () -> Unit
) {
    AdminSectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Quick Actions",
                color = AdminText,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = "View all",
                color = AdminBlue,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AdminQuickActionCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.ShoppingCart,
                    title = "Orders",
                    subtitle = "Manage orders",
                    onClick = onOrdersClick
                )
                AdminQuickActionCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Business,
                    title = "Partners",
                    subtitle = "Resellers & dealers",
                    onClick = onPartnersClick
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AdminQuickActionCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.CreditCard,
                    title = "Wallet",
                    subtitle = "Approvals",
                    onClick = onWalletApprovalsClick
                )
                AdminQuickActionCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Assessment,
                    title = "Reports",
                    subtitle = "View insights",
                    onClick = onReportsClick
                )
            }
        }
    }
}

@Composable
private fun AdminQuickActionCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .height(86.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFFF8FAFF),
        border = BorderStroke(1.dp, AdminBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(AdminBlue.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = AdminBlue,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column {
                Text(
                    text = title,
                    color = AdminText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1
                )
                Text(
                    text = subtitle,
                    color = AdminMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun AdminRecentActivityCard() {
    AdminSectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Recent Activity",
                color = AdminText,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = "View all",
                color = AdminBlue,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        AdminActivityRow(
            iconTint = AdminBlue,
            title = "New order #ORD-92341",
            subtitle = "Acme Travel Ltd",
            time = "2m ago"
        )

        AdminActivityRow(
            iconTint = AdminOrange,
            title = "Wallet request from BlueSky Corp",
            subtitle = "$2,500.00 pending approval",
            time = "10m ago"
        )

        AdminActivityRow(
            iconTint = AdminGreen,
            title = "Provider markup updated",
            subtitle = "Airalo default markup changed",
            time = "1h ago"
        )
    }
}

@Composable
private fun AdminActivityRow(
    iconTint: Color,
    title: String,
    subtitle: String,
    time: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(iconTint.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.ReceiptLong,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(19.dp)
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = AdminText,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                color = AdminMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Text(
            text = time,
            color = AdminMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun AdminSectionCard(
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = AdminCard),
        border = BorderStroke(1.dp, AdminBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            content = content
        )
    }
}

@Composable
private fun AdminBottomNavigation(
    selected: AdminBottomNavItem,
    onClick: (AdminBottomNavItem) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        color = Color.White,
        shadowElevation = 14.dp,
        border = BorderStroke(1.dp, AdminBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(66.dp)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            AdminBottomNavButton(
                icon = Icons.Default.GridView,
                label = "Dashboard",
                selected = selected == AdminBottomNavItem.Dashboard,
                onClick = { onClick(AdminBottomNavItem.Dashboard) }
            )
            AdminBottomNavButton(
                icon = Icons.Default.People,
                label = "Partners",
                selected = selected == AdminBottomNavItem.Partners,
                onClick = { onClick(AdminBottomNavItem.Partners) }
            )
            AdminBottomNavButton(
                icon = Icons.Default.ShoppingCart,
                label = "Orders",
                selected = selected == AdminBottomNavItem.Orders,
                onClick = { onClick(AdminBottomNavItem.Orders) }
            )
            AdminBottomNavButton(
                icon = Icons.Default.CreditCard,
                label = "Pricing",
                selected = selected == AdminBottomNavItem.Pricing,
                onClick = { onClick(AdminBottomNavItem.Pricing) }
            )
            AdminBottomNavButton(
                icon = Icons.Default.Menu,
                label = "More",
                selected = selected == AdminBottomNavItem.More,
                onClick = { onClick(AdminBottomNavItem.More) }
            )
        }
    }
}

@Composable
private fun AdminBottomNavButton(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val color = if (selected) AdminBlue else AdminMuted
    val background = if (selected) AdminBlue.copy(alpha = 0.09f) else Color.Transparent

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = label,
            color = color,
            fontSize = 9.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1
        )
    }
}
