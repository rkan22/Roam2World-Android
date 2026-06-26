package im.angry.openeuicc.ui.compose.screens.admin

import androidx.annotation.DrawableRes
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

private val PartnersBg = Color(0xFFF6F8FC)
private val PartnersNavy = Color(0xFF061A3F)
private val PartnersNavy2 = Color(0xFF123EAD)
private val PartnersBlue = Color(0xFF1263F1)
private val PartnersText = Color(0xFF101828)
private val PartnersMuted = Color(0xFF667085)
private val PartnersBorder = Color(0xFFE1E8F2)
private val PartnersGreen = Color(0xFF16A34A)
private val PartnersOrange = Color(0xFFF97316)

@Composable
fun AdminPartnersScreen(
    resellerTotal: String = "0",
    resellerActive: String = "0",
    resellerSuspended: String = "0",
    dealerTotal: String = "0",
    dealerActive: String = "0",
    dealerSuspended: String = "0",
    onResellersClick: () -> Unit = {},
    onDealersClick: () -> Unit = {},
    onBottomNavClick: (AdminBottomNavItem) -> Unit = {}
) {
    val totalPartners = (
        resellerTotal.toIntOrNull().orEmptyInt() +
            dealerTotal.toIntOrNull().orEmptyInt()
        ).toString()

    Scaffold(
        containerColor = PartnersBg,
        bottomBar = {
            PartnersBottomNav(
                selected = AdminBottomNavItem.Partners,
                onClick = onBottomNavClick
            )
        }
    ) { inner ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(PartnersBg)
                .padding(inner)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(Modifier.height(8.dp)) }

            item {
                PartnersHero(totalPartners = totalPartners)
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PartnerMetricCard(
                        modifier = Modifier.weight(1f),
                        icon = R.drawable.admin_icon_partners,
                        label = "Resellers",
                        value = resellerTotal,
                        sub = "$resellerActive active"
                    )
                    PartnerMetricCard(
                        modifier = Modifier.weight(1f),
                        icon = R.drawable.admin_icon_user,
                        label = "Dealers",
                        value = dealerTotal,
                        sub = "$dealerActive active"
                    )
                }
            }

            item {
                PartnersSection(title = "Partner Management") {
                    PartnerLargeRow(
                        icon = R.drawable.admin_icon_partners,
                        title = "Resellers",
                        subtitle = "$resellerActive active · $resellerSuspended suspended",
                        status = resellerTotal,
                        onClick = onResellersClick
                    )
                    PartnerLargeRow(
                        icon = R.drawable.admin_icon_user,
                        title = "Dealers",
                        subtitle = "$dealerActive active · $dealerSuspended suspended",
                        status = dealerTotal,
                        onClick = onDealersClick
                    )
                }
            }

            item {
                PartnersSection(title = "Operational Summary") {
                    SummaryLine("Total partners", totalPartners)
                    SummaryLine("Active partners", (resellerActive.toIntOrNull().orEmptyInt() + dealerActive.toIntOrNull().orEmptyInt()).toString())
                    SummaryLine("Suspended partners", (resellerSuspended.toIntOrNull().orEmptyInt() + dealerSuspended.toIntOrNull().orEmptyInt()).toString())
                }
            }

            item { Spacer(Modifier.height(10.dp)) }
        }
    }
}

private fun Int?.orEmptyInt(): Int = this ?: 0

@Composable
private fun PartnersHero(totalPartners: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = PartnersNavy),
        elevation = CardDefaults.cardElevation(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(148.dp)
                .background(Brush.horizontalGradient(listOf(PartnersNavy, PartnersNavy2)))
                .padding(18.dp)
        ) {
            Column {
                Text(
                    "Partners",
                    color = Color.White,
                    fontSize = 27.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Resellers & dealers management",
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
                        "$totalPartners total partners",
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
private fun PartnerMetricCard(
    modifier: Modifier,
    @DrawableRes icon: Int,
    label: String,
    value: String,
    sub: String
) {
    Card(
        modifier = modifier.height(120.dp),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, PartnersBorder),
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
                Text(label, color = PartnersMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(value, color = PartnersText, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                Text(sub, color = PartnersGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun PartnersSection(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, PartnersBorder),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, color = PartnersText, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun PartnerLargeRow(
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
            Text(title, color = PartnersText, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
            Text(subtitle, color = PartnersMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Surface(
            color = Color(0xFFEAF2FF),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text(
                status,
                color = PartnersBlue,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
            )
        }
        Text("  ›", color = PartnersMuted, fontSize = 24.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SummaryLine(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(38.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = PartnersMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Text(value, color = PartnersText, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun PartnersBottomNav(
    selected: AdminBottomNavItem,
    onClick: (AdminBottomNavItem) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        color = Color.White,
        shadowElevation = 14.dp,
        border = BorderStroke(1.dp, PartnersBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(66.dp)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            PartnersBottomItem(Icons.Default.GridView, "Dashboard", selected == AdminBottomNavItem.Dashboard) { onClick(AdminBottomNavItem.Dashboard) }
            PartnersBottomItem(Icons.Default.People, "Partners", selected == AdminBottomNavItem.Partners) { onClick(AdminBottomNavItem.Partners) }
            PartnersBottomItem(Icons.Default.ShoppingCart, "Orders", selected == AdminBottomNavItem.Orders) { onClick(AdminBottomNavItem.Orders) }
            PartnersBottomItem(Icons.Default.CreditCard, "Pricing", selected == AdminBottomNavItem.Pricing) { onClick(AdminBottomNavItem.Pricing) }
            PartnersBottomItem(Icons.Default.MoreHoriz, "More", selected == AdminBottomNavItem.More) { onClick(AdminBottomNavItem.More) }
        }
    }
}

@Composable
private fun PartnersBottomItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val color = if (selected) PartnersBlue else PartnersMuted
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
