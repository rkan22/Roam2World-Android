package im.angry.openeuicc.ui

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
import android.app.Activity

class WalletActivity : Activity() {
    private val orange = Color.rgb(255, 106, 0)
    private val navy = Color.rgb(15, 23, 42)
    private val slate = Color.rgb(100, 116, 139)
    private val bg = Color.rgb(247, 248, 252)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Wallet"

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
        }

        val scroll = ScrollView(this)
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(36, 56, 36, 24)
        }

        column.addView(text("Wallet", 34f, navy, true))
        column.addView(text("Manage balance and top-up requests", 15f, slate, false).apply {
            setPadding(0, 6, 0, 24)
        })

        column.addView(balanceCard())
        column.addView(space(18))

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 2f
        }

        row.addView(statCard("Pending", "€0.00", "Requests"), LinearLayout.LayoutParams(0, -2, 1f).apply {
            marginEnd = 10
        })

        row.addView(statCard("Spent", "€0.00", "This month"), LinearLayout.LayoutParams(0, -2, 1f).apply {
            marginStart = 10
        })

        column.addView(row)
        column.addView(space(22))

        column.addView(sectionTitle("Actions"))

        column.addView(actionCard("Request Balance", "Create a new wallet top-up request", "→") {
            startActivity(Intent(this, WalletRequestActivity::class.java))
        })

        column.addView(space(12))

        column.addView(actionCard("Request History", "Track pending, completed and failed requests", "→") {
            startActivity(Intent(this, WalletRequestHistoryActivity::class.java))
        })

        column.addView(space(12))

        column.addView(actionCard("Store", "Use your wallet balance to buy eSIM packages", "→") {
            startActivity(Intent(this, PackagesActivity::class.java))
        })

        column.addView(space(18))
        column.addView(sectionTitle("Recent Activity"))
        column.addView(transactionCard("Wallet ready", "No recent wallet transactions yet.", "+ €0.00"))

        scroll.addView(column)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(bottomNav())

        setContentView(root)
    }

    private fun balanceCard(): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(30, 30, 30, 30)
            background = rounded(orange, 30f)

            addView(text("Available Balance", 15f, Color.WHITE, false))
            addView(text("€0.00", 42f, Color.WHITE, true).apply {
                setPadding(0, 8, 0, 4)
            })
            addView(text("Ready for travel eSIM purchases", 14f, Color.WHITE, false))
        }

    private fun statCard(title: String, value: String, note: String): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(22, 20, 22, 20)
            background = rounded(Color.WHITE, 24f)

            addView(text(title, 13f, slate, false))
            addView(text(value, 22f, navy, true).apply {
                setPadding(0, 4, 0, 2)
            })
            addView(text(note, 12f, slate, false))
        }

    private fun actionCard(title: String, body: String, arrow: String, onClick: () -> Unit): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(24, 22, 24, 22)
            background = rounded(Color.WHITE, 24f)
            setOnClickListener { onClick() }

            val texts = LinearLayout(this@WalletActivity).apply {
                orientation = LinearLayout.VERTICAL
            }

            texts.addView(text(title, 18f, navy, true))
            texts.addView(text(body, 13f, slate, false).apply {
                setPadding(0, 4, 0, 0)
            })

            addView(texts, LinearLayout.LayoutParams(0, -2, 1f))
            addView(text(arrow, 26f, orange, true))
        }

    private fun transactionCard(title: String, body: String, amount: String): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(24, 22, 24, 22)
            background = rounded(Color.WHITE, 24f)

            val texts = LinearLayout(this@WalletActivity).apply {
                orientation = LinearLayout.VERTICAL
            }

            texts.addView(text(title, 17f, navy, true))
            texts.addView(text(body, 13f, slate, false).apply {
                setPadding(0, 4, 0, 0)
            })

            addView(texts, LinearLayout.LayoutParams(0, -2, 1f))
            addView(text(amount, 16f, orange, true))
        }

    private fun bottomNav(): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(18, 10, 18, 14)
            setBackgroundColor(Color.WHITE)

            addView(navItem("Home", false) {
                startActivity(Intent(this@WalletActivity, DashboardActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
            })

            addView(navItem("Store", false) {
                startActivity(Intent(this@WalletActivity, PackagesActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
            })

            addView(navItem("Wallet", true) {})

            addView(navItem("eSIMs", false) {
                startActivity(Intent(this@WalletActivity, MobileEsimsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
            })
        }

    private fun navItem(label: String, active: Boolean, onClick: () -> Unit): TextView =
        text(label, 13f, if (active) orange else slate, active).apply {
            gravity = Gravity.CENTER
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }

    private fun sectionTitle(value: String): TextView =
        text(value, 18f, navy, true).apply {
            setPadding(0, 0, 0, 12)
        }

    private fun text(value: String, size: Float, color: Int, bold: Boolean): TextView =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }

    private fun rounded(color: Int, radius: Float): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius
        }

    private fun space(heightValue: Int): TextView =
        TextView(this).apply {
            text = ""
            height = heightValue
        }
}
