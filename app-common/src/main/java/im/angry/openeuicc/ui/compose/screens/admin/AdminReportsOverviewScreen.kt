package im.angry.openeuicc.ui.compose.screens.admin

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import im.angry.openeuicc.common.R

private val ReportsBg = Color(0xFFF6F8FC)
private val ReportsNavy = Color(0xFF061A3F)
private val ReportsNavy2 = Color(0xFF123EAD)
private val ReportsBlue = Color(0xFF1263F1)
private val ReportsText = Color(0xFF101828)
private val ReportsMuted = Color(0xFF667085)
private val ReportsBorder = Color(0xFFE1E8F2)
private val ReportsGreen = Color(0xFF16A34A)
private val ReportsOrange = Color(0xFFF97316)
private val ReportsPurple = Color(0xFF7C3AED)

@Composable
fun AdminReportsOverviewScreen(
    totalRevenue: String = "$0.00",
    totalOrders: String = "0",
    completedOrders: String = "0",
    processingOrders: String = "0",
    totalPartners: String = "0",
    onOpenReportsClick: () -> Unit = {},
    onBottomNavClick: (AdminBottomNavItem) -> Unit = {}
) {
    Scaffold(
        containerColor = ReportsBg,
        bottomBar = {
            ReportsBottomNav(
                selected = AdminBottomNavItem.More,
                onClick = onBottomNavClick
            )
        }
    ) { inner ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(ReportsBg)
                .padding(inner)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(Modifier.height(8.dp)) }

            item {
                ReportsHero(totalRevenue = totalRevenue)
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ReportMetricCard(
                        modifier = Modifier.weight(1f),
                        icon = R.drawable.admin_icon_money,
                        label = "Revenue",
                        value = totalRevenue,
                        sub = "live sales",
                        subColor = ReportsGreen
                    )
                    ReportMetricCard(
                        modifier = Modifier.weight(1f),
                        icon = R.drawable.admin_icon_orders,
                        label = "Orders",
                        value = totalOrders,
                        sub = "$completedOrders completed",
                        subColor = ReportsBlue
                    )
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ReportMetricCard(
                        modifier = Modifier.weight(1f),
                        icon = R.drawable.admin_icon_partners,
                        label = "Partners",
                        value = totalPartners,
                        sub = "resellers + dealers",
                        subColor = ReportsPurple
                    )
                    ReportMetricCard(
                        modifier = Modifier.weight(1f),
                        icon = R.drawable.admin_icon_health,
                        label = "Processing",
                        value = processingOrders,
                        sub = "active orders",
                        subColor = ReportsOrange
                    )
                }
            }

            item {
                ReportsSection(title = "Performance Trend") {
                    ReportsLineChart(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(132.dp)
                    )
                }
            }

            item {
                ReportsSection(title = "Report Center") {
                    ReportActionRow(
                        icon = R.drawable.admin_icon_reports,
                        title = "Open Full Reports",
                        subtitle = "View detailed revenue, order and partner reports",
                        status = "View",
                        onClick = onOpenReportsClick
                    )
                }
            }

            item {
                ReportsSection(title = "Live Summary") {
                    ReportStatusLine("Revenue", totalRevenue)
                    ReportStatusLine("Total orders", totalOrders)
                    ReportStatusLine("Completed orders", completedOrders)
                    ReportStatusLine("Processing orders", processingOrders)
                    ReportStatusLine("Total partners", totalPartners)
                }
            }

            item { Spacer(Modifier.height(10.dp)) }
        }
    }
}

@Composable
private fun ReportsHero(totalRevenue: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = ReportsNavy),
        elevation = CardDefaults.cardElevation(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(148.dp)
                .background(Brush.horizontalGradient(listOf(ReportsNavy, ReportsNavy2)))
                .padding(18.dp)
        ) {
            Column {
                Text(
                    "Reports",
                    color = Color.White,
                    fontSize = 27.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Revenue, orders & partner insights",
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
                        "$totalRevenue total sales",
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
private fun ReportMetricCard(
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
        border = BorderStroke(1.dp, ReportsBorder),
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
                Text(label, color = ReportsMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(
                    value,
                    color = ReportsText,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(sub, color = subColor, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }
    }
}

@Composable
private fun ReportsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, ReportsBorder),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, color = ReportsText, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun ReportsLineChart(modifier: Modifier) {
    Canvas(modifier = modifier) {
        val left = 8.dp.toPx()
        val right = size.width - 8.dp.toPx()
        val top = 10.dp.toPx()
        val bottom = size.height - 14.dp.toPx()

        repeat(4) { i ->
            val y = top + (bottom - top) * i / 3f
            drawLine(
                color = Color(0xFFDDE7F5),
                start = Offset(left, y),
                end = Offset(right, y),
                strokeWidth = 1.4.dp.toPx()
            )
        }

        val values = listOf(0.18f, 0.34f, 0.30f, 0.55f, 0.48f, 0.74f, 0.66f, 0.88f)
        val step = (right - left) / (values.size - 1)
        val points = values.mapIndexed { index, value ->
            Offset(left + index * step, bottom - (bottom - top) * value)
        }

        for (i in 0 until points.lastIndex) {
            drawLine(
                color = ReportsBlue,
                start = points[i],
                end = points[i + 1],
                strokeWidth = 4.dp.toPx(),
                cap = StrokeCap.Round
            )
        }

        points.forEach {
            drawCircle(color = ReportsBlue, radius = 4.dp.toPx(), center = it)
            drawCircle(color = Color.White, radius = 2.dp.toPx(), center = it)
        }
    }
}

@Composable
private fun ReportActionRow(
    @DrawableRes icon: Int,
    title: String,
    subtitle: String,
    status: String,
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
            Text(title, color = ReportsText, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
            Text(
                subtitle,
                color = ReportsMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Surface(
            color = Color(0xFFEAF2FF),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text(
                status,
                color = ReportsBlue,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp)
            )
        }

        Text("  ›", color = ReportsMuted, fontSize = 24.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ReportStatusLine(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(38.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = ReportsMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Text(value, color = ReportsText, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun ReportsBottomNav(
    selected: AdminBottomNavItem,
    onClick: (AdminBottomNavItem) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        color = Color.White,
        shadowElevation = 14.dp,
        border = BorderStroke(1.dp, ReportsBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(66.dp)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ReportsBottomItem(Icons.Default.GridView, "Dashboard", selected == AdminBottomNavItem.Dashboard) { onClick(AdminBottomNavItem.Dashboard) }
            ReportsBottomItem(Icons.Default.People, "Partners", selected == AdminBottomNavItem.Partners) { onClick(AdminBottomNavItem.Partners) }
            ReportsBottomItem(Icons.Default.ShoppingCart, "Orders", selected == AdminBottomNavItem.Orders) { onClick(AdminBottomNavItem.Orders) }
            ReportsBottomItem(Icons.Default.CreditCard, "Pricing", selected == AdminBottomNavItem.Pricing) { onClick(AdminBottomNavItem.Pricing) }
            ReportsBottomItem(Icons.Default.MoreHoriz, "More", selected == AdminBottomNavItem.More) { onClick(AdminBottomNavItem.More) }
        }
    }
}

@Composable
private fun ReportsBottomItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val color = if (selected) ReportsBlue else ReportsMuted
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
