package im.angry.openeuicc.auth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class Roam2WorldAuthApi(private val baseUrl: String) {
    suspend fun login(email: String, password: String): AuthSession = withContext(Dispatchers.IO) {
        val response = postJson(
            MOBILE_LOGIN_PATH,
            JSONObject()
                .put("email", email)
                .put("password", password)
        )
        parseSession(response)
    }

    suspend fun refresh(session: AuthSession): AuthSession = withContext(Dispatchers.IO) {
        val refreshToken = session.refreshToken ?: throw AuthApiException("Refresh token is unavailable")
        val response = postJson(
            REFRESH_PATH,
            JSONObject().put("refresh_token", refreshToken)
        )
        parseSession(response, session)
    }

    suspend fun logout(session: AuthSession) = withContext(Dispatchers.IO) {
        postJson(
            LOGOUT_PATH,
            JSONObject().put("refresh_token", session.refreshToken),
            session.authorizationHeader
        )
        Unit
    }

    suspend fun dashboard(session: AuthSession): MobileDashboardData = withContext(Dispatchers.IO) {
        parseDashboard(getJson(DASHBOARD_PATH, session.authorizationHeader))
    }

    suspend fun wallet(session: AuthSession): MobileWalletData = withContext(Dispatchers.IO) {
        val walletResponse = getJson(WALLET_PATH, session.authorizationHeader)
        val transactionResponse = getJson(TRANSACTIONS_PATH, session.authorizationHeader)
        parseWallet(walletResponse, transactionResponse)
    }

    suspend fun packages(session: AuthSession): MobilePackageCatalog = withContext(Dispatchers.IO) {
        val packageResponse = getJson(PACKAGES_PATH, session.authorizationHeader)
        val featuredResponse = getJson(FEATURED_PACKAGES_PATH, session.authorizationHeader)
        parsePackageCatalog(packageResponse, featuredResponse, session.role)
    }

    private fun getJson(path: String, authorization: String? = null): JSONObject =
        requestJson(path, method = "GET", authorization = authorization)

    private fun postJson(path: String, body: JSONObject, authorization: String? = null): JSONObject {
        return requestJson(path, method = "POST", body = body, authorization = authorization)
    }

    private fun requestJson(
        path: String,
        method: String,
        body: JSONObject? = null,
        authorization: String? = null
    ): JSONObject {
        val connection = (URL(resolve(path)).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json")
            authorization?.let { setRequestProperty("Authorization", it) }
            doOutput = body != null
        }

        return try {
            body?.let { json ->
                connection.outputStream.use {
                    it.write(json.toString().toByteArray(Charsets.UTF_8))
                }
            }

            val status = connection.responseCode
            val responseText = ((if (status in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            })?.bufferedReader()?.use { it.readText() }).orEmpty()

            val response = responseText.takeIf { it.isNotBlank() }?.let { JSONObject(it) } ?: JSONObject()
            if (status !in 200..299 || response.optBoolean("success", true) == false) {
                throw AuthApiException(response.errorMessage(status))
            }
            response
        } finally {
            connection.disconnect()
        }
    }

    private fun resolve(path: String): String =
        "${baseUrl.trimEnd('/')}/${path.trimStart('/')}"

    private fun parseSession(response: JSONObject, existing: AuthSession? = null): AuthSession {
        val data = response.optJSONObject("data") ?: response
        val tokens = data.optJSONObject("tokens") ?: response.optJSONObject("tokens")

        val access = firstNotBlank(
            tokens?.optString("access"),
            data.optString("access"),
            response.optString("access")
        ) ?: throw AuthApiException("Login response did not include an access token")

        val refresh = firstNotBlank(
            tokens?.optString("refresh"),
            data.optString("refresh"),
            response.optString("refresh"),
            existing?.refreshToken
        )

        val user = data.optJSONObject("user") ?: response.optJSONObject("user")
        val accountSummary = accountSummary(data.optJSONObject("account"))
            ?: accountSummary(data.optJSONObject("reseller"))
            ?: accountSummary(data.optJSONObject("dealer"))
            ?: accountSummary(data)
            ?: accountSummary(user)

        val firstName = user?.optString("first_name").orEmpty()
        val lastName = user?.optString("last_name").orEmpty()
        val joinedName = listOf(firstName, lastName)
            .filter { it.isNotBlank() }
            .joinToString(" ")
        val displayName = firstNotBlank(
            joinedName,
            user?.optString("name"),
            user?.optString("email"),
            existing?.displayName
        )

        return AuthSession(
            accessToken = access,
            refreshToken = refresh,
            email = firstNotBlank(user?.optString("email"), existing?.email),
            displayName = displayName,
            role = firstNotBlank(user?.optString("role"), data.optString("role"), existing?.role),
            accountSummary = accountSummary ?: existing?.accountSummary
        )
    }

    private fun parseDashboard(response: JSONObject): MobileDashboardData {
        val data = response.optJSONObject("data") ?: response
        val dashboard = data.optJSONObject("dashboard") ?: data
        val balance = firstNotBlank(
            dashboard.optString("current_balance"),
            dashboard.optString("currentBalance"),
            dashboard.optString("current_credit"),
            dashboard.optString("currentCredit"),
            dashboard.optString("balance"),
            dashboard.optString("credit")
        ) ?: "0"
        val activeCount = firstNotBlank(
            dashboard.optString("active_esim_count"),
            dashboard.optString("active_eSIM_count"),
            dashboard.optString("activeEsimCount"),
            dashboard.optString("active_esims_count"),
            dashboard.optString("active_esims"),
            dashboard.optString("activeEsims")
        ) ?: "0"
        val orders = firstArray(
            dashboard.optJSONArray("recent_orders"),
            dashboard.optJSONArray("recentOrders"),
            dashboard.optJSONArray("orders"),
            data.optJSONArray("recent_orders"),
            data.optJSONArray("recentOrders")
        )

        return MobileDashboardData(
            currentBalance = balance,
            activeEsimCount = activeCount,
            recentOrders = parseOrders(orders)
        )
    }

    private fun parseOrders(orders: org.json.JSONArray?): List<MobileDashboardOrder> {
        if (orders == null) return emptyList()
        return (0 until orders.length()).mapNotNull { index ->
            val order = orders.optJSONObject(index) ?: return@mapNotNull null
            val title = firstNotBlank(
                order.optString("order_number"),
                order.optString("orderNumber"),
                order.optString("reference"),
                order.optString("id")
            )?.let { "#$it" } ?: "Order"
            val subtitle = firstNotBlank(
                order.optString("created_at"),
                order.optString("createdAt"),
                order.optString("date"),
                order.optString("plan_name"),
                order.optString("planName")
            ) ?: "Recent order"
            val amount = firstNotBlank(
                order.optString("total_amount"),
                order.optString("totalAmount"),
                order.optString("amount"),
                order.optString("price")
            )
            val status = firstNotBlank(order.optString("status"), order.optString("state"))
            MobileDashboardOrder(
                title = title,
                subtitle = subtitle,
                amount = amount,
                status = status
            )
        }
    }

    private fun parseWallet(walletResponse: JSONObject, transactionResponse: JSONObject): MobileWalletData {
        val walletData = walletResponse.optJSONObject("data") ?: walletResponse
        val wallet = walletData.optJSONObject("wallet") ?: walletData
        val balance = firstNotBlank(
            wallet.optString("current_balance"),
            wallet.optString("currentBalance"),
            wallet.optString("current_credit"),
            wallet.optString("currentCredit"),
            wallet.optString("balance"),
            wallet.optString("credit")
        ) ?: "0"

        val transactionData = transactionResponse.optJSONObject("data") ?: transactionResponse
        val transactions = firstArray(
            transactionData.optJSONArray("transactions"),
            transactionData.optJSONArray("recent_transactions"),
            transactionData.optJSONArray("recentTransactions"),
            transactionData.optJSONArray("results"),
            transactionResponse.optJSONArray("transactions"),
            transactionResponse.optJSONArray("results")
        )

        return MobileWalletData(
            currentBalance = balance,
            transactions = parseTransactions(transactions)
        )
    }

    private fun parsePackageCatalog(
        packageResponse: JSONObject,
        featuredResponse: JSONObject,
        role: String?
    ): MobilePackageCatalog {
        val packages = dedupePackages(parsePackageList(packageResponse, fallbackFeatured = false))
            .filter { it.isVisibleFor(role) }
        val featuredPackages = dedupePackages(parsePackageList(featuredResponse, fallbackFeatured = true))
            .ifEmpty { packages.filter { it.featured } }
            .filter { it.isVisibleFor(role) }

        return MobilePackageCatalog(
            featuredPackages = featuredPackages,
            packages = packages
        )
    }

    private fun parsePackageList(response: JSONObject, fallbackFeatured: Boolean): List<MobilePackage> {
        val data = response.optJSONObject("data") ?: response
        val packageObject = data.optJSONObject("packages") ?: response.optJSONObject("packages")
        val packages = firstArray(
            data.optJSONArray("packages"),
            data.optJSONArray("featured_packages"),
            data.optJSONArray("featuredPackages"),
            data.optJSONArray("results"),
            data.optJSONArray("items"),
            packageObject?.optJSONArray("packages"),
            packageObject?.optJSONArray("results"),
            packageObject?.optJSONArray("items"),
            response.optJSONArray("packages"),
            response.optJSONArray("results"),
            response.optJSONArray("items")
        )
        if (packages == null && data.has("id")) {
            return listOfNotNull(parsePackage(data, fallbackFeatured))
        }
        return parsePackages(packages, fallbackFeatured)
    }

    private fun parsePackages(packages: JSONArray?, fallbackFeatured: Boolean): List<MobilePackage> {
        if (packages == null) return emptyList()
        return (0 until packages.length()).mapNotNull { index ->
            parsePackage(packages.optJSONObject(index), fallbackFeatured)
        }
    }

    private fun parsePackage(packageJson: JSONObject?, fallbackFeatured: Boolean): MobilePackage? {
        packageJson ?: return null
        val country = packageCountry(packageJson)
        val currency = firstNotBlank(
            packageJson.optString("currency"),
            packageJson.optString("currency_code"),
            packageJson.optString("currencyCode")
        )
        val dataGb = firstNotBlank(
            packageJson.optString("data_gb"),
            packageJson.optString("dataGb"),
            packageJson.optString("gb")
        )
        val validityDays = firstNotBlank(
            packageJson.optString("validity_days"),
            packageJson.optString("validityDays"),
            packageJson.optString("duration_days"),
            packageJson.optString("durationDays")
        )

        return MobilePackage(
            id = firstNotBlank(
                packageJson.optString("id"),
                packageJson.optString("package_id"),
                packageJson.optString("packageId"),
                packageJson.optString("plan_id"),
                packageJson.optString("planId")
            ),
            name = firstNotBlank(
                packageJson.optString("name"),
                packageJson.optString("title"),
                packageJson.optString("package_name"),
                packageJson.optString("packageName"),
                packageJson.optString("plan_name"),
                packageJson.optString("planName")
            ) ?: "Package",
            country = country.first,
            countryCode = country.second,
            dataAmount = firstNotBlank(
                packageJson.optString("data"),
                packageJson.optString("data_amount"),
                packageJson.optString("dataAmount"),
                packageJson.optString("data_allowance"),
                packageJson.optString("dataAllowance"),
                packageJson.optString("quota")
            ) ?: dataGb?.let { "$it GB" },
            validity = firstNotBlank(
                packageJson.optString("validity"),
                packageJson.optString("duration"),
                packageJson.optString("period")
            ) ?: validityDays?.let { "$it days" },
            basePrice = priceValue(
                packageJson,
                currency,
                "price",
                "retail_price",
                "retailPrice",
                "sale_price",
                "salePrice",
                "amount"
            ),
            resellerPrice = priceValue(
                packageJson,
                currency,
                "reseller_price",
                "resellerPrice",
                "reseller_amount",
                "resellerAmount"
            ),
            dealerPrice = priceValue(
                packageJson,
                currency,
                "dealer_price",
                "dealerPrice",
                "dealer_amount",
                "dealerAmount",
                "wholesale_price",
                "wholesalePrice"
            ),
            description = firstNotBlank(
                packageJson.optString("description"),
                packageJson.optString("short_description"),
                packageJson.optString("shortDescription"),
                packageJson.optString("details")
            ),
            network = firstNotBlank(
                packageJson.optString("network"),
                packageJson.optString("operator"),
                packageJson.optString("carrier")
            ),
            coverage = firstNotBlank(
                packageJson.optString("coverage"),
                packageJson.optString("region"),
                country.third
            ),
            visibleToReseller = visibleForRole(packageJson, "reseller"),
            visibleToDealer = visibleForRole(packageJson, "dealer"),
            featured = optionalBoolean(packageJson, "is_featured", "isFeatured", "featured") ?: fallbackFeatured
        )
    }

    private fun packageCountry(packageJson: JSONObject): Triple<String, String?, String?> {
        val countries = firstArray(
            packageJson.optJSONArray("countries"),
            packageJson.optJSONArray("coverage_countries"),
            packageJson.optJSONArray("coverageCountries")
        )
        val countryLabels = countryLabels(countries)
        if (countryLabels.size > 1) {
            return Triple("Multi-country", null, countryLabels.joinToString(", "))
        }
        if (countryLabels.size == 1) {
            return Triple(countryLabels.first(), null, null)
        }

        val country = packageJson.optJSONObject("country")
        val name = firstNotBlank(
            country?.optString("name"),
            country?.optString("country_name"),
            country?.optString("countryName"),
            packageJson.optString("country_name"),
            packageJson.optString("countryName"),
            packageJson.optString("country")
        ) ?: "Global"
        val code = firstNotBlank(
            country?.optString("code"),
            country?.optString("iso2"),
            country?.optString("iso_code"),
            country?.optString("isoCode"),
            packageJson.optString("country_code"),
            packageJson.optString("countryCode"),
            packageJson.optString("iso2")
        )

        return Triple(name, code, null)
    }

    private fun countryLabels(countries: JSONArray?): List<String> {
        if (countries == null) return emptyList()
        return (0 until countries.length()).mapNotNull { index ->
            val country = countries.optJSONObject(index)
            firstNotBlank(
                country?.optString("name"),
                country?.optString("country_name"),
                country?.optString("countryName"),
                country?.optString("code"),
                countries.optString(index)
            )
        }
    }

    private fun priceValue(packageJson: JSONObject, currency: String?, vararg keys: String): String? {
        val value = firstNotBlank(*keys.map { packageJson.optString(it) }.toTypedArray()) ?: return null
        val trimmed = value.trim()
        if (currency.isNullOrBlank() || trimmed.any { it.isLetter() || it == '$' }) return trimmed
        return "$currency $trimmed"
    }

    private fun visibleForRole(packageJson: JSONObject, role: String): Boolean {
        if (optionalBoolean(packageJson, "is_active", "isActive", "active", "enabled") == false) {
            return false
        }

        optionalBoolean(
            packageJson,
            "visible_for_$role",
            "visibleFor${role.replaceFirstChar { it.titlecase() }}",
            "${role}_visible",
            "${role}Visible",
            "available_to_$role",
            "availableTo${role.replaceFirstChar { it.titlecase() }}",
            "show_$role",
            "show${role.replaceFirstChar { it.titlecase() }}"
        )?.let { return it }

        val roles = firstArray(
            packageJson.optJSONArray("visible_to"),
            packageJson.optJSONArray("visibleTo"),
            packageJson.optJSONArray("visibility"),
            packageJson.optJSONArray("roles"),
            packageJson.optJSONArray("allowed_roles"),
            packageJson.optJSONArray("allowedRoles"),
            packageJson.optJSONArray("available_for"),
            packageJson.optJSONArray("availableFor")
        )
        if (roles != null) return roleArrayContains(roles, role)

        val visibility = firstNotBlank(
            packageJson.optString("visible_to"),
            packageJson.optString("visibleTo"),
            packageJson.optString("visibility"),
            packageJson.optString("roles"),
            packageJson.optString("allowed_roles"),
            packageJson.optString("allowedRoles"),
            packageJson.optString("available_for"),
            packageJson.optString("availableFor")
        ) ?: return true

        return visibilityTextContains(visibility, role)
    }

    private fun roleArrayContains(roles: JSONArray, role: String): Boolean =
        (0 until roles.length()).any { index ->
            val roleObject = roles.optJSONObject(index)
            val roleText = firstNotBlank(
                roleObject?.optString("role"),
                roleObject?.optString("name"),
                roleObject?.optString("slug"),
                roles.optString(index)
            ) ?: return@any false
            visibilityTextContains(roleText, role)
        }

    private fun visibilityTextContains(visibility: String, role: String): Boolean {
        val normalized = visibility.lowercase()
        return normalized.contains(role) ||
            normalized.contains("all") ||
            normalized.contains("both") ||
            normalized.contains("public")
    }

    private fun optionalBoolean(packageJson: JSONObject, vararg keys: String): Boolean? {
        keys.forEach { key ->
            if (packageJson.has(key) && !packageJson.isNull(key)) {
                val raw = packageJson.optString(key).lowercase()
                return when (raw) {
                    "true", "1", "yes" -> true
                    "false", "0", "no" -> false
                    else -> null
                }
            }
        }
        return null
    }

    private fun dedupePackages(packages: List<MobilePackage>): List<MobilePackage> {
        val seen = mutableSetOf<String>()
        return packages.filter { mobilePackage ->
            val key = mobilePackage.id ?: "${mobilePackage.name}|${mobilePackage.country}|${mobilePackage.basePrice}"
            seen.add(key)
        }
    }

    private fun parseTransactions(transactions: JSONArray?): List<MobileTransaction> {
        if (transactions == null) return emptyList()
        return (0 until transactions.length()).mapNotNull { index ->
            val transaction = transactions.optJSONObject(index) ?: return@mapNotNull null
            val type = firstNotBlank(
                transaction.optString("type"),
                transaction.optString("transaction_type"),
                transaction.optString("transactionType")
            )
            val description = firstNotBlank(
                transaction.optString("description"),
                transaction.optString("title"),
                transaction.optString("note"),
                transaction.optString("reference")
            )
            val title = description ?: type?.replace("_", " ")?.replaceFirstChar {
                if (it.isLowerCase()) it.titlecase() else it.toString()
            } ?: "Transaction"
            val subtitle = firstNotBlank(
                transaction.optString("created_at"),
                transaction.optString("createdAt"),
                transaction.optString("date"),
                transaction.optString("updated_at"),
                transaction.optString("updatedAt")
            ) ?: "Wallet activity"
            val amount = firstNotBlank(
                transaction.optString("amount"),
                transaction.optString("value"),
                transaction.optString("total"),
                transaction.optString("credit_amount"),
                transaction.optString("creditAmount")
            ) ?: "0"
            val status = firstNotBlank(
                transaction.optString("status"),
                transaction.optString("state"),
                type
            )

            MobileTransaction(
                title = title,
                subtitle = subtitle,
                amount = amount,
                status = status
            )
        }
    }

    private fun accountSummary(account: JSONObject?): String? {
        account ?: return null
        return when {
            account.has("current_credit") -> "Credit: ${account.optString("current_credit")}"
            account.has("current_balance") -> "Balance: ${account.optString("current_balance")}"
            account.has("balance") -> "Balance: ${account.optString("balance")}"
            else -> null
        }
    }

    private fun JSONObject.errorMessage(status: Int): String =
        firstNotBlank(
            optString("message"),
            optString("detail"),
            optJSONObject("errors")?.optString("detail")
        ) ?: "Request failed with HTTP $status"

    private fun firstNotBlank(vararg values: String?): String? =
        values.firstOrNull { !it.isNullOrBlank() && it != "null" }

    private fun firstArray(vararg values: JSONArray?): JSONArray? =
        values.firstOrNull { it != null }

    companion object {
        private const val MOBILE_LOGIN_PATH = "api/v1/mobile/auth/login/"
        private const val DASHBOARD_PATH = "api/v1/mobile/dashboard/"
        private const val WALLET_PATH = "api/v1/mobile/wallet/"
        private const val TRANSACTIONS_PATH = "api/v1/mobile/transactions/"
        private const val PACKAGES_PATH = "api/v1/mobile/packages/"
        private const val FEATURED_PACKAGES_PATH = "api/v1/mobile/packages/featured/"
        private const val REFRESH_PATH = "api/v1/auth/refresh/"
        private const val LOGOUT_PATH = "api/v1/auth/logout/"
        private const val TIMEOUT_MS = 30_000
    }
}

class AuthApiException(message: String) : Exception(message)
