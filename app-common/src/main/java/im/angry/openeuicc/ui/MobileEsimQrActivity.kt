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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import im.angry.openeuicc.auth.MobileEsim
import im.angry.openeuicc.common.R
import java.io.File
import java.net.URLEncoder

class MobileEsimQrActivity : ComponentActivity() {
    private lateinit var activationCode: String
    private lateinit var smdpAddress: String
    private lateinit var payload: String
    private lateinit var planTitle: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        activationCode = intent.getStringExtra(EXTRA_ACTIVATION_CODE).orEmpty()
        smdpAddress = intent.getStringExtra(EXTRA_SMDP).orEmpty()
        payload = intent.getStringExtra(EXTRA_QR_PAYLOAD).orEmpty()
        planTitle = intent.getStringExtra(EXTRA_TITLE).orEmpty()
            .ifBlank { getString(R.string.mobile_esim_qr_title) }

        setContent {
            MobileEsimQrScreen(
                title = planTitle,
                activationCode = activationCode,
                smdpAddress = smdpAddress,
                qrBitmap = createQrBitmap(payload, QR_SIZE),
                qrAvailable = payload.isNotBlank(),
                onClose = { finish() },
                onCopyActivation = {
                    copyToClipboard(
                        getString(R.string.mobile_esim_activation_label),
                        activationCode,
                        R.string.mobile_esim_activation_copied
                    )
                },
                onCopySmdp = {
                    copyToClipboard(
                        getString(R.string.mobile_esim_smdp_label),
                        smdpAddress,
                        R.string.mobile_esim_smdp_copied
                    )
                },
                onShare = {
                    shareQrInstallTemplate(planTitle, payload)
                }
            )
        }
    }

    private fun shareQrInstallTemplate(planName: String, payload: String) {
        if (payload.isBlank()) return

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
            clipData = ClipData.newUri(contentResolver, "eSIM QR", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooser = Intent.createChooser(intent, getString(R.string.mobile_esim_share_qr)).apply {
            clipData = ClipData.newUri(contentResolver, "eSIM QR", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        startActivity(chooser)
    }

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

    private fun buildIphoneInstallLink(payload: String): String =
        "https://esimsetup.apple.com/esim_qrcode_provisioning?carddata=${URLEncoder.encode(payload, "UTF-8")}"

    private fun buildAndroidInstallLink(payload: String): String =
        payload.replaceFirst("LPA:", "lpa:", ignoreCase = true)

    private fun copyToClipboard(label: String, value: String, toastResId: Int) {
        if (value.isBlank()) return
        getSystemService(ClipboardManager::class.java)
            .setPrimaryClip(ClipData.newPlainText(label, value))
        Toast.makeText(this, toastResId, Toast.LENGTH_SHORT).show()
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

    companion object {
        private const val QR_SIZE = 960
        private const val EXTRA_TITLE = "mobile_esim_qr.title"
        private const val EXTRA_QR_PAYLOAD = "mobile_esim_qr.payload"
        private const val EXTRA_ACTIVATION_CODE = "mobile_esim_qr.activation_code"
        private const val EXTRA_SMDP = "mobile_esim_qr.smdp"

        fun createIntent(context: Context, esim: MobileEsim): Intent =
            Intent(context, MobileEsimQrActivity::class.java).apply {
                putExtra(EXTRA_TITLE, esim.title())
                putExtra(EXTRA_QR_PAYLOAD, esim.qrPayload())
                putExtra(EXTRA_ACTIVATION_CODE, esim.activationCode)
                putExtra(EXTRA_SMDP, esim.smdpAddress)
            }
    }
}

@Composable
private fun MobileEsimQrScreen(
    title: String,
    activationCode: String,
    smdpAddress: String,
    qrBitmap: Bitmap?,
    qrAvailable: Boolean,
    onClose: () -> Unit,
    onCopyActivation: () -> Unit,
    onCopySmdp: () -> Unit,
    onShare: () -> Unit
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
                    OutlinedButton(onClick = onClose, shape = RoundedCornerShape(16.dp)) {
                        Text("Kapat")
                    }
                }

                QrHeroCard(title = title, orange = orange)

                QrImageCard(
                    qrBitmap = qrBitmap,
                    qrAvailable = qrAvailable
                )

                QrInfoCard(title = "Manuel Kurulum Bilgileri") {
                    QrDetailRow("Activation Code", activationCode)
                    QrDetailRow("SMDP", smdpAddress)
                }

                QrInfoCard(title = "Aksiyonlar") {
                    OutlinedButton(
                        onClick = onCopyActivation,
                        enabled = activationCode.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Text("Activation Kopyala")
                    }

                    OutlinedButton(
                        onClick = onCopySmdp,
                        enabled = smdpAddress.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Text("SMDP Kopyala")
                    }

                    Button(
                        onClick = onShare,
                        enabled = qrAvailable,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = orange),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Text("QR Paylaş")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun QrHeroCard(title: String, orange: Color) {
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
                text = "QR Code",
                color = orange,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = title,
                color = Color.White,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "Bu QR kodu eSIM kurulumunda kullanılır. Paylaşmadan önce alıcıyı kontrol et.",
                color = Color.White.copy(alpha = 0.74f),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun QrImageCard(
    qrBitmap: Bitmap?,
    qrAvailable: Boolean
) {
    QrInfoCard(title = "Kurulum QR") {
        if (qrBitmap != null && qrAvailable) {
            Image(
                bitmap = qrBitmap.asImageBitmap(),
                contentDescription = "eSIM QR",
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(18.dp))
                    .padding(10.dp)
            )
        } else {
            Text(
                text = "QR code is not available for this eSIM.",
                color = Color(0xFF686B73),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun QrInfoCard(
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
private fun QrDetailRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = Color(0xFF686B73), style = MaterialTheme.typography.bodySmall)
        Text(
            text = value.ifBlank { "-" },
            color = Color(0xFF17181C),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}
