package im.angry.openeuicc

data class WalletRequestDto(
    val id: String,
    val amount: Double,
    val status: String? = null,
    val createdAt: String? = null,

    val dealerId: String? = null,
    val dealerName: String? = null,

    val resellerId: String? = null,
    val resellerName: String? = null
)
