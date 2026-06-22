package im.angry.openeuicc.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import im.angry.openeuicc.auth.AuthSession
import im.angry.openeuicc.auth.AuthTokenStore
import im.angry.openeuicc.auth.JwtUtils
import im.angry.openeuicc.auth.MobileEsim
import im.angry.openeuicc.auth.Roam2WorldAuthApi
import im.angry.openeuicc.common.BuildConfig
import im.angry.openeuicc.common.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URLEncoder
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment

class MobileEsimDetailActivity : ComponentActivity() {
    private val tokenStore by lazy { AuthTokenStore(this) }
    private val authApi by lazy { Roam2WorldAuthApi(BuildConfig.ROAM2WORLD_API_BASE_URL) }

    private var currentEsim by mutableStateOf<MobileEsim?>(null)
    private var loading by mutableStateOf(false)
    private var errorMessage by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        currentEsim = readIntentEsim()

        setContent {
            MobileEsimDetailScreen(
                esim = currentEsim,
                loading = loading,
                error = errorMessage,
                onBack = { finish() },
                onRetry = { loadLatestDetails() },
                onCopy = { label, value, toastRes -> copyToClipboard(label, value, toastRes) },
                onShowQr = { esim -> startActivity(MobileEsimQrActivity.createIntent(this, esim)) },
                onShareQr = { esim -> shareQrPayload(esim) },
                onSendCustomer = { esim ->
                    shareQrPayload(esim, esim.customerEmail?.takeIf { email -> email.isNotBlank() })
                },
                onInstall = { esim -> launchInstallFlow(esim, esim.installCode()) },
                onRenew = { esim -> openRenewal(esim) }
            )
        }

        loadLatestDetails()
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

    private fun launchInstallFlow(esim: MobileEsim, installCode: String?) {
        if (installCode.isNullOrBlank()) {
            Toast.makeText(this, "Install bilgisi henüz hazır değil", Toast.LENGTH_SHORT).show()
            return
        }
        startActivity(MobileEsimInstallActivity.createIntent(this, esim))
    }

    private fun openRenewal(esim: MobileEsim) {
        esim.iccid?.takeIf { it.isNotBlank() }?.let {
            getSystemService(ClipboardManager::class.java)
                .setPrimaryClip(ClipData.newPlainText("ICCID", it))
            Toast.makeText(this, "ICCID copied for renewal", Toast.LENGTH_SHORT).show()
        }

        val provider = esim.provider.orEmpty().lowercase()
        val target = if (provider.contains("airhub") || provider.contains("vodafone")) {
            VodafoneRenewalActivity::class.java
        } else {
            TgtSimRechargeActivity::class.java
        }

        startActivity(Intent(this, target).apply { putExtra("renew.iccid", esim.iccid) })
    }

    private fun copyToClipboard(label: String, value: String?, toastResId: Int) {
        if (value.isNullOrBlank()) return
        getSystemService(ClipboardManager::class.java)
            .setPrimaryClip(ClipData.newPlainText(label, value))
        Toast.makeText(this, toastResId, Toast.LENGTH_SHORT).show()
    }

    private fun shareQrPayload(esim: MobileEsim, recipientEmail: String? = null) {
        val payload = esim.qrPayload().orEmpty()
        if (payload.isBlank()) return

        val planName = esim.title()
        val iphoneLink = buildIphoneInstallLink(payload)
        val androidLink = buildAndroidInstallLink(payload)
        val bitmap = createQrBitmap(payload, QR_SIZE) ?: return

        val outputDir = File(cacheDir, "qr").apply { mkdirs() }
        val outputFile = File(outputDir, "esim-install-${System.currentTimeMillis()}.png")
        outputFile.outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        val uri = FileProvider.getUriForFile(
            this,
            "${packageName}.fileprovider",
            outputFile
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "message/rfc822"
            putExtra(Intent.EXTRA_SUBJECT, "eSIM Installation - $planName")
            putExtra(Intent.EXTRA_TEXT, buildInstallPlainText(planName, iphoneLink, androidLink))
            putExtra(Intent.EXTRA_HTML_TEXT, buildInstallHtml(planName, iphoneLink, androidLink))
            putExtra(Intent.EXTRA_STREAM, uri)
            recipientEmail?.let { putExtra(Intent.EXTRA_EMAIL, arrayOf(it)) }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        startActivity(Intent.createChooser(intent, getString(R.string.mobile_esim_share_qr)))
    }

    private fun buildIphoneInstallLink(payload: String): String =
        "https://esimsetup.apple.com/esim_qrcode_provisioning?carddata=${URLEncoder.encode(payload, "UTF-8")}"

    private fun buildAndroidInstallLink(payload: String): String =
        payload.replaceFirst("LPA:", "lpa:", ignoreCase = true)

    private fun buildInstallPlainText(planName: String, iphoneLink: String, androidLink: String): String =
        buildString {
            appendLine(planName)
            appendLine()
            appendLine("QR code is attached.")
            appendLine()
            appendLine("iPhone Quick Install:")
            appendLine(iphoneLink)
            appendLine()
            appendLine("Android Quick Install:")
            appendLine(androidLink)
        }

    private fun buildInstallHtml(planName: String, iphoneLink: String, androidLink: String): String {
        val safePlanName = htmlEscape(planName)
        val safeIphoneLink = htmlEscape(iphoneLink)
        val safeAndroidLink = htmlEscape(androidLink)

        return """
            <html>
              <body style="font-family: Arial, sans-serif; color: #18263A; line-height: 1.45;">
                <h2 style="margin: 0 0 12px 0;">$safePlanName</h2>
                <p style="margin: 0 0 18px 0;">QR code is attached.</p>
                <p style="margin: 0 0 14px 0;">
                  <a href="$safeIphoneLink"
                     style="display:inline-block;background:#2563EB;color:#FFFFFF;text-decoration:none;
                            padding:12px 18px;border-radius:10px;font-weight:bold;">
                    iPhone Quick Install
                  </a>
                </p>
                <p style="margin: 0 0 18px 0;">
                  <a href="$safeAndroidLink"
                     style="display:inline-block;background:#0F172A;color:#FFFFFF;text-decoration:none;
                            padding:12px 18px;border-radius:10px;font-weight:bold;">
                    Android Quick Install
                  </a>
                </p>
                <p style="font-size:12px;color:#5F6B7C;margin-top:20px;">
                  If the buttons do not open, copy and paste the links below:<br/>
                  iPhone: $safeIphoneLink<br/>
                  Android: $safeAndroidLink
                </p>
              </body>
            </html>
        """.trimIndent()
    }

    private fun htmlEscape(value: String): String =
        value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")

    private fun readIntentEsim(): MobileEsim? =
        if (listOf(
                EXTRA_ID,
                EXTRA_ICCID,
                EXTRA_PROVIDER,
                EXTRA_PACKAGE_NAME,
                EXTRA_ACTIVATION_CODE,
                EXTRA_LPA_CODE,
                EXTRA_SMDP,
                EXTRA_QR_CODE,
                EXTRA_QR_URL,
                EXTRA_EXPIRES_AT,
                EXTRA_DATA_REMAINING,
                EXTRA_DATA_USED,
                EXTRA_CUSTOMER_FIRST_NAME,
                EXTRA_CUSTOMER_LAST_NAME,
                EXTRA_CUSTOMER_PHONE,
                EXTRA_CUSTOMER_EMAIL
            ).none { !intent.getStringExtra(it).isNullOrBlank() }
        ) {
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
                customerFirstName = intent.getStringExtra(EXTRA_CUSTOMER_FIRST_NAME),
                customerLastName = intent.getStringExtra(EXTRA_CUSTOMER_LAST_NAME),
                customerPhone = intent.getStringExtra(EXTRA_CUSTOMER_PHONE),
                customerEmail = intent.getStringExtra(EXTRA_CUSTOMER_EMAIL),
                orderId = intent.getStringExtra(EXTRA_ORDER_ID),
                lastRenewal = readIntentLastRenewal()
            )
        }

    private fun readIntentLastRenewal(): im.angry.openeuicc.auth.MobileEsimLastRenewal? {
        val orderNo = intent.getStringExtra(EXTRA_LAST_RENEWAL_ORDER_NO)
        val message = intent.getStringExtra(EXTRA_LAST_RENEWAL_MESSAGE)
        val productName = intent.getStringExtra(EXTRA_LAST_RENEWAL_PRODUCT_NAME)
        if (orderNo.isNullOrBlank() && message.isNullOrBlank() && productName.isNullOrBlank()) return null

        return im.angry.openeuicc.auth.MobileEsimLastRenewal(
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

    private fun MobileEsim.matches(other: MobileEsim): Boolean =
        listOf(
            id to other.id,
            iccid to other.iccid,
            orderNumber to other.orderNumber,
            orderId to other.orderId
        ).any { (left, right) -> !left.isNullOrBlank() && left == right }

    companion object {
        private const val QR_SIZE = 720
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
        private const val EXTRA_CUSTOMER_FIRST_NAME = "mobile_esim.customer_first_name"
        private const val EXTRA_CUSTOMER_LAST_NAME = "mobile_esim.customer_last_name"
        private const val EXTRA_CUSTOMER_PHONE = "mobile_esim.customer_phone"
        private const val EXTRA_CUSTOMER_EMAIL = "mobile_esim.customer_email"
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

        fun createIntent(context: Context, esimId: String): Intent =
            Intent(context, MobileEsimDetailActivity::class.java).apply {
                putExtra(EXTRA_ID, esimId)
            }

        fun createIntent(context: Context, esim: MobileEsim): Intent =
            Intent(context, MobileEsimDetailActivity::class.java).apply {
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
                putExtra(EXTRA_CUSTOMER_FIRST_NAME, esim.customerFirstName)
                putExtra(EXTRA_CUSTOMER_LAST_NAME, esim.customerLastName)
                putExtra(EXTRA_CUSTOMER_PHONE, esim.customerPhone)
                putExtra(EXTRA_CUSTOMER_EMAIL, esim.customerEmail)
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
private fun MobileEsimDetailScreen(
    esim: MobileEsim?,
    loading: Boolean,
    error: String?,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onCopy: (String, String?, Int) -> Unit,
    onShowQr: (MobileEsim) -> Unit,
    onShareQr: (MobileEsim) -> Unit,
    onSendCustomer: (MobileEsim) -> Unit,
    onInstall: (MobileEsim) -> Unit,
    onRenew: (MobileEsim) -> Unit
) {
    val orange = Color(0xFFFF6A00)
    val bg = Color(0xFFF7F7FA)

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = bg) {
            Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 116.dp),
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
                            Text("Güncelleniyor...")
                        }
                    }
                }

                if (!error.isNullOrBlank()) {
                    ErrorCard(error = error, onRetry = onRetry)
                }

                if (esim == null) {
                    EmptyCard("eSIM bilgisi bulunamadı.")
                    return@Column
                }

                EsimHeroCard(esim = esim, orange = orange)

                EsimCustomerCard(esim = esim)

                EsimQrCard(
                    esim = esim,
                    onShowQr = { onShowQr(esim) },
                    onShareQr = { onShareQr(esim) },
                    onSendCustomer = { onSendCustomer(esim) }
                )

                EsimInstallCard(
                    esim = esim,
                    orange = orange,
                    onInstall = { onInstall(esim) },
                    onRenew = { onRenew(esim) }
                )

                EsimInfoCard(esim = esim)

                EsimClipboardCard(esim = esim, onCopy = onCopy)

                EsimRenewalCard(esim = esim)

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
private fun EsimHeroCard(esim: MobileEsim, orange: Color) {
    val displayStatus = realStatus(esim)
    val providerText = visibleProvider(esim.provider).orEmpty().ifBlank { "Orange" }
    val packageText = PackageNameCleaner.clean(esim.packageName).orEmpty().ifBlank { esim.title() }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(30.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF17181C))
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "eSIM Detayı",
                color = orange,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = packageText,
                color = Color.White,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = providerText,
                color = Color.White.copy(alpha = 0.74f),
                style = MaterialTheme.typography.bodyMedium
            )

            StatusPill(displayStatus.label)
        }
    }
}


@Composable
private fun EsimCustomerCard(esim: MobileEsim) {
    val fullName = listOfNotNull(
        esim.customerFirstName?.takeIf { it.isNotBlank() },
        esim.customerLastName?.takeIf { it.isNotBlank() }
    ).joinToString(" ").trim()

    val hasCustomerInfo = listOf(
        fullName,
        esim.customerPhone.orEmpty(),
        esim.customerEmail.orEmpty()
    ).any { it.isNotBlank() }

    if (!hasCustomerInfo) return

    InfoCard(title = "Müşteri Bilgileri") {
        DetailRow("Ad Soyad", fullName.ifBlank { "—" })
        DetailRow("Telefon", esim.customerPhone.orEmpty().ifBlank { "—" })
        DetailRow("Email", esim.customerEmail.orEmpty().ifBlank { "—" })
    }
}

@Composable
private fun EsimQrCard(
    esim: MobileEsim,
    onShowQr: () -> Unit,
    onShareQr: () -> Unit,
    onSendCustomer: () -> Unit
) {
    val payload = esim.qrPayload()
    val bitmap = payload?.let { createQrBitmap(it, 720) }

    InfoCard(title = "QR / Aktivasyon") {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "eSIM QR",
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(18.dp))
                    .padding(10.dp)
            )
        } else {
            Text(
                text = "QR bilgisi henüz hazır değil.",
                color = Color(0xFF686B73),
                style = MaterialTheme.typography.bodyMedium
            )
        }

        if (!payload.isNullOrBlank()) {
            OutlinedButton(onClick = onShowQr, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                Text("QR Ekranını Aç")
            }
            OutlinedButton(onClick = onShareQr, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                Text("QR Paylaş")
            }
            OutlinedButton(onClick = onSendCustomer, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                Text("Müşteriye Gönder")
            }
        }
    }
}

@Composable
private fun EsimInstallCard(
    esim: MobileEsim,
    orange: Color,
    onInstall: () -> Unit,
    onRenew: () -> Unit
) {
    val qrReady = !esim.qrPayload().isNullOrBlank()
    val manualReady = !esim.smdpAddress.isNullOrBlank() && !esim.matchingId.isNullOrBlank()
    val installReady = qrReady || manualReady || !esim.installCode().isNullOrBlank()

    val status = when {
        qrReady -> "QR Ready"
        manualReady -> "Manual Install Ready"
        installReady -> "Install Info Ready"
        else -> "Waiting for Install Info"
    }

    InfoCard(title = "Kurulum") {
        DetailRow("Durum", status)
        Text("1. Scan QR code or open QR view", color = Color(0xFF50535C))
        Text("2. Use SMDP + Matching ID for manual install", color = Color(0xFF50535C))
        Text("3. Tap Install eSIM / OpenEUICC", color = Color(0xFF50535C))
        Text("4. Enable mobile data and data roaming after install", color = Color(0xFF50535C))

        Button(
            onClick = onInstall,
            enabled = installReady,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = orange),
            shape = RoundedCornerShape(18.dp)
        ) {
            Text("Install eSIM / OpenEUICC")
        }

        if (canRenew(esim)) {
            OutlinedButton(
                onClick = onRenew,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp)
            ) {
                Text("Renew eSIM")
            }
        }
    }
}

@Composable
private fun EsimInfoCard(esim: MobileEsim) {
    InfoCard(title = "eSIM Bilgileri") {
        DetailRow("ICCID", esim.iccid.orEmpty())
        DetailRow("Provider", visibleProvider(esim.provider).orEmpty().ifBlank { "Orange" })
        DetailRow("Package", PackageNameCleaner.clean(esim.packageName).orEmpty().ifBlank { esim.title() })
        DetailRow("Status", realStatus(esim).label)
        DetailRow("Activation", esim.activationCode.orEmpty())
        DetailRow("SMDP", esim.smdpAddress.orEmpty())
        DetailRow("Matching ID", esim.matchingId.orEmpty())
        DetailRow("Expires", displayEsimExpiry(esim))
        DetailRow("Data Remaining", displayEsimData(esim))
        DetailRow("Validity", displayEsimValidity(esim))
        DetailRow("Data Used", cleanEsimValue(esim.dataUsed))
        DetailRow("Created", esim.createdAt?.let { formatProviderDate(it) }.orEmpty())
        DetailRow("Order", esim.orderNumber.orEmpty())
    }
}

@Composable
private fun EsimClipboardCard(
    esim: MobileEsim,
    onCopy: (String, String?, Int) -> Unit
) {
    InfoCard(title = "Kopyala") {
        if (!esim.iccid.isNullOrBlank()) {
            OutlinedButton(
                onClick = { onCopy("ICCID", esim.iccid, R.string.toast_iccid_copied) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp)
            ) {
                Text("ICCID Kopyala")
            }
        }

        if (!esim.activationCode.isNullOrBlank()) {
            OutlinedButton(
                onClick = { onCopy("Activation", esim.activationCode, R.string.mobile_esim_activation_copied) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp)
            ) {
                Text("Activation Kopyala")
            }
        }

        if (!esim.smdpAddress.isNullOrBlank()) {
            OutlinedButton(
                onClick = { onCopy("SMDP", esim.smdpAddress, R.string.mobile_esim_smdp_copied) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp)
            ) {
                Text("SMDP Kopyala")
            }
        }
    }
}

@Composable
private fun EsimRenewalCard(esim: MobileEsim) {
    val renewal = esim.lastRenewal ?: return
    val lines = listOfNotNull(
        renewal.message?.let { "Status    $it" },
        renewal.code?.let { "Code    $it" },
        renewal.orderNo?.let { "Order    $it" },
        renewal.productName?.let { "Package    $it" },
        renewal.productCode?.let { "Product code    $it" },
        renewal.createdTime?.let { "Created    ${formatProviderDate(it)}" },
        renewal.activatedEndTime?.let { "Activated end    ${formatProviderDate(it)}" },
        renewal.renewExpirationTime?.let { "Renew expiry    ${formatProviderDate(it)}" },
        renewal.latestActivationTime?.let { "Latest activation    ${formatProviderDate(it)}" },
        renewal.orderStatus?.let { "Order status    ${formatProviderStatus(it)}" },
        renewal.profileStatus?.let { "Profile status    ${formatProviderStatus(it)}" }
    )

    if (lines.isEmpty()) return

    InfoCard(title = "Last Renewal") {
        lines.forEach {
            Text(text = it, color = Color(0xFF50535C), style = MaterialTheme.typography.bodyMedium)
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
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            color = Color(0xFF686B73),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(0.42f)
        )
        Text(
            text = value.ifBlank { "-" },
            color = Color(0xFF17181C),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(0.58f)
        )
    }
}

@Composable
private fun StatusPill(label: String) {
    val normalized = label.lowercase()
    val colors = when {
        normalized.contains("active") || normalized.contains("ready") || normalized.contains("provision") ->
            Color(0xFFDCFCE7) to Color(0xFF166534)
        normalized.contains("expired") || normalized.contains("used") || normalized.contains("terminated") ->
            Color(0xFFFEE2E2) to Color(0xFFB91C1C)
        else -> Color(0xFFFEF9C3) to Color(0xFF854D0E)
    }

    Text(
        text = label,
        color = colors.second,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .background(colors.first, RoundedCornerShape(999.dp))
            .padding(horizontal = 12.dp, vertical = 7.dp)
    )
}

@Composable
private fun ErrorCard(error: String, onRetry: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEAEA))
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Detay yüklenemedi", color = Color(0xFFB91C1C), fontWeight = FontWeight.Bold)
            Text(error, color = Color(0xFF7F1D1D))
            OutlinedButton(onClick = onRetry) {
                Text("Tekrar Dene")
            }
        }
    }
}

@Composable
private fun EmptyCard(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(18.dp),
            color = Color(0xFF686B73)
        )
    }
}

private data class DisplayStatus(
    val label: String,
    val raw: String
)

private fun visibleProvider(provider: String?): String? =
    provider?.replace("TGT", "Orange", ignoreCase = true)
        ?.replace("tgt", "Orange", ignoreCase = true)

private fun realStatus(esim: MobileEsim): DisplayStatus {
    val raw = esim.status.orEmpty().trim()
    val normalized = raw.lowercase()
    val expiresAt = parseDate(esim.expiresAt)
    val isExpiredByDate = expiresAt?.isBefore(OffsetDateTime.now()) == true
    val hasIccid = !esim.iccid.isNullOrBlank()
    val hasInstallCode = !esim.installCode().isNullOrBlank() || !esim.qrPayload().isNullOrBlank()

    return when {
        normalized.contains("expired") || normalized.contains("depleted") || normalized.contains("terminated") || isExpiredByDate ->
            DisplayStatus("Expired", "expired")
        normalized.contains("active") || normalized.contains("activated") || normalized.contains("enabled") ->
            DisplayStatus("Active", "active")
        normalized.contains("pending") || normalized.contains("processing") || normalized.contains("waiting") || normalized.contains("ordered") ->
            DisplayStatus("Pending", "pending")
        hasIccid && hasInstallCode && expiresAt != null ->
            DisplayStatus("Ready", "ready")
        hasIccid && expiresAt != null ->
            DisplayStatus("Active", "active")
        hasIccid && hasInstallCode ->
            DisplayStatus("Ready", "ready")
        hasInstallCode ->
            DisplayStatus("Ready to Install", "ready")
        hasIccid ->
            DisplayStatus("Provisioned", raw.ifBlank { "provisioned" })
        else ->
            DisplayStatus("Pending", "pending")
    }
}

private fun parseDate(value: String?): OffsetDateTime? =
    value?.takeIf { it.isNotBlank() }?.let { runCatching { OffsetDateTime.parse(it) }.getOrNull() }

private fun cleanEsimValue(value: String?): String =
    value.orEmpty()
        .replace(Regex("""^(Data Remaining|Data Used|Expiry|Expires|Package|Status|Provider|ICCID):\s*""", RegexOption.IGNORE_CASE), "")
        .trim()

private fun extractDataFromPackage(packageName: String?): String {
    val match = Regex("""(?i)\b(\d+(?:[.,]\d+)?)\s*(GB|MB)\b""").find(packageName.orEmpty())
    return match?.let { "${it.groupValues[1].replace(",", ".")}${it.groupValues[2].uppercase(Locale.ENGLISH)}" }.orEmpty()
}

private fun extractValidityFromPackage(packageName: String?): String {
    val match = Regex("""(?i)\b(\d+)\s*(day|days|gün|gun)\b""").find(packageName.orEmpty())
    return match?.let { "${it.groupValues[1]} Days" }.orEmpty()
}

private fun displayEsimData(esim: MobileEsim): String =
    cleanEsimValue(esim.dataRemaining)
        .ifBlank { cleanEsimValue(esim.dataUsed) }
        .ifBlank { extractDataFromPackage(esim.packageName) }
        .ifBlank { "—" }

private fun displayEsimValidity(esim: MobileEsim): String =
    extractValidityFromPackage(esim.packageName).ifBlank { "—" }

private fun displayEsimExpiry(esim: MobileEsim): String =
    esim.expiresAt?.let { formatProviderDate(it) }.orEmpty()
        .ifBlank { esim.lastRenewal?.renewExpirationTime?.let { formatProviderDate(it) }.orEmpty() }
        .ifBlank { esim.lastRenewal?.activatedEndTime?.let { formatProviderDate(it) }.orEmpty() }
        .ifBlank { esim.lastRenewal?.latestActivationTime?.let { formatProviderDate(it) }.orEmpty() }
        .ifBlank { "—" }

private fun formatProviderDate(value: String): String {
    if (value.isBlank()) return ""
    return try {
        val formatter = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm", Locale.ENGLISH)
        Instant.parse(value).atZone(ZoneId.systemDefault()).format(formatter)
    } catch (_: Exception) {
        value
    }
}

private fun formatProviderStatus(value: String): String =
    when (value.uppercase(Locale.ROOT)) {
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
    val status = realStatus(esim).raw
    if (status == "expired") return false
    val provider = esim.provider.orEmpty().lowercase()
    return provider.contains("tgt") || provider.contains("airhub") || provider.contains("vodafone")
}

private fun createQrBitmap(content: String, size: Int): Bitmap? =
    runCatching {
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 1
        )
        val matrix = MultiFormatWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
        val pixels = IntArray(size * size)
        for (y in 0 until size) {
            for (x in 0 until size) {
                pixels[y * size + x] = if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
            }
        }
        Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, size, 0, 0, size, size)
        }
    }.getOrNull()
