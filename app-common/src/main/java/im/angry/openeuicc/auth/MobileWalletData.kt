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
