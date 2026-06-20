package im.angry.openeuicc.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MobileEsimDataTest {
    @Test
    fun installCodeUsesExistingLpaPayload() {
        val esim = esim(lpaCode = "LPA:1\$rsp.example\$MATCH")

        assertEquals("LPA:1\$rsp.example\$MATCH", esim.installCode())
        assertEquals("LPA:1\$rsp.example\$MATCH", esim.qrPayload())
    }

    @Test
    fun installCodeBuildsPayloadFromSmdpAndMatchingId() {
        val esim = esim(
            smdpAddress = "rsp.example",
            matchingId = "MATCH"
        )

        assertEquals("1\$rsp.example\$MATCH", esim.installCode())
        assertEquals("LPA:1\$rsp.example\$MATCH", esim.qrPayload())
    }

    @Test
    fun installCodeStaysMissingWithoutActivationData() {
        assertNull(esim().installCode())
    }

    private fun esim(
        lpaCode: String? = null,
        smdpAddress: String? = null,
        matchingId: String? = null
    ): MobileEsim =
        MobileEsim(
            id = "1",
            iccid = null,
            provider = null,
            packageName = null,
            status = null,
            activationCode = null,
            lpaCode = lpaCode,
            smdpAddress = smdpAddress,
            matchingId = matchingId,
            confirmationCodeRequired = false,
            qrCode = null,
            qrCodeUrl = null,
            createdAt = null,
            orderNumber = null
        )
}
