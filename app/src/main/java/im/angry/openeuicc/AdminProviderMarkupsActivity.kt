package im.angry.openeuicc

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.util.Log
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
private val MarkupBorder = Color(0xFFE1E8F2)
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
                    onUpdateDefault = { provider, markup ->
                        showProviderMarkupDialog(provider, markup) {
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

        val defaults = data.keys().asSequence().toList().sorted().map { provider ->
            ProviderDefaultUi(
                provider = provider,
                markup = data.optDouble(provider, 0.0)
            )
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

    private fun showProviderMarkupDialog(provider: String, currentMarkup: Double, onDone: () -> Unit) {
        val input = EditText(this)
        input.hint = "Markup percentage"
        input.setText(String.format("%.2f", currentMarkup))
        input.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        input.setSelectAllOnFocus(true)

        AlertDialog.Builder(this)
            .setTitle("Update ${provider.uppercase()} markup")
            .setMessage("Enter default markup percentage between 0 and 100.")
            .setView(input)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Update") { _, _ ->
                updateProviderMarkup(provider, input.text.toString().trim(), onDone)
            }
            .show()
    }

    private fun updateProviderMarkup(provider: String, markupValue: String, onDone: () -> Unit) {
        val normalizedValue = markupValue.replace(",", ".").trim()
        val parsed = normalizedValue.toDoubleOrNull()

        if (parsed == null || parsed < 0.0 || parsed > 100.0) {
            Toast.makeText(this, "Markup must be between 0 and 100", Toast.LENGTH_LONG).show()
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
                        markupValue = normalizedValue
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
        markupValue: String
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

            val body = "{\"markup_percentage\":$markupValue}"

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
            Log.d("AdminProviderMarkups", "SCOPE POST body=$body")

            connection.outputStream.use { output ->
                output.write(body.toByteArray(Charsets.UTF_8))
            }

            val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
            val responseBody = stream.bufferedReader().use { it.readText() }
            Log.d("AdminProviderMarkups", "SCOPE HTTP ${connection.responseCode}: $responseBody")

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
            Log.d("AdminProviderMarkups", "DELETE HTTP ${connection.responseCode}: $responseBody")

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
    val markup: Double
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
    onUpdateDefault: (String, Double) -> Unit,
    onDeleteRule: (Int) -> Unit,
    onBottomNavClick: (MarkupTab) -> Unit
) {
    val visibleDefaults by remember(providerDefaults, query, filter) {
        derivedStateOf {
            val cleanQuery = query.trim().lowercase()
            providerDefaults.filter { item ->
                val matchesQuery = cleanQuery.isBlank() || item.provider.lowercase().contains(cleanQuery)
                val matchesFilter = filter == "all" || filter == "defaults"
                matchesQuery && matchesFilter
            }
        }
    }

    val visibleRules by remember(scopeRules, query, filter) {
        derivedStateOf {
            val cleanQuery = query.trim().lowercase()
            scopeRules.filter { rule ->
                val searchable = listOf(
                    rule.id.toString(),
                    rule.provider,
                    rule.scopeType,
                    rule.scopeValue,
                    rule.resellerId,
                    rule.priority.toString(),
                    rule.markup.toString()
                ).joinToString(" ").lowercase()

                val matchesQuery = cleanQuery.isBlank() || searchable.contains(cleanQuery)
                val matchesFilter = when (filter) {
                    "all" -> true
                    "rules" -> true
                    "country" -> rule.scopeType.lowercase() == "country"
                    "region" -> rule.scopeType.lowercase() == "region"
                    "global" -> rule.scopeType.lowercase() == "global"
                    else -> false
                }

                matchesQuery && matchesFilter
            }
        }
    }

    Scaffold(
        containerColor = MarkupBg,
        bottomBar = {
            MarkupBottomNav(
                selected = MarkupTab.Pricing,
                onClick = onBottomNavClick
            )
        }
    ) { inner ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MarkupBg)
                .padding(inner)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(Modifier.height(8.dp)) }

            item {
                MarkupHero(
                    defaults = providerDefaults.size.toString(),
                    rules = scopeRules.size.toString()
                )
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MarkupMetricCard(
                        modifier = Modifier.weight(1f),
                        icon = R.drawable.admin_icon_tag,
                        label = "Defaults",
                        value = providerDefaults.size.toString(),
                        sub = "provider rules",
                        subColor = MarkupBlue
                    )
                    MarkupMetricCard(
                        modifier = Modifier.weight(1f),
                        icon = R.drawable.admin_icon_settings,
                        label = "Scope Rules",
                        value = scopeRules.size.toString(),
                        sub = "overrides",
                        subColor = MarkupOrange
                    )
                }
            }

            item {
                MarkupActionCard(
                    title = "Add Scope Rule",
                    subtitle = "Create or update country, region, global, or reseller-specific markup",
                    onClick = onAddScopeRule
                )
            }

            item {
                MarkupFilterCard(
                    query = query,
                    filter = filter,
                    loading = loading,
                    onQueryChange = onQueryChange,
                    onFilterChange = onFilterChange,
                    onRefresh = onRefresh
                )
            }

            if (loading) {
                item {
                    MarkupInfoCard("Loading provider markups", "Fetching provider defaults and scope rules.") {
                        CircularProgressIndicator(color = MarkupBlue)
                    }
                }
            }

            if (!loading && errorMessage != null) {
                item {
                    MarkupInfoCard("Provider markups unavailable", errorMessage)
                }
            }

            if (!loading && errorMessage == null && visibleDefaults.isEmpty() && visibleRules.isEmpty()) {
                item {
                    MarkupInfoCard("No Matching Markups", "Clear filters or search provider, scope, reseller id, priority, or markup value.")
                }
            }

            if (!loading && visibleDefaults.isNotEmpty()) {
                item {
                    MarkupSectionTitle("Provider Defaults")
                }

                items(visibleDefaults.size) { index ->
                    ProviderDefaultCard(
                        item = visibleDefaults[index],
                        onUpdate = { onUpdateDefault(visibleDefaults[index].provider, visibleDefaults[index].markup) }
                    )
                }
            }

            if (!loading && visibleRules.isNotEmpty()) {
                item {
                    MarkupSectionTitle("Scope Rules")
                }

                items(visibleRules.size) { index ->
                    ScopeRuleCard(
                        item = visibleRules[index],
                        onDelete = { onDeleteRule(visibleRules[index].id) }
                    )
                }
            }

            item { Spacer(Modifier.height(10.dp)) }
        }
    }
}

@Composable
private fun MarkupHero(defaults: String, rules: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MarkupNavy),
        elevation = CardDefaults.cardElevation(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .background(Brush.horizontalGradient(listOf(MarkupNavy, MarkupNavy2)))
                .padding(18.dp)
        ) {
            Column {
                Text("Provider Markups", color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(6.dp))
                Text(
                    "$defaults provider default(s), $rules scope rule(s)",
                    color = Color.White.copy(alpha = 0.72f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    "Pricing control center",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .background(Color.White.copy(alpha = 0.14f), RoundedCornerShape(18.dp))
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                )
            }
        }
    }
}

@Composable
private fun MarkupMetricCard(
    modifier: Modifier,
    @DrawableRes icon: Int,
    label: String,
    value: String,
    sub: String,
    subColor: Color
) {
    Card(
        modifier = modifier.height(120.dp),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, MarkupBorder),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Image(painterResource(icon), contentDescription = null, modifier = Modifier.size(38.dp))
            Column {
                Text(label, color = MarkupMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(value, color = MarkupText, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                Text(sub, color = subColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun MarkupActionCard(title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, MarkupBorder),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.admin_icon_settings), contentDescription = null, modifier = Modifier.size(44.dp))
            Column(modifier = Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(title, color = MarkupText, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(4.dp))
                Text(subtitle, color = MarkupMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            Surface(color = MarkupGreen.copy(alpha = 0.12f), shape = RoundedCornerShape(16.dp)) {
                Text("Add", color = MarkupGreen, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp))
            }
        }
    }
}

@Composable
private fun MarkupFilterCard(
    query: String,
    filter: String,
    loading: Boolean,
    onQueryChange: (String) -> Unit,
    onFilterChange: (String) -> Unit,
    onRefresh: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, MarkupBorder),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                label = { Text("Search provider, scope, reseller id") },
                singleLine = true
            )

            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "all" to "All",
                    "defaults" to "Provider Defaults",
                    "rules" to "Scope Rules",
                    "country" to "Country",
                    "region" to "Region",
                    "global" to "Global"
                ).forEach { (value, label) ->
                    val selected = filter == value
                    AssistChip(
                        onClick = { onFilterChange(value) },
                        label = { Text(label, fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = if (selected) Color(0xFFEAF2FF) else Color.White,
                            labelColor = if (selected) MarkupBlue else MarkupMuted
                        ),
                        border = BorderStroke(1.dp, if (selected) MarkupBlue.copy(alpha = 0.35f) else MarkupBorder)
                    )
                }

                AssistChip(
                    onClick = onRefresh,
                    enabled = !loading,
                    label = { Text(if (loading) "Loading" else "Refresh", fontWeight = FontWeight.ExtraBold) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = Color(0xFFEAF2FF),
                        labelColor = MarkupBlue
                    ),
                    border = BorderStroke(1.dp, MarkupBlue.copy(alpha = 0.35f))
                )
            }
        }
    }
}

@Composable
private fun MarkupSectionTitle(title: String) {
    Text(
        title,
        color = MarkupText,
        fontSize = 18.sp,
        fontWeight = FontWeight.ExtraBold,
        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
    )
}

@Composable
private fun ProviderDefaultCard(item: ProviderDefaultUi, onUpdate: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, MarkupBorder),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(R.drawable.admin_icon_tag), contentDescription = null, modifier = Modifier.size(42.dp))
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.provider.uppercase(), color = MarkupText, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                    Text("Default markup: ${formatPercent(item.markup)}", color = MarkupMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                Surface(color = MarkupBlue.copy(alpha = 0.12f), shape = RoundedCornerShape(14.dp)) {
                    Text(formatPercent(item.markup), color = MarkupBlue, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                }
            }

            Text(
                "Applies when no country, region, reseller, or global scope rule overrides it.",
                color = MarkupMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )

            Button(
                onClick = onUpdate,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MarkupBlue)
            ) {
                Text("Update Default Markup", fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

@Composable
private fun ScopeRuleCard(item: ScopeRuleUi, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, MarkupBorder),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(R.drawable.admin_icon_settings), contentDescription = null, modifier = Modifier.size(42.dp))
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("#${item.id} · ${item.provider.uppercase()}", color = MarkupText, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                    Text("${item.scopeType.uppercase()} / ${item.scopeValue}", color = MarkupMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                Surface(color = MarkupOrange.copy(alpha = 0.12f), shape = RoundedCornerShape(14.dp)) {
                    Text(formatPercent(item.markup), color = MarkupOrange, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MarkupMiniStat(Modifier.weight(1f), "Reseller ID", item.resellerId)
                MarkupMiniStat(Modifier.weight(1f), "Priority", item.priority.toString())
            }

            Button(
                onClick = onDelete,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MarkupRed)
            ) {
                Text("Delete Rule", fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

@Composable
private fun MarkupMiniStat(modifier: Modifier, label: String, value: String) {
    Surface(
        modifier = modifier,
        color = Color(0xFFF8FAFC),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MarkupBorder)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(label, color = MarkupMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text(value.ifBlank { "-" }, color = MarkupText, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        }
    }
}

@Composable
private fun MarkupInfoCard(title: String, message: String, content: @Composable (() -> Unit)? = null) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, MarkupBorder),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, color = MarkupText, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            Text(message, color = MarkupMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            content?.invoke()
        }
    }
}

@Composable
private fun MarkupBottomNav(selected: MarkupTab, onClick: (MarkupTab) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
        color = Color.White,
        shadowElevation = 14.dp,
        border = BorderStroke(1.dp, MarkupBorder)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(66.dp).padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            MarkupBottomItem(Icons.Default.GridView, "Dashboard", selected == MarkupTab.Dashboard) { onClick(MarkupTab.Dashboard) }
            MarkupBottomItem(Icons.Default.People, "Partners", selected == MarkupTab.Partners) { onClick(MarkupTab.Partners) }
            MarkupBottomItem(Icons.Default.ShoppingCart, "Orders", selected == MarkupTab.Orders) { onClick(MarkupTab.Orders) }
            MarkupBottomItem(Icons.Default.CreditCard, "Pricing", selected == MarkupTab.Pricing) { onClick(MarkupTab.Pricing) }
            MarkupBottomItem(Icons.Default.MoreHoriz, "More", selected == MarkupTab.More) { onClick(MarkupTab.More) }
        }
    }
}

@Composable
private fun MarkupBottomItem(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    val color = if (selected) MarkupBlue else MarkupMuted
    val bg = if (selected) Color(0xFFEAF2FF) else Color.Transparent

    Column(
        modifier = Modifier
            .size(width = 74.dp, height = 54.dp)
            .background(bg, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(21.dp))
        Spacer(Modifier.height(3.dp))
        Text(label, color = color, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
    }
}

private fun formatPercent(value: Double): String {
    return String.format("%.2f%%", value)
}
