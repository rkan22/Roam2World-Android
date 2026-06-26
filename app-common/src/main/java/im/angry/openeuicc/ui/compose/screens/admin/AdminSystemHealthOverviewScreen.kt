package im.angry.openeuicc.ui.compose.screens.admin

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

private val Bg = Color(0xFFF6F8FC)
private val Navy = Color(0xFF061A3F)
private val Navy2 = Color(0xFF123EAD)
private val Blue = Color(0xFF1263F1)
private val TextDark = Color(0xFF101828)
private val Muted = Color(0xFF667085)
private val Border = Color(0xFFE1E8F2)
private val Green = Color(0xFF16A34A)
private val Orange = Color(0xFFF97316)
private val Red = Color(0xFFEF4444)

@Composable
fun AdminSystemHealthOverviewScreen(
    status: String = "unknown",
    riskScore: String = "0",
    unreadAlerts: String = "0",
    onOpenSystemHealthClick: () -> Unit = {},
    onBottomNavClick: (AdminBottomNavItem) -> Unit = {}
) {
    val risk = riskScore.toIntOrNull() ?: 0
    val cleanStatus = status.replace("_", " ").replaceFirstChar { it.uppercase() }
    val healthColor = if (risk > 0 || status.contains("attention", ignoreCase = true)) Orange else Green

    Scaffold(
        containerColor = Bg,
        bottomBar = {
            SystemHealthBottomNav(
                selected = AdminBottomNavItem.More,
                onClick = onBottomNavClick
            )
        }
    ) { inner ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Bg)
                .padding(inner)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(Modifier.height(8.dp)) }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = Navy),
                    elevation = CardDefaults.cardElevation(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(148.dp)
                            .background(Brush.horizontalGradient(listOf(Navy, Navy2)))
                            .padding(18.dp)
                    ) {
                        Column {
                            Text(
                                "System Health",
                                color = Color.White,
                                fontSize = 27.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Platform status & diagnostics",
                                color = Color.White.copy(alpha = 0.72f),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(16.dp))
                            Surface(
                                color = healthColor.copy(alpha = 0.25f),
                                shape = RoundedCornerShape(18.dp)
                            ) {
                                Text(
                                    cleanStatus,
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

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HealthMetric(
                        modifier = Modifier.weight(1f),
                        icon = R.drawable.admin_icon_health,
                        label = "Status",
                        value = cleanStatus,
                        sub = "live backend",
                        subColor = healthColor
                    )
                    HealthMetric(
                        modifier = Modifier.weight(1f),
                        icon = R.drawable.admin_icon_shield,
                        label = "Risk Score",
                        value = riskScore,
                        sub = if (risk > 0) "review needed" else "low risk",
                        subColor = healthColor
                    )
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HealthMetric(
                        modifier = Modifier.weight(1f),
                        icon = R.drawable.admin_icon_notifications,
                        label = "Alerts",
                        value = unreadAlerts,
                        sub = if (unreadAlerts == "0") "clear" else "unread",
                        subColor = if (unreadAlerts == "0") Green else Red
                    )
                    HealthMetric(
                        modifier = Modifier.weight(1f),
                        icon = R.drawable.admin_icon_doc,
                        label = "Audit",
                        value = "Live",
                        sub = "tracking enabled",
                        subColor = Blue
                    )
                }
            }

            item {
                HealthCard("System Overview") {
                    Text("Risk level", color = Muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { (risk / 10f).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp),
                        color = healthColor,
                        trackColor = Color(0xFFEAF2FF)
                    )
                    Spacer(Modifier.height(14.dp))
                    StatusLine("Backend status", cleanStatus)
                    StatusLine("Risk score", riskScore)
                    StatusLine("Unread alerts", unreadAlerts)
                }
            }

            item {
                HealthCard("Diagnostics") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(72.dp)
                            .clickable(onClick = onOpenSystemHealthClick),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            painter = painterResource(R.drawable.admin_icon_health),
                            contentDescription = null,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(Modifier.size(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Open System Health Details",
                                color = TextDark,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1
                            )
                            Text(
                                "View services, providers and diagnostics",
                                color = Muted,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text("›", color = Muted, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            item { Spacer(Modifier.height(10.dp)) }
        }
    }
}

@Composable
private fun HealthMetric(
    modifier: Modifier,
    icon: Int,
    label: String,
    value: String,
    sub: String,
    subColor: Color
) {
    Card(
        modifier = modifier.height(120.dp),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Border),
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
                Text(label, color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(
                    value,
                    color = TextDark,
                    fontSize = 18.sp,
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
private fun HealthCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Border),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, color = TextDark, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(10.dp))
            content()
        }
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
        Text(label, color = Muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Text(value, color = TextDark, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun SystemHealthBottomNav(
    selected: AdminBottomNavItem,
    onClick: (AdminBottomNavItem) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        color = Color.White,
        shadowElevation = 14.dp,
        border = BorderStroke(1.dp, Border)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(66.dp)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BottomItem(Icons.Default.GridView, "Dashboard", selected == AdminBottomNavItem.Dashboard) { onClick(AdminBottomNavItem.Dashboard) }
            BottomItem(Icons.Default.People, "Partners", selected == AdminBottomNavItem.Partners) { onClick(AdminBottomNavItem.Partners) }
            BottomItem(Icons.Default.ShoppingCart, "Orders", selected == AdminBottomNavItem.Orders) { onClick(AdminBottomNavItem.Orders) }
            BottomItem(Icons.Default.CreditCard, "Pricing", selected == AdminBottomNavItem.Pricing) { onClick(AdminBottomNavItem.Pricing) }
            BottomItem(Icons.Default.MoreHoriz, "More", selected == AdminBottomNavItem.More) { onClick(AdminBottomNavItem.More) }
        }
    }
}

@Composable
private fun BottomItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val color = if (selected) Blue else Muted
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
