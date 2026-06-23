package im.angry.openeuicc.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.BarcodeFormat
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import android.graphics.Color as AndroidColor
import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import im.angry.openeuicc.auth.MobileEsim
import im.angry.openeuicc.common.R
import im.angry.openeuicc.ui.wizard.DownloadWizardActivity
import im.angry.openeuicc.util.LPAString
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch

class MobileEsimInstallActivity : BaseEuiccAccessActivity() {
    private var installCode: String = ""
    private var lpaPayload by mutableStateOf("")
    private var esimPackageName by mutableStateOf("")
    private var iccid by mutableStateOf("")
    private var provider by mutableStateOf("")
    private var customerName by mutableStateOf("")


    private var loading by mutableStateOf(false)
    private var errorMessage by mutableStateOf<String?>(null)
    private var retryVisible by mutableStateOf(false)
    private var startEnabled by mutableStateOf(false)

    private var compatibilityStatus by mutableStateOf("")
    private var deviceStatus by mutableStateOf("")
    private var downloadStatus by mutableStateOf("")
    private var smdpText by mutableStateOf("")
    private var matchingIdText by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        installCode = intent.getStringExtra(EXTRA_INSTALL_CODE).orEmpty()
        esimPackageName = intent.getStringExtra(EXTRA_PACKAGE_NAME).orEmpty().ifBlank { "Roam2World eSIM" }
        iccid = intent.getStringExtra(EXTRA_ICCID).orEmpty()
        provider = intent.getStringExtra(EXTRA_PROVIDER).orEmpty().ifBlank { "Roam2World" }
        customerName = intent.getStringExtra(EXTRA_CUSTOMER_NAME).orEmpty().ifBlank { "Customer" }
        setInitialState()

        setContent {
            MobileEsimInstallScreenV2(
                packageName = esimPackageName,
                iccid = iccid,
                provider = provider,
                customerName = customerName,
                lpaPayload = lpaPayload,
                loading = loading,
                error = errorMessage,
                retryVisible = retryVisible,
                startEnabled = startEnabled,
                compatibilityStatus = compatibilityStatus,
                deviceStatus = deviceStatus,
                downloadStatus = downloadStatus,
                smdpText = smdpText,
                matchingIdText = matchingIdText,
                onBack = { finish() },
                onRetry = { runPreflight() },
                onStart = { launchDownloadWizard() }
            )
        }
    }

    override fun onInit() {
        runPreflight()
    }

    private fun setInitialState() {
        loading = false
        errorMessage = null
        retryVisible = false
        startEnabled = false
        compatibilityStatus = getString(R.string.mobile_esim_install_step_waiting)
        deviceStatus = getString(R.string.mobile_esim_install_step_waiting)
        downloadStatus = getString(R.string.mobile_esim_install_step_waiting)
        smdpText = ""
        matchingIdText = ""
    }

    private fun runPreflight() {
        lifecycleScope.launch {
            setInitialState()
            loading = true
            compatibilityStatus = getString(R.string.mobile_esim_install_step_running)

            val parsed = runCatching {
                val payload = if (installCode.startsWith("LPA:", ignoreCase = true)) {
                    installCode
                } else {
                    "LPA:$installCode"
                }
                LPAString.parse(payload).also { lpaPayload = payload }
            }.getOrElse {
                showFailure(getString(R.string.mobile_esim_install_invalid_code))
                return@launch
            }

            compatibilityStatus = getString(R.string.mobile_esim_install_step_passed)
            smdpText = getString(R.string.mobile_esim_smdp_format, parsed.address)
            matchingIdText = getString(
                R.string.mobile_esim_matching_id_format,
                parsed.matchingId ?: getString(R.string.mobile_esim_value_unavailable)
            )

            deviceStatus = getString(R.string.mobile_esim_install_step_running)

            val openEuiccPorts = runCatching {
                euiccChannelManager.flowAllOpenEuiccPorts().toList()
            }.getOrElse {
                showFailure(getString(R.string.mobile_esim_install_device_check_failed))
                return@launch
            }

            val platformHasEuicc = packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY_EUICC)
            if (openEuiccPorts.isEmpty() && !platformHasEuicc) {
                showFailure(getString(R.string.mobile_esim_install_device_unsupported))
                return@launch
            }

            deviceStatus = getString(R.string.mobile_esim_install_step_passed)
            downloadStatus = getString(R.string.mobile_esim_install_ready)
            loading = false
            startEnabled = true
        }
    }

    private fun showFailure(message: String) {
        loading = false
        errorMessage = message
        retryVisible = true
        startEnabled = false
        downloadStatus = getString(R.string.mobile_esim_install_step_blocked)
    }

    private fun launchDownloadWizard() {
        if (lpaPayload.isBlank()) return
        downloadStatus = getString(R.string.mobile_esim_install_launching)
        startActivity(
            DownloadWizardActivity.newIntent(this).apply {
                action = Intent.ACTION_VIEW
                data = lpaPayload.toUri()
            }
        )
        finish()
    }

    companion object {
        private const val EXTRA_INSTALL_CODE = "mobile_esim_install.install_code"
        private const val EXTRA_PACKAGE_NAME = "mobile_esim_install.package_name"
        private const val EXTRA_ICCID = "mobile_esim_install.iccid"
        private const val EXTRA_PROVIDER = "mobile_esim_install.provider"
        private const val EXTRA_CUSTOMER_NAME = "mobile_esim_install.customer_name"

        fun createIntent(context: Context, esim: MobileEsim): Intent =
            Intent(context, MobileEsimInstallActivity::class.java).apply {
                putExtra(EXTRA_INSTALL_CODE, esim.installCode())
                putExtra(EXTRA_PACKAGE_NAME, esim.packageName)
                putExtra(EXTRA_ICCID, esim.iccid)
                putExtra(EXTRA_PROVIDER, esim.provider)
                putExtra(EXTRA_CUSTOMER_NAME, esim.customerName())
            }
    }
}


@Composable
private fun MobileEsimInstallScreenV2(
    packageName: String,
    iccid: String,
    provider: String,
    customerName: String,
    lpaPayload: String,
    loading: Boolean,
    error: String?,
    retryVisible: Boolean,
    startEnabled: Boolean,
    compatibilityStatus: String,
    deviceStatus: String,
    downloadStatus: String,
    smdpText: String,
    matchingIdText: String,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onStart: () -> Unit
) {
    val blue = Color(0xFF175CFF)
    val bg = Color(0xFFF7F8FC)
    val text = Color(0xFF071330)
    val muted = Color(0xFF667085)

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = bg) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(onClick = onBack, shape = RoundedCornerShape(14.dp)) {
                        Text("Back")
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "Install eSIM",
                        color = text,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF3F7FF)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(packageName, color = text, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineSmall)
                        InstallInfoLine("Customer", customerName.ifBlank { "—" })
                        InstallInfoLine("ICCID", iccid.ifBlank { "—" })
                        InstallInfoLine("Provider", provider.ifBlank { "—" })
                    }
                }

                Text(
                    text = "Installation method",
                    color = text,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0xFFD8E2F3), RoundedCornerShape(18.dp)),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    InstallTab("QR Code Install", true, Modifier.weight(1f))
                    InstallTab("Activation Code Install", false, Modifier.weight(1f))
                    InstallTab("Manual Install", false, Modifier.weight(1f))
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        val bitmap = remember(lpaPayload) {
                            lpaPayload.takeIf { it.isNotBlank() }?.let { createInstallQrBitmap(it, 720) }
                        }

                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "eSIM QR Code",
                                modifier = Modifier.size(300.dp)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(260.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("QR will appear after preflight", color = muted, textAlign = TextAlign.Center)
                            }
                        }

                        Button(
                            onClick = onStart,
                            enabled = startEnabled,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = blue),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Text("Install QR with OpenEUICC", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (error.isNullOrBlank()) Color(0xFFEFFAF1) else Color(0xFFFFF3F3)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = when {
                                loading -> "Checking"
                                !error.isNullOrBlank() -> "Needs attention"
                                startEnabled -> "Ready"
                                else -> "Waiting"
                            },
                            color = if (error.isNullOrBlank()) Color(0xFF179441) else Color(0xFFD92D20),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = error
                                ?: "Activation code, device eSIM support and OpenEUICC wizard are ready.",
                            color = muted
                        )
                        if (retryVisible) {
                            OutlinedButton(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
                                Text("Retry")
                            }
                        }
                    }
                }

                InfoCard(title = "Preflight Details") {
                    StepRow("Activation code", compatibilityStatus)
                    StepRow("Device eSIM support", deviceStatus)
                    StepRow("Download wizard", downloadStatus)
                    if (smdpText.isNotBlank()) DetailRow("SMDP", smdpText)
                    if (matchingIdText.isNotBlank()) DetailRow("Matching ID", matchingIdText)
                }
            }
        }
    }
}

@Composable
private fun InstallInfoLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(label + ":", color = Color(0xFF667085), modifier = Modifier.weight(0.35f))
        Text(value, color = Color(0xFF071330), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(0.65f))
    }
}

@Composable
private fun InstallTab(label: String, selected: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(if (selected) Color(0xFFEAF1FF) else Color.Transparent)
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) Color(0xFF175CFF) else Color(0xFF3A4252),
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            textAlign = TextAlign.Center
        )
    }
}

private fun createInstallQrBitmap(content: String, size: Int): Bitmap? =
    runCatching {
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size)
        Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).apply {
            for (x in 0 until size) {
                for (y in 0 until size) {
                    setPixel(x, y, if (matrix[x, y]) AndroidColor.BLACK else AndroidColor.WHITE)
                }
            }
        }
    }.getOrNull()

@Composable
private fun MobileEsimInstallScreen(
    loading: Boolean,
    error: String?,
    retryVisible: Boolean,
    startEnabled: Boolean,
    compatibilityStatus: String,
    deviceStatus: String,
    downloadStatus: String,
    smdpText: String,
    matchingIdText: String,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onStart: () -> Unit
) {
    val orange = Color(0xFFFF6A00)
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
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onBack, shape = RoundedCornerShape(16.dp)) {
                        Text("Geri")
                    }

                    if (loading) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 10.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.height(20.dp))
                            Text("Kontrol ediliyor...")
                        }
                    }
                }

                HeroInstallCard(orange)

                if (!error.isNullOrBlank()) {
                    ErrorCard(error = error, onRetry = onRetry, retryVisible = retryVisible)
                }

                InfoCard(title = "Kurulum Ön Kontrol") {
                    StepRow("Aktivasyon kodu", compatibilityStatus)
                    StepRow("Cihaz eSIM desteği", deviceStatus)
                    StepRow("İndirme sihirbazı", downloadStatus)
                }

                if (smdpText.isNotBlank() || matchingIdText.isNotBlank()) {
                    InfoCard(title = "Manuel Kurulum Bilgileri") {
                        DetailRow("SMDP", smdpText)
                        DetailRow("Matching ID", matchingIdText)
                    }
                }

                InfoCard(title = "Sonraki Adım") {
                    Text(
                        text = "Kontroller geçerse OpenEUICC indirme sihirbazını açıp eSIM profilini cihaza kurabilirsin.",
                        color = Color(0xFF50535C),
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Button(
                        onClick = onStart,
                        enabled = startEnabled,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = orange),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Text("OpenEUICC ile Kuruluma Başla")
                    }

                    if (retryVisible) {
                        OutlinedButton(
                            onClick = onRetry,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Text("Tekrar Kontrol Et")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun HeroInstallCard(orange: Color) {
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
                text = "eSIM Kurulumu",
                color = orange,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "OpenEUICC ile güvenli kurulum",
                color = Color.White,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Aktivasyon kodu doğrulanır, cihaz eSIM desteği kontrol edilir ve ardından native OpenEUICC sihirbazı açılır.",
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
private fun StepRow(label: String, value: String) {
    val normalized = value.lowercase()
    val pill = when {
        normalized.contains("passed") || normalized.contains("ready") || normalized.contains("başar") ->
            Color(0xFFDCFCE7) to Color(0xFF166534)
        normalized.contains("blocked") || normalized.contains("failed") || normalized.contains("unsupported") ->
            Color(0xFFFEE2E2) to Color(0xFFB91C1C)
        normalized.contains("running") || normalized.contains("kontrol") ->
            Color(0xFFDBEAFE) to Color(0xFF1D4ED8)
        else ->
            Color(0xFFFEF9C3) to Color(0xFF854D0E)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(pill.first, RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(label, color = Color(0xFF50535C), style = MaterialTheme.typography.bodySmall)
        Text(value.ifBlank { "-" }, color = pill.second, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = Color(0xFF686B73), style = MaterialTheme.typography.bodySmall)
        Text(value.ifBlank { "-" }, color = Color(0xFF17181C), fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ErrorCard(error: String, onRetry: () -> Unit, retryVisible: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEAEA))
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Kurulum kontrolü başarısız", color = Color(0xFFB91C1C), fontWeight = FontWeight.Bold)
            Text(error, color = Color(0xFF7F1D1D))
            if (retryVisible) {
                OutlinedButton(onClick = onRetry) {
                    Text("Tekrar Dene")
                }
            }
        }
    }
}
