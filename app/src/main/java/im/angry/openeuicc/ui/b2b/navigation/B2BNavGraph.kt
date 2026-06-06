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
}

@Composable
fun B2BNavGraph(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = B2BScreen.Dashboard.route
    ) {
        composable(B2BScreen.Dashboard.route) {
            DashboardScreen() // In real app, pass navigation actions
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
    }
}
