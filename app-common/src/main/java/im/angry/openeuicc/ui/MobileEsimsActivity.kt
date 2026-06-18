package im.angry.openeuicc.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import im.angry.openeuicc.auth.AuthSession
import im.angry.openeuicc.auth.AuthTokenStore
import im.angry.openeuicc.auth.JwtUtils
import im.angry.openeuicc.auth.MobileEsim
import im.angry.openeuicc.auth.MobileEsimFilters
import im.angry.openeuicc.auth.Roam2WorldAuthApi
import im.angry.openeuicc.common.BuildConfig
import im.angry.openeuicc.common.R
import im.angry.openeuicc.ui.compose.screens.CompactMobileEsimsScreen
import im.angry.openeuicc.ui.compose.screens.EsimFilter
import im.angry.openeuicc.ui.compose.screens.realStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MobileEsimsActivity : ComponentActivity() {
    private val tokenStore by lazy { AuthTokenStore(this) }
    private val authApi by lazy { Roam2WorldAuthApi(BuildConfig.ROAM2WORLD_API_BASE_URL) }

    private var allEsims by mutableStateOf<List<MobileEsim>>(emptyList())
    private var selectedFilter by mutableStateOf(EsimFilter.ACTIVE)
    private var initialFilter: String? = null
    private var loading by mutableStateOf(false)
    private var errorMessage by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        actionBar?.hide()
        configureSystemBars()
        initialFilter = intent.getStringExtra(MobileEsimFilters.FILTER_EXTRA_KEY)?.trim()

        setContent {
            CompactMobileEsimsScreen(
                allEsims = allEsims,
                filteredEsims = filteredEsims(),
                selectedFilter = selectedFilter,
                initialFilter = initialFilter,
                loading = loading,
                error = errorMessage,
                onFilterChange = {
                    selectedFilter = it
                    initialFilter = null
                },
                onRefresh = { loadEsims() },
                onOpenDetail = { esim -> startActivity(MobileEsimDetailActivity.createIntent(this, esim)) },
                onOpenDashboard = { openDashboardActivity() },
                onOpenPackages = { openPackagesActivity() },
                onOpenMore = { openMoreActivity() }
            )
        }

        loadEsims()
    }

    private fun configureSystemBars() {
        window.statusBarColor = Color.rgb(248, 250, 255)
        window.navigationBarColor = Color.BLACK
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = false
        }
    }

    private fun loadEsims() {
        lifecycleScope.launch {
            errorMessage = null
            loading = true
            val session = activeSessionOrReturnToLogin()
            if (session == null) {
                loading = false
                return@launch
            }
            runCatching { authApi.esims(session) }
                .onSuccess { allEsims = it.esims }
                .onFailure { errorMessage = it.message ?: getString(R.string.mobile_esims_load_failed) }
            loading = false
        }
    }

    private fun filteredEsims(): List<MobileEsim> {
        val base = if (initialFilter == MobileEsimFilters.FILTER_EXPIRED_SOON) {
            allEsims.filter { MobileEsimFilters.isExpiredSoon(it) }
        } else {
            allEsims
        }
        return if (initialFilter == MobileEsimFilters.FILTER_EXPIRED_SOON) {
            base
        } else {
            base.filter { selectedFilter.matches(realStatus(it)) }
        }
    }

    private suspend fun activeSessionOrReturnToLogin(): AuthSession? {
        val savedSession = withContext(Dispatchers.IO) { tokenStore.getSession() } ?: return redirectToLogin()
        if (!JwtUtils.isExpired(savedSession.accessToken)) return savedSession
        val refreshed = runCatching { authApi.refresh(savedSession) }.getOrNull() ?: return redirectToLogin()
        withContext(Dispatchers.IO) { tokenStore.save(refreshed) }
        return refreshed
    }

    private fun openDashboardActivity() {
        startActivity(Intent(this, DashboardActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
    }

    private fun openPackagesActivity() {
        startActivity(Intent(this, PackagesActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
    }

    private fun openMoreActivity() {
        startActivity(Intent(this, MoreActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
    }

    private fun redirectToLogin(): AuthSession? {
        tokenStore.clear()
        startActivity(Intent(this, LoginActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        })
        finish()
        return null
    }
}
