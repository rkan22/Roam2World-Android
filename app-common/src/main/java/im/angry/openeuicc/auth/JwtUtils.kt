package im.angry.openeuicc.auth

import android.util.Base64
import org.json.JSONObject

object JwtUtils {
    fun isExpired(token: String, leewaySeconds: Long = 60): Boolean = runCatching {
        val payload = token.split(".").getOrNull(1)
        if (payload == null) {
            true
        } else {
            val decoded = Base64.decode(payload, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
            val expiresAt = JSONObject(decoded.toString(Charsets.UTF_8)).optLong("exp", 0)
            expiresAt <= 0 || expiresAt <= (System.currentTimeMillis() / 1000L) + leewaySeconds
        }
    }.getOrDefault(true)
}
