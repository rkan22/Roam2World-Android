package im.angry.openeuicc.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.net.toUri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import im.angry.openeuicc.auth.MobilePackagePurchaseResult
import im.angry.openeuicc.ui.wizard.DownloadWizardActivity

class PurchaseConfirmationActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
                onOpenProfiles = {
                    startActivity(Intent(this, OpenEuiccIntegrationActivity::class.java))
                },
                onOpenEsims = {
                    startActivity(Intent(this, MobileEsimsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
                },
                onHome = { openDashboard() }
            )
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
    val orange = Color(0xFFFF6A00)
    val bg = Color(0xFFF7F7FA)
    val scroll = rememberScrollState()

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = bg) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scroll)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                SuccessHeader(packageName = packageName, orderNumber = orderNumber)

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        InfoRow("Durum", status.ifBlank { "Başarılı" })
                        InfoRow("Fiyat", price.ifBlank { "-" })
                        InfoRow("Kalan Bakiye", balanceAfter.ifBlank { "-" })
                        if (iccid.isNotBlank()) InfoRow("ICCID", iccid)
                        if (esimId.isNotBlank()) InfoRow("eSIM ID", esimId)
                    }
                }

                ActivationCard(
                    lpaCode = lpaCode,
                    smdp = smdp,
                    matchingId = matchingId,
                    qrCode = qrCode,
                    qrUrl = qrUrl
                )

                Button(
                    onClick = onInstall,
                    enabled = canInstall,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = orange),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text(if (canInstall) "OpenEUICC ile Kur" else "Kurulum kodu bekleniyor")
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onOpenProfiles,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Text("Profiller")
                    }

                    OutlinedButton(
                        onClick = onOpenEsims,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Text("eSIM’lerim")
                    }
                }

                Spacer(modifier = Modifier.padding(top = 4.dp))

                OutlinedButton(
                    onClick = onHome,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text("Ana Sayfaya Dön")
                }
            }
        }
    }
}

@Composable
private fun SuccessHeader(
    packageName: String,
    orderNumber: String
) {
    val orange = Color(0xFFFF6A00)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(30.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF17181C)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Sipariş Başarılı",
                color = orange,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = packageName.ifBlank { "Roam2World eSIM Paketi" },
                color = Color.White,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            if (orderNumber.isNotBlank()) {
                Text(
                    text = "Sipariş No: $orderNumber",
                    color = Color.White.copy(alpha = 0.74f),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun ActivationCard(
    lpaCode: String,
    smdp: String,
    matchingId: String,
    qrCode: String,
    qrUrl: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Aktivasyon Bilgileri",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF17181C)
            )

            val empty = lpaCode.isBlank() && smdp.isBlank() && matchingId.isBlank() && qrCode.isBlank() && qrUrl.isBlank()
            if (empty) {
                Text(
                    text = "Backend aktivasyon bilgisini hazırlıyor olabilir. eSIM’lerim ekranından tekrar kontrol edebilirsin.",
                    color = Color(0xFF686B73),
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                if (lpaCode.isNotBlank()) InfoRow("Activation Code", lpaCode)
                if (smdp.isNotBlank()) InfoRow("SM-DP+", smdp)
                if (matchingId.isNotBlank()) InfoRow("Matching ID", matchingId)
                if (qrCode.isNotBlank()) InfoRow("QR", qrCode)
                if (qrUrl.isNotBlank()) InfoRow("QR URL", qrUrl)
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(
            text = label,
            color = Color(0xFF686B73),
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            text = value,
            color = Color(0xFF17181C),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

private fun cleanVisibleValue(value: String): String =
    value.replace("TGT", "Orange", ignoreCase = true)
        .replace("tgt", "Orange", ignoreCase = true)

private fun cleanPackageName(value: String): String =
    PackageNameCleaner.clean(value)

private fun r2wMoney(value: String?): String? {
    val clean = value?.trim()?.takeIf { it.isNotBlank() } ?: return null
    return when {
        clean.startsWith("$") -> clean
        clean.startsWith("USD", ignoreCase = true) -> clean
        clean.any { it.isDigit() } -> "USD $clean"
        else -> clean
    }
}
