package im.angry.openeuicc.ui.compose.screens.admin

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import im.angry.openeuicc.common.R

private val NotifyBg = Color(0xFFF6F8FC)
private val NotifyNavy = Color(0xFF061A3F)
private val NotifyNavy2 = Color(0xFF123EAD)
private val NotifyBlue = Color(0xFF1263F1)
private val NotifyText = Color(0xFF101828)
private val NotifyMuted = Color(0xFF667085)
private val NotifyBorder = Color(0xFFE1E8F2)
private val NotifyGreen = Color(0xFF16A34A)
private val NotifyOrange = Color(0xFFF97316)
private val NotifyRed = Color(0xFFEF4444)

@Composable
fun AdminNotificationsOverviewScreen(
    unreadCount: String = "0",
    onOpenNotificationsClick: () -> Unit = {},
    onBottomNavClick: (AdminBottomNavItem) -> Unit = {}
) {
    val unread = unreadCount.toIntOrNull() ?: 0

    Scaffold(
        containerColor = NotifyBg,
        bottomBar = {
            NotifyBottomNav(
                selected = AdminBottomNavItem.More,
                onClick = onBottomNavClick
            )
        }
    ) { inner ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(NotifyBg)
                .padding(inner)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(Modifier.height(8.dp)) }

            item {
                NotificationsHero(unreadCount = unreadCount)
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NotificationMetricCard(
                        modifier = Modifier.weight(1f),
                        icon = R.drawable.admin_icon_notifications,
                        label = "Unread",
                        value = unreadCount,
                        sub = if (unread > 0) "needs attention" else "all clear",
                        subColor = if (unread > 0) NotifyRed else NotifyGreen
                    )
                    NotificationMetricCard(
                        modifier = Modifier.weight(1f),
                        icon = R.drawable.admin_icon_doc,
                        label = "Broadcasts",
                        value = "Live",
                        sub = "backend synced",
                        subColor = NotifyBlue
                    )
                }
            }

            item {
                NotifySection(title = "Notification Center") {
                    NotifyActionRow(
                        icon = R.drawable.admin_icon_notifications,
                        title = "Open Notification Center",
                        subtitle = "View messages, broadcasts and admin alerts",
                        badge = unreadCount,
                        badgeColor = if (unread > 0) NotifyRed else NotifyGreen,
                        onClick = onOpenNotificationsClick
                    )
                }
            }

            item {
                NotifySection(title = "Live Summary") {
                    NotificationStatusLine("Unread notifications", unreadCount)
                    NotificationStatusLine("Delivery source", "Backend API")
                    NotificationStatusLine("Admin visibility", "Enabled")
                }
            }

            item {
                NotifySection(title = "Recent Notification Types") {
                    MiniNotificationRow(
                        icon = R.drawable.admin_icon_money,
                        title = "Wallet updates",
                        subtitle = "Approval and top-up status changes",
                        dotColor = NotifyOrange
                    )
                    MiniNotificationRow(
                        icon = R.drawable.admin_icon_orders,
                        title = "Order updates",
                        subtitle = "Processing and fulfilment events",
                        dotColor = NotifyBlue
                    )
                    MiniNotificationRow(
                        icon = R.drawable.admin_icon_health,
                        title = "System alerts",
                        subtitle = "Health, sync and platform warnings",
                        dotColor = NotifyRed
                    )
                }
            }

            item { Spacer(Modifier.height(10.dp)) }
        }
    }
}

@Composable
private fun NotificationsHero(unreadCount: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = NotifyNavy),
        elevation = CardDefaults.cardElevation(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(148.dp)
                .background(Brush.horizontalGradient(listOf(NotifyNavy, NotifyNavy2)))
                .padding(18.dp)
        ) {
            Column {
                Text(
                    "Notifications",
                    color = Color.White,
                    fontSize = 27.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Broadcasts, alerts & admin messages",
                    color = Color.White.copy(alpha = 0.72f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(16.dp))
                Surface(
                    color = Color.White.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text(
                        "$unreadCount unread notifications",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun NotificationMetricCard(
    modifier: Modifier,
    @DrawableRes icon: Int,
    label: String,
    value: String,
    sub: String,
    subColor: Color
) {
    Card(
        modifier = modifier.height(120.dp),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, NotifyBorder),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Image(painterResource(icon), contentDescription = null, modifier = Modifier.size(38.dp))
            Column {
                Text(label, color = NotifyMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(value, color = NotifyText, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                Text(sub, color = subColor, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }
    }
}

@Composable
private fun NotifySection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, NotifyBorder),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, color = NotifyText, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun NotifyActionRow(
    @DrawableRes icon: Int,
    title: String,
    subtitle: String,
    badge: String,
    badgeColor: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(painterResource(icon), contentDescription = null, modifier = Modifier.size(44.dp))
        Spacer(Modifier.size(12.dp))

        Column(Modifier.weight(1f)) {
            Text(title, color = NotifyText, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
            Text(
                subtitle,
                color = NotifyMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Surface(
            color = badgeColor.copy(alpha = 0.12f),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text(
                badge,
                color = badgeColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
            )
        }

        Text("  ›", color = NotifyMuted, fontSize = 24.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun MiniNotificationRow(
    @DrawableRes icon: Int,
    title: String,
    subtitle: String,
    dotColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(62.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            Image(painterResource(icon), contentDescription = null, modifier = Modifier.size(40.dp))
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .align(Alignment.TopEnd)
                    .background(dotColor, CircleShape)
            )
        }

        Spacer(Modifier.size(12.dp))

        Column(Modifier.weight(1f)) {
            Text(title, color = NotifyText, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
            Text(
                subtitle,
                color = NotifyMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun NotificationStatusLine(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(38.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = NotifyMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Text(value, color = NotifyText, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun NotifyBottomNav(
    selected: AdminBottomNavItem,
    onClick: (AdminBottomNavItem) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        color = Color.White,
        shadowElevation = 14.dp,
        border = BorderStroke(1.dp, NotifyBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(66.dp)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            NotifyBottomItem(Icons.Default.GridView, "Dashboard", selected == AdminBottomNavItem.Dashboard) { onClick(AdminBottomNavItem.Dashboard) }
            NotifyBottomItem(Icons.Default.People, "Partners", selected == AdminBottomNavItem.Partners) { onClick(AdminBottomNavItem.Partners) }
            NotifyBottomItem(Icons.Default.ShoppingCart, "Orders", selected == AdminBottomNavItem.Orders) { onClick(AdminBottomNavItem.Orders) }
            NotifyBottomItem(Icons.Default.CreditCard, "Pricing", selected == AdminBottomNavItem.Pricing) { onClick(AdminBottomNavItem.Pricing) }
            NotifyBottomItem(Icons.Default.MoreHoriz, "More", selected == AdminBottomNavItem.More) { onClick(AdminBottomNavItem.More) }
        }
    }
}

@Composable
private fun NotifyBottomItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val color = if (selected) NotifyBlue else NotifyMuted
    val bg = if (selected) Color(0xFFEAF2FF) else Color.Transparent

    Column(
        modifier = Modifier
            .size(width = 74.dp, height = 54.dp)
            .background(bg, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(21.dp))
        Spacer(Modifier.height(3.dp))
        Text(label, color = color, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
    }
}
