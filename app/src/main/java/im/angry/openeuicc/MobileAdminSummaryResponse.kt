package im.angry.openeuicc

data class MobileAdminSummaryResponse(
    val pendingResellerTopUpRequests: Int? = null,
    val resellerCount: Int? = null,
    val dealerCount: Int? = null,
    val walletSummary: String? = null
)
