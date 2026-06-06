package im.angry.openeuicc.ui

import android.app.AlertDialog
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
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
    private var lastEmptyReason: String? = null
    private var selectedRegion = FILTER_ALL
    private var selectedProvider = FILTER_ALL
    private var selectedData = FILTER_ALL
    private var selectedValidity = FILTER_ALL

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
        lastEmptyReason = LIVE_CATALOG_EMPTY_MESSAGE
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
                .onSuccess { remoteCatalog ->
                    catalog = remoteCatalog
                    lastEmptyReason = if (remoteCatalog.hasProducts()) {
                        null
                    } else {
                        LIVE_CATALOG_EMPTY_MESSAGE
                    }
                    renderCatalog()
                }
                .onFailure {
                    catalog = MobilePackageCatalog(emptyList(), emptyList())
                    lastEmptyReason = it.message ?: getString(R.string.packages_load_failed)
                    renderCatalog()
                    error.text = lastEmptyReason
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
        val featured = catalog.featuredPackages.filter { it.matches(query) && it.matchesSelectedFilters() }
        val packages = catalog.packages.filter { it.matches(query) && it.matchesSelectedFilters() }
        val isEmpty = featured.isEmpty() && packages.isEmpty()

        renderFeatured(featured)
        renderPackages(packages)
        empty.text = if (isEmpty) {
            if (query.isBlank() && filtersAreDefault()) {
                lastEmptyReason ?: LIVE_CATALOG_EMPTY_MESSAGE
            } else {
                "No packages match your filters."
            }
        } else {
            ""
        }
        empty.visibility = if (isEmpty) View.VISIBLE else View.GONE
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
        addFilterPanel(packageList)
        packageData
            .groupBy { it.country.ifBlank { getString(R.string.packages_global_country) } }
            .toSortedMap()
            .forEach { (country, packages) ->
                TextView(this).apply {
                    text = country
                    setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleSmall)
                    setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface))
                    setPadding(0, dp(12), 0, dp(8))
                    packageList.addView(this)
                }
                packages.forEach { mobilePackage ->
                    packageList.addView(createPackageCard(mobilePackage))
                }
            }
    }

    private fun addFilterPanel(parent: LinearLayout) {
        val titleRow = LinearLayout(this).apply {
            orientatgit add app-common/src/main/java/im/angry/openeuicc/ui/PackagesActivity.kt
            git commit -m "merge main into store local filters"
            git push origin feat/store-local-filters-v2ion = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(10))
        }

        titleRow.addView(
            TextView(this).apply {
                text = "Filter Plans"
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium)
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface))
            },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )

        if (!filtersAreDefault()) {
            titleRow.addView(
                TextView(this).apply {
                    text = "Clear"
                    setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelLarge)
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimary))
                    setPadding(dp(12), dp(8), dp(12), dp(8))
                    setOnClickListener {
                        selectedRegion = FILTER_ALL
                        selectedProvider = FILTER_ALL
                        selectedData = FILTER_ALL
                        selectedValidity = FILTER_ALL
                        renderCatalog()
                    }
                }
            )
        }
        parent.addView(titleRow)

        val rowOne = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            baselineAligned = false
        }
        rowOne.addView(createFilterButton("Region", selectedRegion) {
            showFilterDialog("Region", REGION_FILTERS, selectedRegion) { selectedRegion = it }
        }, filterButtonParams(end = 6))
        rowOne.addView(createFilterButton("Provider", selectedProvider) {
            showFilterDialog("Provider", PROVIDER_FILTERS, selectedProvider) { selectedProvider = it }
        }, filterButtonParams(start = 6))
        parent.addView(rowOne)

        val rowTwo = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            baselineAligned = false
            setPadding(0, dp(10), 0, dp(12))
        }
        rowTwo.addView(createFilterButton("Data", selectedData) {
            showFilterDialog("Data", DATA_FILTERS, selectedData) { selectedData = it }
        }, filterButtonParams(end = 6))
        rowTwo.addView(createFilterButton("Validity", selectedValidity) {
            showFilterDialog("Validity", VALIDITY_FILTERS, selectedValidity) { selectedValidity = it }
        }, filterButtonParams(start = 6))
        parent.addView(rowTwo)
    }

<<<<<<< HEAD
    private fun createFilterButton(label: String, value: String, onClick: () -> Unit): TextView {
        val selected = value != FILTER_ALL
        val anchor = window.decorView
        val primary = MaterialColors.getColor(anchor, com.google.android.material.R.attr.colorPrimary)
        val onPrimary = MaterialColors.getColor(anchor, com.google.android.material.R.attr.colorOnPrimary)
        val secondaryText = MaterialColors.getColor(anchor, com.google.android.material.R.attr.colorOnSurfaceVariant)
=======
    private fun createFilterChip(label: String, selected: Boolean, onClick: () -> Unit): TextView {
        val primary = MaterialColors.getColor(window.decorView, com.google.android.material.R.attr.colorPrimary)
        val onPrimary = MaterialColors.getColor(window.decorView, com.google.android.material.R.attr.colorOnPrimary)
        val secondaryText = MaterialColors.getColor(window.decorView, com.google.android.material.R.attr.colorOnSurfaceVariant)
>>>>>>> origin/main
        return TextView(this).apply {
            text = "$label: $value  ▼"
            gravity = Gravity.CENTER
            maxLines = 1
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelMedium)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(if (selected) onPrimary else secondaryText)
            setPadding(dp(10), dp(10), dp(10), dp(10))
            setBackgroundResource(R.drawable.wallet_request_status_badge)
            backgroundTintList = ColorStateList.valueOf(if (selected) primary else getColor(R.color.r2w_card))
            setOnClickListener { onClick() }
        }
    }

    private fun showFilterDialog(
        title: String,
        options: List<String>,
        currentValue: String,
        onSelected: (String) -> Unit
    ) {
        val currentIndex = options.indexOf(currentValue).coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle(title)
            .setSingleChoiceItems(options.toTypedArray(), currentIndex) { dialog, which ->
                onSelected(options[which])
                dialog.dismiss()
                renderCatalog()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun filterButtonParams(start: Int = 0, end: Int = 0): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            setMargins(dp(start), 0, dp(end), 0)
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

    private fun MobilePackage.matchesSelectedFilters(): Boolean =
        matchesRegion(selectedRegion) && matchesProvider(selectedProvider) && matchesData(selectedData) && matchesValidity(selectedValidity)

    private fun MobilePackage.matchesRegion(region: String): Boolean {
        if (region == FILTER_ALL) return true
        val text = searchableText()
        return when (region) {
            "Turkey" -> text.contains("turkey") || text.contains(" tr ") || countryCode.equals("TR", ignoreCase = true)
            "Europe" -> text.contains("europe") || text.contains("europa") || text.contains(" eu ")
            "Europe Balkans" -> text.contains("balkan") || text.contains("balkans")
            "Global" -> text.contains("global") || text.contains("world") || text.contains("worldwide")
            else -> text.contains(region.lowercase())
        }
    }

    private fun MobilePackage.matchesProvider(providerFilter: String): Boolean {
        if (providerFilter == FILTER_ALL) return true
        val providerText = provider.orEmpty().lowercase()
        return when (providerFilter) {
            "TraveRoam" -> providerText.contains("traveroam") || providerText.contains("travelroam")
            "TGT" -> providerText.contains("tgt")
            "Flexnet" -> providerText.contains("flexnet")
            "AirHub" -> providerText.contains("airhub") || providerText.contains("airhubapp")
            "eSIMCard" -> providerText.contains("esimcard") || providerText.contains("esim_card")
            else -> providerText.contains(providerFilter.lowercase())
        }
    }

    private fun MobilePackage.matchesData(dataFilter: String): Boolean {
        if (dataFilter == FILTER_ALL) return true
        val text = searchableText()
        if (dataFilter == "Unlimited") {
            return text.contains("unlimited") || text.contains(" ul ") || text.contains("_ul_")
        }
        val gb = dataFilter.removeSuffix("GB").trim().toIntOrNull() ?: return true
        val compact = text.replace(" ", "")
        return compact.contains("${gb}gb") || text.contains("${gb * 1000} mb") || text.contains("${gb * 1024} mb")
    }

    private fun MobilePackage.matchesValidity(validityFilter: String): Boolean {
        if (validityFilter == FILTER_ALL) return true
        val days = validityFilter.substringBefore(" ").toIntOrNull() ?: return true
        val text = searchableText()
        val compact = text.replace(" ", "")
        return compact.contains("${days}days") || compact.contains("${days}day") || compact.contains("${days}d")
    }

    private fun MobilePackage.searchableText(): String =
        listOfNotNull(
            provider,
            packageType,
            name,
            country,
            countryCode,
            dataAmount,
            validity,
            description,
            network,
            coverage,
            specs()
        ).joinToString(" ").lowercase()

    private fun filtersAreDefault(): Boolean =
        selectedRegion == FILTER_ALL && selectedProvider == FILTER_ALL && selectedData == FILTER_ALL && selectedValidity == FILTER_ALL

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

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

    private companion object {
        private const val LIVE_CATALOG_EMPTY_MESSAGE =
            "No live packages available. Please configure packages in Roam2World backend."
        private const val FILTER_ALL = "All"
        private val REGION_FILTERS = listOf("All", "Turkey", "Europe", "Europe Balkans", "Global")
        private val PROVIDER_FILTERS = listOf("All", "TraveRoam", "TGT", "Flexnet", "AirHub", "eSIMCard")
        private val DATA_FILTERS = listOf(
            "All", "1GB", "2GB", "3GB", "5GB", "10GB", "20GB", "30GB", "50GB",
            "60GB", "100GB", "135GB", "200GB", "300GB", "400GB", "500GB", "Unlimited"
        )
        private val VALIDITY_FILTERS = listOf(
            "All", "1 Day", "3 Days", "5 Days", "7 Days", "10 Days", "15 Days", "30 Days", "60 Days", "90 Days"
        )
    }
}
