package im.angry.openeuicc.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import im.angry.openeuicc.auth.AuthSession
import im.angry.openeuicc.auth.AuthTokenStore
import im.angry.openeuicc.auth.JwtUtils
import im.angry.openeuicc.auth.MobileEsim
import im.angry.openeuicc.auth.Roam2WorldAuthApi
import im.angry.openeuicc.common.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

class CustomersActivity : ComponentActivity() {
    private val tokenStore by lazy { AuthTokenStore(this) }
    private val authApi by lazy { Roam2WorldAuthApi(BuildConfig.ROAM2WORLD_API_BASE_URL) }

    private var allCustomers by mutableStateOf<List<CustomerSummary>>(emptyList())
    private var query by mutableStateOf("")
    private var loading by mutableStateOf(false)
    private var errorMessage by mutableStateOf<String?>(null)
    private var selectedCustomer by mutableStateOf<CustomerSummary?>(null)
    private var refreshKey by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            CustomersScreen(
                customers = filteredCustomers(),
                allCustomers = allCustomers,
                query = query,
                loading = loading,
                errorMessage = errorMessage,
                selectedCustomer = selectedCustomer,
                refreshKey = refreshKey,
                onQueryChange = { query = it },
                onRefresh = { loadCustomers() },
                onOpenCustomer = { selectedCustomer = it },
                onDismissCustomer = { selectedCustomer = null },
                onOpenEsimDetail = { openEsimDetail(it) },
                onCopyIccid = { copyIccid(it) },
                onRenew = { openRenewal(it) },
                onNavDashboard = { startActivity(Intent(this, DashboardActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)) },
                onNavPackages = { startActivity(Intent(this, PackagesActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)) },
                onNavWallet = { startActivity(Intent(this, WalletActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)) },
                onNavEsims = { startActivity(Intent(this, MobileEsimsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)) },
                onNavMore = { finish() }
            )
        }

        loadCustomers()
    }

    override fun onResume() {
        super.onResume()
        refreshKey += 1
    }

    private fun filteredCustomers(): List<CustomerSummary> {
        val clean = query.trim().lowercase(Locale.ROOT)
        return if (clean.isBlank()) {
            allCustomers
        } else {
            allCustomers.filter { it.searchText().lowercase(Locale.ROOT).contains(clean) }
        }
    }

    private fun loadCustomers() {
        lifecycleScope.launch {
            loading = true
            errorMessage = null

            val session = activeSessionOrReturnToLogin()
            if (session == null) {
                loading = false
                return@launch
            }

            val result = runCatching { fetchEsimsWithCustomers(session) }
            loading = false

            result
                .onSuccess {
                    allCustomers = buildCustomers(it)
                    refreshKey += 1
                }
                .onFailure {
                    errorMessage = it.message ?: "Customers could not be loaded"
                }
        }
    }

    private suspend fun activeSessionOrReturnToLogin(): AuthSession? {
        val savedSession = withContext(Dispatchers.IO) { tokenStore.getSession() } ?: return redirectToLogin()
        if (!JwtUtils.isExpired(savedSession.accessToken)) return savedSession
        val refreshed = runCatching { authApi.refresh(savedSession) }.getOrNull() ?: return redirectToLogin()
        withContext(Dispatchers.IO) { tokenStore.save(refreshed) }
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

    private suspend fun fetchEsimsWithCustomers(session: AuthSession): List<CustomerEsimRecord> = withContext(Dispatchers.IO) {
        val url = "${BuildConfig.ROAM2WORLD_API_BASE_URL.trimEnd('/')}/api/v1/mobile/esims/"
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 30_000
            readTimeout = 30_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Authorization", session.authorizationHeader)
        }

        try {
            val status = connection.responseCode
            val text = ((if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()
                ?.use { it.readText() }).orEmpty()

            if (status !in 200..299) throw IllegalStateException("Customers API failed with HTTP $status")

            val root = JSONObject(text)
            val data = root.optJSONObject("data") ?: root
            val array = data.optJSONArray("esims")
                ?: data.optJSONArray("results")
                ?: root.optJSONArray("data")
                ?: JSONArray()

            (0 until array.length()).mapNotNull { index -> parseCustomerEsim(array.optJSONObject(index)) }
        } finally {
            connection.disconnect()
        }
    }

    private fun parseCustomerEsim(json: JSONObject?): CustomerEsimRecord? {
        json ?: return null
        val customer = json.optJSONObject("customer")
        val activation = json.optJSONObject("activation") ?: json.optJSONObject("activation_details")

        return CustomerEsimRecord(
            id = firstNotBlank(json.optString("id"), json.optString("esim_id"), json.optString("esimId")),
            iccid = firstNotBlank(json.optString("iccid"), activation?.optString("iccid")),
            provider = firstNotBlank(json.optString("display_provider"), json.optString("provider"), json.optString("source")),
            packageName = firstNotBlank(json.optString("package_name"), json.optString("packageName"), json.optString("plan_name"), json.optString("planName"), json.optString("product_name")),
            status = firstNotBlank(json.optString("status"), json.optString("state")),
            createdAt = firstNotBlank(json.optString("created_at"), json.optString("createdAt"), json.optString("purchased_at"), json.optString("purchasedAt")),
            expiresAt = firstNotBlank(json.optString("expires_at"), json.optString("expiresAt"), json.optString("expiry_date"), json.optString("expiryDate")),
            dataRemaining = firstNotBlank(json.optString("data_remaining"), json.optString("dataRemaining"), json.optString("remaining_data"), json.optString("remainingData")),
            dataUsed = firstNotBlank(json.optString("data_used"), json.optString("dataUsed"), json.optString("used_data"), json.optString("usedData")),
            activationCode = firstNotBlank(json.optString("activation_code"), json.optString("activationCode"), activation?.optString("activation_code"), activation?.optString("activationCode")),
            lpaCode = firstNotBlank(json.optString("lpa_code"), json.optString("lpaCode"), activation?.optString("lpa_code"), activation?.optString("lpaCode")),
            smdpAddress = firstNotBlank(json.optString("smdp_address"), json.optString("smdpAddress"), activation?.optString("smdp_address"), activation?.optString("smdpAddress")),
            matchingId = firstNotBlank(json.optString("matching_id"), json.optString("matchingId"), activation?.optString("matching_id"), activation?.optString("matchingId")),
            qrCode = firstNotBlank(json.optString("qr_code"), json.optString("qrCode"), activation?.optString("qr_code"), activation?.optString("qrCode")),
            qrCodeUrl = firstNotBlank(json.optString("qr_code_url"), json.optString("qrCodeUrl"), json.optString("qr_url"), activation?.optString("qr_code_url")),
            orderNumber = firstNotBlank(json.optString("order_number"), json.optString("orderNumber")),
            orderId = firstNotBlank(json.optString("order_id"), json.optString("orderId")),
            customerFirstName = firstNotBlank(json.optString("customer_first_name"), json.optString("customerFirstName"), customer?.optString("first_name"), customer?.optString("firstName")),
            customerLastName = firstNotBlank(json.optString("customer_last_name"), json.optString("customerLastName"), customer?.optString("last_name"), customer?.optString("lastName")),
            customerPhone = firstNotBlank(json.optString("customer_phone"), json.optString("customerPhone"), json.optString("phone_number"), json.optString("phoneNumber"), customer?.optString("phone")),
            customerEmail = firstNotBlank(json.optString("customer_email"), json.optString("customerEmail"), customer?.optString("email"))
        )
    }

    private fun buildCustomers(esims: List<CustomerEsimRecord>): List<CustomerSummary> =
        esims
            .filter { !it.customerName().isNullOrBlank() || !it.customerPhone.isNullOrBlank() || !it.customerEmail.isNullOrBlank() }
            .groupBy { it.customerKey() }
            .map { (_, records) ->
                val sorted = records.sortedByDescending { it.createdAt.orEmpty() }
                val latest = sorted.first()
                CustomerSummary(
                    name = latest.customerName() ?: "Customer",
                    phone = latest.customerPhone,
                    email = latest.customerEmail,
                    totalEsims = records.size,
                    activeEsims = records.count { !isExpired(it) },
                    expiredEsims = records.count { isExpired(it) },
                    latestEsim = latest,
                    esims = sorted
                )
            }
            .sortedBy { it.name.lowercase(Locale.ROOT) }

    private fun openEsimDetail(record: CustomerEsimRecord) {
        startActivity(MobileEsimDetailActivity.createIntent(this, record.toMobileEsim()))
    }

    private fun openRenewal(record: CustomerEsimRecord) {
        copyIccid(record, forRenewal = true)
        val provider = record.provider.orEmpty().lowercase(Locale.ROOT)
        val target = if (provider.contains("airhub") || provider.contains("vodafone")) {
            VodafoneRenewalActivity::class.java
        } else {
            TgtSimRechargeActivity::class.java
        }
        startActivity(Intent(this, target).apply { putExtra("renew.iccid", record.iccid) })
    }

    private fun copyIccid(record: CustomerEsimRecord, forRenewal: Boolean = false) {
        val value = record.iccid?.takeIf { it.isNotBlank() }
        if (value == null) {
            Toast.makeText(this, "ICCID not available", Toast.LENGTH_SHORT).show()
            return
        }

        getSystemService(ClipboardManager::class.java)
            .setPrimaryClip(ClipData.newPlainText("ICCID", value))

        Toast.makeText(
            this,
            if (forRenewal) "ICCID copied for renewal" else "ICCID copied",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun isExpired(record: CustomerEsimRecord): Boolean {
        if (record.status?.contains("expired", ignoreCase = true) == true) return true
        val expires = record.expiresAt ?: return false
        return runCatching { OffsetDateTime.parse(expires).isBefore(OffsetDateTime.now()) }.getOrDefault(false)
    }

    private fun formatCustomerDate(value: String?): String {
        val raw = value?.trim().orEmpty()
        if (raw.isBlank()) return ""
        return try {
            val formatter = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm", Locale.ENGLISH)
            OffsetDateTime.parse(raw).format(formatter)
        } catch (_: Exception) {
            raw
        }
    }

    private fun visibleProvider(provider: String?): String =
        formatCustomerStatus(
            provider?.replace("TGT", "Orange", ignoreCase = true)
                ?.replace("tgt", "Orange", ignoreCase = true)
        )

    private fun formatCustomerStatus(value: String?): String {
        val raw = value?.trim().orEmpty()
        if (raw.isBlank()) return "Unknown"
        return raw
            .replace("_", " ")
            .replace("-", " ")
            .split(" ")
            .filter { it.isNotBlank() }
            .joinToString(" ") { word ->
                word.lowercase(Locale.ROOT).replaceFirstChar {
                    if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString()
                }
            }
            .ifBlank { "Unknown" }
    }

    private fun statusLabel(record: CustomerEsimRecord): String =
        when {
            isExpired(record) -> "Expired"
            record.status?.contains("pending", ignoreCase = true) == true -> "Pending"
            else -> "Active"
        }

    private fun initialsForCustomer(name: String): String =
        name.split(" ")
            .filter { it.isNotBlank() }
            .take(2)
            .map { it.first().uppercaseChar() }
            .joinToString("")
            .ifBlank { "CU" }

    private fun firstNotBlank(vararg values: String?): String? =
        values.firstOrNull { !it.isNullOrBlank() && it != "null" }

    @Composable
    private fun CustomersScreen(
        customers: List<CustomerSummary>,
        allCustomers: List<CustomerSummary>,
        query: String,
        loading: Boolean,
        errorMessage: String?,
        selectedCustomer: CustomerSummary?,
        refreshKey: Int,
        onQueryChange: (String) -> Unit,
        onRefresh: () -> Unit,
        onOpenCustomer: (CustomerSummary) -> Unit,
        onDismissCustomer: () -> Unit,
        onOpenEsimDetail: (CustomerEsimRecord) -> Unit,
        onCopyIccid: (CustomerEsimRecord) -> Unit,
        onRenew: (CustomerEsimRecord) -> Unit,
        onNavDashboard: () -> Unit,
        onNavPackages: () -> Unit,
        onNavWallet: () -> Unit,
        onNavEsims: () -> Unit,
        onNavMore: () -> Unit
    ) {
        val bg = Color(0xFFF6F7FB)
        val totalActive = remember(refreshKey, allCustomers) { allCustomers.sumOf { it.activeEsims } }

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
                        CustomersHero(
                            totalCustomers = allCustomers.size,
                            activeEsims = totalActive,
                            onRefresh = onRefresh,
                            loading = loading
                        )

                        OutlinedTextField(
                            value = query,
                            onValueChange = onQueryChange,
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("Search customers, eSIMs, ICCID") },
                            shape = RoundedCornerShape(18.dp)
                        )

                        errorMessage?.let {
                            InfoCard(title = "Customers could not be loaded") {
                                Text(it, color = Color(0xFFDC2626))
                                OutlinedButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) {
                                    Text("Try again")
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Showing ${customers.size} customers",
                                color = Color(0xFF6B7280),
                                fontWeight = FontWeight.Bold
                            )
                            if (loading) {
                                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = Color(0xFFFF7900))
                            }
                        }

                        if (!loading && customers.isEmpty()) {
                            InfoCard(title = "No customers found") {
                                Text(
                                    text = if (query.isBlank()) "Customer records will appear here after eSIM purchases." else "No customer matches your search.",
                                    color = Color(0xFF6B7280)
                                )
                            }
                        }

                        customers.forEach { customer ->
                            CustomerCard(customer = customer, onOpen = { onOpenCustomer(customer) })
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    CustomersBottomNav(
                        onDashboard = onNavDashboard,
                        onPackages = onNavPackages,
                        onWallet = onNavWallet,
                        onEsims = onNavEsims,
                        onMore = onNavMore
                    )
                }

                selectedCustomer?.let {
                    CustomerDetailSheet(
                        customer = it,
                        onDismiss = onDismissCustomer,
                        onOpenEsimDetail = onOpenEsimDetail,
                        onCopyIccid = onCopyIccid,
                        onRenew = onRenew
                    )
                }
            }
        }
    }

    @Composable
    private fun CustomersHero(
        totalCustomers: Int,
        activeEsims: Int,
        onRefresh: () -> Unit,
        loading: Boolean
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(32.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF17181C))
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column {
                        Text(
                            text = "Customers CRM",
                            color = Color.White,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = "Live customer eSIM overview",
                            color = Color.White.copy(alpha = 0.72f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    Text(
                        text = if (loading) "Loading..." else "Refresh",
                        color = Color(0xFFFF7900),
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.clickable(enabled = !loading, onClick = onRefresh)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    HeroMetric("Customers", totalCustomers.toString(), Modifier.weight(1f))
                    HeroMetric("Active eSIMs", activeEsims.toString(), Modifier.weight(1f))
                }
            }
        }
    }

    @Composable
    private fun HeroMetric(label: String, value: String, modifier: Modifier = Modifier) {
        Card(
            modifier = modifier,
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.10f))
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(value, color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                Text(label, color = Color.White.copy(alpha = 0.72f), style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    @Composable
    private fun CustomerCard(customer: CustomerSummary, onOpen: () -> Unit) {
        val latest = customer.latestEsim
        val status = statusLabel(latest)
        val statusColors = customerStatusColors(status)

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CustomerAvatar(customer.name)

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(
                        customer.name,
                        color = Color(0xFF17181C),
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        listOfNotNull(customer.email, customer.phone).firstOrNull { it.isNotBlank() } ?: "No contact info",
                        color = Color(0xFF6B7280),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        PackageNameCleaner.clean(latest.packageName).orEmpty().ifBlank { "Package unavailable" },
                        color = Color(0xFFFF7900),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${customer.totalEsims} eSIMs • ${customer.activeEsims} active • ${customer.expiredEsims} expired",
                        color = Color(0xFF6B7280),
                        style = MaterialTheme.typography.labelMedium
                    )
                }

                StatusPill(status, statusColors)
            }
        }
    }

    @Composable
    private fun CustomerAvatar(name: String) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .background(Color(0xFFFFEFE2), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initialsForCustomer(name),
                color = Color(0xFF17181C),
                fontWeight = FontWeight.Black
            )
        }
    }

    @OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
    @Composable
    private fun CustomerDetailSheet(
        customer: CustomerSummary,
        onDismiss: () -> Unit,
        onOpenEsimDetail: (CustomerEsimRecord) -> Unit,
        onCopyIccid: (CustomerEsimRecord) -> Unit,
        onRenew: (CustomerEsimRecord) -> Unit
    ) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            containerColor = Color(0xFFF6F7FB)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            CustomerAvatar(customer.name)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(customer.name, color = Color(0xFF17181C), fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge)
                                Text(
                                    listOfNotNull(customer.email, customer.phone).firstOrNull { it.isNotBlank() } ?: "No contact information",
                                    color = Color(0xFF6B7280),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            AssistChip(onClick = {}, label = { Text("${customer.totalEsims} eSIMs") })
                            AssistChip(onClick = {}, label = { Text("${customer.activeEsims} active") })
                            AssistChip(onClick = {}, label = { Text("${customer.expiredEsims} expired") })
                        }
                    }
                }

                Text("Customer eSIMs", color = Color(0xFF17181C), fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(520.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    customer.esims.forEach { record ->
                        CustomerEsimCard(
                            record = record,
                            onOpenEsimDetail = { onOpenEsimDetail(record) },
                            onCopyIccid = { onCopyIccid(record) },
                            onRenew = { onRenew(record) }
                        )
                    }
                }

                OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                    Text("Close")
                }
            }
        }
    }

    @Composable
    private fun CustomerEsimCard(
        record: CustomerEsimRecord,
        onOpenEsimDetail: () -> Unit,
        onCopyIccid: () -> Unit,
        onRenew: () -> Unit
    ) {
        val status = statusLabel(record)
        val statusColors = customerStatusColors(status)

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(Color(0xFFFFEFE2), RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("SIM", color = Color(0xFFFF7900), fontWeight = FontWeight.Black)
                    }

                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            PackageNameCleaner.clean(record.packageName).orEmpty().ifBlank { "eSIM Package" },
                            color = Color(0xFF17181C),
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            listOfNotNull(
                                record.provider?.let { visibleProvider(it) },
                                record.createdAt?.let { formatCustomerDate(it) }
                            ).filter { it.isNotBlank() }.joinToString(" • ").ifBlank { "Provider unavailable" },
                            color = Color(0xFF6B7280),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    StatusPill(status, statusColors)
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(record.iccid?.let { "ICCID: $it" } ?: "ICCID unavailable", color = Color(0xFF6B7280), style = MaterialTheme.typography.bodySmall)
                    Text(record.dataRemaining?.let { "Remaining: $it" } ?: "Remaining: —", color = Color(0xFF6B7280), style = MaterialTheme.typography.bodySmall)
                    Text(record.expiresAt?.let { "Expires: ${formatCustomerDate(it)}" } ?: "Expires: —", color = Color(0xFF6B7280), style = MaterialTheme.typography.bodySmall)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onOpenEsimDetail, modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp)) {
                        Text("QR / Detail")
                    }
                    OutlinedButton(onClick = onCopyIccid, modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp)) {
                        Text("Copy ICCID")
                    }
                }

                Button(
                    onClick = onRenew,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF7900)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Renew", fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    @Composable
    private fun StatusPill(label: String, colors: Pair<Color, Color>) {
        Box(
            modifier = Modifier
                .background(colors.second, RoundedCornerShape(50))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("●  $label", color = colors.first, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black)
        }
    }

    private fun customerStatusColors(label: String): Pair<Color, Color> =
        when (label.lowercase(Locale.ENGLISH)) {
            "expired" -> Color(0xFFDC2626) to Color(0xFFFEE2E2)
            "pending", "expiring" -> Color(0xFFF59E0B) to Color(0xFFFEF3C7)
            else -> Color(0xFF168653) to Color(0xFFE4F8EC)
        }

    @Composable
    private fun CustomersBottomNav(
        onDashboard: () -> Unit,
        onPackages: () -> Unit,
        onWallet: () -> Unit,
        onEsims: () -> Unit,
        onMore: () -> Unit
    ) {
        Surface(shadowElevation = 8.dp, color = Color.White) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                BottomNavText("Dashboard", false, onDashboard)
                BottomNavText("Packages", false, onPackages)
                BottomNavText("Wallet", false, onWallet)
                BottomNavText("eSIMs", false, onEsims)
                BottomNavText("More", true, onMore)
            }
        }
    }

    @Composable
    private fun BottomNavText(label: String, selected: Boolean, onClick: () -> Unit) {
        Text(
            text = label,
            color = if (selected) Color(0xFFFF7900) else Color(0xFF6B7280),
            fontWeight = if (selected) FontWeight.Black else FontWeight.SemiBold,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = 6.dp, vertical = 8.dp)
        )
    }

    @Composable
    private fun InfoCard(title: String, content: @Composable ColumnScope.() -> Unit) {
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
                Text(title, color = Color(0xFF17181C), fontWeight = FontWeight.Black)
                HorizontalDivider()
                content()
            }
        }
    }

    private data class CustomerEsimRecord(
        val id: String?,
        val iccid: String?,
        val provider: String?,
        val packageName: String?,
        val status: String?,
        val createdAt: String?,
        val expiresAt: String?,
        val dataRemaining: String?,
        val dataUsed: String?,
        val activationCode: String?,
        val lpaCode: String?,
        val smdpAddress: String?,
        val matchingId: String?,
        val qrCode: String?,
        val qrCodeUrl: String?,
        val orderNumber: String?,
        val orderId: String?,
        val customerFirstName: String?,
        val customerLastName: String?,
        val customerPhone: String?,
        val customerEmail: String?
    ) {
        fun customerName(): String? = listOfNotNull(customerFirstName, customerLastName)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .takeIf { it.isNotBlank() }

        fun customerKey(): String = firstNotBlankStatic(customerPhone, customerEmail, customerName(), iccid, id) ?: "unknown"

        fun searchText(): String = listOfNotNull(
            customerName(),
            customerPhone,
            customerEmail,
            iccid,
            packageName,
            provider,
            dataRemaining,
            dataUsed,
            orderNumber,
            orderId,
            status,
            createdAt,
            expiresAt
        ).joinToString(" ")

        fun toMobileEsim(): MobileEsim = MobileEsim(
            id = id,
            iccid = iccid,
            provider = provider,
            packageName = packageName,
            status = status,
            activationCode = activationCode,
            lpaCode = lpaCode,
            smdpAddress = smdpAddress,
            matchingId = matchingId,
            confirmationCodeRequired = false,
            qrCode = qrCode,
            qrCodeUrl = qrCodeUrl,
            createdAt = createdAt,
            orderNumber = orderNumber,
            expiresAt = expiresAt,
            dataRemaining = dataRemaining,
            dataUsed = dataUsed,
            orderId = orderId,
            customerFirstName = customerFirstName,
            customerLastName = customerLastName,
            customerPhone = customerPhone,
            customerEmail = customerEmail
        )
    }

    private data class CustomerSummary(
        val name: String,
        val phone: String?,
        val email: String?,
        val totalEsims: Int,
        val activeEsims: Int,
        val expiredEsims: Int,
        val latestEsim: CustomerEsimRecord,
        val esims: List<CustomerEsimRecord>
    ) {
        fun searchText(): String =
            listOfNotNull(name, phone, email).joinToString(" ") + " " + esims.joinToString(" ") { it.searchText() }
    }

    private companion object {
        fun firstNotBlankStatic(vararg values: String?): String? =
            values.firstOrNull { !it.isNullOrBlank() && it != "null" }
    }
}
