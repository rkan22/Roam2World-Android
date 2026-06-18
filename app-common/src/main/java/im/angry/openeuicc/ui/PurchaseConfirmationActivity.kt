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
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import im.angry.openeuicc.auth.MobilePackagePurchaseResult
import im.angry.openeuicc.ui.wizard.DownloadWizardActivity

private val SuccessBlue = Color(0xFF1263F1)
private val SuccessBlueDark = Color(0xFF0649B8)
private val SuccessText = Color(0xFF111827)
private val SuccessMuted = Color(0xFF6B7280)
private val SuccessBorder = Color(0xFFE5E7EB)
private val SuccessBg = Color(0xFFF8FAFF)
private val SuccessGreen = Color(0xFF16A34A)

class PurchaseConfirmationActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        actionBar?.hide()
        configureSystemBars()

        val packageName = cleanPackageName(intent.getStringExtra(EXTRA_PACKAGE_NAME).orEmpty())
        val orderNumber = intent.getStringExtra(EXTRA_ORDER_NUMBER).orEmpty()
        val status = cleanVisibleValue(intent.getStringExtra(EXTRA_STATUS).orEmpty())
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
                status = status,
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
        window.statusBarColor = AndroidColor.rgb(248, 250, 255)
        window.navigationBarColor = AndroidColor.BLACK
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = false
        }
    }

    private fun launchInstallFlow(installCode: String) {
        if (installCode.isBlank()) return
        val lpaUri = if (installCode.startsWith("LPA:", ignoreCase = true)) installCode else "LPA:$installCode"
        startActivity(
            DownloadWizardActivity.newIntent(this).apply {
                action = Intent.ACTION_VIEW
                data = lpaUri.toUri()
            }
        )
    }

    private fun openDashboard() {
        startActivity(
            Intent(this, R2wComposeHomeActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
        )
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
    status: String,
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
            SuccessHero(packageName, orderNumber)

            SuccessCard("Order Details", Icons.Default.ReceiptLong) {
                InfoRow(Icons.Default.CheckCircle, "Status", status.ifBlank { "Successful" })
                InfoRow(Icons.Default.Sell, "Price", price.ifBlank { "-" })
                InfoRow(Icons.Default.Wallet, "Balance After", balanceAfter.ifBlank { "-" })
                if (iccid.isNotBlank()) InfoRow(Icons.Default.SimCard, "ICCID", iccid, valueMaxLines = 2)
                if (esimId.isNotBlank()) InfoRow(Icons.Default.SimCard, "eSIM ID", esimId, valueMaxLines = 2)
            }

            ActivationCard(lpaCode, smdp, matchingId, qrCode, qrUrl)

            Button(
                onClick = onInstall,
                enabled = canInstall,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SuccessBlue, disabledContainerColor = SuccessBlue.copy(alpha = .40f)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.Download, null, tint = Color.White, modifier = Modifier.size(22.dp))
                Text(if (canInstall) "Install eSIM" else "Installation Code Pending", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(start = 10.dp))
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onOpenProfiles, modifier = Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(14.dp), colors = OutlinedButtonDefaults.outlinedButtonColors(contentColor = SuccessBlue), border = BorderStroke(1.dp, SuccessBlue.copy(alpha = .35f))) {
                    Text("Profiles", fontWeight = FontWeight.Bold)
                }
                OutlinedButton(onClick = onOpenEsims, modifier = Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(14.dp), colors = OutlinedButtonDefaults.outlinedButtonColors(contentColor = SuccessBlue), border = BorderStroke(1.dp, SuccessBlue.copy(alpha = .35f))) {
                    Text("My eSIMs", fontWeight = FontWeight.Bold)
                }
            }

            OutlinedButton(onClick = onHome, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(14.dp), colors = OutlinedButtonDefaults.outlinedButtonColors(contentColor = SuccessText), border = BorderStroke(1.dp, SuccessBorder)) {
                Icon(Icons.Default.Home, null, tint = SuccessText, modifier = Modifier.size(21.dp))
                Text("Back to Dashboard", fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun SuccessHero(packageName: String, orderNumber: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(3.dp)
    ) {
        Box(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(SuccessBlue, SuccessBlueDark))).padding(24.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(78.dp).clip(RoundedCornerShape(999.dp)).background(Color.White.copy(alpha = .18f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.CheckCircle, null, tint = Color.White, modifier = Modifier.size(56.dp))
                }
                Text("Purchase Successful", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
                Text(packageName.ifBlank { "Roam2World eSIM Package" }, color = Color.White.copy(alpha = .92f), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (orderNumber.isNotBlank()) {
                    Surface(color = Color.White.copy(alpha = .16f), shape = RoundedCornerShape(999.dp)) {
                        Text("Order #$orderNumber", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SuccessCard(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(Color.White), border = BorderStroke(1.dp, SuccessBorder), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(46.dp).clip(RoundedCornerShape(999.dp)).background(SuccessBlue.copy(alpha = .10f)), contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = SuccessBlue, modifier = Modifier.size(25.dp))
                }
                Text(title, color = SuccessText, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(start = 12.dp))
            }
            HorizontalDivider(color = SuccessBorder)
            content()
        }
    }
}

@Composable
private fun ActivationCard(lpaCode: String, smdp: String, matchingId: String, qrCode: String, qrUrl: String) {
    val empty = lpaCode.isBlank() && smdp.isBlank() && matchingId.isBlank() && qrCode.isBlank() && qrUrl.isBlank()
    SuccessCard("Activation Details", Icons.Default.QrCode2) {
        if (empty) {
            Text("Activation details are being prepared. You can check My eSIMs again in a moment.", color = SuccessMuted, fontSize = 14.sp, lineHeight = 20.sp)
        } else {
            if (lpaCode.isNotBlank()) InfoRow(Icons.Default.QrCode2, "Activation Code", lpaCode, valueMaxLines = 3)
            if (smdp.isNotBlank()) InfoRow(Icons.Default.QrCode2, "SM-DP+", smdp, valueMaxLines = 2)
            if (matchingId.isNotBlank()) InfoRow(Icons.Default.QrCode2, "Matching ID", matchingId, valueMaxLines = 2)
            if (qrCode.isNotBlank()) InfoRow(Icons.Default.QrCode2, "QR", qrCode, valueMaxLines = 3)
            if (qrUrl.isNotBlank()) InfoRow(Icons.Default.QrCode2, "QR URL", qrUrl, valueMaxLines = 3)
        }
    }
}

@Composable
private fun InfoRow(icon: ImageVector, label: String, value: String, valueMaxLines: Int = 1) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = SuccessMuted, modifier = Modifier.size(21.dp))
        Text(label, color = SuccessMuted, fontSize = 14.sp, modifier = Modifier.padding(start = 12.dp).weight(1f))
        Text(value, color = SuccessText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = valueMaxLines, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End)
    }
}

private fun cleanVisibleValue(value: String): String =
    value.replace("TGT", "Orange", ignoreCase = true).replace("tgt", "Orange", ignoreCase = true).replace("demo_success", "Successful", ignoreCase = true)

private fun cleanPackageName(value: String): String = PackageNameCleaner.clean(value)

private fun r2wMoney(value: String?): String? {
    val clean = value?.trim()?.takeIf { it.isNotBlank() } ?: return null
    return when {
        clean.startsWith("$") -> clean
        clean.startsWith("USD", ignoreCase = true) -> clean.replace("USD", "$", ignoreCase = true).replace(Regex("\\s+"), "")
        clean.any { it.isDigit() } -> "$${clean.replace(Regex("[^0-9.,-]"), "").replace(",", ".")}" 
        else -> clean
    }
}
