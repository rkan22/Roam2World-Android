package im.angry.openeuicc.auth

data class MobilePackagePurchaseRequest(
    val packageId: String?,
    val provider: String?,
    val packageName: String,
    val packageDescription: String?,
    val country: String?,
    val price: String,
    val role: String?,
    val customerFirstName: String? = null,
    val customerLastName: String? = null,
    val customerPhone: String? = null,
    val simIccid: String? = null
)

data class MobilePackagePurchaseResult(
    val orderId: String?,
    val orderNumber: String?,
    val status: String?,
    val packageName: String,
    val price: String,
    val balanceAfter: String?,
    val activation: MobileActivationDetails
)

data class MobileActivationDetails(
    val lpaCode: String?,
    val smdpAddress: String?,
    val matchingId: String?,
    val confirmationCodeRequired: Boolean,
    val qrCode: String?,
    val qrCodeUrl: String?,
    val iccid: String?,
    val esimId: String?
) {
    fun installCode(): String? =
        lpaCode
            ?: qrCode?.takeIf { it.startsWith("LPA:", ignoreCase = true) || it.startsWith("1\$") }
            ?: smdpAddress?.let { address ->
                listOf("1", address, matchingId.orEmpty(), "", if (confirmationCodeRequired) "1" else "")
                    .joinToString("\$")
                    .trimEnd('$')
            }
}
