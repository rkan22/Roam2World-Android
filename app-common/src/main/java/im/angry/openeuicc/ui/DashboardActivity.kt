package im.angry.openeuicc.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import im.angry.openeuicc.auth.AuthSession
import im.angry.openeuicc.auth.AuthTokenStore
import im.angry.openeuicc.auth.JwtUtils
import im.angry.openeuicc.auth.MobileDashboardData
import im.angry.openeuicc.auth.MobileEsimFilters
import im.angry.openeuicc.auth.Roam2WorldAuthApi
import im.angry.openeuicc.common.BuildConfig
import im.angry.openeuicc.ui.compose.screens.DashboardScreen
import im.angry.openeuicc.ui.compose.theme.R2WTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DashboardActivity : ComponentActivity() {
    private val tokenStore by lazy { AuthTokenStore(this) }
    private val authApi by lazy { Roam2WorldAuthApi(BuildConfig.ROAM2WORLD_API_BASE_URL) }

    private val dashboardDataFlow = MutableStateFlow<MobileDashboardData?>(null)
    private var displayName by mutableStateOf("Admin")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = android.graphics.Color.rgb(244, 246, 250)
        window.navigationBarColor = android.graphics.Color.WHITE
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        }

        setContent {
            val data by dashboardDataFlow.collectAsState()
            R2WTheme {
                DashboardScreen(
                    userName = displayName,
                    data = data,
                    onWalletClick = { openWalletActivity() },
                    onActionClick = { action ->
                        when (action) {
                            "store", "packages" -> startActivity(Intent(this, PackagesActivity::class.java))
                            "orders", "history", "transactions" -> openPurchaseHistoryActivity()
                            "wallet" -> openWalletActivity()
                            "orange", "recharge", "tgt" -> startActivity(Intent(this, TgtSimRechargeActivity::class.java))
                            "vodafone" -> startActivity(Intent(this, VodafoneRenewalActivity::class.java))
                            "crm", "customers", "dealers" -> openMyDealersActivity()
                            "reports" -> startActivity(Intent(this, ReportsActivity::class.java))
                            "check_gb", "esims" -> startActivity(Intent(this, MobileEsimsActivity::class.java))
                            "expiring_soon" -> openExpiredSoonEsims()
                            "openeuicc" -> startActivity(Intent(this, OpenEuiccIntegrationActivity::class.java))
                            "more" -> startActivity(Intent(this, MoreActivity::class.java))
                        }
                    }
                )
            }
        }

        authApi.logMobileEndpointConfiguration()
        loadDashboard()
    }

    private fun loadDashboard() {
        lifecycleScope.launch {
            val session = activeSessionOrReturnToLogin() ?: return@launch
            displayName = session.displayName ?: "Admin"

            runCatching {
                authApi.dashboard(session)
            }.onSuccess { dashboard ->
                dashboardDataFlow.value = dashboard
            }
        }
    }

    private suspend fun activeSessionOrReturnToLogin(): AuthSession? {
        val savedSession = withContext(Dispatchers.IO) {
            tokenStore.getSession()
        } ?: return redirectToLogin()

        if (!JwtUtils.isExpired(savedSession.accessToken)) return savedSession

        val refreshed = runCatching {
            authApi.refresh(savedSession)
        }.getOrNull() ?: return redirectToLogin()

        withContext(Dispatchers.IO) {
            tokenStore.save(refreshed)
        }

        return refreshed
    }

    private fun openWalletActivity() {
        try {
            startActivity(Intent(this, WalletActivity::class.java))
        } catch (e: Exception) {
            android.widget.Toast.makeText(this, e.message ?: "Wallet could not be opened", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    private fun openPurchaseHistoryActivity() {
        startActivity(Intent(this, PurchaseHistoryActivity::class.java))
    }

    private fun openMyDealersActivity() {
        startActivity(Intent(this, MyDealersActivity::class.java))
    }

    private fun redirectToLogin(): AuthSession? {
        tokenStore.clear()
        startActivity(
            Intent(this, LoginActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
        )
        finish()
        return null
    }

    private fun openExpiredSoonEsims() {
        startActivity(
            Intent(this, MobileEsimsActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                putExtra(MobileEsimFilters.FILTER_EXTRA_KEY, MobileEsimFilters.FILTER_EXPIRED_SOON)
            }
        )
    }

    companion object {
        const val META_ESIM_ACTIVITY = "im.angry.openeuicc.DASHBOARD_ESIM_ACTIVITY"
    }
}
