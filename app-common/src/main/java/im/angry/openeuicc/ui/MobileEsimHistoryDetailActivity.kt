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
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SdCard
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowInsetsControllerCompat
import im.angry.openeuicc.auth.MobileEsim

private val DetailBlue = Color(0xFF0F4FD7)
private val DetailDark = Color(0xFF050B3D)
private val DetailText = Color(0xFF20242C)
private val DetailMuted = Color(0xFF68707C)
private val DetailBg = Color(0xFFF8FAFD)
private val DetailBorder = Color(0xFFE1E6EF)
private val DetailGreen = Color(0xFF12813A)
private val DetailGreenBg = Color(0xFFE9F7EF)

class MobileEsimHistoryDetailActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        actionBar?.hide()
        window.statusBarColor = AndroidColor.rgb(248, 250, 253)
        window.navigationBarColor = AndroidColor.BLACK
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true
        val esim = readIntentEsim()
        setContent {
            PurchaseHistoryMockupDetail(
                esim = esim,
                onBack = { finish() },
                onCopy = { label, value -> copyToClipboard(label, value) },
                onOpenEsimDetail = { startActivity(MobileEsimDetailActivity.createIntent(this, it)) },
                onOpenOpenEuicc = { startActivity(Intent(this, OpenEuiccIntegrationActivity::class.java)) },
                onRenew = { startActivity(Intent(this, VodafoneRenewalActivity::class.java)) }
            )
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
        customerFirstName = intent.getStringExtra(EXTRA_CUSTOMER_FIRST),
        customerLastName = intent.getStringExtra(EXTRA_CUSTOMER_LAST),
        customerPhone = intent.getStringExtra(EXTRA_CUSTOMER_PHONE),
        customerEmail = intent.getStringExtra(EXTRA_CUSTOMER_EMAIL)
    )

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
        private const val EXTRA_CUSTOMER_FIRST = "history_esim.customer_first"
        private const val EXTRA_CUSTOMER_LAST = "history_esim.customer_last"
        private const val EXTRA_CUSTOMER_PHONE = "history_esim.customer_phone"
        private const val EXTRA_CUSTOMER_EMAIL = "history_esim.customer_email"

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
            putExtra(EXTRA_CUSTOMER_FIRST, esim.customerFirstName)
            putExtra(EXTRA_CUSTOMER_LAST, esim.customerLastName)
            putExtra(EXTRA_CUSTOMER_PHONE, esim.customerPhone)
            putExtra(EXTRA_CUSTOMER_EMAIL, esim.customerEmail)
        }
    }
}

@Composable
private fun PurchaseHistoryMockupDetail(
    esim: MobileEsim,
    onBack: () -> Unit,
    onCopy: (String, String?) -> Unit,
    onOpenEsimDetail: (MobileEsim) -> Unit,
    onOpenOpenEuicc: () -> Unit,
    onRenew: () -> Unit
) {
    MaterialTheme {
        Surface(Modifier.fillMaxSize(), color = DetailBg) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 18.dp, top = 18.dp, end = 18.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ArrowBack, null, tint = DetailBlue, modifier = Modifier.size(32.dp).clickable(onClick = onBack))
                    Text("Purchase History", color = DetailDark, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(32.dp))
                }
                DetailCard("Customer Information", Icons.Default.AccountCircle) {
                    DetailIconRow(Icons.Default.Person, esim.displayCustomerName())
                    DetailDivider()
                    DetailIconRow(Icons.Default.Phone, esim.customerPhone ?: esim.orderNumber?.let { "Order #$it" } ?: "--")
                    DetailDivider()
                    DetailIconRow(Icons.Default.Email, esim.customerEmail ?: "--")
                }
                DetailCard("eSIM Details", Icons.Default.SimCard) {
                    DetailValueRow("ICCID", esim.iccid ?: "--", copyable = true) { onCopy("ICCID", esim.iccid) }
                    DetailDivider()
                    DetailValueRow("Provider", esim.displayProviderName())
                    DetailDivider()
                    DetailValueRow("Package", esim.displayPackageName())
                    DetailDivider()
                    DetailValueRow("Data", esim.displayDataLabel())
                    DetailDivider()
                    DetailValueRow("Validity", esim.displayValidityLabel())
                }
                DetailCard("Purchase Information", Icons.Default.CalendarMonth) {
                    DetailValueRow("Purchase Date", esim.createdAt.prettyDate())
                    DetailDivider()
                    DetailValueRow("Expiry Date", esim.expiresAt.prettyDate())
                    DetailDivider()
                    DetailValueRow("Remaining Data", esim.displayDataLabel(), valueColor = DetailBlue)
                    DetailDivider()
                    DetailValueRow("Renewable", "Yes", chip = true)
                    DetailDivider()
                    DetailValueRow("Installation Status", esim.statusLabel() ?: esim.historyStatus().label, chip = true)
                }
                PrimaryAction("View eSIM", Icons.Default.SdCard) { onOpenEsimDetail(esim) }
                SecondaryAction("Open OpenEUICC", Icons.Default.OpenInNew, true, onOpenOpenEuicc)
                SecondaryAction("Renew", Icons.Default.Refresh, false, onRenew)
            }
        }
    }
}

@Composable
private fun DetailCard(title: String, icon: ImageVector, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(Color.White), border = BorderStroke(1.dp, DetailBorder), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFFEFF5FF)), contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = DetailBlue, modifier = Modifier.size(25.dp))
                }
                Text(title, color = DetailDark, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(start = 12.dp))
            }
            content()
        }
    }
}

@Composable
private fun DetailIconRow(icon: ImageVector, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = DetailBlue, modifier = Modifier.size(24.dp))
        Text(value, color = DetailText, fontSize = 16.sp, modifier = Modifier.padding(start = 14.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun DetailValueRow(label: String, value: String, valueColor: Color = DetailText, copyable: Boolean = false, chip: Boolean = false, onCopy: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = DetailMuted, fontSize = 15.sp, modifier = Modifier.weight(0.9f))
        if (chip) {
            Box(Modifier.clip(RoundedCornerShape(8.dp)).background(DetailGreenBg).padding(horizontal = 8.dp, vertical = 3.dp)) {
                Text(value, color = DetailGreen, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
        } else {
            Text(value, color = valueColor, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1.25f), textAlign = TextAlign.End)
        }
        if (copyable) Icon(Icons.Default.ContentCopy, null, tint = DetailBlue, modifier = Modifier.padding(start = 6.dp).size(20.dp).clickable { onCopy?.invoke() })
    }
}

@Composable private fun DetailDivider() { HorizontalDivider(color = DetailBorder, thickness = 1.dp) }

@Composable
private fun PrimaryAction(title: String, icon: ImageVector, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth().height(58.dp), shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = DetailBlue)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(25.dp))
            Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(start = 14.dp).weight(1f))
            Text("›", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SecondaryAction(title: String, icon: ImageVector, outlined: Boolean, onClick: () -> Unit) {
    val colors = if (outlined) ButtonDefaults.outlinedButtonColors(containerColor = Color.White) else ButtonDefaults.buttonColors(containerColor = Color(0xFFEFF5FF))
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, DetailBlue), colors = colors) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = DetailBlue, modifier = Modifier.size(25.dp))
            Text(title, color = DetailBlue, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(start = 14.dp).weight(1f))
            Text("›", color = DetailBlue, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        }
    }
}
