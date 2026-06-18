package im.angry.openeuicc.ui

import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.core.net.toUri
import androidx.core.view.WindowInsetsControllerCompat
import im.angry.openeuicc.auth.MobilePackagePurchaseResult
import im.angry.openeuicc.ui.wizard.DownloadWizardActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val SuccessBlue = Color(0xFF0B84FF)
private val SuccessGreen = Color(0xFF2FB344)
private val SuccessGreenLight = Color(0xFFEAF8EE)
private val SuccessText = Color(0xFF111827)
private val SuccessMuted = Color(0xFF6B7280)
private val SuccessBorder = Color(0xFFE5E7EB)
private val SuccessBg = Color(0xFFFAFCFF)

class PurchaseConfirmationActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        actionBar?.hide()
        configureSystemBars()

        val packageName = cleanPackageName(intent.getStringExtra(EXTRA_PACKAGE_NAME).orEmpty())
        val orderNumber = intent.getStringExtra(EXTRA_ORDER_NUMBER).orEmpty()
        val price = r2wMoney(intent.getStringExtra(EXTRA_PRICE)).orEmpty()
        val balanceAfter = r2wMoney(intent.getStringExtra(EXTRA_BALANCE_AFTER)).orEmpty()
        val lpaCode = intent.getStringExtra(EXTRA_LPA_CODE).orEmpty()
        val smdp = intent.getStringExtra(EXTRA_SMDP).orEmpty()
        val matchingId = intent.getStringExtra(EXTRA_MATCHING_ID).orEmpty()
        val qrCode = intent.getStringExtra(EXTRA_QR_CODE).orEmpty()
        val qrUrl = intent.getStringExtra(EXTRA_QR_URL).orEmpty()
        val iccid = intent.getStringExtra(EXTRA_ICCID).orEmpty()
        val esimId = intent.getStringExtra(EXTRA_ESIM_ID).orEmpty()
        val installCode = intent.getStringExtra(EXTRA_INSTALL_CODE).orEmpty()

        setContent {
            PurchaseConfirmationScreen(
                packageName = packageName,
                orderNumber = orderNumber,
                price = price,
                balanceAfter = balanceAfter,
                lpaCode = lpaCode,
                smdp = smdp,
                matchingId = matchingId,
                qrCode = qrCode,
                qrUrl = qrUrl,
                iccid = iccid,
                esimId = esimId,
                canInstall = installCode.isNotBlank(),
                onInstall = { launchInstallFlow(installCode) },
                onOpenProfiles = { startActivity(Intent(this, OpenEuiccIntegrationActivity::class.java)) },
                onOpenEsims = { startActivity(Intent(this, MobileEsimsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)) },
                onHome = { openDashboard() }
            )
        }
    }

    private fun configureSystemBars() {
        window.statusBarColor = AndroidColor.rgb(250, 252, 255)
        window.navigationBarColor = AndroidColor.BLACK
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = false
        }
    }

    private fun launchInstallFlow(installCode: String) {
        if (installCode.isBlank()) return
        val lpaUri = if (installCode.startsWith("LPA:", ignoreCase = true)) installCode else "LPA:$installCode"
        startActivity(DownloadWizardActivity.newIntent(this).apply { action = Intent.ACTION_VIEW; data = lpaUri.toUri() })
    }

    private fun openDashboard() {
        startActivity(Intent(this, R2wComposeHomeActivity::class.java).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK) })
        finish()
    }

    companion object {
        private const val EXTRA_ORDER_ID = "purchase.order_id"
        private const val EXTRA_ORDER_NUMBER = "purchase.order_number"
        private const val EXTRA_STATUS = "purchase.status"
        private const val EXTRA_PACKAGE_NAME = "purchase.package_name"
        private const val EXTRA_PRICE = "purchase.price"
        private const val EXTRA_BALANCE_AFTER = "purchase.balance_after"
        private const val EXTRA_LPA_CODE = "purchase.lpa_code"
        private const val EXTRA_SMDP = "purchase.smdp"
        private const val EXTRA_MATCHING_ID = "purchase.matching_id"
        private const val EXTRA_QR_CODE = "purchase.qr_code"
        private const val EXTRA_QR_URL = "purchase.qr_url"
        private const val EXTRA_ICCID = "purchase.iccid"
        private const val EXTRA_ESIM_ID = "purchase.esim_id"
        private const val EXTRA_INSTALL_CODE = "purchase.install_code"

        fun createIntent(context: Context, result: MobilePackagePurchaseResult): Intent =
            Intent(context, PurchaseConfirmationActivity::class.java).apply {
                putExtra(EXTRA_ORDER_ID, result.orderId)
                putExtra(EXTRA_ORDER_NUMBER, result.orderNumber)
                putExtra(EXTRA_STATUS, result.status)
                putExtra(EXTRA_PACKAGE_NAME, result.packageName)
                putExtra(EXTRA_PRICE, result.price)
                putExtra(EXTRA_BALANCE_AFTER, result.balanceAfter)
                putExtra(EXTRA_LPA_CODE, result.activation.lpaCode)
                putExtra(EXTRA_SMDP, result.activation.smdpAddress)
                putExtra(EXTRA_MATCHING_ID, result.activation.matchingId)
                putExtra(EXTRA_QR_CODE, result.activation.qrCode)
                putExtra(EXTRA_QR_URL, result.activation.qrCodeUrl)
                putExtra(EXTRA_ICCID, result.activation.iccid)
                putExtra(EXTRA_ESIM_ID, result.activation.esimId)
                putExtra(EXTRA_INSTALL_CODE, result.activation.installCode())
            }
    }
}

@Composable
private fun PurchaseConfirmationScreen(
    packageName: String,
    orderNumber: String,
    price: String,
    balanceAfter: String,
    lpaCode: String,
    smdp: String,
    matchingId: String,
    qrCode: String,
    qrUrl: String,
    iccid: String,
    esimId: String,
    canInstall: Boolean,
    onInstall: () -> Unit,
    onOpenProfiles: () -> Unit,
    onOpenEsims: () -> Unit,
    onHome: () -> Unit
) {
    Surface(Modifier.fillMaxSize(), color = SuccessBg) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SuccessHero()
            EsimDetailsCard(packageName, iccid, esimId, price, balanceAfter)
            ActivationDetailsCard(lpaCode, smdp, matchingId, qrCode, qrUrl, orderNumber)

            Button(
                onClick = onInstall,
                enabled = canInstall,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SuccessBlue, disabledContainerColor = SuccessBlue.copy(alpha = .40f)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.Download, null, tint = Color.White, modifier = Modifier.size(24.dp))
                Text(if (canInstall) "Install eSIM Now" else "Installation Code Pending", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(start = 12.dp))
                Text("›", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 12.dp))
            }

            ActionButton("Open OpenEUICC", Icons.Default.OpenInNew, onOpenProfiles)
            ActionButton("View eSIM Detail", Icons.Default.SimCard, onOpenEsims)
            ActionButton("Back to Dashboard", Icons.Default.Home, onHome)
        }
    }
}

@Composable
private fun SuccessHero() {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().padding(top = 26.dp, bottom = 12.dp)) {
        Box(Modifier.size(120.dp).clip(RoundedCornerShape(999.dp)).background(SuccessGreenLight), contentAlignment = Alignment.Center) {
            Box(Modifier.size(92.dp).clip(RoundedCornerShape(999.dp)).background(SuccessGreen), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.CheckCircle, null, tint = Color.White, modifier = Modifier.size(68.dp))
            }
        }
        Text("Purchase Successful", color = SuccessText, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
        Text("Your eSIM has been purchased successfully\nand is ready to install.", color = SuccessMuted, fontSize = 18.sp, lineHeight = 25.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun EsimDetailsCard(packageName: String, iccid: String, esimId: String, price: String, balanceAfter: String) {
    SuccessCard("eSIM Details", Icons.Default.SimCard) {
        DetailRow(Icons.Default.SimCard, "ICCID", iccid.ifBlank { esimId.ifBlank { "Pending" } }, copyIcon = iccid.isNotBlank() || esimId.isNotBlank())
        DetailRow(Icons.Default.ReceiptLong, "Provider", providerFromPackage(packageName))
        DetailRow(Icons.Default.SimCard, "Package", packageName.ifBlank { "Roam2World eSIM Package" })
        DetailRow(Icons.Default.Today, "Purchase Date", SimpleDateFormat("MMM d yyyy", Locale.US).format(Date()))
        DetailRow(Icons.Default.CheckCircle, "Activation", if (iccid.isNotBlank() || esimId.isNotBlank()) "Available" else "Pending", valueColor = if (iccid.isNotBlank() || esimId.isNotBlank()) SuccessGreen else SuccessMuted)
        if (price.isNotBlank()) DetailRow(Icons.Default.ReceiptLong, "Price", price)
        if (balanceAfter.isNotBlank() && balanceAfter != "-") DetailRow(Icons.Default.CreditCard, "Balance After", balanceAfter)
    }
}

@Composable
private fun ActivationDetailsCard(lpaCode: String, smdp: String, matchingId: String, qrCode: String, qrUrl: String, orderNumber: String) {
    val empty = lpaCode.isBlank() && smdp.isBlank() && matchingId.isBlank() && qrCode.isBlank() && qrUrl.isBlank() && orderNumber.isBlank()
    if (empty) return
    SuccessCard("Installation Details", Icons.Default.QrCode2) {
        if (orderNumber.isNotBlank()) ActivationInfoBlock("Order Number", orderNumber)
        if (matchingId.isNotBlank()) ActivationInfoBlock("Matching ID", matchingId)
        if (lpaCode.isNotBlank()) ActivationInfoBlock("Activation Code", lpaCode)
        if (smdp.isNotBlank()) ActivationInfoBlock("SM-DP+", smdp)
        if (qrCode.isNotBlank()) ActivationInfoBlock("QR", qrCode)
        if (qrUrl.isNotBlank()) ActivationInfoBlock("QR URL", qrUrl)
    }
}

@Composable
private fun SuccessCard(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(Color.White), border = BorderStroke(1.dp, SuccessBorder), elevation = CardDefaults.cardElevation(4.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(46.dp).clip(RoundedCornerShape(999.dp)).background(SuccessGreen.copy(alpha = .14f)), contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = SuccessGreen, modifier = Modifier.size(25.dp))
                }
                Text(title, color = SuccessGreen, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(start = 12.dp))
            }
            HorizontalDivider(color = SuccessBorder)
            content()
        }
    }
}

@Composable
private fun DetailRow(icon: ImageVector, label: String, value: String, valueColor: Color = SuccessText, copyIcon: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = SuccessGreen, modifier = Modifier.size(25.dp))
        Text(label, color = SuccessText, fontSize = 16.sp, modifier = Modifier.padding(start = 14.dp).weight(1f))
        Text(value, color = valueColor, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End)
        if (copyIcon) Icon(Icons.Default.ContentCopy, null, tint = SuccessGreen, modifier = Modifier.padding(start = 8.dp).size(21.dp))
    }
}

@Composable
private fun ActivationInfoBlock(label: String, value: String) {
    Surface(color = Color(0xFFF8FAFF), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, SuccessBorder)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(label, color = SuccessMuted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(value, color = SuccessText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 4, overflow = TextOverflow.Ellipsis, lineHeight = 19.sp)
        }
    }
}

@Composable
private fun ActionButton(text: String, icon: ImageVector, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, SuccessBlue)) {
        Icon(icon, null, tint = SuccessBlue, modifier = Modifier.size(22.dp))
        Text(text, color = SuccessBlue, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f).padding(start = 12.dp), textAlign = TextAlign.Center)
        Text("›", color = SuccessBlue, fontSize = 26.sp, fontWeight = FontWeight.Bold)
    }
}

private fun providerFromPackage(packageName: String): String = if (packageName.contains("vodafone", true)) "Vodafone" else "Orange"

private fun cleanPackageName(value: String): String {
    val cleaned = PackageNameCleaner.clean(value)
    val data = Regex("""(\d+(?:\.\d+)?)\s*GB""", RegexOption.IGNORE_CASE).find(cleaned)?.value?.uppercase()?.replace("GB", " GB")?.replace(Regex("\\s+"), " ")?.trim().orEmpty()
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

private fun r2wMoney(value: String?): String? {
    val clean = value?.trim()?.takeIf { it.isNotBlank() } ?: return null
    return when {
        clean.startsWith("$") -> clean
        clean.startsWith("USD", ignoreCase = true) -> clean.replace("USD", "$", ignoreCase = true).replace(Regex("\\s+"), "")
        clean.any { it.isDigit() } -> "$${clean.replace(Regex("[^0-9.,-]"), "").replace(",", ".")}" 
        else -> clean
    }
}
