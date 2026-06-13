package im.angry.openeuicc.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
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
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt

class TgtCheckGbActivity : AppCompatActivity() {

    private fun formatTgtDate(value: String): String {
        if (value.isBlank()) return ""
        return try {
            val formatter = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm", Locale.ENGLISH)
            Instant.parse(value).atZone(ZoneId.systemDefault()).format(formatter)
        } catch (_: Exception) {
            value
        }
    }

    private fun formatTgtStatus(value: String): String {
        return when (value.uppercase(Locale.ROOT)) {
            "INUSE" -> "In Use"
            "ACTIVATED" -> "Activated"
            "NOTACTIVE" -> "Not Activated"
            "USED" -> "Used Up"
            "EXPIRED" -> "Expired"
            "ABANDON" -> "Abandoned"
            "TERMINATION" -> "Terminated"
            else -> value.ifBlank { "Unknown" }
        }
    }


    private val tokenStore by lazy { AuthTokenStore(this) }

    private lateinit var scroll: View
    private lateinit var iccidLayout: TextInputLayout
    private lateinit var iccidInput: TextInputEditText
    private lateinit var checkButton: MaterialButton
    private lateinit var result: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tgt_check_gb)
        setSupportActionBar(requireViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Orange Check GB"

        scroll = requireViewById(R.id.tgt_check_gb_scroll)
        iccidLayout = requireViewById(R.id.tgt_check_gb_iccid_layout)
        iccidInput = requireViewById(R.id.tgt_check_gb_iccid)
        checkButton = requireViewById(R.id.tgt_check_gb_button)
        result = requireViewById(R.id.tgt_check_gb_result)

        setupInsets()
        checkButton.setOnClickListener { checkGb() }
        findViewById<MaterialButton>(R.id.tgt_usage_topup).setOnClickListener {
            startActivity(Intent(this, TgtSimRechargeActivity::class.java).apply {
                putExtra("renew.iccid", iccidInput.text?.toString()?.trim().orEmpty())
            })
        }
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

    private fun checkGb() {
        iccidLayout.error = null
        val iccid = iccidInput.text?.toString()?.trim().orEmpty()
        if (iccid.length < 6) {
            iccidLayout.error = "Enter ICCID"
            return
        }

        lifecycleScope.launch {
            val session = withContext(Dispatchers.IO) { tokenStore.getSession() }
            if (session == null) {
                startActivity(
                    Intent(this@TgtCheckGbActivity, LoginActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    }
                )
                finish()
                return@launch
            }

            setLoading(true)
            val response = runCatching {
                withContext(Dispatchers.IO) { postUsage(session, iccid) }
            }
            setLoading(false)

            response
                .onSuccess { renderUsage(it) }
                .onFailure {
                    Toast.makeText(this@TgtCheckGbActivity, it.message ?: "Orange Check GB failed", Toast.LENGTH_LONG).show()
                    result.text = it.message ?: "Orange Check GB failed"
                }
        }
    }

    private fun postUsage(session: AuthSession, iccid: String): JSONObject {
        val url = URL("${BuildConfig.ROAM2WORLD_API_BASE_URL.trimEnd('/')}/api/v1/mobile/tgt/check-gb/")
        val body = JSONObject().put("iccid", iccid).toString()

        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20000
            readTimeout = 20000
            doOutput = true
            setRequestProperty("Authorization", session.authorizationHeader)
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
        }

        connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

        val statusCode = connection.responseCode
        val responseText = if (statusCode in 200..299) {
            connection.inputStream.bufferedReader().use { it.readText() }
        } else {
            connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
        }

        val json = JSONObject(responseText.ifBlank { "{}" })
        if (statusCode !in 200..299 || !json.optBoolean("success", false)) {
            throw IllegalStateException(json.optString("error").ifBlank { "Orange usage check failed" })
        }
        return json
    }

    private fun renderUsage(response: JSONObject) {
        val usage = response.optJSONObject("usage") ?: JSONObject()
        val raw = response.optJSONObject("raw") ?: JSONObject()
        val rawData = raw.optJSONObject("data") ?: JSONObject()

        val totalMb = firstNumber(usage, rawData, "total_mb", "totalMb", "dataTotal")
        val usedMb = firstNumber(usage, rawData, "used_mb", "usedMb", "dataUsage")
        val remainingMb = firstNumber(usage, rawData, "remaining_mb", "remainingMb", "dataResidual")

        val totalText = totalMb?.let { formatGb(it) } ?: "-- GB"
        val usedText = usedMb?.let { formatGb(it) } ?: "-- GB"
        val remainingText = remainingMb?.let { formatGb(it) } ?: "-- GB"

        val percent = if (totalMb != null && usedMb != null && totalMb > 0) {
            ((usedMb / totalMb) * 100).roundToInt().coerceIn(0, 100)
        } else {
            0
        }

        val statusRaw = usage.optString("status").ifBlank { raw.optString("status") }
        val statusText = formatTgtStatus(statusRaw.ifBlank { "ACTIVE" })

        val startDate = firstNotBlank(
            usage.optString("start_date"),
            usage.optString("startDate"),
            rawData.optString("startTime"),
            rawData.optString("activated_time")
        )?.let { formatTgtDate(it) } ?: "—"

        val endDate = firstNotBlank(
            usage.optString("end_date"),
            usage.optString("endDate"),
            rawData.optString("endTime"),
            rawData.optString("expiredTime")
        )?.let { formatTgtDate(it) } ?: "—"

        val packageName = firstNotBlank(
            usage.optString("package_name"),
            usage.optString("packageName"),
            rawData.optString("productName"),
            rawData.optString("packageName")
        ) ?: "Orange Package"

        val iccid = response.optString("iccid").ifBlank { iccidInput.text?.toString()?.trim().orEmpty() }

        findViewById<com.google.android.material.progressindicator.LinearProgressIndicator>(R.id.tgt_usage_progress).progress = percent
        findViewById<TextView>(R.id.tgt_usage_remaining).text = remainingText
        findViewById<TextView>(R.id.tgt_usage_total).text = "of $totalText"
        findViewById<TextView>(R.id.tgt_usage_percent).text = "$percent% Used"
        findViewById<TextView>(R.id.tgt_usage_package).text = packageName
        findViewById<TextView>(R.id.tgt_usage_iccid).text = iccid.ifBlank { "ICCID unavailable" }
        findViewById<TextView>(R.id.tgt_usage_allowance).text = totalText
        findViewById<TextView>(R.id.tgt_usage_activated).text = startDate
        findViewById<TextView>(R.id.tgt_usage_expires).text = endDate
        findViewById<TextView>(R.id.tgt_usage_status).apply {
            text = statusText
            if (statusText.contains("expired", ignoreCase = true)) {
                setBackgroundResource(R.drawable.r2w_customer_expired_badge)
                setTextColor(android.graphics.Color.parseColor("#DC2626"))
            } else {
                setBackgroundResource(R.drawable.r2w_customer_active_badge)
                setTextColor(android.graphics.Color.parseColor("#168653"))
            }
        }

        result.visibility = View.GONE
        result.text = listOf(
            "Total: $totalText",
            "Used: $usedText",
            "Remaining: $remainingText",
            "Status: $statusText"
        ).joinToString("\n")
    }


    private fun firstNotBlank(vararg values: String?): String? =
        values.firstOrNull { !it.isNullOrBlank() && it != "null" }

    private fun firstNumber(primary: JSONObject, secondary: JSONObject, vararg keys: String): Double? {
        for (key in keys) {
            val p = primary.opt(key)
            val pValue = numberValue(p)
            if (pValue != null) return pValue

            val s = secondary.opt(key)
            val sValue = numberValue(s)
            if (sValue != null) return sValue
        }
        return null
    }

    private fun numberValue(value: Any?): Double? {
        return when (value) {
            is Number -> value.toDouble()
            is String -> value.trim().replace(",", ".").toDoubleOrNull()
            else -> null
        }
    }

    private fun formatGb(mb: Double): String {
        val gb = mb / 1024.0
        val rounded = (gb * 100).roundToInt() / 100.0
        return "$rounded GB"
    }

    private fun setLoading(loading: Boolean) {
        checkButton.isEnabled = !loading
        checkButton.text = if (loading) "Checking..." else "Check GB"
    }
}
