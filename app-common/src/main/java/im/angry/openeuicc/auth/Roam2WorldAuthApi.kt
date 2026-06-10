package im.angry.openeuicc.auth

import android.util.Log
import im.angry.openeuicc.common.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class Roam2WorldAuthApi(baseUrl: String) {
    private val apiBaseUrl = normalizeBaseUrl(baseUrl)

    val loginEndpointUrl: String
        get() = resolve(MOBILE_LOGIN_ENDPOINT.path)

    val mobileEndpointUrls: Map<String, String>
        get() = MOBILE_ENDPOINTS.associate { it.label to resolve(it.path) }

    suspend fun login(email: String, password: String): AuthSession = withContext(Dispatchers.IO) {
        val response = postJson(
            MOBILE_LOGIN_ENDPOINT,
            JSONObject()
                .put("email", email)
                .put("password", password)
        )
        parseSession(response)
    }

    suspend fun refresh(session: AuthSession): AuthSession = withContext(Dispatchers.IO) {
        val refreshToken = session.refreshToken ?: throw AuthApiException("Refresh token is unavailable")
        val response = postJson(
            REFRESH_ENDPOINT,
            JSONObject().put("refresh_token", refreshToken)
        )
        parseSession(response, session)
    }

    suspend fun logout(session: AuthSession) = withContext(Dispatchers.IO) {
        postJson(
            LOGOUT_ENDPOINT,
            JSONObject().put("refresh_token", session.refreshToken),
            session.authorizationHeader
        )
        Unit
    }

    suspend fun dashboard(session: AuthSession): MobileDashboardData = withContext(Dispatchers.IO) {
        parseDashboard(getJson(DASHBOARD_ENDPOINT, session.authorizationHeader))
    }

    suspend fun wallet(session: AuthSession): MobileWalletData = withContext(Dispatchers.IO) {
        val walletResponse = getJson(WALLET_ENDPOINT, session.authorizationHeader)
        val transactionResponse = getJson(TRANSACTIONS_ENDPOINT, session.authorizationHeader)
        parseWallet(walletResponse, transactionResponse)
    }

    suspend fun walletRequests(session: AuthSession): List<MobileWalletRequest> = withContext(Dispatchers.IO) {
        parseWalletRequests(getJson(WALLET_REQUESTS_ENDPOINT, session.authorizationHeader))
    }

    suspend fun createWalletRequest(
        session: AuthSession,
        amount: String,
        currency: String,
        note: String
    ): MobileWalletRequest = withContext(Dispatchers.IO) {
        parseWalletRequest(
            postJson(
                WALLET_REQUESTS_ENDPOINT,
                JSONObject()
                    .put("amount", amount)
                    .put("currency", currency)
                    .put("note", note),
                session.authorizationHeader
            )
        )
    }

    suspend fun packages(session: AuthSession): MobilePackageCatalog = withContext(Dispatchers.IO) {
        val packageResponse = getJson(PACKAGES_ENDPOINT, session.authorizationHeader)
        val featuredResponse = getJson(FEATURED_PACKAGES_ENDPOINT, session.authorizationHeader)
        parsePackageCatalog(packageResponse, featuredResponse, session.role)
    }

    suspend fun orders(session: AuthSession): MobileOrderHistory = withContext(Dispatchers.IO) {
        parseMobileOrders(getJson(MOBILE_ORDERS_ENDPOINT, session.authorizationHeader))
    }

    suspend fun dealers(session: AuthSession): MobileDealerList = withContext(Dispatchers.IO) {
        parseMobileDealers(getJson(MOBILE_DEALERS_ENDPOINT, session.authorizationHeader))
    }

    suspend fun dealer(session: AuthSession, dealerId: String): MobileDealer = withContext(Dispatchers.IO) {
        parseMobileDealers(
            getJson(
                ApiEndpoint("mobile dealer detail", "api/v1/mobile/dealers/$dealerId/"),
                session.authorizationHeader
            )
        ).dealers.firstOrNull() ?: throw AuthApiException("Dealer detail was unavailable")
    }

    suspend fun allocateDealerBalance(
        session: AuthSession,
        dealerId: String,
        amount: String
    ): MobileDealerAllocationResult = withContext(Dispatchers.IO) {
        parseMobileDealerAllocation(
            postJson(
                ApiEndpoint("mobile dealer balance allocation", "api/v1/mobile/dealers/$dealerId/allocate-balance/"),
                JSONObject().put("amount", amount),
                session.authorizationHeader
            )
        )
    }

    suspend fun suspendDealer(session: AuthSession, dealerId: String): MobileDealer = withContext(Dispatchers.IO) {
        parseMobileDealers(
            postJson(
                ApiEndpoint("mobile dealer suspend", "api/v1/mobile/dealers/$dealerId/suspend/"),
                JSONObject(),
                session.authorizationHeader
            )
        ).dealers.firstOrNull() ?: throw AuthApiException("Dealer status was unavailable")
    }

    suspend fun activateDealer(session: AuthSession, dealerId: String): MobileDealer = withContext(Dispatchers.IO) {
        parseMobileDealers(
            postJson(
                ApiEndpoint("mobile dealer activate", "api/v1/mobile/dealers/$dealerId/activate/"),
                JSONObject(),
                session.authorizationHeader
            )
        ).dealers.firstOrNull() ?: throw AuthApiException("Dealer status was unavailable")
    }

    suspend fun order(session: AuthSession, orderId: String): MobileOrder = withContext(Dispatchers.IO) {
        parseMobileOrders(
            getJson(
                ApiEndpoint("mobile order detail", "api/v1/mobile/orders/$orderId/"),
                session.authorizationHeader
            )
        ).orders.firstOrNull() ?: throw AuthApiException("Order detail was unavailable")
    }

    suspend fun esims(session: AuthSession): MobileEsimList = withContext(Dispatchers.IO) {
        parseMobileEsims(getJson(MOBILE_ESIMS_ENDPOINT, session.authorizationHeader))
    }

    suspend fun esim(session: AuthSession, esimId: String): MobileEsim = withContext(Dispatchers.IO) {
        parseMobileEsims(
            getJson(
                ApiEndpoint("mobile eSIM detail", "api/v1/mobile/esims/$esimId/"),
                session.authorizationHeader
            )
        ).esims.firstOrNull() ?: throw AuthApiException("eSIM detail was unavailable")
    }

    suspend fun purchasePackage(
        session: AuthSession,
        request: MobilePackagePurchaseRequest
    ): MobilePackagePurchaseResult = withContext(Dispatchers.IO) {
        val amount = moneyAmount(request.price)
        val body = JSONObject()
            .put("payment_method", "wallet")
            .put("quantity", 1)
            .put("order_type", "esim")
            .put("order_source", request.role ?: "app")
            .put("product_name", request.packageName)
            .put("unit_price", amount)
            .put("subtotal", amount)
            .put("tax_amount", "0.00")
            .put("delivery_fee", "0.00")
            .put("total_amount", amount)
            .put("price", amount)
        request.packageId?.let { body.put("package_id", it) }
        request.provider?.let { body.put("provider", it) }
        request.packageDescription?.let { body.put("product_description", it) }
        request.country?.let { body.put("delivery_country", it) }
        request.customerFirstName?.let { body.put("customer_first_name", it) }
        request.customerLastName?.let { body.put("customer_last_name", it) }
        request.customerPhone?.let {
            body.put("customer_phone", it)
            body.put("phone_number", it)
        }

        parsePurchaseResult(
            postJson(MOBILE_ORDERS_ENDPOINT, body, session.authorizationHeader),
            request.packageName,
            request.price
        )
    }

    fun logMobileEndpointConfiguration() {
        if (!BuildConfig.DEBUG) return
        mobileEndpointUrls.forEach { (label, url) ->
            Log.d(TAG, "Configured $label endpoint: $url")
        }
    }

    private fun getJson(endpoint: ApiEndpoint, authorization: String? = null): JSONObject =
        requestJson(endpoint, method = "GET", authorization = authorization)

    private fun postJson(endpoint: ApiEndpoint, body: JSONObject, authorization: String? = null): JSONObject {
        return requestJson(endpoint, method = "POST", body = body, authorization = authorization)
    }

    private fun requestJson(
        endpoint: ApiEndpoint,
        method: String,
        body: JSONObject? = null,
        authorization: String? = null
    ): JSONObject {
        val requestUrl = resolve(endpoint.path)
        logRequest(method, endpoint.label, requestUrl)
        val connection = (URL(requestUrl).openConnection() as HttpURLConnection).apply {
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

            logRawResponse(method, endpoint.label, requestUrl, status, connection.contentType, responseText)
            val response = parseJsonResponse(responseText, status, connection.contentType, endpoint.label, requestUrl)
            if (status !in 200..299 || response.optBoolean("success", true) == false) {
                throw AuthApiException("${endpoint.label}: ${response.errorMessage(status)}")
            }
            response
        } finally {
            connection.disconnect()
        }
    }

    private fun resolve(path: String): String =
        "${apiBaseUrl.trimEnd('/')}/${path.trimStart('/')}"

    private fun parseJsonResponse(
        responseText: String,
        status: Int,
        contentType: String?,
        endpointLabel: String,
        requestUrl: String
    ): JSONObject {
        if (responseText.isBlank()) return JSONObject()

        val trimmed = responseText.trimStart()
        if (trimmed.startsWith("[")) {
            return try {
                JSONObject().put("data", JSONArray(responseText))
            } catch (e: JSONException) {
                throw AuthApiException("$endpointLabel returned invalid JSON from $requestUrl (HTTP $status): ${e.message}")
            }
        }

        if (!trimmed.startsWith("{")) {
            val responseType = contentType?.takeIf { it.isNotBlank() } ?: "unknown content type"
            throw AuthApiException(
                "$endpointLabel returned non-JSON response from $requestUrl " +
                    "(HTTP $status, $responseType). Check that ROAM2WORLD_API_BASE_URL points to the mobile API backend."
            )
        }

        return try {
            JSONObject(responseText)
        } catch (e: JSONException) {
            throw AuthApiException("$endpointLabel returned invalid JSON from $requestUrl (HTTP $status): ${e.message}")
        }
    }

    private fun parseSession(response: JSONObject, existing: AuthSession? = null): AuthSession {
        val data = response.optJSONObject("data") ?: response
        val tokens = data.optJSONObject("tokens") ?: response.optJSONObject("tokens")

        val access = firstNotBlank(
            tokens?.optString("access"),
            tokens?.optString("access_token"),
            data.optString("access"),
            data.optString("access_token"),
            response.optString("access"),
            response.optString("access_token")
        ) ?: throw AuthApiException("Login response did not include an access token")

        val refresh = firstNotBlank(
            tokens?.optString("refresh"),
            tokens?.optString("refresh_token"),
            data.optString("refresh"),
            data.optString("refresh_token"),
            response.optString("refresh"),
            response.optString("refresh_token"),
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

    internal fun parseMobileOrders(response: JSONObject): MobileOrderHistory {
        val dataArray = response.optJSONArray("data")
        val data = response.optJSONObject("data") ?: response
        val orderObject = firstObject(
            data.optJSONObject("order"),
            data.optJSONObject("purchase"),
            response.optJSONObject("order"),
            response.optJSONObject("purchase")
        )
        val orders = firstArray(
            dataArray,
            data.optJSONArray("orders"),
            data.optJSONArray("recent_orders"),
            data.optJSONArray("recentOrders"),
            data.optJSONArray("results"),
            data.optJSONArray("items"),
            response.optJSONArray("orders"),
            response.optJSONArray("results"),
            response.optJSONArray("items")
        )

        if (orders == null && (orderObject != null || data.has("id") || data.has("order_number"))) {
            return MobileOrderHistory(listOfNotNull(parseMobileOrder(orderObject ?: data)))
        }

        return MobileOrderHistory(parseMobileOrderList(orders))
    }

    private fun parseMobileOrderList(orders: JSONArray?): List<MobileOrder> {
        if (orders == null) return emptyList()
        return (0 until orders.length()).mapNotNull { index ->
            parseMobileOrder(orders.optJSONObject(index))
        }
    }

    internal fun parseMobileDealers(response: JSONObject): MobileDealerList {
        val dataArray = response.optJSONArray("data")
        val data = response.optJSONObject("data") ?: response
        val dealerObject = firstObject(
            data.optJSONObject("dealer"),
            data.optJSONObject("profile"),
            response.optJSONObject("dealer"),
            response.optJSONObject("profile")
        )
        val dealers = firstArray(
            dataArray,
            data.optJSONArray("dealers"),
            data.optJSONArray("results"),
            data.optJSONArray("items"),
            response.optJSONArray("dealers"),
            response.optJSONArray("results"),
            response.optJSONArray("items")
        )

        if (dealers == null && (dealerObject != null || data.has("dealer_id") || data.has("id"))) {
            return MobileDealerList(listOfNotNull(parseMobileDealer(dealerObject ?: data)))
        }

        return MobileDealerList(parseMobileDealerList(dealers))
    }

    private fun parseMobileDealerList(dealers: JSONArray?): List<MobileDealer> {
        if (dealers == null) return emptyList()
        return (0 until dealers.length()).mapNotNull { index ->
            parseMobileDealer(dealers.optJSONObject(index))
        }
    }

    private fun parseMobileDealer(dealerJson: JSONObject?): MobileDealer? {
        dealerJson ?: return null
        val profile = dealerJson.optJSONObject("profile")
        val recentOrders = firstArray(
            dealerJson.optJSONArray("recent_orders"),
            dealerJson.optJSONArray("recentOrders"),
            dealerJson.optJSONArray("orders"),
            profile?.optJSONArray("recent_orders"),
            profile?.optJSONArray("recentOrders")
        )

        return MobileDealer(
            id = firstNotBlank(
                dealerJson.optString("id"),
                dealerJson.optString("dealer_id"),
                dealerJson.optString("dealerId"),
                profile?.optString("id")
            ),
            name = firstNotBlank(
                dealerJson.optString("dealer_name"),
                dealerJson.optString("dealerName"),
                dealerJson.optString("name"),
                dealerJson.optString("full_name"),
                dealerJson.optString("fullName"),
                profile?.optString("name"),
                profile?.optString("full_name"),
                profile?.optString("fullName"),
                profile?.optString("email"),
                dealerJson.optString("email")
            ) ?: "Dealer",
            email = firstNotBlank(
                dealerJson.optString("email"),
                profile?.optString("email")
            ),
            currentBalance = firstNotBlank(
                dealerJson.optString("current_balance"),
                dealerJson.optString("currentBalance"),
                dealerJson.optString("balance"),
                dealerJson.optString("available_balance"),
                dealerJson.optString("availableBalance")
            ) ?: "0",
            status = firstNotBlank(
                dealerJson.optString("status"),
                dealerJson.optString("state")
            ) ?: "active",
            totalOrders = firstNotBlank(
                dealerJson.optString("total_orders"),
                dealerJson.optString("totalOrders"),
                dealerJson.optString("order_count"),
                dealerJson.optString("orderCount")
            ) ?: "0",
            revenue = firstNotBlank(
                dealerJson.optString("revenue"),
                dealerJson.optString("total_revenue"),
                dealerJson.optString("totalRevenue")
            ) ?: "0",
            currency = firstNotBlank(dealerJson.optString("currency")) ?: "USD",
            firstName = firstNotBlank(
                dealerJson.optString("first_name"),
                dealerJson.optString("firstName"),
                profile?.optString("first_name"),
                profile?.optString("firstName")
            ),
            lastName = firstNotBlank(
                dealerJson.optString("last_name"),
                dealerJson.optString("lastName"),
                profile?.optString("last_name"),
                profile?.optString("lastName")
            ),
            phoneNumber = firstNotBlank(
                dealerJson.optString("phone_number"),
                dealerJson.optString("phoneNumber"),
                profile?.optString("phone_number"),
                profile?.optString("phoneNumber")
            ),
            countryCode = firstNotBlank(
                dealerJson.optString("country_code"),
                dealerJson.optString("countryCode"),
                profile?.optString("country_code"),
                profile?.optString("countryCode")
            ),
            totalAllocated = firstNotBlank(
                dealerJson.optString("total_allocated"),
                dealerJson.optString("totalAllocated")
            ),
            totalSpent = firstNotBlank(
                dealerJson.optString("total_spent"),
                dealerJson.optString("totalSpent")
            ),
            createdAt = firstNotBlank(
                dealerJson.optString("created_at"),
                dealerJson.optString("createdAt"),
                profile?.optString("created_at"),
                profile?.optString("createdAt")
            ),
            suspensionReason = firstNotBlank(
                dealerJson.optString("suspension_reason"),
                dealerJson.optString("suspensionReason")
            ),
            recentOrders = parseMobileOrderList(recentOrders)
        )
    }

    private fun parseMobileDealerAllocation(response: JSONObject): MobileDealerAllocationResult {
        val data = response.optJSONObject("data") ?: response
        val dealer = parseMobileDealer(data.optJSONObject("dealer") ?: response.optJSONObject("dealer"))
            ?: throw AuthApiException("Dealer allocation response did not include dealer data")
        return MobileDealerAllocationResult(
            amount = firstNotBlank(
                data.optString("amount"),
                response.optString("amount")
            ) ?: "0",
            currency = firstNotBlank(
                data.optString("currency"),
                response.optString("currency")
            ) ?: "USD",
            resellerRemainingBalance = firstNotBlank(
                data.optString("reseller_remaining_balance"),
                data.optString("resellerRemainingBalance"),
                data.optString("remaining_balance"),
                data.optString("remainingBalance")
            ),
            dealer = dealer
        )
    }

    private fun parseMobileOrder(orderJson: JSONObject?): MobileOrder? {
        orderJson ?: return null
        val packageObject = firstObject(
            orderJson.optJSONObject("package"),
            orderJson.optJSONObject("plan"),
            orderJson.optJSONObject("product")
        )
        val esimObject = firstObject(
            orderJson.optJSONObject("esim"),
            orderJson.optJSONObject("eSIM")
        )
        return MobileOrder(
            id = firstNotBlank(
                orderJson.optString("id"),
                orderJson.optString("order_id"),
                orderJson.optString("orderId")
            ),
            orderNumber = firstNotBlank(
                orderJson.optString("order_number"),
                orderJson.optString("orderNumber"),
                orderJson.optString("reference"),
                orderJson.optString("reference_number"),
                orderJson.optString("referenceNumber")
            ),
            packageName = firstNotBlank(
                orderJson.optString("package_name"),
                orderJson.optString("packageName"),
                orderJson.optString("plan_name"),
                orderJson.optString("planName"),
                orderJson.optString("product_name"),
                orderJson.optString("productName"),
                packageObject?.optString("name"),
                packageObject?.optString("package_name"),
                packageObject?.optString("packageName")
            ) ?: "Package",
            price = firstNotBlank(
                orderJson.optString("price"),
                orderJson.optString("amount"),
                orderJson.optString("total_amount"),
                orderJson.optString("totalAmount"),
                orderJson.optString("subtotal")
            ),
            status = firstNotBlank(
                orderJson.optString("status"),
                orderJson.optString("state"),
                orderJson.optString("order_status"),
                orderJson.optString("orderStatus")
            ),
            createdAt = firstNotBlank(
                orderJson.optString("created_at"),
                orderJson.optString("createdAt"),
                orderJson.optString("created"),
                orderJson.optString("date"),
                orderJson.optString("updated_at"),
                orderJson.optString("updatedAt")
            ),
            provider = firstNotBlank(
                orderJson.optString("provider"),
                orderJson.optString("source"),
                esimObject?.optString("provider")
            ),
            esimId = firstNotBlank(
                orderJson.optString("esim_id"),
                orderJson.optString("esimId"),
                esimObject?.optString("id"),
                esimObject?.optString("esim_id"),
                esimObject?.optString("esimId")
            ),
            esim = parseMobileEsim(esimObject)
        )
    }

    internal fun parseMobileEsims(response: JSONObject): MobileEsimList {
        val dataArray = response.optJSONArray("data")
        val data = response.optJSONObject("data") ?: response
        val esimObject = firstObject(
            data.optJSONObject("esim"),
            data.optJSONObject("eSIM"),
            data.optJSONObject("profile"),
            response.optJSONObject("esim"),
            response.optJSONObject("eSIM"),
            response.optJSONObject("profile")
        )
        val esims = firstArray(
            dataArray,
            data.optJSONArray("esims"),
            data.optJSONArray("eSIMs"),
            data.optJSONArray("profiles"),
            data.optJSONArray("results"),
            data.optJSONArray("items"),
            response.optJSONArray("esims"),
            response.optJSONArray("eSIMs"),
            response.optJSONArray("results"),
            response.optJSONArray("items")
        )

        if (esims == null && (esimObject != null || data.has("iccid") || data.has("activation_code"))) {
            return MobileEsimList(listOfNotNull(parseMobileEsim(esimObject ?: data)))
        }

        return MobileEsimList(parseMobileEsimList(esims))
    }

    private fun parseMobileEsimList(esims: JSONArray?): List<MobileEsim> {
        if (esims == null) return emptyList()
        return (0 until esims.length()).mapNotNull { index ->
            parseMobileEsim(esims.optJSONObject(index))
        }
    }

    private fun parseMobileEsimLastRenewal(json: JSONObject?): MobileEsimLastRenewal? {
        json ?: return null
        if (json.length() == 0) return null
        return MobileEsimLastRenewal(
            provider = firstNotBlank(json.optString("provider")),
            success = if (json.has("success")) json.optBoolean("success") else null,
            message = firstNotBlank(json.optString("message"), json.optString("msg")),
            code = firstNotBlank(json.optString("code")),
            orderNo = firstNotBlank(json.optString("order_no"), json.optString("orderNo")),
            productName = firstNotBlank(json.optString("product_name"), json.optString("productName")),
            productCode = firstNotBlank(json.optString("product_code"), json.optString("productCode")),
            createdTime = firstNotBlank(json.optString("created_time"), json.optString("createdTime")),
            activatedEndTime = firstNotBlank(json.optString("activated_end_time"), json.optString("activatedEndTime")),
            renewExpirationTime = firstNotBlank(json.optString("renew_expiration_time"), json.optString("renewExpirationTime")),
            latestActivationTime = firstNotBlank(json.optString("latest_activation_time"), json.optString("latestActivationTime")),
            orderStatus = firstNotBlank(json.optString("order_status"), json.optString("orderStatus")),
            profileStatus = firstNotBlank(json.optString("profile_status"), json.optString("profileStatus"))
        )
    }

    private fun parseMobileEsim(esimJson: JSONObject?): MobileEsim? {
        esimJson ?: return null
        val activation = firstObject(
            esimJson.optJSONObject("activation"),
            esimJson.optJSONObject("activation_details"),
            esimJson.optJSONObject("activationDetails"),
            esimJson.optJSONObject("qr")
        )
        val packageObject = firstObject(
            esimJson.optJSONObject("package"),
            esimJson.optJSONObject("plan"),
            esimJson.optJSONObject("product")
        )
        val orderObject = esimJson.optJSONObject("order")
        val qrCode = firstNotBlank(
            activation?.optString("qr_code"),
            activation?.optString("qrCode"),
            activation?.optString("qr"),
            esimJson.optString("qr_code"),
            esimJson.optString("qrCode"),
            esimJson.optString("qr")
        )
        val activationCode = firstNotBlank(
            activation?.optString("activation_code"),
            activation?.optString("activationCode"),
            esimJson.optString("activation_code"),
            esimJson.optString("activationCode"),
            qrCode?.takeIf { it.startsWith("LPA:", ignoreCase = true) || it.startsWith("1\$") }
        )
        val lpaCode = firstNotBlank(
            activation?.optString("lpa_code"),
            activation?.optString("lpaCode"),
            activation?.optString("lpa"),
            activation?.optString("lpa_string"),
            activation?.optString("lpaString"),
            esimJson.optString("lpa_code"),
            esimJson.optString("lpaCode"),
            esimJson.optString("lpa"),
            esimJson.optString("lpa_string"),
            esimJson.optString("lpaString"),
            activationCode?.takeIf { it.startsWith("LPA:", ignoreCase = true) || it.startsWith("1\$") }
        )
        val matchingId = firstNotBlank(
            activation?.optString("matching_id"),
            activation?.optString("matchingId"),
            activation?.optString("matchingID"),
            esimJson.optString("matching_id"),
            esimJson.optString("matchingId"),
            esimJson.optString("matchingID")
        )?.takeUnless { it.startsWith("LPA:", ignoreCase = true) || it.startsWith("1\$") }

        return MobileEsim(
            id = firstNotBlank(
                esimJson.optString("id"),
                esimJson.optString("esim_id"),
                esimJson.optString("esimId"),
                activation?.optString("esim_id"),
                activation?.optString("esimId")
            ),
            iccid = firstNotBlank(
                esimJson.optString("iccid"),
                activation?.optString("iccid")
            ),
            provider = firstNotBlank(
                esimJson.optString("provider"),
                esimJson.optString("source"),
                esimJson.optString("carrier"),
                activation?.optString("provider")
            ),
            packageName = firstNotBlank(
                esimJson.optString("package_name"),
                esimJson.optString("packageName"),
                esimJson.optString("plan_name"),
                esimJson.optString("planName"),
                esimJson.optString("product_name"),
                esimJson.optString("productName"),
                packageObject?.optString("name"),
                packageObject?.optString("package_name"),
                packageObject?.optString("packageName"),
                orderObject?.optString("package_name"),
                orderObject?.optString("packageName"),
                orderObject?.optString("product_name"),
                orderObject?.optString("productName")
            ),
            status = firstNotBlank(
                esimJson.optString("status"),
                esimJson.optString("state"),
                esimJson.optString("profile_status"),
                esimJson.optString("profileStatus")
            ),
            activationCode = activationCode,
            lpaCode = lpaCode,
            smdpAddress = firstNotBlank(
                activation?.optString("smdp_address"),
                activation?.optString("smdpAddress"),
                activation?.optString("smdp"),
                activation?.optString("sm_dp_plus_address"),
                activation?.optString("smDpPlusAddress"),
                esimJson.optString("smdp_address"),
                esimJson.optString("smdpAddress"),
                esimJson.optString("smdp"),
                esimJson.optString("sm_dp_plus_address"),
                esimJson.optString("smDpPlusAddress")
            ),
            matchingId = matchingId,
            confirmationCodeRequired = optionalBoolean(
                activation ?: esimJson,
                "confirmation_code_required",
                "confirmationCodeRequired"
            ) ?: false,
            qrCode = qrCode,
            qrCodeUrl = firstNotBlank(
                activation?.optString("qr_code_url"),
                activation?.optString("qrCodeUrl"),
                activation?.optString("qr_url"),
                activation?.optString("qrUrl"),
                esimJson.optString("qr_code_url"),
                esimJson.optString("qrCodeUrl"),
                esimJson.optString("qr_url"),
                esimJson.optString("qrUrl")
            ),
            createdAt = firstNotBlank(
                esimJson.optString("created_at"),
                esimJson.optString("createdAt"),
                esimJson.optString("assigned_at"),
                esimJson.optString("assignedAt"),
                esimJson.optString("activated_at"),
                esimJson.optString("activatedAt")
            ),
            orderNumber = firstNotBlank(
                esimJson.optString("order_number"),
                esimJson.optString("orderNumber"),
                orderObject?.optString("order_number"),
                orderObject?.optString("orderNumber"),
                orderObject?.optString("reference")
            ),
            expiresAt = firstNotBlank(
                esimJson.optString("expires_at"),
                esimJson.optString("expiresAt"),
                esimJson.optString("expiry_date"),
                esimJson.optString("expiryDate"),
                esimJson.optString("expiration_date"),
                esimJson.optString("expirationDate")
            ),
            dataRemaining = firstNotBlank(
                esimJson.optString("data_remaining"),
                esimJson.optString("dataRemaining"),
                esimJson.optString("remaining_data"),
                esimJson.optString("remainingData")
            ),
            lastRenewal = parseMobileEsimLastRenewal(esimJson.optJSONObject("last_renewal") ?: esimJson.optJSONObject("lastRenewal")),
            dataUsed = firstNotBlank(
                esimJson.optString("data_used"),
                esimJson.optString("dataUsed"),
                esimJson.optString("used_data"),
                esimJson.optString("usedData")
            ),
            orderId = firstNotBlank(
                esimJson.optString("order_id"),
                esimJson.optString("orderId"),
                orderObject?.optString("id"),
                orderObject?.optString("order_id"),
                orderObject?.optString("orderId")
            )
        )
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

        val transactionArray = transactionResponse.optJSONArray("data")
        val transactionData = transactionResponse.optJSONObject("data") ?: transactionResponse
        val transactions = firstArray(
            transactionArray,
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

    private fun parseWalletRequests(response: JSONObject): List<MobileWalletRequest> {
        val data = response.optJSONObject("data") ?: response
        val requests = firstArray(
            response.optJSONArray("data"),
            data.optJSONArray("requests"),
            data.optJSONArray("results"),
            response.optJSONArray("requests"),
            response.optJSONArray("results")
        ) ?: return if (response.has("id")) listOf(parseWalletRequest(response)) else emptyList()

        return (0 until requests.length()).mapNotNull { index ->
            requests.optJSONObject(index)?.let { parseWalletRequest(it) }
        }
    }

    private fun parseWalletRequest(response: JSONObject): MobileWalletRequest {
        val data = response.optJSONObject("data") ?: response
        val item = data.optJSONObject("request")
            ?: data.optJSONObject("wallet_request")
            ?: data
        return MobileWalletRequest(
            id = firstNotBlank(
                item.optString("id"),
                item.optString("request_id"),
                item.optString("requestId")
            ),
            amount = firstNotBlank(
                item.optString("amount"),
                item.optString("requested_amount"),
                item.optString("requestedAmount")
            ) ?: "0",
            currency = firstNotBlank(item.optString("currency")) ?: "USD",
            status = firstNotBlank(item.optString("status")) ?: "pending",
            note = firstNotBlank(
                item.optString("note"),
                item.optString("dealer_notes"),
                item.optString("payment_notes"),
                item.optString("notes")
            ),
            createdAt = firstNotBlank(
                item.optString("created_at"),
                item.optString("createdAt")
            ),
            reviewedAt = firstNotBlank(
                item.optString("reviewed_at"),
                item.optString("reviewedAt"),
                item.optString("processed_at"),
                item.optString("processedAt")
            )
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
        val dataArray = response.optJSONArray("data")
        val data = response.optJSONObject("data") ?: response
        val packageObject = data.optJSONObject("packages") ?: response.optJSONObject("packages")
        val packages = firstArray(
            dataArray,
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
        val dataQuantity = firstNotBlank(
            packageJson.optString("data_quantity"),
            packageJson.optString("dataQuantity"),
            packageJson.optString("data_quota"),
            packageJson.optString("dataQuota")
        )
        val dataUnit = firstNotBlank(
            packageJson.optString("data_unit"),
            packageJson.optString("dataUnit")
        )
        val validityUnit = firstNotBlank(
            packageJson.optString("validity_unit"),
            packageJson.optString("validityUnit"),
            packageJson.optString("package_validity_unit"),
            packageJson.optString("packageValidityUnit")
        )

        return MobilePackage(
            id = firstNotBlank(
                packageJson.optString("id"),
                packageJson.optString("package_id"),
                packageJson.optString("packageId"),
                packageJson.optString("plan_id"),
                packageJson.optString("planId")
            ),
            provider = firstNotBlank(
                packageJson.optString("provider"),
                packageJson.optString("source")
            ) ?: "esimcard",
            displayProvider = firstNotBlank(
                packageJson.optString("display_provider"),
                packageJson.optString("displayProvider"),
                packageJson.optString("network_label"),
                packageJson.optString("networkLabel"),
                packageJson.optString("display_brand"),
                packageJson.optString("displayBrand")
            ),
            packageType = firstNotBlank(
                packageJson.optString("package_type"),
                packageJson.optString("packageType"),
                packageJson.optString("type")
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
            ) ?: quantityWithUnit(dataQuantity, dataUnit)
                ?: dataGb?.let { "$it GB" },
            validity = firstNotBlank(
                packageJson.optString("validity"),
                packageJson.optString("duration"),
                packageJson.optString("period"),
                packageJson.optString("package_validity"),
                packageJson.optString("packageValidity")
            )?.let { quantityWithUnit(it, validityUnit) ?: it }
                ?: validityDays?.let { "$it days" },
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
        val countryCodes = countryLabels(
            firstArray(
                packageJson.optJSONArray("country_codes"),
                packageJson.optJSONArray("countryCodes")
            )
        )
        val countryLabels = countryLabels(countries)
        if (countryLabels.size > 1) {
            return Triple("Multi-country", null, countryLabels.joinToString(", "))
        }
        if (countryLabels.size == 1) {
            return Triple(countryLabels.first(), countryCodes.firstOrNull(), null)
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
            packageJson.optString("iso2"),
            countryCodes.firstOrNull()
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

    private fun quantityWithUnit(quantity: String?, unit: String?): String? {
        if (quantity.isNullOrBlank()) return null
        return listOfNotNull(quantity, unit)
            .filter { it.isNotBlank() }
            .joinToString(" ")
    }

    private fun parsePurchaseResult(
        response: JSONObject,
        fallbackPackageName: String,
        fallbackPrice: String
    ): MobilePackagePurchaseResult {
        val data = response.optJSONObject("data") ?: response
        val order = firstObject(
            data.optJSONObject("order"),
            data.optJSONObject("purchase"),
            data.optJSONObject("result"),
            data
        ) ?: data
        val activationSource = firstObject(
            data.optJSONObject("activation"),
            data.optJSONObject("activation_details"),
            data.optJSONObject("activationDetails"),
            data.optJSONObject("esim"),
            data.optJSONObject("eSIM"),
            data.optJSONObject("qr"),
            order.optJSONObject("activation"),
            order.optJSONObject("activation_details"),
            order.optJSONObject("activationDetails"),
            order.optJSONObject("esim"),
            order.optJSONObject("eSIM"),
            order.optJSONObject("qr"),
            data
        ) ?: data

        val qrCode = firstNotBlank(
            activationSource.optString("qr_code"),
            activationSource.optString("qrCode"),
            activationSource.optString("qr"),
            data.optString("qr_code"),
            data.optString("qrCode")
        )
        val activationCode = firstNotBlank(
            activationSource.optString("lpa_code"),
            activationSource.optString("lpaCode"),
            activationSource.optString("lpa"),
            activationSource.optString("lpa_string"),
            activationSource.optString("lpaString"),
            activationSource.optString("activation_code"),
            activationSource.optString("activationCode"),
            data.optString("activation_code"),
            data.optString("activationCode"),
            qrCode?.takeIf { it.startsWith("LPA:", ignoreCase = true) || it.startsWith("1\$") }
        )
        val lpaCode = activationCode?.takeIf {
            it.startsWith("LPA:", ignoreCase = true) || it.startsWith("1\$")
        }
        val matchingId = firstNotBlank(
            activationSource.optString("matching_id"),
            activationSource.optString("matchingId"),
            activationSource.optString("matchingID"),
            activationSource.optString("activation_code"),
            activationSource.optString("activationCode")
        )?.takeUnless { it.startsWith("LPA:", ignoreCase = true) || it.startsWith("1\$") }

        return MobilePackagePurchaseResult(
            orderId = firstNotBlank(order.optString("id"), order.optString("order_id"), order.optString("orderId")),
            orderNumber = firstNotBlank(
                order.optString("order_number"),
                order.optString("orderNumber"),
                order.optString("reference"),
                data.optString("order_number"),
                data.optString("orderNumber")
            ),
            status = firstNotBlank(order.optString("status"), data.optString("status")),
            packageName = firstNotBlank(
                order.optString("product_name"),
                order.optString("productName"),
                data.optString("package_name"),
                data.optString("packageName")
            ) ?: fallbackPackageName,
            price = firstNotBlank(
                order.optString("total_amount"),
                order.optString("totalAmount"),
                order.optString("price"),
                data.optString("price")
            ) ?: fallbackPrice,
            balanceAfter = firstNotBlank(
                data.optString("balance_after"),
                data.optString("balanceAfter"),
                data.optString("current_balance"),
                data.optString("currentBalance"),
                data.optString("current_credit"),
                data.optString("currentCredit")
            ),
            activation = MobileActivationDetails(
                lpaCode = lpaCode,
                smdpAddress = firstNotBlank(
                    activationSource.optString("smdp_address"),
                    activationSource.optString("smdpAddress"),
                    activationSource.optString("smdp"),
                    activationSource.optString("sm_dp_plus_address"),
                    activationSource.optString("smDpPlusAddress")
                ),
                matchingId = matchingId,
                confirmationCodeRequired = optionalBoolean(
                    activationSource,
                    "confirmation_code_required",
                    "confirmationCodeRequired"
                ) ?: false,
                qrCode = qrCode,
                qrCodeUrl = firstNotBlank(
                    activationSource.optString("qr_code_url"),
                    activationSource.optString("qrCodeUrl"),
                    activationSource.optString("qr_url"),
                    activationSource.optString("qrUrl")
                ),
                iccid = firstNotBlank(activationSource.optString("iccid"), data.optString("iccid")),
                esimId = firstNotBlank(
                    activationSource.optString("esim_id"),
                    activationSource.optString("esimId"),
                    data.optString("esim_id"),
                    data.optString("esimId")
                )
            )
        )
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
            val provider = mobilePackage.provider?.lowercase().orEmpty()
            val key = mobilePackage.id?.let { "$provider|$it" }
                ?: "$provider|${mobilePackage.name}|${mobilePackage.country}|${mobilePackage.basePrice}"
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

    private fun firstObject(vararg values: JSONObject?): JSONObject? =
        values.firstOrNull { it != null }

    private fun moneyAmount(value: String): String {
        val raw = value.trim()
        val numeric = raw
            .replace(",", ".")
            .replace(Regex("[^0-9.-]"), "")
        return numeric.ifBlank { raw.ifBlank { "0.00" } }
    }

    private data class ApiEndpoint(
        val label: String,
        val path: String
    )

    companion object {
        private const val TAG = "Roam2WorldAuthApi"
        private const val DEFAULT_API_BASE_URL = "https://roam2world-panels-backend.onrender.com"
        private const val PARTNERS_FRONTEND_HOST = "partners.roam2world.com"
        private const val TIMEOUT_MS = 30_000
        private const val LOG_CHUNK_SIZE = 3_500
        private val MOBILE_LOGIN_ENDPOINT = ApiEndpoint("mobile login", "api/v1/mobile/auth/login/")
        private val DASHBOARD_ENDPOINT = ApiEndpoint("mobile dashboard", "api/v1/mobile/dashboard/")
        private val WALLET_ENDPOINT = ApiEndpoint("mobile wallet", "api/v1/mobile/wallet/")
        private val WALLET_REQUESTS_ENDPOINT = ApiEndpoint("mobile wallet requests", "api/v1/mobile/wallet/requests/")
        private val TRANSACTIONS_ENDPOINT = ApiEndpoint("mobile transactions", "api/v1/mobile/transactions/")
        private val PACKAGES_ENDPOINT = ApiEndpoint("mobile packages", "api/v1/mobile/packages/")
        private val FEATURED_PACKAGES_ENDPOINT = ApiEndpoint("mobile featured packages", "api/v1/mobile/packages/featured/")
        private val MOBILE_ORDERS_ENDPOINT = ApiEndpoint("mobile orders", "api/v1/mobile/orders/")
        private val MOBILE_ESIMS_ENDPOINT = ApiEndpoint("mobile eSIMs", "api/v1/mobile/esims/")
        private val MOBILE_DEALERS_ENDPOINT = ApiEndpoint("mobile dealers", "api/v1/mobile/dealers/")
        private val REFRESH_ENDPOINT = ApiEndpoint("auth refresh", "api/v1/auth/refresh/")
        private val LOGOUT_ENDPOINT = ApiEndpoint("auth logout", "api/v1/auth/logout/")
        private val MOBILE_ENDPOINTS = listOf(
            MOBILE_LOGIN_ENDPOINT,
            DASHBOARD_ENDPOINT,
            MOBILE_DEALERS_ENDPOINT,
            MOBILE_ORDERS_ENDPOINT,
            PACKAGES_ENDPOINT,
            WALLET_ENDPOINT,
            WALLET_REQUESTS_ENDPOINT,
            MOBILE_ESIMS_ENDPOINT,
            TRANSACTIONS_ENDPOINT,
            FEATURED_PACKAGES_ENDPOINT,
        )

        private fun normalizeBaseUrl(configuredBaseUrl: String): String {
            val trimmed = configuredBaseUrl.trim().ifBlank { DEFAULT_API_BASE_URL }
            val parsed = runCatching { URL(trimmed) }.getOrNull() ?: return DEFAULT_API_BASE_URL
            if (parsed.host.equals(PARTNERS_FRONTEND_HOST, ignoreCase = true)) {
                return DEFAULT_API_BASE_URL
            }

            val port = parsed.port.takeIf { it != -1 }?.let { ":$it" }.orEmpty()
            return "${parsed.protocol}://${parsed.host}$port"
        }

        private fun logRequest(method: String, endpointLabel: String, requestUrl: String) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "$method $endpointLabel $requestUrl")
            }
        }

        private fun logRawResponse(
            method: String,
            endpointLabel: String,
            requestUrl: String,
            status: Int,
            contentType: String?,
            responseText: String
        ) {
            if (!BuildConfig.DEBUG) return

            Log.d(TAG, "HTTP $status $method $endpointLabel $requestUrl contentType=${contentType.orEmpty()}")
            if (responseText.isBlank()) {
                Log.d(TAG, "Raw response body: <empty>")
                return
            }

            responseText.chunked(LOG_CHUNK_SIZE).forEachIndexed { index, chunk ->
                Log.d(TAG, "Raw response body[$index]: $chunk")
            }
        }
    }
}

class AuthApiException(message: String) : Exception(message)
