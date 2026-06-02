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
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.color.MaterialColors
import com.google.android.material.textfield.TextInputEditText
import im.angry.openeuicc.auth.AuthSession
import im.angry.openeuicc.auth.AuthTokenStore
import im.angry.openeuicc.auth.JwtUtils
import im.angry.openeuicc.auth.MobilePackage
import im.angry.openeuicc.auth.MobilePackageCatalog
import im.angry.openeuicc.auth.Roam2WorldAuthApi
import im.angry.openeuicc.common.BuildConfig
import im.angry.openeuicc.common.R
import im.angry.openeuicc.util.activityToolbarInsetHandler
import im.angry.openeuicc.util.mainViewPaddingInsetHandler
import im.angry.openeuicc.util.setupRootViewSystemBarInsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PackagesActivity : AppCompatActivity() {
    private val tokenStore by lazy { AuthTokenStore(this) }
    private val authApi by lazy { Roam2WorldAuthApi(BuildConfig.ROAM2WORLD_API_BASE_URL) }

    private lateinit var refresh: SwipeRefreshLayout
    private lateinit var bottomNav: BottomNavigationView
    private lateinit var search: TextInputEditText
    private lateinit var featuredTitle: TextView
    private lateinit var featuredPackages: LinearLayout
    private lateinit var packageList: LinearLayout
    private lateinit var empty: TextView
    private lateinit var error: TextView

    private var catalog = MobilePackageCatalog(emptyList(), emptyList())
    private var userRole: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_packages)
        setSupportActionBar(requireViewById(R.id.toolbar))
        supportActionBar?.title = getString(R.string.packages_title)

        refresh = requireViewById(R.id.packages_refresh)
        bottomNav = requireViewById(R.id.packages_bottom_nav)
        search = requireViewById(R.id.packages_search)
        featuredTitle = requireViewById(R.id.packages_featured_title)
        featuredPackages = requireViewById(R.id.packages_featured)
        packageList = requireViewById(R.id.packages_list)
        empty = requireViewById(R.id.packages_empty)
        error = requireViewById(R.id.packages_error)

        setupInsets()
        setupBottomNavigation()
        setupSearch()
        setupRefresh()
        renderCatalog()
        loadPackages()
    }

    override fun onResume() {
        super.onResume()
        bottomNav.selectedItemId = R.id.nav_packages
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.activity_dashboard, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
            R.id.reload -> {
                loadPackages()
                true
            }

            R.id.logout -> {
                logout()
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
                R.id.nav_packages -> true
                R.id.nav_wallet -> {
                    openWalletActivity()
                    false
                }
                R.id.nav_esims -> {
                    openEsimActivity()
                    false
                }
                else -> false
            }
        }
        bottomNav.selectedItemId = R.id.nav_packages
    }

    private fun setupSearch() {
        search.addTextChangedListener {
            renderCatalog()
        }
    }

    private fun setupRefresh() {
        refresh.setOnRefreshListener {
            loadPackages()
        }
    }

    private fun loadPackages() {
        lifecycleScope.launch {
            error.visibility = View.GONE
            setLoading(true)
            val session = activeSessionOrReturnToLogin() ?: return@launch
            userRole = session.role
            val result = runCatching {
                authApi.packages(session)
            }
            setLoading(false)

            result
                .onSuccess {
                    catalog = it
                    renderCatalog()
                }
                .onFailure {
                    error.text = it.message ?: getString(R.string.packages_load_failed)
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

    private fun renderCatalog() {
        val query = search.text?.toString().orEmpty()
        val featured = catalog.featuredPackages.filter { it.matches(query) }
        val packages = catalog.packages.filter { it.matches(query) }

        renderFeatured(featured)
        renderPackages(packages)
        empty.visibility = if (featured.isEmpty() && packages.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun renderFeatured(packageData: List<MobilePackage>) {
        featuredPackages.removeAllViews()
        featuredTitle.visibility = if (packageData.isEmpty()) View.GONE else View.VISIBLE
        featuredPackages.visibility = if (packageData.isEmpty()) View.GONE else View.VISIBLE
        packageData.forEach { mobilePackage ->
            featuredPackages.addView(createPackageCard(mobilePackage))
        }
    }

    private fun renderPackages(packageData: List<MobilePackage>) {
        packageList.removeAllViews()
        packageData
            .groupBy { it.country.ifBlank { getString(R.string.packages_global_country) } }
            .toSortedMap()
            .forEach { (country, packages) ->
                TextView(this).apply {
                    text = country
                    setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleSmall)
                    setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface))
                    packageList.addView(this)
                }
                packages.forEach { mobilePackage ->
                    packageList.addView(createPackageCard(mobilePackage))
                }
            }
    }

    private fun createPackageCard(mobilePackage: MobilePackage): View {
        val item = LayoutInflater.from(this).inflate(R.layout.package_list_item, packageList, false)
        item.requireViewById<TextView>(R.id.package_title).text = mobilePackage.name
        item.requireViewById<TextView>(R.id.package_country).text = listOfNotNull(
            mobilePackage.country,
            mobilePackage.countryCode?.takeIf { it.isNotBlank() }
        ).joinToString(" - ")
        item.requireViewById<TextView>(R.id.package_specs).apply {
            text = mobilePackage.specs()
            visibility = if (text.isBlank()) View.GONE else View.VISIBLE
        }
        item.requireViewById<TextView>(R.id.package_price).text = mobilePackage.priceFor(userRole)
        item.requireViewById<TextView>(R.id.package_visibility).text = mobilePackage.visibilityLabel()
        item.setOnClickListener {
            startActivity(PackageDetailActivity.createIntent(this, mobilePackage, userRole))
        }
        return item
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

    private fun openEsimActivity() {
        val target = targetActivityName(DashboardActivity.META_ESIM_ACTIVITY)
        if (target.isNullOrBlank()) {
            error.text = getString(R.string.dashboard_missing_esim_target)
            error.visibility = View.VISIBLE
            return
        }
        startActivity(Intent().setClassName(this, target))
    }

    private fun logout() {
        lifecycleScope.launch {
            val session = withContext(Dispatchers.IO) {
                tokenStore.getSession().also {
                    tokenStore.clear()
                }
            }
            session?.let {
                runCatching {
                    authApi.logout(it)
                }
            }
            openLoginActivity()
        }
    }

    private fun redirectToLogin(): AuthSession? {
        tokenStore.clear()
        openLoginActivity()
        return null
    }

    private fun openLoginActivity() {
        startActivity(
            Intent(this, LoginActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
        )
        finish()
    }

    private fun targetActivityName(key: String): String? {
        val appInfo = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
        return appInfo.metaData?.getString(key)
    }
}
