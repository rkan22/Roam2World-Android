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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowInsetsControllerCompat
import im.angry.openeuicc.auth.MobileEsim
import im.angry.openeuicc.auth.MobileEsimLastRenewal

private val DetailOrange = Color(0xFFFF7900)
private val DetailText = Color(0xFF17181C)
private val DetailMuted = Color(0xFF6B7280)
private val DetailBg = Color(0xFFF7F9FC)
private val DetailBorder = Color(0xFFE5E7EB)
private val DetailGreen = Color(0xFF16A34A)
private val DetailRed = Color(0xFFDC2626)
private val DetailYellow = Color(0xFFF59E0B)

class MobileEsimHistoryDetailActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        actionBar?.hide()
        configureSystemBars()
        val esim = readIntentEsim()
        setContent {
            MobileEsimHistoryDetailScreen(
                esim = esim,
                onBack = { finish() },
                onCopy = { label, value -> copyToClipboard(label, value) },
                onOpenEsimDetail = { startActivity(MobileEsimDetailActivity.createIntent(this, it)) }
            )
        }
    }

    private fun configureSystemBars() {
        window.statusBarColor = AndroidColor.rgb(247, 249, 252)
        window.navigationBarColor = AndroidColor.BLACK
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = false
        }
    }

    private fun copyToClipboard(label: String, value: String?) {
        if (value.isNullOrBlank()) return
        getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(label, value))
        Toast.makeText(this, "$label copied", Toast.LENGTH_SHORT).show()
    }

    private fun readIntentEsim(): MobileEsim = MobileEsim(
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

    companion object {
        private const val EXTRA_ID = "history_esim.id"
        private const val EXTRA_ICCID = "history_esim.iccid"
        private const val EXTRA_PROVIDER = "history_esim.provider"
        private const val EXTRA_PACKAGE_NAME = "history_esim.package_name"
        private const val EXTRA_STATUS = "history_esim.status"
        private const val EXTRA_ACTIVATION_CODE = "history_esim.activation_code"
        private const val EXTRA_LPA_CODE = "history_esim.lpa_code"
        private const val EXTRA_SMDP = "history_esim.smdp"
        private const val EXTRA_MATCHING_ID = "history_esim.matching_id"
        private const val EXTRA_CONFIRMATION_REQUIRED = "history_esim.confirmation_required"
        private const val EXTRA_QR_CODE = "history_esim.qr_code"
        private const val EXTRA_QR_URL = "history_esim.qr_url"
        private const val EXTRA_CREATED_AT = "history_esim.created_at"
        private const val EXTRA_ORDER_NUMBER = "history_esim.order_number"
        private const val EXTRA_EXPIRES_AT = "history_esim.expires_at"
        private const val EXTRA_DATA_REMAINING = "history_esim.data_remaining"
        private const val EXTRA_DATA_USED = "history_esim.data_used"
        private const val EXTRA_ORDER_ID = "history_esim.order_id"
        private const val EXTRA_LAST_RENEWAL_PROFILE_STATUS = "history_esim.last_renewal.profile_status"
        private const val EXTRA_LAST_RENEWAL_ORDER_STATUS = "history_esim.last_renewal.order_status"
        private const val EXTRA_LAST_RENEWAL_LATEST_ACTIVATION_TIME = "history_esim.last_renewal.latest_activation_time"
        private const val EXTRA_LAST_RENEWAL_RENEW_EXPIRATION_TIME = "history_esim.last_renewal.renew_expiration_time"
        private const val EXTRA_LAST_RENEWAL_ACTIVATED_END_TIME = "history_esim.last_renewal.activated_end_time"
        private const val EXTRA_LAST_RENEWAL_CREATED_TIME = "history_esim.last_renewal.created_time"
        private const val EXTRA_LAST_RENEWAL_PRODUCT_CODE = "history_esim.last_renewal.product_code"
        private const val EXTRA_LAST_RENEWAL_PRODUCT_NAME = "history_esim.last_renewal.product_name"
        private const val EXTRA_LAST_RENEWAL_ORDER_NO = "history_esim.last_renewal.order_no"
        private const val EXTRA_LAST_RENEWAL_CODE = "history_esim.last_renewal.code"
        private const val EXTRA_LAST_RENEWAL_MESSAGE = "history_esim.last_renewal.message"
        private const val EXTRA_LAST_RENEWAL_SUCCESS = "history_esim.last_renewal.success"
        private const val EXTRA_LAST_RENEWAL_PROVIDER = "history_esim.last_renewal.provider"

        fun createIntent(context: Context, esim: MobileEsim): Intent = Intent(context, MobileEsimHistoryDetailActivity::class.java).apply {
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
private fun MobileEsimHistoryDetailScreen(
    esim: MobileEsim,
    onBack: () -> Unit,
    onCopy: (String, String?) -> Unit,
    onOpenEsimDetail: (MobileEsim) -> Unit
) {
    val status = esim.historyStatus()
    MaterialTheme {
        Surface(Modifier.fillMaxSize(), color = DetailBg) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ArrowBack, null, tint = DetailText, modifier = Modifier.size(30.dp).clickable(onClick = onBack))
                    Text("History Detail", color = DetailText, fontSize = 27.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(start = 16.dp).weight(1f))
                }

                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(30.dp), colors = CardDefaults.cardColors(Color(0xFF17181C))) {
                    Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(58.dp).clip(CircleShape).background(DetailOrange), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.ReceiptLong, null, tint = Color.White, modifier = Modifier.size(30.dp))
                            }
                            Column(Modifier.padding(start = 14.dp).weight(1f)) {
                                Text(esim.orderNumber?.let { "Order #$it" } ?: "eSIM record", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(esim.createdAt.prettyDate(), color = Color.White.copy(alpha = .72f), fontSize = 13.sp)
                            }
                        }
                        StatusBadge(status.label, status.historyColor())
                    }
                }

                DetailSection("Profile") {
                    DetailRow("Package", esim.packageName ?: "Roam2World eSIM")
                    DetailRow("Provider", esim.provider ?: "--")
                    DetailRow("ICCID", esim.iccid ?: "--", copyable = true) { onCopy("ICCID", esim.iccid) }
                    DetailRow("Status", esim.statusLabel() ?: status.label)
                }

                DetailSection("Lifecycle") {
                    DetailRow("Purchased", esim.createdAt.prettyDate())
                    DetailRow("Expires", esim.expiresAt.prettyDate())
                    DetailRow("Data remaining", esim.dataRemaining ?: "--")
                    DetailRow("Data used", esim.dataUsed ?: "--")
                }

                DetailSection("References") {
                    DetailRow("Order number", esim.orderNumber?.let { "#$it" } ?: "--", copyable = true) { onCopy("Order", esim.orderNumber) }
                    DetailRow("Order ID", esim.orderId ?: "--")
                    DetailRow("Install code", esim.installCode()?.take(36)?.plus(if ((esim.installCode()?.length ?: 0) > 36) "..." else "") ?: "--", copyable = true) { onCopy("Install code", esim.installCode()) }
                }

                DetailTimeline(esim)

                Button(
                    onClick = { onOpenEsimDetail(esim) },
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DetailOrange)
                ) {
                    Icon(Icons.Default.OpenInNew, null, tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text("Open eSIM Detail", fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}

@Composable
private fun DetailSection(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(Color.White), border = BorderStroke(1.dp, DetailBorder), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, color = DetailText, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            HorizontalDivider(color = DetailBorder)
            content()
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String, copyable: Boolean = false, onCopy: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, color = DetailMuted, fontSize = 12.sp)
            Text(value, color = DetailText, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (copyable) {
            Icon(Icons.Default.ContentCopy, null, tint = DetailOrange, modifier = Modifier.size(24.dp).clickable { onCopy?.invoke() })
        }
    }
}

@Composable
private fun DetailTimeline(esim: MobileEsim) {
    DetailSection("Status timeline") {
        TimelineRow("Purchased", esim.createdAt.prettyDate(), true)
        TimelineRow("Profile status", esim.statusLabel() ?: esim.historyStatus().label, true)
        TimelineRow("Renewal", esim.lastRenewal?.message ?: esim.lastRenewal?.productName ?: "No renewal record", esim.lastRenewal != null)
        TimelineRow("Expiry", esim.expiresAt.prettyDate(), !esim.expiresAt.isNullOrBlank())
    }
}

@Composable
private fun TimelineRow(title: String, subtitle: String, complete: Boolean) {
    Row(verticalAlignment = Alignment.Top) {
        Box(Modifier.size(22.dp).clip(CircleShape).background(if (complete) DetailGreen else DetailBorder), contentAlignment = Alignment.Center) {
            if (complete) Box(Modifier.size(8.dp).clip(CircleShape).background(Color.White))
        }
        Column(Modifier.padding(start = 12.dp)) {
            Text(title, color = DetailText, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = DetailMuted, fontSize = 13.sp)
        }
    }
}

@Composable
private fun StatusBadge(label: String, color: Color) {
    Box(Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = .18f)).padding(horizontal = 12.dp, vertical = 7.dp)) {
        Text(label, color = color, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
    }
}
