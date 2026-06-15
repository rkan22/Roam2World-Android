package im.angry.openeuicc.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import im.angry.openeuicc.auth.AuthTokenStore
import im.angry.openeuicc.auth.JwtUtils
import im.angry.openeuicc.auth.Roam2WorldAuthApi
import im.angry.openeuicc.common.BuildConfig
import im.angry.openeuicc.common.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

class LoginActivity : ComponentActivity() {
    private val tokenStore by lazy { AuthTokenStore(this) }
    private val authApi by lazy { Roam2WorldAuthApi(BuildConfig.ROAM2WORLD_API_BASE_URL) }

    private var email by mutableStateOf("")
    private var password by mutableStateOf("")
    private var emailError by mutableStateOf<String?>(null)
    private var passwordError by mutableStateOf<String?>(null)
    private var busy by mutableStateOf(false)
    private var statusMessage by mutableStateOf<String?>(null)
    private var statusIsError by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        logLoginEndpoint()

        setContent {
            LoginScreen(
                email = email,
                password = password,
                emailError = emailError,
                passwordError = passwordError,
                busy = busy,
                statusMessage = statusMessage,
                statusIsError = statusIsError,
                onEmailChange = {
                    email = it
                    emailError = null
                    statusMessage = null
                },
                onPasswordChange = {
                    password = it
                    passwordError = null
                    statusMessage = null
                },
                onSubmit = { submitLogin() }
            )
        }

        restoreSession()
    }

    private fun logLoginEndpoint() {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Login endpoint: ${authApi.loginEndpointUrl}")
        }
    }

    private fun restoreSession() {
        lifecycleScope.launch {
            val savedSession = withContext(Dispatchers.IO) {
                tokenStore.getSession()
            } ?: return@launch

            setBusy(true, getString(R.string.login_checking_session))
            val activeSession = if (!JwtUtils.isExpired(savedSession.accessToken)) {
                savedSession
            } else {
                runCatching {
                    authApi.refresh(savedSession).also { refreshed ->
                        withContext(Dispatchers.IO) {
                            tokenStore.save(refreshed)
                        }
                    }
                }.getOrNull()
            }

            if (activeSession != null) {
                openMainActivity()
            } else {
                withContext(Dispatchers.IO) {
                    tokenStore.clear()
                }
                setBusy(false)
                showStatus(getString(R.string.login_session_expired), isError = true)
            }
        }
    }

    private fun submitLogin() {
        val cleanEmail = email.trim()
        val cleanPassword = password

        emailError = null
        passwordError = null
        statusMessage = null

        var valid = true
        if (cleanEmail.isBlank()) {
            emailError = getString(R.string.login_email_required)
            valid = false
        }
        if (cleanPassword.isBlank()) {
            passwordError = getString(R.string.login_password_required)
            valid = false
        }
        if (!valid) return

        lifecycleScope.launch {
            setBusy(true, getString(R.string.login_signing_in))
            val result = runCatching {
                authApi.login(cleanEmail, cleanPassword).also { session ->
                    withContext(Dispatchers.IO) {
                        tokenStore.save(session)
                    }
                }
            }

            result
                .onSuccess { openMainActivity() }
                .onFailure {
                    setBusy(false)
                    showStatus(it.message ?: getString(R.string.login_session_expired), isError = true)
                }
        }
    }

    private fun openMainActivity() {
        val target = targetActivityName()
        if (target.isNullOrBlank()) {
            setBusy(false)
            showStatus(getString(R.string.login_missing_target), isError = true)
            return
        }

        startActivity(
            Intent().setClassName(this, target).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
        )
        finish()
    }

    private fun setBusy(value: Boolean, message: String? = null) {
        busy = value
        if (message == null) {
            statusMessage = null
        } else {
            showStatus(message, isError = false)
        }
    }

    private fun showStatus(message: String, isError: Boolean) {
        statusMessage = message
        statusIsError = isError
    }

    private fun targetActivityName(): String? {
        val appInfo = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
        return appInfo.metaData?.getString(META_TARGET_ACTIVITY)
    }

    companion object {
        private const val TAG = "LoginActivity"
        const val META_TARGET_ACTIVITY = "im.angry.openeuicc.LOGIN_TARGET_ACTIVITY"
    }
}

@Composable
private fun LoginScreen(
    email: String,
    password: String,
    emailError: String?,
    passwordError: String?,
    busy: Boolean,
    statusMessage: String?,
    statusIsError: Boolean,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit
) {
    val orange = Color(0xFFFF7900)
    val bg = Color(0xFFF6F7FB)

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = bg) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(22.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                LoginHero(orange = orange)

                Spacer(modifier = Modifier.height(18.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(32.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(22.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            "Sign in",
                            color = Color(0xFF17181C),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            "Use your Roam2World account to continue.",
                            color = Color(0xFF6B7280)
                        )

                        OutlinedTextField(
                            value = email,
                            onValueChange = onEmailChange,
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Email") },
                            singleLine = true,
                            isError = emailError != null,
                            supportingText = { emailError?.let { Text(it) } },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Email,
                                imeAction = ImeAction.Next
                            ),
                            shape = RoundedCornerShape(18.dp)
                        )

                        OutlinedTextField(
                            value = password,
                            onValueChange = onPasswordChange,
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Password") },
                            singleLine = true,
                            isError = passwordError != null,
                            supportingText = { passwordError?.let { Text(it) } },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(onDone = { onSubmit() }),
                            shape = RoundedCornerShape(18.dp)
                        )

                        if (busy) {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth(),
                                color = orange,
                                trackColor = Color(0xFFFFE2C4)
                            )
                        }

                        statusMessage?.let {
                            Text(
                                text = it,
                                color = if (statusIsError) Color(0xFFDC2626) else Color(0xFF6B7280),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Button(
                            onClick = onSubmit,
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = orange),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            if (busy) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = Color.White
                                    )
                                    Text("Please wait", fontWeight = FontWeight.Bold)
                                }
                            } else {
                                Text(
                                    "Login",
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.padding(vertical = 6.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    "Secure access to eSIM, wallet, packages and customer tools.",
                    color = Color(0xFF6B7280),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun LoginHero(orange: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            modifier = Modifier
                .size(78.dp)
                .background(orange, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "R2W",
                color = Color.White,
                fontWeight = FontWeight.Black,
                style = MaterialTheme.typography.titleLarge
            )
        }

        Text(
            "Roam2World",
            color = Color(0xFF17181C),
            fontWeight = FontWeight.Black,
            style = MaterialTheme.typography.headlineMedium
        )

        Text(
            "Mobile eSIM Partner Portal",
            color = Color(0xFF6B7280),
            fontWeight = FontWeight.SemiBold
        )
    }
}
