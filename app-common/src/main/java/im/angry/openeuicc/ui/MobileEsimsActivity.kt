package im.angry.openeuicc.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.bottomnavigation.BottomNavigationView
import im.angry.openeuicc.auth.AuthSession
import im.angry.openeuicc.auth.AuthTokenStore
import im.angry.openeuicc.auth.JwtUtils
import im.angry.openeuicc.auth.MobileEsim
import im.angry.openeuicc.auth.Roam2WorldAuthApi
import im.angry.openeuicc.common.BuildConfig
import im.angry.openeuicc.common.R
import im.angry.openeuicc.util.activityToolbarInsetHandler
import im.angry.openeuicc.util.mainViewPaddingInsetHandler
import im.angry.openeuicc.util.setupRootViewSystemBarInsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MobileEsimsActivity : AppCompatActivity() {
    private val tokenStore by lazy { AuthTokenStore(this) }
    private val authApi by lazy { Roam2WorldAuthApi(BuildConfig.ROAM2WORLD_API_BASE_URL) }

    private lateinit var refresh: SwipeRefreshLayout
    private lateinit var bottomNav: BottomNavigationView
    private lateinit var esims: LinearLayout
    private lateinit var empty: TextView
    private lateinit var error: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mobile_esims)
        setSupportActionBar(requireViewById(R.id.toolbar))
        supportActionBar?.title = getString(R.string.mobile_esims_title)

        refresh = requireViewById(R.id.mobile_esims_refresh)
        bottomNav = requireViewById(R.id.mobile_esims_bottom_nav)
        esims = requireViewById(R.id.mobile_esims_list)
        empty = requireViewById(R.id.mobile_esims_empty)
        error = requireViewById(R.id.mobile_esims_error)

        setupInsets()
        setupBottomNavigation()
        refresh.setOnRefreshListener { loadEsims() }
        renderEsims(emptyList())
        loadEsims()
    }

    override fun onResume() {
        super.onResume()
        bottomNav.selectedItemId = R.id.nav_esims
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.activity_mobile_esims, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
            R.id.reload -> {
                loadEsims()
                true
            }

            R.id.open_device_esim_manager -> {
                openNativeEsimActivity()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }

    private fun setupInsets() {
        setupRootViewSystemBarInsets(
            window.decorView.rootView,
            arrayOf(
                this::activityToolbarInsetHandler,
                mainViewPaddingInsetHandler(refresh),
                { insets ->
                    bottomNav.updatePadding(
                        insets.left,
                        bottomNav.paddingTop,
                        insets.right,
                        insets.bottom
                    )
                }
            ),
            consume = false
        )
    }

    private fun setupBottomNavigation() {
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_dashboard -> {
                    openDashboardActivity()
                    false
                }
                R.id.nav_packages -> {
                    openPackagesActivity()
                    false
                }
                R.id.nav_wallet -> {
                    openWalletActivity()
                    false
                }
                R.id.nav_esims -> true
                else -> false
            }
        }
        bottomNav.selectedItemId = R.id.nav_esims
    }

    private fun loadEsims() {
        lifecycleScope.launch {
            error.visibility = View.GONE
            empty.visibility = View.GONE
            setLoading(true)
            val session = activeSessionOrReturnToLogin() ?: return@launch
            val result = runCatching { authApi.esims(session) }
            setLoading(false)

            result
                .onSuccess { renderEsims(it.esims) }
                .onFailure {
                    error.text = it.message ?: getString(R.string.mobile_esims_load_failed)
                    error.visibility = View.VISIBLE
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

    private fun renderEsims(esimData: List<MobileEsim>) {
        esims.removeAllViews()
        empty.visibility = if (esimData.isEmpty()) View.VISIBLE else View.GONE
        if (esimData.isEmpty()) return

        val inflater = LayoutInflater.from(this)
        esimData.forEach { esim ->
            val item = inflater.inflate(R.layout.mobile_esim_list_item, esims, false)
            item.requireViewById<TextView>(R.id.mobile_esim_title).text = esim.title()
            item.requireViewById<TextView>(R.id.mobile_esim_subtitle).text = listOfNotNull(
                esim.iccid?.let { getString(R.string.mobile_esim_iccid_format, it) },
                esim.provider
            ).joinToString(" - ")
            item.requireViewById<TextView>(R.id.mobile_esim_status).apply {
                applyRoamStatusChip(esim.statusLabel(), esim.status)
            }
            item.setOnClickListener {
                startActivity(MobileEsimDetailActivity.createIntent(this, esim))
            }
            esims.addView(item)
        }
    }

    private fun setLoading(loading: Boolean) {
        refresh.isRefreshing = loading
    }

    private fun openDashboardActivity() {
        startActivity(
            Intent(this, DashboardActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            }
        )
    }

    private fun openWalletActivity() {
        startActivity(
            Intent(this, WalletActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            }
        )
    }

    private fun openPackagesActivity() {
        startActivity(
            Intent(this, PackagesActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            }
        )
    }

    private fun openNativeEsimActivity() {
        val target = targetActivityName(DashboardActivity.META_ESIM_ACTIVITY)
        if (target.isNullOrBlank()) {
            error.text = getString(R.string.dashboard_missing_esim_target)
            error.visibility = View.VISIBLE
            return
        }
        startActivity(Intent().setClassName(this, target))
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

    private fun targetActivityName(key: String): String? {
        val appInfo = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
        return appInfo.metaData?.getString(key)
    }
}
