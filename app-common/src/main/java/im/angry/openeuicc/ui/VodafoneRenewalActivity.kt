package im.angry.openeuicc.ui

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import im.angry.openeuicc.auth.MobileEsim
import im.angry.openeuicc.auth.Roam2WorldAuthApi
import im.angry.openeuicc.common.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.OffsetDateTime
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment

class VodafoneRenewalActivity : ComponentActivity() {
    private val tokenStore by lazy { AuthTokenStore(this) }
    private val authApi by lazy { Roam2WorldAuthApi(BuildConfig.ROAM2WORLD_API_BASE_URL) }

    private var selectedDataGb by mutableStateOf("200")
    private var iccid by mutableStateOf("")
    private var customerName by mutableStateOf("")
    private var customerPhone by mutableStateOf("")
    private var customerEmail by mutableStateOf("")
    private var resultMessage by mutableStateOf<String?>(null)
    private var submitting by mutableStateOf(false)

    private var iccidError by mutableStateOf<String?>(null)
    private var nameError by mutableStateOf<String?>(null)
    private var phoneError by mutableStateOf<String?>(null)
    private var emailError by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        applyPrefilledIccid()

        setContent {
            VodafoneRenewalScreen(
                selectedDataGb = selectedDataGb,
                iccid = iccid,
                customerName = customerName,
                customerPhone = customerPhone,
                customerEmail = customerEmail,
                resultMessage = resultMessage,
                submitting = submitting,
                iccidError = iccidError,
                nameError = nameError,
                phoneError = phoneError,
                emailError = emailError,
                onBack = { finish() },
                onSelectData = { selectedDataGb = it },
                onIccidChange = { iccid = it },
                onNameChange = { customerName = it },
                onPhoneChange = { customerPhone = it },
                onEmailChange = { customerEmail = it },
                onSubmit = {
                    if (!validateForm()) return@VodafoneRenewalScreen
                    submitRenewal()
                }
            )
        }
    }

    private fun applyPrefilledIccid() {
        val prefilled = intent.getStringExtra(EXTRA_RENEW_ICCID)
            ?: intent.getStringExtra(EXTRA_ICCID)
            ?: return
        if (prefilled.isNotBlank()) iccid = prefilled
    }

    private fun submitRenewal() {
        val query = iccid.trim()

        lifecycleScope.launch {
            val session = activeSessionOrReturnToLogin() ?: return@launch
            submitting = true
            resultMessage = null

            val result = runCatching {
                val esim = withContext(Dispatchers.IO) {
                    authApi.esims(session).esims.firstOrNull { esim ->
                        isVodafoneEsim(esim) &&
                            (
                                esim.iccid?.equals(query, ignoreCase = true) == true ||
                                    esim.iccid?.contains(query, ignoreCase = true) == true
                            )
                    }
                } ?: throw IllegalStateException("No Vodafone eSIM found for ICCID: $query")

                if (isExpired(esim)) {
                    throw IllegalStateException("Expired Vodafone eSIMs cannot be renewed")
                }

                withContext(Dispatchers.IO) { postVodafoneRenewal(session, esim) }
            }

            submitting = false

            result
                .onSuccess { message ->
                    resultMessage = message
                    Toast.makeText(this@VodafoneRenewalActivity, message, Toast.LENGTH_LONG).show()
                }
                .onFailure { error ->
                    val message = error.message ?: "Vodafone renewal request failed"
                    resultMessage = message
                    Toast.makeText(this@VodafoneRenewalActivity, message, Toast.LENGTH_LONG).show()
                }
        }
    }

    private suspend fun activeSessionOrReturnToLogin(): AuthSession? {
        val savedSession = withContext(Dispatchers.IO) {
            tokenStore.getSession()
        } ?: return redirectToLogin()

        if (!JwtUtils.isExpired(savedSession.accessToken)) return savedSession

        val refreshed = runCatching {
            authApi.refresh(savedSession)
        }.getOrNull() ?: return redirectToLogin()

        withContext(Dispatchers.IO) {
            tokenStore.save(refreshed)
        }
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

    private fun postVodafoneRenewal(session: AuthSession, esim: MobileEsim): String {
        val body = JSONObject()
            .put("iccid", esim.iccid.orEmpty())
            .put("renewal_data_gb", selectedDataGb)
            .put("customer_name", customerName.trim())
            .put("customer_phone", customerPhone.trim())
            .put("source", "android")

        customerEmail.trim().takeIf { it.isNotBlank() }?.let {
            body.put("email", it)
        }

        esim.id?.takeIf { it.isNotBlank() }?.let {
            body.put("esim_id", it)
        }

        return postJsonRequest(
            requestUrl = vodafoneRenewalUrl(),
            authorizationHeader = session.authorizationHeader,
            body = body,
            fallbackMessage = "Vodafone eSIM renewal submitted",
            fallbackError = "Vodafone renewal request failed"
        )
    }

    private fun postJsonRequest(
        requestUrl: String,
        authorizationHeader: String,
        body: JSONObject,
        fallbackMessage: String,
        fallbackError: String
    ): String {
        val connection = (URL(requestUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 30_000
            readTimeout = 30_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", authorizationHeader)
            doOutput = true
        }

        return try {
            connection.outputStream.use { stream ->
                stream.write(body.toString().toByteArray(Charsets.UTF_8))
            }

            val status = connection.responseCode
            val responseText = ((if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()
                ?.use { it.readText() })
                .orEmpty()

            val response = responseText.takeIf { it.isNotBlank() }?.let { JSONObject(it) }

            if (status !in 200..299 || response?.optBoolean("success", true) == false) {
                val message = response?.optString("message")
                    ?.takeIf { it.isNotBlank() }
                    ?: response?.optString("error")?.takeIf { it.isNotBlank() }
                    ?: response?.optString("detail")?.takeIf { it.isNotBlank() }
                    ?: "$fallbackError with HTTP $status"
                throw IllegalStateException(message)
            }

            response?.optString("message")?.takeIf { it.isNotBlank() } ?: fallbackMessage
        } finally {
            connection.disconnect()
        }
    }

    private fun validateForm(): Boolean {
        iccidError = null
        nameError = null
        phoneError = null
        emailError = null

        var valid = true

        if (iccid.trim().length < 6) {
            iccidError = "Enter Vodafone eSIM ICCID"
            valid = false
        }

        if (customerName.trim().isBlank()) {
            nameError = "Customer name is required"
            valid = false
        }

        if (customerPhone.trim().length < 6) {
            phoneError = "Enter a valid phone number"
            valid = false
        }

        val email = customerEmail.trim()
        if (email.isNotBlank() && !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailError = "Enter a valid email"
            valid = false
        }

        return valid
    }

    private fun isVodafoneEsim(esim: MobileEsim): Boolean {
        val provider = esim.provider.orEmpty().lowercase()
        val title = esim.packageName.orEmpty().lowercase()
        return provider.contains("airhub") || provider.contains("vodafone") || title.contains("vodafone")
    }

    private fun isExpired(esim: MobileEsim): Boolean {
        if (esim.status?.equals("expired", ignoreCase = true) == true) return true
        val expiresAt = esim.expiresAt ?: return false
        return runCatching {
            OffsetDateTime.parse(expiresAt).isBefore(OffsetDateTime.now())
        }.getOrDefault(false)
    }

    private fun vodafoneRenewalUrl(): String =
        "${BuildConfig.ROAM2WORLD_API_BASE_URL.trimEnd('/')}/api/v1/mobile/airhub/vodafone/renew/"

    private companion object {
        const val EXTRA_RENEW_ICCID = "renew.iccid"
        const val EXTRA_ICCID = "iccid"
    }
}

@Composable
private fun VodafoneRenewalScreen(
    selectedDataGb: String,
    iccid: String,
    customerName: String,
    customerPhone: String,
    customerEmail: String,
    resultMessage: String?,
    submitting: Boolean,
    iccidError: String?,
    nameError: String?,
    phoneError: String?,
    emailError: String?,
    onBack: () -> Unit,
    onSelectData: (String) -> Unit,
    onIccidChange: (String) -> Unit,
    onNameChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onSubmit: () -> Unit
) {
    val red = Color(0xFFD71920)
    val bg = Color(0xFFF7F7FA)

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = bg) {
            Box(modifier = Modifier.fillMaxSize()) {
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

                VodafoneHeroCard(red = red)

                InfoCard(title = "Paket Seçimi") {
                    Text(
                        text = "Vodafone eSIM Renewal\n${selectedDataGb}GB",
                        color = Color(0xFF17181C),
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("200", "400", "500").forEach { data ->
                            PackageButton(
                                label = "${data}GB",
                                selected = selectedDataGb == data,
                                selectedColor = red,
                                onClick = { onSelectData(data) }
                            )
                        }
                    }
                }

                InfoCard(title = "Müşteri / ICCID") {
                    OutlinedTextField(
                        value = iccid,
                        onValueChange = onIccidChange,
                        label = { Text("Vodafone eSIM ICCID") },
                        isError = iccidError != null,
                        supportingText = { iccidError?.let { Text(it) } },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = customerName,
                        onValueChange = onNameChange,
                        label = { Text("Customer name") },
                        isError = nameError != null,
                        supportingText = { nameError?.let { Text(it) } },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = customerPhone,
                        onValueChange = onPhoneChange,
                        label = { Text("Customer phone") },
                        isError = phoneError != null,
                        supportingText = { phoneError?.let { Text(it) } },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = customerEmail,
                        onValueChange = onEmailChange,
                        label = { Text("Email optional") },
                        isError = emailError != null,
                        supportingText = { emailError?.let { Text(it) } },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (!resultMessage.isNullOrBlank()) {
                    ResultCard(message = resultMessage)
                }

                Button(
                    onClick = onSubmit,
                    enabled = !submitting,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = red),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    if (submitting) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CircularProgressIndicator(modifier = Modifier.height(18.dp), color = Color.White)
                            Text("Submitting...")
                        }
                    } else {
                        Text("Continue Renewal")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
            }
        
                R2wBottomNav(
                    selected = R2wBottomTab.Esims,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }
    }
}

@Composable
private fun VodafoneHeroCard(red: Color) {
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
                text = "Vodafone Recharge",
                color = red,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "eSIM renewal",
                color = Color.White,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "ICCID ile mevcut Vodafone/Airhub eSIM bulunur ve seçilen data paketiyle yenileme isteği gönderilir.",
                color = Color.White.copy(alpha = 0.74f),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun InfoCard(
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
private fun PackageButton(
    label: String,
    selected: Boolean,
    selectedColor: Color,
    onClick: () -> Unit
) {
    val bg = if (selected) selectedColor else Color.White
    val fg = if (selected) Color.White else Color(0xFF17181C)

    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = bg)
    ) {
        Text(label, color = fg, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun ResultCard(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF))
    ) {
        Text(
            text = message,
            color = Color(0xFF1D4ED8),
            modifier = Modifier.padding(18.dp),
            fontWeight = FontWeight.SemiBold
        )
    }
}
