package im.angry.openeuicc

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Approval
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import im.angry.openeuicc.auth.AuthTokenStore
import im.angry.openeuicc.auth.Roam2WorldAuthApi
import im.angry.openeuicc.common.BuildConfig
import im.angry.openeuicc.ui.WalletApprovalsActivity
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

class AdminWalletApprovalsOverviewActivity : ComponentActivity() {
    private val tokenStore by lazy { AuthTokenStore(this) }
    private val api by lazy { Roam2WorldAuthApi(BuildConfig.ROAM2WORLD_API_BASE_URL) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val resellerPending = remember { mutableStateOf("0") }
            val dealerPending = remember { mutableStateOf("0") }

            LaunchedEffect(Unit) {
                val session = withContext(Dispatchers.IO) { tokenStore.getSession() }
                if (session != null) {
                    val result = withContext(Dispatchers.IO) {
                        runCatching { api.mobileAdminDashboardRaw(session) }
                    }

                    result.onSuccess { json ->
                        val wallet = json
                            .optJSONObject("data")
                            ?.optJSONObject("metrics")
                            ?.optJSONObject("wallet_requests")

                        resellerPending.value = (wallet?.optInt("reseller_pending", 0) ?: 0).toString()
                        dealerPending.value = (wallet?.optInt("dealer_pending", 0) ?: 0).toString()
                    }
                }
            }

            R2WTheme {
                AdminWalletApprovalsOverviewSaasScreen(
                    resellerPending = resellerPending.value,
                    dealerPending = dealerPending.value,
                    onOpenApprovalsClick = {
                        val intent = Intent(this@AdminWalletApprovalsOverviewActivity, WalletApprovalsActivity::class.java)
                        intent.putExtra(
                            ApprovalsActivity.EXTRA_APPROVAL_MODE,
                            ApprovalsActivity.MODE_ADMIN_RESELLER_WALLET
                        )
                        startActivity(intent)
                    },
                    onBottomNavClick = { item ->
                        when (item) {
                            R2wSaasNavItem.Dashboard -> startActivity(Intent(this@AdminWalletApprovalsOverviewActivity, MobileAdminActivity::class.java))
                            R2wSaasNavItem.Partners -> startActivity(Intent(this@AdminWalletApprovalsOverviewActivity, AdminPartnersActivity::class.java))
                            R2wSaasNavItem.Orders -> startActivity(Intent(this@AdminWalletApprovalsOverviewActivity, AdminOrdersOverviewActivity::class.java))
                            R2wSaasNavItem.Pricing -> startActivity(Intent(this@AdminWalletApprovalsOverviewActivity, AdminPricingOverviewActivity::class.java))
                            R2wSaasNavItem.More -> startActivity(Intent(this@AdminWalletApprovalsOverviewActivity, AdminMoreActivity::class.java))
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun AdminWalletApprovalsOverviewSaasScreen(
    resellerPending: String,
    dealerPending: String,
    onOpenApprovalsClick: () -> Unit,
    onBottomNavClick: (R2wSaasNavItem) -> Unit
) {
    val totalPending = (
        resellerPending.toIntOrNull().orZero() +
            dealerPending.toIntOrNull().orZero()
        ).toString()

    Scaffold(
        containerColor = R2wSaasColors.Background,
        bottomBar = {
            R2wSaasBottomNav(
                selected = R2wSaasNavItem.More,
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
                    title = "Wallet Approvals",
                    subtitle = "$totalPending pending top-up requests need admin review.",
                    badge = "Live API"
                )
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    R2wMetricCard(
                        modifier = Modifier.weight(1f),
                        title = "Reseller",
                        value = resellerPending,
                        subtitle = "pending requests",
                        icon = Icons.Default.People,
                        tint = R2wSaasColors.Primary
                    )

                    R2wMetricCard(
                        modifier = Modifier.weight(1f),
                        title = "Dealer",
                        value = dealerPending,
                        subtitle = "pending requests",
                        icon = Icons.Default.CreditCard,
                        tint = R2wSaasColors.Orange
                    )
                }
            }

            item {
                R2wActionCard(
                    title = "Open Approval Queue",
                    subtitle = "Approve or reject reseller and dealer wallet top-ups",
                    icon = Icons.Default.Approval,
                    onClick = onOpenApprovalsClick,
                    tint = R2wSaasColors.Green
                )
            }

            item {
                R2wSaasCard {
                    androidx.compose.material3.Text(
                        text = "Approval Flow",
                        color = R2wSaasColors.Text,
                        fontSize = 17.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Black
                    )

                    Spacer(Modifier.height(10.dp))

                    androidx.compose.material3.Text(
                        text = "Review reseller and dealer top-up requests with approval history and audit trace.",
                        color = R2wSaasColors.Muted,
                        fontSize = 13.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                        lineHeight = 19.sp
                    )
                }
            }

            item { Spacer(Modifier.height(18.dp)) }
        }
    }
}

private fun Int?.orZero(): Int = this ?: 0
