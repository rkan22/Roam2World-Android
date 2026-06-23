package im.angry.openeuicc.auth

data class MobileEsimList(
    val esims: List<MobileEsim>
)

data class MobileEsimLastRenewal(
    val provider: String? = null,
    val success: Boolean? = null,
    val message: String? = null,
    val code: String? = null,
    val orderNo: String? = null,
    val productName: String? = null,
    val productCode: String? = null,
    val createdTime: String? = null,
    val activatedEndTime: String? = null,
    val renewExpirationTime: String? = null,
    val latestActivationTime: String? = null,
    val orderStatus: String? = null,
    val profileStatus: String? = null
)

data class MobileEsim(
    val id: String?,
    val iccid: String?,
    val provider: String?,
    val packageName: String?,
    val status: String?,
    val activationCode: String?,
    val lpaCode: String?,
    val smdpAddress: String?,
    val matchingId: String?,
    val confirmationCodeRequired: Boolean,
    val qrCode: String?,
    val qrCodeUrl: String?,
    val createdAt: String?,
    val orderNumber: String?,
    val expiresAt: String? = null,
    val dataRemaining: String? = null,
    val dataUsed: String? = null,
    val lastRenewal: MobileEsimLastRenewal? = null,
    val orderId: String? = null,
    val customerFirstName: String? = null,
    val customerLastName: String? = null,
    val customerPhone: String? = null,
    val customerEmail: String? = null,
    val msisdn: String? = null,
    val activationDate: String? = null
) {
    fun title(): String =
        packageName
            ?: iccid?.let { "eSIM $it" }
            ?: "Roam2World eSIM"

    fun customerName(): String? =
        listOfNotNull(customerFirstName, customerLastName)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .takeIf { it.isNotBlank() }

    fun statusLabel(): String? {
        val normalized = status?.trim()?.lowercase() ?: return null
        return when (normalized) {
            "active", "activated", "enabled", "in_use", "inuse" -> "Active"
            "ready", "assigned", "provisioned" -> "Ready"
            "ready_to_install", "installable" -> "Ready to install"
            "pending", "processing", "ordered", "waiting" -> "Pending"
            "expired", "depleted", "terminated" -> "Expired"
            else -> normalized.replace("_", " ").replaceFirstChar {
                if (it.isLowerCase()) it.titlecase() else it.toString()
            }
        }
    }

    fun installCode(): String? =
        firstNotBlank(
            lpaCode?.takeIf { it.isInstallCode() },
            activationCode?.takeIf { it.isInstallCode() },
            qrCode?.takeIf { it.isInstallCode() }
        ) ?: smdpAddress?.let { address ->
            listOf("1", address, matchingId.orEmpty(), "", if (confirmationCodeRequired) "1" else "")
                .joinToString("\$")
                .trimEnd('$')
        }

    fun qrPayload(): String? =
        installCode()?.let {
            if (it.startsWith("LPA:", ignoreCase = true)) it else "LPA:$it"
        } ?: firstNotBlank(qrCode, activationCode, qrCodeUrl)

    private fun String.isInstallCode(): Boolean =
        startsWith("LPA:", ignoreCase = true) || startsWith("1$")

    private fun firstNotBlank(vararg values: String?): String? =
        values.firstOrNull { !it.isNullOrBlank() && it != "null" }
}
