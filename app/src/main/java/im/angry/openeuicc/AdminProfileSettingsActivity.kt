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
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import im.angry.openeuicc.auth.AuthTokenStore
import im.angry.openeuicc.ui.LoginActivity
import im.angry.openeuicc.ui.compose.saas.R2wActionCard
import im.angry.openeuicc.ui.compose.saas.R2wMetricCard
import im.angry.openeuicc.ui.compose.saas.R2wSaasBottomNav
import im.angry.openeuicc.ui.compose.saas.R2wSaasCard
import im.angry.openeuicc.ui.compose.saas.R2wSaasColors
import im.angry.openeuicc.ui.compose.saas.R2wSaasHeader
import im.angry.openeuicc.ui.compose.saas.R2wSaasNavItem
import im.angry.openeuicc.ui.compose.theme.R2WTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AdminProfileSettingsActivity : ComponentActivity() {
    private val tokenStore by lazy { AuthTokenStore(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val role = remember { mutableStateOf("Admin") }
            val email = remember { mutableStateOf("-") }
            val notificationsEnabled = remember { mutableStateOf(true) }

            LaunchedEffect(Unit) {
                val session = withContext(Dispatchers.IO) { tokenStore.getSession() }
                role.value = session?.role?.takeIf { it.isNotBlank() } ?: "Admin"
                email.value = session?.email?.takeIf { it.isNotBlank() } ?: "-"
            }

            R2WTheme {
                AdminProfileSettingsSaasScreen(
                    role = role.value,
                    email = email.value,
                    notificationsEnabled = notificationsEnabled.value,
                    onNotificationsToggle = {
                        notificationsEnabled.value = it
                    },
                    onLogoutClick = {
                        logoutAdmin()
                    },
                    onBottomNavClick = { item ->
                        when (item) {
                            R2wSaasNavItem.Dashboard -> startActivity(Intent(this@AdminProfileSettingsActivity, MobileAdminActivity::class.java))
                            R2wSaasNavItem.Partners -> startActivity(Intent(this@AdminProfileSettingsActivity, AdminPartnersActivity::class.java))
                            R2wSaasNavItem.Orders -> startActivity(Intent(this@AdminProfileSettingsActivity, AdminOrdersOverviewActivity::class.java))
                            R2wSaasNavItem.Pricing -> startActivity(Intent(this@AdminProfileSettingsActivity, AdminPricingOverviewActivity::class.java))
                            R2wSaasNavItem.More -> startActivity(Intent(this@AdminProfileSettingsActivity, AdminMoreActivity::class.java))
                        }
                    }
                )
            }
        }
    }

    private fun logoutAdmin() {
        tokenStore.clear()
        startActivity(
            Intent(this, LoginActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
        )
        finish()
    }
}

@Composable
private fun AdminProfileSettingsSaasScreen(
    role: String,
    email: String,
    notificationsEnabled: Boolean,
    onNotificationsToggle: (Boolean) -> Unit,
    onLogoutClick: () -> Unit,
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
                    title = "Profile Settings",
                    subtitle = "Account, security and notification preferences.",
                    badge = role
                )
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    R2wMetricCard(
                        modifier = Modifier.weight(1f),
                        title = "Role",
                        value = role,
                        subtitle = "account type",
                        icon = Icons.Default.AdminPanelSettings,
                        tint = R2wSaasColors.Primary
                    )

                    R2wMetricCard(
                        modifier = Modifier.weight(1f),
                        title = "Session",
                        value = "Active",
                        subtitle = "logged in",
                        icon = Icons.Default.Security,
                        tint = R2wSaasColors.Green
                    )
                }
            }

            item {
                R2wSaasCard {
                    Text(
                        text = "Account",
                        color = R2wSaasColors.Text,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Black
                    )

                    Spacer(Modifier.height(12.dp))

                    R2wActionCard(
                        title = "Admin Email",
                        subtitle = email,
                        icon = Icons.Default.Person,
                        onClick = {},
                        tint = R2wSaasColors.Primary
                    )

                    Spacer(Modifier.height(9.dp))

                    R2wActionCard(
                        title = "Role & Permissions",
                        subtitle = "$role access level",
                        icon = Icons.Default.AdminPanelSettings,
                        onClick = {},
                        tint = R2wSaasColors.Purple
                    )
                }
            }

            item {
                R2wSaasCard {
                    Text(
                        text = "Preferences",
                        color = R2wSaasColors.Text,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Black
                    )

                    Spacer(Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Notifications",
                                color = R2wSaasColors.Text,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Black
                            )

                            Spacer(Modifier.height(4.dp))

                            Text(
                                text = "Receive wallet, provider and order alerts.",
                                color = R2wSaasColors.Muted,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Switch(
                            checked = notificationsEnabled,
                            onCheckedChange = onNotificationsToggle
                        )
                    }

                    Spacer(Modifier.height(9.dp))

                    R2wActionCard(
                        title = "Security Settings",
                        subtitle = "Password, 2FA and device sessions later",
                        icon = Icons.Default.Lock,
                        onClick = {},
                        tint = R2wSaasColors.Orange
                    )

                    Spacer(Modifier.height(9.dp))

                    R2wActionCard(
                        title = "App Settings",
                        subtitle = "Language, currency and display options later",
                        icon = Icons.Default.Settings,
                        onClick = {},
                        tint = R2wSaasColors.Green
                    )
                }
            }

            item {
                R2wSaasCard {
                    Text(
                        text = "Session",
                        color = R2wSaasColors.Text,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Black
                    )

                    Spacer(Modifier.height(12.dp))

                    Text(
                        text = "Logout clears the saved token and returns to the login screen.",
                        color = R2wSaasColors.Muted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 19.sp
                    )

                    Spacer(Modifier.height(12.dp))

                    Button(
                        onClick = onLogoutClick,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = R2wSaasColors.Red),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        androidx.compose.material3.Icon(
                            imageVector = Icons.Default.Logout,
                            contentDescription = null
                        )
                        Text(
                            text = "Logout",
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(18.dp)) }
        }
    }
}
