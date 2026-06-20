package im.angry.openeuicc.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import im.angry.openeuicc.auth.AuthTokenStore
import im.angry.openeuicc.auth.JwtUtils
import im.angry.openeuicc.auth.Roam2WorldAuthApi
import im.angry.openeuicc.common.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CustomerInfoActivity : ComponentActivity() {
    private val tokenStore by lazy { AuthTokenStore(this) }
    private val authApi by lazy { Roam2WorldAuthApi(BuildConfig.ROAM2WORLD_API_BASE_URL) }

    private var firstName by mutableStateOf("")
    private var lastName by mutableStateOf("")
    private var phone by mutableStateOf("")
    private var loading by mutableStateOf(false)
    private var errorMessage by mutableStateOf<String?>(null)

    private var firstNameError by mutableStateOf<String?>(null)
    private var lastNameError by mutableStateOf<String?>(null)
    private var phoneError by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            CustomerInfoScreen(
                packageName = PackageNameCleaner.clean(intent.getStringExtra(EXTRA_NAME)),
                packageMeta = packageMeta(),
                packagePrice = intent.getStringExtra(EXTRA_PRICE) ?: "0",
                firstName = firstName,
                lastName = lastName,
                phone = phone,
                loading = loading,
                errorMessage = errorMessage,
                firstNameError = firstNameError,
                lastNameError = lastNameError,
                phoneError = phoneError,
                onBack = { finish() },
                onFirstNameChange = { firstName = it },
                onLastNameChange = { lastName = it },
                onPhoneChange = { phone = it },
                onContinue = {
                    if (validateForm()) {
                        openReviewWithWalletBalance()
                    }
                }
            )
        }
    }

    private fun packageMeta(): String =
        listOfNotNull(
            intent.getStringExtra(EXTRA_DATA),
            intent.getStringExtra(EXTRA_VALIDITY),
            intent.getStringExtra(EXTRA_COUNTRY)
        )
            .filter { it.isNotBlank() }
            .joinToString("  •  ")

    private fun validateForm(): Boolean {
        firstNameError = null
        lastNameError = null
        phoneError = null
        errorMessage = null

        var valid = true

        if (firstName.trim().isBlank()) {
            firstNameError = "First name is required"
            valid = false
        }

        if (lastName.trim().isBlank()) {
            lastNameError = "Last name is required"
            valid = false
        }

        if (phone.trim().isBlank()) {
            phoneError = "Phone number is required"
            valid = false
        }

        return valid
    }

    private fun openReviewWithWalletBalance() {
        lifecycleScope.launch {
            loading = true
            errorMessage = null

            val currentBalance = runCatching {
                val session = withContext(Dispatchers.IO) { tokenStore.getSession() }
                if (session == null || JwtUtils.isExpired(session.accessToken)) {
                    null
                } else {
                    withContext(Dispatchers.IO) { authApi.wallet(session).currentBalance }
                }
            }.getOrNull()

            loading = false

            startActivity(
                PurchaseReviewActivity.createIntent(
                    context = this@CustomerInfoActivity,
                    intent = intent,
                    first = firstName.trim(),
                    last = lastName.trim(),
                    phone = phone.trim(),
                    balance = currentBalance
                )
            )
        }
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

        fun createIntent(context: Context, packageIntent: Intent): Intent =
            Intent(context, CustomerInfoActivity::class.java).apply {
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
            }
    }
}

@Composable
private fun CustomerInfoScreen(
    packageName: String,
    packageMeta: String,
    packagePrice: String,
    firstName: String,
    lastName: String,
    phone: String,
    loading: Boolean,
    errorMessage: String?,
    firstNameError: String?,
    lastNameError: String?,
    phoneError: String?,
    onBack: () -> Unit,
    onFirstNameChange: (String) -> Unit,
    onLastNameChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    onContinue: () -> Unit
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

                CustomerHeroCard(orange = orange)

                CustomerInfoCard(title = "Seçilen Paket") {
                    Text(
                        text = packageName.ifBlank { "eSIM Package" },
                        color = Color(0xFF17181C),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    if (packageMeta.isNotBlank()) {
                        Text(
                            text = packageMeta,
                            color = Color(0xFF6B7280),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    Text(
                        text = packagePrice,
                        color = orange,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                CustomerInfoCard(title = "Müşteri Bilgileri") {
                    OutlinedTextField(
                        value = firstName,
                        onValueChange = onFirstNameChange,
                        label = { Text("First name") },
                        isError = firstNameError != null,
                        supportingText = { firstNameError?.let { Text(it) } },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = lastName,
                        onValueChange = onLastNameChange,
                        label = { Text("Last name") },
                        isError = lastNameError != null,
                        supportingText = { lastNameError?.let { Text(it) } },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = phone,
                        onValueChange = onPhoneChange,
                        label = { Text("Phone number") },
                        isError = phoneError != null,
                        supportingText = { phoneError?.let { Text(it) } },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                errorMessage?.takeIf { it.isNotBlank() }?.let {
                    CustomerResultCard(message = it)
                }

                Button(
                    onClick = onContinue,
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = orange),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    if (loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.height(18.dp),
                            color = Color.White
                        )
                        Text(" Devam ediliyor...")
                    } else {
                        Text("Continue")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun CustomerHeroCard(orange: Color) {
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
                text = "Customer Info",
                color = orange,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Satın alma bilgileri",
                color = Color.White,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Paket aktivasyonu için müşteri adını ve telefon bilgisini gir.",
                color = Color.White.copy(alpha = 0.74f),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun CustomerInfoCard(
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
private fun CustomerResultCard(message: String) {
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
