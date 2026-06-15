package im.angry.openeuicc.ui

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import im.angry.openeuicc.auth.AuthSession
import im.angry.openeuicc.auth.AuthTokenStore
import im.angry.openeuicc.auth.JwtUtils
import im.angry.openeuicc.auth.Roam2WorldAuthApi
import im.angry.openeuicc.common.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.util.Locale

class AddDealerActivity : ComponentActivity() {
    private val tokenStore by lazy { AuthTokenStore(this) }
    private val authApi by lazy { Roam2WorldAuthApi(BuildConfig.ROAM2WORLD_API_BASE_URL) }

    private var loading by mutableStateOf(false)
    private var errorMessage by mutableStateOf<String?>(null)
    private var successMessage by mutableStateOf<String?>(null)

    private var firstName by mutableStateOf("")
    private var lastName by mutableStateOf("")
    private var email by mutableStateOf("")
    private var phone by mutableStateOf("")
    private var countryCode by mutableStateOf("")
    private var password by mutableStateOf("")
    private var initialBalance by mutableStateOf("")

    private var firstNameError by mutableStateOf<String?>(null)
    private var lastNameError by mutableStateOf<String?>(null)
    private var emailError by mutableStateOf<String?>(null)
    private var passwordError by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AddDealerScreen(
                firstName = firstName,
                lastName = lastName,
                email = email,
                phone = phone,
                countryCode = countryCode,
                password = password,
                initialBalance = initialBalance,
                firstNameError = firstNameError,
                lastNameError = lastNameError,
                emailError = emailError,
                passwordError = passwordError,
                loading = loading,
                errorMessage = errorMessage,
                successMessage = successMessage,
                onFirstNameChange = {
                    firstName = it
                    firstNameError = null
                },
                onLastNameChange = {
                    lastName = it
                    lastNameError = null
                },
                onEmailChange = {
                    email = it
                    emailError = null
                },
                onPhoneChange = { phone = it },
                onCountryCodeChange = { countryCode = it },
                onPasswordChange = {
                    password = it
                    passwordError = null
                },
                onInitialBalanceChange = { initialBalance = it },
                onBack = { finish() },
                onSubmit = { createDealer() }
            )
        }
    }

    private fun createDealer() {
        clearMessages()

        val cleanFirstName = firstName.trim()
        val cleanLastName = lastName.trim()
        val cleanEmail = email.trim()
        val cleanPhone = phone.trim()
        val cleanCountryCode = countryCode.trim()
        val cleanPassword = password.trim()
        val cleanInitialBalance = initialBalance.trim().replace(",", ".")

        var valid = true

        if (cleanFirstName.isBlank()) {
            firstNameError = "Enter first name."
            valid = false
        }

        if (cleanLastName.isBlank()) {
            lastNameError = "Enter last name."
            valid = false
        }

        if (cleanEmail.isBlank() || !Patterns.EMAIL_ADDRESS.matcher(cleanEmail).matches()) {
            emailError = "Enter a valid email."
            valid = false
        }

        if (cleanPassword.isBlank()) {
            passwordError = "Enter a temporary password."
            valid = false
        }

        if (!valid) return

        lifecycleScope.launch {
            loading = true
            errorMessage = null
            successMessage = null

            val session = activeSessionOrReturnToLogin()
            if (session == null) {
                loading = false
                return@launch
            }

            val result = runCatching {
                authApi.createDealer(
                    session = session,
                    firstName = cleanFirstName,
                    lastName = cleanLastName,
                    email = cleanEmail,
                    phoneNumber = cleanPhone,
                    countryCode = cleanCountryCode,
                    password = cleanPassword,
                    initialBalance = cleanInitialBalance
                )
            }

            loading = false

            result
                .onSuccess { dealer ->
                    successMessage = "Dealer created successfully."
                    val id = dealer.id
                    if (!id.isNullOrBlank()) {
                        startActivity(
                            Intent(this@AddDealerActivity, DealerDetailActivity::class.java)
                                .putExtra(DealerDetailActivity.EXTRA_DEALER_ID, id)
                                .putExtra(DealerDetailActivity.EXTRA_DEALER_NAME, dealer.name)
                        )
                    }
                    finish()
                }
                .onFailure {
                    errorMessage = it.message ?: "Could not create dealer."
                }
        }
    }

    private fun clearMessages() {
        firstNameError = null
        lastNameError = null
        emailError = null
        passwordError = null
        errorMessage = null
        successMessage = null
    }

    private suspend fun activeSessionOrReturnToLogin(): AuthSession? {
        val savedSession = withContext(Dispatchers.IO) { tokenStore.getSession() } ?: return redirectToLogin()

        if (!JwtUtils.isExpired(savedSession.accessToken)) return savedSession

        val refreshed = runCatching { authApi.refresh(savedSession) }.getOrNull() ?: return redirectToLogin()

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
}

@Composable
private fun AddDealerScreen(
    firstName: String,
    lastName: String,
    email: String,
    phone: String,
    countryCode: String,
    password: String,
    initialBalance: String,
    firstNameError: String?,
    lastNameError: String?,
    emailError: String?,
    passwordError: String?,
    loading: Boolean,
    errorMessage: String?,
    successMessage: String?,
    onFirstNameChange: (String) -> Unit,
    onLastNameChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    onCountryCodeChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onInitialBalanceChange: (String) -> Unit,
    onBack: () -> Unit,
    onSubmit: () -> Unit
) {
    val bg = Color(0xFFF6F7FB)

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = bg) {
            Column(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    AddDealerHero(
                        loading = loading,
                        onBack = onBack
                    )

                    errorMessage?.let {
                        InfoCard(title = "Dealer could not be created") {
                            Text(it, color = Color(0xFFDC2626))
                        }
                    }

                    successMessage?.let {
                        InfoCard(title = "Success") {
                            Text(it, color = Color(0xFF168653), fontWeight = FontWeight.Bold)
                        }
                    }

                    InfoCard(title = "Dealer account") {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = firstName,
                                onValueChange = onFirstNameChange,
                                modifier = Modifier.weight(1f),
                                label = { Text("First name") },
                                singleLine = true,
                                isError = firstNameError != null,
                                supportingText = { firstNameError?.let { Text(it) } },
                                shape = RoundedCornerShape(18.dp)
                            )

                            OutlinedTextField(
                                value = lastName,
                                onValueChange = onLastNameChange,
                                modifier = Modifier.weight(1f),
                                label = { Text("Last name") },
                                singleLine = true,
                                isError = lastNameError != null,
                                supportingText = { lastNameError?.let { Text(it) } },
                                shape = RoundedCornerShape(18.dp)
                            )
                        }

                        OutlinedTextField(
                            value = email,
                            onValueChange = onEmailChange,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Email") },
                            singleLine = true,
                            isError = emailError != null,
                            supportingText = { emailError?.let { Text(it) } },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            shape = RoundedCornerShape(18.dp)
                        )

                        OutlinedTextField(
                            value = password,
                            onValueChange = onPasswordChange,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Temporary password") },
                            singleLine = true,
                            isError = passwordError != null,
                            supportingText = { passwordError?.let { Text(it) } },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            shape = RoundedCornerShape(18.dp)
                        )
                    }

                    InfoCard(title = "Optional profile details") {
                        OutlinedTextField(
                            value = phone,
                            onValueChange = onPhoneChange,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Phone") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            shape = RoundedCornerShape(18.dp)
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = countryCode,
                                onValueChange = { onCountryCodeChange(it.uppercase(Locale.ROOT).take(3)) },
                                modifier = Modifier.weight(1f),
                                label = { Text("Country code") },
                                singleLine = true,
                                shape = RoundedCornerShape(18.dp)
                            )

                            OutlinedTextField(
                                value = initialBalance,
                                onValueChange = onInitialBalanceChange,
                                modifier = Modifier.weight(1f),
                                label = { Text("Initial balance") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                shape = RoundedCornerShape(18.dp)
                            )
                        }
                    }

                    InfoCard(title = "Before creating") {
                        Text(
                            "This will create a new dealer account under your reseller workspace. Use a temporary password and share it securely.",
                            color = Color(0xFF6B7280)
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))
                }

                Surface(shadowElevation = 8.dp, color = Color.White) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = onBack,
                            enabled = !loading,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Text("Back")
                        }

                        Button(
                            onClick = onSubmit,
                            enabled = !loading,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF7900)),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            if (loading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White
                                )
                            } else {
                                Text("Create dealer", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddDealerHero(
    loading: Boolean,
    onBack: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF17181C))
    ) {
        Column(modifier = Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Add dealer",
                        color = Color.White,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        "Create a new dealer account under your reseller workspace.",
                        color = Color.White.copy(alpha = 0.72f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Text(
                    text = "Back",
                    color = Color(0xFFFF7900),
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.clickable(enabled = !loading, onClick = onBack)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .background(Color(0xFFFFEFE2), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("+", color = Color(0xFFFF7900), fontWeight = FontWeight.Black)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Dealer profile", color = Color.White, fontWeight = FontWeight.Black)
                    Text("Identity, login and initial wallet balance", color = Color.White.copy(alpha = 0.72f))
                }
            }
        }
    }
}

@Composable
private fun InfoCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                title,
                color = Color(0xFF17181C),
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            HorizontalDivider()
            content()
        }
    }
}
