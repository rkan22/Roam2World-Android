package im.angry.openeuicc

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Domain
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import im.angry.openeuicc.ui.compose.saas.R2wActionCard
import im.angry.openeuicc.ui.compose.saas.R2wMetricCard
import im.angry.openeuicc.ui.compose.saas.R2wSaasBottomNav
import im.angry.openeuicc.ui.compose.saas.R2wSaasCard
import im.angry.openeuicc.ui.compose.saas.R2wSaasColors
import im.angry.openeuicc.ui.compose.saas.R2wSaasHeader
import im.angry.openeuicc.ui.compose.saas.R2wSaasNavItem
import im.angry.openeuicc.ui.compose.theme.R2WTheme

class AdminWhiteLabelActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            R2WTheme {
                AdminWhiteLabelSaasScreen(
                    onBottomNavClick = { item ->
                        when (item) {
                            R2wSaasNavItem.Dashboard -> startActivity(Intent(this, MobileAdminActivity::class.java))
                            R2wSaasNavItem.Partners -> startActivity(Intent(this, AdminPartnersActivity::class.java))
                            R2wSaasNavItem.Orders -> startActivity(Intent(this, AdminOrdersOverviewActivity::class.java))
                            R2wSaasNavItem.Pricing -> startActivity(Intent(this, AdminPricingOverviewActivity::class.java))
                            R2wSaasNavItem.More -> startActivity(Intent(this, AdminMoreActivity::class.java))
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun AdminWhiteLabelSaasScreen(
    onBottomNavClick: (R2wSaasNavItem) -> Unit
) {
    Scaffold(
        containerColor = R2wSaasColors.Background,
        bottomBar = {
            R2wSaasBottomNav(
                selected = R2wSaasNavItem.More,
                onClick = onBottomNavClick
            )
        }
    ) { inner ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { Spacer(Modifier.height(8.dp)) }

            item {
                R2wSaasHeader(
                    title = "White-label",
                    subtitle = "Branding, app identity, support contact and custom domain.",
                    badge = "B2B"
                )
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    R2wMetricCard(
                        modifier = Modifier.weight(1f),
                        title = "Brands",
                        value = "0",
                        subtitle = "configured",
                        icon = Icons.Default.Storefront,
                        tint = R2wSaasColors.Primary
                    )

                    R2wMetricCard(
                        modifier = Modifier.weight(1f),
                        title = "Domains",
                        value = "0",
                        subtitle = "connected",
                        icon = Icons.Default.Domain,
                        tint = R2wSaasColors.Green
                    )
                }
            }

            item {
                R2wSaasCard {
                    Text(
                        text = "Brand Controls",
                        color = R2wSaasColors.Text,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Black
                    )

                    Spacer(Modifier.height(12.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        R2wActionCard(
                            title = "Brand Identity",
                            subtitle = "Logo, app name, splash screen and color palette",
                            icon = Icons.Default.Brush,
                            onClick = {},
                            tint = R2wSaasColors.Primary
                        )

                        R2wActionCard(
                            title = "Custom Domain",
                            subtitle = "Partner domain and portal URL",
                            icon = Icons.Default.Domain,
                            onClick = {},
                            tint = R2wSaasColors.Green
                        )

                        R2wActionCard(
                            title = "Mobile App Settings",
                            subtitle = "Partner app icon, package label and support info",
                            icon = Icons.Default.PhoneAndroid,
                            onClick = {},
                            tint = R2wSaasColors.Orange
                        )

                        R2wActionCard(
                            title = "Support Branding",
                            subtitle = "Email, WhatsApp, terms and help center links",
                            icon = Icons.Default.Settings,
                            onClick = {},
                            tint = R2wSaasColors.Purple
                        )
                    }
                }
            }

            item {
                R2wSaasCard {
                    Text(
                        text = "Next backend connection",
                        color = R2wSaasColors.Text,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Later this screen will save reseller-specific branding to backend and apply it on login, dashboard, package cards and emails.",
                        color = R2wSaasColors.Muted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 19.sp
                    )
                }
            }

            item { Spacer(Modifier.height(18.dp)) }
        }
    }
}
