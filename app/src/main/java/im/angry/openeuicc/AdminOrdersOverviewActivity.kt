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
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.ShoppingCart
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
                AdminOrdersOverviewSaasScreen(
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
                            R2wSaasNavItem.Dashboard -> startActivity(Intent(this@AdminOrdersOverviewActivity, MobileAdminActivity::class.java))
                            R2wSaasNavItem.Partners -> startActivity(Intent(this@AdminOrdersOverviewActivity, AdminPartnersActivity::class.java))
                            R2wSaasNavItem.Orders -> Unit
                            R2wSaasNavItem.Pricing -> startActivity(Intent(this@AdminOrdersOverviewActivity, AdminPricingOverviewActivity::class.java))
                            R2wSaasNavItem.More -> startActivity(Intent(this@AdminOrdersOverviewActivity, AdminMoreActivity::class.java))
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun AdminOrdersOverviewSaasScreen(
    totalOrders: String,
    todayOrders: String,
    pendingOrders: String,
    processingOrders: String,
    completedOrders: String,
    cancelledOrders: String,
    onOpenOrdersClick: () -> Unit,
    onBottomNavClick: (R2wSaasNavItem) -> Unit
) {
    Scaffold(
        containerColor = R2wSaasColors.Background,
        bottomBar = {
            R2wSaasBottomNav(
                selected = R2wSaasNavItem.Orders,
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
                    title = "Orders",
                    subtitle = "$todayOrders new today from $totalOrders total orders.",
                    badge = "Live API"
                )
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        R2wMetricCard(
                            modifier = Modifier.weight(1f),
                            title = "Total",
                            value = totalOrders,
                            subtitle = "all orders",
                            icon = Icons.Default.ShoppingCart,
                            tint = R2wSaasColors.Primary
                        )

                        R2wMetricCard(
                            modifier = Modifier.weight(1f),
                            title = "Today",
                            value = todayOrders,
                            subtitle = "new today",
                            icon = Icons.Default.ReceiptLong,
                            tint = R2wSaasColors.Green
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        R2wMetricCard(
                            modifier = Modifier.weight(1f),
                            title = "Pending",
                            value = pendingOrders,
                            subtitle = "waiting",
                            icon = Icons.Default.HourglassTop,
                            tint = R2wSaasColors.Orange
                        )

                        R2wMetricCard(
                            modifier = Modifier.weight(1f),
                            title = "Completed",
                            value = completedOrders,
                            subtitle = "delivered",
                            icon = Icons.Default.CheckCircle,
                            tint = R2wSaasColors.Green
                        )
                    }
                }
            }

            item {
                R2wActionCard(
                    title = "View All Orders",
                    subtitle = "$pendingOrders pending • $processingOrders processing • $cancelledOrders cancelled",
                    icon = Icons.Default.Assessment,
                    onClick = onOpenOrdersClick,
                    tint = R2wSaasColors.Primary
                )
            }

            item { Spacer(Modifier.height(18.dp)) }
        }
    }
}
