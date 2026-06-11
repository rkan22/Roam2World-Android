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
import com.google.android.material.button.MaterialButton
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import im.angry.openeuicc.auth.MobileEsim
import im.angry.openeuicc.common.R
import im.angry.openeuicc.util.mainViewPaddingInsetHandler
import im.angry.openeuicc.util.setupRootViewSystemBarInsets

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
            val shareText = buildString {
                appendLine(requireViewById<TextView>(R.id.mobile_esim_qr_title).text.toString())
                appendLine()
                appendLine(getString(R.string.mobile_esim_share_qr_payload_label))
                appendLine(payload)
            }

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, getString(R.string.mobile_esim_qr_title))
                putExtra(Intent.EXTRA_TEXT, shareText)
            }

            startActivity(Intent.createChooser(intent, getString(R.string.mobile_esim_share_qr)))
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
