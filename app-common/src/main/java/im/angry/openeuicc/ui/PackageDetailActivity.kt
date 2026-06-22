package im.angry.openeuicc.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import im.angry.openeuicc.auth.MobilePackage
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import coil.compose.AsyncImage
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.unit.sp

class PackageDetailActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            PackageDetailScreen(
                packageName = displayPackageName(),
                countryLine = countryDisplay(),
                price = r2wMoney(intent.getStringExtra(EXTRA_PRICE)),
                data = intent.getStringExtra(EXTRA_DATA).orEmpty(),
                validity = intent.getStringExtra(EXTRA_VALIDITY).orEmpty(),
                network = displayProvider(),
                coverage = coverageDisplay(),
                description = descriptionText(),
                visibility = intent.getStringExtra(EXTRA_VISIBILITY).orEmpty(),
                renewal = renewalDisplay(),
                onBack = { finish() },
                onBuy = {
                    startActivity(CustomerInfoActivity.createIntent(this, intent))
                }
            )
        }
    }

    private fun displayPackageName(): String {
        val rawName = intent.getStringExtra(EXTRA_NAME).orEmpty()
        val data = intent.getStringExtra(EXTRA_DATA)
        val country = intent.getStringExtra(EXTRA_COUNTRY)
        val countryCode = intent.getStringExtra(EXTRA_COUNTRY_CODE)
        val coverage = intent.getStringExtra(EXTRA_COVERAGE)
        val network = intent.getStringExtra(EXTRA_NETWORK)

        val allText = listOfNotNull(rawName, country, countryCode, coverage, network)
            .joinToString(" ")
            .lowercase()

        val region = when {
            allText.contains("balkan") -> "Balkans"
            allText.contains("europe") || allText.contains("eu ") || allText.contains("europa") -> "Europe"
            allText.contains("turkey") || allText.contains("türkiye") || countryCode.equals("TR", ignoreCase = true) -> "Turkey"
            country?.isNotBlank() == true && !country.equals("Multi-country", ignoreCase = true) -> country
            else -> "World"
        }

        val dataLabel = data?.trim()?.takeIf { it.isNotBlank() } ?: extractDataFromName(rawName)

        return listOfNotNull("Orange", region, dataLabel)
            .joinToString(" ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .ifBlank { PackageNameCleaner.clean(rawName).ifBlank { "Orange eSIM Package" } }
    }

    private fun extractDataFromName(rawName: String): String? =
        Regex("""(\d+(?:\.\d+)?)\s*GB""", RegexOption.IGNORE_CASE)
            .find(rawName)
            ?.value
            ?.uppercase()
            ?.replace("GB", " GB")
            ?.replace(Regex("\\s+"), " ")
            ?.trim()

    private fun displayProvider(): String {
        val joined = listOf(
            intent.getStringExtra(EXTRA_PROVIDER),
            intent.getStringExtra(EXTRA_NETWORK),
            intent.getStringExtra(EXTRA_COVERAGE),
            intent.getStringExtra(EXTRA_COUNTRY)
        )
            .filterNotNull()
            .joinToString(" ")
            .lowercase()

        return when {
            joined.contains("vodafone") -> "Vodafone Europe"
            joined.contains("balkan") -> "Orange Balkans"
            joined.contains("europe") -> "Orange Europe"
            joined.contains("orange") -> "Orange"
            else -> "Roam2World"
        }
    }

    private fun countryDisplay(): String {
        val country = intent.getStringExtra(EXTRA_COUNTRY)?.takeIf { it.isNotBlank() } ?: "Global"
        val code = intent.getStringExtra(EXTRA_COUNTRY_CODE)?.takeIf { it.isNotBlank() }?.uppercase()
        val flag = code?.let { codeToFlag(it) }.orEmpty()
        return listOf(flag, country, code?.let { "($it)" })
            .filterNotNull()
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .trim()
    }

    private fun coverageDisplay(): String =
        intent.getStringExtra(EXTRA_COVERAGE)
            ?.takeIf { it.isNotBlank() }
            ?: "150+ Countries"

    private fun descriptionText(): String {
        val rawDescription = intent.getStringExtra(EXTRA_DESCRIPTION)?.takeIf { it.isNotBlank() }
        if (rawDescription != null) return rawDescription

        val name = displayPackageName()
        return "Activate reliable mobile data with $name. Built for fast setup, secure connectivity and international business travel."
    }

    private fun codeToFlag(code: String): String {
        val normalized = code.trim().uppercase()
        if (normalized.length != 2 || normalized.any { it !in 'A'..'Z' }) return ""
        val first = Character.codePointAt(normalized, 0) - 'A'.code + 0x1F1E6
        val second = Character.codePointAt(normalized, 1) - 'A'.code + 0x1F1E6
        return String(Character.toChars(first)) + String(Character.toChars(second))
    }


    private fun renewalDisplay(): String =
        when {
            !intent.hasExtra(EXTRA_RENEWAL_SUPPORTED) -> "Not specified"
            intent.getBooleanExtra(EXTRA_RENEWAL_SUPPORTED, false) -> "Supported"
            else -> "Not supported"
        }

    companion object {
        private const val EXTRA_ID = "package.id"
        private const val EXTRA_PROVIDER = "package.provider"
        private const val EXTRA_TYPE = "package.type"
        private const val EXTRA_NAME = "package.name"
        private const val EXTRA_COUNTRY = "package.country"
        private const val EXTRA_COUNTRY_CODE = "package.country_code"
        private const val EXTRA_PRICE = "package.price"
        private const val EXTRA_ROLE = "package.role"
        private const val EXTRA_VISIBILITY = "package.visibility"
        private const val EXTRA_DATA = "package.data"
        private const val EXTRA_VALIDITY = "package.validity"
        private const val EXTRA_NETWORK = "package.network"
        private const val EXTRA_COVERAGE = "package.coverage"
        private const val EXTRA_DESCRIPTION = "package.description"
        private const val EXTRA_RENEWAL_SUPPORTED = "package.renewal_supported"

        fun createIntent(context: Context, mobilePackage: MobilePackage, role: String?): Intent =
            Intent(context, PackageDetailActivity::class.java).apply {
                putExtra(EXTRA_ID, mobilePackage.id)
                putExtra(EXTRA_PROVIDER, mobilePackage.provider)
                putExtra(EXTRA_TYPE, mobilePackage.packageType)
                putExtra(EXTRA_NAME, mobilePackage.name)
                putExtra(EXTRA_COUNTRY, mobilePackage.country)
                putExtra(EXTRA_COUNTRY_CODE, mobilePackage.countryCode)
                putExtra(EXTRA_PRICE, mobilePackage.priceFor(role))
                putExtra(EXTRA_ROLE, role)
                putExtra(EXTRA_VISIBILITY, mobilePackage.visibilityLabel())
                putExtra(EXTRA_DATA, mobilePackage.dataAmount)
                putExtra(EXTRA_VALIDITY, mobilePackage.validity)
                putExtra(EXTRA_NETWORK, mobilePackage.network)
                putExtra(EXTRA_COVERAGE, mobilePackage.coverage)
                putExtra(EXTRA_DESCRIPTION, mobilePackage.description)
                mobilePackage.renewalSupported?.let { putExtra(EXTRA_RENEWAL_SUPPORTED, it) }
            }

        fun createIntent(context: Context, packageId: String?): Intent =
            Intent(context, PackageDetailActivity::class.java).apply {
                putExtra(EXTRA_ID, packageId)
            }
    }
}

@Composable
private fun PackageDetailScreen(
    packageName: String,
    countryLine: String,
    price: String,
    data: String,
    validity: String,
    network: String,
    coverage: String,
    description: String,
    visibility: String,
    renewal: String,
    onBack: () -> Unit,
    onBuy: () -> Unit
) {
    val blue = Color(0xFF1263F1)
    val dark = Color(0xFF07142F)
    val bg = Color(0xFFF5F8FC)
    val muted = Color(0xFF667085)

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = bg) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(start = 18.dp, top = 10.dp, end = 18.dp, bottom = 210.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    DetailHeader(onBack = onBack)

                    EuropeHeroCard(
                        packageName = packageName,
                        data = data,
                        validity = validity,
                        network = network,
                        price = price
                    )



                    PackageInfoCard(title = "Package Details") {
                        CleanDetailRow(
                            label = "Data",
                            value = data.ifBlank { "Mobile data" },
                            iconRes = im.angry.openeuicc.common.R.drawable.r2w_ic_data
                        )
                        CleanDetailRow(
                            label = "Validity",
                            value = formatValidityLabel(validity.ifBlank { "30 Days" }),
                            iconRes = im.angry.openeuicc.common.R.drawable.r2w_ic_validity
                        )
                        CleanDetailRow(
                            label = "Coverage",
                            value = coverageCountLabel(packageName, coverage, countryLine),
                            iconRes = im.angry.openeuicc.common.R.drawable.r2w_ic_coverage
                        )
                        CleanDetailRow(
                            label = "Type",
                            value = "eSIM",
                            iconRes = im.angry.openeuicc.common.R.drawable.r2w_ic_package
                        )
                        CleanDetailRow(
                            label = "Provider",
                            value = network,
                            iconRes = im.angry.openeuicc.common.R.drawable.r2w_ic_provider
                        )
                        CleanDetailRow(
                            label = "Renewal",
                            value = renewal,
                            iconRes = im.angry.openeuicc.common.R.drawable.r2w_ic_renewal
                        )
                    }


                    CoverageFlagsCard(
                        packageName = packageName,
                        coverage = coverage,
                        countryLine = countryLine
                    )


                }

                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(bottom = 28.dp),
                    color = Color.White,
                    shadowElevation = 14.dp,
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.width(118.dp)) {
                            Text(
                                text = "Total",
                                color = muted,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = price,
                                color = blue,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Black
                            )
                        }

                        Button(
                            onClick = onBuy,
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = blue),
                            shape = RoundedCornerShape(22.dp)
                        ) {
                            Text(
                                text = "Buy Now",
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium,
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
private fun DetailHeader(onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.White)
                .border(1.dp, Color(0xFFE1E7F0), CircleShape)
                .clickable { onBack() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "‹",
                color = Color(0xFF07142F),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Image(
                painter = painterResource(id = im.angry.openeuicc.common.R.drawable.ic_logo),
                contentDescription = null,
                modifier = Modifier.size(26.dp),
                contentScale = ContentScale.Fit
            )

            Text(
                text = "Package Details",
                color = Color(0xFF07142F),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black
            )
        }
    }
}

@Composable
private fun EuropeHeroCard(
    packageName: String,
    data: String,
    validity: String,
    network: String,
    price: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(218.dp),
        shape = RoundedCornerShape(30.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1263F1)),
        elevation = CardDefaults.cardElevation(defaultElevation = 5.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Image(
                painter = painterResource(id = im.angry.openeuicc.common.R.drawable.store_banner),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alignment = Alignment.CenterEnd
            )

            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xFF061848),
                                Color(0xFF061848),
                                Color(0xEE061848),
                                Color(0x661263F1),
                                Color(0x22000000)
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(22.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = Color.White.copy(alpha = 0.18f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.42f))
                    ) {
                        Text(
                            text = "Roam2World",
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Black
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = Color.White.copy(alpha = 0.16f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.38f))
                    ) {
                        Text(
                            text = "B2B",
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Black
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = packageName,
                        color = Color.White,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black
                    )

                    Text(
                        text = buildString {
                            append(data.ifBlank { "Mobile Data" })
                            if (validity.isNotBlank()) append(" • ").append(formatValidityLabel(validity))
                        },
                        color = Color.White.copy(alpha = 0.92f),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    Text(
                        text = network,
                        color = Color.White.copy(alpha = 0.84f),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )

                    Box(
                        modifier = Modifier
                            .width(54.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(Color(0xFF45C8FF))
                    )

                    Text(
                        text = "Reliable travel data across Europe.",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }

                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Color.White
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Text(
                            text = priceCurrency(price),
                            color = Color(0xFF1263F1),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Black
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Text(
                            text = priceAmount(price),
                            color = Color(0xFF1263F1),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniSummaryCard(
    title: String,
    value: String,
    subValue: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.height(108.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                color = Color(0xFF07142F),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = value,
                color = Color(0xFF1263F1),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                maxLines = 1
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = subValue,
                color = Color(0xFF667085),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun PackageInfoCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title,
                color = Color(0xFF07142F),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black
            )

            HorizontalDivider(color = Color(0xFFE6EAF0))

            content()
        }
    }
}

@Composable
private fun CleanDetailRow(
    label: String,
    value: String,
    iconRes: Int
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(id = iconRes),
                    contentDescription = label,
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                )

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = label,
                    fontSize = 16.sp,
                    color = Color(0xFF7B859B),
                    fontWeight = FontWeight.Medium
                )
            }

            Text(
                text = value,
                fontSize = 16.sp,
                color = Color(0xFF0B1736),
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.End,
                modifier = Modifier.widthIn(min = 118.dp)
            )
        }

        HorizontalDivider(
            thickness = 1.dp,
            color = Color(0xFFE8EDF5)
        )
    }
}


@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CoverageFlagsCard(
    packageName: String,
    coverage: String,
    countryLine: String
) {
    val allCodes = supportedCountryCodes(packageName, coverage, countryLine)
    val visibleCodes = allCodes.take(24)
    val remaining = allCodes.size - visibleCodes.size

    PackageInfoCard(title = "Supported Countries") {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            visibleCodes.forEach { code ->
                CountryFlagChip(code = code)
            }

            if (remaining > 0) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = Color(0xFFEAF3FF)
                ) {
                    Text(
                        text = "+$remaining more",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        color = Color(0xFF1263F1),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }
    }
}

@Composable
private fun CountryFlagChip(code: String) {
    AsyncImage(
        model = "https://flagcdn.com/w80/${code.lowercase()}.png",
        contentDescription = code,
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .border(1.dp, Color.White, CircleShape),
        contentScale = ContentScale.Crop
    )
}

private fun supportedCountryCodes(packageName: String, coverage: String, countryLine: String): List<String> {
    val lower = "$packageName $coverage $countryLine".lowercase()
    val joined = "$packageName $coverage $countryLine".uppercase()

    val extracted = Regex("\\b[A-Z]{2}\\b")
        .findAll(joined)
        .map { it.value }
        .filterNot { it in setOf("EU", "GB", "MB", "KB", "USD") }
        .distinct()
        .take(250)
        .toList()

    if (extracted.isNotEmpty()) return extracted

    if (
        lower.contains("balkan") ||
        lower.contains("orange balkans") ||
        lower.contains("balkans sim") ||
        lower.contains("balkans esim")
    ) {
        return listOf(
            "AL", "AD", "AT", "BE", "BA", "BG", "HR", "CY", "CZ", "DK",
            "EE", "FI", "FR", "DE", "GR", "HU", "IS", "IE", "IT", "LV",
            "LI", "LT", "LU", "MT", "MD", "MC", "ME", "NL", "MK", "NO",
            "PL", "PT", "RO", "SM", "RS", "SK", "SI", "ES", "SE", "CH",
            "TR"
        )
    }

    if (lower.contains("turkey") || lower.contains("türkiye")) {
        return listOf("TR")
    }

    return when {
        lower.contains("europe") || lower.contains("multi-country") ->
            listOf(
                "AD", "AT", "BE", "BG", "HR", "CY", "CZ", "DK", "EE", "FI",
                "FR", "DE", "GR", "HU", "IE", "IS", "IT", "LV", "LI", "LT",
                "LU", "MT", "MC", "NL", "NO", "PL", "PT", "RO", "SM", "SK",
                "SI", "ES", "SE", "CH", "GB", "VA"
            )

        else ->
            listOf("GB", "DE", "FR", "ES", "IT", "NL", "TR", "US")
    }
}


private fun renewalSupport(packageName: String, description: String): String {
    val text = "$packageName $description".lowercase()

    return when {
        text.contains("no renewal") ||
            text.contains("not renewable") ||
            text.contains("non-renew") ||
            text.contains("renewal not") ->
            "Not supported"

        text.contains("renewal") ||
            text.contains("renewable") ||
            text.contains("renew") ||
            text.contains("recharge") ||
            text.contains("top-up") ||
            text.contains("top up") ->
            "Supported"

        else ->
            "Not specified"
    }
}


private fun formatValidityLabel(raw: String): String {
    val value = raw.trim().replace(Regex("\\s+"), " ")
    if (value.isBlank()) return value

    val match = Regex("^(\\d+)\\s*(day|days|d)$", RegexOption.IGNORE_CASE).find(value)
    if (match != null) {
        val days = match.groupValues[1]
        return "$days Days"
    }

    return value
        .replace(" day", " Days", ignoreCase = true)
        .replace(" days", " Days", ignoreCase = true)
}

private fun coverageCountLabel(packageName: String, coverage: String, countryLine: String): String {
    val count = supportedCountryCodes(packageName, coverage, countryLine).size
    if (count > 0) {
        return if (count == 1) "1 Country" else "$count Countries"
    }

    val text = "$coverage $countryLine"
    val match = Regex("(\\d+)\\+?\\s*(countries|country)", RegexOption.IGNORE_CASE).find(text)
    if (match != null) {
        val value = match.groupValues[1]
        return if (value == "1") "1 Country" else "$value Countries"
    }

    return shortCoverage(coverage, countryLine)
}
private fun shortCoverage(coverage: String, countryLine: String): String {
    val text = "$coverage $countryLine".lowercase()
    return when {
        text.contains("turkey") || text.contains("türkiye") -> "Turkey"
        text.contains("balkan") -> "Balkans"
        text.contains("europe") || text.contains("eu") -> "Europe / multi-country"
        coverage.contains(",") || coverage.length > 36 -> "Europe / multi-country"
        coverage.isNotBlank() -> coverage
        countryLine.isNotBlank() -> countryLine
        else -> "Multi-country"
    }
}

private fun priceCurrency(price: String): String {
    val trimmed = price.trim()
    return trimmed.substringBefore(" ", "USD").ifBlank { "USD" }
}

private fun priceAmount(price: String): String {
    val trimmed = price.trim()
    return trimmed.substringAfter(" ", trimmed).ifBlank { trimmed }
}
