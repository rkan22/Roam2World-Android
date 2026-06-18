package im.angry.openeuicc.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
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
import im.angry.openeuicc.auth.MobilePackage
import im.angry.openeuicc.auth.MobilePackageCatalog
import im.angry.openeuicc.auth.Roam2WorldAuthApi
import im.angry.openeuicc.common.BuildConfig
import im.angry.openeuicc.ui.compose.screens.CompactStoreScreen
import im.angry.openeuicc.ui.compose.screens.StoreProviderTabs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.util.Locale

class PackagesActivity : ComponentActivity() {
    private val tokenStore by lazy { AuthTokenStore(this) }
    private val authApi by lazy { Roam2WorldAuthApi(BuildConfig.ROAM2WORLD_API_BASE_URL) }

    private var loading by mutableStateOf(false)
    private var catalog by mutableStateOf(MobilePackageCatalog(emptyList(), emptyList()))
    private var userRole by mutableStateOf<String?>(null)
    private var errorMessage by mutableStateOf<String?>(null)
    private var query by mutableStateOf("")
    private var selectedProvider by mutableStateOf(StoreProviderTabs.first())
    private var cartCount by mutableStateOf(ShoppingCartStore.count())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        actionBar?.hide()
        configureSystemBars()

        setContent {
            CompactStoreScreen(
                loading = loading,
                catalog = catalog,
                userRole = userRole,
                errorMessage = errorMessage,
                query = query,
                selectedProvider = selectedProvider,
                onQueryChange = { query = it },
                onProviderChange = { selectedProvider = it },
                onRefresh = { loadPackages() },
                onOpenPackage = { mobilePackage ->
                    startActivity(PackageDetailActivity.createIntent(this, mobilePackage, userRole))
                },
                onDashboard = { startActivity(Intent(this, DashboardActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)) },
                onEsims = { startActivity(Intent(this, MobileEsimsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)) },
                onMore = { startActivity(Intent(this, MoreActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)) }
            )
        }

        loadPackages()
    }

    override fun onResume() {
        super.onResume()
        cartCount = ShoppingCartStore.count()
    }

    private fun configureSystemBars() {
        window.statusBarColor = Color.rgb(248, 250, 255)
        window.navigationBarColor = Color.BLACK
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = false
        }
    }

    private fun loadPackages() {
        lifecycleScope.launch {
            loading = true
            errorMessage = null
            val session = activeSessionOrReturnToLogin()
            if (session == null) {
                loading = false
                return@launch
            }
            userRole = session.role
            runCatching { authApi.packages(session) }
                .onSuccess {
                    catalog = it
                    errorMessage = if (it.featuredPackages.isEmpty() && it.packages.isEmpty()) {
                        "No live packages available. Please configure packages in Roam2World backend."
                    } else null
                }
                .onFailure {
                    catalog = MobilePackageCatalog(emptyList(), emptyList())
                    errorMessage = it.message ?: "Packages could not be loaded"
                }
            loading = false
        }
    }

    private fun addToCart(mobilePackage: MobilePackage) {
        val title = mobilePackage.name
        val subtitle = mobilePackage.validity.orEmpty().ifBlank { "eSIM data plan" }
        val price = r2wMoney(mobilePackage.priceFor(userRole))
        val providerName = mobilePackage.providerLabel()

        ShoppingCartStore.add(
            ShoppingCartStore.Item(
                id = listOf(title, subtitle, price, providerName).joinToString("-"),
                title = title,
                subtitle = subtitle,
                provider = providerName,
                price = price
            )
        )

        cartCount = ShoppingCartStore.count()
        Toast.makeText(this, "$title added to cart", Toast.LENGTH_SHORT).show()
    }

    private suspend fun activeSessionOrReturnToLogin(): AuthSession? {
        val savedSession = withContext(Dispatchers.IO) { tokenStore.getSession() } ?: return redirectToLogin()
        if (!JwtUtils.isExpired(savedSession.accessToken)) return savedSession
        val refreshed = runCatching { authApi.refresh(savedSession) }.getOrNull() ?: return redirectToLogin()
        withContext(Dispatchers.IO) { tokenStore.save(refreshed) }
        return refreshed
    }

    private fun redirectToLogin(): AuthSession? {
        tokenStore.clear()
        openLoginActivity()
        return null
    }

    private fun openLoginActivity() {
        startActivity(Intent(this, LoginActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        })
        finish()
    }
}

private fun r2wMoney(raw: String): String {
    val numeric = raw.filter { it.isDigit() || it == '.' }.toDoubleOrNull() ?: return raw
    return NumberFormat.getCurrencyInstance(Locale.US).format(numeric)
}
