package im.angry.openeuicc.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp

class R2wActivationCodeActivity : ComponentActivity() {
    private var activationCode by mutableStateOf("")

    companion object {
        const val EXTRA_ACTIVATION_CODE = "r2w.activation.code"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        activationCode = intent.getStringExtra(EXTRA_ACTIVATION_CODE).orEmpty()

        setContent {
            R2wActivationCodeScreen(
                activationCode = activationCode,
                onActivationCodeChange = { activationCode = it },
                onInstall = { openOpenEuiccWizard(activationCode) },
                onClose = { finish() }
            )
        }
    }

    private fun openOpenEuiccWizard(rawCode: String) {
        val cleaned = rawCode.trim()

        if (cleaned.isBlank()) {
            Toast.makeText(this, "Aktivasyon kodu gir", Toast.LENGTH_SHORT).show()
            return
        }

        val lpaCode = when {
            cleaned.startsWith("LPA:", ignoreCase = true) -> cleaned
            cleaned.startsWith("1$") -> "LPA:$cleaned"
            else -> "LPA:1$$cleaned"
        }

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(lpaCode)).apply {
            setClass(this@R2wActivationCodeActivity, im.angry.openeuicc.ui.wizard.DownloadWizardActivity::class.java)
        }

        runCatching {
            startActivity(intent)
        }.onFailure {
            Toast.makeText(
                this,
                "OpenEUICC kurulum ekranı açılamadı",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}

@Composable
private fun R2wActivationCodeScreen(
    activationCode: String,
    onActivationCodeChange: (String) -> Unit,
    onInstall: () -> Unit,
    onClose: () -> Unit
) {
    val orange = Color(0xFFFF6A00)
    val bg = Color(0xFFF7F7FA)
    val canInstall = activationCode.trim().isNotEmpty()

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = bg) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Aktivasyon Kodu",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF17181C)
                )

                Text(
                    text = "Satın alma sonrası gelen eSIM aktivasyon kodunu buraya yapıştır.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF686B73)
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        OutlinedTextField(
                            value = activationCode,
                            onValueChange = onActivationCodeChange,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("LPA aktivasyon kodu") },
                            placeholder = { Text("LPA:1$... veya aktivasyon kodu") },
                            minLines = 3,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Characters
                            )
                        )

                        Text(
                            text = "Kod girildiğinde OpenEUICC kurulum sihirbazına aktarılır.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF686B73)
                        )

                        Button(
                            onClick = onInstall,
                            enabled = canInstall,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = orange)
                        ) {
                            Text("OpenEUICC ile Kur")
                        }
                    }
                }

                InfoCard(
                    title = "Güvenli kurulum",
                    body = "Kurulum işlemi Roam2World ekranından başlatılır, profil indirme ve eUICC işlemleri OpenEUICC wizard tarafından yapılır."
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
private fun InfoCard(
    title: String,
    body: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF17181C))
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = body,
                color = Color.White.copy(alpha = 0.78f),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
