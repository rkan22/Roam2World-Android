package im.angry.openeuicc.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import im.angry.openeuicc.auth.AuthSession
import im.angry.openeuicc.auth.AuthTokenStore
import im.angry.openeuicc.auth.JwtUtils
import im.angry.openeuicc.auth.MobileWalletRequest
import im.angry.openeuicc.auth.Roam2WorldAuthApi
import im.angry.openeuicc.common.BuildConfig
import im.angry.openeuicc.ui.compose.saas.R2wMetricCard
import im.angry.openeuicc.ui.compose.saas.R2wSaasBottomNav
import im.angry.openeuicc.ui.compose.saas.R2wSaasCard
import im.angry.openeuicc.ui.compose.saas.R2wSaasColors
import im.angry.openeuicc.ui.compose.saas.R2wSaasHeader
import im.angry.openeuicc.ui.compose.saas.R2wSaasNavItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
            WalletApprovalsSaasScreen(
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

@Composable
private fun WalletApprovalsSaasScreen(
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
    val pending = requests.count { it.status.equals("pending", ignoreCase = true) }
    val approved = requests.count { it.status.equals("approved", ignoreCase = true) }
    val rejected = requests.count { it.status.equals("rejected", ignoreCase = true) }

    Scaffold(
        containerColor = R2wSaasColors.Background,
        bottomBar = {
            R2wSaasBottomNav(
                selected = R2wSaasNavItem.More,
                onClick = { item ->
                    when (item) {
                        R2wSaasNavItem.Dashboard -> onBack()
                        R2wSaasNavItem.Partners -> onBack()
                        R2wSaasNavItem.Orders -> onBack()
                        R2wSaasNavItem.Pricing -> onBack()
                        R2wSaasNavItem.More -> Unit
                    }
                }
            )
        }
    ) { inner ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { Spacer(Modifier.height(8.dp)) }

            item {
                R2wSaasHeader(
                    title = title,
                    subtitle = "$subtitle • ${requests.size} visible wallet requests.",
                    badge = if (loading) "Loading" else "Live API"
                )
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    R2wMetricCard(
                        modifier = Modifier.weight(1f),
                        title = "Pending",
                        value = pending.toString(),
                        subtitle = "waiting",
                        icon = Icons.Default.HourglassTop,
                        tint = R2wSaasColors.Orange
                    )

                    R2wMetricCard(
                        modifier = Modifier.weight(1f),
                        title = "Approved",
                        value = approved.toString(),
                        subtitle = "accepted",
                        icon = Icons.Default.CheckCircle,
                        tint = R2wSaasColors.Green
                    )
                }
            }

            item {
                R2wSaasCard {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        WalletFilterChip("pending", "Pending", activeFilter, onFilterChange)
                        WalletFilterChip("approved", "Approved", activeFilter, onFilterChange)
                        WalletFilterChip("rejected", "Rejected", activeFilter, onFilterChange)
                        WalletFilterChip("all", "All", activeFilter, onFilterChange)

                        AssistChip(
                            onClick = onRefresh,
                            label = { Text("Refresh", fontWeight = FontWeight.ExtraBold) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = R2wSaasColors.PrimarySoft,
                                labelColor = R2wSaasColors.Primary,
                                leadingIconContentColor = R2wSaasColors.Primary
                            ),
                            border = BorderStroke(1.dp, R2wSaasColors.Border)
                        )
                    }
                }
            }

            if (loading) {
                item {
                    R2wSaasCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(color = R2wSaasColors.Primary)
                            Text(
                                text = "Loading wallet requests...",
                                color = R2wSaasColors.Muted,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(start = 12.dp)
                            )
                        }
                    }
                }
            }

            errorMessage?.let { message ->
                item {
                    WalletSaasMessageCard(message = message, isError = true)
                }
            }

            if (!loading && errorMessage == null && requests.isEmpty()) {
                item {
                    WalletSaasMessageCard(message = "No $activeFilter requests.", isError = false)
                }
            }

            items(requests, key = { it.id ?: it.hashCode().toString() }) { request ->
                WalletApprovalSaasCard(
                    request = request,
                    onApprove = onApprove,
                    onReject = onReject
                )
            }

            item { Spacer(Modifier.height(18.dp)) }
        }
    }
}

@Composable
private fun WalletFilterChip(
    key: String,
    label: String,
    selected: String,
    onClick: (String) -> Unit
) {
    val isSelected = selected == key

    AssistChip(
        onClick = { onClick(key) },
        label = { Text(label, fontWeight = FontWeight.ExtraBold) },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = if (isSelected) R2wSaasColors.PrimarySoft else R2wSaasColors.Card,
            labelColor = if (isSelected) R2wSaasColors.Primary else R2wSaasColors.Muted
        ),
        border = BorderStroke(1.dp, R2wSaasColors.Border)
    )
}

@Composable
private fun WalletApprovalSaasCard(
    request: MobileWalletRequest,
    onApprove: (String) -> Unit,
    onReject: (String) -> Unit
) {
    val status = request.status.ifBlank { "pending" }
    val id = request.id ?: ""
    val amountText = moneyValue(request.amount) + " " + request.currency

    R2wSaasCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Surface(
                shape = RoundedCornerShape(17.dp),
                color = walletStatusColor(status).copy(alpha = 0.10f),
                border = BorderStroke(1.dp, R2wSaasColors.Border)
            ) {
                Icon(
                    imageVector = Icons.Default.AccountBalanceWallet,
                    contentDescription = null,
                    tint = walletStatusColor(status),
                    modifier = Modifier.padding(11.dp)
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Request #${request.id ?: "-"}",
                            color = R2wSaasColors.Primary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(Modifier.height(3.dp))

                        Text(
                            text = request.note?.ifBlank { "Wallet top-up request" } ?: "Wallet top-up request",
                            color = R2wSaasColors.Text,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(Modifier.height(2.dp))

                        Text(
                            text = "Created ${request.createdAt ?: "-"}",
                            color = R2wSaasColors.Muted,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        WalletStatusPill(status, walletStatusColor(status))

                        Spacer(Modifier.height(6.dp))

                        Text(
                            text = amountText,
                            color = R2wSaasColors.Text,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 1
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WalletMiniStat(
                        title = "Type",
                        value = "Top-up",
                        modifier = Modifier.weight(1f)
                    )

                    WalletMiniStat(
                        title = "Status",
                        value = status.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() },
                        modifier = Modifier.weight(1f)
                    )
                }

                if (status.equals("pending", ignoreCase = true) && id.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { onApprove(id) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = R2wSaasColors.Green),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null
                            )
                            Text(
                                text = "Approve",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(start = 6.dp)
                            )
                        }

                        Button(
                            onClick = { onReject(id) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = R2wSaasColors.Red),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.RemoveCircle,
                                contentDescription = null
                            )
                            Text(
                                text = "Reject",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(start = 6.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WalletStatusPill(
    text: String,
    color: Color
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.18f))
    ) {
        Text(
            text = text.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() },
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
            maxLines = 1
        )
    }
}

@Composable
private fun WalletMiniStat(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = R2wSaasColors.Background,
        border = BorderStroke(1.dp, R2wSaasColors.Border)
    ) {
        Column(modifier = Modifier.padding(9.dp)) {
            Text(
                text = title,
                color = R2wSaasColors.Muted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = value.ifBlank { "-" },
                color = R2wSaasColors.Text,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun WalletSaasMessageCard(
    message: String,
    isError: Boolean
) {
    R2wSaasCard {
        Text(
            text = if (isError) "Wallet request error" else "No requests",
            color = if (isError) R2wSaasColors.Red else R2wSaasColors.Text,
            fontSize = 16.sp,
            fontWeight = FontWeight.Black
        )

        Spacer(Modifier.height(6.dp))

        Text(
            text = message,
            color = R2wSaasColors.Muted,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

private fun walletStatusColor(status: String): Color {
    val s = status.lowercase()
    return when {
        s.contains("approved") -> R2wSaasColors.Green
        s.contains("rejected") || s.contains("failed") -> R2wSaasColors.Red
        s.contains("pending") -> R2wSaasColors.Orange
        else -> R2wSaasColors.Primary
    }
}

private fun moneyValue(value: String): String {
    val clean = value.trim()
    if (clean.isBlank()) return "$0.00"
    return if (clean.startsWith("$") || clean.contains("€")) clean else "$" + clean
}
