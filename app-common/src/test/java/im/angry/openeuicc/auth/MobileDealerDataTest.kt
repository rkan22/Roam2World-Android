package im.angry.openeuicc.auth

import org.junit.Assert.assertEquals
import org.junit.Test

class MobileDealerDataTest {

    @Test
    fun statusLabelNormalizesActive() {
        assertEquals("Active", dealer(status = "active").statusLabel())
    }

    @Test
    fun statusLabelNormalizesInactive() {
        assertEquals("Inactive", dealer(status = "inactive").statusLabel())
    }

    @Test
    fun statusLabelNormalizesSuspended() {
        assertEquals("Suspended", dealer(status = "suspended").statusLabel())
    }

    @Test
    fun statusLabelCapitalizesUnknownStatus() {
        assertEquals("Pending review", dealer(status = "pending_review").statusLabel())
    }

    @Test
    fun statusLabelHandlesUppercaseInput() {
        assertEquals("Active", dealer(status = "ACTIVE").statusLabel())
    }

    private fun dealer(
        status: String = "active"
    ): MobileDealer = MobileDealer(
        id = "1",
        name = "Test Dealer",
        email = "dealer@test.com",
        currentBalance = "100.00 USD",
        status = status,
        totalOrders = "5",
        revenue = "500.00 USD",
        currency = "USD"
    )
}
