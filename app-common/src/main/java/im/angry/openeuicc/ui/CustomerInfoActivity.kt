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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.CalendarMonth
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import im.angry.openeuicc.auth.AuthTokenStore
import im.angry.openeuicc.auth.JwtUtils
import im.angry.openeuicc.auth.Roam2WorldAuthApi
import im.angry.openeuicc.common.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.util.Locale

private val CustomerBlue = Color(0xFF1263F1)
private val CustomerText = Color(0xFF111827)
private val CustomerMuted = Color(0xFF6B7280)
private val CustomerBorder = Color(0xFFE5E7EB)
private val CustomerBg = Color(0xFFF8FAFF)

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
        actionBar?.hide()
        configureSystemBars()
        setContent {
            CustomerInfoScreen(
                packageName = displayPackageName(),
                packageMeta = packageMeta(),
                packagePrice = r2wMoney(intent.getStringExtra(EXTRA_PRICE).orEmpty()),
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
                onCancel = { finish() },
                onContinue = { if (validateForm()) openReviewWithWalletBalance() }
            )
        }
    }

    private fun configureSystemBars() {
        window.statusBarColor = AndroidColor.rgb(248, 250, 255)
        window.navigationBarColor = AndroidColor.BLACK
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = false
        }
    }

    private fun displayPackageName(): String =
        PackageNameCleaner.clean(intent.getStringExtra(EXTRA_NAME)).ifBlank { "eSIM Package" }

    private fun packageMeta(): String = listOfNotNull(
        intent.getStringExtra(EXTRA_DATA),
        intent.getStringExtra(EXTRA_VALIDITY),
        intent.getStringExtra(EXTRA_COUNTRY)
    ).filter { it.isNotBlank() }.joinToString(" • ")

    private fun validateForm(): Boolean {
        firstNameError = null
        lastNameError = null
        phoneError = null
        errorMessage = null
        var valid = true
        if (firstName.trim().isBlank()) { firstNameError = "First name is required"; valid = false }
        if (lastName.trim().isBlank()) { lastNameError = "Last name is required"; valid = false }
        if (phone.trim().isBlank()) { phoneError = "Phone number is required"; valid = false }
        return valid
    }

    private fun openReviewWithWalletBalance() {
        lifecycleScope.launch {
            loading = true
            errorMessage = null
            val currentBalance = runCatching {
                val session = withContext(Dispatchers.IO) { tokenStore.getSession() }
                if (session == null || JwtUtils.isExpired(session.accessToken)) null else withContext(Dispatchers.IO) { authApi.wallet(session).currentBalance }
            }.getOrNull()
            loading = false
            startActivity(
                PurchaseReviewActivity.createIntent(
                    context = this@CustomerInfoActivity,
                    packageIntent = intent,
                    customerFirstName = firstName.trim(),
                    customerLastName = lastName.trim(),
                    customerPhone = phone.trim(),
                    currentBalance = currentBalance
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
                listOf(EXTRA_ID, EXTRA_PROVIDER, EXTRA_TYPE, EXTRA_NAME, EXTRA_COUNTRY, EXTRA_COUNTRY_CODE, EXTRA_PRICE, EXTRA_ROLE, EXTRA_VISIBILITY, EXTRA_DATA, EXTRA_VALIDITY, EXTRA_NETWORK, EXTRA_COVERAGE, EXTRA_DESCRIPTION).forEach { key ->
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
    onCancel: () -> Unit,
    onContinue: () -> Unit
) {
    Surface(Modifier.fillMaxSize(), color = CustomerBg) {
        Box(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 150.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ArrowBack, null, tint = CustomerText, modifier = Modifier.size(30.dp).clickable(onClick = onBack))
                    Text("Customer Information", color = CustomerText, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(start = 18.dp))
                }
                Text("Please enter the customer details below.", color = CustomerMuted, fontSize = 16.sp)

                CustomerField("First Name", "Enter first name", firstName, firstNameError, onFirstNameChange, Icons.Default.Person)
                CustomerField("Last Name", "Enter last name", lastName, lastNameError, onLastNameChange, Icons.Default.Person)
                CustomerField("Phone Number", "Enter phone number", phone, phoneError, onPhoneChange, Icons.Default.Phone)

                SelectedPackageCard(packageName, packageMeta, packagePrice)

                errorMessage?.takeIf { it.isNotBlank() }?.let { ErrorCard(it) }
            }

            Surface(Modifier.align(Alignment.BottomCenter).fillMaxWidth(), color = CustomerBg.copy(alpha = 0.96f)) {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Button(
                        onClick = onContinue,
                        enabled = !loading,
                        modifier = Modifier.fillMaxWidth().height(58.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = CustomerBlue),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        if (loading) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White) else Text("Continue", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    Text("Cancel", color = CustomerBlue, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp).clickable(onClick = onCancel))
                }
            }
        }
    }
}

@Composable
private fun CustomerField(label: String, placeholder: String, value: String, error: String?, onChange: (String) -> Unit, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, color = CustomerMuted, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            placeholder = { Text(placeholder) },
            leadingIcon = { Icon(icon, null, tint = CustomerMuted) },
            isError = error != null,
            supportingText = { error?.let { Text(it) } },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = CustomerBlue,
                unfocusedBorderColor = CustomerBorder,
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White
            )
        )
    }
}

@Composable
private fun SelectedPackageCard(packageName: String, packageMeta: String, packagePrice: String) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(Color.White), border = BorderStroke(1.dp, CustomerBorder), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(54.dp).clip(RoundedCornerShape(999.dp)).background(CustomerBlue.copy(alpha = .10f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.SimCard, null, tint = CustomerBlue, modifier = Modifier.size(30.dp))
                }
                Column(Modifier.padding(start = 14.dp)) {
                    Text("Selected Package", color = CustomerText, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                    Text(packageName, color = CustomerBlue, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (packageMeta.isNotBlank()) Text(packageMeta, color = CustomerMuted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(CustomerBorder))
            InfoRow(Icons.Default.CalendarMonth, "Billing Cycle", packageMeta.ifBlank { "30 day" })
            InfoRow(Icons.Default.Sell, "Price", packagePrice)
            InfoRow(Icons.Default.Security, "Support Level", "Priority Support")
        }
    }
}

@Composable
private fun InfoRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = CustomerBlue, modifier = Modifier.size(22.dp))
        Text(label, color = CustomerMuted, fontSize = 14.sp, modifier = Modifier.padding(start = 12.dp).weight(1f))
        Text(value, color = CustomerText, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ErrorCard(message: String) {
    Surface(color = Color(0xFFFFEAEA), shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, Color(0xFFFFCACA))) {
        Text(message, color = Color(0xFFB91C1C), modifier = Modifier.padding(14.dp), fontWeight = FontWeight.Bold)
    }
}

private fun r2wMoney(raw: String): String {
    val numeric = raw.filter { it.isDigit() || it == '.' }.toDoubleOrNull() ?: return raw
    return NumberFormat.getCurrencyInstance(Locale.US).format(numeric)
}
