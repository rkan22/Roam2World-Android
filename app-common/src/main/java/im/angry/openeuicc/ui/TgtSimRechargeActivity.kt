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
    private lateinit var iccid: TextInputEditText
    private lateinit var customerName: TextInputEditText
    private lateinit var customerPhone: TextInputEditText
    private lateinit var esimSearchIccid: TextInputEditText
    private lateinit var esimSearchButton: MaterialButton
    private lateinit var esimRenewButton: MaterialButton
    private lateinit var esimSearchResult: TextView
    private lateinit var activate: MaterialButton

    private var selectedPackageName = "10GB / 30 Days"
    private var selectedRenewalEsim: MobileEsim? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tgt_sim_recharge)
        setSupportActionBar(requireViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.r2w_tgt_recharge)

        scroll = requireNestedScrollView()
        simRechargeSection = requireViewById(R.id.tgt_sim_recharge_section)
        esimRenewalSection = requireViewById(R.id.tgt_esim_renewal_section)
        selectedPackage = requireViewById(R.id.tgt_selected_package)
        iccidLayout = requireViewById(R.id.tgt_iccid_layout)
        customerNameLayout = requireViewById(R.id.tgt_customer_name_layout)
        customerPhoneLayout = requireViewById(R.id.tgt_customer_phone_layout)
        esimSearchIccidLayout = requireViewById(R.id.tgt_esim_search_iccid_layout)
        iccid = requireViewById(R.id.tgt_iccid)
        customerName = requireViewById(R.id.tgt_customer_name)
        customerPhone = requireViewById(R.id.tgt_customer_phone)
        esimSearchIccid = requireViewById(R.id.tgt_esim_search_iccid)
        esimSearchButton = requireViewById(R.id.tgt_esim_search_button)
        esimRenewButton = requireViewById(R.id.tgt_esim_renew_button)
        esimSearchResult = requireViewById(R.id.tgt_esim_search_result)
        activate = requireViewById(R.id.tgt_activate)

        setupInsets()
        setupModeTabs()
        setupPackageSelection()
        setupActivation()
        setupEsimRenewal()
        renderSelectedPackage()
        renderMode(isEsimRenewal = false)
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
        requireViewById<Chip>(R.id.tgt_mode_sim).setOnClickListener {
            renderMode(isEsimRenewal = false)
        }
        requireViewById<Chip>(R.id.tgt_mode_esim_renewal).setOnClickListener {
            renderMode(isEsimRenewal = true)
        }
    }

    private fun renderMode(isEsimRenewal: Boolean) {
        simRechargeSection.visibility = if (isEsimRenewal) View.GONE else View.VISIBLE
        esimRenewalSection.visibility = if (isEsimRenewal) View.VISIBLE else View.GONE
    }

    private fun setupPackageSelection() {
        listOf(
            R.id.tgt_package_10gb_30d to "10GB / 30 Days",
            R.id.tgt_package_20gb_30d to "20GB / 30 Days",
            R.id.tgt_package_30gb_30d to "30GB / 30 Days",
            R.id.tgt_package_50gb_30d to "50GB / 30 Days",
            R.id.tgt_package_20gb_60d to "20GB / 60 Days",
            R.id.tgt_package_60gb_60d to "60GB / 60 Days"
        ).forEach { (chipId, packageName) ->
            requireViewById<Chip>(chipId).setOnClickListener {
                selectedPackageName = packageName
                renderSelectedPackage()
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
            val esim = selectedRenewalEsim ?: return@setOnClickListener
            Toast.makeText(
                this,
                "TGT eSIM renewal endpoint will be connected next for ${esim.iccid.orEmpty()}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun searchTgtEsimForRenewal() {
        esimSearchIccidLayout.error = null
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
                        esimSearchResult.text = "No TGT eSIM found for ICCID: $query"
                        esimRenewButton.isEnabled = false
                    } else {
                        esimSearchResult.text = listOf(
                            "TGT eSIM found",
                            "ICCID: ${esim.iccid.orEmpty()}",
                            "Plan: ${esim.packageName.orEmpty().ifBlank { "Unknown" }}",
                            "Status: ${esim.statusLabel()}"
                        ).joinToString("\n")
                        esimRenewButton.isEnabled = true
                    }
                }
                .onFailure { error ->
                    selectedRenewalEsim = null
                    esimRenewButton.isEnabled = false
                    esimSearchResult.text = error.message ?: "TGT eSIM search failed"
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
                        error.message ?: "TGT recharge request failed",
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

        val connection = (URL(requestUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 30_000
            readTimeout = 30_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", session.authorizationHeader)
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
                    ?: response?.optString("detail")?.takeIf { it.isNotBlank() }
                    ?: "TGT recharge request failed with HTTP $status"
                throw IllegalStateException(message)
            }
            response?.optString("message")?.takeIf { it.isNotBlank() }
                ?: "TGT recharge request submitted"
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
        esimSearchButton.text = if (searching) "Searching..." else "Find TGT eSIM"
        if (searching) {
            selectedRenewalEsim = null
            esimRenewButton.isEnabled = false
        }
    }

    private fun renderSelectedPackage() {
        selectedPackage.text = "Selected package: $selectedPackageName"
    }

    private fun tgtRechargeUrl(): String =
        "${BuildConfig.ROAM2WORLD_API_BASE_URL.trimEnd('/')}/api/v1/mobile/tgt/recharge/"

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

    private fun clearErrors() {
        iccidLayout.error = null
        customerNameLayout.error = null
        customerPhoneLayout.error = null
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
}
