package im.angry.openeuicc

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import im.angry.openeuicc.auth.AuthTokenStore
import im.angry.openeuicc.auth.Roam2WorldAuthApi
import im.angry.openeuicc.common.BuildConfig
import im.angry.openeuicc.ui.compose.screens.admin.AdminBottomNavItem
import im.angry.openeuicc.ui.compose.screens.admin.AdminWalletApprovalsOverviewScreen
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
                AdminWalletApprovalsOverviewScreen(
                    resellerPending = resellerPending.value,
                    dealerPending = dealerPending.value,
                    onOpenApprovalsClick = {
                        val intent = Intent(this@AdminWalletApprovalsOverviewActivity, ApprovalsActivity::class.java)
                        intent.putExtra(
                            ApprovalsActivity.EXTRA_APPROVAL_MODE,
                            ApprovalsActivity.MODE_ADMIN_RESELLER_WALLET
                        )
                        startActivity(intent)
                    },
                    onBottomNavClick = { item ->
                        when (item) {
                            AdminBottomNavItem.Dashboard -> startActivity(Intent(this@AdminWalletApprovalsOverviewActivity, MobileAdminActivity::class.java))
                            AdminBottomNavItem.Partners -> startActivity(Intent(this@AdminWalletApprovalsOverviewActivity, AdminPartnersActivity::class.java))
                            AdminBottomNavItem.Orders -> startActivity(Intent(this@AdminWalletApprovalsOverviewActivity, AdminOrdersOverviewActivity::class.java))
                            AdminBottomNavItem.Pricing -> startActivity(Intent(this@AdminWalletApprovalsOverviewActivity, AdminPricingOverviewActivity::class.java))
                            AdminBottomNavItem.More -> startActivity(Intent(this@AdminWalletApprovalsOverviewActivity, AdminMoreActivity::class.java))
                        }
                    }
                )
            }
        }
    }
}
