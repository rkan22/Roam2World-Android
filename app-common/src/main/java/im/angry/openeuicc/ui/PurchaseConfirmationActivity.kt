package im.angry.openeuicc.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import com.google.android.material.button.MaterialButton
import im.angry.openeuicc.auth.MobilePackagePurchaseResult
import im.angry.openeuicc.common.R
import im.angry.openeuicc.ui.wizard.DownloadWizardActivity
import im.angry.openeuicc.util.activityToolbarInsetHandler
import im.angry.openeuicc.util.mainViewPaddingInsetHandler
import im.angry.openeuicc.util.setupRootViewSystemBarInsets

class PurchaseConfirmationActivity : AppCompatActivity() {
    private lateinit var installButton: MaterialButton
    private lateinit var installUnavailable: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_purchase_confirmation)
        setSupportActionBar(requireViewById(R.id.toolbar))
        supportActionBar?.apply {
            title = getString(R.string.purchase_confirmation_title)
            setDisplayHomeAsUpEnabled(true)
        }

        setupInsets()
        installButton = requireViewById(R.id.purchase_install_button)
        installUnavailable = requireViewById(R.id.purchase_install_unavailable)
        renderConfirmation()
        installButton.setOnClickListener {
            launchInstallFlow()
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

    private fun setupInsets() {
        setupRootViewSystemBarInsets(
            window.decorView.rootView,
            arrayOf(
                this::activityToolbarInsetHandler,
                mainViewPaddingInsetHandler(requireViewById(R.id.purchase_confirmation_scroll))
            ),
            consume = false
        )
    }

    private fun renderConfirmation() {
        requireViewById<TextView>(R.id.purchase_package_name).text =
            intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: getString(R.string.packages_title)
        setOptionalText(R.id.purchase_order_number, intent.getStringExtra(EXTRA_ORDER_NUMBER), R.string.purchase_order_number_format)
        setOptionalText(R.id.purchase_order_status, intent.getStringExtra(EXTRA_STATUS), R.string.purchase_status_format)
        setOptionalText(R.id.purchase_price, intent.getStringExtra(EXTRA_PRICE), R.string.purchase_price_format)
        setOptionalText(R.id.purchase_balance_after, intent.getStringExtra(EXTRA_BALANCE_AFTER), R.string.purchase_balance_after_format)

        setOptionalText(R.id.purchase_activation_code, intent.getStringExtra(EXTRA_LPA_CODE), R.string.purchase_activation_code_format)
        setOptionalText(R.id.purchase_smdp, intent.getStringExtra(EXTRA_SMDP), R.string.purchase_smdp_format)
        setOptionalText(R.id.purchase_matching_id, intent.getStringExtra(EXTRA_MATCHING_ID), R.string.purchase_matching_id_format)
        setOptionalText(R.id.purchase_qr_code, intent.getStringExtra(EXTRA_QR_CODE), R.string.purchase_qr_code_format)
        setOptionalText(R.id.purchase_qr_url, intent.getStringExtra(EXTRA_QR_URL), R.string.purchase_qr_url_format)
        setOptionalText(R.id.purchase_iccid, intent.getStringExtra(EXTRA_ICCID), R.string.purchase_iccid_format)
        setOptionalText(R.id.purchase_esim_id, intent.getStringExtra(EXTRA_ESIM_ID), R.string.purchase_esim_id_format)

        val canInstall = !intent.getStringExtra(EXTRA_INSTALL_CODE).isNullOrBlank()
        installButton.isEnabled = canInstall
        installUnavailable.visibility = if (canInstall) View.GONE else View.VISIBLE
    }

    private fun setOptionalText(viewId: Int, value: String?, formatResId: Int) {
        requireViewById<TextView>(viewId).apply {
            text = value?.let { getString(formatResId, it) }.orEmpty()
            visibility = if (value.isNullOrBlank()) View.GONE else View.VISIBLE
        }
    }

    private fun launchInstallFlow() {
        val installCode = intent.getStringExtra(EXTRA_INSTALL_CODE) ?: return
        val lpaUri = if (installCode.startsWith("LPA:", ignoreCase = true)) {
            installCode
        } else {
            "LPA:$installCode"
        }
        startActivity(
            DownloadWizardActivity.newIntent(this).apply {
                action = Intent.ACTION_VIEW
                data = lpaUri.toUri()
            }
        )
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
