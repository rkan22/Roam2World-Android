package im.angry.openeuicc.ui.compose.screens

import androidx.compose.runtime.Composable
import im.angry.openeuicc.auth.MobilePackage
import im.angry.openeuicc.auth.MobilePackageCatalog

val StoreProviderTabs = listOf(
    "Roam2World Turkey",
    "Orange Europe",
    "Orange Balkans",
    "Orange World",
    "Vodafone Europe"
)

@Composable
fun CompactStoreScreen(
    loading: Boolean,
    catalog: MobilePackageCatalog,
    userRole: String?,
    errorMessage: String?,
    query: String,
    selectedProvider: String,
    onQueryChange: (String) -> Unit,
    onProviderChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onOpenPackage: (MobilePackage) -> Unit,
    onDashboard: () -> Unit,
    onEsims: () -> Unit,
    onMore: () -> Unit
) {
    CompactStoreScreenV2(
        loading = loading,
        catalog = catalog,
        userRole = userRole,
        errorMessage = errorMessage,
        query = query,
        selectedProvider = selectedProvider,
        onQueryChange = onQueryChange,
        onProviderChange = onProviderChange,
        onRefresh = onRefresh,
        onOpenPackage = onOpenPackage,
        onDashboard = onDashboard,
        onEsims = onEsims,
        onMore = onMore
    )
}
