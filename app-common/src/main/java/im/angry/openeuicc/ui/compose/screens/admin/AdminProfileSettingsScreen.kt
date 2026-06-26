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
import androidx.compose.material3.Switch
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

private val SettingsBg = Color(0xFFF6F8FC)
private val SettingsNavy = Color(0xFF061A3F)
private val SettingsNavy2 = Color(0xFF123EAD)
private val SettingsBlue = Color(0xFF1263F1)
private val SettingsText = Color(0xFF101828)
private val SettingsMuted = Color(0xFF667085)
private val SettingsBorder = Color(0xFFE1E8F2)
private val SettingsGreen = Color(0xFF16A34A)
private val SettingsRed = Color(0xFFEF4444)

@Composable
fun AdminProfileSettingsScreen(
    role: String = "Admin",
    notificationsEnabled: Boolean = true,
    onNotificationsToggle: (Boolean) -> Unit = {},
    onLogoutClick: () -> Unit = {},
    onBottomNavClick: (AdminBottomNavItem) -> Unit = {}
) {
    Scaffold(
        containerColor = SettingsBg,
        bottomBar = {
            SettingsBottomNav(
                selected = AdminBottomNavItem.More,
                onClick = onBottomNavClick
            )
        }
    ) { inner ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(SettingsBg)
                .padding(inner)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(Modifier.height(8.dp)) }

            item {
                SettingsHero(role = role)
            }

            item {
                SettingsSection(title = "Profile") {
                    ProfileRow(
                        icon = R.drawable.admin_icon_user,
                        title = "Admin Account",
                        subtitle = "Role: ${role.ifBlank { "Admin" }}",
                        status = "Active"
                    )
                    ProfileRow(
                        icon = R.drawable.admin_icon_shield,
                        title = "Access Level",
                        subtitle = "Mobile admin console",
                        status = "Verified"
                    )
                }
            }

            item {
                SettingsSection(title = "Preferences") {
                    SettingsSwitchRow(
                        icon = R.drawable.admin_icon_notifications,
                        title = "Push Notifications",
                        subtitle = "Receive admin alerts and updates",
                        checked = notificationsEnabled,
                        onCheckedChange = onNotificationsToggle
                    )
                    ProfileRow(
                        icon = R.drawable.admin_icon_settings,
                        title = "Appearance",
                        subtitle = "Clean dashboard theme",
                        status = "Default"
                    )
                }
            }

            item {
                SettingsSection(title = "Security") {
                    ProfileRow(
                        icon = R.drawable.admin_icon_doc,
                        title = "Activity Tracking",
                        subtitle = "Admin actions are audited",
                        status = "Enabled"
                    )
                    LogoutRow(
                        icon = R.drawable.admin_icon_logout,
                        title = "Logout",
                        subtitle = "Sign out from admin console",
                        onClick = onLogoutClick
                    )
                }
            }

            item { Spacer(Modifier.height(10.dp)) }
        }
    }
}

@Composable
private fun SettingsHero(role: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = SettingsNavy),
        elevation = CardDefaults.cardElevation(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .background(Brush.horizontalGradient(listOf(SettingsNavy, SettingsNavy2)))
                .padding(18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .background(Color.White.copy(alpha = 0.14f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(R.drawable.admin_icon_user),
                        contentDescription = null,
                        modifier = Modifier.size(42.dp)
                    )
                }

                Spacer(Modifier.size(14.dp))

                Column(Modifier.weight(1f)) {
                    Text(
                        "Profile & Settings",
                        color = Color.White,
                        fontSize = 23.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Admin preferences and account controls",
                        color = Color.White.copy(alpha = 0.72f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        role.ifBlank { "Admin" },
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .background(Color.White.copy(alpha = 0.14f), RoundedCornerShape(18.dp))
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, SettingsBorder),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, color = SettingsText, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun ProfileRow(
    @DrawableRes icon: Int,
    title: String,
    subtitle: String,
    status: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(painterResource(icon), contentDescription = null, modifier = Modifier.size(42.dp))
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = SettingsText, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
            Text(
                subtitle,
                color = SettingsMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Surface(
            color = SettingsBlue.copy(alpha = 0.10f),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text(
                status,
                color = SettingsBlue,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
            )
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    @DrawableRes icon: Int,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(68.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(painterResource(icon), contentDescription = null, modifier = Modifier.size(42.dp))
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = SettingsText, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
            Text(
                subtitle,
                color = SettingsMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun LogoutRow(
    @DrawableRes icon: Int,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(painterResource(icon), contentDescription = null, modifier = Modifier.size(42.dp))
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = SettingsRed, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
            Text(
                subtitle,
                color = SettingsMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text("›", color = SettingsMuted, fontSize = 26.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SettingsBottomNav(
    selected: AdminBottomNavItem,
    onClick: (AdminBottomNavItem) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        color = Color.White,
        shadowElevation = 14.dp,
        border = BorderStroke(1.dp, SettingsBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(66.dp)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SettingsBottomItem(Icons.Default.GridView, "Dashboard", selected == AdminBottomNavItem.Dashboard) { onClick(AdminBottomNavItem.Dashboard) }
            SettingsBottomItem(Icons.Default.People, "Partners", selected == AdminBottomNavItem.Partners) { onClick(AdminBottomNavItem.Partners) }
            SettingsBottomItem(Icons.Default.ShoppingCart, "Orders", selected == AdminBottomNavItem.Orders) { onClick(AdminBottomNavItem.Orders) }
            SettingsBottomItem(Icons.Default.CreditCard, "Pricing", selected == AdminBottomNavItem.Pricing) { onClick(AdminBottomNavItem.Pricing) }
            SettingsBottomItem(Icons.Default.MoreHoriz, "More", selected == AdminBottomNavItem.More) { onClick(AdminBottomNavItem.More) }
        }
    }
}

@Composable
private fun SettingsBottomItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val color = if (selected) SettingsBlue else SettingsMuted
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
