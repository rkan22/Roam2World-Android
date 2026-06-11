package im.angry.openeuicc.auth

data class MobileDashboardData(
    val currentBalance: String,
    val todaySales: String,
    val monthlySales: String,
    val activeEsimCount: String,
    val expiredEsimCount: String,
    val recentOrders: List<MobileDashboardOrder>
)

data class MobileDashboardOrder(
    val id: String?,
    val orderNumber: String?,
    val title: String,
    val subtitle: String,
    val amount: String?,
    val status: String?
)
