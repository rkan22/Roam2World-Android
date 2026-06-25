package im.angry.openeuicc.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import im.angry.openeuicc.auth.AuthSession
import im.angry.openeuicc.auth.AuthTokenStore
import im.angry.openeuicc.auth.JwtUtils
import im.angry.openeuicc.auth.Roam2WorldAuthApi
import im.angry.openeuicc.common.BuildConfig
import im.angry.openeuicc.common.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import im.angry.openeuicc.ui.compose.theme.Primary
import im.angry.openeuicc.ui.compose.theme.R2WTheme

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
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        logLoginEndpoint()

        setContent {
            R2WTheme {
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
                openMainActivity(activeSession)
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
                .onSuccess { session -> openMainActivity(session) }
                .onFailure {
                    setBusy(false)
                    showStatus(it.message ?: getString(R.string.login_session_expired), isError = true)
                }
        }
    }

    private fun openMainActivity(session: AuthSession) {
        val target = targetActivityName(session)
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

    private fun targetActivityName(session: AuthSession): String? {
        val role = session.role.orEmpty().trim().lowercase()
        if (role in ADMIN_ROLES) {
            return MOBILE_ADMIN_ACTIVITY
        }

        val appInfo = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
        return appInfo.metaData?.getString(META_TARGET_ACTIVITY)
    }

    companion object {
        private const val TAG = "LoginActivity"
        private const val MOBILE_ADMIN_ACTIVITY = "im.angry.openeuicc.MobileAdminActivity"
        private val ADMIN_ROLES = setOf("admin", "super_admin", "superadmin")
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
    val bg = MaterialTheme.colorScheme.background

    Surface(modifier = Modifier.fillMaxSize(), color = bg) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Hero section with Logo
            Surface(
                modifier = Modifier.size(100.dp),
                shape = CircleShape,
                color = Primary.copy(alpha = 0.1f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("R2W", color = Primary, fontWeight = FontWeight.Bold, fontSize = 24.sp)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        "Sign in",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Use your Roam2World account to continue.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
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
                        shape = RoundedCornerShape(12.dp)
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
                        shape = RoundedCornerShape(12.dp)
                    )

                    if (busy) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = Primary
                        )
                    }

                    statusMessage?.let {
                        Text(
                            text = it,
                            color = if (statusIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Button(
                        onClick = onSubmit,
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Primary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (busy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                        } else {
                            Text(
                                "Login",
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                "Secure access to eSIM, wallet and telecom tools.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
