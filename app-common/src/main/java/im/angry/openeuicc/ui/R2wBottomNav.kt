package im.angry.openeuicc.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(24.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Border)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            R2wBottomNavText(
                text = "Home",
                selected = selected == R2wBottomTab.Home,
                onClick = {
                    if (selected != R2wBottomTab.Home) open(DashboardActivity::class.java)
                }
            )
            R2wBottomNavText(
                text = "Packages",
                selected = selected == R2wBottomTab.Packages,
                onClick = {
                    if (selected != R2wBottomTab.Packages) open(PackagesActivity::class.java)
                }
            )
            R2wBottomNavText(
                text = "Wallet",
                selected = selected == R2wBottomTab.Wallet,
                onClick = {
                    if (selected != R2wBottomTab.Wallet) open(WalletActivity::class.java)
                }
            )
            R2wBottomNavText(
                text = "eSIMs",
                selected = selected == R2wBottomTab.Esims,
                onClick = {
                    if (selected != R2wBottomTab.Esims) open(MobileEsimsActivity::class.java)
                }
            )
            R2wBottomNavText(
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
private fun R2wBottomNavText(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Text(
        text = text,
        color = if (selected) Color.White else TextSecondary,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
        fontSize = 12.sp,
        modifier = Modifier
            .background(
                color = if (selected) Primary else Color.Transparent,
                shape = RoundedCornerShape(999.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    )
}
