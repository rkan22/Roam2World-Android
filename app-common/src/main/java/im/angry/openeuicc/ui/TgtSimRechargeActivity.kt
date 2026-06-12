package im.angry.openeuicc.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import im.angry.openeuicc.auth.AuthSession
import im.angry.openeuicc.auth.AuthTokenStore
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
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class TgtSimRechargeActivity : AppCompatActivity() {
    private val tokenStore by lazy { AuthTokenStore(this) }
    private val authApi by lazy { Roam2WorldAuthApi(BuildConfig.ROAM2WORLD_API_BASE_URL) }

    private lateinit var scroll: View
    private lateinit var simRechargeSection: LinearLayout
    private lateinit var esimRenewalSection: LinearLayout
    private lateinit var selectedPackage: TextView
    private lateinit var iccidLayout: TextInputLayout
    private lateinit var customerNameLayout: TextInputLayout
    private lateinit var customerPhoneLayout: TextInputLayout
    private lateinit var esimSearchIccidLayout: TextInputLayout
    private lateinit var esimCustomerNameLayout: TextInputLayout
    private lateinit var esimCustomerPhoneLayout: TextInputLayout
    private lateinit var esimCustomerEmailLayout: TextInputLayout
    private lateinit var iccid: TextInputEditText
    private lateinit var customerName: TextInputEditText
    private lateinit var customerPhone: TextInputEditText
    private lateinit var esimSearchIccid: TextInputEditText
    private lateinit var esimCustomerName: TextInputEditText
    private lateinit var esimCustomerPhone: TextInputEditText
    private lateinit var esimCustomerEmail: TextInputEditText
    private lateinit var esimSearchButton: MaterialButton
    private lateinit var esimRenewButton: MaterialButton
    private lateinit var esimSearchResult: TextView
    private lateinit var activate: MaterialButton

    private var selectedPackageName = "10GB / 30 Days"
    private var selectedRenewalDataGb = "10"
    private var selectedRenewalEsim: MobileEsim? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tgt_sim_recharge)
        setSupportActionBar(requireViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Orange Recharge"

        scroll = requireNestedScrollView()
        simRechargeSection = requireViewById(R.id.tgt_sim_recharge_section)
        esimRenewalSection = requireViewById(R.id.tgt_esim_renewal_section)
        selectedPackage = requireViewById(R.id.tgt_selected_package)
        iccidLayout = requireViewById(R.id.tgt_iccid_layout)
        customerNameLayout = requireViewById(R.id.tgt_customer_name_layout)
        customerPhoneLayout = requireViewById(R.id.tgt_customer_phone_layout)
        esimSearchIccidLayout = requireViewById(R.id.tgt_esim_search_iccid_layout)
        esimCustomerNameLayout = requireViewById(R.id.tgt_esim_customer_name_layout)
        esimCustomerPhoneLayout = requireViewById(R.id.tgt_esim_customer_phone_layout)
        esimCustomerEmailLayout = requireViewById(R.id.tgt_esim_customer_email_layout)
        iccid = requireViewById(R.id.tgt_iccid)
        customerName = requireViewById(R.id.tgt_customer_name)
        customerPhone = requireViewById(R.id.tgt_customer_phone)
        esimSearchIccid = requireViewById(R.id.tgt_esim_search_iccid)
        esimCustomerName = requireViewById(R.id.tgt_esim_customer_name)
        esimCustomerPhone = requireViewById(R.id.tgt_esim_customer_phone)
        esimCustomerEmail = requireViewById(R.id.tgt_esim_customer_email)
        esimSearchButton = requireViewById(R.id.tgt_esim_search_button)
        esimRenewButton = requireViewById(R.id.tgt_esim_renew_button)
        esimSearchResult = requireViewById(R.id.tgt_esim_search_result)
        activate = requireViewById(R.id.tgt_activate)

        setupInsets()
        setupModeTabs()
        setupPackageSelection()
        setupRenewalPackageSelection()
        setupActivation()
        setupEsimRenewal()
        renderSelectedPackage()
        renderMode(isEsimRenewal = false)
        applyPrefilledIccid()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun setupInsets() {
        setupRootViewSystemBarInsets(
            window.decorView.rootView,
            arrayOf(
                this::activityToolbarInsetHandler,
                mainViewPaddingInsetHandler(scroll)
            ),
            consume = false
        )
    }

    private fun setupModeTabs() {
        requireViewById<View>(R.id.tgt_mode_sim).setOnClickListener {
            renderMode(isEsimRenewal = false)
        }
        requireViewById<View>(R.id.tgt_mode_esim_renewal).setOnClickListener {
            renderMode(isEsimRenewal = true)
        }
    }



    private fun renderMode(isEsimRenewal: Boolean) {
        simRechargeSection.visibility = if (isEsimRenewal) View.GONE else View.VISIBLE
        esimRenewalSection.visibility = if (isEsimRenewal) View.VISIBLE else View.GONE

        requireViewById<View>(R.id.tgt_mode_sim).setBackgroundResource(
            if (!isEsimRenewal) R.drawable.r2w_orange_small_toggle_selected else R.drawable.r2w_orange_small_toggle_unselected
        )
        requireViewById<View>(R.id.tgt_mode_esim_renewal).setBackgroundResource(
            if (isEsimRenewal) R.drawable.r2w_orange_small_toggle_selected else R.drawable.r2w_orange_small_toggle_unselected
        )
    }



    private fun setupPackageSelection() {
        requireViewById<View>(R.id.tgt_product_30d).setOnClickListener {
            selectedPackageName = "10GB / 30 Days"
            renderSelectedPackage()
        }
        requireViewById<View>(R.id.tgt_product_60d).setOnClickListener {
            selectedPackageName = "20GB / 60 Days"
            renderSelectedPackage()
        }

        listOf(
            R.id.tgt_package_10gb_30d to "10GB / 30 Days",
            R.id.tgt_package_20gb_30d to "20GB / 30 Days",
            R.id.tgt_package_30gb_30d to "30GB / 30 Days",
            R.id.tgt_package_50gb_30d to "50GB / 30 Days",
            R.id.tgt_package_20gb_60d to "20GB / 60 Days",
            R.id.tgt_package_60gb_60d to "60GB / 60 Days"
        ).forEach { (viewId, packageName) ->
            requireViewById<View>(viewId).setOnClickListener {
                selectedPackageName = packageName
                renderSelectedPackage()
            }
        }
    }



    private fun setupRenewalPackageSelection() {
        listOf(
            R.id.tgt_esim_renewal_10gb to "10",
            R.id.tgt_esim_renewal_20gb to "20",
            R.id.tgt_esim_renewal_30gb to "30",
            R.id.tgt_esim_renewal_50gb to "50"
        ).forEach { (chipId, dataGb) ->
            requireViewById<Chip>(chipId).setOnClickListener {
                selectedRenewalDataGb = dataGb
            }
        }
    }

    private fun setupActivation() {
        activate.setOnClickListener {
            if (!validateForm()) return@setOnClickListener
            submitRechargeRequest()
        }
    }

    private fun setupEsimRenewal() {
        esimSearchButton.setOnClickListener {
            searchTgtEsimForRenewal()
        }
        esimRenewButton.setOnClickListener {
            if (!validateEsimRenewalForm()) return@setOnClickListener
            submitEsimRenewalRequest()
        }
    }

    private fun applyPrefilledIccid() {
        val prefilled = intent.getStringExtra(EXTRA_RENEW_ICCID)
            ?: intent.getStringExtra(EXTRA_ICCID)
            ?: return
        if (prefilled.isBlank()) return
        renderMode(isEsimRenewal = true)
        esimSearchIccid.setText(prefilled)
        esimSearchIccid.setSelection(esimSearchIccid.text?.length ?: 0)
        esimSearchResult.text = "Searching Orange eSIM: $prefilled"
        esimSearchIccid.post { searchTgtEsimForRenewal() }
    }

    private fun searchTgtEsimForRenewal() {
        clearEsimRenewalErrors()
        val query = esimSearchIccid.text?.toString()?.trim().orEmpty()
        if (query.length < 6) {
            esimSearchIccidLayout.error = "Enter ICCID or at least 6 digits"
            return
        }

        lifecycleScope.launch {
            val session = withContext(Dispatchers.IO) { tokenStore.getSession() }
            if (session == null) {
                startActivity(
                    Intent(this@TgtSimRechargeActivity, LoginActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    }
                )
                finish()
                return@launch
            }

            setEsimSearching(true)
            val result = runCatching {
                withContext(Dispatchers.IO) { authApi.esims(session).esims }
                    .firstOrNull { esim ->
                        esim.provider?.contains("tgt", ignoreCase = true) == true &&
                            esim.iccid?.contains(query, ignoreCase = true) == true
                    }
            }
            setEsimSearching(false)

            result
                .onSuccess { esim ->
                    selectedRenewalEsim = esim
                    if (esim == null) {
                        esimSearchResult.text = "No Orange eSIM found for ICCID: $query"
                        esimRenewButton.isEnabled = false
                    } else {
                        renderFoundTgtEsim(esim)
                        esimRenewButton.isEnabled = true
                    }
                }
                .onFailure { error ->
                    selectedRenewalEsim = null
                    esimRenewButton.isEnabled = false
                    esimSearchResult.text = error.message ?: "Orange eSIM search failed"
                }
        }
    }

    private fun renderFoundTgtEsim(esim: MobileEsim) {
        esimSearchResult.text = listOf(
            "Orange eSIM found",
            "ICCID: ${esim.iccid.orEmpty()}",
            "Current plan: ${PackageNameCleaner.clean(esim.packageName)}",
            "Selected renewal: ${selectedRenewalDataGb}GB",
            "Status: ${esim.statusLabel()}"
        ).joinToString("\n")
    }

    private fun submitEsimRenewalRequest() {
        val esim = selectedRenewalEsim ?: return
        lifecycleScope.launch {
            val session = withContext(Dispatchers.IO) { tokenStore.getSession() }
            if (session == null) {
                startActivity(
                    Intent(this@TgtSimRechargeActivity, LoginActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    }
                )
                finish()
                return@launch
            }

            setEsimRenewing(true)
            val result = runCatching {
                withContext(Dispatchers.IO) { postTgtEsimRenewal(session, esim) }
            }
            setEsimRenewing(false)

            result
                .onSuccess { message ->
                    Toast.makeText(this@TgtSimRechargeActivity, message, Toast.LENGTH_LONG).show()
                    esimSearchResult.text = message
                    selectedRenewalEsim = null
                    esimRenewButton.isEnabled = false
                }
                .onFailure { error ->
                    Toast.makeText(
                        this@TgtSimRechargeActivity,
                        error.message ?: "Orange eSIM renewal request failed",
                        Toast.LENGTH_LONG
                    ).show()
                }
        }
    }

    private fun submitRechargeRequest() {
        lifecycleScope.launch {
            val session = withContext(Dispatchers.IO) { tokenStore.getSession() }
            if (session == null) {
                startActivity(
                    Intent(this@TgtSimRechargeActivity, LoginActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    }
                )
                finish()
                return@launch
            }

            setSubmitting(true)
            val result = runCatching {
                withContext(Dispatchers.IO) { postTgtRecharge(session) }
            }
            setSubmitting(false)

            result
                .onSuccess { message ->
                    Toast.makeText(this@TgtSimRechargeActivity, message, Toast.LENGTH_LONG).show()
                    finish()
                }
                .onFailure { error ->
                    Toast.makeText(
                        this@TgtSimRechargeActivity,
                        error.message ?: "Orange recharge request failed",
                        Toast.LENGTH_LONG
                    ).show()
                }
        }
    }

    private fun postTgtRecharge(session: AuthSession): String {
        val requestUrl = tgtRechargeUrl()
        val body = JSONObject()
            .put("package_name", selectedPackageName)
            .put("iccid", iccid.text?.toString()?.trim().orEmpty())
            .put("customer_name", customerName.text?.toString()?.trim().orEmpty())
            .put("customer_phone", customerPhone.text?.toString()?.trim().orEmpty())
            .put("provider", "Orange Balkans")
            .put("source", "android")

        return postJsonRequest(
            requestUrl = requestUrl,
            authorizationHeader = session.authorizationHeader,
            body = body,
            fallbackMessage = "Orange recharge request submitted",
            fallbackError = "Orange recharge request failed"
        )
    }

    private fun postTgtEsimRenewal(session: AuthSession, esim: MobileEsim): String {
        val requestUrl = tgtEsimRenewalUrl()
        val body = JSONObject()
            .put("iccid", esim.iccid.orEmpty())
            .put("renewal_data_gb", selectedRenewalDataGb)
            .put("customer_name", esimCustomerName.text?.toString()?.trim().orEmpty())
            .put("customer_phone", esimCustomerPhone.text?.toString()?.trim().orEmpty())
            .put("source", "android")

        esimCustomerEmail.text?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let {
            body.put("email", it)
        }
        esim.id?.takeIf { it.isNotBlank() }?.let { body.put("esim_id", it) }

        return postJsonRequest(
            requestUrl = requestUrl,
            authorizationHeader = session.authorizationHeader,
            body = body,
            fallbackMessage = "Orange eSIM renewal submitted",
            fallbackError = "Orange eSIM renewal request failed"
        )
    }

    private fun postJsonRequest(
        requestUrl: String,
        authorizationHeader: String,
        body: JSONObject,
        fallbackMessage: String,
        fallbackError: String
    ): String {
        val connection = (URL(requestUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 30_000
            readTimeout = 30_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", authorizationHeader)
            doOutput = true
        }

        return try {
            connection.outputStream.use { stream ->
                stream.write(body.toString().toByteArray(Charsets.UTF_8))
            }
            val status = connection.responseCode
            val responseText = ((if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()
                ?.use { it.readText() })
                .orEmpty()
            val response = responseText.takeIf { it.isNotBlank() }?.let { JSONObject(it) }
            if (status !in 200..299 || response?.optBoolean("success", true) == false) {
                val message = response?.optString("message")
                    ?.takeIf { it.isNotBlank() }
                    ?: response?.optString("error")?.takeIf { it.isNotBlank() }
                    ?: response?.optString("detail")?.takeIf { it.isNotBlank() }
                    ?: "$fallbackError with HTTP $status"
                throw IllegalStateException(message)
            }
            response?.optString("message")?.takeIf { it.isNotBlank() } ?: fallbackMessage
        } finally {
            connection.disconnect()
        }
    }

    private fun setSubmitting(submitting: Boolean) {
        activate.isEnabled = !submitting
        activate.text = if (submitting) "Submitting..." else getString(R.string.r2w_activate)
    }

    private fun setEsimSearching(searching: Boolean) {
        esimSearchButton.isEnabled = !searching
        esimSearchButton.text = if (searching) "Searching..." else "Find Orange eSIM"
        if (searching) {
            selectedRenewalEsim = null
            esimRenewButton.isEnabled = false
        }
    }

    private fun setEsimRenewing(renewing: Boolean) {
        esimRenewButton.isEnabled = !renewing && selectedRenewalEsim != null
        esimRenewButton.text = if (renewing) "Submitting..." else "Continue Renewal"
    }

    private fun renderSelectedPackage() {
        selectedPackage.text = "Orange Balkans SIM\n$selectedPackageName"

        val is60Days = selectedPackageName.contains("60 Days")
        requireViewById<View>(R.id.tgt_product_30d).setBackgroundResource(
            if (!is60Days) R.drawable.r2w_orange_card_selected else R.drawable.r2w_orange_card_unselected
        )
        requireViewById<View>(R.id.tgt_product_60d).setBackgroundResource(
            if (is60Days) R.drawable.r2w_orange_card_selected else R.drawable.r2w_orange_card_unselected
        )

        listOf(
            R.id.tgt_package_10gb_30d to "10GB / 30 Days",
            R.id.tgt_package_20gb_30d to "20GB / 30 Days",
            R.id.tgt_package_30gb_30d to "30GB / 30 Days",
            R.id.tgt_package_50gb_30d to "50GB / 30 Days",
            R.id.tgt_package_20gb_60d to "20GB / 60 Days",
            R.id.tgt_package_60gb_60d to "60GB / 60 Days"
        ).forEach { (viewId, packageName) ->
            val selected = packageName == selectedPackageName
            val view = requireViewById<TextView>(viewId)
            view.setBackgroundResource(if (selected) R.drawable.r2w_orange_card_selected else R.drawable.r2w_orange_card_unselected)
            view.setTextColor(getColor(if (selected) R.color.r2w_premium_primary else R.color.r2w_premium_text))
            view.typeface = if (selected) android.graphics.Typeface.DEFAULT_BOLD else android.graphics.Typeface.DEFAULT
        }
    }



    private fun tgtRechargeUrl(): String =
        "${BuildConfig.ROAM2WORLD_API_BASE_URL.trimEnd('/')}/api/v1/mobile/tgt/recharge/"

    private fun tgtEsimRenewalUrl(): String =
        "${BuildConfig.ROAM2WORLD_API_BASE_URL.trimEnd('/')}/api/v1/mobile/tgt/esim/renew/"

    private fun validateForm(): Boolean {
        clearErrors()

        val iccidValue = iccid.text?.toString()?.trim().orEmpty()
        val customerNameValue = customerName.text?.toString()?.trim().orEmpty()
        val customerPhoneValue = customerPhone.text?.toString()?.trim().orEmpty()

        var valid = true

        if (iccidValue.length < 10) {
            iccidLayout.error = "Enter a valid ICCID"
            valid = false
        }

        if (customerNameValue.isBlank()) {
            customerNameLayout.error = "Customer name is required"
            valid = false
        }

        if (customerPhoneValue.length < 6) {
            customerPhoneLayout.error = "Enter a valid phone number"
            valid = false
        }

        return valid
    }

    private fun validateEsimRenewalForm(): Boolean {
        clearEsimRenewalErrors()
        var valid = true

        if (selectedRenewalEsim == null) {
            esimSearchIccidLayout.error = "Find an Orange eSIM first"
            valid = false
        }

        if (esimCustomerName.text?.toString()?.trim().isNullOrBlank()) {
            esimCustomerNameLayout.error = "Customer name is required"
            valid = false
        }

        if ((esimCustomerPhone.text?.toString()?.trim()?.length ?: 0) < 6) {
            esimCustomerPhoneLayout.error = "Enter a valid phone number"
            valid = false
        }

        val email = esimCustomerEmail.text?.toString()?.trim().orEmpty()
        if (email.isNotBlank() && !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            esimCustomerEmailLayout.error = "Enter a valid email"
            valid = false
        }

        return valid
    }

    private fun clearErrors() {
        iccidLayout.error = null
        customerNameLayout.error = null
        customerPhoneLayout.error = null
    }

    private fun clearEsimRenewalErrors() {
        esimSearchIccidLayout.error = null
        esimCustomerNameLayout.error = null
        esimCustomerPhoneLayout.error = null
        esimCustomerEmailLayout.error = null
    }

    private fun requireNestedScrollView(): NestedScrollView =
        findNestedScrollView(findViewById(android.R.id.content))
            ?: error("TgtSimRechargeActivity requires a NestedScrollView")

    private fun findNestedScrollView(view: View): NestedScrollView? {
        if (view is NestedScrollView) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                findNestedScrollView(view.getChildAt(index))?.let { return it }
            }
        }
        return null
    }

    private companion object {
        const val EXTRA_RENEW_ICCID = "renew.iccid"
        const val EXTRA_ICCID = "iccid"
    }
}
