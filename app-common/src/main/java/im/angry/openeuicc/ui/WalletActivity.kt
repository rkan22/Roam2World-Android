package im.angry.openeuicc.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.Window
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import im.angry.openeuicc.auth.AuthSession
import im.angry.openeuicc.auth.AuthTokenStore
import im.angry.openeuicc.auth.JwtUtils
import im.angry.openeuicc.auth.MobileWalletRequest
import im.angry.openeuicc.auth.MobileTransaction
import im.angry.openeuicc.auth.Roam2WorldAuthApi
import im.angry.openeuicc.common.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.util.Locale

class WalletActivity : Activity() {
    private val tokenStore by lazy { AuthTokenStore(this) }
    private val authApi by lazy { Roam2WorldAuthApi(BuildConfig.ROAM2WORLD_API_BASE_URL) }
    private val uiScope = CoroutineScope(Dispatchers.Main)

    private val blue = Color.rgb(0, 88, 255)
    private val blueDark = Color.rgb(0, 70, 220)
    private val orange = Color.rgb(255, 106, 0)
    private val navy = Color.rgb(15, 23, 42)
    private val slate = Color.rgb(100, 116, 139)
    private val soft = Color.rgb(248, 250, 252)
    private val line = Color.rgb(226, 232, 240)
    private val green = Color.rgb(22, 163, 74)
    private val red = Color.rgb(239, 68, 68)

    private lateinit var balanceText: TextView
    private lateinit var balanceSubtitleText: TextView
    private lateinit var pendingCountText: TextView
    private lateinit var approvedCountText: TextView
    private lateinit var rejectedCountText: TextView
    private lateinit var totalCountText: TextView
    private lateinit var recentContainer: LinearLayout
    private lateinit var transactionContainer: LinearLayout
    private lateinit var amountInput: EditText
    private lateinit var noteInput: EditText
    private lateinit var submitButton: Button

    private var loading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(android.R.style.Theme_Material_Light_NoActionBar)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        super.onCreate(savedInstanceState)
        title = ""
        actionBar?.hide()

        title = ""
        actionBar?.hide()
        window.statusBarColor = soft
        window.navigationBarColor = Color.WHITE
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(soft)
        }

        val scroll = ScrollView(this)
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(14))
        }

        column.addView(topBar())
        column.addView(space(18))
        column.addView(text("Wallet Request", 27f, navy, true))
        column.addView(text("Request funds to top up your wallet balance.", 14f, slate, false).apply {
            setPadding(0, dp(4), 0, dp(18))
        })

        column.addView(balanceCard())
        column.addView(space(14))
        column.addView(statusStats())
        column.addView(space(22))

        column.addView(text("Create Request", 20f, navy, true).apply {
            setPadding(0, 0, 0, dp(10))
        })
        column.addView(createRequestCard())

        column.addView(space(22))
        column.addView(recentHeader())
        column.addView(space(10))
        recentContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(Color.WHITE, dp(18).toFloat())
        }
        column.addView(recentContainer)

        column.addView(space(22))
        column.addView(text("Transactions", 18f, navy, true).apply {
            setPadding(0, 0, 0, dp(10))
        })
        transactionContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(Color.WHITE, dp(18).toFloat())
        }
        column.addView(transactionContainer)

        scroll.addView(column)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(bottomNav())

        setContentView(root)
        renderEmptyRequests("Loading wallet requests...")
        renderEmptyTransactions("Loading wallet transactions...")
        loadWallet()
    }

    private fun loadWallet() {
        uiScope.launch {
            setLoading(true)
            try {
                val session = activeSessionOrReturnToLogin() ?: return@launch

                val wallet = authApi.wallet(session)
                val requests = authApi.walletRequests(session)

                balanceText.text = money(wallet.currentBalance)
                balanceSubtitleText.text = "Live backend balance"

                renderStats(requests)
                renderRequests(requests)
                renderTransactions(wallet.transactions)
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@WalletActivity, e.message ?: "Wallet load failed", Toast.LENGTH_LONG).show()
                renderEmptyRequests("Could not load wallet requests.")
            } finally {
                setLoading(false)
            }
        }
    }

    private fun submitBalanceRequest() {
        val cleanAmount = amountInput.text.toString().trim().replace(",", ".")
        val cleanNote = noteInput.text.toString().trim()
        val parsedAmount = cleanAmount.toBigDecimalOrNull()

        if (parsedAmount == null || parsedAmount <= BigDecimal.ZERO) {
            Toast.makeText(this, "Valid amount required", Toast.LENGTH_SHORT).show()
            return
        }

        uiScope.launch {
            setLoading(true)
            try {
                val session = activeSessionOrReturnToLogin() ?: return@launch

                val created = authApi.createWalletRequest(
                    session = session,
                    amount = cleanAmount,
                    currency = "USD",
                    note = cleanNote
                )

                Toast.makeText(
                    this@WalletActivity,
                    "Request created: ${money(created.amount)}",
                    Toast.LENGTH_LONG
                ).show()

                amountInput.setText("")
                noteInput.setText("")
                loadWallet()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@WalletActivity, e.message ?: "Wallet request failed", Toast.LENGTH_LONG).show()
            } finally {
                setLoading(false)
            }
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

    private fun setLoading(value: Boolean) {
        loading = value
        submitButton.isEnabled = !value
        submitButton.text = if (value) "Please wait..." else "Submit Top-up Request"
    }

    private fun renderStats(requests: List<MobileWalletRequest>) {
        val pending = requests.count { it.status.equals("pending", true) || it.status.contains("pending", true) }
        val approved = requests.count { it.status.equals("approved", true) || it.status.contains("approved", true) || it.status.contains("completed", true) }
        val rejected = requests.count { it.status.equals("rejected", true) || it.status.contains("rejected", true) || it.status.contains("failed", true) }

        pendingCountText.text = pending.toString()
        approvedCountText.text = approved.toString()
        rejectedCountText.text = rejected.toString()
        totalCountText.text = requests.size.toString()
    }

    private fun renderTransactions(transactions: List<MobileTransaction>) {
        transactionContainer.removeAllViews()

        if (transactions.isEmpty()) {
            renderEmptyTransactions("Wallet transactions will appear here.")
            return
        }

        transactions.take(5).forEachIndexed { index, transaction ->
            transactionContainer.addView(transactionItem(transaction))
            if (index < transactions.take(5).lastIndex) transactionContainer.addView(divider())
        }
    }

    private fun renderEmptyTransactions(message: String) {
        transactionContainer.removeAllViews()
        transactionContainer.gravity = Gravity.CENTER
        transactionContainer.setPadding(dp(18), dp(24), dp(18), dp(24))
        transactionContainer.addView(premiumIconBox("r2w_ic_wallet", slate, 46))
        transactionContainer.addView(text("No transactions yet", 17f, navy, true).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(4))
        })
        transactionContainer.addView(text(message, 13f, slate, false).apply {
            gravity = Gravity.CENTER
        })
    }

    private fun transactionItem(transaction: MobileTransaction): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(14), dp(12), dp(14))

            val isPositive = transaction.amount.trim().startsWith("+")
            val color = if (isPositive) green else slate

            addView(premiumIconBox("r2w_ic_wallet", color, 42))

            val texts = LinearLayout(this@WalletActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), 0, 0, 0)
            }

            texts.addView(text(transaction.title.ifBlank { "Transaction" }, 14f, navy, true))
            texts.addView(text(transaction.subtitle.ifBlank { "-" }, 12f, slate, false).apply {
                setPadding(0, dp(3), 0, 0)
            })

            addView(texts, LinearLayout.LayoutParams(0, -2, 1f))
            addView(text(transaction.amount.ifBlank { "$0.00" }, 14f, color, true))
        }

    private fun renderRequests(requests: List<MobileWalletRequest>) {
        recentContainer.removeAllViews()

        if (requests.isEmpty()) {
            renderEmptyRequests("Submitted top-up requests will appear here.")
            return
        }

        requests.take(5).forEachIndexed { index, request ->
            recentContainer.addView(requestItem(request))
            if (index < requests.take(5).lastIndex) {
                recentContainer.addView(divider())
            }
        }
    }

    private fun renderEmptyRequests(message: String) {
        recentContainer.removeAllViews()
        recentContainer.gravity = Gravity.CENTER
        recentContainer.setPadding(dp(18), dp(26), dp(18), dp(26))
        recentContainer.addView(premiumIconBox("request_balance", slate, 48))
        recentContainer.addView(text("No requests yet", 17f, navy, true).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(4))
        })
        recentContainer.addView(text(message, 13f, slate, false).apply {
            gravity = Gravity.CENTER
        })
    }

    private fun topBar(): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL

            addView(text("‹", 34f, navy, false).apply {
                gravity = Gravity.CENTER
                setOnClickListener { finish() }
            }, LinearLayout.LayoutParams(dp(42), dp(42)))

            addView(TextView(this@WalletActivity).apply { text = "" }, LinearLayout.LayoutParams(0, dp(42), 1f))
            addView(premiumIconBox("r2w_ic_wallet", blue, 42))
        }

    private fun balanceCard(): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(22), dp(22), dp(20))
            background = gradient(blue, blueDark, dp(20).toFloat())

            val top = LinearLayout(this@WalletActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            top.addView(text("Available Balance", 14f, Color.WHITE, false), LinearLayout.LayoutParams(0, -2, 1f))

            top.addView(TextView(this@WalletActivity).apply {
                text = "+  Add Funds"
                textSize = 14f
                gravity = Gravity.CENTER
                setTextColor(blue)
                typeface = Typeface.DEFAULT_BOLD
                background = rounded(Color.WHITE, dp(22).toFloat())
                setOnClickListener { amountInput.requestFocus() }
            }, LinearLayout.LayoutParams(dp(132), dp(44)))

            addView(top)

            balanceText = text("$0.00", 36f, Color.WHITE, true).apply {
                setPadding(0, dp(12), 0, dp(6))
            }
            addView(balanceText)

            val bottom = LinearLayout(this@WalletActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            balanceSubtitleText = text("Loading backend balance...", 15f, Color.WHITE, false)
            bottom.addView(balanceSubtitleText, LinearLayout.LayoutParams(0, -2, 1f))

            bottom.addView(text("Transactions  >", 15f, Color.WHITE, true).apply {
                setOnClickListener {
                    startActivity(Intent(this@WalletActivity, TransactionsActivity::class.java))
                }
            })

            addView(bottom)
        }

    private fun statusStats(): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(12), dp(8), dp(12))
            background = rounded(Color.WHITE, dp(18).toFloat())

            addView(statusItem("request_balance", "Pending", orange) { pendingCountText = it }, LinearLayout.LayoutParams(0, -2, 1f))
            addView(statusItem("review_wallet", "Approved", green) { approvedCountText = it }, LinearLayout.LayoutParams(0, -2, 1f))
            addView(statusItem("review_wallet", "Rejected", red) { rejectedCountText = it }, LinearLayout.LayoutParams(0, -2, 1f))
            addView(statusItem("r2w_ic_wallet", "Total", blue) { totalCountText = it }, LinearLayout.LayoutParams(0, -2, 1f))
        }

    private fun statusItem(iconName: String, label: String, color: Int, bind: (TextView) -> Unit): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER

            addView(premiumIconBox(iconName, color, 36))

            val texts = LinearLayout(this@WalletActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(8), 0, 0, 0)
            }

            val count = text("0", 17f, navy, true)
            bind(count)

            texts.addView(count)
            texts.addView(text(label, 11f, slate, false))
            addView(texts)
        }

    private fun createRequestCard(): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = rounded(Color.WHITE, dp(20).toFloat())

            addView(text("Amount", 13f, slate, false))
            addView(space(8))

            amountInput = EditText(this@WalletActivity).apply {
                hint = "0.00"
                textSize = 18f
                setTextColor(Color.rgb(15, 23, 42))
                setHintTextColor(Color.rgb(100, 116, 139))
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                setSingleLine(true)
                setPadding(dp(16), dp(12), dp(16), dp(12))
                background = rounded(Color.WHITE, dp(14).toFloat())
            }

            val amountRow = LinearLayout(this@WalletActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = rounded(Color.WHITE, dp(14).toFloat())
            }

            amountRow.addView(TextView(this@WalletActivity).apply {
                text = "$"
                textSize = 18f
                gravity = Gravity.CENTER
                setTextColor(navy)
                typeface = Typeface.DEFAULT_BOLD
            }, LinearLayout.LayoutParams(dp(54), dp(52)))

            amountRow.addView(amountInput, LinearLayout.LayoutParams(0, dp(52), 1f))
            addView(amountRow)

            addView(space(10))
            addView(quickAmountRow())

            addView(space(14))
            addView(fieldLabel("Currency"))
            addView(selectRow("$", "USD - Dollar", "Fixed"))

            addView(space(14))
            addView(fieldLabel("Note (Optional)"))

            noteInput = EditText(this@WalletActivity).apply {
                hint = "Add note for this top-up request..."
                textSize = 15f
                setTextColor(Color.rgb(15, 23, 42))
                setHintTextColor(Color.rgb(100, 116, 139))
                minLines = 2
                setPadding(dp(14), dp(12), dp(14), dp(12))
                background = rounded(Color.WHITE, dp(14).toFloat())
            }
            addView(noteInput, LinearLayout.LayoutParams(-1, dp(76)))

            addView(space(16))

            submitButton = Button(this@WalletActivity).apply {
                text = "Submit Top-up Request"
                textSize = 15f
                setTextColor(Color.WHITE)
                typeface = Typeface.DEFAULT_BOLD
                background = rounded(blue, dp(14).toFloat())
                setOnClickListener { submitBalanceRequest() }
            }
            addView(submitButton, LinearLayout.LayoutParams(-1, dp(54)))
        }

    private fun quickAmountRow(): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL

            listOf(
                listOf("$100", "$250", "$500"),
                listOf("$750", "$1000", "$2000")
            ).forEachIndexed { rowIndex, rowValues ->
                val row = LinearLayout(this@WalletActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                }

                rowValues.forEachIndexed { index, value ->
                    row.addView(TextView(this@WalletActivity).apply {
                        text = value
                        textSize = 13f
                        gravity = Gravity.CENTER
                        setTextColor(blue)
                        typeface = Typeface.DEFAULT_BOLD
                        background = rounded(Color.rgb(232, 240, 255), dp(18).toFloat())
                        setOnClickListener {
                            amountInput.setText(value.replace("$", ""))
                            amountInput.setSelection(amountInput.text.length)
                        }
                    }, LinearLayout.LayoutParams(0, dp(36), 1f).apply {
                        if (index > 0) marginStart = dp(8)
                    })
                }

                addView(row)
                if (rowIndex == 0) addView(space(8))
            }
        }

    private fun selectRow(icon: String, value: String, rightText: String): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), 0, dp(12), 0)
            background = rounded(Color.WHITE, dp(14).toFloat())

            addView(TextView(this@WalletActivity).apply {
                text = icon
                textSize = 15f
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                typeface = Typeface.DEFAULT_BOLD
                background = rounded(blue, dp(18).toFloat())
            }, LinearLayout.LayoutParams(dp(36), dp(36)))

            addView(text(value, 15f, navy, true).apply {
                setPadding(dp(12), 0, 0, 0)
            }, LinearLayout.LayoutParams(0, -2, 1f))

            addView(text(rightText, 12f, slate, true))
        }.also {
            it.layoutParams = LinearLayout.LayoutParams(-1, dp(52))
        }

    private fun recentHeader(): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(text("Recent Requests", 18f, navy, true), LinearLayout.LayoutParams(0, -2, 1f))
            addView(text("Live", 14f, blue, true))
        }

    private fun requestItem(request: MobileWalletRequest): LinearLayout {
        val status = request.statusLabel()
        val color = statusColor(request.status)
        val icon = when {
            request.status.contains("pending", true) -> "request_balance"
            request.status.contains("approved", true) || request.status.contains("completed", true) -> "review_wallet"
            else -> "review_wallet"
        }

        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(14), dp(12), dp(14))

            addView(premiumIconBox(icon, color, 42))

            val texts = LinearLayout(this@WalletActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), 0, 0, 0)
            }

            texts.addView(text(request.id ?: "Wallet request", 14f, navy, true))
            texts.addView(text(request.createdAt ?: "-", 12f, slate, false).apply {
                setPadding(0, dp(3), 0, 0)
            })

            addView(texts, LinearLayout.LayoutParams(0, -2, 1f))

            val right = LinearLayout(this@WalletActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.END
            }

            right.addView(text(money(request.amount), 13f, navy, true))
            right.addView(TextView(this@WalletActivity).apply {
                text = status
                textSize = 11f
                gravity = Gravity.CENTER
                setTextColor(color)
                background = rounded(Color.argb(28, Color.red(color), Color.green(color), Color.blue(color)), dp(12).toFloat())
            }, LinearLayout.LayoutParams(dp(86), dp(24)).apply {
                topMargin = dp(4)
            })

            addView(right)
        }
    }

    private fun bottomNav(): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(6), dp(8), dp(8))
            setBackgroundColor(Color.WHITE)

            addView(navIconItem("ic_dashboard", "Home", false) {
                startActivity(Intent(this@WalletActivity, DashboardActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
            })

            addView(navIconItem("ic_quick_store", "Store", false) {
                startActivity(Intent(this@WalletActivity, PackagesActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
            })

            addView(navIconItem("r2w_nav_wallet", "Wallet", true) {
                amountInput.requestFocus()
            })

            addView(navIconItem("r2w_ic_esim", "eSIMs", false) {
                startActivity(Intent(this@WalletActivity, MobileEsimsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
            })
        }

    private fun centerPlus(): TextView =
        TextView(this).apply {
            text = "+"
            textSize = 30f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            background = rounded(blue, dp(28).toFloat())
            setOnClickListener { amountInput.requestFocus() }
            layoutParams = LinearLayout.LayoutParams(0, dp(56), 1f)
        }

    private fun navIconItem(iconName: String, label: String, active: Boolean, onClick: () -> Unit): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(0, dp(58), 1f)

            val color = if (active) blue else slate

            val image = ImageView(this@WalletActivity).apply {
                val preferred = resources.getIdentifier(iconName, "drawable", packageName)
                val fallback = when (label) {
                    "Home" -> resources.getIdentifier("ic_dashboard", "drawable", packageName)
                    "Store" -> resources.getIdentifier("ic_quick_store", "drawable", packageName)
                    "Wallet" -> resources.getIdentifier("r2w_nav_wallet", "drawable", packageName)
                    else -> resources.getIdentifier("r2w_ic_esim", "drawable", packageName)
                }
                val id = if (preferred != 0) preferred else fallback
                if (id != 0) setImageResource(id)
                setColorFilter(color)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
            }

            addView(image, LinearLayout.LayoutParams(dp(24), dp(24)))

            addView(text(label, 11f, color, active).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(3), 0, 0)
            })
        }

    private fun navItem(label: String, onClick: () -> Unit): TextView =
        TextView(this).apply {
            text = label
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(if (label == "Wallet") blue else slate)
            if (label == "Wallet") typeface = Typeface.DEFAULT_BOLD
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(0, dp(56), 1f)
        }

    private fun premiumIconBox(iconName: String, tintColor: Int, sizeDp: Int): LinearLayout =
        LinearLayout(this).apply {
            gravity = Gravity.CENTER
            background = rounded(
                Color.argb(28, Color.red(tintColor), Color.green(tintColor), Color.blue(tintColor)),
                (sizeDp / 2f) * resources.displayMetrics.density
            )

            val image = ImageView(this@WalletActivity).apply {
                val id = resources.getIdentifier(iconName, "drawable", packageName)
                if (id != 0) setImageResource(id)
                setColorFilter(tintColor)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
            }

            addView(image, LinearLayout.LayoutParams(dp(sizeDp - 14), dp(sizeDp - 14)))
            layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
        }

    private fun fieldLabel(value: String): TextView =
        text(value, 13f, slate, false).apply {
            setPadding(0, 0, 0, dp(6))
        }

    private fun text(value: String, size: Float, color: Int, bold: Boolean): TextView =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }

    private fun divider(): TextView =
        TextView(this).apply {
            height = dp(1)
            setBackgroundColor(line)
        }

    private fun rounded(color: Int, radius: Float): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius
            if (color == Color.WHITE) setStroke(dp(1), line)
        }

    private fun gradient(start: Int, end: Int, radius: Float): GradientDrawable =
        GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(start, end)).apply {
            cornerRadius = radius
        }

    private fun space(heightValue: Int): TextView =
        TextView(this).apply {
            text = ""
            height = heightValue
        }

    private fun money(raw: String): String {
        val clean = raw.trim()
        if (clean.isBlank()) return "$0.00"
        val n = clean.replace(",", ".").toBigDecimalOrNull()
        return if (n != null) "$" + String.format(Locale.US, "%.2f", n) else clean
    }

    private fun statusColor(status: String): Int =
        when {
            status.contains("pending", true) -> orange
            status.contains("approved", true) || status.contains("completed", true) -> green
            status.contains("rejected", true) || status.contains("failed", true) -> red
            else -> slate
        }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
