package im.angry.openeuicc.auth

data class MobilePackageCatalog(
    val featuredPackages: List<MobilePackage>,
    val packages: List<MobilePackage>
)

data class MobilePackage(
    val id: String?,
    val name: String,
    val country: String,
    val countryCode: String?,
    val dataAmount: String?,
    val validity: String?,
    val basePrice: String?,
    val resellerPrice: String?,
    val dealerPrice: String?,
    val description: String?,
    val network: String?,
    val coverage: String?,
    val visibleToReseller: Boolean,
    val visibleToDealer: Boolean,
    val featured: Boolean
) {
    fun priceFor(role: String?): String =
        when (role?.lowercase()) {
            "reseller" -> resellerPrice ?: basePrice
            "dealer" -> dealerPrice ?: basePrice
            else -> basePrice ?: resellerPrice ?: dealerPrice
        } ?: "0"

    fun isVisibleFor(role: String?): Boolean =
        when (role?.lowercase()) {
            "reseller" -> visibleToReseller
            "dealer" -> visibleToDealer
            else -> visibleToReseller || visibleToDealer
        }

    fun matches(query: String): Boolean {
        if (query.isBlank()) return true
        val normalized = query.trim().lowercase()
        return listOfNotNull(
            name,
            country,
            countryCode,
            dataAmount,
            validity,
            description,
            network,
            coverage
        ).any { it.lowercase().contains(normalized) }
    }

    fun specs(): String =
        listOfNotNull(dataAmount, validity, network)
            .filter { it.isNotBlank() }
            .joinToString(" - ")

    fun visibilityLabel(): String =
        when {
            visibleToReseller && visibleToDealer -> "Reseller and dealer"
            visibleToReseller -> "Reseller"
            visibleToDealer -> "Dealer"
            else -> "Unavailable"
        }
}
