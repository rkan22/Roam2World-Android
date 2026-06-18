package im.angry.openeuicc.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
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
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import im.angry.openeuicc.auth.AuthSession
import im.angry.openeuicc.auth.AuthTokenStore
import im.angry.openeuicc.auth.JwtUtils
import im.angry.openeuicc.auth.MobileEsim
import im.angry.openeuicc.auth.MobileEsimLastRenewal
import im.angry.openeuicc.auth.Roam2WorldAuthApi
import im.angry.openeuicc.common.BuildConfig
import im.angry.openeuicc.common.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val EsimBlue = Color(0xFF1263F1)
private val EsimText = Color(0xFF111827)
private val EsimMuted = Color(0xFF6B7280)
private val EsimBorder = Color(0xFFE5E7EB)
private val EsimBg = Color(0xFFF8FAFF)
private val EsimGreen = Color(0xFF16A34A)
private val EsimOrange = Color(0xFFF59E0B)
private val EsimRed = Color(0xFFDC2626)

class MobileEsimDetailActivity : ComponentActivity() {
    private val tokenStore by lazy { AuthTokenStore(this) }
    private val authApi by lazy { Roam2WorldAuthApi(BuildConfig.ROAM2WORLD_API_BASE_URL) }

    private var currentEsim by mutableStateOf<MobileEsim?>(null)
    private var loading by mutableStateOf(false)
    private var errorMessage by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        actionBar?.hide()
        configureSystemBars()
        currentEsim = readIntentEsim()

        setContent {
            CompactMobileEsimDetailScreen(
                esim = currentEsim,
                loading = loading,
                error = errorMessage,
                onBack = { finish() },
                onRetry = { loadLatestDetails() },
                onCopy = { label, value -> copyToClipboard(label, value) },
                onShowQr = { esim -> startActivity(MobileEsimQrActivity.createIntent(this, esim)) },
                onShare = { esim -> shareInstallText(esim) },
                onInstall = { esim -> launchInstallFlow(esim) },
                onRenew = { esim -> openRenewal(esim) }
            )
        }

        loadLatestDetails()
    }

    private fun configureSystemBars() {
        window.statusBarColor = AndroidColor.rgb(248, 250, 255)
        window.navigationBarColor = AndroidColor.BLACK
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = false
        }
    }

    private fun loadLatestDetails() {
        lifecycleScope.launch {
            val selected = currentEsim ?: return@launch
            errorMessage = null
            loading = true
            val session = activeSessionOrReturnToLogin()
            if (session == null) {
                loading = false
                return@launch
            }
            val result = runCatching {
                selected.id?.takeIf { it.isNotBlank() }?.let { authApi.esim(session, it) }
                    ?: authApi.esims(session).esims.firstOrNull { it.matches(selected) }
                    ?: throw IllegalStateException(getString(R.string.mobile_esim_detail_missing))
            }
            result
                .onSuccess { currentEsim = it }
                .onFailure { errorMessage = it.message ?: getString(R.string.mobile_esims_load_failed) }
            loading = false
        }
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
        startActivity(Intent(this, LoginActivity::class.java).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK) })
        finish()
        return null
    }

    private fun launchInstallFlow(esim: MobileEsim) {
        if (esim.installCode().isNullOrBlank() && esim.qrPayload().isNullOrBlank()) {
            Toast.makeText(this, "Install information is not ready yet", Toast.LENGTH_SHORT).show()
            return
        }
        startActivity(MobileEsimInstallActivity.createIntent(this, esim))
    }

    private fun openRenewal(esim: MobileEsim) {
        esim.iccid?.takeIf { it.isNotBlank() }?.let { copyToClipboard("ICCID", it) }
        val provider = esim.provider.orEmpty().lowercase()
        val target = if (provider.contains("airhub") || provider.contains("vodafone")) VodafoneRenewalActivity::class.java else TgtSimRechargeActivity::class.java
        startActivity(Intent(this, target).apply { putExtra("renew.iccid", esim.iccid) })
    }

    private fun copyToClipboard(label: String, value: String?) {
        if (value.isNullOrBlank()) return
        getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(label, value))
        Toast.makeText(this, "$label copied", Toast.LENGTH_SHORT).show()
    }

    private fun shareInstallText(esim: MobileEsim) {
        val payload = esim.qrPayload().orEmpty()
        if (payload.isBlank()) {
            Toast.makeText(this, "QR information is not ready yet", Toast.LENGTH_SHORT).show()
            return
        }
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "eSIM Installation - ${displayPackage(esim)}")
            putExtra(Intent.EXTRA_TEXT, "${displayPackage(esim)}\n\nInstall code:\n$payload")
        }, "Share eSIM install info"))
    }

    private fun readIntentEsim(): MobileEsim? =
        if (listOf(EXTRA_ID, EXTRA_ICCID, EXTRA_PROVIDER, EXTRA_PACKAGE_NAME, EXTRA_ACTIVATION_CODE, EXTRA_LPA_CODE, EXTRA_SMDP, EXTRA_QR_CODE, EXTRA_QR_URL, EXTRA_EXPIRES_AT, EXTRA_DATA_REMAINING, EXTRA_DATA_USED).none { !intent.getStringExtra(it).isNullOrBlank() }) {
            null
        } else {
            MobileEsim(
                id = intent.getStringExtra(EXTRA_ID),
                iccid = intent.getStringExtra(EXTRA_ICCID),
                provider = intent.getStringExtra(EXTRA_PROVIDER),
                packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME),
                status = intent.getStringExtra(EXTRA_STATUS),
                activationCode = intent.getStringExtra(EXTRA_ACTIVATION_CODE),
                lpaCode = intent.getStringExtra(EXTRA_LPA_CODE),
                smdpAddress = intent.getStringExtra(EXTRA_SMDP),
                matchingId = intent.getStringExtra(EXTRA_MATCHING_ID),
                confirmationCodeRequired = intent.getBooleanExtra(EXTRA_CONFIRMATION_REQUIRED, false),
                qrCode = intent.getStringExtra(EXTRA_QR_CODE),
                qrCodeUrl = intent.getStringExtra(EXTRA_QR_URL),
                createdAt = intent.getStringExtra(EXTRA_CREATED_AT),
                orderNumber = intent.getStringExtra(EXTRA_ORDER_NUMBER),
                expiresAt = intent.getStringExtra(EXTRA_EXPIRES_AT),
                dataRemaining = intent.getStringExtra(EXTRA_DATA_REMAINING),
                dataUsed = intent.getStringExtra(EXTRA_DATA_USED),
                orderId = intent.getStringExtra(EXTRA_ORDER_ID),
                lastRenewal = readIntentLastRenewal()
            )
        }

    private fun readIntentLastRenewal(): MobileEsimLastRenewal? {
        val orderNo = intent.getStringExtra(EXTRA_LAST_RENEWAL_ORDER_NO)
        val message = intent.getStringExtra(EXTRA_LAST_RENEWAL_MESSAGE)
        val productName = intent.getStringExtra(EXTRA_LAST_RENEWAL_PRODUCT_NAME)
        if (orderNo.isNullOrBlank() && message.isNullOrBlank() && productName.isNullOrBlank()) return null
        return MobileEsimLastRenewal(
            provider = intent.getStringExtra(EXTRA_LAST_RENEWAL_PROVIDER),
            success = if (intent.hasExtra(EXTRA_LAST_RENEWAL_SUCCESS)) intent.getBooleanExtra(EXTRA_LAST_RENEWAL_SUCCESS, false) else null,
            message = message,
            code = intent.getStringExtra(EXTRA_LAST_RENEWAL_CODE),
            orderNo = orderNo,
            productName = productName,
            productCode = intent.getStringExtra(EXTRA_LAST_RENEWAL_PRODUCT_CODE),
            createdTime = intent.getStringExtra(EXTRA_LAST_RENEWAL_CREATED_TIME),
            activatedEndTime = intent.getStringExtra(EXTRA_LAST_RENEWAL_ACTIVATED_END_TIME),
            renewExpirationTime = intent.getStringExtra(EXTRA_LAST_RENEWAL_RENEW_EXPIRATION_TIME),
            latestActivationTime = intent.getStringExtra(EXTRA_LAST_RENEWAL_LATEST_ACTIVATION_TIME),
            orderStatus = intent.getStringExtra(EXTRA_LAST_RENEWAL_ORDER_STATUS),
            profileStatus = intent.getStringExtra(EXTRA_LAST_RENEWAL_PROFILE_STATUS)
        )
    }

    private fun MobileEsim.matches(other: MobileEsim): Boolean = listOf(id to other.id, iccid to other.iccid, orderNumber to other.orderNumber, orderId to other.orderId).any { (left, right) -> !left.isNullOrBlank() && left == right }

    companion object {
        private const val EXTRA_ID = "mobile_esim.id"
        private const val EXTRA_ICCID = "mobile_esim.iccid"
        private const val EXTRA_PROVIDER = "mobile_esim.provider"
        private const val EXTRA_PACKAGE_NAME = "mobile_esim.package_name"
        private const val EXTRA_STATUS = "mobile_esim.status"
        private const val EXTRA_ACTIVATION_CODE = "mobile_esim.activation_code"
        private const val EXTRA_LPA_CODE = "mobile_esim.lpa_code"
        private const val EXTRA_SMDP = "mobile_esim.smdp"
        private const val EXTRA_MATCHING_ID = "mobile_esim.matching_id"
        private const val EXTRA_CONFIRMATION_REQUIRED = "mobile_esim.confirmation_required"
        private const val EXTRA_QR_CODE = "mobile_esim.qr_code"
        private const val EXTRA_QR_URL = "mobile_esim.qr_url"
        private const val EXTRA_CREATED_AT = "mobile_esim.created_at"
        private const val EXTRA_ORDER_NUMBER = "mobile_esim.order_number"
        private const val EXTRA_EXPIRES_AT = "mobile_esim.expires_at"
        private const val EXTRA_DATA_REMAINING = "mobile_esim.data_remaining"
        private const val EXTRA_DATA_USED = "mobile_esim.data_used"
        private const val EXTRA_ORDER_ID = "mobile_esim.order_id"
        private const val EXTRA_LAST_RENEWAL_PROFILE_STATUS = "mobile_esim.last_renewal.profile_status"
        private const val EXTRA_LAST_RENEWAL_ORDER_STATUS = "mobile_esim.last_renewal.order_status"
        private const val EXTRA_LAST_RENEWAL_LATEST_ACTIVATION_TIME = "mobile_esim.last_renewal.latest_activation_time"
        private const val EXTRA_LAST_RENEWAL_RENEW_EXPIRATION_TIME = "mobile_esim.last_renewal.renew_expiration_time"
        private const val EXTRA_LAST_RENEWAL_ACTIVATED_END_TIME = "mobile_esim.last_renewal.activated_end_time"
        private const val EXTRA_LAST_RENEWAL_CREATED_TIME = "mobile_esim.last_renewal.created_time"
        private const val EXTRA_LAST_RENEWAL_PRODUCT_CODE = "mobile_esim.last_renewal.product_code"
        private const val EXTRA_LAST_RENEWAL_PRODUCT_NAME = "mobile_esim.last_renewal.product_name"
        private const val EXTRA_LAST_RENEWAL_ORDER_NO = "mobile_esim.last_renewal.order_no"
        private const val EXTRA_LAST_RENEWAL_CODE = "mobile_esim.last_renewal.code"
        private const val EXTRA_LAST_RENEWAL_MESSAGE = "mobile_esim.last_renewal.message"
        private const val EXTRA_LAST_RENEWAL_SUCCESS = "mobile_esim.last_renewal.success"
        private const val EXTRA_LAST_RENEWAL_PROVIDER = "mobile_esim.last_renewal.provider"

        fun createIntent(context: Context, esimId: String): Intent = Intent(context, MobileEsimDetailActivity::class.java).apply { putExtra(EXTRA_ID, esimId) }

        fun createIntent(context: Context, esim: MobileEsim): Intent = Intent(context, MobileEsimDetailActivity::class.java).apply {
            putExtra(EXTRA_ID, esim.id)
            putExtra(EXTRA_ICCID, esim.iccid)
            putExtra(EXTRA_PROVIDER, esim.provider)
            putExtra(EXTRA_PACKAGE_NAME, esim.packageName)
            putExtra(EXTRA_STATUS, esim.status)
            putExtra(EXTRA_ACTIVATION_CODE, esim.activationCode)
            putExtra(EXTRA_LPA_CODE, esim.lpaCode)
            putExtra(EXTRA_SMDP, esim.smdpAddress)
            putExtra(EXTRA_MATCHING_ID, esim.matchingId)
            putExtra(EXTRA_CONFIRMATION_REQUIRED, esim.confirmationCodeRequired)
            putExtra(EXTRA_QR_CODE, esim.qrCode)
            putExtra(EXTRA_QR_URL, esim.qrCodeUrl)
            putExtra(EXTRA_CREATED_AT, esim.createdAt)
            putExtra(EXTRA_ORDER_NUMBER, esim.orderNumber)
            putExtra(EXTRA_EXPIRES_AT, esim.expiresAt)
            putExtra(EXTRA_DATA_REMAINING, esim.dataRemaining)
            putExtra(EXTRA_DATA_USED, esim.dataUsed)
            putExtra(EXTRA_ORDER_ID, esim.orderId)
            if (esim.lastRenewal?.success != null) putExtra(EXTRA_LAST_RENEWAL_SUCCESS, esim.lastRenewal.success)
            putExtra(EXTRA_LAST_RENEWAL_PROVIDER, esim.lastRenewal?.provider)
            putExtra(EXTRA_LAST_RENEWAL_MESSAGE, esim.lastRenewal?.message)
            putExtra(EXTRA_LAST_RENEWAL_CODE, esim.lastRenewal?.code)
            putExtra(EXTRA_LAST_RENEWAL_ORDER_NO, esim.lastRenewal?.orderNo)
            putExtra(EXTRA_LAST_RENEWAL_PRODUCT_NAME, esim.lastRenewal?.productName)
            putExtra(EXTRA_LAST_RENEWAL_PRODUCT_CODE, esim.lastRenewal?.productCode)
            putExtra(EXTRA_LAST_RENEWAL_CREATED_TIME, esim.lastRenewal?.createdTime)
            putExtra(EXTRA_LAST_RENEWAL_ACTIVATED_END_TIME, esim.lastRenewal?.activatedEndTime)
            putExtra(EXTRA_LAST_RENEWAL_RENEW_EXPIRATION_TIME, esim.lastRenewal?.renewExpirationTime)
            putExtra(EXTRA_LAST_RENEWAL_LATEST_ACTIVATION_TIME, esim.lastRenewal?.latestActivationTime)
            putExtra(EXTRA_LAST_RENEWAL_ORDER_STATUS, esim.lastRenewal?.orderStatus)
            putExtra(EXTRA_LAST_RENEWAL_PROFILE_STATUS, esim.lastRenewal?.profileStatus)
        }
    }
}

@Composable
private fun CompactMobileEsimDetailScreen(
    esim: MobileEsim?,
    loading: Boolean,
    error: String?,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onCopy: (String, String?) -> Unit,
    onShowQr: (MobileEsim) -> Unit,
    onShare: (MobileEsim) -> Unit,
    onInstall: (MobileEsim) -> Unit,
    onRenew: (MobileEsim) -> Unit
) {
    Surface(Modifier.fillMaxSize(), color = EsimBg) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 20.dp, top = 14.dp, end = 20.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.ArrowBack, null, tint = EsimText, modifier = Modifier.size(30.dp).clickable(onClick = onBack))
                Text("eSIM Detail", color = EsimText, fontSize = 27.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(start = 16.dp).weight(1f))
                if (loading) CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = EsimBlue)
            }

            if (!error.isNullOrBlank()) ErrorCard(error, onRetry)
            if (esim == null) {
                EmptyState()
                return@Column
            }

            HeroCard(esim)
            QuickStatsCard(esim)
            DetailsCard(esim, onCopy)
            ActivationCard(esim, onCopy)
            ActionPanel(esim, onShowQr, onShare, onInstall, onRenew)
            RenewalCard(esim)
        }
    }
}

@Composable
private fun HeroCard(esim: MobileEsim) {
    val status = realStatus(esim)
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(Color.White), border = BorderStroke(1.dp, EsimBorder), elevation = CardDefaults.cardElevation(3.dp)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(62.dp).clip(RoundedCornerShape(18.dp)).background(EsimBlue.copy(alpha = .10f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.SimCard, null, tint = EsimBlue, modifier = Modifier.size(36.dp))
                }
                Column(Modifier.padding(start = 14.dp).weight(1f)) {
                    Text(displayPackage(esim), color = EsimText, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(visibleProvider(esim.provider).orEmpty().ifBlank { providerFromPackage(esim) }, color = EsimMuted, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                }
            }
            StatusPill(status.label)
            Text("Manage installation, activation and customer delivery details for this eSIM.", color = EsimMuted, fontSize = 14.sp, lineHeight = 20.sp)
        }
    }
}

@Composable
private fun QuickStatsCard(esim: MobileEsim) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        MiniStat("Data", displayEsimData(esim), Icons.Default.SimCard, Modifier.weight(1f))
        MiniStat("Validity", displayEsimValidity(esim), Icons.Default.CalendarMonth, Modifier.weight(1f))
        MiniStat("Expiry", displayEsimExpiry(esim), Icons.Default.Security, Modifier.weight(1f))
    }
}

@Composable
private fun MiniStat(label: String, value: String, icon: ImageVector, modifier: Modifier) {
    Card(modifier, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(Color.White), border = BorderStroke(1.dp, EsimBorder)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null, tint = EsimBlue, modifier = Modifier.size(22.dp))
            Text(label, color = EsimMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(value, color = EsimText, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun DetailsCard(esim: MobileEsim, onCopy: (String, String?) -> Unit) {
    SectionCard("eSIM Details", Icons.Default.SimCard) {
        DetailRow("ICCID", esim.iccid.orEmpty(), copyable = true, onCopy = { onCopy("ICCID", esim.iccid) })
        DetailRow("Provider", visibleProvider(esim.provider).orEmpty().ifBlank { providerFromPackage(esim) })
        DetailRow("Package", displayPackage(esim))
        DetailRow("Status", realStatus(esim).label, valueColor = statusColor(realStatus(esim).raw))
        DetailRow("Order", esim.orderNumber.orEmpty())
        DetailRow("Created", esim.createdAt?.let { formatProviderDate(it) }.orEmpty())
    }
}

@Composable
private fun ActivationCard(esim: MobileEsim, onCopy: (String, String?) -> Unit) {
    SectionCard("Activation Details", Icons.Default.QrCode2) {
        val payload = esim.qrPayload().orEmpty()
        DetailRow("QR / LPA", if (payload.isBlank()) "Pending" else payload, copyable = payload.isNotBlank(), maxLines = 3, onCopy = { onCopy("QR / LPA", payload) })
        DetailRow("SM-DP+", esim.smdpAddress.orEmpty(), copyable = !esim.smdpAddress.isNullOrBlank(), maxLines = 2, onCopy = { onCopy("SM-DP+", esim.smdpAddress) })
        DetailRow("Matching ID", esim.matchingId.orEmpty(), copyable = !esim.matchingId.isNullOrBlank(), maxLines = 2, onCopy = { onCopy("Matching ID", esim.matchingId) })
        DetailRow("Activation Code", esim.activationCode.orEmpty(), copyable = !esim.activationCode.isNullOrBlank(), maxLines = 2, onCopy = { onCopy("Activation Code", esim.activationCode) })
    }
}

@Composable
private fun ActionPanel(esim: MobileEsim, onShowQr: (MobileEsim) -> Unit, onShare: (MobileEsim) -> Unit, onInstall: (MobileEsim) -> Unit, onRenew: (MobileEsim) -> Unit) {
    val installReady = !esim.installCode().isNullOrBlank() || !esim.qrPayload().isNullOrBlank()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(onClick = { onInstall(esim) }, enabled = installReady, modifier = Modifier.fillMaxWidth().height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = EsimBlue, disabledContainerColor = EsimBlue.copy(alpha = .36f)), shape = RoundedCornerShape(14.dp)) {
            Icon(Icons.Default.Download, null, tint = Color.White, modifier = Modifier.size(23.dp))
            Text(if (installReady) "Install eSIM / OpenEUICC" else "Installation Pending", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(start = 10.dp))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionButton("QR", Icons.Default.QrCode2, Modifier.weight(1f)) { onShowQr(esim) }
            ActionButton("Share", Icons.Default.Share, Modifier.weight(1f)) { onShare(esim) }
        }
        if (canRenew(esim)) ActionButton("Renew eSIM", Icons.Default.Refresh, Modifier.fillMaxWidth()) { onRenew(esim) }
    }
}

@Composable
private fun ActionButton(text: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = modifier.height(52.dp), shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, EsimBlue.copy(alpha = .45f))) {
        Icon(icon, null, tint = EsimBlue, modifier = Modifier.size(21.dp))
        Text(text, color = EsimBlue, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun RenewalCard(esim: MobileEsim) {
    val renewal = esim.lastRenewal ?: return
    SectionCard("Last Renewal", Icons.Default.Refresh) {
        DetailRow("Status", renewal.message.orEmpty())
        DetailRow("Order", renewal.orderNo.orEmpty())
        DetailRow("Package", renewal.productName.orEmpty())
        DetailRow("Expiry", renewal.renewExpirationTime?.let { formatProviderDate(it) }.orEmpty())
    }
}

@Composable
private fun SectionCard(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(Color.White), border = BorderStroke(1.dp, EsimBorder), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(999.dp)).background(EsimBlue.copy(alpha = .10f)), contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = EsimBlue, modifier = Modifier.size(25.dp))
                }
                Text(title, color = EsimText, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(start = 12.dp))
            }
            HorizontalDivider(color = EsimBorder)
            content()
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String, valueColor: Color = EsimText, copyable: Boolean = false, maxLines: Int = 1, onCopy: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = EsimMuted, fontSize = 14.sp, modifier = Modifier.weight(.42f))
        Text(value.ifBlank { "—" }, color = valueColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = maxLines, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End, modifier = Modifier.weight(.50f))
        if (copyable) Icon(Icons.Default.ContentCopy, null, tint = EsimBlue, modifier = Modifier.padding(start = 8.dp).size(20.dp).clickable(onClick = onCopy))
    }
}

@Composable
private fun StatusPill(label: String) {
    val color = statusColor(label)
    Text(label, color = color, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.background(color.copy(alpha = .12f), RoundedCornerShape(999.dp)).padding(horizontal = 12.dp, vertical = 7.dp))
}

@Composable
private fun ErrorCard(error: String, onRetry: () -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(Color(0xFFFFEAEA)), border = BorderStroke(1.dp, Color(0xFFFFCACA))) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Detail could not be loaded", color = EsimRed, fontWeight = FontWeight.Bold)
            Text(error, color = Color(0xFF7F1D1D), fontSize = 13.sp)
            OutlinedButton(onClick = onRetry, shape = RoundedCornerShape(12.dp)) { Text("Retry") }
        }
    }
}

@Composable
private fun EmptyState() {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(Color.White), border = BorderStroke(1.dp, EsimBorder)) {
        Text("eSIM details are not available.", modifier = Modifier.padding(18.dp), color = EsimMuted)
    }
}

private data class DisplayStatus(val label: String, val raw: String)

private fun visibleProvider(provider: String?): String? = provider?.replace("TGT", "Orange", ignoreCase = true)?.replace("tgt", "Orange", ignoreCase = true)

private fun realStatus(esim: MobileEsim): DisplayStatus {
    val raw = esim.status.orEmpty().trim()
    val normalized = raw.lowercase()
    val expiresAt = parseDate(esim.expiresAt)
    val isExpiredByDate = expiresAt?.isBefore(OffsetDateTime.now()) == true
    val hasIccid = !esim.iccid.isNullOrBlank()
    val hasInstallCode = !esim.installCode().isNullOrBlank() || !esim.qrPayload().isNullOrBlank()
    return when {
        normalized.contains("expired") || normalized.contains("depleted") || normalized.contains("terminated") || isExpiredByDate -> DisplayStatus("Expired", "expired")
        normalized.contains("active") || normalized.contains("activated") || normalized.contains("enabled") -> DisplayStatus("Active", "active")
        normalized.contains("pending") || normalized.contains("processing") || normalized.contains("waiting") || normalized.contains("ordered") -> DisplayStatus("Pending", "pending")
        hasIccid && hasInstallCode -> DisplayStatus("Ready", "ready")
        hasInstallCode -> DisplayStatus("Ready to Install", "ready")
        hasIccid -> DisplayStatus("Provisioned", "provisioned")
        else -> DisplayStatus("Pending", "pending")
    }
}

private fun statusColor(value: String): Color {
    val normalized = value.lowercase()
    return when {
        normalized.contains("active") || normalized.contains("ready") || normalized.contains("provision") -> EsimGreen
        normalized.contains("expired") || normalized.contains("used") || normalized.contains("terminated") -> EsimRed
        else -> EsimOrange
    }
}

private fun parseDate(value: String?): OffsetDateTime? = value?.takeIf { it.isNotBlank() }?.let { runCatching { OffsetDateTime.parse(it) }.getOrNull() }

private fun cleanEsimValue(value: String?): String = value.orEmpty().replace(Regex("""^(Data Remaining|Data Used|Expiry|Expires|Package|Status|Provider|ICCID):\s*""", RegexOption.IGNORE_CASE), "").trim()

private fun extractDataFromPackage(packageName: String?): String {
    val match = Regex("""(?i)\b(\d+(?:[.,]\d+)?)\s*(GB|MB)\b""").find(packageName.orEmpty())
    return match?.let { "${it.groupValues[1].replace(",", ".")} ${it.groupValues[2].uppercase(Locale.ENGLISH)}" }.orEmpty()
}

private fun extractValidityFromPackage(packageName: String?): String {
    val match = Regex("""(?i)\b(\d+)\s*(day|days|gün|gun)\b""").find(packageName.orEmpty())
    return match?.let { "${it.groupValues[1]} Days" }.orEmpty()
}

private fun displayEsimData(esim: MobileEsim): String = cleanEsimValue(esim.dataRemaining).ifBlank { cleanEsimValue(esim.dataUsed) }.ifBlank { extractDataFromPackage(esim.packageName) }.ifBlank { "—" }
private fun displayEsimValidity(esim: MobileEsim): String = extractValidityFromPackage(esim.packageName).ifBlank { "—" }
private fun displayEsimExpiry(esim: MobileEsim): String = esim.expiresAt?.let { formatProviderDate(it) }.orEmpty().ifBlank { esim.lastRenewal?.renewExpirationTime?.let { formatProviderDate(it) }.orEmpty() }.ifBlank { esim.lastRenewal?.activatedEndTime?.let { formatProviderDate(it) }.orEmpty() }.ifBlank { "—" }

private fun formatProviderDate(value: String): String {
    if (value.isBlank()) return ""
    return try {
        Instant.parse(value).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH))
    } catch (_: Exception) {
        value
    }
}

private fun providerFromPackage(esim: MobileEsim): String = if (displayPackage(esim).contains("vodafone", true)) "Vodafone" else "Orange"

private fun displayPackage(esim: MobileEsim): String {
    val cleaned = PackageNameCleaner.clean(esim.packageName).orEmpty().ifBlank { esim.title() }
    val data = extractDataFromPackage(cleaned)
    val lower = cleaned.lowercase()
    val region = when {
        lower.contains("turkey") || lower.contains("türkiye") -> "Turkey"
        lower.contains("europe") -> "Europe"
        lower.contains("balkan") -> "Balkans"
        lower.contains("world") || lower.contains("global") -> "World"
        else -> "Turkey"
    }
    val provider = if (lower.contains("vodafone")) "Vodafone" else "Orange"
    return listOf(provider, region, data).filter { it.isNotBlank() }.joinToString(" ").ifBlank { cleaned }
}

private fun formatProviderStatus(value: String): String = when (value.uppercase(Locale.ROOT)) {
    "INUSE" -> "In Use"
    "ACTIVATED" -> "Activated"
    "NOTACTIVE" -> "Not Activated"
    "USED" -> "Used Up"
    "EXPIRED" -> "Expired"
    "ABANDON" -> "Abandoned"
    "TERMINATION" -> "Terminated"
    "ENABLED" -> "Enabled"
    "DISABLED" -> "Disabled"
    else -> value.ifBlank { "Unknown" }
}

private fun canRenew(esim: MobileEsim): Boolean {
    if (realStatus(esim).raw == "expired") return false
    val provider = esim.provider.orEmpty().lowercase()
    return provider.contains("tgt") || provider.contains("airhub") || provider.contains("vodafone")
}
