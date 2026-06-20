package im.angry.openeuicc.auth

data class AuthSession(
    val accessToken: String,
    val refreshToken: String?,
    val email: String?,
    val displayName: String?,
    val role: String?,
    val accountSummary: String?,
    val savedAtMillis: Long = System.currentTimeMillis()
) {
    val authorizationHeader: String
        get() = "Bearer $accessToken"
}
