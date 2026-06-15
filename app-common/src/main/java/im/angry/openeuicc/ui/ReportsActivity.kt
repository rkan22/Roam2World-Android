package im.angry.openeuicc.ui

import android.content.ClipData
import android.content.Intent
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import im.angry.openeuicc.auth.AuthSession
import im.angry.openeuicc.auth.AuthTokenStore
import im.angry.openeuicc.auth.JwtUtils
import im.angry.openeuicc.auth.MobileDealer
import im.angry.openeuicc.auth.MobileOrder
import im.angry.openeuicc.auth.Roam2WorldAuthApi
import im.angry.openeuicc.common.BuildConfig
import im.angry.openeuicc.common.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

class ReportsActivity : ComponentActivity() {
    private val tokenStore by lazy { AuthTokenStore(this) }
    private val authApi by lazy { Roam2WorldAuthApi(BuildConfig.ROAM2WORLD_API_BASE_URL) }

    private var reportRange by mutableStateOf(ReportRange.THIRTY_DAYS)
    private var loadedOrders by mutableStateOf<List<MobileOrder>>(emptyList())
    private var loadedDealers by mutableStateOf<List<MobileDealer>>(emptyList())
    private var loadedWalletBalance by mutableStateOf<String?>(null)
    private var loadedActiveEsims by mutableStateOf<String?>(null)
    private var loading by mutableStateOf(false)
    private var errorMessage by mutableStateOf<String?>(null)

    private enum class ReportRange {
        TODAY,
        SEVEN_DAYS,
        THIRTY_DAYS,
        ALL
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val summary = remember(reportRange, loadedOrders, loadedDealers, loadedWalletBalance, loadedActiveEsims, loading, errorMessage) {
                buildReportSummary()
            }

            ReportsScreen(
                summary = summary,
                reportRange = reportRange,
                loading = loading,
                errorMessage = errorMessage,
                onBack = { finish() },
                onRefresh = { loadReports() },
                onRange = {
                    reportRange = it
                },
                onExportPdf = { exportReportPdf() },
                onNavDashboard = { startActivity(Intent(this, DashboardActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)) },
                onNavPackages = { startActivity(Intent(this, PackagesActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)) },
                onNavWallet = { startActivity(Intent(this, WalletActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)) },
                onNavEsims = { startActivity(Intent(this, MobileEsimsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)) },
                onNavMore = { startActivity(Intent(this, MoreActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)) }
            )
        }

        loadReports()
    }

    private fun loadReports() {
        lifecycleScope.launch {
            loading = true
            errorMessage = null

            val session = activeSessionOrReturnToLogin()
            if (session == null) {
                loading = false
                return@launch
            }

            val dashboardRequest = async { runCatching { authApi.dashboard(session) } }
            val ordersRequest = async { runCatching { authApi.orders(session) } }
            val walletRequest = async { runCatching { authApi.wallet(session) } }
            val dealersRequest = async { runCatching { authApi.dealers(session) } }

            val dashboard = dashboardRequest.await().getOrNull()
            val wallet = walletRequest.await().getOrNull()
            val ordersResult = ordersRequest.await()
            val dealersResult = dealersRequest.await()

            loadedOrders = ordersResult.getOrNull()?.orders.orEmpty()
            loadedDealers = dealersResult.getOrNull()?.dealers.orEmpty()
            loadedWalletBalance = wallet?.currentBalance
            loadedActiveEsims = dashboard?.activeEsimCount

            errorMessage = listOfNotNull(
                ordersResult.exceptionOrNull()?.message?.let { "Orders: $it" },
                dealersResult.exceptionOrNull()?.message?.let { "Dealers: $it" }
            ).joinToString("\n").ifBlank { null }

            loading = false
        }
    }

    private fun buildReportSummary(): ReportSummary {
        val orders = filteredOrders()
        val totalRevenue = orders.mapNotNull { amount(it.price) }.fold(BigDecimal.ZERO, BigDecimal::add)
        val totalProfit = totalRevenue.multiply(BigDecimal("0.22"))
        val completedOrders = orders.count { it.statusLabel()?.contains("Completed", ignoreCase = true) == true }
            .takeIf { it > 0 } ?: orders.size
        val pendingOrders = orders.count { it.statusLabel()?.contains("Pending", ignoreCase = true) == true }
        val failedOrderCount = orders.count { it.statusLabel()?.contains("Failed", ignoreCase = true) == true }

        return ReportSummary(
            status = if (orders.isEmpty()) {
                "No report data for ${rangeLabel()} yet."
            } else {
                "Live report data loaded • ${orders.size} orders • ${loadedDealers.size} dealers • ${rangeLabel()}"
            },
            revenue = currency(totalRevenue),
            revenueDelta = "↑ Live • ${orders.size} orders",
            sales = completedOrders.toString(),
            salesDelta = "↑ $pendingOrders pending • $failedOrderCount failed",
            profit = currency(totalProfit),
            activeEsims = loadedActiveEsims ?: "--",
            salesOverview = buildSalesOverview(orders, loadedWalletBalance),
            providerUsage = buildProviderUsage(orders),
            dealerPerformance = buildDealerPerformance(loadedDealers),
            failedOrders = buildFailedOrders(orders),
            profitOverview = buildProfitOverview(totalRevenue, totalProfit, orders),
            orderCount = orders.size,
            dealerCount = loadedDealers.size
        )
    }

    private fun filteredOrders(): List<MobileOrder> {
        if (reportRange == ReportRange.ALL) return loadedOrders

        val now = Instant.now()
        val cutoff = when (reportRange) {
            ReportRange.TODAY -> now.minusSeconds(24 * 60 * 60)
            ReportRange.SEVEN_DAYS -> now.minusSeconds(7 * 24 * 60 * 60)
            ReportRange.THIRTY_DAYS -> now.minusSeconds(30 * 24 * 60 * 60)
            ReportRange.ALL -> Instant.EPOCH
        }

        return loadedOrders.filter { order ->
            parseReportInstant(order.createdAt)?.isAfter(cutoff) == true
        }
    }

    private fun rangeLabel(): String =
        when (reportRange) {
            ReportRange.TODAY -> "Today"
            ReportRange.SEVEN_DAYS -> "7 days"
            ReportRange.THIRTY_DAYS -> "30 days"
            ReportRange.ALL -> "All"
        }

    private fun exportReportPdf() {
        val orders = filteredOrders()
        if (orders.isEmpty()) {
            Toast.makeText(this, R.string.reports_export_empty, Toast.LENGTH_LONG).show()
            return
        }

        val safeRange = rangeLabel().lowercase(Locale.ROOT).replace(" ", "-")
        val outputDir = File(cacheDir, "reports").apply { mkdirs() }
        val outputFile = File(outputDir, "roam2world-report-$safeRange.pdf")

        val document = PdfDocument()
        try {
            val pageWidth = 842
            val pageHeight = 595
            val margin = 28f
            val lineHeight = 20f
            var pageNumber = 1
            var y = 42f

            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.rgb(24, 38, 58)
                textSize = 18f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.rgb(95, 107, 124)
                textSize = 10f
            }
            val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.WHITE
                textSize = 8.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.rgb(32, 45, 64)
                textSize = 8.5f
            }
            val mutedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.rgb(95, 107, 124)
                textSize = 8.5f
            }
            val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.rgb(224, 230, 238)
                strokeWidth = 1f
            }
            val headerBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.rgb(37, 99, 235)
                style = Paint.Style.FILL
            }

            fun shorten(value: String, paint: Paint, maxWidth: Float): String {
                if (paint.measureText(value) <= maxWidth) return value
                var result = value
                while (result.length > 4 && paint.measureText("$result...") > maxWidth) {
                    result = result.dropLast(1)
                }
                return "$result..."
            }

            val totalRevenue = orders.mapNotNull { amount(it.price) }.fold(BigDecimal.ZERO, BigDecimal::add)
            val totalProfit = totalRevenue.multiply(BigDecimal("0.22"))
            val totalSales = orders.count { it.statusLabel()?.contains("Completed", ignoreCase = true) == true }
                .takeIf { it > 0 } ?: orders.size
            val totalActiveEsims = loadedActiveEsims ?: "--"

            val summaryLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.rgb(95, 107, 124)
                textSize = 8.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val summaryValuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.rgb(24, 38, 58)
                textSize = 14f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val summaryCardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.rgb(246, 249, 253)
                style = Paint.Style.FILL
            }
            val summaryStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.rgb(224, 230, 238)
                style = Paint.Style.STROKE
                strokeWidth = 1f
            }

            fun drawSummaryCards(canvas: android.graphics.Canvas) {
                val labels = listOf("Total Revenue", "Total Sales", "Total Profit", "Active eSIMs")
                val values = listOf(currency(totalRevenue), totalSales.toString(), currency(totalProfit), totalActiveEsims)
                val cardWidth = 188f
                val cardHeight = 48f
                var x = margin

                labels.forEachIndexed { index, label ->
                    canvas.drawRoundRect(x, y, x + cardWidth, y + cardHeight, 12f, 12f, summaryCardPaint)
                    canvas.drawRoundRect(x, y, x + cardWidth, y + cardHeight, 12f, 12f, summaryStrokePaint)
                    canvas.drawText(label, x + 12f, y + 17f, summaryLabelPaint)
                    canvas.drawText(shorten(values[index], summaryValuePaint, cardWidth - 24f), x + 12f, y + 38f, summaryValuePaint)
                    x += cardWidth + 10f
                }

                y += cardHeight + 24f
            }

            fun drawTableHeader(canvas: android.graphics.Canvas) {
                canvas.drawRect(margin, y - 13f, pageWidth - margin, y + 7f, headerBgPaint)

                val headers = listOf("Order", "Date", "Customer", "Package", "Provider", "Status", "Amount", "eSIM")
                val widths = listOf(70f, 92f, 105f, 150f, 78f, 75f, 65f, 110f)
                var x = margin + 6f
                headers.forEachIndexed { index, header ->
                    canvas.drawText(header, x, y, headerPaint)
                    x += widths[index]
                }

                y += 16f
            }

            fun startPage(): PdfDocument.Page {
                val page = document.startPage(
                    PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                )
                val canvas = page.canvas

                canvas.drawText("Roam2World Report", margin, y, titlePaint)
                canvas.drawText(
                    "Period: ${rangeLabel()}   •   Orders: ${orders.size}   •   Generated: ${formatReportDate(Instant.now().toString())}",
                    margin,
                    y + 18f,
                    subtitlePaint
                )

                y += 42f

                if (pageNumber == 1) {
                    drawSummaryCards(canvas)
                }

                drawTableHeader(canvas)
                return page
            }

            fun finishPage(page: PdfDocument.Page) {
                page.canvas.drawText("Page $pageNumber", pageWidth - margin - 45f, pageHeight - 18f, subtitlePaint)
                document.finishPage(page)
                pageNumber += 1
                y = 42f
            }

            var page = startPage()
            val widths = listOf(70f, 92f, 105f, 150f, 78f, 75f, 65f, 110f)

            orders.forEachIndexed { index, order ->
                if (y > pageHeight - 42f) {
                    finishPage(page)
                    page = startPage()
                }

                val rowPaint = if (index % 2 == 0) textPaint else mutedPaint
                val canvas = page.canvas

                canvas.drawLine(margin, y + 5f, pageWidth - margin, y + 5f, linePaint)

                val values = listOf(
                    order.displayNumber(),
                    formatReportDate(order.createdAt),
                    order.customerName().orEmpty().ifBlank { "-" },
                    PackageNameCleaner.clean(order.packageName).orEmpty().ifBlank { "-" },
                    order.provider.orEmpty().replace("TGT", "Orange", ignoreCase = true).ifBlank { "-" },
                    formatReportStatus(order.statusLabel()),
                    r2wMoney(order.price, "-"),
                    order.esimId.orEmpty().ifBlank { "-" }
                )

                var x = margin + 6f
                values.forEachIndexed { valueIndex, value ->
                    canvas.drawText(shorten(value, rowPaint, widths[valueIndex] - 8f), x, y, rowPaint)
                    x += widths[valueIndex]
                }

                y += lineHeight
            }

            finishPage(page)

            outputFile.outputStream().use { out ->
                document.writeTo(out)
            }
        } finally {
            document.close()
        }

        val uri = FileProvider.getUriForFile(
            this,
            "${packageName}.fileprovider",
            outputFile
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.reports_export_subject, rangeLabel()))
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newUri(contentResolver, "Roam2World report", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooser = Intent.createChooser(intent, getString(R.string.reports_export_title)).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        startActivity(chooser)
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

    private fun parseReportInstant(value: String?): Instant? {
        val raw = value?.trim().orEmpty()
        if (raw.isBlank()) return null

        val normalized = raw
            .replace(" ", "T")
            .replace(Regex("""(\.\d{3})\d+"""), "$1")
            .let {
                if (it.endsWith("Z", ignoreCase = true) || it.contains(Regex("""[+-]\d{2}:?\d{2}$"""))) {
                    it
                } else {
                    "${it}Z"
                }
            }

        return runCatching { Instant.parse(normalized) }.getOrNull()
    }

    private fun formatReportDate(value: String?): String {
        val raw = value?.trim().orEmpty()
        if (raw.isBlank()) return ""
        return parseReportInstant(raw)
            ?.atZone(ZoneId.systemDefault())
            ?.format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm", Locale.ENGLISH))
            ?: raw
    }

    private fun formatReportStatus(value: String?): String {
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

    private fun buildDealerPerformance(dealers: List<MobileDealer>): String =
        if (dealers.isEmpty()) {
            getString(R.string.reports_empty_dealer_performance)
        } else {
            dealers.take(5).mapIndexed { index, dealer ->
                listOf(
                    "${index + 1}. ${dealer.name}",
                    "Orders: ${dealer.totalOrders}",
                    "Balance: ${dealer.currentBalance}"
                ).joinToString("\n")
            }.joinToString("\n\n")
        }

    private fun buildSalesOverview(orders: List<MobileOrder>, currentBalance: String?): String {
        val completed = orders.count { it.statusLabel()?.contains("Completed", ignoreCase = true) == true }
        val pending = orders.count { it.statusLabel()?.contains("Pending", ignoreCase = true) == true }
        val failed = orders.count {
            val status = it.statusLabel().orEmpty()
            status.contains("Failed", ignoreCase = true) ||
                status.contains("Cancel", ignoreCase = true) ||
                status.contains("Refund", ignoreCase = true)
        }

        val latest = orders.take(5).joinToString("\n\n") { order ->
            listOfNotNull(
                order.displayNumber(),
                PackageNameCleaner.clean(order.packageName),
                order.price?.let { "Amount: ${r2wMoney(it)}" },
                order.statusLabel()?.let { "Status: ${formatReportStatus(it)}" },
                order.createdAt?.let { "Date: ${formatReportDate(it)}" }
            ).joinToString("\n")
        }

        return listOfNotNull(
            currentBalance?.let { "Current balance: ${r2wMoney(it)}" },
            "Orders: ${orders.size} total • $completed completed • $pending pending • $failed failed",
            latest.ifBlank { "No order data yet" }
        ).joinToString("\n\n")
    }

    private fun visibleProvider(provider: String?): String =
        formatReportStatus(
            provider?.replace("TGT", "Orange", ignoreCase = true)
                ?.replace("tgt", "Orange", ignoreCase = true)
        )

    private fun buildProviderUsage(orders: List<MobileOrder>): String {
        val providers = orders.groupingBy { it.provider ?: "Unknown" }.eachCount()
        if (providers.isEmpty()) return "No provider usage yet"
        val total = providers.values.sum().coerceAtLeast(1)

        return providers.entries
            .sortedByDescending { it.value }
            .take(5)
            .joinToString("\n\n") { (provider, count) ->
                val percent = (count * 100) / total
                "${visibleProvider(provider)}\n$percent% of provider usage • $count orders"
            }
    }

    private fun buildFailedOrders(orders: List<MobileOrder>): String {
        val failed = orders.filter {
            val status = it.statusLabel().orEmpty()
            status.contains("Failed", ignoreCase = true) ||
                status.contains("Cancel", ignoreCase = true) ||
                status.contains("Refund", ignoreCase = true) ||
                status.contains("Error", ignoreCase = true)
        }
        if (orders.isEmpty()) return "No order data yet"
        val percent = (failed.size * 100) / orders.size
        return "${failed.size} failed orders\n$percent% of total orders"
    }

    private fun buildProfitOverview(totalRevenue: BigDecimal, totalProfit: BigDecimal, orders: List<MobileOrder>): String {
        val orderCount = orders.size.coerceAtLeast(1)
        val avgProfit = totalProfit.divide(BigDecimal(orderCount), 2, java.math.RoundingMode.HALF_UP)
        return listOf(
            "Gross revenue: ${currency(totalRevenue)}",
            "Net profit est.: ${currency(totalProfit)}",
            "Profit margin est.: 22%",
            "Avg. profit / order: ${currency(avgProfit)}"
        ).joinToString("\n")
    }

    private fun amount(value: String?): BigDecimal? {
        val normalized = value
            ?.trim()
            ?.replace(",", ".")
            ?.replace(Regex("[^0-9.-]"), "")
            ?.takeIf { it.isNotBlank() }
        return normalized?.toBigDecimalOrNull()
    }

    private fun currency(value: BigDecimal): String =
        "€${value.setScale(2, java.math.RoundingMode.HALF_UP)}"

    private data class ReportSummary(
        val status: String,
        val revenue: String,
        val revenueDelta: String,
        val sales: String,
        val salesDelta: String,
        val profit: String,
        val activeEsims: String,
        val salesOverview: String,
        val providerUsage: String,
        val dealerPerformance: String,
        val failedOrders: String,
        val profitOverview: String,
        val orderCount: Int,
        val dealerCount: Int
    )

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    private fun ReportsScreen(
        summary: ReportSummary,
        reportRange: ReportRange,
        loading: Boolean,
        errorMessage: String?,
        onBack: () -> Unit,
        onRefresh: () -> Unit,
        onRange: (ReportRange) -> Unit,
        onExportPdf: () -> Unit,
        onNavDashboard: () -> Unit,
        onNavPackages: () -> Unit,
        onNavWallet: () -> Unit,
        onNavEsims: () -> Unit,
        onNavMore: () -> Unit
    ) {
        val bg = Color(0xFFF6F7FB)

        MaterialTheme {
            Surface(modifier = Modifier.fillMaxSize(), color = bg) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 116.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        ReportsHero(
                            status = summary.status,
                            loading = loading,
                            onBack = onBack,
                            onRefresh = onRefresh,
                            onExportPdf = onExportPdf
                        )

                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            ReportRangeButton("Today", reportRange == ReportRange.TODAY) { onRange(ReportRange.TODAY) }
                            ReportRangeButton("7 days", reportRange == ReportRange.SEVEN_DAYS) { onRange(ReportRange.SEVEN_DAYS) }
                            ReportRangeButton("30 days", reportRange == ReportRange.THIRTY_DAYS) { onRange(ReportRange.THIRTY_DAYS) }
                            ReportRangeButton("All", reportRange == ReportRange.ALL) { onRange(ReportRange.ALL) }
                        }

                        errorMessage?.let {
                            InfoCard(title = "Some reports could not be loaded") {
                                Text(it, color = Color(0xFFDC2626))
                                OutlinedButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                                    Text("Try again")
                                }
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            KpiCard("Revenue", summary.revenue, summary.revenueDelta, Modifier.weight(1f))
                            KpiCard("Sales", summary.sales, summary.salesDelta, Modifier.weight(1f))
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            KpiCard("Profit", summary.profit, "Estimated margin", Modifier.weight(1f))
                            KpiCard("Active eSIMs", summary.activeEsims, "Live dashboard", Modifier.weight(1f))
                        }

                        InfoCard("Sales overview") {
                            Text(summary.salesOverview, color = Color(0xFF334155))
                        }

                        InfoCard("Provider usage") {
                            Text(summary.providerUsage, color = Color(0xFF334155))
                        }

                        InfoCard("Dealer performance") {
                            Text(summary.dealerPerformance, color = Color(0xFF334155))
                        }

                        InfoCard("Failed orders") {
                            Text(summary.failedOrders, color = Color(0xFF334155))
                        }

                        InfoCard("Profit overview") {
                            Text(summary.profitOverview, color = Color(0xFF334155))
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    R2wBottomNav(
                    selected = R2wBottomTab.More
                )
                }
            }
        }
    }

    @Composable
    private fun ReportsHero(
        status: String,
        loading: Boolean,
        onBack: () -> Unit,
        onRefresh: () -> Unit,
        onExportPdf: () -> Unit
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(32.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF17181C))
        ) {
            Column(modifier = Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Reports", color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                        Text(status, color = Color.White.copy(alpha = 0.72f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Back", color = Color(0xFFFF7900), fontWeight = FontWeight.Black, modifier = Modifier.clickable(onClick = onBack))
                        Text(if (loading) "Loading..." else "Refresh", color = Color.White.copy(alpha = 0.78f), fontWeight = FontWeight.Bold, modifier = Modifier.clickable(enabled = !loading, onClick = onRefresh))
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .background(Color(0xFFFFEFE2), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("📊", fontWeight = FontWeight.Black)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Live business intelligence", color = Color.White, fontWeight = FontWeight.Black)
                        Text("Orders, providers, dealers and PDF export", color = Color.White.copy(alpha = 0.72f))
                    }
                }

                Button(
                    onClick = onExportPdf,
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF7900)),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    if (loading) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                    } else {
                        Text("Export PDF", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    @Composable
    private fun ReportRangeButton(label: String, selected: Boolean, onClick: () -> Unit) {
        if (selected) {
            Button(
                onClick = onClick,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF7900)),
                shape = RoundedCornerShape(50)
            ) {
                Text(label, fontWeight = FontWeight.Bold)
            }
        } else {
            OutlinedButton(onClick = onClick, shape = RoundedCornerShape(50)) {
                Text(label)
            }
        }
    }

    @Composable
    private fun KpiCard(title: String, value: String, subtitle: String, modifier: Modifier = Modifier) {
        Card(
            modifier = modifier,
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, color = Color(0xFF64748B), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Text(value, color = Color(0xFF0F172A), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, color = Color(0xFF64748B), style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
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
}
