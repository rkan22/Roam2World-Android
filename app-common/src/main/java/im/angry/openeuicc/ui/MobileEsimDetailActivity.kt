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
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import im.angry.openeuicc.auth.AuthSession
import im.angry.openeuicc.auth.AuthTokenStore
import im.angry.openeuicc.auth.JwtUtils
import im.angry.openeuicc.auth.MobileEsim
import im.angry.openeuicc.auth.Roam2WorldAuthApi
import im.angry.openeuicc.common.BuildConfig
import im.angry.openeuicc.common.R
import im.angry.openeuicc.util.activityToolbarInsetHandler
import im.angry.openeuicc.util.mainViewPaddingInsetHandler
import im.angry.openeuicc.util.setupRootViewSystemBarInsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.net.URLEncoder
import java.io.File
import java.time.OffsetDateTime

class MobileEsimDetailActivity : AppCompatActivity() {

    private fun formatProviderDate(value: String): String {
        if (value.isBlank()) return ""
        return try {
            val formatter = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm", Locale.ENGLISH)
            Instant.parse(value).atZone(ZoneId.systemDefault()).format(formatter)
        } catch (_: Exception) {
            value
        }
    }

    private fun formatProviderStatus(value: String): String {
        return when (value.uppercase(Locale.ROOT)) {
            "INUSE" -> "In Use"
            "ACTIVATED" -> "Activated"
            "NOTACTIVE" -> "Not Activated"
            "USED" -> "Used Up"
            "EXPIRED" -> "Expired"
            "ABANDON" -> "Abandoned"
            "TERMINATION" -> "Terminated"
            "ENABLED" -> "Enabled"
            "DISABLED" -> "Disabled"
            else -> value.ifBlank { "Unknown" }
        }
    }


    private val tokenStore by lazy { AuthTokenStore(this) }
    private val authApi by lazy { Roam2WorldAuthApi(BuildConfig.ROAM2WORLD_API_BASE_URL) }

    private lateinit var progress: LinearProgressIndicator
    private lateinit var error: TextView
    private lateinit var qrImage: ImageView
    private lateinit var qrUnavailable: TextView
    private lateinit var copyIccidButton: MaterialButton
    private lateinit var copyActivationButton: MaterialButton
    private lateinit var copySmdpButton: MaterialButton
    private lateinit var showQrButton: MaterialButton
    private lateinit var shareQrButton: MaterialButton
    private lateinit var sendCustomerButton: MaterialButton
    private lateinit var renewButton: MaterialButton
    private lateinit var installButton: MaterialButton
    private lateinit var installUnavailable: TextView

    private var currentEsim: MobileEsim? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mobile_esim_detail)
        setSupportActionBar(requireViewById(R.id.toolbar))
        supportActionBar?.apply {
            title = getString(R.string.mobile_esim_detail_title)
            setDisplayHomeAsUpEnabled(true)
        }

        progress = requireViewById(R.id.mobile_esim_detail_progress)
        error = requireViewById(R.id.mobile_esim_detail_error)
        qrImage = requireViewById(R.id.mobile_esim_qr_image)
        qrUnavailable = requireViewById(R.id.mobile_esim_qr_unavailable)
        copyIccidButton = requireViewById(R.id.mobile_esim_copy_iccid)
        copyActivationButton = requireViewById(R.id.mobile_esim_copy_activation)
        copySmdpButton = requireViewById(R.id.mobile_esim_copy_smdp)
        showQrButton = requireViewById(R.id.mobile_esim_show_qr)
        shareQrButton = requireViewById(R.id.mobile_esim_share_qr)
        sendCustomerButton = requireViewById(R.id.mobile_esim_send_customer)
        installButton = requireViewById(R.id.mobile_esim_install_button)
        installUnavailable = requireViewById(R.id.mobile_esim_install_unavailable)
        renewButton = createRenewButton()

        setupInsets()
        renderEsim(readIntentEsim())
        loadLatestDetails()
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
                mainViewPaddingInsetHandler(requireViewById(R.id.mobile_esim_detail_scroll))
            ),
            consume = false
        )
    }

    private fun createRenewButton(): MaterialButton {
        val parent = installButton.parent as LinearLayout
        val button = MaterialButton(this).apply {
            text = "Renew eSIM"
            visibility = View.GONE
            cornerRadius = dp(14)
            icon = getDrawable(R.drawable.ic_packages)
            setTextColor(getColor(android.R.color.white))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            background = getDrawable(R.drawable.r2w_premium_primary_button)
            backgroundTintList = null
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(56)).apply {
                topMargin = dp(20)
            }
        }
        parent.addView(button, parent.indexOfChild(installButton))
        return button
    }

    private fun loadLatestDetails() {
        lifecycleScope.launch {
            val selected = currentEsim ?: return@launch
            error.visibility = View.GONE
            setLoading(true)
            val session = activeSessionOrReturnToLogin()
            if (session == null) {
                setLoading(false)
                return@launch
            }
            val result = runCatching {
                selected.id?.takeIf { it.isNotBlank() }?.let { authApi.esim(session, it) }
                    ?: authApi.esims(session).esims.firstOrNull { it.matches(selected) }
                    ?: throw IllegalStateException(getString(R.string.mobile_esim_detail_missing))
            }
            setLoading(false)

            result
                .onSuccess { renderEsim(it) }
                .onFailure {
                    error.text = it.message ?: getString(R.string.mobile_esims_load_failed)
                    error.visibility = View.VISIBLE
                }
        }
    }

    private suspend fun activeSessionOrReturnToLogin(): AuthSession? {
        val savedSession = withContext(Dispatchers.IO) {
            tokenStore.getSession()
        } ?: return redirectToLogin()

        if (!JwtUtils.isExpired(savedSession.accessToken)) return savedSession

        val refreshed = runCatching {
            authApi.refresh(savedSession)
        }.getOrNull() ?: return redirectToLogin()

        withContext(Dispatchers.IO) {
            tokenStore.save(refreshed)
        }
        return refreshed
    }


    private fun visibleProvider(provider: String?): String? =
        provider?.replace("TGT", "Orange", ignoreCase = true)
            ?.replace("tgt", "Orange", ignoreCase = true)

    private fun renderEsim(esim: MobileEsim?) {
        currentEsim = esim
        if (esim == null) {
            error.text = getString(R.string.mobile_esim_detail_missing)
            error.visibility = View.VISIBLE
            return
        }

        val displayStatus = realStatus(esim)
        requireViewById<TextView>(R.id.mobile_esim_detail_title).text = esim.title()
        setOptionalText(R.id.mobile_esim_detail_iccid, esim.iccid, R.string.mobile_esim_iccid_format)
        requireViewById<TextView>(R.id.mobile_esim_detail_provider).applyRoamProviderChip(visibleProvider(esim.provider))
        setOptionalText(R.id.mobile_esim_detail_package, PackageNameCleaner.clean(esim.packageName), R.string.mobile_esim_package_format)
        requireViewById<TextView>(R.id.mobile_esim_detail_status).applyRoamStatusChip(displayStatus.label, displayStatus.raw)
        setOptionalText(R.id.mobile_esim_detail_activation, esim.activationCode, R.string.mobile_esim_activation_format)
        setOptionalText(R.id.mobile_esim_detail_smdp, esim.smdpAddress, R.string.mobile_esim_smdp_format)
        setOptionalText(R.id.mobile_esim_detail_matching_id, esim.matchingId, R.string.mobile_esim_matching_id_format)
        setOptionalText(R.id.mobile_esim_detail_expires, esim.expiresAt?.let { formatProviderDate(it) }, R.string.mobile_esim_expires_format)
        setOptionalText(R.id.mobile_esim_detail_data_remaining, esim.dataRemaining, R.string.mobile_esim_data_remaining_format)
        setOptionalText(R.id.mobile_esim_detail_data_used, esim.dataUsed, R.string.mobile_esim_data_used_format)
        setOptionalText(R.id.mobile_esim_detail_created, esim.createdAt?.let { formatProviderDate(it) }, R.string.mobile_esim_created_format)
        setOptionalText(R.id.mobile_esim_detail_order, esim.orderNumber, R.string.mobile_esim_order_format)
        renderQr(esim)
        renderInstallAssistant(esim)
        renderLastRenewal(esim)
        renderActions(esim)
    }

    private fun renderInstallAssistant(esim: MobileEsim) {
        val qrReady = !esim.qrPayload().isNullOrBlank()
        val manualReady = !esim.smdpAddress.isNullOrBlank() && !esim.matchingId.isNullOrBlank()
        val installReady = qrReady || manualReady || !esim.installCode().isNullOrBlank()

        val status = when {
            qrReady -> "QR Ready"
            manualReady -> "Manual Install Ready"
            installReady -> "Install Info Ready"
            else -> "• Waiting for Install Info"
        }

        val steps = listOf(
            "1. Scan QR code or open QR view",
            "2. Use SMDP + Matching ID for manual install",
            "3. Tap Install eSIM / OpenEUICC",
            "• 4. Enable mobile data and data roaming after install"
        ).joinToString("\n")

        requireViewById<TextView>(R.id.mobile_esim_install_status).text = status
        requireViewById<TextView>(R.id.mobile_esim_install_steps).text = steps
    }

    private fun renderLastRenewal(esim: MobileEsim) {
        val renewal = esim.lastRenewal ?: return
        val parent = requireViewById<View>(R.id.mobile_esim_detail_data_used).parent as? LinearLayout ?: return
        val existing = parent.findViewWithTag<View>("last_renewal_card")
        if (existing != null) parent.removeView(existing)

        val details = listOfNotNull(
            renewal.message?.let { "Status: $it" },
            renewal.code?.let { "Code: $it" },
            renewal.orderNo?.let { "Order: $it" },
            renewal.productName?.let { "Package: $it" },
            renewal.productCode?.let { "Product code: $it" },
            renewal.createdTime?.let { "Created: ${formatProviderDate(it)}" },
            renewal.activatedEndTime?.let { "Activated end: ${formatProviderDate(it)}" },
            renewal.renewExpirationTime?.let { "Renew expiry: ${formatProviderDate(it)}" },
            renewal.latestActivationTime?.let { "Latest activation: ${formatProviderDate(it)}" },
            renewal.orderStatus?.let { "Order status: ${formatProviderStatus(it)}" },
            renewal.profileStatus?.let { "Profile status: ${formatProviderStatus(it)}" }
        ).joinToString("\n")

        if (details.isBlank()) return

        val card = MaterialCardView(this).apply {
            tag = "last_renewal_card"
            radius = dp(18).toFloat()
            cardElevation = dp(3).toFloat()
            strokeWidth = dp(1)
            setStrokeColor(getColor(R.color.r2w_premium_border))
            setCardBackgroundColor(getColor(R.color.r2w_premium_surface))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(14) }
        }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(16))
        }
        body.addView(TextView(this).apply {
            text = "Last Renewal"
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(getColor(R.color.r2w_premium_text))
        })
        body.addView(TextView(this).apply {
            text = details
            setPadding(0, dp(8), 0, 0)
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
            setTextColor(getColor(R.color.r2w_premium_muted))
        })
        card.addView(body)

        val anchor = requireViewById<View>(R.id.mobile_esim_detail_created)
        val index = parent.indexOfChild(anchor)
        parent.addView(card, if (index >= 0) index + 1 else parent.childCount)
    }

    private fun renderQr(esim: MobileEsim) {
        val payload = esim.qrPayload()
        val bitmap = payload?.let { createQrBitmap(it, QR_SIZE) }
        qrImage.setImageBitmap(bitmap)
        qrImage.visibility = if (bitmap == null) View.GONE else View.VISIBLE
        qrUnavailable.visibility = if (bitmap == null) View.VISIBLE else View.GONE
    }

    private fun renderActions(esim: MobileEsim) {
        requireViewById<View>(R.id.mobile_esim_iccid_card).visibility =
            if (esim.iccid.isNullOrBlank()) View.GONE else View.VISIBLE
        copyIccidButton.visibility = if (esim.iccid.isNullOrBlank()) View.GONE else View.VISIBLE
        copyIccidButton.setOnClickListener {
            copyToClipboard(
                getString(R.string.mobile_esim_iccid_label),
                esim.iccid,
                R.string.toast_iccid_copied
            )
        }

        requireViewById<View>(R.id.mobile_esim_activation_card).visibility =
            if (esim.activationCode.isNullOrBlank()) View.GONE else View.VISIBLE
        copyActivationButton.visibility = if (esim.activationCode.isNullOrBlank()) View.GONE else View.VISIBLE
        copyActivationButton.setOnClickListener {
            copyToClipboard(
                getString(R.string.mobile_esim_activation_label),
                esim.activationCode,
                R.string.mobile_esim_activation_copied
            )
        }

        requireViewById<View>(R.id.mobile_esim_smdp_card).visibility =
            if (esim.smdpAddress.isNullOrBlank() && esim.matchingId.isNullOrBlank()) View.GONE else View.VISIBLE
        copySmdpButton.visibility = if (esim.smdpAddress.isNullOrBlank()) View.GONE else View.VISIBLE
        copySmdpButton.setOnClickListener {
            copyToClipboard(
                getString(R.string.mobile_esim_smdp_label),
                esim.smdpAddress,
                R.string.mobile_esim_smdp_copied
            )
        }

        val qrPayload = esim.qrPayload()
        showQrButton.visibility = if (qrPayload.isNullOrBlank()) View.GONE else View.VISIBLE
        shareQrButton.visibility = showQrButton.visibility
        sendCustomerButton.visibility = showQrButton.visibility

        showQrButton.setOnClickListener {
            startActivity(MobileEsimQrActivity.createIntent(this, esim))
        }

        shareQrButton.setOnClickListener {
            shareQrPayload(esim)
        }

        sendCustomerButton.setOnClickListener {
            shareQrPayload(esim, esim.customerEmail?.takeIf { email -> email.isNotBlank() })
        }

        renewButton.visibility = if (canRenew(esim)) View.VISIBLE else View.GONE
        renewButton.setOnClickListener { openRenewal(esim) }

        val installCode = esim.installCode()
        installButton.isEnabled = !installCode.isNullOrBlank()
        installUnavailable.visibility = if (installCode.isNullOrBlank()) View.VISIBLE else View.GONE
        installButton.setOnClickListener {
            launchInstallFlow(esim, installCode)
        }
    }

    private fun shareQrPayload(esim: MobileEsim, recipientEmail: String? = null) {
        val payload = esim.qrPayload().orEmpty()
        if (payload.isBlank()) return

        val planName = esim.title()
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
            recipientEmail?.let { putExtra(Intent.EXTRA_EMAIL, arrayOf(it)) }
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

    private fun launchInstallFlow(esim: MobileEsim, installCode: String?) {
        if (installCode.isNullOrBlank()) {
            installUnavailable.visibility = View.VISIBLE
            return
        }
        startActivity(MobileEsimInstallActivity.createIntent(this, esim))
    }

    private fun canRenew(esim: MobileEsim): Boolean {
        val status = realStatus(esim).raw
        if (status == "expired") return false
        val provider = esim.provider.orEmpty().lowercase()
        return provider.contains("tgt") || provider.contains("airhub") || provider.contains("vodafone")
    }

    private fun openRenewal(esim: MobileEsim) {
        esim.iccid?.takeIf { it.isNotBlank() }?.let {
            getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("ICCID", it))
            Toast.makeText(this, "ICCID copied for renewal", Toast.LENGTH_SHORT).show()
        }
        val provider = esim.provider.orEmpty().lowercase()
        val target = if (provider.contains("airhub") || provider.contains("vodafone")) {
            VodafoneRenewalActivity::class.java
        } else {
            TgtSimRechargeActivity::class.java
        }
        startActivity(Intent(this, target).apply { putExtra("renew.iccid", esim.iccid) })
    }

    private fun setOptionalText(viewId: Int, value: String?, formatResId: Int) {
        requireViewById<TextView>(viewId).apply {
            text = value?.let { getString(formatResId, it) }.orEmpty()
            visibility = if (value.isNullOrBlank()) View.GONE else View.VISIBLE
        }
    }

    private fun realStatus(esim: MobileEsim): DisplayStatus {
        val raw = esim.status.orEmpty().trim()
        val normalized = raw.lowercase()
        val expiresAt = parseDate(esim.expiresAt)
        val isExpiredByDate = expiresAt?.isBefore(OffsetDateTime.now()) == true
        val hasIccid = !esim.iccid.isNullOrBlank()
        val hasInstallCode = !esim.installCode().isNullOrBlank() || !esim.qrPayload().isNullOrBlank()

        return when {
            normalized.contains("expired") || normalized.contains("depleted") || normalized.contains("terminated") || isExpiredByDate ->
                DisplayStatus("Expired", "expired")
            normalized.contains("active") || normalized.contains("activated") || normalized.contains("enabled") ->
                DisplayStatus("Active", "active")
            normalized.contains("pending") || normalized.contains("processing") || normalized.contains("waiting") || normalized.contains("ordered") ->
                DisplayStatus("Pending", "pending")
            hasIccid && hasInstallCode && expiresAt != null ->
                DisplayStatus("Ready", "ready")
            hasIccid && expiresAt != null ->
                DisplayStatus("Active", "active")
            hasIccid && hasInstallCode ->
                DisplayStatus("Ready", "ready")
            hasInstallCode ->
                DisplayStatus("Ready to Install", "ready")
            hasIccid ->
                DisplayStatus("Provisioned", raw.ifBlank { "provisioned" })
            else ->
                DisplayStatus("Pending", "pending")
        }
    }

    private fun parseDate(value: String?): OffsetDateTime? =
        value?.takeIf { it.isNotBlank() }?.let { runCatching { OffsetDateTime.parse(it) }.getOrNull() }

    private fun copyToClipboard(label: String, value: String?, toastResId: Int) {
        if (value.isNullOrBlank()) return
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

    private fun setLoading(loading: Boolean) {
        progress.visibility = if (loading) View.VISIBLE else View.GONE
    }

    private fun redirectToLogin(): AuthSession? {
        tokenStore.clear()
        startActivity(
            Intent(this, LoginActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
        )
        finish()
        return null
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun readIntentEsim(): MobileEsim? =
        if (listOf(
                EXTRA_ID,
                EXTRA_ICCID,
                EXTRA_PROVIDER,
                EXTRA_PACKAGE_NAME,
                EXTRA_ACTIVATION_CODE,
                EXTRA_LPA_CODE,
                EXTRA_SMDP,
                EXTRA_QR_CODE,
                EXTRA_QR_URL,
                EXTRA_EXPIRES_AT,
                EXTRA_DATA_REMAINING,
                EXTRA_DATA_USED
            ).none { !intent.getStringExtra(it).isNullOrBlank() }
        ) {
            null
        } else {
            MobileEsim(
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
        }

    private fun readIntentLastRenewal(): im.angry.openeuicc.auth.MobileEsimLastRenewal? {
        val orderNo = intent.getStringExtra(EXTRA_LAST_RENEWAL_ORDER_NO)
        val message = intent.getStringExtra(EXTRA_LAST_RENEWAL_MESSAGE)
        val productName = intent.getStringExtra(EXTRA_LAST_RENEWAL_PRODUCT_NAME)
        if (orderNo.isNullOrBlank() && message.isNullOrBlank() && productName.isNullOrBlank()) return null
        return im.angry.openeuicc.auth.MobileEsimLastRenewal(
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

    private fun MobileEsim.matches(other: MobileEsim): Boolean =
        listOf(
            id to other.id,
            iccid to other.iccid,
            orderNumber to other.orderNumber,
            orderId to other.orderId
        ).any { (left, right) -> !left.isNullOrBlank() && left == right }

    private data class DisplayStatus(
        val label: String,
        val raw: String
    )

    companion object {
        private const val QR_SIZE = 720
        private const val EXTRA_ID = "mobile_esim.id"
        private const val EXTRA_ICCID = "mobile_esim.iccid"
        private const val EXTRA_PROVIDER = "mobile_esim.provider"
        private const val EXTRA_PACKAGE_NAME = "mobile_esim.package_name"
        private const val EXTRA_STATUS = "mobile_esim.status"
        private const val EXTRA_ACTIVATION_CODE = "mobile_esim.activation_code"
        private const val EXTRA_LPA_CODE = "mobile_esim.lpa_code"
        private const val EXTRA_SMDP = "mobile_esim.smdp"
        private const val EXTRA_MATCHING_ID = "mobile_esim.matching_id"
        private const val EXTRA_CONFIRMATION_REQUIRED = "mobile_esim.confirmation_required"
        private const val EXTRA_QR_CODE = "mobile_esim.qr_code"
        private const val EXTRA_QR_URL = "mobile_esim.qr_url"
        private const val EXTRA_CREATED_AT = "mobile_esim.created_at"
        private const val EXTRA_ORDER_NUMBER = "mobile_esim.order_number"
        private const val EXTRA_EXPIRES_AT = "mobile_esim.expires_at"
        private const val EXTRA_DATA_REMAINING = "mobile_esim.data_remaining"
        private const val EXTRA_DATA_USED = "mobile_esim.data_used"
        private const val EXTRA_ORDER_ID = "mobile_esim.order_id"
        private const val EXTRA_LAST_RENEWAL_PROFILE_STATUS = "mobile_esim.last_renewal.profile_status"
        private const val EXTRA_LAST_RENEWAL_ORDER_STATUS = "mobile_esim.last_renewal.order_status"
        private const val EXTRA_LAST_RENEWAL_LATEST_ACTIVATION_TIME = "mobile_esim.last_renewal.latest_activation_time"
        private const val EXTRA_LAST_RENEWAL_RENEW_EXPIRATION_TIME = "mobile_esim.last_renewal.renew_expiration_time"
        private const val EXTRA_LAST_RENEWAL_ACTIVATED_END_TIME = "mobile_esim.last_renewal.activated_end_time"
        private const val EXTRA_LAST_RENEWAL_CREATED_TIME = "mobile_esim.last_renewal.created_time"
        private const val EXTRA_LAST_RENEWAL_PRODUCT_CODE = "mobile_esim.last_renewal.product_code"
        private const val EXTRA_LAST_RENEWAL_PRODUCT_NAME = "mobile_esim.last_renewal.product_name"
        private const val EXTRA_LAST_RENEWAL_ORDER_NO = "mobile_esim.last_renewal.order_no"
        private const val EXTRA_LAST_RENEWAL_CODE = "mobile_esim.last_renewal.code"
        private const val EXTRA_LAST_RENEWAL_MESSAGE = "mobile_esim.last_renewal.message"
        private const val EXTRA_LAST_RENEWAL_SUCCESS = "mobile_esim.last_renewal.success"
        private const val EXTRA_LAST_RENEWAL_PROVIDER = "mobile_esim.last_renewal.provider"

        fun createIntent(context: Context, esimId: String): Intent =
            Intent(context, MobileEsimDetailActivity::class.java).apply {
                putExtra(EXTRA_ID, esimId)
            }

        fun createIntent(context: Context, esim: MobileEsim): Intent =
            Intent(context, MobileEsimDetailActivity::class.java).apply {
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
