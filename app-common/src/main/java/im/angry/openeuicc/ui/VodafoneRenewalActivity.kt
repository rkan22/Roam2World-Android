package im.angry.openeuicc.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
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
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.OffsetDateTime

class VodafoneRenewalActivity : AppCompatActivity() {
    private val tokenStore by lazy { AuthTokenStore(this) }
    private val authApi by lazy { Roam2WorldAuthApi(BuildConfig.ROAM2WORLD_API_BASE_URL) }

    private lateinit var scroll: NestedScrollView
    private lateinit var searchIccidLayout: TextInputLayout
    private lateinit var customerNameLayout: TextInputLayout
    private lateinit var customerPhoneLayout: TextInputLayout
    private lateinit var customerEmailLayout: TextInputLayout
    private lateinit var searchIccid: TextInputEditText
    private lateinit var customerName: TextInputEditText
    private lateinit var customerPhone: TextInputEditText
    private lateinit var customerEmail: TextInputEditText
    private lateinit var searchButton: MaterialButton
    private lateinit var renewButton: MaterialButton
    private lateinit var searchResult: TextView

    private var selectedDataGb = "200"
    private var selectedEsim: MobileEsim? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_vodafone_renewal)
        setSupportActionBar(requireViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Vodafone Recharge"

        scroll = requireViewById(R.id.vodafone_renewal_scroll)
        searchIccidLayout = requireViewById(R.id.vodafone_search_iccid_layout)
        customerNameLayout = requireViewById(R.id.vodafone_customer_name_layout)
        customerPhoneLayout = requireViewById(R.id.vodafone_customer_phone_layout)
        customerEmailLayout = requireViewById(R.id.vodafone_customer_email_layout)
        searchIccid = requireViewById(R.id.vodafone_search_iccid)
        customerName = requireViewById(R.id.vodafone_customer_name)
        customerPhone = requireViewById(R.id.vodafone_customer_phone)
        customerEmail = requireViewById(R.id.vodafone_customer_email)
        searchButton = requireViewById(R.id.vodafone_search_button)
        renewButton = requireViewById(R.id.vodafone_renew_button)
        searchResult = requireViewById(R.id.vodafone_search_result)

        setupInsets()
        setupPackageSelection()
        setupActions()
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

    private fun setupPackageSelection() {
        listOf(
            R.id.vodafone_package_200gb to "200",
            R.id.vodafone_package_400gb to "400",
            R.id.vodafone_package_500gb to "500"
        ).forEach { (chipId, dataGb) ->
            requireViewById<Chip>(chipId).setOnClickListener {
                selectedDataGb = dataGb
                selectedEsim?.let { renderFoundEsim(it) }
            }
        }
    }

    private fun setupActions() {
        searchButton.setOnClickListener { searchVodafoneEsim() }
        renewButton.setOnClickListener {
            if (!validateForm()) return@setOnClickListener
            submitRenewal()
        }
    }

    private fun applyPrefilledIccid() {
        val prefilled = intent.getStringExtra(EXTRA_RENEW_ICCID)
            ?: intent.getStringExtra(EXTRA_ICCID)
            ?: return
        if (prefilled.isBlank()) return
        searchIccid.setText(prefilled)
        searchIccid.setSelection(searchIccid.text?.length ?: 0)
        searchResult.text = "Searching Vodafone eSIM: $prefilled"
        searchIccid.post { searchVodafoneEsim() }
    }

    private fun searchVodafoneEsim() {
        clearErrors()
        val query = searchIccid.text?.toString()?.trim().orEmpty()
        if (query.length < 6) {
            searchIccidLayout.error = "Enter ICCID or at least 6 digits"
            return
        }

        lifecycleScope.launch {
            val session = activeSessionOrReturnToLogin() ?: return@launch
            setSearching(true)
            val result = runCatching {
                withContext(Dispatchers.IO) { authApi.esims(session).esims }
                    .firstOrNull { esim ->
                        isVodafoneEsim(esim) && esim.iccid?.contains(query, ignoreCase = true) == true
                    }
            }
            setSearching(false)

            result
                .onSuccess { esim ->
                    selectedEsim = esim
                    when {
                        esim == null -> {
                            searchResult.text = "No Vodafone eSIM found for ICCID: $query"
                            renewButton.isEnabled = false
                        }
                        isExpired(esim) -> {
                            searchResult.text = listOf(
                                "Vodafone eSIM found but cannot be renewed",
                                "ICCID: ${esim.iccid.orEmpty()}",
                                "Plan: ${PackageNameCleaner.clean(esim.packageName)}",
                                "Expires: ${esim.expiresAt.orEmpty().ifBlank { "Unknown" }}",
                                "Reason: expired eSIMs cannot be renewed"
                            ).joinToString("\n")
                            renewButton.isEnabled = false
                        }
                        else -> {
                            renderFoundEsim(esim)
                            renewButton.isEnabled = true
                        }
                    }
                }
                .onFailure { error ->
                    selectedEsim = null
                    renewButton.isEnabled = false
                    searchResult.text = error.message ?: "Vodafone eSIM search failed"
                }
        }
    }

    private fun renderFoundEsim(esim: MobileEsim) {
        searchResult.text = listOf(
            "Vodafone eSIM found",
            "ICCID: ${esim.iccid.orEmpty()}",
            "Current plan: ${PackageNameCleaner.clean(esim.packageName)}",
            "Expires: ${esim.expiresAt.orEmpty().ifBlank { "Unknown" }}",
            "Selected renewal: ${selectedDataGb}GB / 30 days",
            "Status: ${esim.statusLabel().orEmpty().ifBlank { "Unknown" }}"
        ).joinToString("\n")
    }

    private fun submitRenewal() {
        val esim = selectedEsim ?: return
        lifecycleScope.launch {
            val session = activeSessionOrReturnToLogin() ?: return@launch
            setRenewing(true)
            val result = runCatching {
                withContext(Dispatchers.IO) { postVodafoneRenewal(session, esim) }
            }
            setRenewing(false)

            result
                .onSuccess { message ->
                    Toast.makeText(this@VodafoneRenewalActivity, message, Toast.LENGTH_LONG).show()
                    searchResult.text = message
                    selectedEsim = null
                    renewButton.isEnabled = false
                }
                .onFailure { error ->
                    Toast.makeText(
                        this@VodafoneRenewalActivity,
                        error.message ?: "Vodafone renewal request failed",
                        Toast.LENGTH_LONG
                    ).show()
                }
        }
    }

    private suspend fun activeSessionOrReturnToLogin(): AuthSession? {
        val savedSession = withContext(Dispatchers.IO) { tokenStore.getSession() } ?: return redirectToLogin()
        if (!JwtUtils.isExpired(savedSession.accessToken)) return savedSession
        val refreshed = runCatching { authApi.refresh(savedSession) }.getOrNull() ?: return redirectToLogin()
        withContext(Dispatchers.IO) { tokenStore.save(refreshed) }
        return refreshed
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

    private fun postVodafoneRenewal(session: AuthSession, esim: MobileEsim): String {
        val body = JSONObject()
            .put("iccid", esim.iccid.orEmpty())
            .put("renewal_data_gb", selectedDataGb)
            .put("customer_name", customerName.text?.toString()?.trim().orEmpty())
            .put("customer_phone", customerPhone.text?.toString()?.trim().orEmpty())
            .put("source", "android")

        customerEmail.text?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let {
            body.put("email", it)
        }
        esim.id?.takeIf { it.isNotBlank() }?.let { body.put("esim_id", it) }

        return postJsonRequest(
            requestUrl = vodafoneRenewalUrl(),
            authorizationHeader = session.authorizationHeader,
            body = body,
            fallbackMessage = "Vodafone eSIM renewal submitted",
            fallbackError = "Vodafone renewal request failed"
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

    private fun validateForm(): Boolean {
        clearErrors()
        var valid = true

        if (selectedEsim == null) {
            searchIccidLayout.error = "Find a Vodafone eSIM first"
            valid = false
        }
        if (customerName.text?.toString()?.trim().isNullOrBlank()) {
            customerNameLayout.error = "Customer name is required"
            valid = false
        }
        if ((customerPhone.text?.toString()?.trim()?.length ?: 0) < 6) {
            customerPhoneLayout.error = "Enter a valid phone number"
            valid = false
        }
        val email = customerEmail.text?.toString()?.trim().orEmpty()
        if (email.isNotBlank() && !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            customerEmailLayout.error = "Enter a valid email"
            valid = false
        }
        return valid
    }

    private fun clearErrors() {
        searchIccidLayout.error = null
        customerNameLayout.error = null
        customerPhoneLayout.error = null
        customerEmailLayout.error = null
    }

    private fun setSearching(searching: Boolean) {
        searchButton.isEnabled = !searching
        searchButton.text = if (searching) "Searching..." else "Find Vodafone eSIM"
        if (searching) {
            selectedEsim = null
            renewButton.isEnabled = false
        }
    }

    private fun setRenewing(renewing: Boolean) {
        renewButton.isEnabled = !renewing && selectedEsim != null
        renewButton.text = if (renewing) "Submitting..." else "Continue Renewal"
    }

    private fun isVodafoneEsim(esim: MobileEsim): Boolean {
        val provider = esim.provider.orEmpty().lowercase()
        val title = esim.packageName.orEmpty().lowercase()
        return provider.contains("airhub") || provider.contains("vodafone") || title.contains("vodafone")
    }

    private fun isExpired(esim: MobileEsim): Boolean {
        if (esim.status?.equals("expired", ignoreCase = true) == true) return true
        val expiresAt = esim.expiresAt ?: return false
        return runCatching { OffsetDateTime.parse(expiresAt).isBefore(OffsetDateTime.now()) }.getOrDefault(false)
    }

    private fun vodafoneRenewalUrl(): String =
        "${BuildConfig.ROAM2WORLD_API_BASE_URL.trimEnd('/')}/api/v1/mobile/airhub/vodafone/renew/"

    private companion object {
        const val EXTRA_RENEW_ICCID = "renew.iccid"
        const val EXTRA_ICCID = "iccid"
    }
}
