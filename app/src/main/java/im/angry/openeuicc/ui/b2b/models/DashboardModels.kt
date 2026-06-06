package im.angry.openeuicc.ui.b2b.models

data class DashboardState(
    val adminName: String = "",
    val walletBalance: Double = 0.0,
    val currency: String = "USD",
    val totalSales: Int = 0,
    val activeEsims: Int = 0,
    val recentPurchases: List<RecentPurchase> = emptyList(),
    val isLoading: Boolean = false
)

data class RecentPurchase(
    val id: String,
    val customerName: String,
    val packageName: String,
    val amount: Double,
    val currency: String,
    val date: String,
    val status: String // "Completed", "Pending", "Failed"
)

sealed class DashboardEvent {
    object Refresh : DashboardEvent()
    data class OnQuickActionClick(val action: String) : DashboardEvent()
    data class OnPurchaseClick(val purchaseId: String) : DashboardEvent()
}
