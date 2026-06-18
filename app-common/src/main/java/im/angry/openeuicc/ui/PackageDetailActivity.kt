package im.angry.openeuicc.ui

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowInsetsControllerCompat
import im.angry.openeuicc.auth.MobilePackage
import im.angry.openeuicc.ui.compose.screens.CompactPackageDetailScreen
import java.text.NumberFormat
import java.util.Locale

class PackageDetailActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        actionBar?.hide()
        configureSystemBars()

        setContent {
            CompactPackageDetailScreen(
                packageName = displayPackageName(),
                price = r2wMoney(intent.getStringExtra(EXTRA_PRICE).orEmpty()),
                data = intent.getStringExtra(EXTRA_DATA).orEmpty(),
                validity = normalizeValidity(intent.getStringExtra(EXTRA_VALIDITY).orEmpty()),
                network = displayProvider(),
                coverage = coverageDisplay(),
                description = descriptionText(),
                onBack = { finish() },
                onBuy = { startActivity(CustomerInfoActivity.createIntent(this, intent)) }
            )
        }
    }

    private fun configureSystemBars() {
        window.statusBarColor = Color.rgb(248, 250, 255)
        window.navigationBarColor = Color.BLACK
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = false
        }
    }

    private fun displayPackageName(): String {
        val rawName = intent.getStringExtra(EXTRA_NAME).orEmpty()
        val data = intent.getStringExtra(EXTRA_DATA)
        val country = intent.getStringExtra(EXTRA_COUNTRY)
        val countryCode = intent.getStringExtra(EXTRA_COUNTRY_CODE)
        val coverage = intent.getStringExtra(EXTRA_COVERAGE)
        val network = intent.getStringExtra(EXTRA_NETWORK)
        val allText = listOfNotNull(rawName, country, countryCode, coverage, network).joinToString(" ").lowercase()
        val region = when {
            allText.contains("balkan") -> "Balkans"
            allText.contains("europe") || allText.contains("eu ") || allText.contains("europa") -> "Europe"
            allText.contains("turkey") || allText.contains("türkiye") || countryCode.equals("TR", ignoreCase = true) -> "Turkey"
            country?.isNotBlank() == true && !country.equals("Multi-country", ignoreCase = true) -> country
            else -> "World"
        }
        val dataLabel = data?.trim()?.takeIf { it.isNotBlank() } ?: extractDataFromName(rawName)
        return listOfNotNull(displayProviderPrefix(), region, dataLabel)
            .joinToString(" ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .ifBlank { PackageNameCleaner.clean(rawName).ifBlank { "Roam2World eSIM Package" } }
    }

    private fun displayProviderPrefix(): String =
        if (displayProvider().contains("vodafone", true)) "Vodafone" else "Orange"

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
        ).filterNotNull().joinToString(" ").lowercase()
        return when {
            joined.contains("vodafone") -> "Vodafone Europe"
            joined.contains("balkan") -> "Orange Balkans"
            joined.contains("europe") -> "Orange Europe"
            joined.contains("orange") -> "Orange"
            else -> "Roam2World"
        }
    }

    private fun coverageDisplay(): String =
        intent.getStringExtra(EXTRA_COVERAGE)?.takeIf { it.isNotBlank() } ?: "150+ Countries"

    private fun descriptionText(): String {
        val rawDescription = intent.getStringExtra(EXTRA_DESCRIPTION)?.takeIf { it.isNotBlank() }
        if (rawDescription != null) return rawDescription
        return "Stay connected with fast and reliable data."
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

private fun r2wMoney(raw: String): String {
    val numeric = raw.filter { it.isDigit() || it == '.' }.toDoubleOrNull() ?: return raw
    return NumberFormat.getCurrencyInstance(Locale.US).format(numeric)
}

private fun normalizeValidity(raw: String): String {
    val value = raw.trim()
    if (value.isBlank()) return "30 Days"
    val days = Regex("""(\d+)\s*day[s]?""", RegexOption.IGNORE_CASE).find(value)?.groupValues?.getOrNull(1)
    if (days != null) return "$days Days"
    return value.replace(" day", " Days", ignoreCase = true).replace(" days", " Days", ignoreCase = true)
}
