package im.angry.openeuicc.ui.b2b.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import im.angry.openeuicc.ui.b2b.dashboard.DashboardScreen
import im.angry.openeuicc.ui.b2b.dashboard.StoreScreen

sealed class B2BScreen(val route: String) {
    object Dashboard : B2BScreen("dashboard")
    object Store : B2BScreen("store")
    object PackageDetail : B2BScreen("package_detail/{packageId}") {
        fun createRoute(packageId: String) = "package_detail/$packageId"
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
    }
}
