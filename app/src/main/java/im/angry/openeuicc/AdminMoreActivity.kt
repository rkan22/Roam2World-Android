package im.angry.openeuicc

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import im.angry.openeuicc.auth.AuthTokenStore
import im.angry.openeuicc.auth.Roam2WorldAuthApi
import im.angry.openeuicc.common.BuildConfig
import im.angry.openeuicc.ui.compose.screens.admin.AdminBottomNavItem
import im.angry.openeuicc.ui.compose.screens.admin.AdminMoreScreen
import im.angry.openeuicc.ui.compose.theme.R2WTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AdminMoreActivity : ComponentActivity() {
    private val tokenStore by lazy { AuthTokenStore(this) }
    private val api by lazy { Roam2WorldAuthApi(BuildConfig.ROAM2WORLD_API_BASE_URL) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val unreadNotifications = remember { mutableStateOf("0") }
            val pendingWallet = remember { mutableStateOf("0") }
            val supportTickets = remember { mutableStateOf("0") }

            LaunchedEffect(Unit) {
                val session = withContext(Dispatchers.IO) { tokenStore.getSession() }
                if (session != null) {
                    val result = withContext(Dispatchers.IO) {
                        runCatching { api.mobileAdminDashboardRaw(session) }
                    }

                    result.onSuccess { json ->
                        val metrics = json.optJSONObject("data")?.optJSONObject("metrics")
                        val wallet = metrics?.optJSONObject("wallet_requests")
                        val notifications = metrics?.optJSONObject("notifications")

                        unreadNotifications.value = (notifications?.optInt("unread", 0) ?: 0).toString()
                        pendingWallet.value = (
                            (wallet?.optInt("reseller_pending", 0) ?: 0) +
                                (wallet?.optInt("dealer_pending", 0) ?: 0)
                            ).toString()

                        supportTickets.value = metrics
                            ?.optJSONObject("support")
                            ?.optInt("open", 0)
                            ?.toString()
                            ?: "0"
                    }
                }
            }

            R2WTheme {
                AdminMoreScreen(
                    unreadNotifications = unreadNotifications.value,
                    pendingWallet = pendingWallet.value,
                    supportTickets = supportTickets.value,
                    onNotificationsClick = {
                        startActivity(Intent(this@AdminMoreActivity, AdminNotificationsOverviewActivity::class.java))
                    },
                    onActivityLogsClick = {
                        startActivity(Intent(this@AdminMoreActivity, AdminActivityLogsOverviewActivity::class.java))
                    },
                    onSupportTicketsClick = {
                        startActivity(Intent(this@AdminMoreActivity, AdminSupportTicketsOverviewActivity::class.java))
                    },
                    onWalletApprovalsClick = {
                        startActivity(Intent(this@AdminMoreActivity, AdminWalletApprovalsOverviewActivity::class.java))
                    },
                    onProviderMarkupsClick = {
                        startActivity(Intent(this@AdminMoreActivity, AdminPricingActivity::class.java))
                    },
                    onReportsClick = {
                        startActivity(Intent(this@AdminMoreActivity, AdminReportsOverviewActivity::class.java))
                    },
                    onSystemHealthClick = {
                        startActivity(Intent(this@AdminMoreActivity, AdminSystemHealthOverviewActivity::class.java))
                    },
                    onSettingsClick = {
                        startActivity(Intent(this@AdminMoreActivity, AdminProfileSettingsActivity::class.java))
                    },
                    onLogoutClick = {
                        Toast.makeText(this@AdminMoreActivity, "Logout from main account screen", Toast.LENGTH_SHORT).show()
                    },
                    onBottomNavClick = { item ->
                        when (item) {
                            AdminBottomNavItem.Dashboard -> startActivity(Intent(this@AdminMoreActivity, MobileAdminActivity::class.java))
                            AdminBottomNavItem.Partners -> startActivity(Intent(this@AdminMoreActivity, AdminPartnersActivity::class.java))
                            AdminBottomNavItem.Orders -> startActivity(Intent(this@AdminMoreActivity, AdminOrdersOverviewActivity::class.java))
                            AdminBottomNavItem.Pricing -> startActivity(Intent(this@AdminMoreActivity, AdminPricingOverviewActivity::class.java))
                            AdminBottomNavItem.More -> Unit
                        }
                    }
                )
            }
        }
    }
}
