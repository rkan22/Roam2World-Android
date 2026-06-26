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
import im.angry.openeuicc.ui.compose.screens.admin.AdminActivityLogsOverviewScreen
import im.angry.openeuicc.ui.compose.screens.admin.AdminBottomNavItem
import im.angry.openeuicc.ui.compose.theme.R2WTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AdminActivityLogsOverviewActivity : ComponentActivity() {
    private val tokenStore by lazy { AuthTokenStore(this) }
    private val api by lazy { Roam2WorldAuthApi(BuildConfig.ROAM2WORLD_API_BASE_URL) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val status = remember { mutableStateOf("Live") }
            val riskScore = remember { mutableStateOf("0") }

            LaunchedEffect(Unit) {
                val session = withContext(Dispatchers.IO) { tokenStore.getSession() }
                if (session != null) {
                    val result = withContext(Dispatchers.IO) {
                        runCatching { api.mobileAdminDashboardRaw(session) }
                    }

                    result.onSuccess { json ->
                        val system = json
                            .optJSONObject("data")
                            ?.optJSONObject("metrics")
                            ?.optJSONObject("system")

                        status.value = system?.optString("status", "Live") ?: "Live"
                        riskScore.value = (system?.optInt("risk_score", 0) ?: 0).toString()
                    }
                }
            }

            R2WTheme {
                AdminActivityLogsOverviewScreen(
                    systemStatus = status.value,
                    riskScore = riskScore.value,
                    onOpenLogsClick = {
                        startActivity(Intent(this@AdminActivityLogsOverviewActivity, AdminActivityLogsActivity::class.java))
                    },
                    onBottomNavClick = { item ->
                        when (item) {
                            AdminBottomNavItem.Dashboard -> startActivity(Intent(this@AdminActivityLogsOverviewActivity, MobileAdminActivity::class.java))
                            AdminBottomNavItem.Partners -> startActivity(Intent(this@AdminActivityLogsOverviewActivity, AdminPartnersActivity::class.java))
                            AdminBottomNavItem.Orders -> startActivity(Intent(this@AdminActivityLogsOverviewActivity, AdminOrdersOverviewActivity::class.java))
                            AdminBottomNavItem.Pricing -> startActivity(Intent(this@AdminActivityLogsOverviewActivity, AdminPricingOverviewActivity::class.java))
                            AdminBottomNavItem.More -> startActivity(Intent(this@AdminActivityLogsOverviewActivity, AdminMoreActivity::class.java))
                        }
                    }
                )
            }
        }
    }
}
