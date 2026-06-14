package im.angry.openeuicc.ui

import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import im.angry.openeuicc.auth.AuthSession
import im.angry.openeuicc.auth.AuthTokenStore
import im.angry.openeuicc.auth.JwtUtils
import im.angry.openeuicc.auth.MobileOrder
import im.angry.openeuicc.auth.MobileDealer
import im.angry.openeuicc.auth.Roam2WorldAuthApi
import im.angry.openeuicc.common.BuildConfig
import im.angry.openeuicc.common.R
import im.angry.openeuicc.util.activityToolbarInsetHandler
import im.angry.openeuicc.util.mainViewPaddingInsetHandler
import im.angry.openeuicc.util.setupRootViewSystemBarInsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class ReportsActivity : AppCompatActivity() {

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


    private val tokenStore by lazy { AuthTokenStore(this) }
    private val authApi by lazy { Roam2WorldAuthApi(BuildConfig.ROAM2WORLD_API_BASE_URL) }

    private lateinit var scroll: NestedScrollView
    private lateinit var status: TextView
    private lateinit var revenue: TextView
    private lateinit var revenueDelta: TextView
    private lateinit var sales: TextView
    private lateinit var salesDelta: TextView
    private lateinit var profit: TextView
    private lateinit var activeEsims: TextView
    private lateinit var salesOverview: TextView
    private lateinit var providerUsage: TextView
    private lateinit var dealerPerformance: TextView
    private lateinit var failedOrders: TextView
    private lateinit var profitOverview: TextView
    private lateinit var filterToday: MaterialButton
    private lateinit var filterSevenDays: MaterialButton
    private lateinit var filterThirtyDays: MaterialButton
    private lateinit var filterAll: MaterialButton
    private lateinit var exportCsv: MaterialButton
    private lateinit var bottomNav: BottomNavigationView

    private var reportRange: ReportRange = ReportRange.THIRTY_DAYS
    private var loadedOrders: List<MobileOrder> = emptyList()
    private var loadedDealers: List<MobileDealer> = emptyList()
    private var loadedWalletBalance: String? = null
    private var loadedActiveEsims: String? = null

    private enum class ReportRange {
        TODAY,
        SEVEN_DAYS,
        THIRTY_DAYS,
        ALL
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reports)
        setSupportActionBar(requireViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.r2w_reports)

        scroll = requireViewById(R.id.reports_scroll)
        status = requireViewById(R.id.reports_status)
        revenue = requireViewById(R.id.reports_revenue)
        revenueDelta = requireViewById(R.id.reports_revenue_delta)
        sales = requireViewById(R.id.reports_sales)
        salesDelta = requireViewById(R.id.reports_sales_delta)
        profit = requireViewById(R.id.reports_profit)
        activeEsims = requireViewById(R.id.reports_active_esims)
        salesOverview = requireViewById(R.id.reports_sales_overview)
        providerUsage = requireViewById(R.id.reports_provider_usage)
        dealerPerformance = requireViewById(R.id.reports_dealer_performance)
        failedOrders = requireViewById(R.id.reports_failed_orders)
        profitOverview = requireViewById(R.id.reports_profit_overview)
        filterToday = requireViewById(R.id.reports_filter_today)
        filterSevenDays = requireViewById(R.id.reports_filter_7_days)
        filterThirtyDays = requireViewById(R.id.reports_filter_30_days)
        filterAll = requireViewById(R.id.reports_filter_all)
        exportCsv = requireViewById(R.id.reports_export_csv)
        bottomNav = requireViewById(R.id.reports_bottom_nav)

        setupInsets()
        setupFilters()
        setupBottomNavigation()
        renderLoading()
        loadReports()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun setupInsets() {
        setupRootViewSystemBarInsets(
            window.decorView.rootView,
            arrayOf(
                this::activityToolbarInsetHandler,
                mainViewPaddingInsetHandler(scroll)
            ),
            consume = false
        )
    }

    private fun setupBottomNavigation() {
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_dashboard -> {
                    startActivity(Intent(this, DashboardActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
                    false
                }
                R.id.nav_packages -> {
                    startActivity(Intent(this, PackagesActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
                    false
                }
                R.id.nav_wallet -> {
                    startActivity(Intent(this, WalletActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
                    false
                }
                R.id.nav_esims -> {
                    startActivity(Intent(this, MobileEsimsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
                    false
                }
                R.id.nav_more -> {
                    startActivity(Intent(this, MoreActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
                    false
                }
                else -> false
            }
        }
        bottomNav.menu.findItem(R.id.nav_more)?.isChecked = true
    }

    private fun setupFilters() {
        filterToday.setOnClickListener { applyReportRange(ReportRange.TODAY) }
        filterSevenDays.setOnClickListener { applyReportRange(ReportRange.SEVEN_DAYS) }
        filterThirtyDays.setOnClickListener { applyReportRange(ReportRange.THIRTY_DAYS) }
        filterAll.setOnClickListener { applyReportRange(ReportRange.ALL) }
        exportCsv.setOnClickListener { exportReportPdf() }
        updateFilterButtons()
    }

    private fun applyReportRange(range: ReportRange) {
        reportRange = range
        updateFilterButtons()
        renderReports()
    }

    private fun updateFilterButtons() {
        val buttons = listOf(
            filterToday to ReportRange.TODAY,
            filterSevenDays to ReportRange.SEVEN_DAYS,
            filterThirtyDays to ReportRange.THIRTY_DAYS,
            filterAll to ReportRange.ALL
        )

        buttons.forEach { (button, range) ->
            val selected = range == reportRange
            button.isSelected = selected
            button.alpha = 1.0f
            button.strokeWidth = 0
            button.setTextColor(
                getColor(if (selected) android.R.color.white else R.color.r2w_text_primary)
            )
            button.backgroundTintList = android.content.res.ColorStateList.valueOf(
                getColor(if (selected) R.color.r2w_premium_primary else android.R.color.white)
            )
        }
    }

    private fun renderLoading() {
        status.text = "Loading live reports..."
        revenue.text = "--"
        revenueDelta.text = "Live orders"
        sales.text = "--"
        salesDelta.text = "Live orders"
        profit.text = "--"
        activeEsims.text = "--"
        salesOverview.text = "Loading sales overview..."
        providerUsage.text = "Loading provider usage..."
        dealerPerformance.text = "Loading dealer performance..."
        failedOrders.text = "Loading failed orders..."
        profitOverview.text = "Loading profit overview..."
    }

    private fun loadReports() {
        lifecycleScope.launch {
            val session = activeSessionOrReturnToLogin() ?: return@launch
            val dashboardRequest = async { runCatching { authApi.dashboard(session) } }
            val ordersRequest = async { runCatching { authApi.orders(session) } }
            val walletRequest = async { runCatching { authApi.wallet(session) } }
            val dealersRequest = async { runCatching { authApi.dealers(session) } }

            val dashboard = dashboardRequest.await().getOrNull()
            val wallet = walletRequest.await().getOrNull()

            loadedOrders = ordersRequest.await().getOrNull()?.orders.orEmpty()
            loadedDealers = dealersRequest.await().getOrNull()?.dealers.orEmpty()
            loadedWalletBalance = wallet?.currentBalance
            loadedActiveEsims = dashboard?.activeEsimCount

            renderReports()
        }
    }

    private fun renderReports() {
        val orders = filteredOrders()
        val totalRevenue = orders.mapNotNull { amount(it.price) }.fold(BigDecimal.ZERO, BigDecimal::add)
        val totalProfit = totalRevenue.multiply(BigDecimal("0.22"))
        val completedOrders = orders.count { it.statusLabel()?.contains("Completed", ignoreCase = true) == true }
            .takeIf { it > 0 } ?: orders.size
        val pendingOrders = orders.count { it.statusLabel()?.contains("Pending", ignoreCase = true) == true }
        val failedOrderCount = orders.count { it.statusLabel()?.contains("Failed", ignoreCase = true) == true }

        revenue.text = currency(totalRevenue)
        revenueDelta.text = "↑ Live • ${orders.size} orders"
        sales.text = completedOrders.toString()
        salesDelta.text = "↑ $pendingOrders pending • $failedOrderCount failed"
        profit.text = currency(totalProfit)
        activeEsims.text = "Active eSIMs\n${loadedActiveEsims ?: "--"}\nLive dashboard"

        salesOverview.text = buildSalesOverview(orders, loadedWalletBalance)
        providerUsage.text = buildProviderUsage(orders)
        failedOrders.text = buildFailedOrders(orders)
        profitOverview.text = buildProfitOverview(totalRevenue, totalProfit, orders)
        dealerPerformance.text = buildDealerPerformance(loadedDealers)
        status.text = if (orders.isEmpty()) {
            getString(R.string.reports_empty_status, rangeLabel())
        } else {
            getString(R.string.reports_loaded_status, orders.size, loadedDealers.size, rangeLabel())
        }
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
            ReportRange.TODAY -> getString(R.string.reports_filter_today)
            ReportRange.SEVEN_DAYS -> getString(R.string.reports_filter_7_days)
            ReportRange.THIRTY_DAYS -> getString(R.string.reports_filter_30_days)
            ReportRange.ALL -> getString(R.string.reports_filter_all)
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
                color = Color.rgb(24, 38, 58)
                textSize = 18f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(95, 107, 124)
                textSize = 10f
            }
            val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 8.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(32, 45, 64)
                textSize = 8.5f
            }
            val mutedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(95, 107, 124)
                textSize = 8.5f
            }
            val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(224, 230, 238)
                strokeWidth = 1f
            }
            val headerBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(37, 99, 235)
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
                color = Color.rgb(95, 107, 124)
                textSize = 8.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val summaryValuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(24, 38, 58)
                textSize = 14f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val summaryCardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(246, 249, 253)
                style = Paint.Style.FILL
            }
            val summaryStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(224, 230, 238)
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
                    PackageNameCleaner.clean(order.packageName),
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
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        startActivity(Intent.createChooser(intent, getString(R.string.reports_export_title)))
    }

    private fun csvEscape(value: String): String {
        val escaped = value.replace("\"", "\"\"")
        return "\"$escaped\""
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
        val percent = if (orders.isEmpty()) 0 else (failed.size * 100) / orders.size
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
}
