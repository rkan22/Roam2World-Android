package im.angry.openeuicc.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import im.angry.openeuicc.auth.AuthSession
import im.angry.openeuicc.auth.AuthTokenStore
import im.angry.openeuicc.auth.JwtUtils
import im.angry.openeuicc.auth.MobileWalletRequest
import im.angry.openeuicc.auth.Roam2WorldAuthApi
import im.angry.openeuicc.common.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val WalletBg = Color(0xFFF7FAFC)
private val WalletDark = Color(0xFF0F172A)
private val WalletMuted = Color(0xFF64748B)
private val WalletBlue = Color(0xFF2563EB)
private val WalletGreen = Color(0xFF16A34A)
private val WalletRed = Color(0xFFDC2626)
private val WalletOrange = Color(0xFFF97316)
private val WalletBorder = Color(0xFFE2E8F0)

class WalletApprovalsActivity : ComponentActivity() {
    private val tokenStore by lazy { AuthTokenStore(this) }
    private val api by lazy { Roam2WorldAuthApi(BuildConfig.ROAM2WORLD_API_BASE_URL) }
    private val uiScope = CoroutineScope(Dispatchers.Main)

    private var activeFilter by mutableStateOf("pending")
    private var requests by mutableStateOf<List<MobileWalletRequest>>(emptyList())
    private var loading by mutableStateOf(false)
    private var errorMessage by mutableStateOf<String?>(null)
    private var title by mutableStateOf("Admin Approvals")
    private var subtitle by mutableStateOf("Reseller wallet requests")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            WalletApprovalsScreen(
                title = title,
                subtitle = subtitle,
                activeFilter = activeFilter,
                requests = requests,
                loading = loading,
                errorMessage = errorMessage,
                onFilterChange = {
                    activeFilter = it
                    loadRequests()
                },
                onRefresh = { loadRequests() },
                onApprove = { id -> approve(id) },
                onReject = { id -> reject(id) },
                onBack = { finish() }
            )
        }

        loadRequests()
    }

    private fun loadRequests() {
        loading = true
        errorMessage = null

        uiScope.launch {
            val session = activeSessionOrReturnToLogin() ?: return@launch
            val role = session.role?.lowercase() ?: "dealer"
            val isAdmin = role == "admin" || role == "superadmin" || role == "staff"

            title = if (isAdmin) "Admin Approvals" else "Dealer Approvals"
            subtitle = if (isAdmin) "Reseller top-up requests" else "Dealer top-up requests"

            val result = runCatching {
                val all = withContext(Dispatchers.IO) {
                    api.walletApprovalRequests(session)
                }
                if (activeFilter == "all") all else all.filter {
                    it.status.equals(activeFilter, ignoreCase = true)
                }
            }

            loading = false
            result.onSuccess {
                requests = it
            }.onFailure {
                requests = emptyList()
                errorMessage = it.message ?: "Could not load requests."
            }
        }
    }

    private fun approve(id: String) {
        uiScope.launch {
            val session = activeSessionOrReturnToLogin() ?: return@launch
            loading = true

            val result = runCatching {
                withContext(Dispatchers.IO) {
                    api.approveWalletApprovalRequest(session, id, "Approved from mobile")
                }
            }

            loading = false
            Toast.makeText(
                this@WalletApprovalsActivity,
                result.fold({ "Approved" }, { it.message ?: "Approve failed" }),
                Toast.LENGTH_SHORT
            ).show()

            loadRequests()
        }
    }

    private fun reject(id: String) {
        uiScope.launch {
            val session = activeSessionOrReturnToLogin() ?: return@launch
            loading = true

            val result = runCatching {
                withContext(Dispatchers.IO) {
                    api.rejectWalletApprovalRequest(session, id, "Rejected from mobile")
                }
            }

            loading = false
            Toast.makeText(
                this@WalletApprovalsActivity,
                result.fold({ "Rejected" }, { it.message ?: "Reject failed" }),
                Toast.LENGTH_SHORT
            ).show()

            loadRequests()
        }
    }

    private suspend fun activeSessionOrReturnToLogin(): AuthSession? {
        val saved = withContext(Dispatchers.IO) { tokenStore.getSession() } ?: return redirectToLogin()
        if (!JwtUtils.isExpired(saved.accessToken)) return saved
        val refreshed = runCatching { api.refresh(saved) }.getOrNull() ?: return redirectToLogin()
        withContext(Dispatchers.IO) { tokenStore.save(refreshed) }
        return refreshed
    }

    private fun redirectToLogin(): AuthSession? {
        tokenStore.clear()
        startActivity(Intent(this, LoginActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        })
        finish()
        return null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WalletApprovalsScreen(
    title: String,
    subtitle: String,
    activeFilter: String,
    requests: List<MobileWalletRequest>,
    loading: Boolean,
    errorMessage: String?,
    onFilterChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onApprove: (String) -> Unit,
    onReject: (String) -> Unit,
    onBack: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(WalletBg)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                WalletHeader(
                    title = title,
                    subtitle = subtitle,
                    pendingCount = requests.count { it.status.equals("pending", ignoreCase = true) },
                    onBack = onBack,
                    onRefresh = onRefresh
                )
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("pending", "approved", "rejected", "all").forEach { status ->
                        FilterChip(
                            selected = activeFilter == status,
                            onClick = { onFilterChange(status) },
                            label = {
                                Text(
                                    status.replaceFirstChar { it.uppercase() },
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = WalletBlue,
                                selectedLabelColor = Color.White,
                                containerColor = Color.White,
                                labelColor = WalletDark
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = activeFilter == status,
                                borderColor = WalletBorder,
                                selectedBorderColor = WalletBlue
                            )
                        )
                    }
                }
            }

            if (loading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 42.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = WalletBlue)
                    }
                }
            }

            errorMessage?.let { message ->
                item {
                    WalletMessageCard(message = message)
                }
            }

            if (!loading && errorMessage == null && requests.isEmpty()) {
                item {
                    WalletMessageCard(message = "No $activeFilter requests.")
                }
            }

            items(requests, key = { it.id ?: it.hashCode().toString() }) { request ->
                WalletApprovalCard(
                    request = request,
                    onApprove = onApprove,
                    onReject = onReject
                )
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun WalletHeader(
    title: String,
    subtitle: String,
    pendingCount: Int,
    onBack: () -> Unit,
    onRefresh: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.Transparent
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF0F172A), Color(0xFF1D4ED8))
                    )
                )
                .padding(horizontal = 20.dp, vertical = 20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onBack,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.14f)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Back", color = Color.White, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = onRefresh,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Refresh", color = WalletBlue, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            Text(
                title,
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Black
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                subtitle,
                color = Color.White.copy(alpha = 0.82f),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                WalletHeaderStat("Pending", pendingCount.toString())
                WalletHeaderStat("Flow", "Top-up")
            }
        }
    }
}

@Composable
private fun WalletHeaderStat(label: String, value: String) {
    Surface(
        color = Color.White.copy(alpha = 0.14f),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(label, color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(value, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun WalletApprovalCard(
    request: MobileWalletRequest,
    onApprove: (String) -> Unit,
    onReject: (String) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp),
        color = Color.White,
        shape = RoundedCornerShape(26.dp),
        border = BorderStroke(1.dp, WalletBorder)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Request #${request.id ?: "-"}",
                        color = WalletDark,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "Created: ${request.createdAt ?: "-"}",
                        color = WalletMuted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                WalletStatusChip(request.statusLabel())
            }

            Text(
                "$${request.amount} ${request.currency}",
                color = WalletBlue,
                fontSize = 30.sp,
                fontWeight = FontWeight.Black
            )

            WalletInfoRow("Note", request.note?.ifBlank { "-" } ?: "-")

            if (request.status.equals("pending", ignoreCase = true) && !request.id.isNullOrBlank()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        onClick = { onApprove(request.id) },
                        colors = ButtonDefaults.buttonColors(containerColor = WalletGreen),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("Approve", fontWeight = FontWeight.Black)
                    }

                    Button(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        onClick = { onReject(request.id) },
                        colors = ButtonDefaults.buttonColors(containerColor = WalletRed),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("Reject", fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}

@Composable
private fun WalletInfoRow(label: String, value: String) {
    Surface(
        color = Color(0xFFF8FAFC),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, WalletBorder)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(label, color = WalletMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(value, color = WalletDark, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun WalletStatusChip(status: String) {
    val lower = status.lowercase()
    val color = when {
        lower.contains("approved") -> WalletGreen
        lower.contains("reject") -> WalletRed
        lower.contains("pending") -> WalletOrange
        else -> WalletMuted
    }

    Surface(
        color = color.copy(alpha = 0.12f),
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.28f))
    ) {
        Text(
            status,
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
        )
    }
}

@Composable
private fun WalletMessageCard(message: String) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp),
        color = Color.White,
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, WalletBorder)
    ) {
        Text(
            message,
            color = WalletMuted,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(22.dp)
        )
    }
}
