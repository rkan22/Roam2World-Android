package im.angry.openeuicc

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import im.angry.openeuicc.auth.AuthTokenStore
import im.angry.openeuicc.auth.JwtUtils
import im.angry.openeuicc.ui.LoginActivity
import androidx.compose.material3.OutlinedTextFieldDefaults
import im.angry.openeuicc.ui.compose.saas.R2wMetricCard
import im.angry.openeuicc.ui.compose.saas.R2wSaasBottomNav
import im.angry.openeuicc.ui.compose.saas.R2wSaasCard
import im.angry.openeuicc.ui.compose.saas.R2wSaasColors
import im.angry.openeuicc.ui.compose.saas.R2wSaasHeader
import im.angry.openeuicc.ui.compose.saas.R2wSaasNavItem
import im.angry.openeuicc.ui.compose.theme.R2WTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private val MarkupBg = Color(0xFFF6F8FC)
private val MarkupNavy = Color(0xFF061A3F)
private val MarkupNavy2 = Color(0xFF123EAD)
private val MarkupBlue = Color(0xFF1263F1)
private val MarkupText = Color(0xFF101828)
private val MarkupMuted = Color(0xFF667085)
private val MarkupBorder = Color(0xFFE2E8F0)
private val MarkupGreen = Color(0xFF16A34A)
private val MarkupOrange = Color(0xFFF97316)
private val MarkupRed = Color(0xFFEF4444)

class AdminProviderMarkupsActivity : ComponentActivity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val tokenStore by lazy { AuthTokenStore(this) }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val composeScope = rememberCoroutineScope()
            var loading by remember { mutableStateOf(true) }
            var errorMessage by remember { mutableStateOf<String?>(null) }
            var providerDefaults by remember { mutableStateOf<List<ProviderDefaultUi>>(emptyList()) }
            var scopeRules by remember { mutableStateOf<List<ScopeRuleUi>>(emptyList()) }
            var query by remember { mutableStateOf("") }
            var filter by remember { mutableStateOf("all") }

            suspend fun loadProviderMarkups() {
                loading = true
                errorMessage = null

                val session = withContext(Dispatchers.IO) { tokenStore.getSession() }

                if (session == null || JwtUtils.isExpired(session.accessToken)) {
                    redirectToLogin()
                    loading = false
                    return
                }

                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        val response = fetchProviderMarkups(session.authorizationHeader)
                        parseProviderMarkups(response)
                    }
                }

                result
                    .onSuccess {
                        providerDefaults = it.defaults
                        scopeRules = it.rules
                    }
                    .onFailure {
                        errorMessage = it.message ?: "Provider markup API error"
                        Toast.makeText(this@AdminProviderMarkupsActivity, errorMessage, Toast.LENGTH_LONG).show()
                    }

                loading = false
            }

            LaunchedEffect(Unit) {
                loadProviderMarkups()
            }

            R2WTheme {
                AdminProviderMarkupsScreen(
                    loading = loading,
                    errorMessage = errorMessage,
                    providerDefaults = providerDefaults,
                    scopeRules = scopeRules,
                    query = query,
                    filter = filter,
                    onQueryChange = { query = it },
                    onFilterChange = { filter = it },
                    onRefresh = {
                        composeScope.launch {
                            loadProviderMarkups()
                        }
                    },
                    onAddScopeRule = {
                        showScopeRuleDialog {
                            composeScope.launch {
                                loadProviderMarkups()
                            }
                        }
                    },
                    onUpdateDefault = { provider, resellerMarkup, dealerMarkup ->
                        showProviderMarkupDialog(provider, resellerMarkup, dealerMarkup) {
                            composeScope.launch {
                                loadProviderMarkups()
                            }
                        }
                    },
                    onDeleteRule = { ruleId ->
                        confirmDeleteScopeRule(ruleId) {
                            composeScope.launch {
                                loadProviderMarkups()
                            }
                        }
                    },
                    onBottomNavClick = { tab ->
                        when (tab) {
                            MarkupTab.Dashboard -> startActivity(Intent(this@AdminProviderMarkupsActivity, MobileAdminActivity::class.java))
                            MarkupTab.Partners -> startActivity(Intent(this@AdminProviderMarkupsActivity, AdminPartnersActivity::class.java))
                            MarkupTab.Orders -> startActivity(Intent(this@AdminProviderMarkupsActivity, AdminOrdersOverviewActivity::class.java))
                            MarkupTab.Pricing -> startActivity(Intent(this@AdminProviderMarkupsActivity, AdminPricingActivity::class.java))
                            MarkupTab.More -> startActivity(Intent(this@AdminProviderMarkupsActivity, AdminMoreActivity::class.java))
                        }
                    }
                )
            }
        }
    }

    private fun fetchProviderMarkups(authorizationHeader: String): JSONObject {
        val url = URL("https://roam2world-panels-backend.onrender.com/api/v1/provider-markups/")
        val connection = url.openConnection() as HttpURLConnection

        return try {
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Authorization", authorizationHeader)
            connection.connectTimeout = 15000
            connection.readTimeout = 15000

            val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
            val body = stream.bufferedReader().use { it.readText() }

            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("HTTP ${connection.responseCode}: $body")
            }

            JSONObject(body)
        } finally {
            connection.disconnect()
        }
    }

    private fun parseProviderMarkups(response: JSONObject): ProviderMarkupResponseUi {
        val data = response.optJSONObject("data") ?: JSONObject()
        val rules = response.optJSONArray("rules") ?: JSONArray()

        val defaults = mutableListOf<ProviderDefaultUi>()

        val providerDefaultsArray = response.optJSONObject("data")
            ?.optJSONArray("provider_markups")
            ?: response.optJSONArray("data")

        if (providerDefaultsArray != null) {
            for (i in 0 until providerDefaultsArray.length()) {
                val item = providerDefaultsArray.optJSONObject(i) ?: continue
                val resellerMarkup = item.optDouble(
                    "reseller_markup_percentage",
                    item.optDouble("markup_percentage", 0.0)
                )
                defaults.add(
                    ProviderDefaultUi(
                        provider = item.optString("provider", "-"),
                        markup = resellerMarkup,
                        dealerMarkup = item.optDouble("dealer_markup_percentage", resellerMarkup)
                    )
                )
            }
        } else {
            data.keys().asSequence().toList().sorted().forEach { provider ->
                val rawValue = data.opt(provider)
                if (rawValue is JSONObject) {
                    val resellerMarkup = rawValue.optDouble(
                        "reseller_markup_percentage",
                        rawValue.optDouble("markup_percentage", 0.0)
                    )
                    defaults.add(
                        ProviderDefaultUi(
                            provider = provider,
                            markup = resellerMarkup,
                            dealerMarkup = rawValue.optDouble("dealer_markup_percentage", resellerMarkup)
                        )
                    )
                } else {
                    val resellerMarkup = data.optDouble(provider, 0.0)
                    defaults.add(
                        ProviderDefaultUi(
                            provider = provider,
                            markup = resellerMarkup,
                            dealerMarkup = resellerMarkup
                        )
                    )
                }
            }
        }

        val parsedRules = mutableListOf<ScopeRuleUi>()
        for (i in 0 until rules.length()) {
            val rule = rules.optJSONObject(i) ?: continue
            parsedRules.add(
                ScopeRuleUi(
                    id = rule.optInt("id", 0),
                    provider = rule.optString("provider", "-"),
                    scopeType = rule.optString("scope_type", "-"),
                    scopeValue = rule.optString("scope_value", "-"),
                    resellerId = if (rule.isNull("reseller_id")) "-" else rule.optString("reseller_id", "-"),
                    priority = rule.optInt("priority", 0),
                    markup = rule.optDouble("markup_percentage", 0.0)
                )
            )
        }

        return ProviderMarkupResponseUi(defaults = defaults, rules = parsedRules)
    }

    private fun showProviderMarkupDialog(
        provider: String,
        currentResellerMarkup: Double,
        currentDealerMarkup: Double,
        onDone: () -> Unit
    ) {
        val form = LinearLayout(this)
        form.orientation = LinearLayout.VERTICAL
        form.setPadding(32, 16, 32, 0)

        val resellerInput = EditText(this)
        resellerInput.hint = "Reseller markup percentage"
        resellerInput.setText(String.format("%.2f", currentResellerMarkup))
        resellerInput.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        resellerInput.setSelectAllOnFocus(true)
        form.addView(resellerInput)

        val dealerInput = EditText(this)
        dealerInput.hint = "Dealer markup percentage"
        dealerInput.setText(String.format("%.2f", currentDealerMarkup))
        dealerInput.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        dealerInput.setSelectAllOnFocus(true)
        form.addView(dealerInput)

        AlertDialog.Builder(this)
            .setTitle("Update ${provider.uppercase()} markup")
            .setMessage("Enter reseller and dealer default markup percentages between 0 and 100.")
            .setView(form)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Update") { _, _ ->
                updateProviderMarkup(
                    provider = provider,
                    resellerMarkupValue = resellerInput.text.toString().trim(),
                    dealerMarkupValue = dealerInput.text.toString().trim(),
                    onDone = onDone
                )
            }
            .show()
    }

    private fun updateProviderMarkup(
        provider: String,
        resellerMarkupValue: String,
        dealerMarkupValue: String,
        onDone: () -> Unit
    ) {
        val normalizedResellerValue = resellerMarkupValue.replace(",", ".").trim()
        val normalizedDealerValue = dealerMarkupValue.replace(",", ".").trim()

        val parsedReseller = normalizedResellerValue.toDoubleOrNull()
        val parsedDealer = normalizedDealerValue.toDoubleOrNull()

        if (parsedReseller == null || parsedReseller < 0.0 || parsedReseller > 100.0) {
            Toast.makeText(this, "Reseller markup must be between 0 and 100", Toast.LENGTH_LONG).show()
            return
        }

        if (parsedDealer == null || parsedDealer < 0.0 || parsedDealer > 100.0) {
            Toast.makeText(this, "Dealer markup must be between 0 and 100", Toast.LENGTH_LONG).show()
            return
        }

        scope.launch {
            val session = withContext(Dispatchers.IO) { tokenStore.getSession() }

            if (session == null || JwtUtils.isExpired(session.accessToken)) {
                redirectToLogin()
                return@launch
            }

            val result = withContext(Dispatchers.IO) {
                runCatching {
                    sendProviderMarkupUpdate(
                        authorizationHeader = session.authorizationHeader,
                        provider = provider,
                        resellerMarkupValue = normalizedResellerValue,
                        dealerMarkupValue = normalizedDealerValue
                    )
                }
            }

            result.onSuccess {
                Toast.makeText(this@AdminProviderMarkupsActivity, "${provider.uppercase()} markup updated", Toast.LENGTH_SHORT).show()
                onDone()
            }.onFailure { error ->
                Toast.makeText(this@AdminProviderMarkupsActivity, error.message ?: "Provider markup update failed", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun sendProviderMarkupUpdate(
        authorizationHeader: String,
        provider: String,
        resellerMarkupValue: String,
        dealerMarkupValue: String
    ): JSONObject {
        val url = URL("https://roam2world-panels-backend.onrender.com/api/v1/provider-markups/$provider/")
        val connection = url.openConnection() as HttpURLConnection

        return try {
            connection.requestMethod = "PUT"
            connection.doOutput = true
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", authorizationHeader)
            connection.connectTimeout = 15000
            connection.readTimeout = 15000

            val body = JSONObject()
                .put("markup_percentage", resellerMarkupValue.toDouble())
                .put("reseller_markup_percentage", resellerMarkupValue.toDouble())
                .put("dealer_markup_percentage", dealerMarkupValue.toDouble())
                .toString()

            connection.outputStream.use { output ->
                output.write(body.toByteArray(Charsets.UTF_8))
            }

            val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
            val responseBody = stream.bufferedReader().use { it.readText() }
            Log.d("DEFAULT_MARKUP_SAVE", "provider=$provider body=$body")
            Log.d("DEFAULT_MARKUP_SAVE", "HTTP ${connection.responseCode}: $responseBody")

            if (connection.responseCode !in 200..299) {
                val apiError = runCatching {
                    JSONObject(responseBody).optString("error", responseBody)
                }.getOrDefault(responseBody)

                throw IllegalStateException("HTTP ${connection.responseCode}: $apiError")
            }

            val json = JSONObject(responseBody)
            if (!json.optBoolean("success", true)) {
                throw IllegalStateException(json.optString("error", "Provider markup update failed"))
            }

            json
        } finally {
            connection.disconnect()
        }
    }

    private fun showScopeRuleDialog(onDone: () -> Unit) {
        val form = LinearLayout(this)
        form.orientation = LinearLayout.VERTICAL
        form.setPadding(32, 12, 32, 0)

        val providerInput = EditText(this)
        providerInput.hint = "Provider: airhub, esimcard, flexnet, tgt, traveroam"
        providerInput.inputType = InputType.TYPE_CLASS_TEXT
        form.addView(providerInput)

        val scopeTypeInput = EditText(this)
        scopeTypeInput.hint = "Scope type: country, region, global"
        scopeTypeInput.inputType = InputType.TYPE_CLASS_TEXT
        form.addView(scopeTypeInput)

        val scopeValueInput = EditText(this)
        scopeValueInput.hint = "Scope value: TR, Europe, global"
        scopeValueInput.inputType = InputType.TYPE_CLASS_TEXT
        form.addView(scopeValueInput)

        val markupInput = EditText(this)
        markupInput.hint = "Markup percentage: 5"
        markupInput.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        form.addView(markupInput)

        val resellerInput = EditText(this)
        resellerInput.hint = "Reseller ID optional"
        resellerInput.inputType = InputType.TYPE_CLASS_NUMBER
        form.addView(resellerInput)

        val priorityInput = EditText(this)
        priorityInput.hint = "Priority optional, default 0"
        priorityInput.inputType = InputType.TYPE_CLASS_NUMBER
        form.addView(priorityInput)

        AlertDialog.Builder(this)
            .setTitle("Add / Update Scope Rule")
            .setMessage("Creates or updates a provider markup rule by provider + scope + reseller.")
            .setView(form)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                createOrUpdateScopeRule(
                    provider = providerInput.text.toString(),
                    scopeType = scopeTypeInput.text.toString(),
                    scopeValue = scopeValueInput.text.toString(),
                    markupValue = markupInput.text.toString(),
                    resellerIdValue = resellerInput.text.toString(),
                    priorityValue = priorityInput.text.toString(),
                    onDone = onDone
                )
            }
            .show()
    }

    private fun createOrUpdateScopeRule(
        provider: String,
        scopeType: String,
        scopeValue: String,
        markupValue: String,
        resellerIdValue: String,
        priorityValue: String,
        onDone: () -> Unit
    ) {
        val normalizedProvider = provider.trim().lowercase()
        val normalizedScopeType = scopeType.trim().lowercase()
        val normalizedScopeValue = scopeValue.trim().ifBlank {
            if (normalizedScopeType == "global") "global" else ""
        }
        val normalizedMarkup = markupValue.replace(",", ".").trim()
        val parsedMarkup = normalizedMarkup.toDoubleOrNull()
        val parsedResellerId = resellerIdValue.trim().takeIf { it.isNotEmpty() }?.toIntOrNull()
        val parsedPriority = priorityValue.trim().takeIf { it.isNotEmpty() }?.toIntOrNull() ?: 0

        val allowedProviders = setOf("airhub", "esimcard", "flexnet", "tgt", "traveroam")
        val allowedScopeTypes = setOf("country", "region", "global")

        if (normalizedProvider !in allowedProviders) {
            Toast.makeText(this, "Invalid provider", Toast.LENGTH_LONG).show()
            return
        }

        if (normalizedScopeType !in allowedScopeTypes) {
            Toast.makeText(this, "Invalid scope type", Toast.LENGTH_LONG).show()
            return
        }

        if (normalizedScopeValue.isEmpty()) {
            Toast.makeText(this, "Scope value is required", Toast.LENGTH_LONG).show()
            return
        }

        if (parsedMarkup == null || parsedMarkup < 0.0 || parsedMarkup > 100.0) {
            Toast.makeText(this, "Markup must be between 0 and 100", Toast.LENGTH_LONG).show()
            return
        }

        if (resellerIdValue.trim().isNotEmpty() && parsedResellerId == null) {
            Toast.makeText(this, "Reseller ID must be a number", Toast.LENGTH_LONG).show()
            return
        }

        scope.launch {
            val session = withContext(Dispatchers.IO) { tokenStore.getSession() }

            if (session == null || JwtUtils.isExpired(session.accessToken)) {
                redirectToLogin()
                return@launch
            }

            val result = withContext(Dispatchers.IO) {
                runCatching {
                    sendScopeRuleUpsert(
                        authorizationHeader = session.authorizationHeader,
                        provider = normalizedProvider,
                        scopeType = normalizedScopeType,
                        scopeValue = normalizedScopeValue,
                        markupValue = parsedMarkup,
                        resellerId = parsedResellerId,
                        priority = parsedPriority
                    )
                }
            }

            result.onSuccess {
                Toast.makeText(this@AdminProviderMarkupsActivity, "Scope rule saved", Toast.LENGTH_SHORT).show()
                onDone()
            }.onFailure { error ->
                Toast.makeText(this@AdminProviderMarkupsActivity, error.message ?: "Scope rule update failed", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun sendScopeRuleUpsert(
        authorizationHeader: String,
        provider: String,
        scopeType: String,
        scopeValue: String,
        markupValue: Double,
        resellerId: Int?,
        priority: Int
    ): JSONObject {
        val url = URL("https://roam2world-panels-backend.onrender.com/api/v1/provider-scope-markups/")
        val connection = url.openConnection() as HttpURLConnection

        return try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", authorizationHeader)
            connection.connectTimeout = 15000
            connection.readTimeout = 15000

            val bodyJson = JSONObject()
                .put("provider", provider)
                .put("scope_type", scopeType)
                .put("scope_value", scopeValue)
                .put("markup_percentage", markupValue)
                .put("priority", priority)

            if (resellerId != null) {
                bodyJson.put("reseller_id", resellerId)
            }

            val body = bodyJson.toString()

            connection.outputStream.use { output ->
                output.write(body.toByteArray(Charsets.UTF_8))
            }

            val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
            val responseBody = stream.bufferedReader().use { it.readText() }

            if (connection.responseCode !in 200..299) {
                val apiError = runCatching {
                    JSONObject(responseBody).optString("error", responseBody)
                }.getOrDefault(responseBody)

                throw IllegalStateException("HTTP ${connection.responseCode}: $apiError")
            }

            val json = JSONObject(responseBody)
            if (!json.optBoolean("success", true)) {
                throw IllegalStateException(json.optString("error", "Scope rule update failed"))
            }

            json
        } finally {
            connection.disconnect()
        }
    }

    private fun confirmDeleteScopeRule(ruleId: Int, onDone: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle("Delete scope rule")
            .setMessage("Rule #$ruleId will be disabled. This can affect provider pricing.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ ->
                deleteScopeRule(ruleId, onDone)
            }
            .show()
    }

    private fun deleteScopeRule(ruleId: Int, onDone: () -> Unit) {
        scope.launch {
            val session = withContext(Dispatchers.IO) { tokenStore.getSession() }

            if (session == null || JwtUtils.isExpired(session.accessToken)) {
                redirectToLogin()
                return@launch
            }

            val result = withContext(Dispatchers.IO) {
                runCatching {
                    sendScopeRuleDelete(
                        authorizationHeader = session.authorizationHeader,
                        ruleId = ruleId
                    )
                }
            }

            result.onSuccess {
                Toast.makeText(this@AdminProviderMarkupsActivity, "Scope rule deleted", Toast.LENGTH_SHORT).show()
                onDone()
            }.onFailure { error ->
                Toast.makeText(this@AdminProviderMarkupsActivity, error.message ?: "Scope rule delete failed", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun sendScopeRuleDelete(
        authorizationHeader: String,
        ruleId: Int
    ): JSONObject {
        val url = URL("https://roam2world-panels-backend.onrender.com/api/v1/provider-scope-markups/$ruleId/")
        val connection = url.openConnection() as HttpURLConnection

        return try {
            connection.requestMethod = "DELETE"
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Authorization", authorizationHeader)
            connection.connectTimeout = 15000
            connection.readTimeout = 15000

            val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
            val responseBody = stream.bufferedReader().use { it.readText() }

            if (connection.responseCode !in 200..299) {
                val apiError = runCatching {
                    JSONObject(responseBody).optString("error", responseBody)
                }.getOrDefault(responseBody)

                throw IllegalStateException("HTTP ${connection.responseCode}: $apiError")
            }

            val json = JSONObject(responseBody)
            if (!json.optBoolean("success", true)) {
                throw IllegalStateException(json.optString("error", "Scope rule delete failed"))
            }

            json
        } finally {
            connection.disconnect()
        }
    }

    private fun redirectToLogin() {
        startActivity(
            Intent(this, LoginActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
        )
        finish()
    }
}

private data class ProviderMarkupResponseUi(
    val defaults: List<ProviderDefaultUi>,
    val rules: List<ScopeRuleUi>
)

private data class ProviderDefaultUi(
    val provider: String,
    val markup: Double,
    val dealerMarkup: Double
)

private data class ScopeRuleUi(
    val id: Int,
    val provider: String,
    val scopeType: String,
    val scopeValue: String,
    val resellerId: String,
    val priority: Int,
    val markup: Double
)

private enum class MarkupTab {
    Dashboard,
    Partners,
    Orders,
    Pricing,
    More
}

@Composable
private fun AdminProviderMarkupsScreen(
    loading: Boolean,
    errorMessage: String?,
    providerDefaults: List<ProviderDefaultUi>,
    scopeRules: List<ScopeRuleUi>,
    query: String,
    filter: String,
    onQueryChange: (String) -> Unit,
    onFilterChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onAddScopeRule: () -> Unit,
    onUpdateDefault: (String, Double, Double) -> Unit,
    onDeleteRule: (Int) -> Unit,
    onBottomNavClick: (MarkupTab) -> Unit
) {
    val filteredDefaults by remember(providerDefaults, query, filter) {
        derivedStateOf {
            val q = query.trim().lowercase()
            providerDefaults.filter { item ->
                val matchesQuery = q.isBlank() ||
                    item.provider.lowercase().contains(q) ||
                    providerMarkupProviderLabel(item.provider).lowercase().contains(q)

                val matchesFilter = filter == "all" || item.provider.lowercase() == filter.lowercase()
                matchesQuery && matchesFilter
            }
        }
    }

    val providerKeys = remember(providerDefaults) {
        providerDefaults
            .map { it.provider }
            .filter { it.isNotBlank() && it != "-" }
            .distinctBy { it.lowercase() }
            .take(10)
    }

    Scaffold(
        containerColor = R2wSaasColors.Background,
        bottomBar = {
            R2wSaasBottomNav(
                selected = R2wSaasNavItem.Pricing,
                onClick = { item -> onBottomNavClick(item.toMarkupTab()) }
            )
        }
    ) { inner ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { Spacer(Modifier.height(8.dp)) }

            item {
                R2wSaasHeader(
                    title = "Provider Markups",
                    subtitle = "${filteredDefaults.size} providers with reseller and dealer margin rules.",
                    badge = if (loading) "Loading" else "Live API"
                )
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    R2wMetricCard(
                        modifier = Modifier.weight(1f),
                        title = "Providers",
                        value = providerDefaults.size.toString(),
                        subtitle = "default rules",
                        icon = Icons.Default.CreditCard,
                        tint = R2wSaasColors.Primary
                    )

                    R2wMetricCard(
                        modifier = Modifier.weight(1f),
                        title = "Scope Rules",
                        value = scopeRules.size.toString(),
                        subtitle = "custom overrides",
                        icon = Icons.Default.People,
                        tint = R2wSaasColors.Orange
                    )
                }
            }

            item {
                MarkupSearchAndFilters(
                    query = query,
                    filter = filter,
                    providers = providerKeys,
                    onQueryChange = onQueryChange,
                    onFilterChange = onFilterChange,
                    onRefresh = onRefresh,
                    onAddScopeRule = onAddScopeRule
                )
            }

            if (errorMessage != null) {
                item {
                    R2wSaasCard {
                        Text(
                            text = "Could not load provider markups",
                            color = R2wSaasColors.Red,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = errorMessage,
                            color = R2wSaasColors.Muted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            if (loading) {
                item {
                    R2wSaasCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(color = R2wSaasColors.Primary)
                            Text(
                                text = "Loading provider markups...",
                                color = R2wSaasColors.Muted,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(start = 12.dp)
                            )
                        }
                    }
                }
            } else if (filteredDefaults.isEmpty()) {
                item {
                    R2wSaasCard {
                        Text(
                            text = "No provider markups found",
                            color = R2wSaasColors.Text,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Try another provider filter or refresh the API.",
                            color = R2wSaasColors.Muted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            } else {
                items(filteredDefaults.size) { index ->
                    ProviderDefaultSaasCard(
                        item = filteredDefaults[index],
                        onUpdate = {
                            onUpdateDefault(
                                filteredDefaults[index].provider,
                                filteredDefaults[index].markup,
                                filteredDefaults[index].dealerMarkup
                            )
                        }
                    )
                }
            }

            if (scopeRules.isNotEmpty()) {
                item {
                    R2wSaasCard {
                        Text(
                            text = "Scope Rules",
                            color = R2wSaasColors.Text,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black
                        )

                        Spacer(Modifier.height(8.dp))

                        Text(
                            text = "Custom overrides for provider, region, country or reseller.",
                            color = R2wSaasColors.Muted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                items(scopeRules.take(20).size) { index ->
                    ScopeRuleSaasCard(
                        item = scopeRules[index],
                        onDelete = { onDeleteRule(scopeRules[index].id) }
                    )
                }
            }

            item { Spacer(Modifier.height(18.dp)) }
        }
    }
}

@Composable
private fun MarkupSearchAndFilters(
    query: String,
    filter: String,
    providers: List<String>,
    onQueryChange: (String) -> Unit,
    onFilterChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onAddScopeRule: () -> Unit
) {
    R2wSaasCard {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = R2wSaasColors.Muted
                )
            },
            placeholder = { Text("Search provider markup") },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = R2wSaasColors.Primary,
                unfocusedBorderColor = R2wSaasColors.Border,
                focusedContainerColor = R2wSaasColors.Card,
                unfocusedContainerColor = R2wSaasColors.Card,
                focusedTextColor = R2wSaasColors.Text,
                unfocusedTextColor = R2wSaasColors.Text,
                cursorColor = R2wSaasColors.Primary
            ),
            shape = RoundedCornerShape(18.dp)
        )

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MarkupFilterChip("all", "All Providers", filter, onFilterChange)

            providers.forEach { provider ->
                MarkupFilterChip(
                    key = provider,
                    label = providerMarkupProviderLabel(provider),
                    selected = filter,
                    onClick = onFilterChange
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onRefresh,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = R2wSaasColors.Primary),
                shape = RoundedCornerShape(18.dp)
            ) {
                Text(
                    text = "Refresh",
                    fontWeight = FontWeight.Black
                )
            }

            Button(
                onClick = onAddScopeRule,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = R2wSaasColors.Orange),
                shape = RoundedCornerShape(18.dp)
            ) {
                Text(
                    text = "Add Rule",
                    fontWeight = FontWeight.Black
                )
            }
        }
    }
}

@Composable
private fun MarkupFilterChip(
    key: String,
    label: String,
    selected: String,
    onClick: (String) -> Unit
) {
    val isSelected = selected.equals(key, ignoreCase = true)

    AssistChip(
        onClick = { onClick(key) },
        label = { Text(label, fontWeight = FontWeight.ExtraBold) },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = if (isSelected) R2wSaasColors.PrimarySoft else R2wSaasColors.Card,
            labelColor = if (isSelected) R2wSaasColors.Primary else R2wSaasColors.Muted
        ),
        border = BorderStroke(1.dp, R2wSaasColors.Border)
    )
}

@Composable
private fun ProviderDefaultSaasCard(
    item: ProviderDefaultUi,
    onUpdate: () -> Unit
) {
    R2wSaasCard(
        modifier = Modifier.clickable(onClick = onUpdate)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = providerMarkupTint(item.provider).copy(alpha = 0.10f),
                border = BorderStroke(1.dp, R2wSaasColors.Border)
            ) {
                Icon(
                    imageVector = Icons.Default.CreditCard,
                    contentDescription = null,
                    tint = providerMarkupTint(item.provider),
                    modifier = Modifier.padding(12.dp)
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = providerMarkupProviderLabel(item.provider),
                            color = R2wSaasColors.Text,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Text(
                            text = item.provider,
                            color = R2wSaasColors.Muted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    MarkupPill("Edit", R2wSaasColors.Primary)
                }

                Spacer(Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MarkupMiniStat(
                        title = "Reseller",
                        value = "${formatMarkup(item.markup)}%",
                        modifier = Modifier.weight(1f)
                    )

                    MarkupMiniStat(
                        title = "Dealer",
                        value = "${formatMarkup(item.dealerMarkup)}%",
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(Modifier.height(8.dp))

                Text(
                    text = "Tap to update reseller and dealer default markup.",
                    color = R2wSaasColors.Muted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun ScopeRuleSaasCard(
    item: ScopeRuleUi,
    onDelete: () -> Unit
) {
    R2wSaasCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = R2wSaasColors.Purple.copy(alpha = 0.10f),
                border = BorderStroke(1.dp, R2wSaasColors.Border)
            ) {
                Icon(
                    imageVector = Icons.Default.People,
                    contentDescription = null,
                    tint = R2wSaasColors.Purple,
                    modifier = Modifier.padding(12.dp)
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = providerMarkupProviderLabel(item.provider),
                            color = R2wSaasColors.Text,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Text(
                            text = "${item.scopeType}: ${item.scopeValue}",
                            color = R2wSaasColors.Muted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    MarkupPill("Delete", R2wSaasColors.Red)
                }

                Spacer(Modifier.height(10.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MarkupMiniStat(
                        title = "Markup",
                        value = "${formatMarkup(item.markup)}%",
                        modifier = Modifier.weight(1f)
                    )

                    MarkupMiniStat(
                        title = "Priority",
                        value = item.priority.toString(),
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(Modifier.height(8.dp))

                Button(
                    onClick = onDelete,
                    colors = ButtonDefaults.buttonColors(containerColor = R2wSaasColors.Red),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = "Delete Rule",
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }
    }
}

@Composable
private fun MarkupPill(
    text: String,
    color: Color
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.18f))
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
            maxLines = 1
        )
    }
}

@Composable
private fun MarkupMiniStat(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = R2wSaasColors.Background,
        border = BorderStroke(1.dp, R2wSaasColors.Border)
    ) {
        Column(modifier = Modifier.padding(9.dp)) {
            Text(
                text = title,
                color = R2wSaasColors.Muted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = value,
                color = R2wSaasColors.Text,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun R2wSaasNavItem.toMarkupTab(): MarkupTab =
    when (this) {
        R2wSaasNavItem.Dashboard -> MarkupTab.Dashboard
        R2wSaasNavItem.Partners -> MarkupTab.Partners
        R2wSaasNavItem.Orders -> MarkupTab.Orders
        R2wSaasNavItem.Pricing -> MarkupTab.Pricing
        R2wSaasNavItem.More -> MarkupTab.More
    }

private fun providerMarkupTint(provider: String): Color {
    return when (provider.lowercase().trim()) {
        "tgt" -> R2wSaasColors.Orange
        "esimcard", "orange" -> R2wSaasColors.Primary
        "airhub", "airhubapp", "vodafone" -> R2wSaasColors.Red
        "flexnet", "masmovil", "mas movil" -> R2wSaasColors.Purple
        "traveroam", "travelroam", "roam2world" -> R2wSaasColors.Green
        else -> R2wSaasColors.Primary
    }
}

private fun providerMarkupProviderLabel(provider: String): String {
    return when (provider.lowercase().trim()) {
        "traveroam", "travelroam", "roam2world" -> "TravelRoam"
        "tgt" -> "Orange Balkans"
        "flexnet", "masmovil", "mas movil" -> "Orange Big Data"
        "esimcard", "orange" -> "Orange World"
        "airhub", "airhubapp", "vodafone" -> "Vodafone"
        else -> provider.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }
}

private fun formatMarkup(value: Double): String =
    String.format("%.2f", value)
