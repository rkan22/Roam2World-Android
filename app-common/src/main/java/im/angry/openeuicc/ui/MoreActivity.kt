package im.angry.openeuicc.ui

import android.content.Intent
import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.HeadsetMic
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.filled.Source
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import im.angry.openeuicc.auth.AuthTokenStore
import im.angry.openeuicc.auth.Roam2WorldAuthApi
import im.angry.openeuicc.common.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val MoreBlue = Color(0xFF0F4FD7)
private val MoreDark = Color(0xFF06103A)
private val MoreBg = Color(0xFFF8FAFD)
private val MoreText = Color(0xFF20242C)
private val MoreMuted = Color(0xFF68707C)
private val MoreBorder = Color(0xFFE1E6EF)
private val MoreRed = Color(0xFFDC2626)

class MoreActivity : ComponentActivity() {
    private val tokenStore by lazy { AuthTokenStore(this) }
    private val api by lazy { Roam2WorldAuthApi(BuildConfig.ROAM2WORLD_API_BASE_URL) }

    private var unreadCount by mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        actionBar?.hide()
        window.statusBarColor = AndroidColor.rgb(248, 250, 253)
        window.navigationBarColor = AndroidColor.BLACK
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true

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
                onEsimHistory = { startActivity(Intent(this, MobileEsimHistoryActivity::class.java)) },
                onTransactions = { startActivity(Intent(this, TransactionsActivity::class.java)) },
                onReports = { startActivity(Intent(this, ReportsActivity::class.java)) },
                onWallet = { startActivity(Intent(this, WalletActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)) },
                onSupport = { startActivity(Intent(this, SupportActivity::class.java)) },
                onSettings = { startActivity(Intent(this, SettingsActivity::class.java)) },
                onLogs = { startActivity(Intent(this, LogsActivity::class.java)) },
                onLogout = { logout() },
                onDashboard = { startActivity(Intent(this, DashboardActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)) },
                onPackages = { startActivity(Intent(this, PackagesActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)) },
                onEsims = { startActivity(Intent(this, MobileEsimsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)) }
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
                withContext(Dispatchers.IO) { runCatching { api.mobileNotifications(session).unreadCount }.getOrDefault(0) }
            } else 0
        }
    }

    private fun logout() {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { tokenStore.clear() }
            startActivity(Intent(this@MoreActivity, LoginActivity::class.java).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK) })
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
    onEsimHistory: () -> Unit,
    onTransactions: () -> Unit,
    onReports: () -> Unit,
    onWallet: () -> Unit,
    onSupport: () -> Unit,
    onSettings: () -> Unit,
    onLogs: () -> Unit,
    onLogout: () -> Unit,
    onDashboard: () -> Unit,
    onPackages: () -> Unit,
    onEsims: () -> Unit
) {
    MaterialTheme {
        Surface(Modifier.fillMaxSize(), color = MoreBg) {
            Column(Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 100.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("More", color = MoreText, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                        NotificationBadge(unreadCount)
                    }

                    MoreHero()

                    MoreCard("Business Center") {
                        MoreRow("Wallet", "Balance, requests and transactions", Icons.Default.AccountBalanceWallet, onWallet)
                        MoreRow("Orders", "Purchase history and order details", Icons.Default.ReceiptLong, onOrders)
                        MoreRow("eSIM History", "Lifecycle and purchased profile records", Icons.Default.History, onEsimHistory)
                        MoreRow("Reports", "Revenue, provider and dealer analytics", Icons.Default.Assessment, onReports)
                        val context = LocalContext.current
                        MoreRow("Notifications", "$unreadCount unread mobile alerts", Icons.Default.Notifications) { context.startActivity(Intent(context, MobileNotificationsActivity::class.java)) }
                    }

                    MoreCard("Account") {
                        MoreRow("Profile", "Account details and permissions", Icons.Default.Person, onProfile)
                        MoreRow("Customers", "Customer management", Icons.Default.People, onCustomers)
                    }

                    MoreCard("eSIM Tools") {
                        MoreRow("OpenEUICC", "Native eUICC integration tools", Icons.Default.SimCard, onOpenEuicc)
                        MoreRow("TGT Recharge", "Top-up and renewal flow", Icons.Default.Sync, onTgtRecharge)
                        MoreRow("TGT Check GB", "Check remaining package data", Icons.Default.Inventory2, onTgtCheckGb)
                        MoreRow("Vodafone Renewal", "Vodafone renewal request", Icons.Default.CreditCard, onVodafoneRenewal)
                    }

                    MoreCard("Help & Settings") {
                        MoreRow("Support", "Help and quick actions", Icons.Default.HeadsetMic, onSupport)
                        MoreRow("Settings", "App preferences", Icons.Default.Settings, onSettings)
                        MoreRow("Logs", "View and save recent debug logs", Icons.Default.Source, onLogs)
                    }

                    Button(onClick = onLogout, modifier = Modifier.fillMaxWidth().height(54.dp), colors = ButtonDefaults.buttonColors(containerColor = MoreRed), shape = RoundedCornerShape(14.dp)) {
                        Icon(Icons.Default.PowerSettingsNew, null, tint = Color.White, modifier = Modifier.size(21.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Logout", color = Color.White, fontWeight = FontWeight.ExtraBold)
                    }
                }

                R2wBottomNav(selected = R2wBottomTab.More)
            }
        }
    }
}

@Composable
private fun NotificationBadge(unreadCount: Int) {
    val text = if (unreadCount > 99) "99+" else unreadCount.toString()
    Box(Modifier.clip(RoundedCornerShape(50)).background(Color(0xFFEAF0FF)).padding(horizontal = 12.dp, vertical = 7.dp)) {
        Text("$text alerts", color = MoreBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun MoreHero() {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(MoreDark), elevation = CardDefaults.cardElevation(3.dp)) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(58.dp).clip(CircleShape).background(MoreBlue), contentAlignment = Alignment.Center) {
                Text("R2W", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Black)
            }
            Column(Modifier.padding(start = 14.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Roam2World B2B", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold)
                Text("Business tools, account actions and eSIM operations", color = Color.White.copy(alpha = .72f), fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun MoreCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(Color.White), border = BorderStroke(1.dp, MoreBorder), elevation = CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, color = MoreText, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            HorizontalDivider(color = MoreBorder)
            content()
        }
    }
}

@Composable
private fun MoreRow(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth().height(60.dp), shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, MoreBorder), colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(38.dp).clip(CircleShape).background(Color(0xFFEAF0FF)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = MoreBlue, modifier = Modifier.size(20.dp))
            }
            Column(Modifier.padding(start = 12.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, color = MoreText, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, color = MoreMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text("›", color = MoreMuted, fontSize = 24.sp, fontWeight = FontWeight.Black)
        }
    }
}
