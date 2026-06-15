package im.angry.openeuicc.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

class R2wInstallHelpActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            R2wInstallHelpScreen(
                onOpenActivationCode = {
                    startActivity(Intent(this, R2wActivationCodeActivity::class.java))
                },
                onClose = { finish() }
            )
        }
    }
}

@Composable
private fun R2wInstallHelpScreen(
    onOpenActivationCode: () -> Unit,
    onClose: () -> Unit
) {
    val orange = Color(0xFFFF6A00)
    val bg = Color(0xFFF7F7FA)

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = bg) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Kurulum Yardımı",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF17181C)
                )

                Text(
                    text = "eSIM profilini güvenli şekilde kurmak için adımları takip et.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF686B73)
                )

                Button(
                    onClick = onOpenActivationCode,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = orange)
                ) {
                    Text("Aktivasyon Kodu Gir")
                }

                HelpStepCard(
                    number = "1",
                    title = "Paketini seç",
                    subtitle = "Roam2World Store’dan ülke veya bölge paketini seç.",
                    orange = orange
                )

                HelpStepCard(
                    number = "2",
                    title = "QR veya aktivasyon kodunu hazırla",
                    subtitle = "Satın alma sonrası gelen QR kodu ya da aktivasyon kodunu kullan.",
                    orange = orange
                )

                HelpStepCard(
                    number = "3",
                    title = "OpenEUICC ile kur",
                    subtitle = "Profil kurulumu ve aktif etme işlemleri OpenEUICC altyapısıyla yapılır.",
                    orange = orange
                )

                HelpStepCard(
                    number = "4",
                    title = "Profili aktif et",
                    subtitle = "Seyahat öncesi doğru eSIM profilinin aktif olduğundan emin ol.",
                    orange = orange
                )

                Spacer(modifier = Modifier.weight(1f))

                OutlinedButton(
                    onClick = onClose,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Geri")
                }
            }
        }
    }
}

@Composable
private fun HelpStepCard(
    number: String,
    title: String,
    subtitle: String,
    orange: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = number,
                color = orange,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .background(
                        color = orange.copy(alpha = 0.10f),
                        shape = RoundedCornerShape(999.dp)
                    )
                    .padding(horizontal = 13.dp, vertical = 8.dp)
            )

            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF17181C)
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF686B73)
                )
            }
        }
    }
}
