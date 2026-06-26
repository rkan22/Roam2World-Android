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

private val LogsBg = Color(0xFFF6F8FC)
private val LogsNavy = Color(0xFF061A3F)
private val LogsNavy2 = Color(0xFF123EAD)
private val LogsBlue = Color(0xFF1263F1)
private val LogsText = Color(0xFF101828)
private val LogsMuted = Color(0xFF667085)
private val LogsBorder = Color(0xFFE1E8F2)
private val LogsGreen = Color(0xFF16A34A)
private val LogsOrange = Color(0xFFF97316)
private val LogsPurple = Color(0xFF7C3AED)

@Composable
fun AdminActivityLogsOverviewScreen(
    systemStatus: String = "Live",
    riskScore: String = "0",
    onOpenLogsClick: () -> Unit = {},
    onBottomNavClick: (AdminBottomNavItem) -> Unit = {}
) {
    val statusLabel = systemStatus.replace("_", " ").replaceFirstChar { it.uppercase() }
    val risk = riskScore.toIntOrNull() ?: 0
    val riskColor = if (risk > 0) LogsOrange else LogsGreen

    Scaffold(
        containerColor = LogsBg,
        bottomBar = {
            LogsBottomNav(
                selected = AdminBottomNavItem.More,
                onClick = onBottomNavClick
            )
        }
    ) { inner ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(LogsBg)
                .padding(inner)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(Modifier.height(8.dp)) }

            item {
                LogsHero(statusLabel = statusLabel)
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LogsMetricCard(
                        modifier = Modifier.weight(1f),
                        icon = R.drawable.admin_icon_doc,
                        label = "Audit Logs",
                        value = "Live",
                        sub = "tracking enabled",
                        subColor = LogsBlue
                    )
                    LogsMetricCard(
                        modifier = Modifier.weight(1f),
                        icon = R.drawable.admin_icon_shield,
                        label = "Risk Score",
                        value = riskScore,
                        sub = if (risk > 0) "review events" else "low risk",
                        subColor = riskColor
                    )
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LogsMetricCard(
                        modifier = Modifier.weight(1f),
                        icon = R.drawable.admin_icon_health,
                        label = "System",
                        value = statusLabel,
                        sub = "backend status",
                        subColor = riskColor
                    )
                    LogsMetricCard(
                        modifier = Modifier.weight(1f),
                        icon = R.drawable.admin_icon_settings,
                        label = "Admin Actions",
                        value = "On",
                        sub = "auditable",
                        subColor = LogsGreen
                    )
                }
            }

            item {
                LogsSection(title = "Audit Center") {
                    LogsActionRow(
                        icon = R.drawable.admin_icon_doc,
                        title = "Open Activity Logs",
                        subtitle = "Review admin actions, audit events and changes",
                        status = "View",
                        statusColor = LogsBlue,
                        onClick = onOpenLogsClick
                    )
                }
            }

            item {
                LogsSection(title = "Tracked Events") {
                    LogsMiniRow(
                        icon = R.drawable.admin_icon_orders,
                        title = "Order actions",
                        subtitle = "Order updates, fulfilment changes and status events",
                        color = LogsBlue
                    )
                    LogsMiniRow(
                        icon = R.drawable.admin_icon_money,
                        title = "Wallet approvals",
                        subtitle = "Top-up approvals and admin wallet operations",
                        color = LogsOrange
                    )
                    LogsMiniRow(
                        icon = R.drawable.admin_icon_tag,
                        title = "Pricing changes",
                        subtitle = "Provider markup and pricing rule updates",
                        color = LogsPurple
                    )
                    LogsMiniRow(
                        icon = R.drawable.admin_icon_partners,
                        title = "Partner changes",
                        subtitle = "Reseller and dealer account actions",
                        color = LogsGreen
                    )
                }
            }

            item {
                LogsSection(title = "Live Summary") {
                    LogsStatusLine("Audit tracking", "Enabled")
                    LogsStatusLine("System status", statusLabel)
                    LogsStatusLine("Risk score", riskScore)
                    LogsStatusLine("Source", "Admin API")
                }
            }

            item { Spacer(Modifier.height(10.dp)) }
        }
    }
}

@Composable
private fun LogsHero(statusLabel: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = LogsNavy),
        elevation = CardDefaults.cardElevation(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(148.dp)
                .background(Brush.horizontalGradient(listOf(LogsNavy, LogsNavy2)))
                .padding(18.dp)
        ) {
            Column {
                Text(
                    "Activity Logs",
                    color = Color.White,
                    fontSize = 27.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Audit trail & admin activity",
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
                        statusLabel,
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
private fun LogsMetricCard(
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
        border = BorderStroke(1.dp, LogsBorder),
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
                Text(label, color = LogsMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(value, color = LogsText, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                Text(sub, color = subColor, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }
    }
}

@Composable
private fun LogsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, LogsBorder),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, color = LogsText, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun LogsActionRow(
    @DrawableRes icon: Int,
    title: String,
    subtitle: String,
    status: String,
    statusColor: Color,
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
            Text(title, color = LogsText, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
            Text(
                subtitle,
                color = LogsMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Surface(
            color = statusColor.copy(alpha = 0.12f),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text(
                status,
                color = statusColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp)
            )
        }

        Text("  ›", color = LogsMuted, fontSize = 24.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun LogsMiniRow(
    @DrawableRes icon: Int,
    title: String,
    subtitle: String,
    color: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(62.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(painterResource(icon), contentDescription = null, modifier = Modifier.size(40.dp))
        Spacer(Modifier.size(12.dp))

        Column(Modifier.weight(1f)) {
            Text(title, color = LogsText, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
            Text(
                subtitle,
                color = LogsMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Surface(
            color = color.copy(alpha = 0.12f),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text(
                "Tracked",
                color = color,
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
            )
        }
    }
}

@Composable
private fun LogsStatusLine(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(38.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = LogsMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Text(value, color = LogsText, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun LogsBottomNav(
    selected: AdminBottomNavItem,
    onClick: (AdminBottomNavItem) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        color = Color.White,
        shadowElevation = 14.dp,
        border = BorderStroke(1.dp, LogsBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(66.dp)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            LogsBottomItem(Icons.Default.GridView, "Dashboard", selected == AdminBottomNavItem.Dashboard) { onClick(AdminBottomNavItem.Dashboard) }
            LogsBottomItem(Icons.Default.People, "Partners", selected == AdminBottomNavItem.Partners) { onClick(AdminBottomNavItem.Partners) }
            LogsBottomItem(Icons.Default.ShoppingCart, "Orders", selected == AdminBottomNavItem.Orders) { onClick(AdminBottomNavItem.Orders) }
            LogsBottomItem(Icons.Default.CreditCard, "Pricing", selected == AdminBottomNavItem.Pricing) { onClick(AdminBottomNavItem.Pricing) }
            LogsBottomItem(Icons.Default.MoreHoriz, "More", selected == AdminBottomNavItem.More) { onClick(AdminBottomNavItem.More) }
        }
    }
}

@Composable
private fun LogsBottomItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val color = if (selected) LogsBlue else LogsMuted
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
