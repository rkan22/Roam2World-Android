package im.angry.openeuicc.auth

data class MobileDealerList(
    val dealers: List<MobileDealer>
)

data class MobileDealer(
    val id: String?,
    val name: String,
    val email: String?,
    val currentBalance: String,
    val status: String,
    val totalOrders: String,
    val revenue: String,
    val currency: String,
    val firstName: String? = null,
    val lastName: String? = null,
    val phoneNumber: String? = null,
    val countryCode: String? = null,
    val totalAllocated: String? = null,
    val totalSpent: String? = null,
    val createdAt: String? = null,
    val suspensionReason: String? = null,
    val recentOrders: List<MobileOrder> = emptyList()
) {
    fun statusLabel(): String {
        val normalized = status.trim().lowercase()
        return when (normalized) {
            "active" -> "Active"
            "inactive" -> "Inactive"
            "suspended" -> "Suspended"
            else -> normalized.replace("_", " ").replaceFirstChar {
                if (it.isLowerCase()) it.titlecase() else it.toString()
            }
        }
    }
}

data class MobileDealerAllocationResult(
    val amount: String,
    val currency: String,
    val resellerRemainingBalance: String?,
    val dealer: MobileDealer
)
