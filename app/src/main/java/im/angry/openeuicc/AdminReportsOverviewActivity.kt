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
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import im.angry.openeuicc.auth.AuthTokenStore
import im.angry.openeuicc.auth.Roam2WorldAuthApi
import im.angry.openeuicc.common.BuildConfig
import im.angry.openeuicc.ui.compose.saas.R2wActionCard
import im.angry.openeuicc.ui.compose.saas.R2wMetricCard
import im.angry.openeuicc.ui.compose.saas.R2wSaasBottomNav
import im.angry.openeuicc.ui.compose.saas.R2wSaasColors
import im.angry.openeuicc.ui.compose.saas.R2wSaasHeader
import im.angry.openeuicc.ui.compose.saas.R2wSaasNavItem
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
                AdminReportsOverviewSaasScreen(
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
                            R2wSaasNavItem.Dashboard -> startActivity(Intent(this@AdminReportsOverviewActivity, MobileAdminActivity::class.java))
                            R2wSaasNavItem.Partners -> startActivity(Intent(this@AdminReportsOverviewActivity, AdminPartnersActivity::class.java))
                            R2wSaasNavItem.Orders -> startActivity(Intent(this@AdminReportsOverviewActivity, AdminOrdersOverviewActivity::class.java))
                            R2wSaasNavItem.Pricing -> startActivity(Intent(this@AdminReportsOverviewActivity, AdminPricingOverviewActivity::class.java))
                            R2wSaasNavItem.More -> startActivity(Intent(this@AdminReportsOverviewActivity, AdminMoreActivity::class.java))
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun AdminReportsOverviewSaasScreen(
    totalRevenue: String,
    totalOrders: String,
    completedOrders: String,
    processingOrders: String,
    totalPartners: String,
    onOpenReportsClick: () -> Unit,
    onBottomNavClick: (R2wSaasNavItem) -> Unit
) {
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
                    title = "Reports",
                    subtitle = "Revenue, orders and partner performance analytics.",
                    badge = "Live API"
                )
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    R2wMetricCard(
                        modifier = Modifier.weight(1f),
                        title = "Revenue",
                        value = totalRevenue,
                        subtitle = "total sales",
                        icon = Icons.Default.AttachMoney,
                        tint = R2wSaasColors.Primary
                    )

                    R2wMetricCard(
                        modifier = Modifier.weight(1f),
                        title = "Orders",
                        value = totalOrders,
                        subtitle = "$completedOrders completed",
                        icon = Icons.Default.ReceiptLong,
                        tint = R2wSaasColors.Green
                    )
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    R2wMetricCard(
                        modifier = Modifier.weight(1f),
                        title = "Processing",
                        value = processingOrders,
                        subtitle = "active flow",
                        icon = Icons.Default.CheckCircle,
                        tint = R2wSaasColors.Orange
                    )

                    R2wMetricCard(
                        modifier = Modifier.weight(1f),
                        title = "Partners",
                        value = totalPartners,
                        subtitle = "resellers + dealers",
                        icon = Icons.Default.People,
                        tint = R2wSaasColors.Purple
                    )
                }
            }

            item {
                R2wActionCard(
                    title = "View Full Reports",
                    subtitle = "Status, source, country and recent order insights",
                    icon = Icons.Default.Analytics,
                    onClick = onOpenReportsClick,
                    tint = R2wSaasColors.Primary
                )
            }

            item { Spacer(Modifier.height(18.dp)) }
        }
    }
}
