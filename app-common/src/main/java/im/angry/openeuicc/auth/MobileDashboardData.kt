package im.angry.openeuicc.auth

data class MobileDashboardData(
    val currentBalance: String,
    val activeEsimCount: String,
    val recentOrders: List<MobileDashboardOrder>
)

data class MobileDashboardOrder(
    val title: String,
    val subtitle: String,
    val amount: String?,
    val status: String?
)
