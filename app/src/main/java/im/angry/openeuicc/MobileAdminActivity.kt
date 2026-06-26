package im.angry.openeuicc

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import im.angry.openeuicc.auth.AuthTokenStore
import im.angry.openeuicc.auth.JwtUtils
import im.angry.openeuicc.auth.Roam2WorldAuthApi
import im.angry.openeuicc.common.BuildConfig
import im.angry.openeuicc.ui.LoginActivity
import im.angry.openeuicc.ui.compose.screens.admin.AdminBottomNavItem
import im.angry.openeuicc.ui.compose.screens.admin.AdminDashboardScreen
import im.angry.openeuicc.ui.compose.theme.R2WTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

class MobileAdminActivity : ComponentActivity() {
    private val tokenStore by lazy { AuthTokenStore(this) }
    private val api by lazy { Roam2WorldAuthApi(BuildConfig.ROAM2WORLD_API_BASE_URL) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val revenue = remember { mutableStateOf("Loading...") }
            val orders = remember { mutableStateOf("...") }
            val partners = remember { mutableStateOf("...") }
            val pending = remember { mutableStateOf("...") }

            LaunchedEffect(Unit) {
                val session = withContext(Dispatchers.IO) {
                    tokenStore.getSession()
                }

                if (session == null || JwtUtils.isExpired(session.accessToken)) {
                    redirectToLogin()
                    return@LaunchedEffect
                }

                val role = session.role?.trim()?.lowercase().orEmpty()
                val isAdmin = role == "admin" || role == "superadmin" || role == "staff"
                if (!isAdmin) {
                    Toast.makeText(
                        this@MobileAdminActivity,
                        "Admin access required",
                        Toast.LENGTH_LONG
                    ).show()
                    redirectToLogin()
                    return@LaunchedEffect
                }

                val result = withContext(Dispatchers.IO) {
                    runCatching { api.mobileAdminDashboardRaw(session) }
                }

                result.onSuccess { json ->
                    Log.d("MobileAdminActivity", "mobile admin dashboard json=$json")

                    val data = json.optJSONObject("data")
                    val metrics = data?.optJSONObject("metrics")
                    val revenueObj = metrics?.optJSONObject("revenue")
                    val ordersObj = metrics?.optJSONObject("orders")
                    val resellersObj = metrics?.optJSONObject("resellers")
                    val dealersObj = metrics?.optJSONObject("dealers")
                    val walletObj = metrics?.optJSONObject("wallet_requests")
                    val notificationsObj = metrics?.optJSONObject("notifications")

                    val revenueRaw = revenueObj?.optString("total_sales")
                        ?: metrics?.optString("total_sales")
                        ?: data?.optString("total_sales")
                        ?: "0.00"

                    val revenueNumber = revenueRaw
                        .replace(",", "")
                        .replace("$", "")
                        .replace("USD", "")
                        .trim()
                        .toDoubleOrNull()

                    val currency = revenueObj?.optString("currency", "USD") ?: "USD"
                    revenue.value = if (revenueNumber != null) {
                        if (currency.equals("USD", ignoreCase = true)) {
                            "$" + String.format(Locale.US, "%,.2f", revenueNumber)
                        } else {
                            String.format("%,.2f %s", revenueNumber, currency)
                        }
                    } else {
                        revenueRaw.ifBlank { "$0.00" }
                    }

                    val totalOrders = ordersObj?.optInt("total", 0)
                        ?: metrics?.optInt("total_orders", 0)
                        ?: 0
                    orders.value = totalOrders.toString()

                    val resellerTotal = resellersObj?.optInt("total", 0) ?: 0
                    val dealerTotal = dealersObj?.optInt("total", 0) ?: 0
                    partners.value = (resellerTotal + dealerTotal).toString()

                    val pendingWallet = (walletObj?.optInt("reseller_pending", 0) ?: 0) +
                        (walletObj?.optInt("dealer_pending", 0) ?: 0)
                    val pendingOrders = ordersObj?.optInt("pending", 0) ?: 0
                    val unreadNotifications = notificationsObj?.optInt("unread", 0) ?: 0
                    pending.value = (pendingWallet + pendingOrders + unreadNotifications).toString()
                }.onFailure { error ->
                    Log.e("MobileAdminActivity", "mobile admin dashboard failed", error)
                    revenue.value = "$0.00"
                    orders.value = "0"
                    partners.value = "0"
                    pending.value = "0"
                }
            }

            R2WTheme {
                AdminDashboardScreen(
                    totalRevenue = revenue.value,
                    totalOrders = orders.value,
                    totalPartners = partners.value,
                    pendingActions = pending.value,
                    onOrdersClick = {
                        startActivity(Intent(this@MobileAdminActivity, AdminOrdersActivity::class.java))
                    },
                    onPartnersClick = {
                        startActivity(Intent(this@MobileAdminActivity, AdminPartnersActivity::class.java))
                    },
                    onWalletApprovalsClick = {
                        val intent = Intent(this@MobileAdminActivity, ApprovalsActivity::class.java)
                        intent.putExtra(
                            ApprovalsActivity.EXTRA_APPROVAL_MODE,
                            ApprovalsActivity.MODE_ADMIN_RESELLER_WALLET
                        )
                        startActivity(intent)
                    },
                    onReportsClick = {
                        startActivity(Intent(this@MobileAdminActivity, AdminReportsOverviewActivity::class.java))
                    },
                    onBottomNavClick = { item ->
                        when (item) {
                            AdminBottomNavItem.Dashboard -> Unit
                            AdminBottomNavItem.Partners -> {
                                startActivity(Intent(this@MobileAdminActivity, AdminResellersActivity::class.java))
                            }
                            AdminBottomNavItem.Orders -> {
                                startActivity(Intent(this@MobileAdminActivity, AdminOrdersActivity::class.java))
                            }
                            AdminBottomNavItem.Pricing -> {
                                startActivity(Intent(this@MobileAdminActivity, AdminPricingOverviewActivity::class.java))
                            }
                            AdminBottomNavItem.More -> {
                                startActivity(Intent(this@MobileAdminActivity, AdminMoreActivity::class.java))
                            }
                        }
                    }
                )
            }
        }
    }

    private fun redirectToLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }
}
