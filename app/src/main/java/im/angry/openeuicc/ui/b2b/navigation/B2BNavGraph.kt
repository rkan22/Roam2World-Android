package im.angry.openeuicc.ui.b2b.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import im.angry.openeuicc.ui.b2b.dashboard.*

sealed class B2BScreen(val route: String) {
    object Dashboard : B2BScreen("dashboard")
    object Store : B2BScreen("store")
    object PackageDetail : B2BScreen("package_detail/{packageId}") {
        fun createRoute(packageId: String) = "package_detail/$packageId"
    }
    object PurchaseConfirmation : B2BScreen("purchase_confirmation/{packageId}") {
        fun createRoute(packageId: String) = "purchase_confirmation/$packageId"
    }
    object PurchaseSuccess : B2BScreen("purchase_success/{iccid}") {
        fun createRoute(iccid: String) = "purchase_success/$iccid"
    }
    object Inventory : B2BScreen("inventory")
    object Wallet : B2BScreen("wallet")
    object DeviceManager : B2BScreen("device_manager")
}

@Composable
fun B2BNavGraph(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = B2BScreen.Dashboard.route
    ) {
        composable(B2BScreen.Dashboard.route) {
            DashboardScreen(
                onBuyEsimClick = { navController.navigate(B2BScreen.Store.route) },
                onWalletClick = { navController.navigate(B2BScreen.Wallet.route) },
                onInventoryClick = { navController.navigate(B2BScreen.Inventory.route) },
                onDeviceManagerClick = { navController.navigate(B2BScreen.DeviceManager.route) }
            )
        }
        
        composable(B2BScreen.Store.route) {
            StoreScreen(
                onBackClick = { navController.popBackStack() },
                onPackageClick = { packageId ->
                    navController.navigate(B2BScreen.PackageDetail.createRoute(packageId))
                }
            )
        }

        composable(B2BScreen.PackageDetail.route) { backStackEntry ->
            val packageId = backStackEntry.arguments?.getString("packageId") ?: ""
            PackageDetailScreen(
                packageId = packageId,
                onBackClick = { navController.popBackStack() },
                onBuyClick = {
                    navController.navigate(B2BScreen.PurchaseConfirmation.createRoute(packageId))
                }
            )
        }

        composable(B2BScreen.PurchaseConfirmation.route) { backStackEntry ->
            val packageId = backStackEntry.arguments?.getString("packageId") ?: ""
            PurchaseConfirmationScreen(
                packageName = "Turkey Premium 10GB", // Mock
                price = "USD 18.50", // Mock
                onBackClick = { navController.popBackStack() },
                onConfirmClick = {
                    navController.navigate(B2BScreen.PurchaseSuccess.createRoute("89012345678901234567"))
                }
            )
        }

        composable(B2BScreen.PurchaseSuccess.route) { backStackEntry ->
            val iccid = backStackEntry.arguments?.getString("iccid") ?: ""
            PurchaseSuccessScreen(
                iccid = iccid,
                onDoneClick = { 
                    navController.navigate(B2BScreen.Dashboard.route) {
                        popUpTo(B2BScreen.Dashboard.route) { inclusive = true }
                    }
                },
                onInstallClick = { /* Trigger OpenEUICC Installation */ }
            )
        }

        composable(B2BScreen.Inventory.route) {
            EsimInventoryScreen(
                onBackClick = { navController.popBackStack() },
                onEsimClick = { /* Navigate to eSIM Detail */ }
            )
        }

        composable(B2BScreen.Wallet.route) {
            WalletDetailScreen(onBackClick = { navController.popBackStack() })
        }

        composable(B2BScreen.DeviceManager.route) {
            OpenEuiccManagerScreen(onBackClick = { navController.popBackStack() })
        }
    }
}
