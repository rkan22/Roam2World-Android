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
import im.angry.openeuicc.ui.compose.screens.admin.AdminNotificationsOverviewScreen
import im.angry.openeuicc.ui.compose.theme.R2WTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AdminNotificationsOverviewActivity : ComponentActivity() {
    private val tokenStore by lazy { AuthTokenStore(this) }
    private val api by lazy { Roam2WorldAuthApi(BuildConfig.ROAM2WORLD_API_BASE_URL) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val unread = remember { mutableStateOf("0") }

            LaunchedEffect(Unit) {
                val session = withContext(Dispatchers.IO) { tokenStore.getSession() }
                if (session != null) {
                    val result = withContext(Dispatchers.IO) {
                        runCatching { api.mobileAdminDashboardRaw(session) }
                    }

                    result.onSuccess { json ->
                        val notifications = json
                            .optJSONObject("data")
                            ?.optJSONObject("metrics")
                            ?.optJSONObject("notifications")

                        unread.value = (notifications?.optInt("unread", 0) ?: 0).toString()
                    }
                }
            }

            R2WTheme {
                AdminNotificationsOverviewScreen(
                    unreadCount = unread.value,
                    onOpenNotificationsClick = {
                        startActivity(Intent(this@AdminNotificationsOverviewActivity, AdminNotificationsActivity::class.java))
                    },
                    onBottomNavClick = { item ->
                        when (item) {
                            AdminBottomNavItem.Dashboard -> startActivity(Intent(this@AdminNotificationsOverviewActivity, MobileAdminActivity::class.java))
                            AdminBottomNavItem.Partners -> startActivity(Intent(this@AdminNotificationsOverviewActivity, AdminPartnersActivity::class.java))
                            AdminBottomNavItem.Orders -> startActivity(Intent(this@AdminNotificationsOverviewActivity, AdminOrdersOverviewActivity::class.java))
                            AdminBottomNavItem.Pricing -> startActivity(Intent(this@AdminNotificationsOverviewActivity, AdminPricingOverviewActivity::class.java))
                            AdminBottomNavItem.More -> startActivity(Intent(this@AdminNotificationsOverviewActivity, AdminMoreActivity::class.java))
                        }
                    }
                )
            }
        }
    }
}
