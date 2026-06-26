package im.angry.openeuicc

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import im.angry.openeuicc.auth.AuthTokenStore
import im.angry.openeuicc.auth.JwtUtils
import im.angry.openeuicc.auth.Roam2WorldAuthApi
import im.angry.openeuicc.common.BuildConfig
import im.angry.openeuicc.ui.LoginActivity
import im.angry.openeuicc.ui.compose.theme.R2WTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val PartnersBg = Color(0xFFF6F8FC)
private val PartnersNavy = Color(0xFF061A3F)
private val PartnersNavy2 = Color(0xFF123EAD)
private val PartnersBlue = Color(0xFF1263F1)
private val PartnersText = Color(0xFF101828)
private val PartnersMuted = Color(0xFF667085)
private val PartnersBorder = Color(0xFFE1E8F2)
private val PartnersGreen = Color(0xFF16A34A)
private val PartnersRed = Color(0xFFEF4444)

class AdminPartnersActivity : ComponentActivity() {
    private val tokenStore by lazy { AuthTokenStore(this) }
    private val api by lazy { Roam2WorldAuthApi(BuildConfig.ROAM2WORLD_API_BASE_URL) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val resellerTotal = remember { mutableStateOf("0") }
            val resellerActive = remember { mutableStateOf("0") }
            val resellerSuspended = remember { mutableStateOf("0") }
            val dealerTotal = remember { mutableStateOf("0") }
            val dealerActive = remember { mutableStateOf("0") }
            val dealerSuspended = remember { mutableStateOf("0") }

            LaunchedEffect(Unit) {
                val session = withContext(Dispatchers.IO) { tokenStore.getSession() }

                if (session == null || JwtUtils.isExpired(session.accessToken)) {
                    startActivity(
                        Intent(this@AdminPartnersActivity, LoginActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        }
                    )
                    finish()
                    return@LaunchedEffect
                }

                runCatching {
                    withContext(Dispatchers.IO) {
                        api.mobileAdminDashboardRaw(session)
                    }
                }.onSuccess { response ->
                    val data = response.optJSONObject("data")
                    val metrics = data?.optJSONObject("metrics")
                    val resellers = metrics?.optJSONObject("resellers")
                    val dealers = metrics?.optJSONObject("dealers")

                    resellerTotal.value = (resellers?.optInt("total", 0) ?: 0).toString()
                    resellerActive.value = (resellers?.optInt("active", 0) ?: 0).toString()
                    resellerSuspended.value = (resellers?.optInt("suspended", 0) ?: 0).toString()

                    dealerTotal.value = (dealers?.optInt("total", 0) ?: 0).toString()
                    dealerActive.value = (dealers?.optInt("active", 0) ?: 0).toString()
                    dealerSuspended.value = (dealers?.optInt("suspended", 0) ?: 0).toString()
                }
            }

            R2WTheme {
                PartnersOverviewScreenFixed(
                    resellerTotal = resellerTotal.value,
                    resellerActive = resellerActive.value,
                    resellerSuspended = resellerSuspended.value,
                    dealerTotal = dealerTotal.value,
                    dealerActive = dealerActive.value,
                    dealerSuspended = dealerSuspended.value,
                    onResellersClick = {
                        startActivity(Intent(this@AdminPartnersActivity, AdminResellersActivity::class.java))
                    },
                    onDealersClick = {
                        startActivity(Intent(this@AdminPartnersActivity, AdminDealersActivity::class.java))
                    },
                    onBottomNavClick = { tab ->
                        when (tab) {
                            PartnersTab.Dashboard -> startActivity(Intent(this@AdminPartnersActivity, MobileAdminActivity::class.java))
                            PartnersTab.Partners -> Unit
                            PartnersTab.Orders -> startActivity(Intent(this@AdminPartnersActivity, AdminOrdersOverviewActivity::class.java))
                            PartnersTab.Pricing -> startActivity(Intent(this@AdminPartnersActivity, AdminPricingOverviewActivity::class.java))
                            PartnersTab.More -> startActivity(Intent(this@AdminPartnersActivity, AdminMoreActivity::class.java))
                        }
                    }
                )
            }
        }
    }
}

private enum class PartnersTab {
    Dashboard,
    Partners,
    Orders,
    Pricing,
    More
}

@Composable
private fun PartnersOverviewScreenFixed(
    resellerTotal: String,
    resellerActive: String,
    resellerSuspended: String,
    dealerTotal: String,
    dealerActive: String,
    dealerSuspended: String,
    onResellersClick: () -> Unit,
    onDealersClick: () -> Unit,
    onBottomNavClick: (PartnersTab) -> Unit
) {
    Scaffold(
        containerColor = PartnersBg,
        bottomBar = {
            PartnersBottomNav(
                selected = PartnersTab.Partners,
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
                PartnersHero(
                    totalPartners = (
                        resellerTotal.toIntOrNull().orZero() +
                            dealerTotal.toIntOrNull().orZero()
                        ).toString()
                )
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PartnersMetricCard(
                        modifier = Modifier.weight(1f),
                        icon = R.drawable.admin_icon_partners,
                        label = "Resellers",
                        value = resellerTotal,
                        sub = "$resellerActive active",
                        subColor = PartnersGreen
                    )

                    PartnersMetricCard(
                        modifier = Modifier.weight(1f),
                        icon = R.drawable.admin_icon_user,
                        label = "Dealers",
                        value = dealerTotal,
                        sub = "$dealerActive active",
                        subColor = PartnersBlue
                    )
                }
            }

            item {
                PartnersActionCard(
                    icon = R.drawable.admin_icon_partners,
                    title = "Manage Resellers",
                    subtitle = "$resellerTotal total • $resellerActive active • $resellerSuspended suspended",
                    buttonText = "Open Resellers List",
                    accent = PartnersBlue,
                    onClick = onResellersClick
                )
            }

            item {
                PartnersActionCard(
                    icon = R.drawable.admin_icon_user,
                    title = "Manage Dealers",
                    subtitle = "$dealerTotal total • $dealerActive active • $dealerSuspended suspended",
                    buttonText = "Open Dealers List",
                    accent = PartnersGreen,
                    onClick = onDealersClick
                )
            }

            item {
                PartnersInfoCard()
            }

            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}

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
                .height(150.dp)
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
                    "$totalPartners total partner accounts",
                    color = Color.White.copy(alpha = 0.72f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    "Reseller & dealer management",
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
private fun PartnersMetricCard(
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
                Text(sub, color = subColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun PartnersActionCard(
    @DrawableRes icon: Int,
    title: String,
    subtitle: String,
    buttonText: String,
    accent: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, PartnersBorder),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(icon),
                contentDescription = null,
                modifier = Modifier.size(46.dp)
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp)
            ) {
                Text(
                    title,
                    color = PartnersText,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    subtitle,
                    color = PartnersMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    buttonText,
                    color = accent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            Surface(
                color = accent.copy(alpha = 0.12f),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    "Open",
                    color = accent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                )
            }
        }
    }
}

@Composable
private fun PartnersInfoCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, PartnersBorder),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Partner Controls",
                color = PartnersText,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                "Use reseller and dealer lists to review balances, markups, account status and activation controls.",
                color = PartnersMuted,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun PartnersBottomNav(
    selected: PartnersTab,
    onClick: (PartnersTab) -> Unit
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
            PartnersBottomItem(Icons.Default.GridView, "Dashboard", selected == PartnersTab.Dashboard) { onClick(PartnersTab.Dashboard) }
            PartnersBottomItem(Icons.Default.People, "Partners", selected == PartnersTab.Partners) { onClick(PartnersTab.Partners) }
            PartnersBottomItem(Icons.Default.ShoppingCart, "Orders", selected == PartnersTab.Orders) { onClick(PartnersTab.Orders) }
            PartnersBottomItem(Icons.Default.CreditCard, "Pricing", selected == PartnersTab.Pricing) { onClick(PartnersTab.Pricing) }
            PartnersBottomItem(Icons.Default.MoreHoriz, "More", selected == PartnersTab.More) { onClick(PartnersTab.More) }
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

private fun Int?.orZero(): Int = this ?: 0
