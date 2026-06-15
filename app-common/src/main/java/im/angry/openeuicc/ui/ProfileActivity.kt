package im.angry.openeuicc.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import im.angry.openeuicc.auth.AuthSession
import im.angry.openeuicc.auth.AuthTokenStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

class ProfileActivity : ComponentActivity() {
    private val tokenStore by lazy { AuthTokenStore(this) }

    private var sessionState by mutableStateOf<AuthSession?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            ProfileScreen(
                session = sessionState,
                onBack = { finish() },
                onSettings = { startActivity(Intent(this, SettingsActivity::class.java)) },
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

        loadProfile()
    }

    private fun loadProfile() {
        lifecycleScope.launch {
            sessionState = withContext(Dispatchers.IO) { tokenStore.getSession() }
        }
    }

    private fun logout() {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { tokenStore.clear() }
            startActivity(
                Intent(this@ProfileActivity, LoginActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                }
            )
            finish()
        }
    }
}

@Composable
private fun ProfileScreen(
    session: AuthSession?,
    onBack: () -> Unit,
    onSettings: () -> Unit,
    onLogout: () -> Unit,
    onDashboard: () -> Unit,
    onPackages: () -> Unit,
    onWallet: () -> Unit,
    onEsims: () -> Unit
) {
    val displayName = session?.displayName?.takeIf { it.isNotBlank() } ?: "Roam2World User"
    val emailValue = session?.email?.takeIf { it.isNotBlank() } ?: "user@company.com"
    val roleValue = session?.role?.takeIf { it.isNotBlank() } ?: "B2B User"
    val prettyRole = roleValue.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    val permissions = permissionsForRole(roleValue)

    val orange = Color(0xFFFF7900)
    val bg = Color(0xFFF7F7FA)

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = bg) {
            Column(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedButton(onClick = onBack, shape = RoundedCornerShape(16.dp)) {
                        Text("Geri")
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(32.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF17181C))
                    ) {
                        Column(
                            modifier = Modifier.padding(22.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .width(58.dp)
                                        .height(58.dp)
                                        .clip(CircleShape)
                                        .background(orange),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = initials(displayName),
                                        color = Color.White,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Black
                                    )
                                }

                                Spacer(modifier = Modifier.width(14.dp))

                                Column {
                                    Text(
                                        text = displayName,
                                        color = Color.White,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Black
                                    )
                                    Text(
                                        text = emailValue,
                                        color = Color.White.copy(alpha = 0.72f),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }

                            Text(
                                text = prettyRole,
                                color = orange,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    ProfileCard(title = "Kişisel Bilgiler") {
                        ProfileLine("Full name", displayName)
                        ProfileLine("Email", emailValue)
                        ProfileLine("Phone", "Not provided")
                    }

                    ProfileCard(title = "Hesap") {
                        ProfileLine("Account type", "Roam2World B2B")
                        ProfileLine("Permissions", permissions)
                    }

                    ProfileCard(title = "Güvenlik") {
                        Button(
                            onClick = onSettings,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = orange),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Text(
                                text = "Settings",
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }

                        Button(
                            onClick = onLogout,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Text(
                                text = "Logout",
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                    }
                }

                ProfileBottomNav(
                    orange = orange,
                    onDashboard = onDashboard,
                    onPackages = onPackages,
                    onWallet = onWallet,
                    onEsims = onEsims
                )
            }
        }
    }
}

@Composable
private fun ProfileCard(
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
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
private fun ProfileLine(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            color = Color(0xFF6B7280),
            style = MaterialTheme.typography.labelMedium
        )
        Text(
            text = value,
            color = Color(0xFF17181C),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun ProfileBottomNav(
    orange: Color,
    onDashboard: () -> Unit,
    onPackages: () -> Unit,
    onWallet: () -> Unit,
    onEsims: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavText("Home", onDashboard)
            NavText("Store", onPackages)
            NavText("Wallet", onWallet)
            NavText("eSIMs", onEsims)
            Text(
                text = "More",
                color = orange,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Black
            )
        }
    }
}

@Composable
private fun NavText(text: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp)
    ) {
        Text(text)
    }
}

private fun initials(value: String): String {
    val parts = value.split(" ").filter { it.isNotBlank() }
    return parts.take(2).map { it.first().uppercaseChar() }.joinToString("").ifBlank { "R2W" }
}

private fun permissionsForRole(role: String): String = when (role.lowercase()) {
    "admin" -> "All modules • Reports • Wallet"
    "reseller" -> "Dealer management • Wallet • Reports"
    "dealer" -> "Store • Wallet • Orders"
    else -> "Store • Wallet • Reports"
}
