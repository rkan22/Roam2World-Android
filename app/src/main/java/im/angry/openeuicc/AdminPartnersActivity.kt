package im.angry.openeuicc

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import im.angry.openeuicc.auth.AuthTokenStore
import im.angry.openeuicc.auth.JwtUtils
import im.angry.openeuicc.auth.Roam2WorldAuthApi
import im.angry.openeuicc.common.BuildConfig
import im.angry.openeuicc.ui.LoginActivity
import im.angry.openeuicc.ui.compose.saas.R2wActionCard
import im.angry.openeuicc.ui.compose.saas.R2wMetricCard
import im.angry.openeuicc.ui.compose.saas.R2wSaasBottomNav
import im.angry.openeuicc.ui.compose.saas.R2wSaasCard
import im.angry.openeuicc.ui.compose.saas.R2wSaasColors
import im.angry.openeuicc.ui.compose.saas.R2wSaasHeader
import im.angry.openeuicc.ui.compose.saas.R2wSaasNavItem
import im.angry.openeuicc.ui.compose.theme.R2WTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
                PartnersOverviewScreen(
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
                            R2wSaasNavItem.Dashboard -> startActivity(Intent(this@AdminPartnersActivity, MobileAdminActivity::class.java))
                            R2wSaasNavItem.Partners -> Unit
                            R2wSaasNavItem.Orders -> startActivity(Intent(this@AdminPartnersActivity, AdminOrdersOverviewActivity::class.java))
                            R2wSaasNavItem.Pricing -> startActivity(Intent(this@AdminPartnersActivity, AdminPricingOverviewActivity::class.java))
                            R2wSaasNavItem.More -> startActivity(Intent(this@AdminPartnersActivity, AdminMoreActivity::class.java))
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun PartnersOverviewScreen(
    resellerTotal: String,
    resellerActive: String,
    resellerSuspended: String,
    dealerTotal: String,
    dealerActive: String,
    dealerSuspended: String,
    onResellersClick: () -> Unit,
    onDealersClick: () -> Unit,
    onBottomNavClick: (R2wSaasNavItem) -> Unit
) {
    val totalPartners = (
        resellerTotal.toIntOrNull().orZero() +
            dealerTotal.toIntOrNull().orZero()
        ).toString()

    Scaffold(
        containerColor = R2wSaasColors.Background,
        bottomBar = {
            R2wSaasBottomNav(
                selected = R2wSaasNavItem.Partners,
                onClick = onBottomNavClick
            )
        }
    ) { inner ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { Spacer(Modifier.height(8.dp)) }

            item {
                R2wSaasHeader(
                    title = "Partner Network",
                    subtitle = "$totalPartners partners across reseller and dealer channels.",
                    badge = "Live API"
                )
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    R2wMetricCard(
                        modifier = Modifier.weight(1f),
                        title = "Resellers",
                        value = resellerTotal,
                        subtitle = "$resellerActive active",
                        icon = Icons.Default.Groups,
                        tint = R2wSaasColors.Primary
                    )

                    R2wMetricCard(
                        modifier = Modifier.weight(1f),
                        title = "Dealers",
                        value = dealerTotal,
                        subtitle = "$dealerActive active",
                        icon = Icons.Default.Storefront,
                        tint = R2wSaasColors.Green
                    )
                }
            }

            item {
                R2wSaasCard {
                    Text(
                        text = "Quick Partner Actions",
                        color = R2wSaasColors.Text,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Black
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Create, manage and monitor partner hierarchy.",
                        color = R2wSaasColors.Muted,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        R2wActionCard(
                            title = "Manage Resellers",
                            subtitle = "$resellerTotal total • $resellerActive active • $resellerSuspended suspended",
                            icon = Icons.Default.People,
                            onClick = onResellersClick,
                            tint = R2wSaasColors.Primary
                        )

                        R2wActionCard(
                            title = "Manage Dealers",
                            subtitle = "$dealerTotal total • $dealerActive active • $dealerSuspended suspended",
                            icon = Icons.Default.Business,
                            onClick = onDealersClick,
                            tint = R2wSaasColors.Green
                        )
                    }
                }
            }

            item {
                PartnerSaasInfoCard()
            }

            item { Spacer(Modifier.height(18.dp)) }
        }
    }
}

@Composable
private fun PartnerSaasInfoCard() {
    R2wSaasCard {
        Text(
            text = "SaaS Partner Controls",
            color = R2wSaasColors.Text,
            fontSize = 17.sp,
            fontWeight = FontWeight.Black
        )

        Spacer(modifier = Modifier.height(12.dp))

        PartnerInfoLine("Wallet credit hierarchy", "Admin → Reseller → Dealer credit flow")
        PartnerInfoLine("Markup management", "Separate reseller and dealer pricing")
        PartnerInfoLine("White-label ready", "Logo, domain and support branding later")
        PartnerInfoLine("API access", "Partner API keys and webhook setup later")
    }
}

@Composable
private fun PartnerInfoLine(
    title: String,
    subtitle: String
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(18.dp),
        color = R2wSaasColors.Background,
        border = BorderStroke(1.dp, R2wSaasColors.Border)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = R2wSaasColors.PrimarySoft
            ) {
                androidx.compose.material3.Icon(
                    imageVector = Icons.Default.CreditCard,
                    contentDescription = null,
                    tint = R2wSaasColors.Primary,
                    modifier = Modifier.padding(9.dp)
                )
            }

            Column {
                Text(
                    text = title,
                    color = R2wSaasColors.Text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = subtitle,
                    color = R2wSaasColors.Muted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

private fun Int?.orZero(): Int = this ?: 0
