package im.angry.openeuicc.ui.compose.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Dataset
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import im.angry.openeuicc.auth.MobilePackage
import im.angry.openeuicc.auth.MobilePackageCatalog
import java.text.NumberFormat
import java.util.Locale

private val StoreBlue = Color(0xFF1263F1)
private val StoreText = Color(0xFF111827)
private val StoreMuted = Color(0xFF6B7280)
private val StoreBorder = Color(0xFFE5E7EB)
private val StoreBg = Color(0xFFF8FAFF)
private val StoreOrange = Color(0xFFFF7900)
private val StoreRed = Color(0xFFE60000)

val StoreProviderTabs = listOf("Roam2World Turkey", "Orange Europe", "Orange Balkans", "Orange World", "Vodafone Europe")

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
    val packages = remember(catalog, userRole, query, selectedProvider) {
        (catalog.featuredPackages + catalog.packages)
            .filter { it.isVisibleFor(userRole) }
            .distinctBy { listOf(it.id, it.name, it.provider, it.priceFor(userRole)).joinToString("|") }
    }
    val filtered by remember(packages, query, selectedProvider) {
        derivedStateOf {
            packages
                .filter { it.matches(query) }
                .filter { it.matchesTab(selectedProvider) }
                .sortedBy { it.priceNumber(userRole) }
        }
    }

    Scaffold(containerColor = StoreBg) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item { StoreHeader(loading, onRefresh) }
                item { SearchBox(query, onQueryChange) }
                item { FilterRow() }
                item { ProviderTabs(selectedProvider, onProviderChange) }
                if (!errorMessage.isNullOrBlank()) item { Notice(errorMessage) }
                if (!loading && filtered.isEmpty()) item { Notice("No packages found.") }
                items(filtered.chunked(2)) { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { pkg -> StorePackageCard(pkg, userRole, Modifier.weight(1f)) { onOpenPackage(pkg) } }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                item { Spacer(Modifier.height(78.dp)) }
            }
            StoreBottomNav(onDashboard, onEsims, onMore)
        }
    }
}

@Composable
private fun StoreHeader(loading: Boolean, onRefresh: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Store", color = StoreText, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
        Text(if (loading) "Loading" else "Refresh", color = StoreBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(enabled = !loading, onClick = onRefresh))
    }
}

@Composable
private fun SearchBox(query: String, onQueryChange: (String) -> Unit) {
    Surface(Modifier.fillMaxWidth().height(50.dp), color = Color.White, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, StoreBorder)) {
        Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Search, null, tint = StoreMuted, modifier = Modifier.size(23.dp))
            Text(if (query.isBlank()) "Search eSIM packages" else query, color = if (query.isBlank()) StoreMuted.copy(alpha = 0.70f) else StoreText, fontSize = 14.sp, modifier = Modifier.padding(start = 12.dp).weight(1f).clickable { onQueryChange(query) })
        }
    }
}

@Composable
private fun FilterRow() {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        SmallFilter(Icons.Default.Dataset, "Data")
        SmallFilter(Icons.Default.CalendarMonth, "Validity")
    }
}

@Composable
private fun SmallFilter(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    Surface(color = Color.White, shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, StoreBorder)) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = StoreMuted, modifier = Modifier.size(18.dp))
            Text(label, color = StoreText, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
private fun ProviderTabs(selected: String, onSelected: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StoreProviderTabs.forEach { provider ->
            val active = provider == selected
            Surface(Modifier.size(width = 92.dp, height = 72.dp).clickable { onSelected(provider) }, color = Color.White, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, if (active) StoreBlue else Color.Transparent)) {
                Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    ProviderLogo(provider)
                    Text(provider.shortLabel(), color = if (active) StoreBlue else StoreText, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun ProviderLogo(provider: String) {
    val color = when {
        provider.contains("Orange", true) -> StoreOrange
        provider.contains("Vodafone", true) -> StoreRed
        else -> StoreBlue
    }
    Box(Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(color), contentAlignment = Alignment.Center) {
        Text(if (provider.contains("Vodafone", true)) "V" else if (provider.contains("Orange", true)) "or" else "R2", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun StorePackageCard(pkg: MobilePackage, userRole: String?, modifier: Modifier, onClick: () -> Unit) {
    Card(modifier.height(150.dp).clickable(onClick = onClick), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(Color.White), border = BorderStroke(1.dp, StoreBorder), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Column {
                Box(Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(StoreBlue.copy(alpha = 0.10f)), contentAlignment = Alignment.Center) { Icon(Icons.Default.Storefront, null, tint = StoreBlue, modifier = Modifier.size(17.dp)) }
                Spacer(Modifier.height(8.dp))
                Text(pkg.storeTitle(), color = StoreText, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(pkg.validity.orEmpty().ifBlank { "Instant activation" }, color = StoreMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column {
                Box(Modifier.fillMaxWidth().height(1.dp).background(StoreBorder))
                Spacer(Modifier.height(7.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(money(pkg.priceFor(userRole)), color = StoreText, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                    Surface(color = StoreBlue, shape = RoundedCornerShape(9.dp)) { Text("Buy", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) }
                }
            }
        }
    }
}

@Composable
private fun Notice(message: String) {
    Surface(color = Color.White, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, StoreBorder)) { Text(message, color = StoreMuted, modifier = Modifier.padding(14.dp), fontWeight = FontWeight.Medium) }
}

@Composable
private fun StoreBottomNav(onDashboard: () -> Unit, onEsims: () -> Unit, onMore: () -> Unit) {
    Surface(Modifier.fillMaxWidth(), color = Color.White, shadowElevation = 8.dp, border = BorderStroke(1.dp, StoreBorder)) {
        Row(Modifier.fillMaxWidth().height(66.dp).padding(horizontal = 8.dp, vertical = 7.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            StoreBottomItem(Icons.Default.GridView, "Dashboard", false, onDashboard)
            StoreBottomItem(Icons.Default.SimCard, "eSIMs", false, onEsims)
            StoreBottomItem(Icons.Default.Storefront, "Store", true) {}
            StoreBottomItem(Icons.Default.GridView, "More", false, onMore)
        }
    }
}

@Composable
private fun StoreBottomItem(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, selected: Boolean, onClick: () -> Unit) {
    Column(Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(horizontal = 5.dp, vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = if (selected) StoreBlue else StoreMuted, modifier = Modifier.size(22.dp))
        Text(title, color = if (selected) StoreBlue else StoreMuted, fontSize = 10.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

private fun MobilePackage.matchesTab(tab: String): Boolean {
    val h = listOf(provider, displayProvider, name, country, countryCode, packageType).joinToString(" ").lowercase()
    return when {
        tab.contains("Turkey", true) -> h.contains("turkey") || h.contains("tr") || country.equals("Turkey", true)
        tab.contains("Vodafone", true) -> h.contains("vodafone")
        tab.contains("Balkans", true) -> h.contains("balkan")
        tab.contains("World", true) -> h.contains("world") || h.contains("global")
        tab.contains("Orange", true) -> h.contains("orange") || h.contains("europe")
        else -> true
    }
}

private fun MobilePackage.storeTitle(): String {
    val base = country.takeIf { it.isNotBlank() } ?: name
    return listOfNotNull(base, dataAmount?.takeIf { it.isNotBlank() }).joinToString(" ")
}

private fun String.shortLabel(): String = when {
    contains("Turkey", true) -> "Roam2World\nTurkey"
    contains("Europe", true) && contains("Orange", true) -> "Orange\nEurope"
    contains("Balkans", true) -> "Orange\nBalkans"
    contains("World", true) -> "Orange\nWorld"
    contains("Vodafone", true) -> "Vodafone\nEurope"
    else -> this
}

private fun MobilePackage.priceNumber(role: String?): Double = priceFor(role).filter { it.isDigit() || it == '.' }.toDoubleOrNull() ?: 0.0

private fun money(raw: String): String {
    val number = raw.filter { it.isDigit() || it == '.' }.toDoubleOrNull() ?: return raw
    return NumberFormat.getCurrencyInstance(Locale.US).format(number)
}
