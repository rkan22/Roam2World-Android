package im.angry.openeuicc.auth

data class MobileOrderHistory(
    val orders: List<MobileOrder>
)

data class MobileOrder(
    val id: String?,
    val orderNumber: String?,
    val packageName: String,
    val price: String?,
    val status: String?,
    val createdAt: String?,
    val provider: String? = null,
    val esimId: String? = null,
    val esim: MobileEsim? = null
) {
    fun displayNumber(): String =
        orderNumber?.let { "#$it" }
            ?: id?.let { "#$it" }
            ?: "Order"

    fun statusLabel(): String? {
        val normalized = status?.trim()?.lowercase() ?: return null
        return when (normalized) {
            "confirmed" -> "Confirmed"
            "completed", "complete" -> "Completed"
            "failed", "failure" -> "Failed"
            "refunded", "refund" -> "Refunded"
            "pending", "pending_payment" -> "Pending"
            "pending_provider_balance" -> "Pending provider balance"
            else -> normalized.replace("_", " ").replaceFirstChar {
                if (it.isLowerCase()) it.titlecase() else it.toString()
            }
        }
    }
}
