package im.angry.openeuicc

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Api
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Webhook
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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

class AdminApiKeysActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            R2WTheme {
                AdminApiKeysSaasScreen(
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
private fun AdminApiKeysSaasScreen(
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
                    title = "API Keys",
                    subtitle = "Reseller API access, webhook setup and usage logs.",
                    badge = "SaaS"
                )
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    R2wMetricCard(
                        modifier = Modifier.weight(1f),
                        title = "Keys",
                        value = "0",
                        subtitle = "active keys",
                        icon = Icons.Default.Key,
                        tint = R2wSaasColors.Primary
                    )

                    R2wMetricCard(
                        modifier = Modifier.weight(1f),
                        title = "Webhooks",
                        value = "0",
                        subtitle = "configured",
                        icon = Icons.Default.Api,
                        tint = R2wSaasColors.Green
                    )
                }
            }

            item {
                R2wSaasCard {
                    Text(
                        text = "API Actions",
                        color = R2wSaasColors.Text,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Black
                    )

                    Spacer(Modifier.height(12.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        R2wActionCard(
                            title = "Generate API Key",
                            subtitle = "Create reseller key with permissions",
                            icon = Icons.Default.Key,
                            onClick = {},
                            tint = R2wSaasColors.Primary
                        )

                        R2wActionCard(
                            title = "Webhook URLs",
                            subtitle = "Order, top-up and usage callback endpoints",
                            icon = Icons.Default.Api,
                            onClick = {},
                            tint = R2wSaasColors.Green
                        )

                        R2wActionCard(
                            title = "Security Rules",
                            subtitle = "IP allowlist, rate limit and key rotation",
                            icon = Icons.Default.Security,
                            onClick = {},
                            tint = R2wSaasColors.Orange
                        )
                    }
                }
            }

            item {
                InfoBox(
                    title = "API-first SaaS layer",
                    message = "This screen will connect to backend API key CRUD later: create, revoke, rotate, permission scope, webhook secret and usage logs."
                )
            }

            item { Spacer(Modifier.height(18.dp)) }
        }
    }
}

@Composable
private fun InfoBox(title: String, message: String) {
    R2wSaasCard {
        Text(
            text = title,
            color = R2wSaasColors.Text,
            fontSize = 16.sp,
            fontWeight = FontWeight.Black
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = message,
            color = R2wSaasColors.Muted,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 19.sp
        )
    }
}
