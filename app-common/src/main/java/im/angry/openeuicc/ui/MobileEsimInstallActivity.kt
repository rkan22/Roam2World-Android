package im.angry.openeuicc.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import im.angry.openeuicc.auth.MobileEsim
import im.angry.openeuicc.common.R
import im.angry.openeuicc.ui.wizard.DownloadWizardActivity
import im.angry.openeuicc.util.LPAString
import im.angry.openeuicc.util.activityToolbarInsetHandler
import im.angry.openeuicc.util.mainViewPaddingInsetHandler
import im.angry.openeuicc.util.setupRootViewSystemBarInsets
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch

class MobileEsimInstallActivity : BaseEuiccAccessActivity() {
    private lateinit var progress: LinearProgressIndicator
    private lateinit var error: TextView
    private lateinit var compatibilityStatus: TextView
    private lateinit var deviceStatus: TextView
    private lateinit var downloadStatus: TextView
    private lateinit var smdpText: TextView
    private lateinit var matchingIdText: TextView
    private lateinit var startButton: MaterialButton
    private lateinit var retryButton: MaterialButton

    private var installCode: String = ""
    private var lpaPayload: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mobile_esim_install)
        setSupportActionBar(requireViewById(R.id.toolbar))
        supportActionBar?.apply {
            title = getString(R.string.mobile_esim_install_title)
            setDisplayHomeAsUpEnabled(true)
        }

        installCode = intent.getStringExtra(EXTRA_INSTALL_CODE).orEmpty()
        progress = requireViewById(R.id.mobile_esim_install_progress)
        error = requireViewById(R.id.mobile_esim_install_error)
        compatibilityStatus = requireViewById(R.id.mobile_esim_install_compatibility_status)
        deviceStatus = requireViewById(R.id.mobile_esim_install_device_status)
        downloadStatus = requireViewById(R.id.mobile_esim_install_download_status)
        smdpText = requireViewById(R.id.mobile_esim_install_smdp)
        matchingIdText = requireViewById(R.id.mobile_esim_install_matching_id)
        startButton = requireViewById(R.id.mobile_esim_install_start)
        retryButton = requireViewById(R.id.mobile_esim_install_retry)

        setupInsets()
        setInitialState()
        startButton.setOnClickListener { launchDownloadWizard() }
        retryButton.setOnClickListener { runPreflight() }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }

    override fun onInit() {
        runPreflight()
    }

    private fun setupInsets() {
        setupRootViewSystemBarInsets(
            window.decorView.rootView,
            arrayOf(
                this::activityToolbarInsetHandler,
                mainViewPaddingInsetHandler(requireViewById(R.id.mobile_esim_install_scroll))
            ),
            consume = false
        )
    }

    private fun setInitialState() {
        setLoading(false)
        error.visibility = View.GONE
        retryButton.visibility = View.GONE
        startButton.isEnabled = false
        compatibilityStatus.text = getString(R.string.mobile_esim_install_step_waiting)
        deviceStatus.text = getString(R.string.mobile_esim_install_step_waiting)
        downloadStatus.text = getString(R.string.mobile_esim_install_step_waiting)
        smdpText.visibility = View.GONE
        matchingIdText.visibility = View.GONE
    }

    private fun runPreflight() {
        lifecycleScope.launch {
            setInitialState()
            setLoading(true)
            compatibilityStatus.text = getString(R.string.mobile_esim_install_step_running)
            val parsed = runCatching {
                val payload = if (installCode.startsWith("LPA:", ignoreCase = true)) {
                    installCode
                } else {
                    "LPA:$installCode"
                }
                LPAString.parse(payload).also { lpaPayload = payload }
            }.getOrElse {
                showFailure(getString(R.string.mobile_esim_install_invalid_code))
                return@launch
            }

            compatibilityStatus.text = getString(R.string.mobile_esim_install_step_passed)
            smdpText.text = getString(R.string.mobile_esim_smdp_format, parsed.address)
            smdpText.visibility = View.VISIBLE
            matchingIdText.text = getString(
                R.string.mobile_esim_matching_id_format,
                parsed.matchingId ?: getString(R.string.mobile_esim_value_unavailable)
            )
            matchingIdText.visibility = View.VISIBLE

            deviceStatus.text = getString(R.string.mobile_esim_install_step_running)
            val openEuiccPorts = runCatching {
                euiccChannelManager.flowAllOpenEuiccPorts().toList()
            }.getOrElse {
                showFailure(getString(R.string.mobile_esim_install_device_check_failed))
                return@launch
            }
            val platformHasEuicc = packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY_EUICC)
            if (openEuiccPorts.isEmpty() && !platformHasEuicc) {
                showFailure(getString(R.string.mobile_esim_install_device_unsupported))
                return@launch
            }

            deviceStatus.text = getString(R.string.mobile_esim_install_step_passed)
            downloadStatus.text = getString(R.string.mobile_esim_install_ready)
            setLoading(false)
            startButton.isEnabled = true
        }
    }

    private fun showFailure(message: String) {
        setLoading(false)
        error.text = message
        error.visibility = View.VISIBLE
        retryButton.visibility = View.VISIBLE
        startButton.isEnabled = false
        downloadStatus.text = getString(R.string.mobile_esim_install_step_blocked)
    }

    private fun launchDownloadWizard() {
        if (lpaPayload.isBlank()) return
        downloadStatus.text = getString(R.string.mobile_esim_install_launching)
        startActivity(
            DownloadWizardActivity.newIntent(this).apply {
                action = Intent.ACTION_VIEW
                data = lpaPayload.toUri()
            }
        )
        finish()
    }

    private fun setLoading(loading: Boolean) {
        progress.visibility = if (loading) View.VISIBLE else View.GONE
    }

    companion object {
        private const val EXTRA_INSTALL_CODE = "mobile_esim_install.install_code"

        fun createIntent(context: Context, esim: MobileEsim): Intent =
            Intent(context, MobileEsimInstallActivity::class.java).apply {
                putExtra(EXTRA_INSTALL_CODE, esim.installCode())
            }
    }
}
