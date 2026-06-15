package im.angry.openeuicc.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import im.angry.openeuicc.auth.AuthSession
import im.angry.openeuicc.auth.AuthTokenStore
import im.angry.openeuicc.auth.JwtUtils
import im.angry.openeuicc.auth.MobileWalletRequest
import im.angry.openeuicc.auth.Roam2WorldAuthApi
import im.angry.openeuicc.common.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Box

class WalletRequestHistoryActivity : ComponentActivity() {
    private val tokenStore by lazy { AuthTokenStore(this) }
    private val authApi by lazy { Roam2WorldAuthApi(BuildConfig.ROAM2WORLD_API_BASE_URL) }

    private var loading by mutableStateOf(false)
    private var requests by mutableStateOf<List<MobileWalletRequest>>(emptyList())
    private var errorMessage by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            WalletRequestHistoryScreen(
                loading = loading,
                requests = requests,
                errorMessage = errorMessage,
                onRefresh = { loadRequests() },
                onBack = { finish() }
            )
        }

        loadRequests()
    }

    private fun loadRequests() {
        lifecycleScope.launch {
            errorMessage = null
            loading = true

            val session = activeSessionOrReturnToLogin()
            if (session == null) {
                loading = false
                return@launch
            }

            val result = runCatching {
                authApi.walletRequests(session)
            }

            loading = false
            result
                .onSuccess { requests = it }
                .onFailure { errorMessage = it.message ?: "Wallet request history failed" }
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
}

@Composable
private fun WalletRequestHistoryScreen(
    loading: Boolean,
    requests: List<MobileWalletRequest>,
    errorMessage: String?,
    onRefresh: () -> Unit,
    onBack: () -> Unit
) {
    val orange = Color(0xFFFF7900)
    val bg = Color(0xFFF7F7FA)

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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(onClick = onBack, shape = RoundedCornerShape(16.dp)) {
                        Text("Geri")
                    }

                    Button(
                        onClick = onRefresh,
                        enabled = !loading,
                        colors = ButtonDefaults.buttonColors(containerColor = orange),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("Refresh", fontWeight = FontWeight.Bold)
                    }
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
                            text = "Wallet",
                            color = orange,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Request History",
                            color = Color.White,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = "Track your balance requests and review status.",
                            color = Color.White.copy(alpha = 0.72f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                errorMessage?.let {
                    WalletHistoryCard(title = "Error") {
                        Text(
                            text = it,
                            color = Color(0xFFDC2626),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                if (loading) {
                    WalletHistoryCard(title = "Loading") {
                        CircularProgressIndicator()
                    }
                }

                if (!loading && requests.isEmpty() && errorMessage == null) {
                    WalletHistoryCard(title = "No requests") {
                        Text(
                            text = "No wallet requests yet.",
                            color = Color(0xFF6B7280),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                requests.forEach { request ->
                    WalletRequestItem(request = request)
                }

                Spacer(modifier = Modifier.height(12.dp))
            }
        
                R2wBottomNav(
                    selected = R2wBottomTab.Wallet,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }
    }
}

@Composable
private fun WalletRequestItem(request: MobileWalletRequest) {
    WalletHistoryCard(title = "${request.amount} ${request.currency}") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Created: ${formatWalletRequestDate(request.createdAt).ifBlank { "-" }}",
                    color = Color(0xFF6B7280),
                    style = MaterialTheme.typography.bodyMedium
                )

                if (!request.note.isNullOrBlank()) {
                    Text(
                        text = request.note,
                        color = Color(0xFF17181C),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (!request.reviewedAt.isNullOrBlank()) {
                    Text(
                        text = "Reviewed: ${formatWalletRequestDate(request.reviewedAt)}",
                        color = Color(0xFF6B7280),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Text(
                text = request.statusLabel(),
                color = statusColor(request.status),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Black
            )
        }
    }
}

@Composable
private fun WalletHistoryCard(
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

private fun formatWalletRequestDate(value: String?): String {
    val raw = value?.trim().orEmpty()
    if (raw.isBlank()) return ""
    return runCatching {
        OffsetDateTime.parse(raw).format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm", Locale.ENGLISH))
    }.getOrElse { raw }
}

private fun statusColor(status: String): Color = when (status.lowercase()) {
    "approved", "completed", "paid" -> Color(0xFF15803D)
    "pending", "waiting", "review" -> Color(0xFFD97706)
    "rejected", "cancelled", "failed" -> Color(0xFFDC2626)
    else -> Color(0xFF6B7280)
}
