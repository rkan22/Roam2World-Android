package im.angry.openeuicc.ui.b2b.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import im.angry.openeuicc.ui.b2b.models.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DashboardViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(DashboardState())
    val uiState: StateFlow<DashboardState> = _uiState.asStateFlow()

    init {
        loadDashboardData()
    }

    private fun loadDashboardData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            // Simulating API call
            _uiState.value = DashboardState(
                adminName = "Alex",
                walletBalance = 8540.50,
                totalSales = 1240,
                activeEsims = 85,
                recentPurchases = listOf(
                    RecentPurchase("1", "John Doe", "Turkey 5GB", 12.0, "USD", "10:30 AM", "Completed"),
                    RecentPurchase("2", "Sarah Smith", "Europe 10GB", 25.0, "USD", "Yesterday", "Completed")
                ),
                isLoading = false
            )
        }
    }

    fun onEvent(event: DashboardEvent) {
        when (event) {
            is DashboardEvent.Refresh -> loadDashboardData()
            is DashboardEvent.OnQuickActionClick -> {
                // Handle navigation or action
            }
            is DashboardEvent.OnPurchaseClick -> {
                // Handle navigation to details
            }
        }
    }
}
