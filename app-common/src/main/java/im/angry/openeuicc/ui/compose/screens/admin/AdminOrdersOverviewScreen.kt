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
import androidx.compose.material3.LinearProgressIndicator
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

private val OrdersBg = Color(0xFFF6F8FC)
private val OrdersNavy = Color(0xFF061A3F)
private val OrdersNavy2 = Color(0xFF123EAD)
private val OrdersBlue = Color(0xFF1263F1)
private val OrdersText = Color(0xFF101828)
private val OrdersMuted = Color(0xFF667085)
private val OrdersBorder = Color(0xFFE1E8F2)
private val OrdersGreen = Color(0xFF16A34A)
private val OrdersOrange = Color(0xFFF97316)
private val OrdersRed = Color(0xFFEF4444)
private val OrdersPurple = Color(0xFF7C3AED)

@Composable
fun AdminOrdersOverviewScreen(
    totalOrders: String = "0",
    todayOrders: String = "0",
    pendingOrders: String = "0",
    processingOrders: String = "0",
    completedOrders: String = "0",
    cancelledOrders: String = "0",
    onOpenOrdersClick: () -> Unit = {},
    onBottomNavClick: (AdminBottomNavItem) -> Unit = {}
) {
    Scaffold(
        containerColor = OrdersBg,
        bottomBar = {
            OrdersBottomNav(
                selected = AdminBottomNavItem.Orders,
                onClick = onBottomNavClick
            )
        }
    ) { inner ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(OrdersBg)
                .padding(inner)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(Modifier.height(8.dp)) }

            item {
                OrdersHero(totalOrders = totalOrders)
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OrderMetricCard(
                            modifier = Modifier.weight(1f),
                            icon = R.drawable.admin_icon_orders,
                            label = "Total Orders",
                            value = totalOrders,
                            sub = "$todayOrders today",
                            subColor = OrdersBlue
                        )
                        OrderMetricCard(
                            modifier = Modifier.weight(1f),
                            icon = R.drawable.admin_icon_health,
                            label = "Processing",
                            value = processingOrders,
                            sub = "in progress",
                            subColor = OrdersOrange
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OrderMetricCard(
                            modifier = Modifier.weight(1f),
                            icon = R.drawable.admin_icon_doc,
                            label = "Completed",
                            value = completedOrders,
                            sub = "fulfilled",
                            subColor = OrdersGreen
                        )
                        OrderMetricCard(
                            modifier = Modifier.weight(1f),
                            icon = R.drawable.admin_icon_notifications,
                            label = "Pending",
                            value = pendingOrders,
                            sub = "needs action",
                            subColor = OrdersRed
                        )
                    }
                }
            }

            item {
                OrdersSection(title = "Order Pipeline") {
                    PipelineRow(
                        label = "Completed",
                        value = completedOrders,
                        total = totalOrders,
                        color = OrdersGreen
                    )
                    PipelineRow(
                        label = "Processing",
                        value = processingOrders,
                        total = totalOrders,
                        color = OrdersOrange
                    )
                    PipelineRow(
                        label = "Pending",
                        value = pendingOrders,
                        total = totalOrders,
                        color = OrdersBlue
                    )
                    PipelineRow(
                        label = "Cancelled",
                        value = cancelledOrders,
                        total = totalOrders,
                        color = OrdersRed
                    )
                }
            }

            item {
                OrdersSection(title = "Quick Access") {
                    BigActionRow(
                        icon = R.drawable.admin_icon_orders,
                        title = "Open Orders List",
                        subtitle = "View all orders, details and fulfilment status",
                        badge = totalOrders,
                        onClick = onOpenOrdersClick
                    )
                }
            }

            item {
                OrdersSection(title = "Recent Status") {
                    StatusLine("Today", "$todayOrders new orders")
                    StatusLine("Processing", "$processingOrders orders currently processing")
                    StatusLine("Completed", "$completedOrders completed orders")
                    StatusLine("Pending review", "$pendingOrders orders waiting")
                }
            }

            item { Spacer(Modifier.height(10.dp)) }
        }
    }
}

@Composable
private fun OrdersHero(totalOrders: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = OrdersNavy),
        elevation = CardDefaults.cardElevation(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(148.dp)
                .background(Brush.horizontalGradient(listOf(OrdersNavy, OrdersNavy2)))
                .padding(18.dp)
        ) {
            Column {
                Text(
                    "Orders",
                    color = Color.White,
                    fontSize = 27.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Order operations & fulfilment",
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
                        "$totalOrders total orders",
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
private fun OrderMetricCard(
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
        border = BorderStroke(1.dp, OrdersBorder),
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
                Text(label, color = OrdersMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(
                    value,
                    color = OrdersText,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(sub, color = subColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun OrdersSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, OrdersBorder),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, color = OrdersText, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun PipelineRow(
    label: String,
    value: String,
    total: String,
    color: Color
) {
    val current = value.toFloatOrNull() ?: 0f
    val max = total.toFloatOrNull()?.takeIf { it > 0f } ?: 1f
    val progress = (current / max).coerceIn(0f, 1f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                color = OrdersMuted,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Text(
                value,
                color = OrdersText,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(7.dp),
            color = color,
            trackColor = Color(0xFFEAF2FF)
        )
    }
}

@Composable
private fun BigActionRow(
    @DrawableRes icon: Int,
    title: String,
    subtitle: String,
    badge: String,
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
            Text(title, color = OrdersText, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
            Text(subtitle, color = OrdersMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Surface(
            color = Color(0xFFEAF2FF),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text(
                badge,
                color = OrdersBlue,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
            )
        }
        Text("  ›", color = OrdersMuted, fontSize = 24.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun StatusLine(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(38.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = OrdersMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Text(value, color = OrdersText, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun OrdersBottomNav(
    selected: AdminBottomNavItem,
    onClick: (AdminBottomNavItem) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        color = Color.White,
        shadowElevation = 14.dp,
        border = BorderStroke(1.dp, OrdersBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(66.dp)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OrdersBottomItem(Icons.Default.GridView, "Dashboard", selected == AdminBottomNavItem.Dashboard) { onClick(AdminBottomNavItem.Dashboard) }
            OrdersBottomItem(Icons.Default.People, "Partners", selected == AdminBottomNavItem.Partners) { onClick(AdminBottomNavItem.Partners) }
            OrdersBottomItem(Icons.Default.ShoppingCart, "Orders", selected == AdminBottomNavItem.Orders) { onClick(AdminBottomNavItem.Orders) }
            OrdersBottomItem(Icons.Default.CreditCard, "Pricing", selected == AdminBottomNavItem.Pricing) { onClick(AdminBottomNavItem.Pricing) }
            OrdersBottomItem(Icons.Default.MoreHoriz, "More", selected == AdminBottomNavItem.More) { onClick(AdminBottomNavItem.More) }
        }
    }
}

@Composable
private fun OrdersBottomItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val color = if (selected) OrdersBlue else OrdersMuted
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
