package im.angry.openeuicc.auth

import org.junit.Assert.assertEquals
import org.junit.Test

class Roam2WorldAuthApiTest {
    @Test
    fun loginEndpointUsesBackendApiBaseUrl() {
        assertEquals(EXPECTED_LOGIN_URL, Roam2WorldAuthApi(BACKEND_BASE_URL).loginEndpointUrl)
    }

    @Test
    fun mobileEndpointUrlsIncludeDashboardSurface() {
        val endpoints = Roam2WorldAuthApi(BACKEND_BASE_URL).mobileEndpointUrls

        assertEquals("$BACKEND_BASE_URL/api/v1/mobile/dashboard/", endpoints["mobile dashboard"])
        assertEquals("$BACKEND_BASE_URL/api/v1/mobile/orders/", endpoints["mobile orders"])
        assertEquals("$BACKEND_BASE_URL/api/v1/mobile/packages/", endpoints["mobile packages"])
        assertEquals("$BACKEND_BASE_URL/api/v1/mobile/wallet/", endpoints["mobile wallet"])
        assertEquals("$BACKEND_BASE_URL/api/v1/mobile/wallet/requests/", endpoints["mobile wallet requests"])
        assertEquals("$BACKEND_BASE_URL/api/v1/mobile/esims/", endpoints["mobile eSIMs"])
    }

    @Test
    fun loginEndpointDropsApiPathFromConfiguredBaseUrl() {
        assertEquals(EXPECTED_LOGIN_URL, Roam2WorldAuthApi("$BACKEND_BASE_URL/api/v1").loginEndpointUrl)
    }

    @Test
    fun loginEndpointRewritesPartnersFrontendLoginUrl() {
        assertEquals(EXPECTED_LOGIN_URL, Roam2WorldAuthApi("https://partners.roam2world.com/login").loginEndpointUrl)
    }

    @Test
    fun loginEndpointUsesBackendForBlankBaseUrl() {
        assertEquals(EXPECTED_LOGIN_URL, Roam2WorldAuthApi(" ").loginEndpointUrl)
    }

    private companion object {
        private const val BACKEND_BASE_URL = "https://roam2world-panels-backend.onrender.com"
        private const val EXPECTED_LOGIN_URL =
            "https://roam2world-panels-backend.onrender.com/api/v1/mobile/auth/login/"
    }
}
