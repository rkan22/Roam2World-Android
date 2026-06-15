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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import im.angry.openeuicc.auth.AuthSession
import im.angry.openeuicc.auth.AuthTokenStore
import im.angry.openeuicc.auth.JwtUtils
import im.angry.openeuicc.auth.MobilePackagePurchaseRequest
import im.angry.openeuicc.auth.Roam2WorldAuthApi
import im.angry.openeuicc.common.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class R2wPackageDetailActivity : ComponentActivity() {
    private val tokenStore by lazy { AuthTokenStore(this) }
    private val authApi by lazy { Roam2WorldAuthApi(BuildConfig.ROAM2WORLD_API_BASE_URL) }

    private var loading by mutableStateOf(false)
    private var errorMessage by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val packageId = intent.getStringExtra(EXTRA_PACKAGE_ID).orEmpty()
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Travel eSIM"
        val data = intent.getStringExtra(EXTRA_DATA) ?: "10 GB"
        val validity = intent.getStringExtra(EXTRA_VALIDITY) ?: "30 gün"
        val region = intent.getStringExtra(EXTRA_REGION) ?: "Global"
        val provider = intent.getStringExtra(EXTRA_PROVIDER) ?: "Roam2World"
        val providerLabel = intent.getStringExtra(EXTRA_PROVIDER_LABEL) ?: provider
        val price = intent.getStringExtra(EXTRA_PRICE) ?: "USD 0"
        val role = intent.getStringExtra(EXTRA_ROLE)
        val description = intent.getStringExtra(EXTRA_DESCRIPTION).orEmpty()

        setContent {
            R2wPackageDetailScreen(
                packageId = packageId,
                title = title,
                data = data,
                validity = validity,
                region = region,
                providerLabel = providerLabel,
                price = price,
                description = description,
                loading = loading,
                errorMessage = errorMessage,
                onCreateOrder = {
                    openCustomerInfoFlow(
                        packageId = packageId,
                        provider = provider,
                        title = title,
                        data = data,
                        validity = validity,
                        region = region,
                        providerLabel = providerLabel,
                        description = description,
                        price = price,
                        role = role
                    )
                },
                onClose = { finish() }
            )
        }
    }

    private fun openCustomerInfoFlow(
        packageId: String,
        provider: String,
        title: String,
        data: String,
        validity: String,
        region: String,
        providerLabel: String,
        description: String,
        price: String,
        role: String?
    ) {
        val packageIntent = Intent().apply {
            putExtra("package.id", packageId)
            putExtra("package.provider", provider)
            putExtra("package.type", "esim")
            putExtra("package.name", title)
            putExtra("package.country", region)
            putExtra("package.country_code", "")
            putExtra("package.price", price)
            putExtra("package.role", role)
            putExtra("package.visibility", "Available")
            putExtra("package.data", data)
            putExtra("package.validity", validity)
            putExtra("package.network", providerLabel)
            putExtra("package.coverage", region)
            putExtra("package.description", description)
        }

        startActivity(CustomerInfoActivity.createIntent(this, packageIntent))
    }

    private fun purchasePackage(
        packageId: String,
        provider: String,
        title: String,
        description: String,
        region: String,
        price: String,
        role: String?
    ) {
        lifecycleScope.launch {
            loading = true
            errorMessage = null

            val session = activeSessionOrReturnToLogin()
            if (session == null) {
                loading = false
                return@launch
            }

            val result = runCatching {
                authApi.purchasePackage(
                    session,
                    MobilePackagePurchaseRequest(
                        packageId = packageId.takeIf { it.isNotBlank() },
                        provider = provider.takeIf { it.isNotBlank() },
                        packageName = title,
                        packageDescription = description.takeIf { it.isNotBlank() },
                        country = region.takeIf { it.isNotBlank() },
                        price = price,
                        role = role.takeIf { !it.isNullOrBlank() } ?: session.role
                    )
                )
            }

            loading = false

            result
                .onSuccess {
                    startActivity(PurchaseConfirmationActivity.createIntent(this@R2wPackageDetailActivity, it))
                }
                .onFailure {
                    errorMessage = it.message ?: "Satın alma işlemi başarısız oldu."
                }
        }
    }

    private suspend fun activeSessionOrReturnToLogin(): AuthSession? {
        val savedSession = withContext(Dispatchers.IO) {
            tokenStore.getSession()
        } ?: return redirectToLogin()

        if (!JwtUtils.isExpired(savedSession.accessToken)) return savedSession

        val refreshed = runCatching {
            authApi.refresh(savedSession)
        }.getOrNull() ?: return redirectToLogin()

        withContext(Dispatchers.IO) {
            tokenStore.save(refreshed)
        }

        return refreshed
    }

    private fun redirectToLogin(): AuthSession? {
        tokenStore.clear()
        startActivity(
            Intent(this, LoginActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
        )
        finish()
        return null
    }

    companion object {
        const val EXTRA_PACKAGE_ID = "r2w.package.id"
        const val EXTRA_TITLE = "r2w.package.title"
        const val EXTRA_DATA = "r2w.package.data"
        const val EXTRA_VALIDITY = "r2w.package.validity"
        const val EXTRA_REGION = "r2w.package.region"
        const val EXTRA_PROVIDER = "r2w.package.provider"
        const val EXTRA_PROVIDER_LABEL = "r2w.package.provider.label"
        const val EXTRA_PRICE = "r2w.package.price"
        const val EXTRA_ROLE = "r2w.package.role"
        const val EXTRA_DESCRIPTION = "r2w.package.description"
    }
}

@Composable
private fun R2wPackageDetailScreen(
    packageId: String,
    title: String,
    data: String,
    validity: String,
    region: String,
    providerLabel: String,
    price: String,
    description: String,
    loading: Boolean,
    errorMessage: String?,
    onCreateOrder: () -> Unit,
    onClose: () -> Unit
) {
    val orange = Color(0xFFFF6A00)
    val bg = Color(0xFFF7F7FA)
    val scroll = rememberScrollState()

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = bg) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scroll)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF17181C)
                )

                Text(
                    text = providerLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF686B73)
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(22.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        Text(
                            text = price,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = orange,
                            modifier = Modifier
                                .background(
                                    color = orange.copy(alpha = 0.10f),
                                    shape = RoundedCornerShape(999.dp)
                                )
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        )

                        DetailRow(label = "Bölge", value = region)
                        DetailRow(label = "Data", value = data)
                        DetailRow(label = "Geçerlilik", value = validity)
                        DetailRow(label = "Sağlayıcı", value = providerLabel)
                        if (packageId.isNotBlank()) {
                            DetailRow(label = "Paket ID", value = packageId)
                        }
                        DetailRow(label = "Kurulum", value = "OpenEUICC ile güvenli kurulum")
                    }
                }

                if (description.isNotBlank()) {
                    InfoCard(
                        title = "Açıklama",
                        body = description,
                        dark = false
                    )
                }

                InfoCard(
                    title = "Satın alma akışı",
                    body = "Bu buton önce müşteri bilgisi ekranını açar, ardından mevcut Roam2World backend satın alma akışıyla sipariş oluşturur.",
                    dark = true
                )

                errorMessage?.let {
                    InfoCard(
                        title = "Satın alma başarısız",
                        body = it,
                        dark = false
                    )
                }

                Button(
                    onClick = onCreateOrder,
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = orange)
                ) {
                    Text(if (loading) "Sipariş oluşturuluyor..." else "Sipariş Oluştur")
                }

                Spacer(modifier = Modifier.padding(top = 4.dp))

                OutlinedButton(
                    onClick = onClose,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !loading
                ) {
                    Text("Geri")
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            color = Color(0xFF686B73),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            color = Color(0xFF17181C),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1.35f)
        )
    }
}

@Composable
private fun InfoCard(
    title: String,
    body: String,
    dark: Boolean
) {
    val container = if (dark) Color(0xFF17181C) else Color.White
    val titleColor = if (dark) Color.White else Color(0xFF17181C)
    val bodyColor = if (dark) Color.White.copy(alpha = 0.78f) else Color(0xFF686B73)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = container),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                color = titleColor,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = body,
                color = bodyColor,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
