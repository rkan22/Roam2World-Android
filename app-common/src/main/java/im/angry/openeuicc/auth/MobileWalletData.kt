package im.angry.openeuicc.auth

data class MobileWalletData(
    val currentBalance: String,
    val transactions: List<MobileTransaction>
)

data class MobileTransaction(
    val title: String,
    val subtitle: String,
    val amount: String,
    val status: String?
)

data class MobileWalletRequest(
    val id: String?,
    val amount: String,
    val currency: String,
    val status: String,
    val note: String?,
    val createdAt: String?,
    val reviewedAt: String?
) {
    fun statusLabel(): String = status
        .replace("_", " ")
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
}
