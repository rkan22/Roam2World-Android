package im.angry.openeuicc.ui.compose.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
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

private val SBlue = Color(0xFF1263F1)
private val SText = Color(0xFF111827)
private val SMuted = Color(0xFF6B7280)
private val SBorder = Color(0xFFE5E7EB)
private val SBg = Color(0xFFF8FAFF)
private val SOrange = Color(0xFFFF7900)

@Composable
fun CompactStoreScreenV2(
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
    val tabs = StoreProviderTabs
    val all = remember(catalog, userRole) { (catalog.featuredPackages + catalog.packages).filter { it.isVisibleFor(userRole) } }
    val filtered by remember(all, query, selectedProvider) { derivedStateOf { all.filter { it.matches(query) && it.matchesStoreTab(selectedProvider) }.sortedBy { it.priceNumber(userRole) } } }
    Scaffold(containerColor = SBg) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            LazyColumn(Modifier.weight(1f).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { Header(loading, onRefresh) }
                item { Search(query, onQueryChange) }
                item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { Filter(Icons.Default.Dataset, "Data"); Filter(Icons.Default.CalendarMonth, "Validity") } }
                item { Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) { tabs.forEach { ProviderTab(it, it == selectedProvider) { onProviderChange(it) } } } }
                if (!errorMessage.isNullOrBlank()) item { Notice(errorMessage) }
                if (!loading && filtered.isEmpty()) item { Notice("No packages found.") }
                items(filtered.chunked(2)) { row -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) { row.forEach { PackageTile(it, userRole, Modifier.weight(1f)) { onOpenPackage(it) } }; if (row.size == 1) Spacer(Modifier.weight(1f)) } }
                item { Spacer(Modifier.height(78.dp)) }
            }
            Bottom(onDashboard, onEsims, onMore)
        }
    }
}

@Composable private fun Header(loading: Boolean, refresh: () -> Unit) = Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) { Text("Store", color = SText, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f)); Icon(Icons.Default.MoreHoriz, null, tint = SText, modifier = Modifier.size(28.dp).clickable(enabled = !loading, onClick = refresh)) }
@Composable private fun Search(query: String, change: (String) -> Unit) = Surface(Modifier.fillMaxWidth().height(50.dp), color = Color.White, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, SBorder)) { Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Search, null, tint = SMuted, modifier = Modifier.size(23.dp)); Text(if (query.isBlank()) "Search eSIM packages" else query, color = if (query.isBlank()) SMuted.copy(alpha = .7f) else SText, fontSize = 14.sp, modifier = Modifier.padding(start = 12.dp).weight(1f).clickable { change(query) }) } }
@Composable private fun Filter(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) = Surface(color = Color.White, shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, SBorder)) { Row(Modifier.padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = SMuted, modifier = Modifier.size(18.dp)); Text(text, color = SText, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 8.dp)) } }
@Composable private fun ProviderTab(name: String, active: Boolean, click: () -> Unit) = Surface(Modifier.size(108.dp, 74.dp).clickable(onClick = click), color = Color.White, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, if (active) SBlue else Color.Transparent)) { Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { Box(Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(if (name.contains("Orange", true)) SOrange else SBlue), contentAlignment = Alignment.Center) { Text(if (name.contains("Orange", true)) "or" else "R2", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold) }; Text(name.shortStoreLabel(), color = if (active) SBlue else SText, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis) } }
@Composable private fun PackageTile(pkg: MobilePackage, role: String?, mod: Modifier, click: () -> Unit) = Card(mod.height(142.dp).clickable(onClick = click), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(Color.White), border = BorderStroke(1.dp, SBorder), elevation = CardDefaults.cardElevation(2.dp)) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) { Column { Box(Modifier.size(26.dp).clip(RoundedCornerShape(8.dp)).background(SBlue.copy(alpha = .1f)), contentAlignment = Alignment.Center) { Icon(Icons.Default.Storefront, null, tint = SBlue, modifier = Modifier.size(16.dp)) }; Spacer(Modifier.height(7.dp)); Text(pkg.storeTitleV2(), color = SText, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 17.sp); Text(pkg.validity.orEmpty().ifBlank { "Instant activation" }, color = SMuted, fontSize = 11.sp, maxLines = 1) }; Column { Box(Modifier.fillMaxWidth().height(1.dp).background(SBorder)); Spacer(Modifier.height(7.dp)); Row(verticalAlignment = Alignment.CenterVertically) { Text(moneyV2(pkg.priceFor(role)), color = SText, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f)); Surface(color = SBlue, shape = RoundedCornerShape(10.dp)) { Text("Buy", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)) } } } } }
@Composable private fun Notice(msg: String) = Surface(color = Color.White, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, SBorder)) { Text(msg, color = SMuted, modifier = Modifier.padding(14.dp), fontWeight = FontWeight.Medium) }
@Composable private fun Bottom(d: () -> Unit, e: () -> Unit, m: () -> Unit) = Surface(Modifier.fillMaxWidth(), color = Color.White, shadowElevation = 8.dp, border = BorderStroke(1.dp, SBorder)) { Row(Modifier.fillMaxWidth().height(66.dp).padding(horizontal = 8.dp, vertical = 7.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) { Nav(Icons.Default.GridView, "Dashboard", false, d); Nav(Icons.Default.SimCard, "eSIMs", false, e); Nav(Icons.Default.Storefront, "Store", true) {}; Nav(Icons.Default.GridView, "More", false, m) } }
@Composable private fun Nav(i: androidx.compose.ui.graphics.vector.ImageVector, t: String, s: Boolean, c: () -> Unit) = Column(Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = c).padding(horizontal = 5.dp, vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(i, null, tint = if (s) SBlue else SMuted, modifier = Modifier.size(22.dp)); Text(t, color = if (s) SBlue else SMuted, fontSize = 10.sp, fontWeight = FontWeight.Medium, maxLines = 1) }

private fun MobilePackage.matchesStoreTab(tab: String): Boolean { val h = listOf(provider, displayProvider, name, country, countryCode, packageType).joinToString(" ").lowercase(); return when { tab.contains("Turkey", true) -> h.contains("turkey") || h.contains("tr") || country.equals("Turkey", true); tab.contains("Vodafone", true) -> h.contains("vodafone"); tab.contains("Balkans", true) -> h.contains("balkan"); tab.contains("World", true) -> h.contains("world") || h.contains("global"); tab.contains("Orange", true) -> h.contains("orange") || h.contains("europe"); else -> true } }
private fun MobilePackage.storeTitleV2(): String = listOfNotNull(country.takeIf { it.isNotBlank() } ?: name, dataAmount?.takeIf { it.isNotBlank() }).joinToString(" ")
private fun String.shortStoreLabel(): String = when { contains("Turkey", true) -> "Roam2W.\nTurkey"; contains("Europe", true) && contains("Orange", true) -> "Orange\nEurope"; contains("Balkans", true) -> "Orange\nBalkans"; contains("World", true) -> "Orange\nWorld"; contains("Vodafone", true) -> "Vodafone\nEurope"; else -> this }
private fun MobilePackage.priceNumber(role: String?): Double = priceFor(role).filter { it.isDigit() || it == '.' }.toDoubleOrNull() ?: 0.0
private fun moneyV2(raw: String): String {
    val parsed = raw.filter { it.isDigit() || it == '.' }.toDoubleOrNull()
    return if (parsed == null) raw else NumberFormat.getCurrencyInstance(Locale.US).format(parsed)
}
