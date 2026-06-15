package im.angry.openeuicc.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import im.angry.openeuicc.auth.AuthTokenStore
import im.angry.openeuicc.auth.Roam2WorldAuthApi
import im.angry.openeuicc.common.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

class MoreActivity : ComponentActivity() {
    private val tokenStore by lazy { AuthTokenStore(this) }
    private val api by lazy { Roam2WorldAuthApi(BuildConfig.ROAM2WORLD_API_BASE_URL) }

    private var unreadCount by mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MoreScreen(
                unreadCount = unreadCount,
                onProfile = { startActivity(Intent(this, ProfileActivity::class.java)) },
                onCustomers = { startActivity(Intent(this, CustomersActivity::class.java)) },
                onOpenEuicc = { startActivity(Intent(this, OpenEuiccIntegrationActivity::class.java)) },
                onTgtRecharge = { startActivity(Intent(this, TgtSimRechargeActivity::class.java)) },
                onTgtCheckGb = { startActivity(Intent(this, TgtCheckGbActivity::class.java)) },
                onVodafoneRenewal = { startActivity(Intent(this, VodafoneRenewalActivity::class.java)) },
                onOrders = { startActivity(Intent(this, PurchaseHistoryActivity::class.java)) },
                onTransactions = { startActivity(Intent(this, TransactionsActivity::class.java)) },
                onReports = { startActivity(Intent(this, ReportsActivity::class.java)) },
                onSupport = { startActivity(Intent(this, SupportActivity::class.java)) },
                onSettings = { startActivity(Intent(this, SettingsActivity::class.java)) },
                onLogs = { startActivity(Intent(this, LogsActivity::class.java)) },
                onLogout = { logout() },
                onDashboard = {
                    startActivity(Intent(this, DashboardActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
                },
                onPackages = {
                    startActivity(Intent(this, PackagesActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
                },
                onWallet = {
                    startActivity(Intent(this, WalletActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
                },
                onEsims = {
                    startActivity(Intent(this, MobileEsimsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
                }
            )
        }

        loadNotificationBadge()
    }

    override fun onResume() {
        super.onResume()
        loadNotificationBadge()
    }

    private fun loadNotificationBadge() {
        lifecycleScope.launch {
            val session = withContext(Dispatchers.IO) { tokenStore.getSession() }
            unreadCount = if (session != null) {
                withContext(Dispatchers.IO) {
                    runCatching { api.mobileNotifications(session).unreadCount }.getOrDefault(0)
                }
            } else {
                0
            }
        }
    }

    private fun logout() {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { tokenStore.clear() }
            startActivity(
                Intent(this@MoreActivity, LoginActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                }
            )
            finish()
        }
    }
}

@Composable
private fun MoreScreen(
    unreadCount: Int,
    onProfile: () -> Unit,
    onCustomers: () -> Unit,
    onOpenEuicc: () -> Unit,
    onTgtRecharge: () -> Unit,
    onTgtCheckGb: () -> Unit,
    onVodafoneRenewal: () -> Unit,
    onOrders: () -> Unit,
    onTransactions: () -> Unit,
    onReports: () -> Unit,
    onSupport: () -> Unit,
    onSettings: () -> Unit,
    onLogs: () -> Unit,
    onLogout: () -> Unit,
    onDashboard: () -> Unit,
    onPackages: () -> Unit,
    onWallet: () -> Unit,
    onEsims: () -> Unit
) {
    val orange = Color(0xFFFF7900)
    val bg = Color(0xFFF7F7FA)

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = bg) {
            Column(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 116.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    MoreHero(orange = orange)

                    MoreCard(title = "Account") {
                        MoreRow("Profile", "Account details and permissions", "👤", onProfile)
                        MoreRow("Customers", "Customer management", "👥", onCustomers)
                    }

                    MoreCard(title = "eSIM Tools") {
                        MoreRow("OpenEUICC", "Native eUICC integration tools", "📲", onOpenEuicc)
                        MoreRow("TGT Recharge", "Top-up and renewal flow", "🔄", onTgtRecharge)
                        MoreRow("TGT Check GB", "Check remaining package data", "📶", onTgtCheckGb)
                        MoreRow("Vodafone Renewal", "Vodafone renewal request", "🧾", onVodafoneRenewal)
                    }

                    MoreCard(title = "Business") {
                        MoreRow("Orders", "Purchase history and order details", "🛒", onOrders)
                        MoreRow("Transactions", "Search and filter all order transactions", "💳", onTransactions)
                        MoreRow("Reports", "Business reports", "📊", onReports)
                        val context = LocalContext.current
                        MoreRow("Notifications ($unreadCount)", "Mobile notifications and order alerts", "🔔") {
                            context.startActivity(Intent(context, MobileNotificationsActivity::class.java))
                        }
                    }

                    MoreCard(title = "Help & Settings") {
                        MoreRow("Support", "Help and quick actions", "💬", onSupport)
                        MoreRow("Settings", "App preferences", "⚙️", onSettings)
                        MoreRow("Logs", "View and save recent debug logs", "LOG", onLogs)
                    }

                    Button(
                        onClick = onLogout,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text(
                            text = "Logout",
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 6.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                }

                R2wBottomNav(
                    selected = R2wBottomTab.More
                )
            }
        }
    }
}

@Composable
private fun MoreHero(orange: Color) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF17181C))
    ) {
        Row(
            modifier = Modifier.padding(22.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(58.dp)
                    .height(58.dp)
                    .clip(CircleShape)
                    .background(orange),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "R2W",
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Black
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "More",
                    color = Color.White,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = "Roam2World tools and account actions",
                    color = Color.White.copy(alpha = 0.72f),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun MoreCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = title,
                color = Color(0xFF17181C),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            HorizontalDivider()
            content()
        }
    }
}

@Composable
private fun MoreRow(
    title: String,
    subtitle: String,
    symbol: String,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = symbol,
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = Color(0xFF17181C),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = subtitle,
                    color = Color(0xFF6B7280),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Text(
                text = "›",
                color = Color(0xFF9CA3AF),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

