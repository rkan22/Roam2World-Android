package im.angry.openeuicc.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import im.angry.openeuicc.auth.MobileEsim
import im.angry.openeuicc.auth.Roam2WorldAuthApi
import im.angry.openeuicc.common.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment

class TgtSimRechargeActivity : ComponentActivity() {
    private val tokenStore by lazy { AuthTokenStore(this) }
    private val authApi by lazy { Roam2WorldAuthApi(BuildConfig.ROAM2WORLD_API_BASE_URL) }

    private var mode by mutableStateOf(TgtMode.ESIM_RENEWAL)

    private var selectedPackageName by mutableStateOf("10GB / 30 Days")
    private var simIccid by mutableStateOf("")
    private var simCustomerName by mutableStateOf("")
    private var simCustomerPhone by mutableStateOf("")
    private var simResultMessage by mutableStateOf<String?>(null)
    private var simSubmitting by mutableStateOf(false)

    private var selectedRenewalPackageName by mutableStateOf("10GB / 30 Days")
    private var selectedRenewalDataGb by mutableStateOf("10")
    private var esimIccid by mutableStateOf("")
    private var esimCustomerName by mutableStateOf("")
    private var esimCustomerPhone by mutableStateOf("")
    private var esimCustomerEmail by mutableStateOf("")
    private var esimResultMessage by mutableStateOf<String?>(null)
    private var esimSubmitting by mutableStateOf(false)

    private var simIccidError by mutableStateOf<String?>(null)
    private var simNameError by mutableStateOf<String?>(null)
    private var simPhoneError by mutableStateOf<String?>(null)

    private var esimIccidError by mutableStateOf<String?>(null)
    private var esimNameError by mutableStateOf<String?>(null)
    private var esimPhoneError by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyPrefilledIccid()

        setContent {
            TgtRechargeScreen(
                mode = mode,
                selectedPackageName = selectedPackageName,
                simIccid = simIccid,
                simCustomerName = simCustomerName,
                simCustomerPhone = simCustomerPhone,
                simResultMessage = simResultMessage,
                simSubmitting = simSubmitting,
                simIccidError = simIccidError,
                simNameError = simNameError,
                simPhoneError = simPhoneError,
                selectedRenewalPackageName = selectedRenewalPackageName,
                selectedRenewalDataGb = selectedRenewalDataGb,
                esimIccid = esimIccid,
                esimCustomerName = esimCustomerName,
                esimCustomerPhone = esimCustomerPhone,
                esimCustomerEmail = esimCustomerEmail,
                esimResultMessage = esimResultMessage,
                esimSubmitting = esimSubmitting,
                esimIccidError = esimIccidError,
                esimNameError = esimNameError,
                esimPhoneError = esimPhoneError,
                onBack = { finish() },
                onModeChange = { mode = it },
                onSelectSimPackage = { selectedPackageName = it },
                onSimIccidChange = { simIccid = it },
                onSimNameChange = { simCustomerName = it },
                onSimPhoneChange = { simCustomerPhone = it },
                onSelectRenewalPackage = { packageName, dataGb ->
                    selectedRenewalPackageName = packageName
                    selectedRenewalDataGb = dataGb
                },
                onEsimIccidChange = { esimIccid = it },
                onEsimNameChange = { esimCustomerName = it },
                onEsimPhoneChange = { esimCustomerPhone = it },
                onEsimEmailChange = { esimCustomerEmail = it },
                onSubmitSim = {
                    if (!validateSimForm()) return@TgtRechargeScreen
                    submitRechargeRequest()
                },
                onSubmitEsim = {
                    if (!validateEsimRenewalForm()) return@TgtRechargeScreen
                    submitEsimRenewalRequest()
                }
            )
        }
    }

    private fun applyPrefilledIccid() {
        val prefilled = intent.getStringExtra(EXTRA_RENEW_ICCID)
            ?: intent.getStringExtra(EXTRA_ICCID)
            ?: return
        if (prefilled.isBlank()) return
        mode = TgtMode.ESIM_RENEWAL
        esimIccid = prefilled
    }

    private fun submitRechargeRequest() {
        lifecycleScope.launch {
            val session = activeSessionOrReturnToLogin() ?: return@launch
            simSubmitting = true
            simResultMessage = null

            val result = runCatching {
                withContext(Dispatchers.IO) { postTgtRecharge(session) }
            }

            simSubmitting = false

            result
                .onSuccess { message ->
                    Toast.makeText(this@TgtSimRechargeActivity, message, Toast.LENGTH_LONG).show()
                    simResultMessage = message
                }
                .onFailure { error ->
                    val message = error.message ?: "Orange recharge request failed"
                    Toast.makeText(this@TgtSimRechargeActivity, message, Toast.LENGTH_LONG).show()
                    simResultMessage = message
                }
        }
    }

    private fun submitEsimRenewalRequest() {
        val query = esimIccid.trim()

        lifecycleScope.launch {
            val session = activeSessionOrReturnToLogin() ?: return@launch
            esimSubmitting = true
            esimResultMessage = null

            val result = runCatching {
                val esim = withContext(Dispatchers.IO) {
                    authApi.esims(session).esims.firstOrNull { esim ->
                        esim.iccid?.equals(query, ignoreCase = true) == true ||
                            esim.iccid?.contains(query, ignoreCase = true) == true
                    }
                } ?: throw IllegalStateException("No Orange eSim found for ICCID: $query")

                withContext(Dispatchers.IO) { postTgtEsimRenewal(session, esim) }
            }

            esimSubmitting = false

            result
                .onSuccess { message ->
                    Toast.makeText(this@TgtSimRechargeActivity, message, Toast.LENGTH_LONG).show()
                    esimResultMessage = message
                }
                .onFailure { error ->
                    val message = error.message ?: "Orange eSim renewal request failed"
                    Toast.makeText(this@TgtSimRechargeActivity, message, Toast.LENGTH_LONG).show()
                    esimResultMessage = message
                }
        }
    }

    private suspend fun activeSessionOrReturnToLogin(): AuthSession? {
        val session = withContext(Dispatchers.IO) { tokenStore.getSession() }
        if (session != null) return session

        startActivity(
            Intent(this, LoginActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
        )
        finish()
        return null
    }

    private fun postTgtRecharge(session: AuthSession): String {
        val body = JSONObject()
            .put("package_name", selectedPackageName)
            .put("iccid", simIccid.trim())
            .put("customer_name", simCustomerName.trim())
            .put("customer_phone", simCustomerPhone.trim())
            .put("provider", "Orange Balkans")
            .put("source", "android")

        return postJsonRequest(
            requestUrl = tgtRechargeUrl(),
            authorizationHeader = session.authorizationHeader,
            body = body,
            fallbackMessage = "Orange recharge request submitted",
            fallbackError = "Orange recharge request failed"
        )
    }

    private fun postTgtEsimRenewal(session: AuthSession, esim: MobileEsim): String {
        val body = JSONObject()
            .put("iccid", esim.iccid.orEmpty())
            .put("renewal_data_gb", selectedRenewalDataGb)
            .put("customer_name", esimCustomerName.trim())
            .put("customer_phone", esimCustomerPhone.trim())
            .put("source", "android")

        esimCustomerEmail.trim().takeIf { it.isNotBlank() }?.let {
            body.put("email", it)
        }

        esim.id?.takeIf { it.isNotBlank() }?.let {
            body.put("esim_id", it)
        }

        return postJsonRequest(
            requestUrl = tgtEsimRenewalUrl(),
            authorizationHeader = session.authorizationHeader,
            body = body,
            fallbackMessage = "Orange eSim renewal submitted",
            fallbackError = "Orange eSim renewal request failed"
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

    private fun validateSimForm(): Boolean {
        simIccidError = null
        simNameError = null
        simPhoneError = null

        var valid = true
        if (simIccid.trim().length < 10) {
            simIccidError = "Enter a valid ICCID"
            valid = false
        }
        if (simCustomerName.trim().isBlank()) {
            simNameError = "Customer name is required"
            valid = false
        }
        if (simCustomerPhone.trim().length < 6) {
            simPhoneError = "Enter a valid phone number"
            valid = false
        }
        return valid
    }

    private fun validateEsimRenewalForm(): Boolean {
        esimIccidError = null
        esimNameError = null
        esimPhoneError = null

        var valid = true
        if (esimIccid.trim().length < 6) {
            esimIccidError = "Enter Orange eSim ICCID"
            valid = false
        }
        if (esimCustomerName.trim().isBlank()) {
            esimNameError = "Customer name is required"
            valid = false
        }
        if (esimCustomerPhone.trim().length < 6) {
            esimPhoneError = "Phone number is required"
            valid = false
        }
        return valid
    }

    private fun tgtRechargeUrl(): String =
        "${BuildConfig.ROAM2WORLD_API_BASE_URL.trimEnd('/')}/api/v1/mobile/tgt/recharge/"

    private fun tgtEsimRenewalUrl(): String =
        "${BuildConfig.ROAM2WORLD_API_BASE_URL.trimEnd('/')}/api/v1/mobile/tgt/esim/renew/"

    private companion object {
        const val EXTRA_RENEW_ICCID = "renew.iccid"
        const val EXTRA_ICCID = "iccid"
    }
}

private enum class TgtMode {
    SIM_RECHARGE,
    ESIM_RENEWAL
}

@Composable
private fun TgtRechargeScreen(
    mode: TgtMode,
    selectedPackageName: String,
    simIccid: String,
    simCustomerName: String,
    simCustomerPhone: String,
    simResultMessage: String?,
    simSubmitting: Boolean,
    simIccidError: String?,
    simNameError: String?,
    simPhoneError: String?,
    selectedRenewalPackageName: String,
    selectedRenewalDataGb: String,
    esimIccid: String,
    esimCustomerName: String,
    esimCustomerPhone: String,
    esimCustomerEmail: String,
    esimResultMessage: String?,
    esimSubmitting: Boolean,
    esimIccidError: String?,
    esimNameError: String?,
    esimPhoneError: String?,
    onBack: () -> Unit,
    onModeChange: (TgtMode) -> Unit,
    onSelectSimPackage: (String) -> Unit,
    onSimIccidChange: (String) -> Unit,
    onSimNameChange: (String) -> Unit,
    onSimPhoneChange: (String) -> Unit,
    onSelectRenewalPackage: (String, String) -> Unit,
    onEsimIccidChange: (String) -> Unit,
    onEsimNameChange: (String) -> Unit,
    onEsimPhoneChange: (String) -> Unit,
    onEsimEmailChange: (String) -> Unit,
    onSubmitSim: () -> Unit,
    onSubmitEsim: () -> Unit
) {
    val orange = Color(0xFFFF7900)
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

                TgtHeroCard(orange = orange)

                TgtModeSelector(
                    mode = mode,
                    orange = orange,
                    onModeChange = onModeChange
                )

                if (mode == TgtMode.SIM_RECHARGE) {
                    TgtSimRechargeSection(
                        selectedPackageName = selectedPackageName,
                        iccid = simIccid,
                        customerName = simCustomerName,
                        customerPhone = simCustomerPhone,
                        resultMessage = simResultMessage,
                        submitting = simSubmitting,
                        iccidError = simIccidError,
                        nameError = simNameError,
                        phoneError = simPhoneError,
                        orange = orange,
                        onSelectPackage = onSelectSimPackage,
                        onIccidChange = onSimIccidChange,
                        onNameChange = onSimNameChange,
                        onPhoneChange = onSimPhoneChange,
                        onSubmit = onSubmitSim
                    )
                } else {
                    TgtEsimRenewalSection(
                        selectedRenewalPackageName = selectedRenewalPackageName,
                        selectedRenewalDataGb = selectedRenewalDataGb,
                        iccid = esimIccid,
                        customerName = esimCustomerName,
                        customerPhone = esimCustomerPhone,
                        customerEmail = esimCustomerEmail,
                        resultMessage = esimResultMessage,
                        submitting = esimSubmitting,
                        iccidError = esimIccidError,
                        nameError = esimNameError,
                        phoneError = esimPhoneError,
                        orange = orange,
                        onSelectPackage = onSelectRenewalPackage,
                        onIccidChange = onEsimIccidChange,
                        onNameChange = onEsimNameChange,
                        onPhoneChange = onEsimPhoneChange,
                        onEmailChange = onEsimEmailChange,
                        onSubmit = onSubmitEsim
                    )
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
private fun TgtHeroCard(orange: Color) {
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
                text = "Orange Recharge",
                color = orange,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "SIM recharge & eSIM renewal",
                color = Color.White,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Orange Balkans SIM recharge veya mevcut Orange eSIM için data yenileme isteği gönder.",
                color = Color.White.copy(alpha = 0.74f),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun TgtModeSelector(
    mode: TgtMode,
    orange: Color,
    onModeChange: (TgtMode) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TgtPackageButton(
            label = "SIM Recharge",
            selected = mode == TgtMode.SIM_RECHARGE,
            selectedColor = orange,
            onClick = { onModeChange(TgtMode.SIM_RECHARGE) }
        )
        TgtPackageButton(
            label = "eSIM Renewal",
            selected = mode == TgtMode.ESIM_RENEWAL,
            selectedColor = orange,
            onClick = { onModeChange(TgtMode.ESIM_RENEWAL) }
        )
    }
}

@Composable
private fun TgtSimRechargeSection(
    selectedPackageName: String,
    iccid: String,
    customerName: String,
    customerPhone: String,
    resultMessage: String?,
    submitting: Boolean,
    iccidError: String?,
    nameError: String?,
    phoneError: String?,
    orange: Color,
    onSelectPackage: (String) -> Unit,
    onIccidChange: (String) -> Unit,
    onNameChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    onSubmit: () -> Unit
) {
    TgtInfoCard(title = "Orange Balkans SIM") {
        Text(
            text = selectedPackageName,
            color = Color(0xFF17181C),
            fontWeight = FontWeight.Bold
        )
        TgtPackagePicker(
            packages = simPackages(),
            selected = selectedPackageName,
            orange = orange,
            onSelect = onSelectPackage
        )
    }

    TgtInfoCard(title = "SIM Bilgileri") {
        OutlinedTextField(
            value = iccid,
            onValueChange = onIccidChange,
            label = { Text("ICCID") },
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
    }

    resultMessage?.takeIf { it.isNotBlank() }?.let {
        TgtResultCard(it)
    }

    TgtSubmitButton(
        text = "Activate",
        submitting = submitting,
        orange = orange,
        onClick = onSubmit
    )
}

@Composable
private fun TgtEsimRenewalSection(
    selectedRenewalPackageName: String,
    selectedRenewalDataGb: String,
    iccid: String,
    customerName: String,
    customerPhone: String,
    customerEmail: String,
    resultMessage: String?,
    submitting: Boolean,
    iccidError: String?,
    nameError: String?,
    phoneError: String?,
    orange: Color,
    onSelectPackage: (String, String) -> Unit,
    onIccidChange: (String) -> Unit,
    onNameChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onSubmit: () -> Unit
) {
    TgtInfoCard(title = "Orange eSIM Renewal") {
        Text(
            text = "$selectedRenewalPackageName · ${selectedRenewalDataGb}GB",
            color = Color(0xFF17181C),
            fontWeight = FontWeight.Bold
        )

        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            renewalPackages().forEach { option ->
                TgtPackageButton(
                    label = option.packageName,
                    selected = selectedRenewalPackageName == option.packageName,
                    selectedColor = orange,
                    onClick = { onSelectPackage(option.packageName, option.dataGb) }
                )
            }
        }
    }

    TgtInfoCard(title = "eSIM Bilgileri") {
        OutlinedTextField(
            value = iccid,
            onValueChange = onIccidChange,
            label = { Text("Orange eSIM ICCID") },
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
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
    }

    resultMessage?.takeIf { it.isNotBlank() }?.let {
        TgtResultCard(it)
    }

    TgtSubmitButton(
        text = "Continue Renewal",
        submitting = submitting,
        orange = orange,
        onClick = onSubmit
    )
}

@Composable
private fun TgtInfoCard(
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
private fun TgtPackagePicker(
    packages: List<String>,
    selected: String,
    orange: Color,
    onSelect: (String) -> Unit
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        packages.forEach { packageName ->
            TgtPackageButton(
                label = packageName,
                selected = selected == packageName,
                selectedColor = orange,
                onClick = { onSelect(packageName) }
            )
        }
    }
}

@Composable
private fun TgtPackageButton(
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
        Text(
            text = label,
            color = fg,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun TgtSubmitButton(
    text: String,
    submitting: Boolean,
    orange: Color,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = !submitting,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(containerColor = orange),
        shape = RoundedCornerShape(20.dp)
    ) {
        if (submitting) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(
                    modifier = Modifier.height(18.dp),
                    color = Color.White
                )
                Text("Submitting...")
            }
        } else {
            Text(text)
        }
    }
}

@Composable
private fun TgtResultCard(message: String) {
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

private data class TgtRenewalOption(
    val packageName: String,
    val dataGb: String
)

private fun simPackages(): List<String> =
    listOf(
        "10GB / 30 Days",
        "20GB / 30 Days",
        "30GB / 30 Days",
        "50GB / 30 Days",
        "20GB / 60 Days",
        "60GB / 60 Days"
    )

private fun renewalPackages(): List<TgtRenewalOption> =
    listOf(
        TgtRenewalOption("10GB / 30 Days", "10"),
        TgtRenewalOption("20GB / 30 Days", "20"),
        TgtRenewalOption("30GB / 30 Days", "30"),
        TgtRenewalOption("50GB / 30 Days", "50"),
        TgtRenewalOption("20GB / 60 Days", "20"),
        TgtRenewalOption("60GB / 60 Days", "60")
    )
