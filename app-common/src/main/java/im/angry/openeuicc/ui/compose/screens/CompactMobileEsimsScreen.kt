package im.angry.openeuicc.ui.compose.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import im.angry.openeuicc.auth.MobileEsim
import im.angry.openeuicc.auth.MobileEsimFilters
import im.angry.openeuicc.ui.PackageNameCleaner
import im.angry.openeuicc.ui.R2wBottomNav
import im.angry.openeuicc.ui.R2wBottomTab
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val EsimBlue = Color(0xFF1263F1)
private val EsimText = Color(0xFF111827)
private val EsimMuted = Color(0xFF6B7280)
private val EsimBorder = Color(0xFFE5E7EB)
private val EsimBg = Color(0xFFF8FAFF)
private val EsimGreen = Color(0xFF16A34A)
private val EsimOrange = Color(0xFFF97316)

public data class EsimsDisplayStatus(val label: String, val raw: String)

public enum class EsimFilter(val label: String) {
    ACTIVE("Active"),
    PENDING("Pending"),
    EXPIRED("Expired");

    fun matches(status: EsimsDisplayStatus): Boolean = when (this) {
        ACTIVE -> status.raw == "active" || status.raw == "ready" || status.raw == "provisioned"
        PENDING -> status.raw == "pending"
        EXPIRED -> status.raw == "expired"
    }
}

@Composable
public fun CompactMobileEsimsScreen(
    allEsims: List<MobileEsim>,
    filteredEsims: List<MobileEsim>,
    selectedFilter: EsimFilter,
    initialFilter: String?,
    loading: Boolean,
    error: String?,
    onFilterChange: (EsimFilter) -> Unit,
    onRefresh: () -> Unit,
    onOpenDetail: (MobileEsim) -> Unit,
    onOpenDashboard: () -> Unit,
    onOpenPackages: () -> Unit,
    onOpenMore: () -> Unit
) {
    Scaffold(containerColor = EsimBg) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item { EsimsHeader(onRefresh, loading) }
                item { EsimTabs(selectedFilter, initialFilter, onFilterChange) }
                if (!error.isNullOrBlank()) item { ErrorBanner(error) }
                if (!loading && filteredEsims.isEmpty()) item { EmptyEsimsCard(allEsims.size) }
                items(filteredEsims) { esim ->
                    EsimCard(esim = esim, onOpenDetail = { onOpenDetail(esim) })
                }
                item { Spacer(Modifier.height(74.dp)) }
            }
            R2wBottomNav(selected = R2wBottomTab.Esims)
        }
    }
}

@Composable
private fun EsimsHeader(onRefresh: () -> Unit, loading: Boolean) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("eSIMs", color = EsimText, fontSize = 27.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
        Surface(
            modifier = Modifier.size(46.dp).clickable(onClick = onRefresh),
            color = EsimBlue,
            shape = RoundedCornerShape(999.dp),
            shadowElevation = 8.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Add, null, tint = Color.White, modifier = Modifier.size(26.dp))
            }
        }
    }
    if (loading) Text("Refreshing eSIM list...", color = EsimMuted, fontSize = 12.sp)
}

@Composable
private fun EsimTabs(selected: EsimFilter, initialFilter: String?, onFilterChange: (EsimFilter) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        EsimFilter.entries.forEach { filter ->
            val active = initialFilter != MobileEsimFilters.FILTER_EXPIRED_SOON && selected == filter
            Column(
                modifier = Modifier.weight(1f).clickable { onFilterChange(filter) },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(filter.label, color = if (active) EsimBlue else EsimMuted, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Box(Modifier.height(3.dp).fillMaxWidth().background(if (active) EsimBlue else Color.Transparent))
            }
        }
    }
}

@Composable
private fun EsimCard(esim: MobileEsim, onOpenDetail: () -> Unit) {
    val status = realStatus(esim)
    val iccid = esim.iccid.orEmpty().ifBlank { "Pending ICCID" }
    val provider = visibleProvider(esim.provider).orEmpty().ifBlank { "Roam2World" }
    val packageName = PackageNameCleaner.clean(esim.packageName).ifBlank { esimTitle(esim) }
    val expiry = esim.expiresAt?.takeIf { it.isNotBlank() }?.let { formatEsimDate(it) } ?: "Not set"

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenDetail),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(Color.White),
        border = BorderStroke(1.dp, EsimBorder),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column {
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text("ICCID", color = EsimMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text(iccid.chunked(4).joinToString(" ").take(27), color = EsimText, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                }
                Icon(Icons.Default.MoreHoriz, null, tint = EsimMuted, modifier = Modifier.size(22.dp))
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(EsimBorder))
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    EsimInfoLine(Icons.Default.WifiTethering, "Provider", provider)
                    EsimInfoLine(Icons.Default.SimCard, "Package", packageName)
                }
                StatusChip(status.label)
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(EsimBorder))
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                EsimBottomInfo(Icons.Default.CalendarMonth, "Expiry date", expiry, Modifier.weight(1f))
                Box(Modifier.size(width = 1.dp, height = 36.dp).background(EsimBorder))
                EsimBottomInfo(Icons.Default.CheckCircle, "Install status", if (status.raw == "pending") "Pending" else "Installed", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun EsimInfoLine(icon: ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconBubble(icon, EsimBlue)
        Column(Modifier.padding(start = 10.dp)) {
            Text(label, color = EsimMuted, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            Text(value, color = EsimText, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun EsimBottomInfo(icon: ImageVector, label: String, value: String, modifier: Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = if (label.contains("Install")) EsimGreen else EsimMuted, modifier = Modifier.size(22.dp))
        Column(Modifier.padding(start = 8.dp)) {
            Text(label, color = EsimMuted, fontSize = 10.sp, fontWeight = FontWeight.Medium)
            Text(value, color = EsimText, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun IconBubble(icon: ImageVector, tint: Color) {
    Box(Modifier.size(34.dp).clip(RoundedCornerShape(999.dp)).background(tint.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(19.dp))
    }
}

@Composable
private fun StatusChip(label: String) {
    val normalized = label.lowercase()
    val color = when {
        normalized.contains("active") || normalized.contains("ready") || normalized.contains("provision") -> EsimGreen
        normalized.contains("expired") -> Color(0xFFDC2626)
        else -> EsimOrange
    }
    Surface(color = color.copy(alpha = 0.10f), shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, color.copy(alpha = 0.30f))) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).clip(RoundedCornerShape(999.dp)).background(color))
            Text(label, Modifier.padding(start = 7.dp), color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ErrorBanner(error: String) {
    Surface(color = Color(0xFFFFEAEA), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Color(0xFFFFCACA))) {
        Text(error, color = Color(0xFFB91C1C), modifier = Modifier.padding(14.dp), fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun EmptyEsimsCard(total: Int) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(Color.White), border = BorderStroke(1.dp, EsimBorder)) {
        Text(if (total == 0) "No eSIM found." else "No eSIM matches this tab.", color = EsimMuted, modifier = Modifier.padding(18.dp))
    }
}

@Composable
private fun EsimsBottomNav(onOpenDashboard: () -> Unit, onOpenPackages: () -> Unit, onOpenMore: () -> Unit) {
    Surface(Modifier.fillMaxWidth(), color = Color.White, shadowElevation = 8.dp, border = BorderStroke(1.dp, EsimBorder)) {
        Row(Modifier.fillMaxWidth().height(66.dp).padding(horizontal = 8.dp, vertical = 7.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            EsimBottomItem(Icons.Default.GridView, "Dashboard", false, onOpenDashboard)
            EsimBottomItem(Icons.Default.SimCard, "eSIMs", true) {}
            EsimBottomItem(Icons.Default.People, "Customers", false) {}
            EsimBottomItem(Icons.Default.Storefront, "Store", false, onOpenPackages)
            EsimBottomItem(Icons.Default.GridView, "More", false, onOpenMore)
        }
    }
}

@Composable
private fun EsimBottomItem(icon: ImageVector, title: String, selected: Boolean, onClick: () -> Unit) {
    Column(Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(horizontal = 5.dp, vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = if (selected) EsimBlue else EsimMuted, modifier = Modifier.size(22.dp))
        Text(title, color = if (selected) EsimBlue else EsimMuted, fontSize = 10.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

public fun realStatus(esim: MobileEsim): EsimsDisplayStatus {
    val raw = esim.status.orEmpty().trim()
    val normalized = raw.lowercase()
    val expiresAt = parseDate(esim.expiresAt)
    val isExpiredByDate = expiresAt?.isBefore(OffsetDateTime.now()) == true
    val hasIccid = !esim.iccid.isNullOrBlank()
    val hasInstallCode = !esim.installCode().isNullOrBlank() || !esim.qrPayload().isNullOrBlank()
    return when {
        normalized.contains("expired") || normalized.contains("depleted") || normalized.contains("terminated") || isExpiredByDate -> EsimsDisplayStatus("Expired", "expired")
        normalized.contains("active") || normalized.contains("activated") || normalized.contains("enabled") -> EsimsDisplayStatus("Active", "active")
        normalized.contains("pending") || normalized.contains("processing") || normalized.contains("waiting") || normalized.contains("ordered") -> EsimsDisplayStatus("Pending", "pending")
        hasIccid && hasInstallCode && expiresAt != null -> EsimsDisplayStatus("Ready", "ready")
        hasIccid && expiresAt != null -> EsimsDisplayStatus("Active", "active")
        hasIccid && hasInstallCode -> EsimsDisplayStatus("Ready", "ready")
        hasInstallCode -> EsimsDisplayStatus("Ready", "ready")
        hasIccid -> EsimsDisplayStatus("Provisioned", "provisioned")
        else -> EsimsDisplayStatus("Pending", "pending")
    }
}

private fun esimTitle(esim: MobileEsim): String =
    PackageNameCleaner.clean(esim.packageName).ifBlank { visibleProvider(esim.provider).orEmpty().ifBlank { "Roam2World eSIM" } }

private fun visibleProvider(provider: String?): String? =
    provider?.replace("TGT", "Orange", ignoreCase = true)?.replace("tgt", "Orange", ignoreCase = true)

private fun formatEsimDate(value: String): String {
    val parsed = runCatching { OffsetDateTime.parse(value) }.getOrNull() ?: return value
    return parsed.format(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.getDefault()))
}

private fun parseDate(value: String?): OffsetDateTime? =
    value?.takeIf { it.isNotBlank() }?.let { runCatching { OffsetDateTime.parse(it) }.getOrNull() }
