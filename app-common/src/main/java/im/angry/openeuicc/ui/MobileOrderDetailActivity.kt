package im.angry.openeuicc.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import im.angry.openeuicc.auth.AuthSession
import im.angry.openeuicc.auth.AuthTokenStore
import im.angry.openeuicc.auth.JwtUtils
import im.angry.openeuicc.auth.MobileOrder
import im.angry.openeuicc.auth.Roam2WorldAuthApi
import im.angry.openeuicc.common.BuildConfig
import im.angry.openeuicc.common.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class MobileOrderDetailActivity : ComponentActivity() {
    private val tokenStore by lazy { AuthTokenStore(this) }
    private val authApi by lazy { Roam2WorldAuthApi(BuildConfig.ROAM2WORLD_API_BASE_URL) }

    private var currentOrder by mutableStateOf<MobileOrder?>(null)
    private var loading by mutableStateOf(false)
    private var errorMessage by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        currentOrder = readIntentOrder()

        setContent {
            MobileOrderDetailScreen(
                order = currentOrder,
                loading = loading,
                error = errorMessage,
                onBack = { finish() },
                onRetry = { loadLatestDetails() },
                onCopyIccid = { iccid -> copyIccid(iccid) },
                onOpenEsim = { order -> openEsim(order) }
            )
        }

        loadLatestDetails()
    }

    private fun loadLatestDetails() {
        lifecycleScope.launch {
            val selected = currentOrder
            val orderId = selected?.id
            if (selected == null || orderId.isNullOrBlank()) {
                if (selected == null) {
                    errorMessage = getString(R.string.order_detail_missing)
                }
                return@launch
            }

            errorMessage = null
            loading = true

            val session = activeSessionOrReturnToLogin()
            if (session == null) {
                loading = false
                return@launch
            }

            runCatching { authApi.order(session, orderId) }
                .onSuccess {
                    currentOrder = it
                }
                .onFailure {
                    errorMessage = it.message ?: getString(R.string.order_detail_load_failed)
                }

            loading = false
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

    private fun copyIccid(iccid: String) {
        getSystemService(ClipboardManager::class.java)
            .setPrimaryClip(ClipData.newPlainText("ICCID", iccid))
        Toast.makeText(this, R.string.toast_iccid_copied, Toast.LENGTH_SHORT).show()
    }

    private fun openEsim(order: MobileOrder) {
        val esim = order.esim
        val esimId = order.esimId
        when {
            esim != null -> startActivity(MobileEsimDetailActivity.createIntent(this, esim))
            !esimId.isNullOrBlank() -> startActivity(MobileEsimDetailActivity.createIntent(this, esimId))
        }
    }

    private fun readIntentOrder(): MobileOrder? {
        if (listOf(EXTRA_ID, EXTRA_ORDER_NUMBER, EXTRA_PACKAGE_NAME, EXTRA_ESIM_ID).none {
                !intent.getStringExtra(it).isNullOrBlank()
            }
        ) {
            return null
        }

        return MobileOrder(
            id = intent.getStringExtra(EXTRA_ID),
            orderNumber = intent.getStringExtra(EXTRA_ORDER_NUMBER),
            packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: getString(R.string.order_detail_package_fallback),
            price = intent.getStringExtra(EXTRA_PRICE),
            status = intent.getStringExtra(EXTRA_STATUS),
            createdAt = intent.getStringExtra(EXTRA_CREATED_AT),
            provider = intent.getStringExtra(EXTRA_PROVIDER),
            esimId = intent.getStringExtra(EXTRA_ESIM_ID),
        )
    }

    companion object {
        private const val EXTRA_ID = "mobile_order.id"
        private const val EXTRA_ORDER_NUMBER = "mobile_order.order_number"
        private const val EXTRA_PACKAGE_NAME = "mobile_order.package_name"
        private const val EXTRA_PROVIDER = "mobile_order.provider"
        private const val EXTRA_PRICE = "mobile_order.price"
        private const val EXTRA_STATUS = "mobile_order.status"
        private const val EXTRA_CREATED_AT = "mobile_order.created_at"
        private const val EXTRA_ESIM_ID = "mobile_order.esim_id"

        fun createIntent(context: Context, order: MobileOrder): Intent =
            Intent(context, MobileOrderDetailActivity::class.java).apply {
                putExtra(EXTRA_ID, order.id)
                putExtra(EXTRA_ORDER_NUMBER, order.orderNumber)
                putExtra(EXTRA_PACKAGE_NAME, order.packageName)
                putExtra(EXTRA_PROVIDER, order.provider)
                putExtra(EXTRA_PRICE, order.price)
                putExtra(EXTRA_STATUS, order.status)
                putExtra(EXTRA_CREATED_AT, order.createdAt)
                putExtra(EXTRA_ESIM_ID, order.esimId)
            }
    }
}

@Composable
private fun MobileOrderDetailScreen(
    order: MobileOrder?,
    loading: Boolean,
    error: String?,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onCopyIccid: (String) -> Unit,
    onOpenEsim: (MobileOrder) -> Unit
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onBack,
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("Geri")
                    }

                    if (loading) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 10.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.height(20.dp))
                            Text("Güncelleniyor...")
                        }
                    }
                }

                if (!error.isNullOrBlank()) {
                    ErrorCard(error = error, onRetry = onRetry)
                }

                if (order == null) {
                    EmptyDetailCard()
                    return@Column
                }

                HeroDetailCard(order = order, orange = orange)

                DetailInfoCard(order = order)

                TimelineCard(order = order)

                CustomerCard(order = order)

                RenewalCard(order = order)

                ActionButtonsCard(
                    order = order,
                    orange = orange,
                    onCopyIccid = onCopyIccid,
                    onOpenEsim = onOpenEsim
                )

                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun HeroDetailCard(order: MobileOrder, orange: Color) {
    val title = PackageNameCleaner.clean(order.packageName).ifBlank { "eSIM Package" }
    val provider = visibleProvider(order.provider).orEmpty().ifBlank { "Provider unavailable" }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(30.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF17181C))
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Purchase History",
                color = orange,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = title,
                color = Color.White,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = provider,
                color = Color.White.copy(alpha = 0.74f),
                style = MaterialTheme.typography.bodyMedium
            )

            StatusBadge(label = order.statusLabel(), rawStatus = order.status)
        }
    }
}

@Composable
private fun DetailInfoCard(order: MobileOrder) {
    InfoCard(title = "Sipariş Bilgileri") {
        DetailRow("Order Number", order.displayNumber().orEmpty().ifBlank { order.orderNumber.orEmpty().ifBlank { order.id.orEmpty() } })
        DetailRow("Package", PackageNameCleaner.clean(order.packageName).ifBlank { order.packageName.orEmpty() })
        DetailRow("Price", formatOrderPrice(order.price.orEmpty()))
        DetailRow("Provider", visibleProvider(order.provider).orEmpty().ifBlank { "-" })
        DetailRow("Status", normalizedStatusLabel(order.statusLabel(), order.status))
        DetailRow("Purchase Date", order.createdAt?.let { formatOrderDate(it) }.orEmpty())
        DetailRow("eSIM ID", order.esimId.orEmpty())
    }
}

@Composable
private fun TimelineCard(order: MobileOrder) {
    val status = order.status.orEmpty().lowercase()
    val failed = listOf("failed", "cancel", "refund", "error", "reject").any { status.contains(it) }
    val esim = order.esim
    val hasEsim = esim != null || !order.esimId.isNullOrBlank()
    val hasIccid = !esim?.iccid.isNullOrBlank()
    val qrReady = hasEsim && hasIccid

    InfoCard(title = "Süreç") {
        TimelineRow(done = true, label = "Order Created")
        TimelineRow(done = !failed, label = if (failed) "Payment / Order Failed" else "Payment Completed")
        TimelineRow(done = !failed && (hasEsim || hasIccid), label = if (hasEsim || hasIccid) "Provider Processing Completed" else "Provider Processing")
        TimelineRow(done = hasEsim, label = if (hasEsim) "eSIM Assigned" else "Waiting for eSIM Assignment")
        TimelineRow(done = qrReady, label = if (qrReady) "QR / Install Info Ready" else "Waiting for QR / Install Info")
    }
}

@Composable
private fun CustomerCard(order: MobileOrder) {
    val esim = order.esim
    val customerName = esim?.customerName() ?: order.customerName()
    val customerPhone = esim?.customerPhone ?: order.customerPhone
    val customerEmail = esim?.customerEmail ?: order.customerEmail
    val iccid = esim?.iccid

    val hasCustomer = !customerName.isNullOrBlank() ||
        !customerPhone.isNullOrBlank() ||
        !customerEmail.isNullOrBlank() ||
        !iccid.isNullOrBlank()

    if (!hasCustomer) return

    InfoCard(title = "Müşteri / eSIM") {
        DetailRow("Name", customerName.orEmpty())
        DetailRow("Phone", customerPhone.orEmpty())
        DetailRow("Email", customerEmail.orEmpty())
        DetailRow("ICCID", iccid.orEmpty())
    }
}

@Composable
private fun RenewalCard(order: MobileOrder) {
    val renewal = order.esim?.lastRenewal ?: return
    val details = listOfNotNull(
        renewal.message?.let { "Status    $it" },
        renewal.code?.let { "Code    $it" },
        renewal.orderNo?.let { "Order    $it" },
        renewal.productName?.let { "Package    $it" },
        renewal.orderStatus?.let { "Order Status    $it" },
        renewal.profileStatus?.let { "Profile Status    $it" },
        renewal.activatedEndTime?.let { "Activated End    ${formatOrderDate(it)}" },
        renewal.renewExpirationTime?.let { "Expiry    ${formatOrderDate(it)}" },
        renewal.latestActivationTime?.let { "Latest Activation    ${formatOrderDate(it)}" }
    )

    if (details.isEmpty()) return

    InfoCard(title = "Son Yenileme") {
        details.forEach { line ->
            Text(
                text = line,
                color = Color(0xFF50535C),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun ActionButtonsCard(
    order: MobileOrder,
    orange: Color,
    onCopyIccid: (String) -> Unit,
    onOpenEsim: (MobileOrder) -> Unit
) {
    val esim = order.esim
    val esimId = order.esimId
    val hasEsim = esim != null || !esimId.isNullOrBlank()
    val iccid = esim?.iccid

    InfoCard(title = "Aksiyonlar") {
        if (hasEsim) {
            Button(
                onClick = { onOpenEsim(order) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = orange),
                shape = RoundedCornerShape(18.dp)
            ) {
                Text("eSIM Detayını Aç")
            }
        } else {
            Text(
                text = "eSIM bilgisi henüz hazır değil.",
                color = Color(0xFF686B73),
                style = MaterialTheme.typography.bodyMedium
            )
        }

        if (!iccid.isNullOrBlank()) {
            OutlinedButton(
                onClick = { onCopyIccid(iccid) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp)
            ) {
                Text("ICCID Kopyala")
            }
        }
    }
}

@Composable
private fun InfoCard(
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
            verticalArrangement = Arrangement.spacedBy(10.dp)
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
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            color = Color(0xFF686B73),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(0.42f)
        )
        Text(
            text = value.ifBlank { "-" },
            color = Color(0xFF17181C),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(0.58f)
        )
    }
}

@Composable
private fun TimelineRow(done: Boolean, label: String) {
    val color = if (done) Color(0xFF166534) else Color(0xFF854D0E)
    val bg = if (done) Color(0xFFDCFCE7) else Color(0xFFFEF9C3)

    Text(
        text = "${if (done) "✓" else "•"} $label",
        color = color,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .fillMaxWidth()
            .background(bg, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    )
}

@Composable
private fun StatusBadge(label: String?, rawStatus: String?) {
    val display = normalizedStatusLabel(label, rawStatus)
    val normalized = listOfNotNull(rawStatus, display).joinToString(" ").lowercase()

    val colors = when {
        isFailedStatus(normalized) -> Color(0xFFFEE2E2) to Color(0xFFB91C1C)
        isCompletedStatus(normalized) -> Color(0xFFDCFCE7) to Color(0xFF166534)
        else -> Color(0xFFFEF9C3) to Color(0xFF854D0E)
    }

    Text(
        text = display,
        color = colors.second,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .background(colors.first, RoundedCornerShape(999.dp))
            .padding(horizontal = 12.dp, vertical = 7.dp)
    )
}

@Composable
private fun ErrorCard(error: String, onRetry: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEAEA))
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Detay yüklenemedi", color = Color(0xFFB91C1C), fontWeight = FontWeight.Bold)
            Text(error, color = Color(0xFF7F1D1D))
            OutlinedButton(onClick = onRetry) {
                Text("Tekrar Dene")
            }
        }
    }
}

@Composable
private fun EmptyDetailCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Text(
            text = "Sipariş bilgisi bulunamadı.",
            modifier = Modifier.padding(18.dp),
            color = Color(0xFF686B73)
        )
    }
}

private fun visibleProvider(provider: String?): String? =
    provider?.replace("TGT", "Orange", ignoreCase = true)
        ?.replace("tgt", "Orange", ignoreCase = true)

private fun formatOrderDate(value: String): String {
    if (value.isBlank()) return ""
    return try {
        val formatter = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm", Locale.ENGLISH)
        Instant.parse(value).atZone(ZoneId.systemDefault()).format(formatter)
    } catch (_: Exception) {
        value
    }
}

private fun formatOrderPrice(value: String): String {
    val clean = value.trim()
    if (clean.isBlank()) return "$0"
    if (clean.startsWith("$") || clean.startsWith("€") || clean.startsWith("£")) return clean
    return "$$clean"
}

private fun normalizedStatusLabel(label: String?, rawStatus: String?): String {
    val display = label?.takeIf { it.isNotBlank() } ?: rawStatus.orEmpty()
    val normalized = listOfNotNull(rawStatus, display).joinToString(" ").lowercase()

    return when {
        isFailedStatus(normalized) -> "FAILED"
        isCompletedStatus(normalized) -> "COMPLETED"
        normalized.contains("pending") || normalized.contains("processing") || normalized.contains("waiting") -> "PENDING"
        else -> display.ifBlank { "STATUS" }.uppercase()
    }
}

private fun isCompletedStatus(status: String): Boolean =
    status.contains("completed") ||
        status.contains("complete") ||
        status.contains("confirmed") ||
        status.contains("confirm") ||
        status.contains("success") ||
        status.contains("paid") ||
        status.contains("active") ||
        status.contains("installed")

private fun isFailedStatus(status: String): Boolean =
    status.contains("failed") ||
        status.contains("failure") ||
        status.contains("cancel") ||
        status.contains("refund") ||
        status.contains("error") ||
        status.contains("reject")
