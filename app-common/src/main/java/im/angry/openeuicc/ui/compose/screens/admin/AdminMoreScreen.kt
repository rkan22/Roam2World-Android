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
import androidx.compose.foundation.layout.width
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

private val MoreBg = Color(0xFFF6F8FC)
private val MoreNavy = Color(0xFF061A3F)
private val MoreNavy2 = Color(0xFF123EAD)
private val MoreBlue = Color(0xFF1263F1)
private val MoreText = Color(0xFF101828)
private val MoreMuted = Color(0xFF667085)
private val MoreBorder = Color(0xFFE1E8F2)
private val MoreRed = Color(0xFFEF4444)
private val MoreGreen = Color(0xFF16A34A)
private val MoreOrange = Color(0xFFF97316)

@Composable
fun AdminMoreScreen(
    unreadNotifications: String = "1",
    pendingWallet: String = "0",
    supportTickets: String = "0",
    onNotificationsClick: () -> Unit = {},
    onActivityLogsClick: () -> Unit = {},
    onSupportTicketsClick: () -> Unit = {},
    onWalletApprovalsClick: () -> Unit = {},
    onProviderMarkupsClick: () -> Unit = {},
    onReportsClick: () -> Unit = {},
    onSystemHealthClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onLogoutClick: () -> Unit = {},
    onBottomNavClick: (AdminBottomNavItem) -> Unit = {}
) {
    Scaffold(
        containerColor = MoreBg,
        bottomBar = {
            MoreBottomNav(
                selected = AdminBottomNavItem.More,
                onClick = onBottomNavClick
            )
        }
    ) { inner ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MoreBg)
                .padding(inner)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(Modifier.height(8.dp)) }

            item {
                MoreHero()
            }

            item {
                MoreSection(title = "Admin Tools") {
                    MoreRow(
                        icon = R.drawable.admin_icon_notifications,
                        title = "Notifications",
                        subtitle = "Broadcasts and unread messages",
                        badge = unreadNotifications,
                        badgeColor = MoreRed,
                        onClick = onNotificationsClick
                    )
                    MoreRow(
                        icon = R.drawable.admin_icon_doc,
                        title = "Activity Logs",
                        subtitle = "Audit trail and admin actions",
                        onClick = onActivityLogsClick
                    )
                    MoreRow(
                        icon = R.drawable.admin_icon_support,
                        title = "Support Tickets",
                        subtitle = "Customer and partner support",
                        badge = supportTickets,
                        badgeColor = MoreOrange,
                        onClick = onSupportTicketsClick
                    )
                    MoreRow(
                        icon = R.drawable.admin_icon_money,
                        title = "Wallet Approvals",
                        subtitle = "Pending reseller/dealer top-ups",
                        badge = pendingWallet,
                        badgeColor = MoreGreen,
                        onClick = onWalletApprovalsClick
                    )
                }
            }

            item {
                MoreSection(title = "Operations") {
                    MoreRow(
                        icon = R.drawable.admin_icon_tag,
                        title = "Provider Markups",
                        subtitle = "Pricing rules and provider margins",
                        onClick = onProviderMarkupsClick
                    )
                    MoreRow(
                        icon = R.drawable.admin_icon_reports,
                        title = "Reports",
                        subtitle = "Revenue, orders and performance",
                        onClick = onReportsClick
                    )
                    MoreRow(
                        icon = R.drawable.admin_icon_health,
                        title = "System Health",
                        subtitle = "API status, sync and alerts",
                        onClick = onSystemHealthClick
                    )
                }
            }

            item {
                MoreSection(title = "Account") {
                    MoreRow(
                        icon = R.drawable.admin_icon_settings,
                        title = "Profile & Settings",
                        subtitle = "Preferences and admin profile",
                        onClick = onSettingsClick
                    )
                    MoreRow(
                        icon = R.drawable.admin_icon_logout,
                        title = "Logout",
                        subtitle = "Sign out from admin console",
                        danger = true,
                        onClick = onLogoutClick
                    )
                }
            }

            item { Spacer(Modifier.height(10.dp)) }
        }
    }
}

@Composable
private fun MoreHero() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MoreNavy),
        elevation = CardDefaults.cardElevation(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(126.dp)
                .background(Brush.horizontalGradient(listOf(MoreNavy, MoreNavy2)))
                .padding(18.dp)
        ) {
            Column {
                Text(
                    "More",
                    color = Color.White,
                    fontSize = 27.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Admin tools & system controls",
                    color = Color.White.copy(alpha = 0.72f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    "Live admin console",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .background(Color.White.copy(alpha = 0.14f), RoundedCornerShape(18.dp))
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                )
            }
        }
    }
}

@Composable
private fun MoreSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, MoreBorder),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                title,
                color = MoreText,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun MoreRow(
    @DrawableRes icon: Int,
    title: String,
    subtitle: String,
    badge: String? = null,
    badgeColor: Color = MoreBlue,
    danger: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PngMoreIcon(icon, Modifier.size(42.dp))

        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = if (danger) MoreRed else MoreText,
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1
            )
            Text(
                subtitle,
                color = MoreMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (!badge.isNullOrBlank() && badge != "0") {
            Box(
                modifier = Modifier
                    .background(badgeColor, CircleShape)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    badge,
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.width(8.dp))
        }

        Text(
            "›",
            color = MoreMuted,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun PngMoreIcon(@DrawableRes resId: Int, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(resId),
        contentDescription = null,
        modifier = modifier
    )
}

@Composable
private fun MoreBottomNav(
    selected: AdminBottomNavItem,
    onClick: (AdminBottomNavItem) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        color = Color.White,
        shadowElevation = 14.dp,
        border = BorderStroke(1.dp, MoreBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(66.dp)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            MoreBottomItem(Icons.Default.GridView, "Dashboard", selected == AdminBottomNavItem.Dashboard) { onClick(AdminBottomNavItem.Dashboard) }
            MoreBottomItem(Icons.Default.People, "Partners", selected == AdminBottomNavItem.Partners) { onClick(AdminBottomNavItem.Partners) }
            MoreBottomItem(Icons.Default.ShoppingCart, "Orders", selected == AdminBottomNavItem.Orders) { onClick(AdminBottomNavItem.Orders) }
            MoreBottomItem(Icons.Default.CreditCard, "Pricing", selected == AdminBottomNavItem.Pricing) { onClick(AdminBottomNavItem.Pricing) }
            MoreBottomItem(Icons.Default.MoreHoriz, "More", selected == AdminBottomNavItem.More) { onClick(AdminBottomNavItem.More) }
        }
    }
}

@Composable
private fun MoreBottomItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val color = if (selected) MoreBlue else MoreMuted
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
