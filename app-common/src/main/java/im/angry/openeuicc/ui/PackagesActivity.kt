package im.angry.openeuicc.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import im.angry.openeuicc.auth.AuthSession
import im.angry.openeuicc.auth.AuthTokenStore
import im.angry.openeuicc.auth.JwtUtils
import im.angry.openeuicc.auth.MobilePackage
import im.angry.openeuicc.auth.MobilePackageCatalog
import im.angry.openeuicc.auth.Roam2WorldAuthApi
import im.angry.openeuicc.common.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private enum class StoreSection(val title: String) {
    ORANGE_EUROPE("Orange Europe"),
    ORANGE_WORLD("Orange World"),
    ORANGE_BALKANS_ESIM("Orange Balkans eSIM"),
    ORANGE_BALKANS_SIM("Orange Balkans SIM"),
    TURKEY("Turkey"),
    VODAFONE("Vodafone Europe")
}

private enum class StoreSort(val title: String) {
    LOWEST_PRICE("Lowest Price"),
    HIGHEST_DATA("Highest Data"),
    LONGEST_VALIDITY("Longest Validity"),
    BEST_VALUE("Best Value")
}

private val STORE_SECTIONS = listOf(
    StoreSection.ORANGE_EUROPE,
    StoreSection.ORANGE_WORLD,
    StoreSection.ORANGE_BALKANS_SIM,
    StoreSection.ORANGE_BALKANS_ESIM,
    StoreSection.TURKEY,
    StoreSection.VODAFONE
)

private const val FILTER_ALL = "All"

class PackagesActivity : ComponentActivity() {
    private val tokenStore by lazy { AuthTokenStore(this) }
    private val authApi by lazy { Roam2WorldAuthApi(BuildConfig.ROAM2WORLD_API_BASE_URL) }

    private var loading by mutableStateOf(false)
    private var catalog by mutableStateOf(MobilePackageCatalog(emptyList(), emptyList()))
    private var userRole by mutableStateOf<String?>(null)
    private var errorMessage by mutableStateOf<String?>(null)
    private var query by mutableStateOf("")
    private var selectedSection by mutableStateOf(StoreSection.ORANGE_EUROPE)
    private var selectedData by mutableStateOf(FILTER_ALL)
    private var selectedValidity by mutableStateOf(FILTER_ALL)
    private var selectedSort by mutableStateOf(StoreSort.LOWEST_PRICE)
    private var cartCount by mutableStateOf(ShoppingCartStore.count())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            PackagesScreen(
                loading = loading,
                catalog = catalog,
                userRole = userRole,
                errorMessage = errorMessage,
                query = query,
                selectedSection = selectedSection,
                selectedData = selectedData,
                selectedValidity = selectedValidity,
                selectedSort = selectedSort,
                cartCount = cartCount,
                onQueryChange = { query = it },
                onSectionChange = {
                    selectedSection = it
                    selectedData = FILTER_ALL
                    selectedValidity = FILTER_ALL
                },
                onDataChange = { selectedData = it },
                onValidityChange = { selectedValidity = it },
                onSortChange = { selectedSort = it },
                onRefresh = { loadPackages() },
                onOpenCart = { startActivity(Intent(this, ShoppingCartActivity::class.java)) },
                onOpenPackage = { mobilePackage ->
                    startActivity(PackageDetailActivity.createIntent(this, mobilePackage, userRole))
                },
                onAddToCart = { mobilePackage -> addToCart(mobilePackage) },
                onDashboard = { startActivity(Intent(this, DashboardActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)) },
                onWallet = { startActivity(Intent(this, WalletActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)) },
                onEsims = { startActivity(Intent(this, MobileEsimsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)) },
                onMore = { startActivity(Intent(this, MoreActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)) },
                onLogout = { logout() }
            )
        }

        loadPackages()
    }

    override fun onResume() {
        super.onResume()
        cartCount = ShoppingCartStore.count()
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

            val result = runCatching {
                authApi.packages(session)
            }

            loading = false

            result
                .onSuccess {
                    catalog = it
                    errorMessage = if (it.featuredPackages.isEmpty() && it.packages.isEmpty()) {
                        "No live packages available. Please configure packages in Roam2World backend."
                    } else {
                        null
                    }
                }
                .onFailure {
                    catalog = MobilePackageCatalog(emptyList(), emptyList())
                    errorMessage = it.message ?: "Packages could not be loaded"
                }
        }
    }

    private fun addToCart(mobilePackage: MobilePackage) {
        val title = mobilePackage.cleanPackageTitle()
        val subtitle = mobilePackage.cardValidityOnly().ifBlank { "eSIM data plan" }
        val price = r2wMoney(mobilePackage.priceFor(userRole))
        val providerName = mobilePackage.visibleProviderName(selectedSection)

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

    private fun logout() {
        lifecycleScope.launch {
            val session = withContext(Dispatchers.IO) {
                tokenStore.getSession().also { tokenStore.clear() }
            }
            session?.let {
                runCatching { authApi.logout(it) }
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
}

@Composable
private fun PackagesScreen(
    loading: Boolean,
    catalog: MobilePackageCatalog,
    userRole: String?,
    errorMessage: String?,
    query: String,
    selectedSection: StoreSection,
    selectedData: String,
    selectedValidity: String,
    selectedSort: StoreSort,
    cartCount: Int,
    onQueryChange: (String) -> Unit,
    onSectionChange: (StoreSection) -> Unit,
    onDataChange: (String) -> Unit,
    onValidityChange: (String) -> Unit,
    onSortChange: (StoreSort) -> Unit,
    onRefresh: () -> Unit,
    onOpenCart: () -> Unit,
    onOpenPackage: (MobilePackage) -> Unit,
    onAddToCart: (MobilePackage) -> Unit,
    onDashboard: () -> Unit,
    onWallet: () -> Unit,
    onEsims: () -> Unit,
    onMore: () -> Unit,
    onLogout: () -> Unit
) {
    val orange = Color(0xFFFF7900)
    val bg = Color(0xFFF7F7FA)

    val allPackages = remember(catalog) {
        (catalog.featuredPackages + catalog.packages).distinctBy {
            listOf(it.id, it.name, it.provider, it.priceFor(userRole)).joinToString("|")
        }
    }

    val sectionPackagesBase = remember(allPackages, selectedSection) {
        allPackages.filter { it.matchesStoreSection(selectedSection) && !it.isEurope33Package() }
    }

    val dataOptions = remember(sectionPackagesBase) {
        listOf(FILTER_ALL) + sectionPackagesBase
            .mapNotNull { it.dataFilterLabel() }
            .distinct()
            .sortedWith(compareBy<String> { dataFilterSortValue(it) }.thenBy { it })
    }

    val validityOptions = remember(sectionPackagesBase) {
        listOf(FILTER_ALL) + sectionPackagesBase
            .mapNotNull { it.validityFilterLabel() }
            .distinct()
            .sortedWith(compareBy<String> { validityFilterSortValue(it) }.thenBy { it })
    }

    val filteredPackages by remember(
        allPackages,
        query,
        selectedSection,
        selectedData,
        selectedValidity,
        selectedSort,
        userRole
    ) {
        derivedStateOf {
            allPackages
                .filter { it.matchesStoreSection(selectedSection) && !it.isEurope33Package() }
                .filter { it.matchesQuery(query) }
                .filter { it.matchesData(selectedData) }
                .filter { it.matchesValidity(selectedValidity) }
                .let { sortPackages(it, selectedSort, userRole) }
        }
    }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = bg) {
            Column(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(32.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF17181C))
                    ) {
                        Column(
                            modifier = Modifier.padding(22.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = "Roam2World",
                                        color = orange,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Packages",
                                        color = Color.White,
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontWeight = FontWeight.Black
                                    )
                                    Text(
                                        text = "${filteredPackages.size} shown • ${allPackages.size} total",
                                        color = Color.White.copy(alpha = 0.72f),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = if (loading) "Loading" else "Refresh",
                                        color = orange,
                                        modifier = Modifier.clickable(enabled = !loading, onClick = onRefresh),
                                        fontWeight = FontWeight.Black
                                    )
                                    Text(
                                        text = "Cart $cartCount",
                                        color = Color.White,
                                        modifier = Modifier
                                            .padding(top = 10.dp)
                                            .clickable(onClick = onOpenCart),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Button(
                                onClick = onOpenCart,
                                colors = ButtonDefaults.buttonColors(containerColor = orange),
                                shape = RoundedCornerShape(18.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Open Cart ($cartCount)", fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    OutlinedTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Search country, provider, GB, validity") },
                        singleLine = true
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        STORE_SECTIONS.forEach { section ->
                            AssistChip(
                                onClick = { onSectionChange(section) },
                                label = {
                                    Text(
                                        section.title,
                                        fontWeight = if (section == selectedSection) FontWeight.Black else FontWeight.SemiBold
                                    )
                                }
                            )
                        }
                    }

                    FilterScroller(
                        title = "Data",
                        options = dataOptions,
                        selected = selectedData,
                        onSelected = onDataChange
                    )

                    FilterScroller(
                        title = "Validity",
                        options = validityOptions,
                        selected = selectedValidity,
                        onSelected = onValidityChange
                    )

                    FilterScroller(
                        title = "Sort",
                        options = StoreSort.entries.map { it.title },
                        selected = selectedSort.title,
                        onSelected = { selected ->
                            StoreSort.entries.firstOrNull { it.title == selected }?.let(onSortChange)
                        }
                    )

                    errorMessage?.let {
                        PackagesInfoCard(title = "Notice") {
                            Text(it, color = Color(0xFFDC2626), fontWeight = FontWeight.SemiBold)
                        }
                    }

                    if (loading) {
                        PackagesInfoCard(title = "Loading") {
                            CircularProgressIndicator()
                        }
                    }

                    if (!loading && filteredPackages.isEmpty()) {
                        PackagesInfoCard(title = "No packages found") {
                            Text(
                                "Try another category, search term, or clear filters.",
                                color = Color(0xFF6B7280)
                            )
                            OutlinedButton(
                                onClick = {
                                    onDataChange(FILTER_ALL)
                                    onValidityChange(FILTER_ALL)
                                },
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Text("Clear filters")
                            }
                        }
                    }

                    Text(
                        text = selectedSection.title,
                        color = Color(0xFF17181C),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black
                    )

                    filteredPackages.forEach { mobilePackage ->
                        PackageCard(
                            mobilePackage = mobilePackage,
                            userRole = userRole,
                            badge = packageBadge(mobilePackage, filteredPackages, userRole),
                            onOpen = { onOpenPackage(mobilePackage) },
                            onAddToCart = { onAddToCart(mobilePackage) }
                        )
                    }

                    OutlinedButton(
                        onClick = onLogout,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Text("Logout", color = Color(0xFFDC2626), fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                }

                PackagesBottomNav(
                    onDashboard = onDashboard,
                    onPackages = {},
                    onWallet = onWallet,
                    onEsims = onEsims,
                    onMore = onMore
                )
            }
        }
    }
}

@Composable
private fun FilterScroller(
    title: String,
    options: List<String>,
    selected: String,
    onSelected: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color = Color(0xFF6B7280), fontWeight = FontWeight.Bold)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { option ->
                AssistChip(
                    onClick = { onSelected(option) },
                    label = {
                        Text(
                            option,
                            fontWeight = if (option == selected) FontWeight.Black else FontWeight.SemiBold
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun PackageCard(
    mobilePackage: MobilePackage,
    userRole: String?,
    badge: String?,
    onOpen: () -> Unit,
    onAddToCart: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = mobilePackage.cleanPackageTitle(),
                        color = Color(0xFF17181C),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = mobilePackage.cleanCardSummary(),
                        color = Color(0xFF6B7280),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = mobilePackage.cleanCardSpecs().ifBlank { mobilePackage.cardValidityOnly().ifBlank { "Instant eSIM delivery" } },
                        color = Color(0xFF6B7280),
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    badge?.let {
                        Text(
                            text = it,
                            color = Color(0xFFFF7900),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Black
                        )
                    }
                    Text(
                        text = r2wMoney(mobilePackage.priceFor(userRole)),
                        color = Color(0xFF17181C),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black
                    )
                }
            }

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onOpen,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Details")
                }

                Button(
                    onClick = onAddToCart,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF7900)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Add", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun PackagesInfoCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(title, color = Color(0xFF17181C), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            HorizontalDivider()
            content()
        }
    }
}

@Composable
private fun PackagesBottomNav(
    onDashboard: () -> Unit,
    onPackages: () -> Unit,
    onWallet: () -> Unit,
    onEsims: () -> Unit,
    onMore: () -> Unit
) {
    Surface(shadowElevation = 8.dp, color = Color.White) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            PackagesBottomNavText("Home", onDashboard)
            PackagesBottomNavText("Packages", onPackages, selected = true)
            PackagesBottomNavText("Wallet", onWallet)
            PackagesBottomNavText("eSIMs", onEsims)
            PackagesBottomNavText("More", onMore)
        }
    }
}

@Composable
private fun PackagesBottomNavText(
    text: String,
    onClick: () -> Unit,
    selected: Boolean = false
) {
    Text(
        text = text,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(8.dp),
        color = if (selected) Color(0xFFFF7900) else Color(0xFF6B7280),
        fontWeight = if (selected) FontWeight.Black else FontWeight.SemiBold,
        style = MaterialTheme.typography.labelMedium
    )
}

private fun sortPackages(
    packages: List<MobilePackage>,
    sort: StoreSort,
    userRole: String?
): List<MobilePackage> =
    when (sort) {
        StoreSort.LOWEST_PRICE -> packages.sortedBy { it.sortPriceValue(userRole) }
        StoreSort.HIGHEST_DATA -> packages.sortedWith(
            compareByDescending<MobilePackage> { it.dataSortValue() }
                .thenBy { it.sortPriceValue(userRole) }
        )
        StoreSort.LONGEST_VALIDITY -> packages.sortedWith(
            compareByDescending<MobilePackage> { it.validitySortValue() }
                .thenBy { it.sortPriceValue(userRole) }
        )
        StoreSort.BEST_VALUE -> packages.sortedWith(
            compareBy<MobilePackage> { it.valueSortValue(userRole) }
                .thenBy { it.sortPriceValue(userRole) }
        )
    }

private fun packageBadge(
    mobilePackage: MobilePackage,
    packages: List<MobilePackage>,
    userRole: String?
): String? {
    if (packages.size < 2) return null

    val cheapest = packages.minByOrNull { it.sortPriceValue(userRole) }
    val mostData = packages.maxByOrNull { it.dataSortValue() }
    val longestValidity = packages.maxByOrNull { it.validitySortValue() }
    val bestValue = packages
        .filter { it.valueSortValue(userRole) != Double.MAX_VALUE }
        .minByOrNull { it.valueSortValue(userRole) }

    return when {
        bestValue === mobilePackage -> "Best Value"
        cheapest === mobilePackage -> "Cheapest"
        mostData === mobilePackage && mobilePackage.dataSortValue() > 0.0 -> "Most Data"
        longestValidity === mobilePackage && mobilePackage.validitySortValue() > 0 -> "Longest Validity"
        else -> null
    }
}

private fun MobilePackage.sortPriceValue(userRole: String?): Double =
    priceFor(userRole)
        .replace(Regex("[^0-9.]"), "")
        .toDoubleOrNull()
        ?: Double.MAX_VALUE

private fun MobilePackage.dataSortValue(): Double =
    dataFilterLabel()?.let { dataFilterSortValue(it) } ?: 0.0

private fun MobilePackage.validitySortValue(): Int =
    validityFilterLabel()?.let { validityFilterSortValue(it) } ?: 0

private fun MobilePackage.valueSortValue(userRole: String?): Double {
    val gb = dataSortValue()
    val price = sortPriceValue(userRole)
    return if (gb > 0.0 && price != Double.MAX_VALUE) price / gb else Double.MAX_VALUE
}

private fun MobilePackage.matchesQuery(query: String): Boolean {
    val cleanQuery = query.trim().lowercase(Locale.ROOT)
    if (cleanQuery.isBlank()) return true
    return searchableText().contains(cleanQuery)
}

private fun MobilePackage.matchesData(dataFilter: String): Boolean {
    if (dataFilter == FILTER_ALL) return true
    val text = searchableText()
    if (dataFilter.equals("Unlimited", ignoreCase = true)) {
        return text.contains("unlimited") || text.contains("_ul_")
    }
    val gb = dataFilter.removeSuffix("GB").trim().toIntOrNull() ?: return true
    val compact = text.replace(" ", "")
    return compact.contains("${gb}gb") || text.contains("${gb * 1000} mb") || text.contains("${gb * 1024} mb")
}

private fun MobilePackage.matchesValidity(validityFilter: String): Boolean {
    if (validityFilter == FILTER_ALL) return true
    val days = validityFilter.substringBefore(" ").toIntOrNull() ?: return true
    val compact = searchableText().replace(" ", "")
    return compact.contains("${days}days") || compact.contains("${days}day") || compact.contains("${days}d")
}

private fun MobilePackage.matchesStoreSection(section: StoreSection): Boolean {
    val text = searchableText()
    val providerText = provider.orEmpty().lowercase(Locale.ROOT)
    val displayProviderText = providerLabel().lowercase(Locale.ROOT)
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
                            (compactText.contains("90days") || compactText.contains("90day") || compactText.contains("90d"))
                    )
            ) &&
                !isOrangeWorldPackage() &&
                !isEuropeBalkansPackage() &&
                !isEurope33Package()

        StoreSection.ORANGE_WORLD ->
            isOrangeWorldPackage() && !isEurope33Package()

        StoreSection.ORANGE_BALKANS_ESIM ->
            isEuropeBalkansPackage() && !isPhysicalSimPackage() && !isEurope33Package()

        StoreSection.ORANGE_BALKANS_SIM ->
            isEuropeBalkansPackage() && isPhysicalSimPackage() && !isEurope33Package()

        StoreSection.TURKEY ->
            (text.contains("turkey") || text.contains("turkiye") || text.contains("türkiye")) && !isOrangeWorldPackage()

        StoreSection.VODAFONE ->
            providerText.contains("vodafone") || displayProviderText.contains("vodafone") || providerText.contains("airhub")
    }
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
        .lowercase(Locale.ROOT)
        .replace(" ", "")
        .replace("-", "")
        .replace("_", "")
        .replace("/", "")
        .replace("（", "(")
        .replace("）", ")")

    return compact.contains(target) ||
        (compact.contains("e211") && compact.contains("500gb") && compact.contains("90d"))
}

private fun MobilePackage.isOrangeWorldPackage(): Boolean {
    val text = searchableText()
    val providerText = provider.orEmpty().lowercase(Locale.ROOT)
    val displayProviderText = providerLabel().lowercase(Locale.ROOT)

    return text.contains("orange world") ||
        (
            text.contains("world") &&
                (text.contains("orange") || providerText.contains("orange") || displayProviderText.contains("orange"))
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

private fun MobilePackage.dataFilterLabel(): String? {
    val gb = Regex("""(\d+(?:\.\d+)?)\s*gb""", RegexOption.IGNORE_CASE)
        .find(searchableText())
        ?.groupValues
        ?.getOrNull(1)
        ?.trimEnd('0')
        ?.trimEnd('.')

    return gb?.let { "$it GB" }
}

private fun MobilePackage.validityFilterLabel(): String? {
    val days = Regex("""(\d+)\s*(?:days|day|gün|gun|d)""", RegexOption.IGNORE_CASE)
        .find(searchableText())
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

private fun MobilePackage.cardValidityOnly(): String {
    val parts = specs()
        .split("•", "·", "|")
        .map { it.trim() }
        .filter { it.isNotBlank() }

    return parts.firstOrNull { it.contains("day", ignoreCase = true) || it.contains("days", ignoreCase = true) }
        ?: parts.lastOrNull()
        ?: cleanValidityLabel()
}

private fun MobilePackage.cleanProviderTitle(): String =
    providerLabel()
        .takeIf { it.isNotBlank() }
        ?: country.ifBlank { "Roam2World" }

private fun MobilePackage.cleanPackageTitle(): String {
    val providerName = cleanProviderTitle()
    val data = cleanDataLabel()
    return listOf(providerName, data)
        .filter { it.isNotBlank() }
        .joinToString(" ")
        .ifBlank { cleanRawName() }
}

private fun MobilePackage.cleanCardSummary(): String =
    listOf(cleanDataLabel(), cleanValidityLabel())
        .filter { it.isNotBlank() }
        .joinToString(" • ")
        .ifBlank { "Instant eSIM delivery" }

private fun MobilePackage.cleanCardSpecs(): String =
    listOf(
        cleanCoverageLabel().takeIf { it.isNotBlank() },
        packageType
            ?.replace("_", " ")
            ?.replace("-", " ")
            ?.lowercase(Locale.ROOT)
            ?.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
            ?.takeIf { it.isNotBlank() }
    )
        .filterNotNull()
        .distinct()
        .joinToString(" · ")

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
        ?.uppercase(Locale.ROOT)
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

private fun MobilePackage.visibleProviderName(section: StoreSection): String {
    val providerText = listOfNotNull(
        section.name,
        provider,
        providerLabel(),
        name,
        coverage,
        country,
        countryCode
    ).joinToString(" ").lowercase(Locale.ROOT)

    return when {
        providerText.contains("vodafone") -> "Vodafone"
        providerText.contains("orange") -> "Orange"
        providerText.contains("airalo") -> "Airalo"
        else -> providerLabel()
            .ifBlank { provider.orEmpty() }
            .ifBlank { "eSIM" }
    }
}

private fun MobilePackage.searchableText(): String =
    listOfNotNull(
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
    ).joinToString(" ").lowercase(Locale.ROOT)
