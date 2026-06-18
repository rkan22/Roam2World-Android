package im.angry.openeuicc.ui.compose.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Dataset
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val DetailBlue = Color(0xFF1263F1)
private val DetailText = Color(0xFF111827)
private val DetailMuted = Color(0xFF6B7280)
private val DetailBorder = Color(0xFFE5E7EB)
private val DetailBg = Color(0xFFF8FAFF)
private val DetailOrange = Color(0xFFFF7900)

@Composable
fun CompactPackageDetailScreen(
    packageName: String,
    price: String,
    data: String,
    validity: String,
    network: String,
    coverage: String,
    description: String,
    onBack: () -> Unit,
    onBuy: () -> Unit
) {
    Surface(Modifier.fillMaxSize(), color = DetailBg) {
        Box(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, top = 14.dp, end = 20.dp, bottom = 142.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ArrowBack, null, tint = DetailText, modifier = Modifier.size(30.dp).clickable(onClick = onBack))
                    Spacer(Modifier.weight(1f))
                    ProviderLogo(network)
                    Spacer(Modifier.weight(1f))
                    Spacer(Modifier.size(30.dp))
                }

                Text(packageName, color = DetailText, fontSize = 26.sp, lineHeight = 30.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(description.ifBlank { "Stay connected with fast and reliable data." }, color = DetailMuted, fontSize = 15.sp, lineHeight = 21.sp, textAlign = TextAlign.Center)

                DetailInfoCard(Icons.Default.Dataset, "Data", data.ifBlank { "10GB" })
                DetailInfoCard(Icons.Default.CalendarMonth, "Validity", validity.ifBlank { "30 Days" })
                DetailInfoCard(Icons.Default.Sell, "Price", price, valueColor = DetailBlue, valueSize = 32)
                CoverageCard(coverage)
                TrustStrip()
            }

            Surface(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                color = DetailBg.copy(alpha = 0.96f)
            ) {
                Button(
                    onClick = onBuy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 18.dp)
                        .height(58.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DetailBlue),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.ShoppingCart, null, tint = Color.White, modifier = Modifier.size(24.dp))
                    Text("Purchase Package", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(start = 12.dp))
                }
            }
        }
    }
}

@Composable
private fun ProviderLogo(network: String) {
    val isVodafone = network.contains("vodafone", true)
    val color = if (isVodafone) Color(0xFFE60000) else DetailOrange
    Box(Modifier.size(54.dp).clip(RoundedCornerShape(10.dp)).background(color), contentAlignment = Alignment.Center) {
        Text(if (isVodafone) "voda" else "orange", color = Color.White, fontSize = if (isVodafone) 12.sp else 13.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun DetailInfoCard(icon: ImageVector, label: String, value: String, valueColor: Color = DetailText, valueSize: Int = 27) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(Color.White), border = BorderStroke(1.dp, DetailBorder), elevation = CardDefaults.cardElevation(2.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(54.dp).clip(RoundedCornerShape(999.dp)).background(DetailBlue.copy(alpha = .10f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = DetailBlue, modifier = Modifier.size(28.dp))
            }
            Column(Modifier.padding(start = 16.dp)) {
                Text(label, color = DetailMuted, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Text(value, color = valueColor, fontSize = valueSize.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun CoverageCard(coverage: String) {
    val normalizedCoverage = coverage.trim().ifBlank { "Coverage details not provided" }
    val countries = coverageTokens(normalizedCoverage)
    val summary = coverageSummary(normalizedCoverage, countries)
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(Color.White), border = BorderStroke(1.dp, DetailBorder), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(999.dp)).background(DetailBlue.copy(alpha = .10f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Language, null, tint = DetailBlue, modifier = Modifier.size(23.dp))
                }
                Column(Modifier.padding(start = 12.dp)) {
                    Text("Coverage", color = DetailMuted, fontSize = 13.sp)
                    Text(summary, color = DetailText, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            if (countries.isNotEmpty()) {
                Text(countries.take(18).joinToString("  "), color = DetailText, fontSize = 14.sp, lineHeight = 22.sp)
                if (countries.size > 18) {
                    Text("+${countries.size - 18} more", color = DetailBlue, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                Text(normalizedCoverage, color = DetailMuted, fontSize = 14.sp, lineHeight = 20.sp, maxLines = 5, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private fun coverageSummary(raw: String, countries: List<String>): String {
    if (countries.size >= 2) return "${countries.size} Countries"
    if (raw.contains("150", true) || raw.contains("global", true) || raw.contains("world", true)) return raw
    if (raw.contains("europe", true)) return "Europe Coverage"
    if (raw.contains("turkey", true) || raw.contains("türkiye", true) || raw.equals("TR", true)) return "Turkey Coverage"
    return raw.take(40)
}

private fun coverageTokens(raw: String): List<String> {
    val compactCodes = raw.split(',', ';', '|', '/', '\n')
        .map { it.trim() }
        .filter { it.length in 2..40 }
    return compactCodes
        .map { token -> coverageName(token) }
        .distinct()
        .take(40)
}

private fun coverageName(token: String): String {
    val clean = token.trim().uppercase()
    return when (clean) {
        "TR", "TUR" -> "Turkey"
        "US", "USA" -> "United States"
        "GB", "UK", "GBR" -> "United Kingdom"
        "FR", "FRA" -> "France"
        "DE", "DEU" -> "Germany"
        "ES", "ESP" -> "Spain"
        "IT", "ITA" -> "Italy"
        "NL", "NLD" -> "Netherlands"
        "CH", "CHE" -> "Switzerland"
        "SE", "SWE" -> "Sweden"
        "PT", "PRT" -> "Portugal"
        "BE", "BEL" -> "Belgium"
        "AT", "AUT" -> "Austria"
        "DK", "DNK" -> "Denmark"
        "NO", "NOR" -> "Norway"
        "IE", "IRL" -> "Ireland"
        "GR", "GRC" -> "Greece"
        "PL", "POL" -> "Poland"
        "RO", "ROU" -> "Romania"
        "BG", "BGR" -> "Bulgaria"
        "HR", "HRV" -> "Croatia"
        "RS", "SRB" -> "Serbia"
        "AL", "ALB" -> "Albania"
        "ME", "MNE" -> "Montenegro"
        "MK", "MKD" -> "North Macedonia"
        "BA", "BIH" -> "Bosnia and Herzegovina"
        "SI", "SVN" -> "Slovenia"
        "SK", "SVK" -> "Slovakia"
        "CZ", "CZE" -> "Czechia"
        "HU", "HUN" -> "Hungary"
        else -> token.trim().replace('_', ' ').replace('-', ' ')
    }
}

@Composable
private fun TrustStrip() {
    Surface(Modifier.fillMaxWidth(), color = Color(0xFFF1F5FB), shape = RoundedCornerShape(14.dp)) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Icon(Icons.Default.Security, null, tint = DetailBlue, modifier = Modifier.size(16.dp))
            Text("Secure payment • Instant activation • 24/7 support", color = DetailMuted, fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
