package im.angry.openeuicc.ui

import android.content.Intent
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
        catalog = demoCatalog()
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

            R.id.purchase_history -> {
                openPurchaseHistoryActivity()
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
                R.id.nav_more -> {
                    openMoreActivity()
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
                    catalog = it.takeIf { remoteCatalog -> remoteCatalog.hasProducts() } ?: demoCatalog()
                    renderCatalog()
                }
                .onFailure {
                    catalog = demoCatalog()
                    renderCatalog()
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
        startActivity(
            Intent(this, MobileEsimsActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            }
        )
    }

    private fun openMoreActivity() {
        startActivity(
            Intent(this, MoreActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            }
        )
    }

    private fun openPurchaseHistoryActivity() {
        startActivity(Intent(this, PurchaseHistoryActivity::class.java))
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

    private fun MobilePackageCatalog.hasProducts(): Boolean =
        featuredPackages.isNotEmpty() || packages.isNotEmpty()

    private fun demoCatalog(): MobilePackageCatalog {
        val packages = listOf(
            demoPackage("demo-r2w-tr-5", "Roam2World Turkey", "Turkey 5GB", "Turkey", "TR", "5GB", "30 days", "Turkey", "€6.50", featured = true),
            demoPackage("demo-r2w-tr-10", "Roam2World Turkey", "Turkey 10GB", "Turkey", "TR", "10GB", "30 days", "Turkey", "€10.50", featured = true),
            demoPackage("demo-r2w-tr-20", "Roam2World Turkey", "Turkey 20GB", "Turkey", "TR", "20GB", "30 days", "Turkey", "€18.00"),
            demoPackage("demo-r2w-tr-50", "Roam2World Turkey", "Turkey 50GB", "Turkey", "TR", "50GB", "30 days", "Turkey", "€36.00"),

            demoPackage("demo-orange-eu-20", "Orange Europe", "Orange Europe 20GB", "Multi-country", null, "20GB", "30 days", "Europe", "€22.00", featured = true),
            demoPackage("demo-orange-eu-50", "Orange Europe", "Orange Europe 50GB", "Multi-country", null, "50GB", "30 days", "Europe", "€42.00"),
            demoPackage("demo-orange-eu-100", "Orange Europe", "Orange Europe 100GB", "Multi-country", null, "100GB", "30 days", "Europe", "€69.00"),
            demoPackage("demo-orange-eu-200", "Orange Europe", "Orange Europe 200GB", "Multi-country", null, "200GB", "30 days", "Europe", "€118.00"),
            demoPackage("demo-orange-eu-500", "Orange Europe", "Orange Europe 500GB", "Multi-country", null, "500GB", "30 days", "Europe", "€249.00"),

            demoPackage("demo-orange-balkans-10", "Orange Balkans", "Orange Balkans 10GB", "Balkans", null, "10GB", "30 days", "Balkans", "€11.00", featured = true),
            demoPackage("demo-orange-balkans-20", "Orange Balkans", "Orange Balkans 20GB", "Balkans", null, "20GB", "30 days", "Balkans", "€19.00"),
            demoPackage("demo-orange-balkans-30", "Orange Balkans", "Orange Balkans 30GB", "Balkans", null, "30GB", "30 days", "Balkans", "€27.00"),
            demoPackage("demo-orange-balkans-50", "Orange Balkans", "Orange Balkans 50GB", "Balkans", null, "50GB", "30 days", "Balkans", "€39.00"),
            demoPackage("demo-orange-balkans-20-60", "Orange Balkans", "Orange Balkans 20GB 60D", "Balkans", null, "20GB", "60 days", "Balkans", "€25.00"),
            demoPackage("demo-orange-balkans-60-60", "Orange Balkans", "Orange Balkans 60GB 60D", "Balkans", null, "60GB", "60 days", "Balkans", "€55.00"),

            demoPackage("demo-orange-world-20", "Orange World", "Orange World 20GB", "Global", null, "20GB", "30 days", "World", "€35.00"),
            demoPackage("demo-orange-world-50", "Orange World", "Orange World 50GB", "Global", null, "50GB", "30 days", "World", "€79.00"),
            demoPackage("demo-orange-world-100", "Orange World", "Orange World 100GB", "Global", null, "100GB", "30 days", "World", "€139.00"),

            demoPackage("demo-vodafone-eu", "AirHub", "Vodafone Europe", "Multi-country", null, "25GB", "30 days", "Europe", "€32.00"),
            demoPackage("demo-orange-big-135", "Orange Big Data", "Orange Big Data 135GB", "Multi-country", null, "135GB", "30 days", "Europe", "€92.00"),
            demoPackage("demo-orange-big-200", "Orange Big Data", "Orange Big Data 200GB", "Multi-country", null, "200GB", "30 days", "Europe", "€129.00"),
            demoPackage("demo-orange-big-300", "Orange Big Data", "Orange Big Data 300GB", "Multi-country", null, "300GB", "30 days", "Europe", "€179.00")
        )
        return MobilePackageCatalog(
            featuredPackages = packages.filter { it.featured },
            packages = packages
        )
    }

    private fun demoPackage(
        id: String,
        provider: String,
        name: String,
        country: String,
        countryCode: String?,
        dataAmount: String,
        validity: String,
        coverage: String,
        price: String,
        featured: Boolean = false
    ): MobilePackage = MobilePackage(
        id = id,
        provider = provider,
        packageType = "esim",
        name = name,
        country = country,
        countryCode = countryCode,
        dataAmount = dataAmount,
        validity = validity,
        basePrice = price,
        resellerPrice = price,
        dealerPrice = price,
        description = "Demo catalog package for Roam2World B2B Store",
        network = provider,
        coverage = coverage,
        visibleToReseller = true,
        visibleToDealer = true,
        featured = featured
    )
}
