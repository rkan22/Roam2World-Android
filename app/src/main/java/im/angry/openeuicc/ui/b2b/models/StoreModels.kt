package im.angry.openeuicc.ui.b2b.models

data class EsimPackage(
    val id: String,
    val name: String,
    val dataAmount: String,
    val validity: String,
    val price: Double,
    val currency: String,
    val country: String,
    val countryCode: String,
    val operatorName: String,
    val features: List<String> = emptyList(),
    val coverageCountries: List<String> = emptyList()
)

data class StoreState(
    val packages: List<EsimPackage> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val selectedFilter: String = "All"
)

sealed class StoreEvent {
    data class OnSearchQueryChange(val query: String) : StoreEvent()
    data class OnFilterSelect(val filter: String) : StoreEvent()
    data class OnPackageClick(val packageId: String) : StoreEvent()
}
