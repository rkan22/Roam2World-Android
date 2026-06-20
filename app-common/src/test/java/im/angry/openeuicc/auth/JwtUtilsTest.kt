package im.angry.openeuicc.auth

import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [JwtUtils].
 *
 * Note: These tests use Java's Base64 (URL-safe, no padding) to construct
 * test JWT payloads, since Android's Base64 is unavailable in unit tests.
 * The logic under test in [JwtUtils] is the expiry comparison, which is
 * platform-independent.
 */
class JwtUtilsTest {

    @Test
    fun isExpiredReturnsTrueForMalformedToken() {
        assertTrue(JwtUtils.isExpired("not.a.jwt"))
    }

    @Test
    fun isExpiredReturnsTrueForEmptyToken() {
        assertTrue(JwtUtils.isExpired(""))
    }

    @Test
    fun isExpiredReturnsTrueForTokenWithOnlyOneSegment() {
        assertTrue(JwtUtils.isExpired("onlyone"))
    }

    @Test
    fun isExpiredReturnsTrueForTokenWithZeroExpiry() {
        // A token with exp=0 should always be expired
        assertTrue(JwtUtils.isExpired("header.${encodePayload(0)}.signature"))
    }

    @Test
    fun isExpiredReturnsTrueForPastExpiry() {
        val pastExpiry = (System.currentTimeMillis() / 1000L) - 3600L // 1 hour ago
        assertTrue(JwtUtils.isExpired("header.${encodePayload(pastExpiry)}.signature"))
    }

    @Test
    fun isExpiredReturnsFalseForFutureExpiry() {
        val futureExpiry = (System.currentTimeMillis() / 1000L) + 3600L // 1 hour from now
        assertFalse(JwtUtils.isExpired("header.${encodePayload(futureExpiry)}.signature"))
    }

    @Test
    fun isExpiredReturnsTrueForExpiryWithinLeeway() {
        // Expiry within the 60-second leeway window should be considered expired
        val nearExpiry = (System.currentTimeMillis() / 1000L) + 30L // 30 seconds from now
        assertTrue(JwtUtils.isExpired("header.${encodePayload(nearExpiry)}.signature"))
    }

    @Test
    fun isExpiredReturnsFalseForExpiryBeyondLeeway() {
        // Expiry beyond the 60-second leeway window should not be expired
        val beyondLeeway = (System.currentTimeMillis() / 1000L) + 120L // 2 minutes from now
        assertFalse(JwtUtils.isExpired("header.${encodePayload(beyondLeeway)}.signature"))
    }

    private fun encodePayload(exp: Long): String {
        val json = JSONObject().put("exp", exp).toString()
        return java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString(json.toByteArray(Charsets.UTF_8))
    }
}
