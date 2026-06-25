package im.angry.openeuicc

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.Log
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import im.angry.openeuicc.auth.AuthTokenStore
import im.angry.openeuicc.auth.JwtUtils
import im.angry.openeuicc.ui.LoginActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class AdminProviderMarkupsActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val tokenStore by lazy { AuthTokenStore(this) }

    private lateinit var subtitleText: TextView
    private lateinit var refreshButton: Button
    private lateinit var addScopeRuleButton: Button
    private lateinit var searchInput: EditText
    private lateinit var filterContainer: LinearLayout
    private lateinit var listContainer: LinearLayout

    private var cachedProviderMarkups: JSONObject? = null
    private var currentSearchQuery: String = ""
    private var currentFilter: String = "all"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scrollView = ScrollView(this)
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(28, 32, 28, 32)
        root.setBackgroundColor(0xFFF6F8FC.toInt())

        val titleText = TextView(this)
        titleText.text = "Provider Markups"
        titleText.textSize = 24f
        titleText.setTextColor(0xFF07133D.toInt())
        titleText.setPadding(0, 0, 0, 8)
        root.addView(titleText)

        subtitleText = TextView(this)
        subtitleText.text = "Provider-based pricing control"
        subtitleText.textSize = 14f
        subtitleText.setTextColor(0xFF526070.toInt())
        subtitleText.setPadding(0, 0, 0, 20)
        root.addView(subtitleText)

        refreshButton = Button(this)
        refreshButton.text = "Refresh"
        refreshButton.setTextColor(0xFFFFFFFF.toInt())
        refreshButton.setBackgroundResource(R.drawable.admin_primary_pill)
        root.addView(refreshButton)

        addScopeRuleButton = Button(this)
        addScopeRuleButton.text = "Add Scope Rule"
        addScopeRuleButton.setTextColor(0xFFFFFFFF.toInt())
        addScopeRuleButton.setBackgroundResource(R.drawable.admin_success_pill)

        val addScopeRuleButtonParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        addScopeRuleButtonParams.setMargins(0, 16, 0, 0)
        addScopeRuleButton.layoutParams = addScopeRuleButtonParams
        root.addView(addScopeRuleButton)

        searchInput = EditText(this)
        searchInput.hint = "Search provider, scope, reseller id..."
        searchInput.textSize = 14f
        searchInput.setSingleLine(true)
        searchInput.setPadding(24, 18, 24, 18)

        val searchParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        searchParams.setMargins(0, 18, 0, 0)
        searchInput.layoutParams = searchParams
        root.addView(searchInput)

        filterContainer = LinearLayout(this)
        filterContainer.orientation = LinearLayout.VERTICAL
        filterContainer.setPadding(0, 16, 0, 0)
        root.addView(filterContainer)

        setupFilterButtons()

        listContainer = LinearLayout(this)
        listContainer.orientation = LinearLayout.VERTICAL
        listContainer.setPadding(0, 24, 0, 0)
        root.addView(listContainer)

        scrollView.addView(root)
        setContentView(scrollView)

        refreshButton.setOnClickListener { loadProviderMarkups() }
        addScopeRuleButton.setOnClickListener { showScopeRuleDialog() }

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                currentSearchQuery = s?.toString().orEmpty()
                renderCachedProviderMarkups()
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        })

        loadProviderMarkups()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun loadProviderMarkups() {
        subtitleText.text = "Loading provider markups..."
        listContainer.removeAllViews()
        addCard("⏳ Loading provider markups...\n\nFetching provider defaults and scope rules.")

        scope.launch {
            val session = withContext(Dispatchers.IO) { tokenStore.getSession() }

            if (session == null || JwtUtils.isExpired(session.accessToken)) {
                redirectToLogin()
                return@launch
            }

            val result = withContext(Dispatchers.IO) {
                runCatching { fetchProviderMarkups(session.authorizationHeader) }
            }

            result.onSuccess { response ->
                cachedProviderMarkups = response
                renderCachedProviderMarkups()
            }.onFailure { error ->
                subtitleText.text = "Provider markups unavailable"
                listContainer.removeAllViews()
                addCard("⚠️ Provider markups unavailable.\n\n${error.message ?: "API error"}")
                Toast.makeText(
                    this@AdminProviderMarkupsActivity,
                    error.message ?: "Provider markup API error",
                    Toast.LENGTH_LONG
                ).show()
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

            val stream = if (connection.responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }

            val body = stream.bufferedReader().use { it.readText() }

            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("HTTP ${connection.responseCode}: $body")
            }

            JSONObject(body)
        } finally {
            connection.disconnect()
        }
    }


    private fun setupFilterButtons() {
        filterContainer.removeAllViews()

        val filters = listOf(
            "all" to "All",
            "defaults" to "Provider Defaults",
            "rules" to "Scope Rules",
            "country" to "Country",
            "region" to "Region",
            "global" to "Global"
        )

        filters.forEach { (key, label) ->
            val button = Button(this)
            button.text = if (currentFilter == key) "✓ $label" else label
            button.textSize = 13f
            button.setTextColor(0xFFFFFFFF.toInt())
            button.setBackgroundResource(
                if (currentFilter == key) R.drawable.admin_success_pill else R.drawable.admin_primary_pill
            )

            val params = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(0, 0, 0, 10)
            button.layoutParams = params

            button.setOnClickListener {
                currentFilter = key
                setupFilterButtons()
                renderCachedProviderMarkups()
            }

            filterContainer.addView(button)
        }
    }

    private fun renderCachedProviderMarkups() {
        val response = cachedProviderMarkups ?: return
        renderProviderMarkups(response)
    }


    private fun renderProviderMarkups(response: JSONObject) {
        val data = response.optJSONObject("data") ?: JSONObject()
        val rules = response.optJSONArray("rules")
        val query = currentSearchQuery.trim().lowercase()

        listContainer.removeAllViews()

        val allProviderNames = data.keys().asSequence().toList().sorted()
        val visibleProviderNames = allProviderNames.filter { provider ->
            val searchable = provider.lowercase()
            val matchesQuery = query.isEmpty() || searchable.contains(query)
            val matchesFilter = currentFilter == "all" || currentFilter == "defaults"
            matchesQuery && matchesFilter
        }

        val visibleRules = mutableListOf<JSONObject>()
        if (rules != null) {
            for (i in 0 until rules.length()) {
                val rule = rules.optJSONObject(i) ?: continue
                val id = rule.optInt("id", 0)
                val provider = rule.optString("provider", "-")
                val scopeType = rule.optString("scope_type", "-")
                val scopeValue = rule.optString("scope_value", "-")
                val resellerId = if (rule.isNull("reseller_id")) "" else rule.optString("reseller_id", "")
                val priority = rule.optInt("priority", 0)
                val markup = rule.optDouble("markup_percentage", 0.0)

                val searchable = listOf(
                    id.toString(),
                    provider,
                    scopeType,
                    scopeValue,
                    resellerId,
                    priority.toString(),
                    markup.toString()
                ).joinToString(" ").lowercase()

                val matchesQuery = query.isEmpty() || searchable.contains(query)
                val matchesFilter = when (currentFilter) {
                    "all" -> true
                    "rules" -> true
                    "country" -> scopeType.lowercase() == "country"
                    "region" -> scopeType.lowercase() == "region"
                    "global" -> scopeType.lowercase() == "global"
                    else -> false
                }

                if (matchesQuery && matchesFilter) {
                    visibleRules.add(rule)
                }
            }
        }

        subtitleText.text =
            "${allProviderNames.size} provider default(s), ${rules?.length() ?: 0} scope rule(s)"

        val showDefaultsSection = currentFilter == "all" || currentFilter == "defaults"
        val showRulesSection = currentFilter != "defaults"

        if (showDefaultsSection) {
            addSectionTitle("Provider Defaults")

            if (visibleProviderNames.isEmpty()) {
                addCard("🔎 No provider defaults match the current search/filter.")
            } else {
                visibleProviderNames.forEach { provider ->
                    val markup = data.optDouble(provider, 0.0)
                    addProviderMarkupCard(provider, markup)
                }
            }
        }

        if (showRulesSection) {
            addSectionTitle("Scope Rules")

            if (visibleRules.isEmpty()) {
                addCard("🔎 No scope rules match the current search/filter.")
                return
            }

            visibleRules.forEach { rule ->
                val id = rule.optInt("id", 0)
                val provider = rule.optString("provider", "-")
                val scopeType = rule.optString("scope_type", "-")
                val scopeValue = rule.optString("scope_value", "-")
                val resellerId = if (rule.isNull("reseller_id")) "-" else rule.optString("reseller_id", "-")
                val priority = rule.optInt("priority", 0)
                val markup = rule.optDouble("markup_percentage", 0.0)

                addScopeRuleCard(
                    ruleId = id,
                    text = "#$id · ${provider.uppercase()}\n\n" +
                        "Scope: ${scopeType.uppercase()} / $scopeValue\n" +
                        "Reseller ID: $resellerId\n" +
                        "Priority: $priority\n" +
                        "Markup: ${formatPercent(markup)}"
                )
            }
        }
    }

    private fun addProviderMarkupCard(provider: String, markup: Double) {
        val card = LinearLayout(this)
        card.orientation = LinearLayout.VERTICAL
        card.setBackgroundResource(R.drawable.admin_card_background)
        card.elevation = 3f
        card.setPadding(28, 24, 28, 24)

        val body = TextView(this)
        body.text =
            "${provider.uppercase()}\n\n" +
                "Default markup: ${formatPercent(markup)}\n" +
                "Applies when no country, region, reseller, or global scope rule overrides it."
        body.textSize = 15.5f
        body.setTextColor(0xFF07133D.toInt())
        card.addView(body)

        val button = Button(this)
        button.text = "Update Default Markup"
        button.textSize = 14f
        button.setTextColor(0xFFFFFFFF.toInt())
        button.setBackgroundResource(R.drawable.admin_primary_pill)

        val buttonParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        buttonParams.setMargins(0, 20, 0, 0)
        button.layoutParams = buttonParams

        button.setOnClickListener {
            showProviderMarkupDialog(provider, markup)
        }

        card.addView(button)

        val params = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        params.setMargins(0, 0, 0, 20)
        card.layoutParams = params
        listContainer.addView(card)
    }

    private fun showProviderMarkupDialog(provider: String, currentMarkup: Double) {
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
                val value = input.text.toString().trim()
                updateProviderMarkup(provider, value)
            }
            .show()
    }

    private fun updateProviderMarkup(provider: String, markupValue: String) {
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
                Toast.makeText(
                    this@AdminProviderMarkupsActivity,
                    "${provider.uppercase()} markup updated",
                    Toast.LENGTH_SHORT
                ).show()
                loadProviderMarkups()
            }.onFailure { error ->
                Toast.makeText(
                    this@AdminProviderMarkupsActivity,
                    error.message ?: "Provider markup update failed",
                    Toast.LENGTH_LONG
                ).show()
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

            val stream = if (connection.responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }

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



    private fun showScopeRuleDialog() {
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
                    priorityValue = priorityInput.text.toString()
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
        priorityValue: String
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
                Toast.makeText(
                    this@AdminProviderMarkupsActivity,
                    "Scope rule saved",
                    Toast.LENGTH_SHORT
                ).show()
                loadProviderMarkups()
            }.onFailure { error ->
                Toast.makeText(
                    this@AdminProviderMarkupsActivity,
                    error.message ?: "Scope rule update failed",
                    Toast.LENGTH_LONG
                ).show()
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

            val stream = if (connection.responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }

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



    private fun addScopeRuleCard(ruleId: Int, text: String) {
        val card = LinearLayout(this)
        card.orientation = LinearLayout.VERTICAL
        card.setBackgroundResource(R.drawable.admin_card_background)
        card.elevation = 3f
        card.setPadding(28, 24, 28, 24)

        val body = TextView(this)
        body.text = text
        body.textSize = 15.5f
        body.setTextColor(0xFF07133D.toInt())
        card.addView(body)

        val deleteButton = Button(this)
        deleteButton.text = "Delete Rule"
        deleteButton.textSize = 14f
        deleteButton.setTextColor(0xFFFFFFFF.toInt())
        deleteButton.setBackgroundResource(R.drawable.admin_danger_pill)

        val buttonParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        buttonParams.setMargins(0, 20, 0, 0)
        deleteButton.layoutParams = buttonParams

        deleteButton.setOnClickListener {
            confirmDeleteScopeRule(ruleId)
        }

        card.addView(deleteButton)

        val params = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        params.setMargins(0, 0, 0, 20)
        card.layoutParams = params
        listContainer.addView(card)
    }

    private fun confirmDeleteScopeRule(ruleId: Int) {
        AlertDialog.Builder(this)
            .setTitle("Delete scope rule")
            .setMessage("Rule #$ruleId will be disabled. This can affect provider pricing.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ ->
                deleteScopeRule(ruleId)
            }
            .show()
    }

    private fun deleteScopeRule(ruleId: Int) {
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
                Toast.makeText(
                    this@AdminProviderMarkupsActivity,
                    "Scope rule deleted",
                    Toast.LENGTH_SHORT
                ).show()
                loadProviderMarkups()
            }.onFailure { error ->
                Toast.makeText(
                    this@AdminProviderMarkupsActivity,
                    error.message ?: "Scope rule delete failed",
                    Toast.LENGTH_LONG
                ).show()
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

            val stream = if (connection.responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }

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


    private fun addSectionTitle(text: String) {
        val title = TextView(this)
        title.text = text
        title.textSize = 18f
        title.setTextColor(0xFF07133D.toInt())
        title.setPadding(0, 18, 0, 12)
        listContainer.addView(title)
    }

    private fun addCard(text: String) {
        val card = TextView(this)
        card.text = text
        card.textSize = 15.5f
        card.setTextColor(0xFF07133D.toInt())
        card.setBackgroundResource(R.drawable.admin_card_background)
        card.elevation = 3f
        card.setPadding(28, 24, 28, 24)

        val params = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        params.setMargins(0, 0, 0, 20)
        card.layoutParams = params
        listContainer.addView(card)
    }

    private fun formatPercent(value: Double): String {
        return String.format("%.2f%%", value)
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
