package im.angry.openeuicc.ui

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import im.angry.openeuicc.ui.compose.theme.Border
import im.angry.openeuicc.ui.compose.theme.Primary
import im.angry.openeuicc.ui.compose.theme.TextSecondary

enum class R2wBottomTab {
    Home,
    Packages,
    Wallet,
    Esims,
    More
}

@Composable
fun R2wBottomNav(
    selected: R2wBottomTab? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    fun open(target: Class<*>) {
        context.startActivity(
            Intent(context, target).apply {
                addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            }
        )
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
        shape = RoundedCornerShape(30.dp),
        border = BorderStroke(1.dp, Border)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(66.dp)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            R2wBottomNavItem(
                icon = Icons.Default.Home,
                text = "Home",
                selected = selected == R2wBottomTab.Home,
                onClick = {
                    if (selected != R2wBottomTab.Home) open(DashboardActivity::class.java)
                }
            )
            R2wBottomNavItem(
                icon = Icons.Default.ViewModule,
                text = "Packages",
                selected = selected == R2wBottomTab.Packages,
                onClick = {
                    if (selected != R2wBottomTab.Packages) open(PackagesActivity::class.java)
                }
            )
            R2wBottomNavItem(
                icon = Icons.Default.AccountBalanceWallet,
                text = "Wallet",
                selected = selected == R2wBottomTab.Wallet,
                onClick = {
                    if (selected != R2wBottomTab.Wallet) open(WalletActivity::class.java)
                }
            )
            R2wBottomNavItem(
                icon = Icons.Default.SimCard,
                text = "eSIMs",
                selected = selected == R2wBottomTab.Esims,
                onClick = {
                    if (selected != R2wBottomTab.Esims) open(MobileEsimsActivity::class.java)
                }
            )
            R2wBottomNavItem(
                icon = Icons.Default.MoreHoriz,
                text = "More",
                selected = selected == R2wBottomTab.More,
                onClick = {
                    if (selected != R2wBottomTab.More) open(MoreActivity::class.java)
                }
            )
        }
    }
}

@Composable
private fun R2wBottomNavItem(
    icon: ImageVector,
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val contentColor = if (selected) Primary else TextSecondary
    val backgroundColor = if (selected) Primary.copy(alpha = 0.10f) else Color.Transparent

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(22.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = text,
            tint = contentColor,
            modifier = Modifier.height(21.dp)
        )
        Text(
            text = text,
            color = contentColor,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            fontSize = 11.sp,
            maxLines = 1
        )
    }
}
