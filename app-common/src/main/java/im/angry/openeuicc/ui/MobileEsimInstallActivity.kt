package im.angry.openeuicc.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import im.angry.openeuicc.auth.MobileEsim
import im.angry.openeuicc.ui.wizard.DownloadWizardActivity
import im.angry.openeuicc.util.LPAString
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch

private val InstallBlue = Color(0xFF1263F1)
private val InstallBlueDark = Color(0xFF0649B8)
private val InstallText = Color(0xFF111827)
private val InstallMuted = Color(0xFF6B7280)
private val InstallBorder = Color(0xFFE5E7EB)
private val InstallBg = Color(0xFFF8FAFF)
private val InstallGreen = Color(0xFF16A34A)
private val InstallOrange = Color(0xFFF59E0B)
private val InstallRed = Color(0xFFDC2626)

class MobileEsimInstallActivity : BaseEuiccAccessActivity() {
    private var installCode: String = ""
    private var lpaPayload: String = ""

    private var loading by mutableStateOf(false)
    private var errorMessage by mutableStateOf<String?>(null)
    private var retryVisible by mutableStateOf(false)
    private var startEnabled by mutableStateOf(false)

    private var compatibilityStatus by mutableStateOf("Waiting")
    private var deviceStatus by mutableStateOf("Waiting")
    private var downloadStatus by mutableStateOf("Waiting")
    private var smdpText by mutableStateOf("")
    private var matchingIdText by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        actionBar?.hide()
        configureSystemBars()
        installCode = intent.getStringExtra(EXTRA_INSTALL_CODE).orEmpty()
        setInitialState()

        setContent {
            MobileEsimInstallScreen(
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

    private fun configureSystemBars() {
        window.statusBarColor = AndroidColor.rgb(248, 250, 255)
        window.navigationBarColor = AndroidColor.BLACK
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = false
        }
    }

    private fun setInitialState() {
        loading = false
        errorMessage = null
        retryVisible = false
        startEnabled = false
        compatibilityStatus = "Waiting"
        deviceStatus = "Waiting"
        downloadStatus = "Waiting"
        smdpText = ""
        matchingIdText = ""
    }

    private fun runPreflight() {
        lifecycleScope.launch {
            setInitialState()
            loading = true
            compatibilityStatus = "Checking"

            val parsed = runCatching {
                val payload = if (installCode.startsWith("LPA:", ignoreCase = true)) installCode else "LPA:$installCode"
                LPAString.parse(payload).also { lpaPayload = payload }
            }.getOrElse {
                compatibilityStatus = "Failed"
                deviceStatus = "Not Checked"
                showFailure("The activation code could not be parsed.")
                return@launch
            }

            compatibilityStatus = "Passed"
            smdpText = parsed.address
            matchingIdText = parsed.matchingId ?: "Unavailable"
            deviceStatus = "Checking"

            val openEuiccPorts = runCatching { euiccChannelManager.flowAllOpenEuiccPorts().toList() }.getOrElse {
                deviceStatus = "External Reader"
                downloadStatus = "Ready"
                loading = false
                startEnabled = true
                return@launch
            }

            val platformHasEuicc = packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY_EUICC)
            deviceStatus = when {
                openEuiccPorts.isNotEmpty() -> "USB Reader Ready"
                platformHasEuicc -> "Native eSIM Ready"
                else -> "External Reader"
            }
            downloadStatus = "Ready"
            loading = false
            startEnabled = true
        }
    }

    private fun showFailure(message: String) {
        loading = false
        errorMessage = message
        retryVisible = true
        startEnabled = false
        downloadStatus = "Blocked"
    }

    private fun launchDownloadWizard() {
        if (lpaPayload.isBlank()) return
        downloadStatus = "Launching"
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

        fun createIntent(context: Context, esim: MobileEsim): Intent =
            Intent(context, MobileEsimInstallActivity::class.java).apply {
                putExtra(EXTRA_INSTALL_CODE, esim.installCode())
            }
    }
}

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
    Surface(Modifier.fillMaxSize(), color = InstallBg) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Header(loading, onBack)
            InstallHero()
            if (!error.isNullOrBlank()) ErrorCard(error, onRetry, retryVisible)
            InstallCard("Preflight Checks", Icons.Default.Security) {
                CheckRow("Activation code", compatibilityStatus)
                CheckRow("Install target", deviceStatus)
                CheckRow("OpenEUICC download wizard", downloadStatus)
            }
            if (smdpText.isNotBlank() || matchingIdText.isNotBlank()) {
                InstallCard("Manual Installation Info", Icons.Default.QrCode2) {
                    InfoLine("SM-DP+", smdpText)
                    HorizontalDivider(color = InstallBorder)
                    InfoLine("Matching ID", matchingIdText)
                }
            }
            InstallCard("Next Step", Icons.Default.Download) {
                Text(
                    "OpenEUICC will ask you to select a native eSIM slot or an external USB eUICC reader, then continue with these activation details.",
                    color = InstallMuted,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
                Button(
                    onClick = onStart,
                    enabled = startEnabled,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = InstallBlue, disabledContainerColor = InstallBlue.copy(alpha = .35f)),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.Download, null, tint = Color.White, modifier = Modifier.size(22.dp))
                    Text("Start Installation", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(start = 10.dp))
                }
                if (retryVisible) {
                    OutlinedButton(onClick = onRetry, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, InstallBlue.copy(alpha = .45f))) {
                        Text("Run Checks Again", color = InstallBlue, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(loading: Boolean, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.ArrowBack, null, tint = InstallText, modifier = Modifier.size(30.dp).clickable(onClick = onBack))
        Text("Install eSIM", color = InstallText, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(start = 16.dp).weight(1f))
        if (loading) CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = InstallBlue)
    }
}

@Composable
private fun InstallHero() {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(Color.Transparent), elevation = CardDefaults.cardElevation(3.dp)) {
        Box(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(InstallBlue, InstallBlueDark))).padding(22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(62.dp).clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = .17f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.SimCard, null, tint = Color.White, modifier = Modifier.size(36.dp))
                }
                Column(Modifier.padding(start = 14.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Ready for OpenEUICC", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.ExtraBold)
                    Text("Send this eSIM to a native slot or external USB eUICC reader.", color = Color.White.copy(alpha = .82f), fontSize = 14.sp, lineHeight = 20.sp)
                }
            }
        }
    }
}

@Composable
private fun InstallCard(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(Color.White), border = BorderStroke(1.dp, InstallBorder), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(999.dp)).background(InstallBlue.copy(alpha = .10f)), contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = InstallBlue, modifier = Modifier.size(24.dp))
                }
                Text(title, color = InstallText, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(start = 12.dp))
            }
            HorizontalDivider(color = InstallBorder)
            content()
        }
    }
}

@Composable
private fun CheckRow(label: String, value: String) {
    val normalized = value.lowercase()
    val color = when {
        normalized.contains("passed") || normalized.contains("ready") -> InstallGreen
        normalized.contains("blocked") || normalized.contains("failed") || normalized.contains("unsupported") -> InstallRed
        normalized.contains("checking") || normalized.contains("launching") -> InstallBlue
        else -> InstallOrange
    }
    Row(Modifier.fillMaxWidth().background(color.copy(alpha = .10f), RoundedCornerShape(12.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.CheckCircle, null, tint = color, modifier = Modifier.size(22.dp))
        Text(label, color = InstallText, fontSize = 15.sp, modifier = Modifier.padding(start = 10.dp).weight(1f))
        Text(value.ifBlank { "Waiting" }, color = color, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = InstallMuted, fontSize = 14.sp, modifier = Modifier.weight(.36f))
        Text(value.ifBlank { "—" }, color = InstallText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(.64f))
    }
}

@Composable
private fun ErrorCard(error: String, onRetry: () -> Unit, retryVisible: Boolean) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(Color(0xFFFFEAEA)), border = BorderStroke(1.dp, Color(0xFFFFCACA))) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Installation check failed", color = InstallRed, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
            Text(error, color = Color(0xFF7F1D1D), fontSize = 14.sp)
            if (retryVisible) {
                OutlinedButton(onClick = onRetry, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, InstallRed.copy(alpha = .4f))) {
                    Text("Try Again", color = InstallRed, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
