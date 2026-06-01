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

    private fun postJson(path: String, body: JSONObject, authorization: String? = null): JSONObject {
        val connection = (URL(resolve(path)).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            doOutput = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json")
            authorization?.let { setRequestProperty("Authorization", it) }
        }

        return try {
            connection.outputStream.use {
                it.write(body.toString().toByteArray(Charsets.UTF_8))
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

    companion object {
        private const val MOBILE_LOGIN_PATH = "api/v1/mobile/auth/login/"
        private const val REFRESH_PATH = "api/v1/auth/refresh/"
        private const val LOGOUT_PATH = "api/v1/auth/logout/"
        private const val TIMEOUT_MS = 30_000
    }
}

class AuthApiException(message: String) : Exception(message)
