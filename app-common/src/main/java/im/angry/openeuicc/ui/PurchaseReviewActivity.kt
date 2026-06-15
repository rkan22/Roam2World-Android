package im.angry.openeuicc.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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

class PurchaseReviewActivity : ComponentActivity() {
    private val tokenStore by lazy { AuthTokenStore(this) }
    private val authApi by lazy { Roam2WorldAuthApi(BuildConfig.ROAM2WORLD_API_BASE_URL) }

    private var loading by mutableStateOf(false)
    private var errorMessage by mutableStateOf<String?>(null)
    private var simIccid by mutableStateOf("")
    private var simIccidError by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            PurchaseReviewScreen(
                customerName = customerName(),
                customerPhone = intent.getStringExtra(EXTRA_CUSTOMER_PHONE).orEmpty(),
                packageName = PackageNameCleaner.clean(intent.getStringExtra(EXTRA_NAME)),
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
                simIccidError = "ICCID is required for this SIMCARD Europe package"
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
                .onFailure {
                    errorMessage = it.message ?: getString(R.string.package_purchase_failed)
                }
        }
    }

    private suspend fun activeSessionOrReturnToLogin(): AuthSession? {
        val savedSession = withContext(Dispatchers.IO) { tokenStore.getSession() } ?: return redirectToLogin()
        if (!JwtUtils.isExpired(savedSession.accessToken)) return savedSession

        val refreshed = runCatching {
            withContext(Dispatchers.IO) { authApi.refresh(savedSession) }
        }.getOrNull() ?: return redirectToLogin()

        withContext(Dispatchers.IO) { tokenStore.save(refreshed) }
        return refreshed
    }

    private fun redirectToLogin(): AuthSession? {
        tokenStore.clear()
        startActivity(
            Intent(this, LoginActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
        )
        finish()
        return null
    }

    private fun requiresSimIccid(): Boolean {
        val id = intent.getStringExtra(EXTRA_ID).orEmpty()
        val provider = intent.getStringExtra(EXTRA_PROVIDER).orEmpty().lowercase()
        val name = intent.getStringExtra(EXTRA_NAME).orEmpty()
        val description = intent.getStringExtra(EXTRA_DESCRIPTION).orEmpty()
        val haystack = "$id $name $description".lowercase()

        return provider.contains("tgt") &&
            (
                haystack.contains("simcard】europe（41）") ||
                    haystack.contains("simcard]europe(41)")
            )
    }

    private fun simIccidOrNull(): String? =
        simIccid.trim().takeIf { it.isNotBlank() }

    private fun priceAmount(): BigDecimal =
        decimalAmount(intent.getStringExtra(EXTRA_PRICE)) ?: BigDecimal.ZERO.setScale(2)

    private fun totalAmount(): BigDecimal =
        priceAmount().add(BigDecimal.ZERO.setScale(2)).setScale(2, RoundingMode.HALF_UP)

    private fun balanceAfterText(): String {
        val balance = decimalAmount(intent.getStringExtra(EXTRA_CURRENT_BALANCE))
        val after = balance?.subtract(totalAmount())?.setScale(2, RoundingMode.HALF_UP)
        return after?.let { money(it) } ?: "--"
    }

    private fun decimalAmount(value: String?): BigDecimal? {
        val normalized = value
            ?.trim()
            ?.replace(",", ".")
            ?.replace(Regex("[^0-9.-]"), "")
            ?.takeIf { it.isNotBlank() }
        return normalized?.toBigDecimalOrNull()?.setScale(2, RoundingMode.HALF_UP)
    }

    private fun money(value: BigDecimal): String =
        "$${value.setScale(2, RoundingMode.HALF_UP)}"

    private fun customerName(): String =
        listOfNotNull(
            intent.getStringExtra(EXTRA_CUSTOMER_FIRST_NAME),
            intent.getStringExtra(EXTRA_CUSTOMER_LAST_NAME)
        )
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .ifBlank { "Customer" }

    private fun displayProvider(): String {
        val provider = intent.getStringExtra(EXTRA_PROVIDER).orEmpty()
        return when {
            provider.contains("tgt", ignoreCase = true) -> "Orange"
            provider.isNotBlank() -> provider.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            else -> "Roam2World"
        }
    }

    private fun isDemoPackage(): Boolean =
        intent.getStringExtra(EXTRA_ID)?.startsWith("demo-") == true

    private fun demoPurchaseResult(price: String): MobilePackagePurchaseResult =
        MobilePackagePurchaseResult(
            orderId = "demo-${UUID.randomUUID()}",
            orderNumber = "DEMO-${System.currentTimeMillis().toString().takeLast(6)}",
            status = "demo_success",
            packageName = intent.getStringExtra(EXTRA_NAME) ?: "eSIM Package",
            price = price,
            balanceAfter = intent.getStringExtra(EXTRA_CURRENT_BALANCE),
            activation = MobileActivationDetails(
                lpaCode = null,
                smdpAddress = null,
                matchingId = null,
                confirmationCodeRequired = false,
                qrCode = null,
                qrCodeUrl = null,
                iccid = "DEMO-ICCID",
                esimId = intent.getStringExtra(EXTRA_ID)
            )
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

        fun createIntent(
            context: Context,
            packageIntent: Intent,
            customerFirstName: String,
            customerLastName: String,
            customerPhone: String,
            currentBalance: String?
        ): Intent = Intent(context, PurchaseReviewActivity::class.java).apply {
            listOf(
                EXTRA_ID,
                EXTRA_PROVIDER,
                EXTRA_TYPE,
                EXTRA_NAME,
                EXTRA_COUNTRY,
                EXTRA_COUNTRY_CODE,
                EXTRA_PRICE,
                EXTRA_ROLE,
                EXTRA_VISIBILITY,
                EXTRA_DATA,
                EXTRA_VALIDITY,
                EXTRA_NETWORK,
                EXTRA_COVERAGE,
                EXTRA_DESCRIPTION
            ).forEach { key ->
                putExtra(key, packageIntent.getStringExtra(key))
            }
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

                ReviewHeroCard(orange = orange)

                ReviewInfoCard(title = "Müşteri") {
                    ReviewLine("Customer", customerName)
                    ReviewLine("Phone", customerPhone.ifBlank { "--" })
                }

                ReviewInfoCard(title = "Paket") {
                    ReviewLine("Package", packageName.ifBlank { "eSIM Package" })
                    ReviewLine("Provider", provider)
                }

                ReviewInfoCard(title = "Ödeme Özeti") {
                    ReviewLine("Package Price", packagePrice)
                    ReviewLine("Tax", tax)
                    HorizontalDivider()
                    ReviewLine("Total", total, strong = true)
                    ReviewLine("Current Balance", currentBalance)
                    ReviewLine("Balance After Purchase", balanceAfter)
                }

                if (requiresSimIccid) {
                    ReviewInfoCard(title = "SIM ICCID") {
                        Text(
                            text = "Bu SIMCARD Europe paketi için fiziksel SIM üzerindeki ICCID gerekiyor.",
                            color = Color(0xFF6B7280),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        OutlinedTextField(
                            value = simIccid,
                            onValueChange = onSimIccidChange,
                            label = { Text("SIM ICCID") },
                            isError = simIccidError != null,
                            supportingText = { simIccidError?.let { Text(it) } },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                errorMessage?.takeIf { it.isNotBlank() }?.let {
                    ReviewResultCard(it)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = onCancel,
                        enabled = !loading,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text("Cancel")
                    }

                    Button(
                        onClick = onConfirm,
                        enabled = !loading,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = orange),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        if (loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.height(18.dp),
                                color = Color.White
                            )
                        } else {
                            Text("Confirm")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun ReviewHeroCard(orange: Color) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(30.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF17181C))
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Confirm Purchase",
                color = orange,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Satın alma özeti",
                color = Color.White,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Bilgileri kontrol et, ardından satın alma isteğini gönder.",
                color = Color.White.copy(alpha = 0.74f),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun ReviewInfoCard(
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
private fun ReviewLine(
    label: String,
    value: String,
    strong: Boolean = false
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            color = Color(0xFF6B7280),
            style = MaterialTheme.typography.labelMedium
        )
        Text(
            text = value,
            color = Color(0xFF17181C),
            style = if (strong) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
            fontWeight = if (strong) FontWeight.Bold else FontWeight.SemiBold
        )
    }
}

@Composable
private fun ReviewResultCard(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF7ED))
    ) {
        Text(
            text = message,
            color = Color(0xFFC2410C),
            modifier = Modifier.padding(18.dp),
            fontWeight = FontWeight.SemiBold
        )
    }
}
