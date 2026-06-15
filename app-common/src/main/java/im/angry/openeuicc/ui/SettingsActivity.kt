package im.angry.openeuicc.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Box

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            SettingsScreen(
                onBack = { finish() },
                onOpenNotificationSettings = {
                    runCatching {
                        startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                        })
                    }.onFailure {
                        startActivity(Intent(Settings.ACTION_SETTINGS))
                    }
                },
                onOpenLocaleSettings = {
                    startActivity(Intent(Settings.ACTION_LOCALE_SETTINGS))
                }
            )
        }
    }
}

@Composable
private fun SettingsScreen(
    onBack: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenLocaleSettings: () -> Unit
) {
    val orange = Color(0xFFFF7900)
    val bg = Color(0xFFF7F7FA)

    val notificationsEnabled = remember { mutableStateOf(true) }
    val biometricEnabled = remember { mutableStateOf(false) }
    val darkModeEnabled = remember { mutableStateOf(false) }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = bg) {
            Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedButton(onClick = onBack, shape = RoundedCornerShape(16.dp)) {
                    Text("Geri")
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(30.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF17181C))
                ) {
                    Column(
                        modifier = Modifier.padding(22.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Settings",
                            color = orange,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Uygulama Tercihleri",
                            color = Color.White,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = "Bildirim, güvenlik ve sistem ayarlarını yönet.",
                            color = Color.White.copy(alpha = 0.72f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                SettingsCard(title = "Tercihler") {
                    SettingsSwitchRow(
                        title = "Bildirimler",
                        subtitle = "Sipariş ve eSIM durum bildirimleri",
                        checked = notificationsEnabled.value,
                        onCheckedChange = { notificationsEnabled.value = it }
                    )
                    SettingsSwitchRow(
                        title = "Biometric",
                        subtitle = "Desteklenen cihazlarda hızlı giriş",
                        checked = biometricEnabled.value,
                        onCheckedChange = { biometricEnabled.value = it }
                    )
                    SettingsSwitchRow(
                        title = "Dark Mode",
                        subtitle = "Koyu tema tercihi",
                        checked = darkModeEnabled.value,
                        onCheckedChange = { darkModeEnabled.value = it }
                    )
                }

                SettingsCard(title = "Sistem") {
                    SettingsActionButton("Bildirim ayarlarını aç", orange, onOpenNotificationSettings)
                    SettingsActionButton("Dil ayarlarını aç", orange, onOpenLocaleSettings)
                }

                Spacer(modifier = Modifier.height(12.dp))
            }
        
                R2wBottomNav(
                    selected = R2wBottomTab.More,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }
    }
}

@Composable
private fun SettingsCard(
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
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color(0xFF17181C),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                color = Color(0xFF6B7280),
                style = MaterialTheme.typography.bodySmall
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingsActionButton(
    text: String,
    orange: Color,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(containerColor = orange),
        shape = RoundedCornerShape(18.dp)
    ) {
        Text(
            text = text,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 4.dp)
        )
    }
}
