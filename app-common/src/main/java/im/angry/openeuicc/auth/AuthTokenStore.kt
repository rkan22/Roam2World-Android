package im.angry.openeuicc.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class AuthTokenStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun save(session: AuthSession) {
        val serialized = JSONObject()
            .put("accessToken", session.accessToken)
            .put("refreshToken", session.refreshToken)
            .put("email", session.email)
            .put("displayName", session.displayName)
            .put("role", session.role)
            .put("accountSummary", session.accountSummary)
            .put("savedAtMillis", session.savedAtMillis)
            .toString()

        prefs.edit()
            .putString(KEY_SESSION, encrypt(serialized))
            .apply()
    }

    fun getSession(): AuthSession? {
        val encrypted = prefs.getString(KEY_SESSION, null) ?: return null
        return runCatching {
            JSONObject(decrypt(encrypted)).let { json ->
                AuthSession(
                    accessToken = json.getString("accessToken"),
                    refreshToken = json.optNullableString("refreshToken"),
                    email = json.optNullableString("email"),
                    displayName = json.optNullableString("displayName"),
                    role = json.optNullableString("role"),
                    accountSummary = json.optNullableString("accountSummary"),
                    savedAtMillis = json.optLong("savedAtMillis", System.currentTimeMillis())
                )
            }
        }.getOrElse {
            clear()
            null
        }
    }

    fun authorizationHeader(): String? = getSession()?.authorizationHeader

    fun clear() {
        prefs.edit().clear().apply()
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val cipherText = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        return JSONObject()
            .put("iv", cipher.iv.base64())
            .put("value", cipherText.base64())
            .toString()
    }

    private fun decrypt(value: String): String {
        val wrapper = JSONObject(value)
        val iv = wrapper.getString("iv").decodeBase64()
        val cipherText = wrapper.getString("value").decodeBase64()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        return cipher.doFinal(cipherText).toString(Charsets.UTF_8)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build()
            )
            generateKey()
        }
    }

    private fun ByteArray.base64(): String = Base64.encodeToString(this, Base64.NO_WRAP)

    private fun String.decodeBase64(): ByteArray = Base64.decode(this, Base64.NO_WRAP)

    private fun JSONObject.optNullableString(name: String): String? =
        if (isNull(name)) null else optString(name).takeIf { it.isNotBlank() }

    companion object {
        private const val PREFS_NAME = "roam2world_auth"
        private const val KEY_SESSION = "session"
        private const val KEY_ALIAS = "roam2world_auth_tokens"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH_BITS = 128
    }
}
