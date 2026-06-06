package im.angry.openeuicc.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
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

    private lateinit var scroll: View
    private lateinit var selectedPackage: TextView
    private lateinit var iccidLayout: TextInputLayout
    private lateinit var customerNameLayout: TextInputLayout
    private lateinit var customerPhoneLayout: TextInputLayout
    private lateinit var iccid: TextInputEditText
    private lateinit var customerName: TextInputEditText
    private lateinit var customerPhone: TextInputEditText
    private lateinit var activate: MaterialButton

    private var selectedPackageName = "10GB / 30 Days"

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tgt_sim_recharge)
        setSupportActionBar(requireViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.r2w_tgt_recharge)

        scroll = requireNestedScrollView()
        selectedPackage = requireViewById(R.id.tgt_selected_package)
        iccidLayout = requireViewById(R.id.tgt_iccid_layout)
        customerNameLayout = requireViewById(R.id.tgt_customer_name_layout)
        customerPhoneLayout = requireViewById(R.id.tgt_customer_phone_layout)
        iccid = requireViewById(R.id.tgt_iccid)
        customerName = requireViewById(R.id.tgt_customer_name)
        customerPhone = requireViewById(R.id.tgt_customer_phone)
        activate = requireViewById(R.id.tgt_activate)

        setupInsets()
        setupPackageSelection()
        setupActivation()
        renderSelectedPackage()
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
