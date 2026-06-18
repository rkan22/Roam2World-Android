package im.angry.openeuicc.ui

import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import im.angry.openeuicc.auth.AuthSession
import im.angry.openeuicc.auth.AuthTokenStore
import im.angry.openeuicc.auth.JwtUtils
import im.angry.openeuicc.auth.MobileActivationDetails
import im.angry.openeuicc.auth.MobilePackagePurchaseRequest
import im.angry.openeuicc.auth.MobilePackagePurchaseResult
import im.angry.openeuicc.auth.Roam2WorldAuthApi
import im.angry.openeuicc.common.BuildConfig
import im.angry.openeuicc.common.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

private val ReviewBlue = Color(0xFF1263F1)
private val ReviewBlueDark = Color(0xFF0649B8)
private val ReviewText = Color(0xFF111827)
private val ReviewMuted = Color(0xFF6B7280)
private val ReviewBorder = Color(0xFFE5E7EB)
private val ReviewBg = Color(0xFFF8FAFF)
private val ReviewRed = Color(0xFFEF4444)

class PurchaseReviewActivity : ComponentActivity() {
    private val tokenStore by lazy { AuthTokenStore(this) }
    private val authApi by lazy { Roam2WorldAuthApi(BuildConfig.ROAM2WORLD_API_BASE_URL) }

    private var loading by mutableStateOf(false)
    private var errorMessage by mutableStateOf<String?>(null)
    private var simIccid by mutableStateOf("")
    private var simIccidError by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        actionBar?.hide()
        configureSystemBars()

        setContent {
            PurchaseReviewScreen(
                customerName = customerName(),
                customerPhone = intent.getStringExtra(EXTRA_CUSTOMER_PHONE).orEmpty(),
                packageName = displayPackageName(),
                provider = displayProvider(),
                packagePrice = money(priceAmount()),
                tax = money(BigDecimal.ZERO.setScale(2)),
                total = money(totalAmount()),
                currentBalance = r2wMoney(intent.getStringExtra(EXTRA_CURRENT_BALANCE)) ?: "--",
                balanceAfter = balanceAfterText(),
                requiresSimIccid = requiresSimIccid(),
                simIccid = simIccid,
                simIccidError = simIccidError,
                loading = loading,
                errorMessage = errorMessage,
                onBack = { finish() },
                onCancel = { finish() },
                onSimIccidChange = { simIccid = it },
                onConfirm = { confirmPurchase() }
            )
        }
    }

    private fun configureSystemBars() {
        window.statusBarColor = AndroidColor.rgb(18, 99, 241)
        window.navigationBarColor = AndroidColor.BLACK
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
    }

    private fun confirmPurchase() {
        lifecycleScope.launch {
            errorMessage = null
            simIccidError = null
            loading = true
            val session = activeSessionOrReturnToLogin()
            if (session == null) {
                loading = false
                return@launch
            }
            if (requiresSimIccid() && simIccidOrNull() == null) {
                simIccidError = "ICCID is required for this SIM package"
                errorMessage = "Enter the ICCID printed on the SIM card to continue."
                loading = false
                return@launch
            }
            val price = intent.getStringExtra(EXTRA_PRICE) ?: "0"
            if (isDemoPackage()) {
                loading = false
                startActivity(PurchaseConfirmationActivity.createIntent(this@PurchaseReviewActivity, demoPurchaseResult(price)))
                finish()
                return@launch
            }
            val purchase = runCatching {
                withContext(Dispatchers.IO) {
                    authApi.purchasePackage(
                        session,
                        MobilePackagePurchaseRequest(
                            packageId = intent.getStringExtra(EXTRA_ID),
                            provider = intent.getStringExtra(EXTRA_PROVIDER),
                            packageName = intent.getStringExtra(EXTRA_NAME) ?: "eSIM Package",
                            packageDescription = intent.getStringExtra(EXTRA_DESCRIPTION),
                            country = intent.getStringExtra(EXTRA_COUNTRY),
                            price = price,
                            role = intent.getStringExtra(EXTRA_ROLE),
                            customerFirstName = intent.getStringExtra(EXTRA_CUSTOMER_FIRST_NAME),
                            customerLastName = intent.getStringExtra(EXTRA_CUSTOMER_LAST_NAME),
                            customerPhone = intent.getStringExtra(EXTRA_CUSTOMER_PHONE),
                            simIccid = simIccidOrNull()
                        )
                    )
                }
            }
            loading = false
            purchase
                .onSuccess {
                    startActivity(PurchaseConfirmationActivity.createIntent(this@PurchaseReviewActivity, it))
                    finish()
                }
                .onFailure { errorMessage = it.message ?: getString(R.string.package_purchase_failed) }
        }
    }

    private suspend fun activeSessionOrReturnToLogin(): AuthSession? {
        val savedSession = withContext(Dispatchers.IO) { tokenStore.getSession() } ?: return redirectToLogin()
        if (!JwtUtils.isExpired(savedSession.accessToken)) return savedSession
        val refreshed = runCatching { withContext(Dispatchers.IO) { authApi.refresh(savedSession) } }.getOrNull() ?: return redirectToLogin()
        withContext(Dispatchers.IO) { tokenStore.save(refreshed) }
        return refreshed
    }

    private fun redirectToLogin(): AuthSession? {
        tokenStore.clear()
        startActivity(Intent(this, LoginActivity::class.java).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK) })
        finish()
        return null
    }

    private fun requiresSimIccid(): Boolean {
        val id = intent.getStringExtra(EXTRA_ID).orEmpty()
        val provider = intent.getStringExtra(EXTRA_PROVIDER).orEmpty().lowercase()
        val name = intent.getStringExtra(EXTRA_NAME).orEmpty()
        val description = intent.getStringExtra(EXTRA_DESCRIPTION).orEmpty()
        val haystack = "$id $name $description".lowercase()
        return provider.contains("tgt") && (haystack.contains("simcard】europe（41）") || haystack.contains("simcard]europe(41)"))
    }

    private fun simIccidOrNull(): String? = simIccid.trim().takeIf { it.isNotBlank() }
    private fun priceAmount(): BigDecimal = decimalAmount(intent.getStringExtra(EXTRA_PRICE)) ?: BigDecimal.ZERO.setScale(2)
    private fun totalAmount(): BigDecimal = priceAmount().add(BigDecimal.ZERO.setScale(2)).setScale(2, RoundingMode.HALF_UP)

    private fun balanceAfterText(): String {
        val balance = decimalAmount(intent.getStringExtra(EXTRA_CURRENT_BALANCE))
        val after = balance?.subtract(totalAmount())?.setScale(2, RoundingMode.HALF_UP)
        return after?.let { money(it) } ?: "--"
    }

    private fun decimalAmount(value: String?): BigDecimal? {
        val normalized = value?.trim()?.replace(",", ".")?.replace(Regex("[^0-9.-]"), "")?.takeIf { it.isNotBlank() }
        return normalized?.toBigDecimalOrNull()?.setScale(2, RoundingMode.HALF_UP)
    }

    private fun money(value: BigDecimal): String = "$${value.setScale(2, RoundingMode.HALF_UP)}"
    private fun customerName(): String = listOfNotNull(intent.getStringExtra(EXTRA_CUSTOMER_FIRST_NAME), intent.getStringExtra(EXTRA_CUSTOMER_LAST_NAME)).filter { it.isNotBlank() }.joinToString(" ").ifBlank { "Customer" }

    private fun displayProvider(): String {
        val provider = intent.getStringExtra(EXTRA_PROVIDER).orEmpty()
        val network = intent.getStringExtra(EXTRA_NETWORK).orEmpty()
        val joined = "$provider $network ${intent.getStringExtra(EXTRA_NAME).orEmpty()}".lowercase()
        return when {
            joined.contains("vodafone") -> "Vodafone"
            provider.contains("tgt", ignoreCase = true) || joined.contains("orange") -> "Orange"
            provider.isNotBlank() -> provider.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            else -> "Roam2World"
        }
    }

    private fun displayPackageName(): String {
        val provider = if (displayProvider().contains("vodafone", true)) "Vodafone" else "Orange"
        val region = displayRegion()
        val data = intent.getStringExtra(EXTRA_DATA)?.trim().orEmpty().ifBlank { extractDataFromName(intent.getStringExtra(EXTRA_NAME).orEmpty()) }
        return listOf(provider, region, data).filter { it.isNotBlank() }.joinToString(" ").ifBlank { PackageNameCleaner.clean(intent.getStringExtra(EXTRA_NAME)) }
    }

    private fun displayRegion(): String {
        val text = listOfNotNull(intent.getStringExtra(EXTRA_COUNTRY), intent.getStringExtra(EXTRA_COUNTRY_CODE), intent.getStringExtra(EXTRA_COVERAGE), intent.getStringExtra(EXTRA_NAME)).joinToString(" ").lowercase()
        return when {
            text.contains("turkey") || text.contains("türkiye") || text == "tr" -> "Turkey"
            text.contains("balkan") -> "Balkans"
            text.contains("europe") -> "Europe"
            text.contains("world") || text.contains("global") || text.contains("multi-country") -> "World"
            else -> intent.getStringExtra(EXTRA_COUNTRY)?.takeIf { it.isNotBlank() } ?: "World"
        }
    }

    private fun extractDataFromName(rawName: String): String = Regex("""(\d+(?:\.\d+)?)\s*GB""", RegexOption.IGNORE_CASE).find(rawName)?.value?.uppercase()?.replace("GB", " GB")?.replace(Regex("\\s+"), " ")?.trim().orEmpty()
    private fun isDemoPackage(): Boolean = intent.getStringExtra(EXTRA_ID)?.startsWith("demo-") == true

    private fun demoPurchaseResult(price: String): MobilePackagePurchaseResult =
        MobilePackagePurchaseResult(
            orderId = "demo-${UUID.randomUUID()}",
            orderNumber = "DEMO-${System.currentTimeMillis().toString().takeLast(6)}",
            status = "demo_success",
            packageName = intent.getStringExtra(EXTRA_NAME) ?: "eSIM Package",
            price = price,
            balanceAfter = intent.getStringExtra(EXTRA_CURRENT_BALANCE),
            activation = MobileActivationDetails(lpaCode = null, smdpAddress = null, matchingId = null, confirmationCodeRequired = false, qrCode = null, qrCodeUrl = null, iccid = "DEMO-ICCID", esimId = intent.getStringExtra(EXTRA_ID))
        )

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
        private const val EXTRA_CUSTOMER_FIRST_NAME = "customer.first_name"
        private const val EXTRA_CUSTOMER_LAST_NAME = "customer.last_name"
        private const val EXTRA_CUSTOMER_PHONE = "customer.phone"
        private const val EXTRA_CURRENT_BALANCE = "wallet.current_balance"

        fun createIntent(context: Context, packageIntent: Intent, customerFirstName: String, customerLastName: String, customerPhone: String, currentBalance: String?): Intent = Intent(context, PurchaseReviewActivity::class.java).apply {
            listOf(EXTRA_ID, EXTRA_PROVIDER, EXTRA_TYPE, EXTRA_NAME, EXTRA_COUNTRY, EXTRA_COUNTRY_CODE, EXTRA_PRICE, EXTRA_ROLE, EXTRA_VISIBILITY, EXTRA_DATA, EXTRA_VALIDITY, EXTRA_NETWORK, EXTRA_COVERAGE, EXTRA_DESCRIPTION).forEach { key -> putExtra(key, packageIntent.getStringExtra(key)) }
            putExtra(EXTRA_CUSTOMER_FIRST_NAME, customerFirstName)
            putExtra(EXTRA_CUSTOMER_LAST_NAME, customerLastName)
            putExtra(EXTRA_CUSTOMER_PHONE, customerPhone)
            putExtra(EXTRA_CURRENT_BALANCE, currentBalance)
        }
    }
}

@Composable
private fun PurchaseReviewScreen(
    customerName: String,
    customerPhone: String,
    packageName: String,
    provider: String,
    packagePrice: String,
    tax: String,
    total: String,
    currentBalance: String,
    balanceAfter: String,
    requiresSimIccid: Boolean,
    simIccid: String,
    simIccidError: String?,
    loading: Boolean,
    errorMessage: String?,
    onBack: () -> Unit,
    onCancel: () -> Unit,
    onSimIccidChange: (String) -> Unit,
    onConfirm: () -> Unit
) {
    Surface(Modifier.fillMaxSize(), color = ReviewBg) {
        Column(Modifier.fillMaxSize()) {
            Header(onBack)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 18.dp)
                    .padding(bottom = 160.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                ReviewCard("Order Summary", Icons.Default.Person) {
                    ReviewRow(Icons.Default.Person, "Customer", customerName)
                    ReviewRow(Icons.Default.Phone, "Phone", customerPhone.ifBlank { "--" })
                    ReviewRow(Icons.Default.SimCard, "Package", packageName.ifBlank { "eSIM Package" }, valueMaxLines = 2)
                    ReviewRow(Icons.Default.Business, "Provider", provider)
                }
                ReviewCard("Price Breakdown", Icons.Default.AttachMoney) {
                    ReviewRow(Icons.Default.Sell, "Package Price", packagePrice)
                    ReviewRow(Icons.Default.Sell, "Tax", tax)
                    HorizontalDivider(color = ReviewBorder)
                    TotalRow(total)
                }
                ReviewCard("Wallet Balance", Icons.Default.CreditCard) {
                    ReviewRow(Icons.Default.CreditCard, "Current Balance", currentBalance)
                    ReviewRow(Icons.Default.CreditCard, "Balance After Purchase", balanceAfter)
                }
                if (requiresSimIccid) {
                    ReviewCard("Physical SIM ICCID", Icons.Default.SimCard) {
                        Text("Enter the ICCID printed on the SIM card to continue.", color = ReviewMuted, fontSize = 13.sp)
                        OutlinedTextField(
                            value = simIccid,
                            onValueChange = onSimIccidChange,
                            label = { Text("SIM ICCID") },
                            isError = simIccidError != null,
                            supportingText = { simIccidError?.let { Text(it) } },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ReviewBlue, unfocusedBorderColor = ReviewBorder)
                        )
                    }
                }
                errorMessage?.takeIf { it.isNotBlank() }?.let { ErrorCard(it) }
            }
            BottomActions(loading, onConfirm, onCancel)
        }
    }
}

@Composable
private fun Header(onBack: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().height(92.dp).background(Brush.horizontalGradient(listOf(ReviewBlue, ReviewBlueDark)))) {
        Icon(Icons.Default.ArrowBack, null, tint = Color.White, modifier = Modifier.align(Alignment.CenterStart).padding(start = 18.dp).size(30.dp).clickable(onClick = onBack))
        Text("Confirm Purchase", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.align(Alignment.Center))
    }
}

@Composable
private fun ReviewCard(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(Color.White), border = BorderStroke(1.dp, ReviewBorder), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(46.dp).clip(RoundedCornerShape(999.dp)).background(ReviewBlue.copy(alpha = .10f)), contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = ReviewBlue, modifier = Modifier.size(26.dp))
                }
                Text(title, color = ReviewText, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(start = 12.dp))
            }
            content()
        }
    }
}

@Composable
private fun ReviewRow(icon: ImageVector, label: String, value: String, valueMaxLines: Int = 1) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = ReviewMuted, modifier = Modifier.size(22.dp))
        Text(label, color = ReviewMuted, fontSize = 15.sp, modifier = Modifier.padding(start = 14.dp).weight(1f))
        Text(value, color = ReviewText, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = valueMaxLines, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun TotalRow(total: String) {
    Surface(color = ReviewBlue.copy(alpha = .08f), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, ReviewBlue.copy(alpha = .12f))) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Total", color = ReviewBlue, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
            Text(total, color = ReviewBlue, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
private fun ErrorCard(message: String) {
    Surface(color = Color(0xFFFFEAEA), shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, Color(0xFFFFCACA))) {
        Text(message, color = Color(0xFFB91C1C), modifier = Modifier.padding(14.dp), fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun BottomActions(loading: Boolean, onConfirm: () -> Unit, onCancel: () -> Unit) {
    Surface(Modifier.fillMaxWidth(), color = ReviewBg.copy(alpha = .98f), shadowElevation = 8.dp) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = onConfirm, enabled = !loading, modifier = Modifier.fillMaxWidth().height(54.dp), colors = ButtonDefaults.buttonColors(containerColor = ReviewBlue), shape = RoundedCornerShape(12.dp)) {
                if (loading) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White) else {
                    Icon(Icons.Default.Security, null, tint = Color.White, modifier = Modifier.size(22.dp))
                    Text("Confirm Purchase", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(start = 10.dp))
                }
            }
            Button(onClick = onCancel, enabled = !loading, modifier = Modifier.fillMaxWidth().height(54.dp), colors = ButtonDefaults.buttonColors(containerColor = ReviewRed), shape = RoundedCornerShape(12.dp)) {
                Text("Cancel", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

private fun r2wMoney(raw: String?): String? {
    val normalized = raw?.trim()?.replace(",", ".")?.replace(Regex("[^0-9.-]"), "")?.takeIf { it.isNotBlank() }
    val decimal = normalized?.toBigDecimalOrNull()?.setScale(2, RoundingMode.HALF_UP)
    return decimal?.let { "$${it.toPlainString()}" }
}
