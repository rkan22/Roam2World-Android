package im.angry.openeuicc.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.google.android.material.button.MaterialButton
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import im.angry.openeuicc.auth.MobileEsim
import im.angry.openeuicc.common.R
import im.angry.openeuicc.util.mainViewPaddingInsetHandler
import im.angry.openeuicc.util.setupRootViewSystemBarInsets
import java.io.File
import java.net.URLEncoder

class MobileEsimQrActivity : AppCompatActivity() {
    private lateinit var activationCode: String
    private lateinit var smdpAddress: String

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mobile_esim_qr)

        activationCode = intent.getStringExtra(EXTRA_ACTIVATION_CODE).orEmpty()
        smdpAddress = intent.getStringExtra(EXTRA_SMDP).orEmpty()
        val payload = intent.getStringExtra(EXTRA_QR_PAYLOAD).orEmpty()

        setupRootViewSystemBarInsets(
            window.decorView.rootView,
            arrayOf(mainViewPaddingInsetHandler(requireViewById(R.id.mobile_esim_qr_root))),
            consume = false
        )

        requireViewById<ImageButton>(R.id.mobile_esim_qr_close).setOnClickListener { finish() }
        requireViewById<TextView>(R.id.mobile_esim_qr_title).text =
            intent.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { getString(R.string.mobile_esim_qr_title) }
        requireViewById<TextView>(R.id.mobile_esim_qr_activation).text =
            getString(R.string.mobile_esim_activation_format, activationCode)
        requireViewById<TextView>(R.id.mobile_esim_qr_smdp).text =
            getString(R.string.mobile_esim_smdp_format, smdpAddress)

        requireViewById<ImageView>(R.id.mobile_esim_qr_full_image).setImageBitmap(
            createQrBitmap(payload, QR_SIZE)
        )

        requireViewById<MaterialButton>(R.id.mobile_esim_qr_copy_activation).setOnClickListener {
            copyToClipboard(
                getString(R.string.mobile_esim_activation_label),
                activationCode,
                R.string.mobile_esim_activation_copied
            )
        }
        requireViewById<MaterialButton>(R.id.mobile_esim_qr_copy_smdp).setOnClickListener {
            copyToClipboard(
                getString(R.string.mobile_esim_smdp_label),
                smdpAddress,
                R.string.mobile_esim_smdp_copied
            )
        }

        requireViewById<MaterialButton>(R.id.mobile_esim_qr_share).setOnClickListener {
            val planName = requireViewById<TextView>(R.id.mobile_esim_qr_title).text.toString()
                .ifBlank { getString(R.string.mobile_esim_qr_title) }
            shareQrInstallTemplate(planName, payload)
        }

    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }

    private fun shareQrInstallTemplate(planName: String, payload: String) {
        if (payload.isBlank()) return

        val iphoneLink = buildIphoneInstallLink(payload)
        val androidLink = buildAndroidInstallLink(payload)
        val bitmap = createQrBitmap(payload, QR_SIZE) ?: return

        val outputDir = File(cacheDir, "qr").apply { mkdirs() }
        val outputFile = File(outputDir, "esim-install-${System.currentTimeMillis()}.png")
        outputFile.outputStream().use { out ->
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
        }

        val uri = FileProvider.getUriForFile(
            this,
            "${packageName}.fileprovider",
            outputFile
        )

        val plainBody = buildInstallPlainText(planName, iphoneLink, androidLink)
        val htmlBody = buildInstallHtml(planName, iphoneLink, androidLink)

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "message/rfc822"
            putExtra(Intent.EXTRA_SUBJECT, "eSIM Installation - $planName")
            putExtra(Intent.EXTRA_TEXT, plainBody)
            putExtra(Intent.EXTRA_HTML_TEXT, htmlBody)
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        startActivity(Intent.createChooser(intent, getString(R.string.mobile_esim_share_qr)))
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
                    pixels[y * size + x] = if (matrix[x, y]) Color.BLACK else Color.WHITE
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
