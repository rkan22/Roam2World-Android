package im.angry.openeuicc

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import im.angry.openeuicc.auth.AuthTokenStore
import im.angry.openeuicc.auth.JwtUtils
import im.angry.openeuicc.auth.Roam2WorldAuthApi
import im.angry.openeuicc.common.BuildConfig
import im.angry.openeuicc.ui.LoginActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class MobileAdminActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val tokenStore by lazy { AuthTokenStore(this) }
    private val api by lazy { Roam2WorldAuthApi(BuildConfig.ROAM2WORLD_API_BASE_URL) }

    private lateinit var pendingRequestsText: TextView
    private lateinit var resellerCountText: TextView
    private lateinit var dealerCountText: TextView
    private lateinit var walletSummaryText: TextView
    private lateinit var orderMetricText: TextView
    private lateinit var systemAlertsText: TextView
    private lateinit var salesOverviewText: TextView
    private lateinit var pendingWalletPreviewText: TextView
    private lateinit var resellerPreviewText: TextView
    private lateinit var pricingPreviewText: TextView
    private lateinit var notificationPreviewText: TextView

    private lateinit var refreshButton: Button
    private lateinit var openApprovalsButton: Button
    private lateinit var openResellersButton: Button
    private lateinit var openDealersButton: Button
    private lateinit var openOrdersButton: Button
    private lateinit var openPricingButton: Button
    private lateinit var openReportsButton: Button
    private lateinit var openSystemHealthButton: Button
    private lateinit var openAuditLogsButton: Button
    private lateinit var openSupportTicketsButton: Button
    private lateinit var openNotificationsButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mobile_admin)

        pendingRequestsText = findViewById(R.id.pendingRequestsText)
        resellerCountText = findViewById(R.id.resellerCountText)
        dealerCountText = findViewById(R.id.dealerCountText)
        walletSummaryText = findViewById(R.id.walletSummaryText)
        orderMetricText = findViewById(R.id.orderMetricText)
        systemAlertsText = findViewById(R.id.systemAlertsText)
        salesOverviewText = findViewById(R.id.salesOverviewText)
        pendingWalletPreviewText = findViewById(R.id.pendingWalletPreviewText)
        resellerPreviewText = findViewById(R.id.resellerPreviewText)
        pricingPreviewText = findViewById(R.id.pricingPreviewText)
        notificationPreviewText = findViewById(R.id.notificationPreviewText)

        refreshButton = findViewById(R.id.refreshButton)
        openApprovalsButton = findViewById(R.id.openApprovalsButton)
        openResellersButton = findViewById(R.id.openResellersButton)
        openDealersButton = findViewById(R.id.openDealersButton)
        openOrdersButton = findViewById(R.id.openOrdersButton)
        openPricingButton = findViewById(R.id.openPricingButton)
        openReportsButton = findViewById(R.id.openReportsButton)
        openSystemHealthButton = findViewById(R.id.openSystemHealthButton)
        openAuditLogsButton = findViewById(R.id.openAuditLogsButton)
        openSupportTicketsButton = findViewById(R.id.openSupportTicketsButton)
        openNotificationsButton = findViewById(R.id.openNotificationsButton)

        bindActions()
        showLoading()
        loadDashboard()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun bindActions() {
        refreshButton.setOnClickListener {
            loadDashboard()
        }

        openApprovalsButton.setOnClickListener {
            val intent = Intent(this, ApprovalsActivity::class.java)
            intent.putExtra(
                ApprovalsActivity.EXTRA_APPROVAL_MODE,
                ApprovalsActivity.MODE_ADMIN_RESELLER_WALLET
            )
            startActivity(intent)
        }

        openResellersButton.setOnClickListener {
            startActivity(Intent(this, AdminResellersActivity::class.java))
        }

        openDealersButton.setOnClickListener {
            startActivity(Intent(this, AdminDealersActivity::class.java))
        }

        openOrdersButton.setOnClickListener {
            startActivity(Intent(this, AdminOrdersActivity::class.java))
        }

        openPricingButton.setOnClickListener {
            startActivity(Intent(this, AdminPricingActivity::class.java))
        }

        openReportsButton.setOnClickListener {
            startActivity(Intent(this, AdminReportsActivity::class.java))
        }

        openSystemHealthButton.setOnClickListener {
            startActivity(Intent(this, AdminSystemHealthActivity::class.java))
        }

        openAuditLogsButton.setOnClickListener {
            startActivity(Intent(this, AdminActivityLogsActivity::class.java))
        }

        openSupportTicketsButton.setOnClickListener {
            startActivity(Intent(this, AdminSupportTicketsActivity::class.java))
        }

        openNotificationsButton.setOnClickListener {
            startActivity(Intent(this, AdminNotificationsActivity::class.java))
        }

        findViewById<TextView>(R.id.bottomDashboardNav).setOnClickListener {
            loadDashboard()
        }

        findViewById<TextView>(R.id.bottomResellersNav).setOnClickListener {
            startActivity(Intent(this, AdminResellersActivity::class.java))
        }

        findViewById<TextView>(R.id.bottomWalletNav).setOnClickListener {
            val intent = Intent(this, ApprovalsActivity::class.java)
            intent.putExtra(
                ApprovalsActivity.EXTRA_APPROVAL_MODE,
                ApprovalsActivity.MODE_ADMIN_RESELLER_WALLET
            )
            startActivity(intent)
        }

        findViewById<TextView>(R.id.bottomPricingNav).setOnClickListener {
            startActivity(Intent(this, AdminPricingActivity::class.java))
        }

        findViewById<TextView>(R.id.bottomMoreNav).setOnClickListener {
            startActivity(Intent(this, AdminReportsActivity::class.java))
        }
    }

    private fun showLoading() {
        pendingRequestsText.text = "Pending Wallet\nLoading..."
        resellerCountText.text = "Resellers\nLoading..."
        dealerCountText.text = "Dealers\nLoading..."
        walletSummaryText.text = "Wallet Balance\nLoading..."
        orderMetricText.text = "Orders\nLoading..."
        systemAlertsText.text = "System Alerts\nLoading..."
        salesOverviewText.text = "Sales Overview\nLoading..."
        pendingWalletPreviewText.text = "Pending Wallet Requests\nLoading..."
        resellerPreviewText.text = "Reseller Management\nLoading..."
        pricingPreviewText.text = "Pricing Management\nLoading..."
        notificationPreviewText.text = "Notifications\nLoading..."
    }

    private fun loadDashboard() {
        showLoading()

        scope.launch {
            val session = withContext(Dispatchers.IO) {
                tokenStore.getSession()
            }

            if (session == null || JwtUtils.isExpired(session.accessToken)) {
                redirectToLogin()
                return@launch
            }

            val result = runCatching {
                api.mobileAdminDashboardRaw(session)
            }

            result
                .onSuccess { response ->
                    renderDashboard(response)
                }
                .onFailure { error ->
                    pendingRequestsText.text = "Pending Wallet\n-"
                    resellerCountText.text = "Resellers\n-"
                    dealerCountText.text = "Dealers\n-"
                    walletSummaryText.text = "Wallet Balance\nAPI error"
                    orderMetricText.text = "Orders\n-"
                    systemAlertsText.text = "System Alerts\n-"
                    salesOverviewText.text = "Sales Overview\nAPI error"
                    pendingWalletPreviewText.text = "Pending Wallet Requests\nAPI error"
                    resellerPreviewText.text = "Reseller Management\nAPI error"
                    pricingPreviewText.text = "Pricing Management\nAPI error"
                    notificationPreviewText.text = "Notifications\nAPI error"

                    Toast.makeText(
                        this@MobileAdminActivity,
                        error.message ?: "Dashboard API error",
                        Toast.LENGTH_LONG
                    ).show()
                }
        }
    }

    private fun renderDashboard(response: JSONObject) {
        val data = response.optJSONObject("data") ?: response
        val metrics = data.optJSONObject("metrics") ?: JSONObject()

        val resellers = metrics.optJSONObject("resellers") ?: JSONObject()
        val dealers = metrics.optJSONObject("dealers") ?: JSONObject()
        val orders = metrics.optJSONObject("orders") ?: JSONObject()
        val walletRequests = metrics.optJSONObject("wallet_requests") ?: JSONObject()
        val revenue = metrics.optJSONObject("revenue") ?: JSONObject()
        val notifications = metrics.optJSONObject("notifications") ?: JSONObject()
        val system = metrics.optJSONObject("system") ?: JSONObject()

        val resellerTotal = resellers.optInt("total", 0)
        val resellerActive = resellers.optInt("active", 0)
        val resellerSuspended = resellers.optInt("suspended", 0)

        val dealerTotal = dealers.optInt("total", 0)
        val dealerActive = dealers.optInt("active", 0)
        val dealerSuspended = dealers.optInt("suspended", 0)

        val orderTotal = orders.optInt("total", 0)
        val orderToday = orders.optInt("today", 0)
        val orderPending = orders.optInt("pending", 0)
        val orderProcessing = orders.optInt("processing", 0)
        val orderCompleted = orders.optInt("completed", 0)
        val orderCancelled = orders.optInt("cancelled", 0)

        val pendingResellerWallet = walletRequests.optInt("reseller_pending", 0)
        val pendingDealerWallet = walletRequests.optInt("dealer_pending", 0)

        val unreadNotifications = notifications.optInt("unread", 0)
        val systemStatus = system.optString("status", "healthy")
        val riskScore = system.optInt("risk_score", 0)

        val totalSales = revenue.optString("total_sales", "0.00")
        val currency = revenue.optString("currency", "USD")

        val pendingTotal = pendingResellerWallet + pendingDealerWallet
        val systemAlerts = riskScore

        val statusLabel = if (systemStatus == "healthy") "Healthy" else "Attention"

        orderMetricText.text =
            "Total Orders\n$orderTotal\n$orderToday Today\n$orderPending Pending"

        resellerCountText.text =
            "Active Resellers\n$resellerActive\n$resellerTotal Total\n$resellerSuspended Suspended"

        dealerCountText.text =
            "Active Dealers\n$dealerActive\n$dealerTotal Total\n$dealerSuspended Suspended"

        walletSummaryText.text =
            "Revenue (USD)\n$totalSales\n$currency\nCurrent period"

        pendingRequestsText.text =
            "Pending Wallet\n$pendingTotal\nReview Now"

        systemAlertsText.text =
            "System Alerts\n$systemAlerts\n$statusLabel"

        salesOverviewText.text =
            "Sales Performance\n\nTotal Revenue\n$totalSales $currency\n\n$orderTotal orders total\n$orderToday orders today"

        resellerPreviewText.text =
            "Recent Activity          View All\n\nNew reseller registered        10:30 AM\nSunrise Telecom\n\nWallet approved                09:45 AM\nFastConnect - $1,250.00\n\nOrder completed                09:20 AM\nUSA 5GB / 30 Days\n\nView All Activities"

        pendingWalletPreviewText.text =
            "Pending Actions          View All\n\nWallet Approvals           $pendingTotal\n$pendingTotal requests\n\nSupport Tickets            3\n3 open\n\nOrder Updates              $orderPending\n$orderPending pending"

        pricingPreviewText.text =
            "Pricing Management\nProvider markup controls active"

        notificationPreviewText.text =
            "Notifications\n$unreadNotifications unread alerts"
    }

    private fun redirectToLogin() {
        startActivity(
            Intent(this, LoginActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
        )
        finish()
    }
}
