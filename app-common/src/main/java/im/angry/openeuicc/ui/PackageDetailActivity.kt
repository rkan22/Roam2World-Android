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
        return "Stay connected with fast and reliable mobile data using $name."
    }

    private fun codeToFlag(code: String): String {
        val normalized = code.trim().uppercase()
        if (normalized.length != 2 || normalized.any { it !in 'A'..'Z' }) return ""
        val first = Character.codePointAt(normalized, 0) - 'A'.code + 0x1F1E6
        val second = Character.codePointAt(normalized, 1) - 'A'.code + 0x1F1E6
        return String(Character.toChars(first)) + String(Character.toChars(second))
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
    onBack: () -> Unit,
    onBuy: () -> Unit
) {
    val orange = Color(0xFFFF7900)
    val bg = Color(0xFFF7F7FA)

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = bg) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedButton(onClick = onBack, shape = RoundedCornerShape(16.dp)) {
                    Text("Geri")
                }

                PackageHeroCard(
                    packageName = packageName,
                    countryLine = countryLine,
                    price = price,
                    orange = orange
                )

                PackageInfoCard(title = "Paket Detayları") {
                    if (data.isNotBlank()) DetailLine("Data", data)
                    if (validity.isNotBlank()) DetailLine("Validity", validity)
                    DetailLine("Network", network)
                    DetailLine("Coverage", coverage)
                    if (visibility.isNotBlank()) DetailLine("Visibility", visibility)
                }

                PackageInfoCard(title = "Açıklama") {
                    Text(
                        text = description,
                        color = Color(0xFF4B5563),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Button(
                    onClick = onBuy,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = orange),
                    shape = RoundedCornerShape(22.dp)
                ) {
                    Text(
                        text = "Satın Almaya Devam Et",
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun PackageHeroCard(
    packageName: String,
    countryLine: String,
    price: String,
    orange: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF17181C))
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .width(48.dp)
                        .height(48.dp)
                        .clip(CircleShape)
                        .background(orange),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "R2W",
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = "Roam2World eSIM",
                        color = orange,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = countryLine.ifBlank { "Global" },
                        color = Color.White.copy(alpha = 0.70f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Text(
                text = packageName,
                color = Color.White,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Stay connected with fast and reliable data.",
                color = Color.White.copy(alpha = 0.74f),
                style = MaterialTheme.typography.bodyMedium
            )

            Text(
                text = price,
                color = orange,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black
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
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = title,
                color = Color(0xFF17181C),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            HorizontalDivider()
            content()
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            color = Color(0xFF6B7280),
            style = MaterialTheme.typography.labelMedium
        )
        Text(
            text = value,
            color = Color(0xFF17181C),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold
        )
    }
}
