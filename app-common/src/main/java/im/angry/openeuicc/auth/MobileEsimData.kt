package im.angry.openeuicc.auth

data class MobileEsimList(
    val esims: List<MobileEsim>
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
    val orderNumber: String?
) {
    fun title(): String =
        packageName
            ?: iccid?.let { "eSIM $it" }
            ?: "Roam2World eSIM"

    fun statusLabel(): String? {
        val normalized = status?.trim()?.lowercase() ?: return null
        return normalized.replace("_", " ").replaceFirstChar {
            if (it.isLowerCase()) it.titlecase() else it.toString()
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
        startsWith("LPA:", ignoreCase = true) || startsWith("1\$")

    private fun firstNotBlank(vararg values: String?): String? =
        values.firstOrNull { !it.isNullOrBlank() && it != "null" }
}
