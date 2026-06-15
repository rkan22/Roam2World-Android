package im.angry.openeuicc.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import im.angry.openeuicc.auth.AuthSession
import im.angry.openeuicc.auth.AuthTokenStore
import im.angry.openeuicc.auth.JwtUtils
import im.angry.openeuicc.auth.MobilePackage
import im.angry.openeuicc.auth.Roam2WorldAuthApi
import im.angry.openeuicc.common.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment

class R2wStoreActivity : ComponentActivity() {
    private val tokenStore by lazy { AuthTokenStore(this) }
    private val authApi by lazy { Roam2WorldAuthApi(BuildConfig.ROAM2WORLD_API_BASE_URL) }

    private var loading by mutableStateOf(true)
    private var errorMessage by mutableStateOf<String?>(null)
    private var packages by mutableStateOf<List<MobilePackage>>(emptyList())
    private var userRole by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            R2wStoreScreen(
                loading = loading,
                errorMessage = errorMessage,
                packages = packages,
                userRole = userRole,
                onRefresh = { loadPackages() },
                onOpenOrders = {
                    startActivity(Intent(this, PurchaseHistoryActivity::class.java))
                },
                onOpenEsims = {
                    startActivity(Intent(this, MobileEsimsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
                },
                onOpenPackage = { mobilePackage ->
                    startActivity(PackageDetailActivity.createIntent(this, mobilePackage, userRole))
                },
                onClose = { finish() }
            )
        }

        loadPackages()
    }

    private fun loadPackages() {
        lifecycleScope.launch {
            loading = true
            errorMessage = null

            val session = activeSessionOrReturnToLogin()
            if (session == null) {
                loading = false
                return@launch
            }

            userRole = session.role

            val result = runCatching {
                authApi.packages(session)
            }

            loading = false

            result
                .onSuccess { catalog ->
                    val rawPackages = catalog.featuredPackages + catalog.packages

                    packages = rawPackages
                        .sortedBy { it.priceFor(session.role).priceSortValue() }

                    errorMessage = if (packages.isEmpty()) {
                        "Canlı paket bulunamadı. Backend panelinde paketleri kontrol et."
                    } else {
                        null
                    }
                }
                .onFailure {
                    packages = emptyList()
                    errorMessage = it.message ?: "Paketler yüklenemedi."
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

    private fun String.priceSortValue(): Double =
        replace(Regex("[^0-9.]"), "").trim().toDoubleOrNull() ?: 0.0
}

@Composable
private fun R2wStoreScreen(
    loading: Boolean,
    errorMessage: String?,
    packages: List<MobilePackage>,
    userRole: String?,
    onRefresh: () -> Unit,
    onOpenOrders: () -> Unit,
    onOpenEsims: () -> Unit,
    onOpenPackage: (MobilePackage) -> Unit,
    onClose: () -> Unit
) {
    val orange = Color(0xFFFF6A00)
    val bg = Color(0xFFF7F7FA)
    val scroll = rememberScrollState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedProvider by remember { mutableStateOf(PROVIDER_ALL) }

    val providerOptions = remember(packages) {
        listOf(PROVIDER_ALL) + packages
            .map { it.providerLabel().ifBlank { "Roam2World" } }
            .distinct()
            .sorted()
    }

    val filteredPackages = packages.filter { mobilePackage ->
        val providerMatches =
            selectedProvider == PROVIDER_ALL ||
                mobilePackage.providerLabel().ifBlank { "Roam2World" } == selectedProvider

        providerMatches && mobilePackage.matches(searchQuery)
    }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = bg) {
            Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scroll)
                    .padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 116.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Paket Satın Al",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF17181C)
                )

                Text(
                    text = "Canlı Roam2World backend kataloğundan eSIM veri paketleri.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF686B73)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onOpenOrders,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("Siparişlerim")
                    }

                    OutlinedButton(
                        onClick = onOpenEsims,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("eSIM’lerim")
                    }
                }

                when {
                    loading -> {
                        StateCard(
                            title = "Paketler yükleniyor",
                            body = "Roam2World backend katalog bilgisi alınıyor.",
                            dark = false
                        )
                    }

                    errorMessage != null -> {
                        StateCard(
                            title = "Paketler yüklenemedi",
                            body = errorMessage,
                            dark = true
                        )

                        Button(
                            onClick = onRefresh,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = orange)
                        ) {
                            Text("Tekrar Dene")
                        }
                    }

                    packages.isEmpty() -> {
                        StateCard(
                            title = "Paket yok",
                            body = "Canlı katalog şu anda boş görünüyor.",
                            dark = false
                        )

                        Button(
                            onClick = onRefresh,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = orange)
                        ) {
                            Text("Yenile")
                        }
                    }

                    else -> {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("Ülke, GB, sağlayıcı veya paket ara") }
                        )

                        ProviderFilterRow(
                            providers = providerOptions,
                            selectedProvider = selectedProvider,
                            onProviderSelected = { selectedProvider = it },
                            orange = orange
                        )

                        Text(
                            text = "${filteredPackages.size} / ${packages.size} canlı paket",
                            style = MaterialTheme.typography.labelLarge,
                            color = orange,
                            fontWeight = FontWeight.Bold
                        )

                        if (filteredPackages.isEmpty()) {
                            StateCard(
                                title = "Sonuç yok",
                                body = "Arama veya filtreyi temizleyerek tekrar dene.",
                                dark = false
                            )
                        } else {
                            filteredPackages.forEach { mobilePackage ->
                                LiveStorePackageCard(
                                    mobilePackage = mobilePackage,
                                    userRole = userRole,
                                    orange = orange,
                                    onClick = onOpenPackage
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.padding(top = 4.dp))

                OutlinedButton(
                    onClick = onClose,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Geri")
                }
            }
        
                R2wBottomNav(
                    selected = R2wBottomTab.Packages,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }
    }
}

@Composable
private fun ProviderFilterRow(
    providers: List<String>,
    selectedProvider: String,
    onProviderSelected: (String) -> Unit,
    orange: Color
) {
    val chipScroll = rememberScrollState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(chipScroll),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        providers.forEach { provider ->
            val selected = provider == selectedProvider
            val container = if (selected) orange else Color.White
            val textColor = if (selected) Color.White else Color(0xFF17181C)

            Button(
                onClick = { onProviderSelected(provider) },
                colors = ButtonDefaults.buttonColors(containerColor = container),
                shape = RoundedCornerShape(999.dp)
            ) {
                Text(
                    text = provider,
                    color = textColor,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

@Composable
private fun LiveStorePackageCard(
    mobilePackage: MobilePackage,
    userRole: String?,
    orange: Color,
    onClick: (MobilePackage) -> Unit
) {
    val price = mobilePackage.priceFor(userRole)
    val specs = listOfNotNull(
        mobilePackage.country.takeIf { it.isNotBlank() },
        mobilePackage.dataAmount?.takeIf { it.isNotBlank() },
        mobilePackage.validity?.takeIf { it.isNotBlank() }
    ).joinToString(" • ")

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
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = mobilePackage.cleanDisplayName(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF17181C)
                    )

                    Text(
                        text = specs.ifBlank { mobilePackage.providerLabel().ifBlank { "Roam2World eSIM" } },
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF686B73)
                    )
                }

                Text(
                    text = price,
                    style = MaterialTheme.typography.labelLarge,
                    color = orange,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .background(
                            color = orange.copy(alpha = 0.10f),
                            shape = RoundedCornerShape(999.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }

            mobilePackage.providerLabel().takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF8A8D96)
                )
            }

            Button(
                onClick = { onClick(mobilePackage) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = orange)
            ) {
                Text("Detayları Gör")
            }
        }
    }
}

@Composable
private fun StateCard(
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

private fun MobilePackage.cleanDisplayName(): String =
    name
        .replace("【ESIM】", "", ignoreCase = true)
        .replace("【Esim】", "", ignoreCase = true)
        .replace("【SIMCARD】", "", ignoreCase = true)
        .replace("(valid for 60 days)", "", ignoreCase = true)
        .replace("(Valid for 60 days)", "", ignoreCase = true)
        .replace(Regex("\\s+"), " ")
        .trim()
        .ifBlank { name }

private const val PROVIDER_ALL = "Tümü"
