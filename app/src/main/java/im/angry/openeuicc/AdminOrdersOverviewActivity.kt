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
import im.angry.openeuicc.ui.compose.screens.admin.AdminOrdersOverviewScreen
import im.angry.openeuicc.ui.compose.theme.R2WTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AdminOrdersOverviewActivity : ComponentActivity() {
    private val tokenStore by lazy { AuthTokenStore(this) }
    private val api by lazy { Roam2WorldAuthApi(BuildConfig.ROAM2WORLD_API_BASE_URL) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val total = remember { mutableStateOf("0") }
            val today = remember { mutableStateOf("0") }
            val pending = remember { mutableStateOf("0") }
            val processing = remember { mutableStateOf("0") }
            val completed = remember { mutableStateOf("0") }
            val cancelled = remember { mutableStateOf("0") }

            LaunchedEffect(Unit) {
                val session = withContext(Dispatchers.IO) { tokenStore.getSession() }
                if (session != null) {
                    val result = withContext(Dispatchers.IO) {
                        runCatching { api.mobileAdminDashboardRaw(session) }
                    }

                    result.onSuccess { json ->
                        val orders = json
                            .optJSONObject("data")
                            ?.optJSONObject("metrics")
                            ?.optJSONObject("orders")

                        total.value = (orders?.optInt("total", 0) ?: 0).toString()
                        today.value = (orders?.optInt("today", 0) ?: 0).toString()
                        pending.value = (orders?.optInt("pending", 0) ?: 0).toString()
                        processing.value = (orders?.optInt("processing", 0) ?: 0).toString()
                        completed.value = (orders?.optInt("completed", 0) ?: 0).toString()
                        cancelled.value = (orders?.optInt("cancelled", 0) ?: 0).toString()
                    }
                }
            }

            R2WTheme {
                AdminOrdersOverviewScreen(
                    totalOrders = total.value,
                    todayOrders = today.value,
                    pendingOrders = pending.value,
                    processingOrders = processing.value,
                    completedOrders = completed.value,
                    cancelledOrders = cancelled.value,
                    onOpenOrdersClick = {
                        startActivity(Intent(this@AdminOrdersOverviewActivity, AdminOrdersActivity::class.java))
                    },
                    onBottomNavClick = { item ->
                        when (item) {
                            AdminBottomNavItem.Dashboard -> startActivity(Intent(this@AdminOrdersOverviewActivity, MobileAdminActivity::class.java))
                            AdminBottomNavItem.Partners -> startActivity(Intent(this@AdminOrdersOverviewActivity, AdminPartnersActivity::class.java))
                            AdminBottomNavItem.Orders -> Unit
                            AdminBottomNavItem.Pricing -> startActivity(Intent(this@AdminOrdersOverviewActivity, AdminPricingOverviewActivity::class.java))
                            AdminBottomNavItem.More -> startActivity(Intent(this@AdminOrdersOverviewActivity, AdminMoreActivity::class.java))
                        }
                    }
                )
            }
        }
    }
}
