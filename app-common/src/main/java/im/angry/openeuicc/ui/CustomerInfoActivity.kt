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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp

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
    val blue = Color(0xFF1263F1)
    val dark = Color(0xFF07142F)
    val muted = Color(0xFF738099)
    val bg = Color(0xFFF4F8FD)

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = bg) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 18.dp, top = 14.dp, end = 18.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White)
                            .border(1.dp, Color(0xFFE2E9F3), RoundedCornerShape(16.dp))
                    ) {
                        Text("‹", color = dark, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                    }

                    Text(
                        text = "Customer Info",
                        color = dark,
                        fontSize = 27.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 52.dp),
                        textAlign = TextAlign.Center
                    )
                }

                CustomerPackageSummaryCard(
                    packageName = packageName.ifBlank { "eSIM Package" },
                    packageMeta = packageMeta,
                    packagePrice = packagePrice,
                    blue = blue
                )

                CustomerFormCard(
                    firstName = firstName,
                    lastName = lastName,
                    phone = phone,
                            firstNameError = firstNameError,
                    lastNameError = lastNameError,
                    phoneError = phoneError,
                        packagePrice = packagePrice,
                    loading = loading,
                    onContinue = onContinue,
                    onFirstNameChange = onFirstNameChange,
                    onLastNameChange = onLastNameChange,
                    onPhoneChange = onPhoneChange,
                    blue = blue,
                    dark = dark,
                    muted = muted
                )

                errorMessage?.takeIf { it.isNotBlank() }?.let {
                    CustomerResultCard(message = it)
                }
            }
        }
    }
}

@Composable
private fun CustomerPackageSummaryCard(
    packageName: String,
    packageMeta: String,
    packagePrice: String,
    blue: Color
) {
    val parts = packageMeta.split("•").map { it.trim() }.filter { it.isNotBlank() }
    val data = parts.getOrNull(0) ?: "Mobile data"
    val validity = formatCustomerValidity(parts.getOrNull(1) ?: "30 Days")

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(id = im.angry.openeuicc.common.R.drawable.store_banner),
                    contentDescription = null,
                    modifier = Modifier
                        .size(width = 118.dp, height = 82.dp)
                        .clip(RoundedCornerShape(18.dp)),
                    contentScale = ContentScale.Crop
                )

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = packageName,
                        color = Color(0xFF07142F),
                        fontWeight = FontWeight.Black,
                        fontSize = 19.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = listOf(data, validity).joinToString("  •  "),
                        color = Color(0xFF5F6B82),
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Roam2World",
                        color = Color(0xFF5F6B82),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MiniSummaryItem("Data", data, blue, Modifier.weight(1f))
                MiniSummaryItem("Validity", validity, blue, Modifier.weight(1f))
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.End
                ) {
                    Text("Price", color = Color(0xFF738099), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(packagePrice, color = blue, fontSize = 19.sp, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
private fun MiniSummaryItem(label: String, value: String, blue: Color, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFFEAF2FF)),
            contentAlignment = Alignment.Center
        ) {
            Text(if (label == "Data") "⇅" else "▣", color = blue, fontWeight = FontWeight.Black)
        }

        Spacer(modifier = Modifier.width(8.dp))

        Column {
            Text(label, color = Color(0xFF738099), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(value, color = Color(0xFF07142F), fontSize = 14.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun CustomerFormCard(
    firstName: String,
    lastName: String,
    phone: String,
    firstNameError: String?,
    lastNameError: String?,
    phoneError: String?,
    packagePrice: String,
    loading: Boolean,
    onContinue: () -> Unit,
    onFirstNameChange: (String) -> Unit,
    onLastNameChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    blue: Color,
    dark: Color,
    muted: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFFEAF2FF)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = im.angry.openeuicc.common.R.drawable.r2w_ic_customer),
                        contentDescription = null,
                        modifier = Modifier.size(30.dp),
                        contentScale = ContentScale.Fit
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text("Personal Information", color = dark, fontSize = 19.sp, fontWeight = FontWeight.Black)
                    Text("Please provide accurate details for your eSIM.", color = muted, fontSize = 13.sp)
                }
            }

            CustomerInputField(
                value = firstName,
                onValueChange = onFirstNameChange,
                label = "First Name",
                placeholder = "e.g. John",
                iconRes = im.angry.openeuicc.common.R.drawable.r2w_ic_customer,
                error = firstNameError
            )

            CustomerInputField(
                value = lastName,
                onValueChange = onLastNameChange,
                label = "Last Name",
                placeholder = "e.g. Smith",
                iconRes = im.angry.openeuicc.common.R.drawable.r2w_ic_customer,
                error = lastNameError
            )

            CustomerInputField(
                value = phone,
                onValueChange = onPhoneChange,
                label = "Phone Number",
                placeholder = "e.g. +1 555 123 4567",
                iconRes = im.angry.openeuicc.common.R.drawable.r2w_ic_phone,
                error = phoneError
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.width(132.dp)) {
                    Text("Total", color = muted, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(
                        text = packagePrice.ifBlank { "USD 0.00" },
                        color = blue,
                        fontWeight = FontWeight.Black,
                        fontSize = 22.sp
                    )
                }

                Button(
                    onClick = onContinue,
                    enabled = !loading,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = blue),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    if (loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Processing")
                    } else {
                        Text("Continue", fontWeight = FontWeight.Black, fontSize = 18.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomerInputField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    iconRes: Int,
    error: String?
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        leadingIcon = {
            Image(
                painter = painterResource(id = iconRes),
                contentDescription = label,
                modifier = Modifier.size(28.dp),
                contentScale = ContentScale.Fit
            )
        },
        isError = error != null,
        supportingText = { error?.let { Text(it, fontSize = 12.sp) } },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().height(58.dp),
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color(0xFF1263F1),
            unfocusedBorderColor = Color(0xFFDCE4F0),
            focusedContainerColor = Color.White,
            unfocusedContainerColor = Color.White
        )
    )
}

@Composable
private fun CustomerCheckRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    title: String,
    subtitle: String?,
    blue: Color,
    error: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, onValueChange = onCheckedChange)
            .padding(vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange
        )

        Spacer(modifier = Modifier.width(8.dp))

        Column {
            Text(
                title,
                color = if (error) Color(0xFFB42318) else Color(0xFF07142F),
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
            subtitle?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it,
                    color = if (error) Color(0xFFB42318) else Color(0xFF738099),
                    fontSize = 13.sp
                )
            }
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
            modifier = Modifier.padding(16.dp),
            color = Color(0xFF9A3412),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

private fun formatCustomerValidity(raw: String): String {
    val value = raw.trim().replace(Regex("\\s+"), " ")
    val match = Regex("^(\\d+)\\s*(day|days|d)$", RegexOption.IGNORE_CASE).find(value)
    return if (match != null) "${match.groupValues[1]} Days" else value
}
