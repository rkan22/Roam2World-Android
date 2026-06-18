package im.angry.openeuicc.ui.compose.screens

import androidx.compose.runtime.Composable
import im.angry.openeuicc.auth.MobileDashboardData

@Composable
fun DashboardScreen(
    userName: String,
    data: MobileDashboardData?,
    onWalletClick: () -> Unit,
    onActionClick: (String) -> Unit
) {
    CompactDashboardScreen(
        userName = userName,
        data = data,
        onWalletClick = onWalletClick,
        onActionClick = onActionClick
    )
}
