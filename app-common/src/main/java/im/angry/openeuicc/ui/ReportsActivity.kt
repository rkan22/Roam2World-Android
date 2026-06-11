package im.angry.openeuicc.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.lifecycleScope
import im.angry.openeuicc.auth.AuthSession
import im.angry.openeuicc.auth.AuthTokenStore
import im.angry.openeuicc.auth.JwtUtils
import im.angry.openeuicc.auth.MobileOrder
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
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class ReportsActivity : AppCompatActivity() {

    private fun formatReportDate(value: String?): String {
        val raw = value?.trim().orEmpty()
        if (raw.isBlank()) return ""
        return try {
            val formatter = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm", Locale.ENGLISH)
            Instant.parse(raw).atZone(ZoneId.systemDefault()).format(formatter)
        } catch (_: Exception) {
            raw
        }
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

        setupInsets()
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
            val orders = ordersRequest.await().getOrNull()?.orders.orEmpty()
            val wallet = walletRequest.await().getOrNull()
            val dealers = dealersRequest.await().getOrNull()?.dealers.orEmpty()

            val totalRevenue = orders.mapNotNull { amount(it.price) }.fold(BigDecimal.ZERO, BigDecimal::add)
            val totalProfit = totalRevenue.multiply(BigDecimal("0.22"))
            val completedOrders = orders.count { it.statusLabel()?.contains("Completed", ignoreCase = true) == true }
                .takeIf { it > 0 } ?: orders.size

            revenue.text = currency(totalRevenue)
            revenueDelta.text = "${orders.size} orders loaded"
            sales.text = completedOrders.toString()
            salesDelta.text = "${orders.count { it.statusLabel()?.contains("Pending", ignoreCase = true) == true }} pending • ${orders.count { it.statusLabel()?.contains("Failed", ignoreCase = true) == true }} failed"
            profit.text = currency(totalProfit)
            activeEsims.text = dashboard?.activeEsimCount ?: "--"

            salesOverview.text = buildSalesOverview(orders, wallet?.currentBalance)
            providerUsage.text = buildProviderUsage(orders)
            failedOrders.text = buildFailedOrders(orders)
            profitOverview.text = buildProfitOverview(totalRevenue, totalProfit, orders)
            dealerPerformance.text = if (dealers.isEmpty()) {
                "Dealer performance data unavailable"
            } else {
                dealers.take(5).mapIndexed { index, dealer ->
                    "${index + 1}. ${dealer.name}\nOrders: ${dealer.totalOrders} • Balance: ${dealer.currentBalance}"
                }.joinToString("\n\n")
            }
            status.text = "Live report data loaded • ${orders.size} orders • ${dealers.size} dealers"
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
                order.price?.let { "Amount: $it" },
                order.statusLabel()?.let { "Status: ${formatReportStatus(it)}" },
                order.createdAt?.let { "Date: ${formatReportDate(it)}" }
            ).joinToString("\n")
        }

        return listOfNotNull(
            currentBalance?.let { "Current balance: $it" },
            "Orders: ${orders.size} total • $completed completed • $pending pending • $failed failed",
            latest.ifBlank { "No order data yet" }
        ).joinToString("\n\n")
    }

    private fun buildProviderUsage(orders: List<MobileOrder>): String {
        val providers = orders.groupingBy { it.provider ?: "Unknown" }.eachCount()
        if (providers.isEmpty()) return "No provider usage yet"
        val total = providers.values.sum().coerceAtLeast(1)
        return providers.entries
            .sortedByDescending { it.value }
            .take(5)
            .joinToString("\n") { (provider, count) ->
                val percent = (count * 100) / total
                "${formatReportStatus(provider)} • $percent% • $count orders"
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
