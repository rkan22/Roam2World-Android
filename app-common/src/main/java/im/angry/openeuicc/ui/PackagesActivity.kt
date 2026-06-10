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
import android.widget.HorizontalScrollView
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


private enum class StoreSection(val title: String) {
    ORANGE_EUROPE("Orange Europe"),
    ORANGE_WORLD("Orange World"),
    ORANGE_BALKANS_ESIM("Orange Balkans eSIM"),
    ORANGE_BALKANS_SIM("Orange Balkans SIM"),
    TURKEY("Türkiye"),
    TGT("TGT"),
    VODAFONE("Vodafone"),
    ALL("All Packages")
}

private val STORE_SECTIONS = listOf(
    StoreSection.ORANGE_EUROPE,
    StoreSection.ORANGE_WORLD,
    StoreSection.ORANGE_BALKANS_SIM,
    StoreSection.ORANGE_BALKANS_ESIM,
    StoreSection.TURKEY,
    StoreSection.VODAFONE
)

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
    private var selectedSort = StoreSort.LOWEST_PRICE
    private var selectedStoreSection: StoreSection? = StoreSection.ORANGE_EUROPE

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
        val featured = catalog.featuredPackages
            .filter { it.matches(query) && it.matchesSelectedFilters() && !it.isEurope33Package() }
            .sortedBy { it.sortPriceValue() }
        val packages = catalog.packages
            .filter { it.matches(query) && it.matchesSelectedFilters() && !it.isEurope33Package() }
            .sortedBy { it.sortPriceValue() }
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

        val section = selectedStoreSection ?: StoreSection.ORANGE_EUROPE
        val sectionFilterOptions = catalog.packages
            .filter { it.matchesStoreSection(section) }
            .sortedBy { it.sortPriceValue() }

        resetUnavailableFilters(sectionFilterOptions)

        addStoreCategories(packageList, packageData)
        addFilterPanel(packageList, sectionFilterOptions)

        val sectionPackages = packageData
            .filter {
                it.matchesStoreSection(section) ||
                    (section == StoreSection.ORANGE_EUROPE && it.isOrangeEurope500gb90dPackage())
            }
            .let { sortPackages(it) }

        addSectionTitle(packageList, section.title, "Lowest price first")

        if (sectionPackages.isEmpty()) {
            addSectionTitle(packageList, "No packages found", "Try another category or filter.")
            return
        }

        sectionPackages.forEach { mobilePackage ->
            packageList.addView(createPackageCard(mobilePackage))
        }
    }

    private fun addStoreCategories(parent: LinearLayout, packageData: List<MobilePackage>) {
        val scroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            setPadding(0, 0, 0, dp(12))
        }

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, dp(2))
        }

        STORE_SECTIONS.forEachIndexed { index, section ->
            val count = packageData.count { it.matchesStoreSection(section) }
            row.addView(
                createStoreCategoryChip(section, count),
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(if (index == 0) 0 else dp(8), 0, 0, 0)
                }
            )
        }

        scroll.addView(row)
        parent.addView(scroll)
    }

    private fun createStoreCategoryChip(section: StoreSection, count: Int): View {
        val selected = selectedStoreSection == section
        val primary = MaterialColors.getColor(window.decorView, com.google.android.material.R.attr.colorPrimary)
        val onPrimary = MaterialColors.getColor(window.decorView, com.google.android.material.R.attr.colorOnPrimary)
        val onSurface = MaterialColors.getColor(window.decorView, com.google.android.material.R.attr.colorOnSurface)
        val surface = getColor(R.color.r2w_card)

        val chip = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            minimumWidth = dp(106)
            minimumHeight = dp(92)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setBackgroundResource(R.drawable.wallet_request_status_badge)
            backgroundTintList = ColorStateList.valueOf(if (selected) primary else surface)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                selectedStoreSection = section
                renderCatalog()
            }
        }

        chip.addView(TextView(this).apply {
            text = sectionEmoji(section)
            textSize = 24f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(6))
        })

        chip.addView(TextView(this).apply {
            text = section.title
            gravity = Gravity.CENTER
            maxLines = 2
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelLarge)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(if (selected) onPrimary else onSurface)
        })

        chip.addView(TextView(this).apply {
            text = "$count"
            gravity = Gravity.CENTER
            maxLines = 1
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelSmall)
            setTextColor(if (selected) onPrimary else MaterialColors.getColor(window.decorView, com.google.android.material.R.attr.colorOnSurfaceVariant))
            setPadding(0, dp(3), 0, 0)
        })

        return chip
    }

    private fun sectionEmoji(section: StoreSection): String =
        when (section) {
            StoreSection.ORANGE_EUROPE -> "🇪🇺"
            StoreSection.ORANGE_WORLD -> "🌐"
            StoreSection.ORANGE_BALKANS_ESIM -> "🌍"
            StoreSection.ORANGE_BALKANS_SIM -> "💳"
            StoreSection.TURKEY -> "🇹🇷"
            StoreSection.TGT -> "📶"
            StoreSection.VODAFONE -> "📱"
            StoreSection.ALL -> "🛒"
        }

    private fun addStoreSectionHeader(parent: LinearLayout, section: StoreSection) {
        TextView(this).apply {
            text = "← Store Categories"
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelLarge)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimary))
            setPadding(0, dp(6), 0, dp(14))
            setOnClickListener {
                selectedStoreSection = StoreSection.ORANGE_EUROPE
                renderCatalog()
            }
            parent.addView(this)
        }
    }

    private fun createStoreCategoryCard(section: StoreSection, count: Int): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            setBackgroundResource(R.drawable.wallet_request_status_badge)
            backgroundTintList = ColorStateList.valueOf(getColor(R.color.r2w_card))
            setOnClickListener {
                selectedStoreSection = section
                renderCatalog()
            }
        }

        card.addView(TextView(this).apply {
            text = section.title
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface))
        })

        card.addView(TextView(this).apply {
            text = if (section == StoreSection.ALL) {
                "Browse every package, lowest price first"
            } else {
                "$count packages - lowest price first"
            }
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
            setTextColor(MaterialColors.getColor(window.decorView, com.google.android.material.R.attr.colorOnSurfaceVariant))
            setPadding(0, dp(4), 0, 0)
        })

        card.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(0, 0, 0, dp(10))
        }

        return card
    }

    private fun addPackageSection(
        parent: LinearLayout,
        title: String,
        subtitle: String?,
        packages: List<MobilePackage>
    ) {
        if (packages.isEmpty()) return
        addSectionTitle(parent, title, subtitle)
        packages.forEach { mobilePackage ->
            parent.addView(createPackageCard(mobilePackage))
        }
    }

    private fun addSectionTitle(
        parent: LinearLayout,
        title: String,
        subtitle: String?,
        compact: Boolean = false
    ) {
        TextView(this).apply {
            text = title
            setTextAppearance(
                if (compact) {
                    com.google.android.material.R.style.TextAppearance_Material3_TitleSmall
                } else {
                    com.google.android.material.R.style.TextAppearance_Material3_TitleMedium
                }
            )
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface))
            setPadding(0, if (compact) dp(12) else dp(18), 0, if (subtitle.isNullOrBlank()) dp(8) else dp(2))
            parent.addView(this)
        }

        if (!subtitle.isNullOrBlank()) {
            TextView(this).apply {
                text = subtitle
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
                setTextColor(MaterialColors.getColor(window.decorView, com.google.android.material.R.attr.colorOnSurfaceVariant))
                setPadding(0, 0, 0, dp(10))
                parent.addView(this)
            }
        }
    }

    private fun addFilterPanel(parent: LinearLayout, sectionPackages: List<MobilePackage>) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(8), 0, dp(12))
        }

        row.addView(
            createFilterButton("Data", selectedData) {
                showFilterDialog("Data", dataFiltersFor(sectionPackages), selectedData) {
                    selectedData = it
                }
            },
            filterButtonParams(end = 6)
        )

        row.addView(
            createFilterButton("Validity", selectedValidity) {
                showFilterDialog("Validity", validityFiltersFor(sectionPackages), selectedValidity) {
                    selectedValidity = it
                }
            },
            filterButtonParams(start = 6, end = 6)
        )

        row.addView(
            createFilterButton("Sort", selectedSort.title) {
                showSortDialog()
            },
            filterButtonParams(start = 6)
        )

        parent.addView(row)
    }

    private fun showSortDialog() {
        val options = StoreSort.entries.map { it.title }.toTypedArray()
        val current = StoreSort.entries.indexOf(selectedSort).coerceAtLeast(0)

        AlertDialog.Builder(this)
            .setTitle("Sort")
            .setSingleChoiceItems(options, current) { dialog, which ->
                selectedSort = StoreSort.entries[which]
                dialog.dismiss()
                renderCatalog()
            }
            .show()
    }

    private fun sortPackages(packages: List<MobilePackage>): List<MobilePackage> =
        when (selectedSort) {
            StoreSort.LOWEST_PRICE -> packages.sortedBy { it.sortPriceValue() }
            StoreSort.HIGHEST_DATA -> packages.sortedWith(
                compareByDescending<MobilePackage> { it.dataSortValue() }
                    .thenBy { it.sortPriceValue() }
            )
            StoreSort.LONGEST_VALIDITY -> packages.sortedWith(
                compareByDescending<MobilePackage> { it.validitySortValue() }
                    .thenBy { it.sortPriceValue() }
            )
            StoreSort.BEST_VALUE -> packages.sortedWith(
                compareBy<MobilePackage> { it.valueSortValue() }
                    .thenBy { it.sortPriceValue() }
            )
        }

    private fun MobilePackage.dataSortValue(): Double =
        dataFilterLabel()
            ?.let { dataFilterSortValue(it) }
            ?: 0.0

    private fun MobilePackage.validitySortValue(): Int =
        validityFilterLabel()
            ?.let { validityFilterSortValue(it) }
            ?: 0

    private fun MobilePackage.valueSortValue(): Double {
        val gb = dataSortValue()
        val price = sortPriceValue()
        return if (gb > 0.0 && price != Double.MAX_VALUE) price / gb else Double.MAX_VALUE
    }

    private fun resetUnavailableFilters(sectionPackages: List<MobilePackage>) {
        val dataOptions = dataFiltersFor(sectionPackages).toSet()
        val validityOptions = validityFiltersFor(sectionPackages).toSet()

        if (selectedData !in dataOptions) {
            selectedData = FILTER_ALL
        }
        if (selectedValidity !in validityOptions) {
            selectedValidity = FILTER_ALL
        }
    }

    private fun dataFiltersFor(sectionPackages: List<MobilePackage>): List<String> {
        val values = sectionPackages
            .mapNotNull { it.dataFilterLabel() }
            .distinct()
            .sortedWith(compareBy<String> { dataFilterSortValue(it) }.thenBy { it })

        return listOf(FILTER_ALL) + values
    }

    private fun validityFiltersFor(sectionPackages: List<MobilePackage>): List<String> {
        val values = sectionPackages
            .mapNotNull { it.validityFilterLabel() }
            .distinct()
            .sortedWith(compareBy<String> { validityFilterSortValue(it) }.thenBy { it })

        return listOf(FILTER_ALL) + values
    }

    private fun MobilePackage.dataFilterLabel(): String? {
        val text = searchableText()
        val gb = Regex("""(\d+(?:\.\d+)?)\s*gb""", RegexOption.IGNORE_CASE)
            .find(text)
            ?.groupValues
            ?.getOrNull(1)
            ?.trimEnd('0')
            ?.trimEnd('.')

        return gb?.let { "$it GB" }
    }

    private fun MobilePackage.validityFilterLabel(): String? {
        val text = searchableText()
        val days = Regex("""(\d+)\s*(?:days|day|gün|gun|d)""", RegexOption.IGNORE_CASE)
            .find(text)
            ?.groupValues
            ?.getOrNull(1)

        return days?.let { "$it Days" }
    }

    private fun dataFilterSortValue(value: String): Double =
        Regex("""(\d+(?:\.\d+)?)""")
            .find(value)
            ?.groupValues
            ?.getOrNull(1)
            ?.toDoubleOrNull()
            ?: Double.MAX_VALUE

    private fun validityFilterSortValue(value: String): Int =
        Regex("""(\d+)""")
            .find(value)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?: Int.MAX_VALUE

    private fun createFilterButton(label: String, value: String, onClick: () -> Unit): TextView {
        val selected = value != FILTER_ALL
        val anchor = window.decorView
        val primary = MaterialColors.getColor(anchor, com.google.android.material.R.attr.colorPrimary)
        val onPrimary = MaterialColors.getColor(anchor, com.google.android.material.R.attr.colorOnPrimary)
        val secondaryText = MaterialColors.getColor(anchor, com.google.android.material.R.attr.colorOnSurfaceVariant)

        return TextView(this).apply {
            text = "$label: $value  ▼"
            gravity = Gravity.CENTER
            maxLines = 1
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelMedium)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(if (selected) onPrimary else secondaryText)
            setPadding(dp(10), dp(10), dp(10), dp(10))
            setBackgroundResource(R.drawable.wallet_request_status_badge)
            backgroundTintList = ColorStateList.valueOf(
                if (selected) primary else getColor(R.color.r2w_card)
            )
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
        item.requireViewById<TextView>(R.id.package_title).text = mobilePackage.cleanPackageTitle()
        item.requireViewById<TextView>(R.id.package_country).text = listOfNotNull(
            mobilePackage.providerLabel().takeIf { it.isNotBlank() },
            mobilePackage.country.takeIf { it.isNotBlank() },
            mobilePackage.countryCode?.takeIf { it.isNotBlank() }
        ).joinToString(" · ")
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
        val displayText = providerLabel().lowercase()
        val text = searchableText()

        return when (providerFilter) {
            "Roam2World Turkey" ->
                displayText.contains("roam2world turkey") ||
                    (providerText.contains("traveroam") && (text.contains("turkey") || text.contains(" tr ")))

            "Orange Balkans" ->
                displayText.contains("orange balkans") ||
                    text.contains("e-185") ||
                    text.contains("eo1") ||
                    text.contains("balkan")

            "Orange Europe" ->
                displayText.contains("orange europe") ||
                    text.contains("e-211") ||
                    text.contains("eo2")

            "Vodafone Europe" ->
                displayText.contains("vodafone europe") ||
                    providerText.contains("airhub") ||
                    providerText.contains("airhubapp")

            "Orange Big Data" ->
                displayText.contains("orange big data") ||
                    providerText.contains("flexnet")

            "Orange World" ->
                displayText.contains("orange world") ||
                    providerText.contains("esimcard") ||
                    providerText.contains("esim_card") ||
                    text.contains("global") ||
                    text.contains("world")

            else -> displayText.contains(providerFilter.lowercase()) || providerText.contains(providerFilter.lowercase())
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

    private fun MobilePackage.isOrangeEurope500gb90dPackage(): Boolean {
        val target = "e211esautteo290d60d500gb"
        val compact = listOf(
            id,
            name,
            provider,
            providerLabel(),
            searchableText()
        )
            .joinToString(" ")
            .lowercase()
            .replace(" ", "")
            .replace("-", "")
            .replace("_", "")
            .replace("/", "")
            .replace("（", "(")
            .replace("）", ")")

        return compact.contains(target) ||
            (
                compact.contains("e211") &&
                    compact.contains("500gb") &&
                    compact.contains("90d")
            )
    }

    private fun MobilePackage.isOrangeWorldPackage(): Boolean {
        val text = searchableText()
        val providerText = provider.orEmpty().lowercase()
        val displayProviderText = providerLabel().lowercase()
        val compactText = text
            .replace(" ", "")
            .replace("-", "")
            .replace("_", "")
            .replace("（", "(")
            .replace("）", ")")

        return text.contains("orange world") ||
            (
                text.contains("world") &&
                    (
                        text.contains("orange") ||
                            providerText.contains("orange") ||
                            displayProviderText.contains("orange")
                    )
            )
    }

    private fun MobilePackage.isEuropeBalkansPackage(): Boolean {
        val text = searchableText()
        val compact = text
            .replace(" ", "")
            .replace("-", "")
            .replace("_", "")
            .replace("（", "(")
            .replace("）", ")")

        return text.contains("balkan") ||
            compact.contains("europe41") ||
            compact.contains("europe(41)") ||
            compact.contains("e185") ||
            text.contains("europe-41") ||
            text.contains("europe 41") ||
            text.contains("41 countries")
    }

    private fun MobilePackage.isPhysicalSimPackage(): Boolean {
        val text = searchableText()
        val compact = text
            .replace(" ", "")
            .replace("-", "")
            .replace("_", "")
            .replace("（", "(")
            .replace("）", ")")

        return compact.contains("simcard") ||
            compact.contains("e185sc") ||
            text.contains("physical sim") ||
            text.contains("sim card")
    }

    private fun MobilePackage.isEurope33Package(): Boolean {
        val text = searchableText()
        val compact = text
            .replace(" ", "")
            .replace("-", "")
            .replace("_", "")
            .replace("（", "(")
            .replace("）", ")")

        return compact.contains("europe33") ||
            compact.contains("eu33") ||
            compact.contains("33europe") ||
            compact.contains("e183") ||
            compact.contains("e184") ||
            text.contains("europe 33 countries") ||
            text.contains("europe (33 countries)") ||
            text.contains("europe-33")
    }

    private fun MobilePackage.sortPriceValue(): Double =
        priceFor(userRole)
            .replace(Regex("[^0-9.]"), "")
            .toDoubleOrNull()
            ?: Double.MAX_VALUE

    private fun MobilePackage.matchesStoreSection(section: StoreSection): Boolean {
        if (section == StoreSection.ALL) return true
        val text = searchableText()
        val providerText = provider.orEmpty().lowercase()
        val displayProviderText = providerLabel().lowercase()
        val compactText = text
            .replace(" ", "")
            .replace("-", "")
            .replace("_", "")
            .replace("（", "(")
            .replace("）", ")")

        return when (section) {
            StoreSection.ORANGE_EUROPE ->
                (
                    isOrangeEurope500gb90dPackage() ||
                        text.contains("orange europe") ||
                        text.contains("orange holiday") ||
                        compactText.contains("e211") ||
                        (
                            text.contains("europe") &&
                                compactText.contains("500gb") &&
                                (
                                    compactText.contains("90days") ||
                                        compactText.contains("90day") ||
                                        compactText.contains("90d")
                                )
                        )
                ) &&
                    !isOrangeWorldPackage() &&
                    !isEuropeBalkansPackage() &&
                    !isEurope33Package()

            StoreSection.ORANGE_WORLD ->
                isOrangeWorldPackage() &&
                    !isEurope33Package()

            StoreSection.ORANGE_BALKANS_ESIM ->
                isEuropeBalkansPackage() &&
                    !isPhysicalSimPackage() &&
                    !isEurope33Package()

            StoreSection.ORANGE_BALKANS_SIM ->
                isEuropeBalkansPackage() &&
                    isPhysicalSimPackage() &&
                    !isEurope33Package()

            StoreSection.TURKEY ->
                (text.contains("turkey") || text.contains("turkiye") || text.contains("türkiye")) &&
                    !isOrangeWorldPackage()

            StoreSection.TGT ->
                providerText.contains("tgt") || displayProviderText.contains("tgt")

            StoreSection.VODAFONE ->
                providerText.contains("vodafone") || displayProviderText.contains("vodafone")

            StoreSection.ALL -> true
        }
    }

    private fun MobilePackage.isRecommendedPackage(): Boolean {
        val text = searchableText()
        val price = priceFor(userRole).replace("$", "").trim().toDoubleOrNull() ?: 0.0
        return text.contains("europe") ||
            text.contains("turkey") ||
            text.contains("global") ||
            price in 1.0..25.0
    }

    private fun MobilePackage.isPopularPackage(): Boolean {
        val text = searchableText()
        return listOf("10gb", "20gb", "30gb", "50gb", "100gb", "200gb", "500gb").any { token ->
            text.replace(" ", "").contains(token)
        } || provider.orEmpty().lowercase().let {
            it.contains("tgt") || it.contains("airhub") || it.contains("traveroam")
        }
    }

    private fun MobilePackage.cleanProviderTitle(): String =
        providerLabel()
            .takeIf { it.isNotBlank() }
            ?: country.ifBlank { "Roam2World" }

    private fun MobilePackage.cleanPackageTitle(): String {
        val provider = cleanProviderTitle()
        val data = cleanDataLabel()
        return listOf(provider, data)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .ifBlank { cleanRawName() }
    }

    private fun MobilePackage.cleanPackageSpecs(): String {
        val validity = cleanValidityLabel()
        val coverage = cleanCoverageLabel()
        val type = packageType
            ?.replace("_", " ")
            ?.replace("-", " ")
            ?.lowercase()
            ?.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            .orEmpty()

        return listOf(validity, coverage, type)
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(" · ")
    }

    private fun MobilePackage.cleanRawName(): String =
        name
            .replace("【Esim】", "", ignoreCase = true)
            .replace("【SIMCARD】", "SIM Card", ignoreCase = true)
            .replace("—", " ")
            .replace("–", " ")
            .replace("  ", " ")
            .trim()

    private fun MobilePackage.cleanDataLabel(): String {
        dataAmount?.takeIf { it.isNotBlank() }?.let { return it.trim() }

        val text = listOf(name, description, specs()).joinToString(" ")
        val match = Regex("""(\d+(?:\.\d+)?)\s*GB""", RegexOption.IGNORE_CASE).find(text)
        return match?.value
            ?.uppercase()
            ?.replace("GB", " GB")
            ?.replace(Regex("""\s+"""), " ")
            ?.trim()
            .orEmpty()
    }

    private fun MobilePackage.cleanValidityLabel(): String {
        validity?.takeIf { it.isNotBlank() }?.let { raw ->
            val days = Regex("""\d+""").find(raw)?.value
            if (!days.isNullOrBlank()) return "$days Days"
            return raw.trim()
        }

        val text = listOf(name, description, specs()).joinToString(" ")
        val days = Regex("""(\d+)\s*(?:days|day|d|gün|gun)""", RegexOption.IGNORE_CASE)
            .find(text)
            ?.groupValues
            ?.getOrNull(1)

        return days?.let { "$it Days" }.orEmpty()
    }

    private fun MobilePackage.cleanCoverageLabel(): String {
        coverage?.takeIf { it.isNotBlank() }?.let { return it.trim() }

        return when {
            providerLabel().contains("World", ignoreCase = true) -> "Global"
            providerLabel().contains("Balkans", ignoreCase = true) -> "Europe Balkans"
            providerLabel().contains("Europe", ignoreCase = true) -> "Europe"
            country.isNotBlank() -> country
            else -> ""
        }
    }

    private fun MobilePackage.searchableText(): String =
        listOfNotNull(
            providerLabel(),
            providerLabel(),
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
        
private enum class StoreSort(val title: String) {
    LOWEST_PRICE("Lowest Price"),
    HIGHEST_DATA("Highest Data"),
    LONGEST_VALIDITY("Longest Validity"),
    BEST_VALUE("Best Value")
}

private const val FILTER_ALL = "All"
        private val REGION_FILTERS = listOf("All", "Turkey", "Europe", "Europe Balkans", "Global")
        private val PROVIDER_FILTERS = listOf(
            "All",
            "Roam2World Turkey",
            "Orange Balkans",
            "Orange Europe",
            "Vodafone Europe",
            "Orange Big Data",
            "Orange World"
        )
        private val DATA_FILTERS = listOf(
            "All", "1GB", "2GB", "3GB", "5GB", "10GB", "20GB", "30GB", "50GB",
            "60GB", "100GB", "135GB", "200GB", "300GB", "400GB", "500GB", "Unlimited"
        )
        private val VALIDITY_FILTERS = listOf(
            "All", "1 Day", "3 Days", "5 Days", "7 Days", "10 Days", "15 Days", "30 Days", "60 Days", "90 Days"
        )
    }
}
