package im.angry.openeuicc.auth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

    private fun parseTransactions(transactions: org.json.JSONArray?): List<MobileTransaction> {
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

    private fun firstArray(vararg values: org.json.JSONArray?): org.json.JSONArray? =
        values.firstOrNull { it != null }

    companion object {
        private const val MOBILE_LOGIN_PATH = "api/v1/mobile/auth/login/"
        private const val DASHBOARD_PATH = "api/v1/mobile/dashboard/"
        private const val WALLET_PATH = "api/v1/mobile/wallet/"
        private const val TRANSACTIONS_PATH = "api/v1/mobile/transactions/"
        private const val REFRESH_PATH = "api/v1/auth/refresh/"
        private const val LOGOUT_PATH = "api/v1/auth/logout/"
        private const val TIMEOUT_MS = 30_000
    }
}

class AuthApiException(message: String) : Exception(message)
