package im.angry.openeuicc

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import im.angry.openeuicc.ui.compose.screens.admin.AdminBottomNavItem
import im.angry.openeuicc.ui.compose.screens.admin.AdminPricingOverviewScreen
import im.angry.openeuicc.ui.compose.theme.R2WTheme

class AdminPricingOverviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            R2WTheme {
                AdminPricingOverviewScreen(
                    onOpenPricingClick = {
                        startActivity(Intent(this@AdminPricingOverviewActivity, AdminPricingActivity::class.java))
                    },
                    onProviderMarkupsClick = {
                        startActivity(Intent(this@AdminPricingOverviewActivity, AdminPricingActivity::class.java))
                    },
                    onReportsClick = {
                        startActivity(Intent(this@AdminPricingOverviewActivity, AdminReportsOverviewActivity::class.java))
                    },
                    onBottomNavClick = { item ->
                        when (item) {
                            AdminBottomNavItem.Dashboard -> startActivity(Intent(this@AdminPricingOverviewActivity, MobileAdminActivity::class.java))
                            AdminBottomNavItem.Partners -> startActivity(Intent(this@AdminPricingOverviewActivity, AdminPartnersActivity::class.java))
                            AdminBottomNavItem.Orders -> startActivity(Intent(this@AdminPricingOverviewActivity, AdminOrdersOverviewActivity::class.java))
                            AdminBottomNavItem.Pricing -> Unit
                            AdminBottomNavItem.More -> startActivity(Intent(this@AdminPricingOverviewActivity, AdminMoreActivity::class.java))
                        }
                    }
                )
            }
        }
    }
}
