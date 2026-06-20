package im.angry.openeuicc.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MobileOrderDataTest {

    @Test
    fun displayNumberUsesOrderNumber() {
        val order = order(orderNumber = "ORD-001")
        assertEquals("#ORD-001", order.displayNumber())
    }

    @Test
    fun displayNumberFallsBackToId() {
        val order = order(id = "abc123")
        assertEquals("#abc123", order.displayNumber())
    }

    @Test
    fun displayNumberFallsBackToOrderWhenBothNull() {
        val order = order()
        assertEquals("Order", order.displayNumber())
    }

    @Test
    fun statusLabelNormalizesConfirmed() {
        assertEquals("Confirmed", order(status = "confirmed").statusLabel())
    }

    @Test
    fun statusLabelNormalizesCompleted() {
        assertEquals("Completed", order(status = "completed").statusLabel())
    }

    @Test
    fun statusLabelNormalizesFailed() {
        assertEquals("Failed", order(status = "failed").statusLabel())
    }

    @Test
    fun statusLabelNormalizesRefunded() {
        assertEquals("Refunded", order(status = "refunded").statusLabel())
    }

    @Test
    fun statusLabelNormalizesPending() {
        assertEquals("Pending", order(status = "pending").statusLabel())
    }

    @Test
    fun statusLabelNormalizesPendingProviderBalance() {
        assertEquals("Pending provider balance", order(status = "pending_provider_balance").statusLabel())
    }

    @Test
    fun statusLabelReturnsNullForNullStatus() {
        assertNull(order(status = null).statusLabel())
    }

    @Test
    fun statusLabelCapitalizesUnknownStatus() {
        assertEquals("Custom status", order(status = "custom_status").statusLabel())
    }

    private fun order(
        id: String? = null,
        orderNumber: String? = null,
        packageName: String = "Test Package",
        status: String? = null
    ): MobileOrder = MobileOrder(
        id = id,
        orderNumber = orderNumber,
        packageName = packageName,
        price = null,
        status = status,
        createdAt = null
    )
}
