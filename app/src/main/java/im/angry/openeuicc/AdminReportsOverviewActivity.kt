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
import im.angry.openeuicc.ui.compose.screens.admin.AdminReportsOverviewScreen
import im.angry.openeuicc.ui.compose.theme.R2WTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

class AdminReportsOverviewActivity : ComponentActivity() {
    private val tokenStore by lazy { AuthTokenStore(this) }
    private val api by lazy { Roam2WorldAuthApi(BuildConfig.ROAM2WORLD_API_BASE_URL) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val revenue = remember { mutableStateOf("$0.00") }
            val totalOrders = remember { mutableStateOf("0") }
            val completedOrders = remember { mutableStateOf("0") }
            val processingOrders = remember { mutableStateOf("0") }
            val totalPartners = remember { mutableStateOf("0") }

            LaunchedEffect(Unit) {
                val session = withContext(Dispatchers.IO) { tokenStore.getSession() }
                if (session != null) {
                    val result = withContext(Dispatchers.IO) {
                        runCatching { api.mobileAdminDashboardRaw(session) }
                    }

                    result.onSuccess { json ->
                        val metrics = json.optJSONObject("data")?.optJSONObject("metrics")
                        val orders = metrics?.optJSONObject("orders")
                        val revenueObj = metrics?.optJSONObject("revenue")
                        val resellers = metrics?.optJSONObject("resellers")
                        val dealers = metrics?.optJSONObject("dealers")

                        val revenueNumber = revenueObj
                            ?.optString("total_sales", "0.00")
                            ?.replace(",", "")
                            ?.replace("$", "")
                            ?.toDoubleOrNull()
                            ?: 0.0

                        revenue.value = "$" + String.format(Locale.US, "%,.2f", revenueNumber)
                        totalOrders.value = (orders?.optInt("total", 0) ?: 0).toString()
                        completedOrders.value = (orders?.optInt("completed", 0) ?: 0).toString()
                        processingOrders.value = (orders?.optInt("processing", 0) ?: 0).toString()

                        val partnerCount = (resellers?.optInt("total", 0) ?: 0) +
                            (dealers?.optInt("total", 0) ?: 0)
                        totalPartners.value = partnerCount.toString()
                    }
                }
            }

            R2WTheme {
                AdminReportsOverviewScreen(
                    totalRevenue = revenue.value,
                    totalOrders = totalOrders.value,
                    completedOrders = completedOrders.value,
                    processingOrders = processingOrders.value,
                    totalPartners = totalPartners.value,
                    onOpenReportsClick = {
                        startActivity(Intent(this@AdminReportsOverviewActivity, AdminReportsActivity::class.java))
                    },
                    onBottomNavClick = { item ->
                        when (item) {
                            AdminBottomNavItem.Dashboard -> startActivity(Intent(this@AdminReportsOverviewActivity, MobileAdminActivity::class.java))
                            AdminBottomNavItem.Partners -> startActivity(Intent(this@AdminReportsOverviewActivity, AdminPartnersActivity::class.java))
                            AdminBottomNavItem.Orders -> startActivity(Intent(this@AdminReportsOverviewActivity, AdminOrdersOverviewActivity::class.java))
                            AdminBottomNavItem.Pricing -> startActivity(Intent(this@AdminReportsOverviewActivity, AdminPricingOverviewActivity::class.java))
                            AdminBottomNavItem.More -> startActivity(Intent(this@AdminReportsOverviewActivity, AdminMoreActivity::class.java))
                        }
                    }
                )
            }
        }
    }
}
