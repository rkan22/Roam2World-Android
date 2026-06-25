package im.angry.openeuicc.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
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

class WalletApprovalsActivity : Activity() {
    private val tokenStore by lazy { AuthTokenStore(this) }
    private val api by lazy { Roam2WorldAuthApi(BuildConfig.ROAM2WORLD_API_BASE_URL) }
    private val uiScope = CoroutineScope(Dispatchers.Main)

    private lateinit var titleText: TextView
    private lateinit var subtitleText: TextView
    private lateinit var list: LinearLayout
    private lateinit var refreshButton: Button
    private var activeFilter = "pending"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        loadRequests()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(255, 247, 240))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(18), dp(22), dp(14))
            background = rounded(Color.rgb(255, 122, 26), 0)
        }
        titleText = TextView(this).apply {
            text = "Wallet Approvals"
            setTextColor(Color.WHITE)
            textSize = 25f
            typeface = Typeface.DEFAULT_BOLD
        }
        subtitleText = TextView(this).apply {
            text = "Pending wallet requests"
            setTextColor(Color.WHITE)
            alpha = 0.9f
            textSize = 14f
            setPadding(0, dp(4), 0, 0)
        }
        header.addView(titleText)
        header.addView(subtitleText)
        root.addView(header)

        val filters = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(14), dp(14), dp(14), dp(8))
            gravity = Gravity.CENTER
        }
        listOf("pending", "approved", "rejected", "all").forEach { status ->
            val button = Button(this).apply {
                text = status.replaceFirstChar { it.uppercase() }
                textSize = 12f
                setTextColor(if (status == activeFilter) Color.WHITE else Color.rgb(35, 35, 35))
                background = rounded(if (status == activeFilter) Color.rgb(255, 122, 26) else Color.WHITE, 24)
                setOnClickListener {
                    activeFilter = status
                    buildUi()
                    loadRequests()
                }
            }
            filters.addView(button, LinearLayout.LayoutParams(0, dp(42), 1f).apply { setMargins(dp(3), 0, dp(3), 0) })
        }
        root.addView(filters)

        refreshButton = Button(this).apply {
            text = "Refresh"
            setTextColor(Color.WHITE)
            background = rounded(Color.rgb(23, 24, 28), 18)
            setOnClickListener { loadRequests() }
        }
        root.addView(refreshButton, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48)).apply {
            setMargins(dp(18), dp(2), dp(18), dp(10))
        })

        val scroll = ScrollView(this)
        list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(4), dp(16), dp(30))
        }
        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
    }

    private fun loadRequests() {
        list.removeAllViews()
        list.addView(message("Loading requests..."))
        refreshButton.isEnabled = false

        uiScope.launch {
            val session = activeSessionOrReturnToLogin() ?: return@launch
            val role = session.role?.lowercase() ?: "dealer"
                        titleText.text = if (role == "admin" || role == "superadmin" || role == "staff") "Admin Approvals" else "Dealer Approvals"
            subtitleText.text = if (role == "admin" || role == "superadmin" || role == "staff") "Reseller wallet requests" else "Dealer wallet requests"

            val result = runCatching {
                val all = api.walletApprovalRequests(session)
                if (activeFilter == "all") all else all.filter { it.status.equals(activeFilter, ignoreCase = true) }
            }
            refreshButton.isEnabled = true
            list.removeAllViews()
            result.onSuccess { requests ->
                if (requests.isEmpty()) {
                    list.addView(message("No ${activeFilter} requests."))
                } else {
                    requests.forEach { list.addView(requestCard(session, it)) }
                }
            }.onFailure { error ->
                list.addView(message(error.message ?: "Could not load requests."))
            }
        }
    }

    private fun requestCard(session: AuthSession, request: MobileWalletRequest): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(16))
            background = rounded(Color.WHITE, 26)
        }
        val title = TextView(this).apply {
            text = "Request #${request.id ?: "-"}"
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(25, 25, 25))
        }
        val amount = TextView(this).apply {
            text = "$${request.amount} ${request.currency}"
            textSize = 28f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(255, 122, 26))
            setPadding(0, dp(8), 0, 0)
        }
        val meta = TextView(this).apply {
            text = "Status: ${request.statusLabel()}\nCreated: ${request.createdAt ?: "-"}\nNote: ${request.note?.ifBlank { "-" } ?: "-"}"
            textSize = 13f
            setTextColor(Color.rgb(95, 95, 95))
            setPadding(0, dp(8), 0, dp(12))
        }
        card.addView(title)
        card.addView(amount)
        card.addView(meta)

        if (request.status.equals("pending", ignoreCase = true) && !request.id.isNullOrBlank()) {
            val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            val approve = Button(this).apply {
                text = "Approve"
                setTextColor(Color.WHITE)
                background = rounded(Color.rgb(24, 160, 88), 18)
                setOnClickListener { approve(session, request.id) }
            }
            val reject = Button(this).apply {
                text = "Reject"
                setTextColor(Color.WHITE)
                background = rounded(Color.rgb(217, 45, 32), 18)
                setOnClickListener { reject(session, request.id) }
            }
            actions.addView(approve, LinearLayout.LayoutParams(0, dp(46), 1f).apply { setMargins(0, 0, dp(6), 0) })
            actions.addView(reject, LinearLayout.LayoutParams(0, dp(46), 1f).apply { setMargins(dp(6), 0, 0, 0) })
            card.addView(actions)
        }

        card.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            setMargins(0, 0, 0, dp(14))
        }
        return card
    }

    private fun approve(session: AuthSession, id: String) {
        uiScope.launch {
            val result = runCatching { api.approveWalletApprovalRequest(session, id, "Approved from mobile") }
            Toast.makeText(this@WalletApprovalsActivity, result.fold({ "Approved" }, { it.message ?: "Approve failed" }), Toast.LENGTH_SHORT).show()
            loadRequests()
            loadRequests()
            loadRequests()
        }
    }

    private fun reject(session: AuthSession, id: String) {
        uiScope.launch {
            val result = runCatching { api.rejectWalletApprovalRequest(session, id, "Rejected from mobile") }
            Toast.makeText(this@WalletApprovalsActivity, result.fold({ "Rejected" }, { it.message ?: "Reject failed" }), Toast.LENGTH_SHORT).show()
            loadRequests()
            loadRequests()
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

    private fun message(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 15f
        setTextColor(Color.rgb(75, 75, 75))
        gravity = Gravity.CENTER
        setPadding(dp(12), dp(38), dp(12), dp(38))
    }

    private fun rounded(color: Int, radiusDp: Int): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radiusDp).toFloat()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
