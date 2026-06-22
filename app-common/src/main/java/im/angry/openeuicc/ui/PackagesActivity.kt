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
import im.angry.openeuicc.common.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import androidx.compose.foundation.clickable
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay

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

        window.statusBarColor = android.graphics.Color.rgb(244, 246, 250)
        window.navigationBarColor = android.graphics.Color.WHITE
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        }

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

@OptIn(ExperimentalMaterial3Api::class)
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
    val bg = Color(0xFFF4F6FA)
    val blue = Color(0xFF1263F1)
    val text = Color(0xFF0B1533)
    val muted = Color(0xFF64708A)
    val border = Color(0xFFE6EAF0)
    var selectedType by remember { mutableStateOf(FILTER_ALL) }
    var showFilterSheet by remember { mutableStateOf(false) }

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
        selectedType,
        userRole
    ) {
        derivedStateOf {
            allPackages
                .filter { it.matchesStoreSection(selectedSection) && !it.isEurope33Package() }
                .filter { it.matchesQuery(query) }
                .filter { it.matchesData(selectedData) }
                .filter { it.matchesValidity(selectedValidity) }
                .filter { it.matchesPackageType(selectedType) }
                .let { sortPackages(it, selectedSort, userRole) }
        }
    }

    val heroPackage = filteredPackages.firstOrNull()

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = bg) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 118.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    StoreHeader(
                        cartCount = cartCount,
                        onOpenCart = onOpenCart,
                        onRefresh = onRefresh,
                        loading = loading
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Store",
                            color = text,
                            fontSize = 34.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = "Browse and buy eSIM data plans for your business.",
                            color = muted,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(16.dp),
                            color = Color.White,
                            border = BorderStroke(1.dp, border),
                            shadowElevation = 1.dp
                        ) {
                            OutlinedTextField(
                                value = query,
                                onValueChange = onQueryChange,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(58.dp),
                                placeholder = { Text("Search by country or region...", color = muted) },
                                singleLine = true
                            )
                        }

                        Surface(
                            modifier = Modifier
                                .size(58.dp)
                                .clickable {
                                    showFilterSheet = true
                                },
                            shape = RoundedCornerShape(16.dp),
                            color = Color.White,
                            border = BorderStroke(1.dp, border),
                            shadowElevation = 1.dp
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("⇅", color = text, fontSize = 24.sp, fontWeight = FontWeight.Black)
                            }
                        }
                    }

                    StoreHeroCard(
                        mobilePackage = heroPackage,
                        onBannerSelected = { target ->
                            val section = when (target) {
                                "Europe" -> StoreSection.ORANGE_EUROPE
                                "Balkan" -> StoreSection.ORANGE_BALKANS_SIM
                                "Turkey" -> StoreSection.TURKEY
                                else -> StoreSection.ORANGE_EUROPE
                            }
                            onSectionChange(section)
                            onDataChange(FILTER_ALL)
                            onValidityChange(FILTER_ALL)
                        },
                        onBuyNow = {
                            heroPackage?.let(onAddToCart) ?: onOpenCart()
                        }
                    )

                    StoreDots()

                    StoreCategoryTabs(
                        selected = selectedSection,
                        onSelected = {
                            onSectionChange(it)
                            onDataChange(FILTER_ALL)
                            onValidityChange(FILTER_ALL)
                        }
                    )

                    if (selectedData != FILTER_ALL || selectedValidity != FILTER_ALL) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (selectedData != FILTER_ALL) {
                                MiniFilterChip(selectedData, true) { onDataChange(FILTER_ALL) }
                            }
                            if (selectedValidity != FILTER_ALL) {
                                MiniFilterChip(selectedValidity, true) { onValidityChange(FILTER_ALL) }
                            }
                        }
                    }

                    errorMessage?.let {
                        PackagesInfoCard(title = "Notice") {
                            Text(it, color = Color(0xFFDC2626), fontWeight = FontWeight.SemiBold)
                        }
                    }

                    if (loading) {
                        PackagesInfoCard(title = "Loading") {
                            CircularProgressIndicator(color = blue)
                        }
                    }

                    if (!loading && filteredPackages.isEmpty()) {
                        PackagesInfoCard(title = "No packages found") {
                            Text(
                                "Try another category, search term, or clear filters.",
                                color = muted
                            )
                            OutlinedButton(
                                onClick = {
                                    onDataChange(FILTER_ALL)
                                    onValidityChange(FILTER_ALL)
                                    onQueryChange("")
                                },
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Text("Clear filters")
                            }
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        filteredPackages.take(12).forEachIndexed { index, mobilePackage ->
                            PackageCard(
                                mobilePackage = mobilePackage,
                                userRole = userRole,
                                badge = null,
                                onOpen = { onOpenPackage(mobilePackage) },
                                onAddToCart = { onAddToCart(mobilePackage) }
                            )
                        }
                    }

                    StoreBenefitsRow()

                    Spacer(modifier = Modifier.height(10.dp))
                }

                R2wBottomNav(selected = R2wBottomTab.Packages)

                if (showFilterSheet) {
                    ModalBottomSheet(
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                        onDismissRequest = { showFilterSheet = false },
                        containerColor = Color.White,
                        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
                    ) {
                        StoreFilterSheetContent(
                            selectedData = selectedData,
                            selectedValidity = selectedValidity,
                            selectedType = selectedType,
                            onDataChange = onDataChange,
                            onValidityChange = onValidityChange,
                            onTypeChange = { selectedType = it },
                            onClose = { showFilterSheet = false }
                        )
                    }
                }
            }


        }
    }
}

}












@Composable
private fun StoreFilterSheetContent(
    selectedData: String,
    selectedValidity: String,
    selectedType: String,
    onDataChange: (String) -> Unit,
    onValidityChange: (String) -> Unit,
    onTypeChange: (String) -> Unit,
    onClose: () -> Unit
) {
    val gbOptions = listOf(
        FILTER_ALL,
        "5 GB",
        "10 GB",
        "20 GB",
        "30 GB",
        "50 GB",
        "60 GB",
        "100 GB",
        "135 GB",
        "200 GB",
        "300 GB",
        "400 GB",
        "500 GB"
    )

    val dayOptions = listOf(
        FILTER_ALL,
        "30 Days",
        "60 Days",
        "90 Days"
    )

    val typeOptions = listOf(
        FILTER_ALL,
        "eSIM",
        "SIM Card"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 560.dp, max = 760.dp)
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .width(44.dp)
                .height(5.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(Color(0xFFD7DEEA))
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Filter Packages",
                    color = Color(0xFF0B1533),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "Choose data, validity and SIM type",
                    color = Color(0xFF64708A),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Text(
                text = "Done",
                color = Color(0xFF1263F1),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { onClose() }
            )
        }

        StoreFilterSection(
            title = "GB",
            options = gbOptions,
            selected = selectedData,
            onSelected = onDataChange
        )

        StoreFilterSection(
            title = "Days",
            options = dayOptions,
            selected = selectedValidity,
            onSelected = onValidityChange
        )

        StoreFilterSection(
            title = "Type",
            options = typeOptions,
            selected = selectedType,
            onSelected = onTypeChange
        )

        Spacer(modifier = Modifier.height(8.dp))
    }
}





@Composable
private fun StoreFilterRow(
    title: String,
    options: List<String>,
    selected: String,
    onSelected: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = title,
            color = Color(0xFF0B1533),
            fontSize = 15.sp,
            fontWeight = FontWeight.Black
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { option ->
                Surface(
                    modifier = Modifier.clickable { onSelected(option) },
                    shape = RoundedCornerShape(999.dp),
                    color = if (option == selected) Color(0xFFEAF3FF) else Color(0xFFF8FAFC),
                    border = BorderStroke(
                        1.dp,
                        if (option == selected) Color(0xFF1263F1) else Color(0xFFE6EAF0)
                    )
                ) {
                    Text(
                        text = option,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        color = if (option == selected) Color(0xFF1263F1) else Color(0xFF64708A),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StoreFilterSection(
    title: String,
    options: List<String>,
    selected: String,
    onSelected: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = title,
            color = Color(0xFF0B1533),
            fontSize = 15.sp,
            fontWeight = FontWeight.ExtraBold
        )

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { option ->
                val active = option == selected
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (active) Color(0xFF1263F1) else Color(0xFFF2F5FA))
                        .border(
                            1.dp,
                            if (active) Color(0xFF1263F1) else Color(0xFFE1E7F0),
                            RoundedCornerShape(999.dp)
                        )
                        .clickable { onSelected(option) }
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = option,
                        color = if (active) Color.White else Color(0xFF34405A),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}



private fun MobilePackage.matchesPackageType(type: String): Boolean {
    if (type == FILTER_ALL) return true

    val haystack = listOf(
        cleanPackageTitle(),
        cleanCardSummary(),
        cleanCardSpecs(),
        cardValidityOnly()
    ).joinToString(" ").lowercase(Locale.getDefault())

    return when (type) {
        "SIM Card" -> haystack.contains("sim card") || haystack.contains("simcard")
        "eSIM" -> haystack.contains("esim") || (!haystack.contains("sim card") && !haystack.contains("simcard"))
        else -> true
    }
}


@Composable
private fun StoreHeader(
    cartCount: Int,
    onOpenCart: () -> Unit,
    onRefresh: () -> Unit,
    loading: Boolean
) {
    val text = Color(0xFF0B1533)
    val blue = Color(0xFF1263F1)
    val border = Color(0xFFE6EAF0)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier
                .size(44.dp)
                .clickable(onClick = onOpenCart),
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            border = BorderStroke(1.dp, border),
            shadowElevation = 1.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Image(
                    painter = painterResource(id = R.drawable.r2w_ic_cart),
                    contentDescription = "Cart",
                    modifier = Modifier.size(23.dp)
                )

                if (cartCount > 0) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(17.dp),
                        shape = RoundedCornerShape(50),
                        color = Color(0xFFE11D48)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = cartCount.coerceAtMost(9).toString(),
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StoreHeroCard(
    mobilePackage: MobilePackage?,
    onBannerSelected: (String) -> Unit,
    onBuyNow: () -> Unit
) {
    var bannerIndex by remember { mutableStateOf(0) }

    val banners = listOf(
        im.angry.openeuicc.common.R.drawable.store_banner to "Europe",
        im.angry.openeuicc.common.R.drawable.store_banner_2 to "Balkan",
        im.angry.openeuicc.common.R.drawable.store_banner_3 to "Turkey"
    )

    LaunchedEffect(Unit) {
        while (true) {
            delay(3000)
            bannerIndex = (bannerIndex + 1) % banners.size
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(178.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(Color.White)
            .border(1.dp, Color(0xFFE6EAF0), RoundedCornerShape(26.dp))
            .clickable {
                onBannerSelected(banners[bannerIndex].second)
            }
    ) {
        Image(
            painter = painterResource(id = banners[bannerIndex].first),
            contentDescription = "Store Banner",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            banners.forEachIndexed { index, _ ->
                Box(
                    modifier = Modifier
                        .width(if (index == bannerIndex) 18.dp else 7.dp)
                        .height(7.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(
                            if (index == bannerIndex) Color(0xFF1263F1)
                            else Color.White.copy(alpha = 0.78f)
                        )
                )
            }
        }
    }
}





@Composable
private fun StoreDots() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        repeat(4) { index ->
            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .size(if (index == 0) 10.dp else 8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (index == 0) Color(0xFF1263F1) else Color(0xFFD5DCE8))
            )
        }
    }
}

@Composable
private fun StoreCategoryTabs(
    selected: StoreSection,
    onSelected: (StoreSection) -> Unit
) {
    val tabs = listOf(
        "Europe" to StoreSection.ORANGE_EUROPE,
        "World" to StoreSection.ORANGE_WORLD,
        "Balkans" to StoreSection.ORANGE_BALKANS_SIM,
        "Turkey" to StoreSection.TURKEY,
        "Vodafone" to StoreSection.VODAFONE
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        tabs.forEach { (label, section) ->
            val isSelected = section == selected

            Column(
                modifier = Modifier
                    .clickable { onSelected(section) }
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = label,
                    color = if (isSelected) Color(0xFF1263F1) else Color(0xFF6B7280),
                    fontSize = 14.sp,
                    fontWeight = if (isSelected) FontWeight.Black else FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .width(58.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(50))
                        .background(if (isSelected) Color(0xFF1263F1) else Color.Transparent)
                )
            }
        }
    }
}


@Composable
private fun MiniFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(999.dp),
        color = if (selected) Color(0xFFEAF3FF) else Color.White,
        border = BorderStroke(1.dp, if (selected) Color(0xFFBFD6FF) else Color(0xFFE6EAF0))
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            color = if (selected) Color(0xFF1263F1) else Color(0xFF64708A),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
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
    val blue = Color(0xFF1263F1)
    val text = Color(0xFF0B1533)
    val muted = Color(0xFF52607A)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE6EAF0)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(16.dp),
                color = Color.Transparent
            ) {
                Box(contentAlignment = Alignment.Center) {
                    PackageFlagImage(mobilePackage = mobilePackage)
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = mobilePackage.cleanPackageTitle(),
                        color = text,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    badge?.takeIf { it.isNotBlank() }?.let {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = Color(0xFFE7FFF4)
                        ) {
                            Text(
                                text = it,
                                color = Color(0xFF08A365),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                maxLines = 1
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = mobilePackage.cleanCardSummary().ifBlank {
                        mobilePackage.cleanCardSpecs().ifBlank { "Instant eSIM delivery" }
                    },
                    color = muted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(
                modifier = Modifier.width(86.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = r2wMoney(mobilePackage.priceFor(userRole)),
                    color = blue,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "›",
                    color = Color(0xFF263550),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Light
                )
            }
        }
    }
}

@Composable
private fun StoreBenefitsRow() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Color(0xFFE6EAF0)),
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BenefitItem("Secure", "100% secure", Modifier.weight(1f), Icons.Default.Security)
            BenefitItem("Support", "24/7 help", Modifier.weight(1f), Icons.Default.SupportAgent)
            BenefitItem("Delivery", "QR seconds", Modifier.weight(1f), Icons.Default.QrCode2)
        }
    }
}

@Composable
private fun BenefitItem(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    icon: ImageVector
) {
    Surface(
        modifier = modifier.height(104.dp),
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFFF8FAFC),
        border = BorderStroke(1.dp, Color(0xFFE8EEF6))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(Color(0xFFEAF2FF)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = Color(0xFF1263F1),
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                title,
                color = Color(0xFF0B1533),
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                subtitle,
                color = Color(0xFF64708A),
                fontSize = 9.sp,
                maxLines = 1
            )
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
        border = BorderStroke(1.dp, Color(0xFFE6EAF0)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(title, color = Color(0xFF0B1533), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            HorizontalDivider(color = Color(0xFFE6EAF0))
            content()
        }
    }
}



@Composable
private fun PackageFlagImage(mobilePackage: MobilePackage) {
    val title = mobilePackage.cleanPackageTitle().lowercase(Locale.getDefault())
    val summary = mobilePackage.cleanCardSummary().lowercase(Locale.getDefault())
    val specs = mobilePackage.cleanCardSpecs().lowercase(Locale.getDefault())
    val haystack = "$title $summary $specs"

    if (haystack.contains("balkan")) {
        Image(
            painter = painterResource(id = im.angry.openeuicc.common.R.drawable.r2w_balkan_flag),
            contentDescription = null,
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(999.dp)),
            contentScale = ContentScale.Crop
        )
        return
    }

    val url = mobilePackage.onlineCountryFlagUrl()

    if (url == null) {
        Image(
            painter = painterResource(id = im.angry.openeuicc.common.R.drawable.r2w_europe_globe),
            contentDescription = null,
            modifier = Modifier.size(42.dp),
            contentScale = ContentScale.Crop
        )
    } else {
        AsyncImage(
            model = url,
            contentDescription = null,
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(999.dp)),
            contentScale = ContentScale.Crop
        )
    }
}


private fun MobilePackage.onlineCountryFlagUrl(): String {
    val text = listOf(
        cleanPackageTitle(),
        cleanCardSummary(),
        cleanCardSpecs(),
        cardValidityOnly()
    ).joinToString(" ").lowercase(Locale.getDefault())

    val code = when {
        text.contains("orange europe") -> "eu"
        text.contains("europe") -> "eu"
        text.contains("eu ") || text.contains(" eu") -> "eu"

        text.contains("turkey") || text.contains("türkiye") -> "tr"
        text.contains("usa") || text.contains("united states") || text.contains("america") -> "us"
        text.contains("japan") -> "jp"
        text.contains("uae") || text.contains("emirates") -> "ae"
        text.contains("germany") -> "de"
        text.contains("france") -> "fr"
        text.contains("italy") -> "it"
        text.contains("spain") -> "es"
        text.contains("netherlands") -> "nl"
        text.contains("belgium") -> "be"
        text.contains("austria") -> "at"
        text.contains("switzerland") -> "ch"
        text.contains("united kingdom") || text.contains("uk") || text.contains("england") -> "gb"
        text.contains("canada") -> "ca"
        text.contains("australia") -> "au"
        text.contains("brazil") -> "br"
        text.contains("morocco") -> "ma"
        text.contains("egypt") -> "eg"
        text.contains("tunisia") -> "tn"
        text.contains("qatar") -> "qa"
        text.contains("saudi") -> "sa"

        text.contains("world") || text.contains("global") -> "un"
        else -> "eu"
    }

    return "https://flagcdn.com/w160/$code.png"
}


private fun packageEmoji(mobilePackage: MobilePackage): String {
    val title = mobilePackage.cleanPackageTitle().lowercase(Locale.getDefault())
    return when {
        title.contains("usa") || title.contains("united states") -> "🇺🇸"
        title.contains("turkey") || title.contains("türkiye") -> "🇹🇷"
        title.contains("japan") -> "🇯🇵"
        title.contains("uae") || title.contains("emirates") -> "🇦🇪"
        title.contains("europe") -> "🌐"
        title.contains("world") || title.contains("global") -> "🌍"
        title.contains("balkan") -> "🧭"
        else -> "🌐"
    }
}

private fun packageIconBackground(mobilePackage: MobilePackage): Color {
    val title = mobilePackage.cleanPackageTitle().lowercase(Locale.getDefault())
    return when {
        title.contains("turkey") || title.contains("türkiye") -> Color(0xFFFFEEF1)
        title.contains("usa") || title.contains("united states") -> Color(0xFFEAF3FF)
        title.contains("japan") -> Color(0xFFF1F3F7)
        title.contains("uae") || title.contains("emirates") -> Color(0xFFEFFFF6)
        else -> Color(0xFFEFFFF6)
    }
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
