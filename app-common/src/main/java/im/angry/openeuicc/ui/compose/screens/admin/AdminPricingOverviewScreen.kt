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

private val PricingBg = Color(0xFFF6F8FC)
private val PricingNavy = Color(0xFF061A3F)
private val PricingNavy2 = Color(0xFF123EAD)
private val PricingBlue = Color(0xFF1263F1)
private val PricingText = Color(0xFF101828)
private val PricingMuted = Color(0xFF667085)
private val PricingBorder = Color(0xFFE1E8F2)
private val PricingGreen = Color(0xFF16A34A)
private val PricingOrange = Color(0xFFF97316)
private val PricingPurple = Color(0xFF7C3AED)

@Composable
fun AdminPricingOverviewScreen(
    onOpenPricingClick: () -> Unit = {},
    onProviderMarkupsClick: () -> Unit = {},
    onReportsClick: () -> Unit = {},
    onBottomNavClick: (AdminBottomNavItem) -> Unit = {}
) {
    Scaffold(
        containerColor = PricingBg,
        bottomBar = {
            PricingBottomNav(
                selected = AdminBottomNavItem.Pricing,
                onClick = onBottomNavClick
            )
        }
    ) { inner ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(PricingBg)
                .padding(inner)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(Modifier.height(8.dp)) }

            item {
                PricingHero()
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PricingMetricCard(
                        modifier = Modifier.weight(1f),
                        icon = R.drawable.admin_icon_tag,
                        label = "Provider Markups",
                        value = "Live",
                        sub = "Pricing rules",
                        subColor = PricingPurple
                    )
                    PricingMetricCard(
                        modifier = Modifier.weight(1f),
                        icon = R.drawable.admin_icon_money,
                        label = "Revenue Pricing",
                        value = "Active",
                        sub = "Synced backend",
                        subColor = PricingGreen
                    )
                }
            }

            item {
                PricingSection(title = "Pricing Management") {
                    PricingActionRow(
                        icon = R.drawable.admin_icon_tag,
                        title = "Open Pricing Manager",
                        subtitle = "Manage packages, markups and provider pricing",
                        status = "Manage",
                        onClick = onOpenPricingClick
                    )
                    PricingActionRow(
                        icon = R.drawable.admin_icon_money,
                        title = "Provider Markups",
                        subtitle = "Adjust provider margin and reseller pricing",
                        status = "Rules",
                        onClick = onProviderMarkupsClick
                    )
                }
            }

            item {
                PricingSection(title = "Insights") {
                    PricingActionRow(
                        icon = R.drawable.admin_icon_reports,
                        title = "Pricing Reports",
                        subtitle = "Review revenue, orders and performance impact",
                        status = "View",
                        onClick = onReportsClick
                    )
                }
            }

            item {
                PricingSection(title = "Status") {
                    PricingStatusLine("Pricing source", "Backend API")
                    PricingStatusLine("Calculation mode", "Live rules")
                    PricingStatusLine("Admin access", "Enabled")
                }
            }

            item { Spacer(Modifier.height(10.dp)) }
        }
    }
}

@Composable
private fun PricingHero() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = PricingNavy),
        elevation = CardDefaults.cardElevation(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(148.dp)
                .background(Brush.horizontalGradient(listOf(PricingNavy, PricingNavy2)))
                .padding(18.dp)
        ) {
            Column {
                Text(
                    "Pricing",
                    color = Color.White,
                    fontSize = 27.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Provider markups & pricing controls",
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
                        "Live pricing console",
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
private fun PricingMetricCard(
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
        border = BorderStroke(1.dp, PricingBorder),
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
                Text(label, color = PricingMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(value, color = PricingText, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                Text(sub, color = subColor, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }
    }
}

@Composable
private fun PricingSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, PricingBorder),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, color = PricingText, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun PricingActionRow(
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
            Text(title, color = PricingText, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
            Text(
                subtitle,
                color = PricingMuted,
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
                color = PricingBlue,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp)
            )
        }

        Text("  ›", color = PricingMuted, fontSize = 24.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PricingStatusLine(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(38.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = PricingMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Text(value, color = PricingText, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun PricingBottomNav(
    selected: AdminBottomNavItem,
    onClick: (AdminBottomNavItem) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        color = Color.White,
        shadowElevation = 14.dp,
        border = BorderStroke(1.dp, PricingBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(66.dp)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            PricingBottomItem(Icons.Default.GridView, "Dashboard", selected == AdminBottomNavItem.Dashboard) { onClick(AdminBottomNavItem.Dashboard) }
            PricingBottomItem(Icons.Default.People, "Partners", selected == AdminBottomNavItem.Partners) { onClick(AdminBottomNavItem.Partners) }
            PricingBottomItem(Icons.Default.ShoppingCart, "Orders", selected == AdminBottomNavItem.Orders) { onClick(AdminBottomNavItem.Orders) }
            PricingBottomItem(Icons.Default.CreditCard, "Pricing", selected == AdminBottomNavItem.Pricing) { onClick(AdminBottomNavItem.Pricing) }
            PricingBottomItem(Icons.Default.MoreHoriz, "More", selected == AdminBottomNavItem.More) { onClick(AdminBottomNavItem.More) }
        }
    }
}

@Composable
private fun PricingBottomItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val color = if (selected) PricingBlue else PricingMuted
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
