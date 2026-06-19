package im.angry.openeuicc.ui

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontWeight

class PurchaseReviewActivity : ComponentActivity() {

    private var loading by mutableStateOf(false)
    private var simIccid by mutableStateOf("")
    private var message by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val packageName = intent.getStringExtra(EXTRA_NAME) ?: "eSIM Package"
        val price = intent.getStringExtra(EXTRA_PRICE) ?: "0"
        val customer = listOfNotNull(
            intent.getStringExtra(EXTRA_CUSTOMER_FIRST_NAME),
            intent.getStringExtra(EXTRA_CUSTOMER_LAST_NAME)
        ).joinToString(" ").ifBlank { "Customer" }

        setContent {
            R2wMockScreen(
                title = "Checkout",
                subtitle = packageName,
                onBack = { finish() },
                bottomTab = R2wBottomTab.Esims
            ) {
                R2wMockHero(
                    eyebrow = "Order summary",
                    title = packageName,
                    body = "Review your order before confirming purchase.",
                    amount = "$ $price",
                    badge = "Secure"
                )

                R2wMockCard("Customer") {
                    R2wMockLine("Name", customer)
                    R2wMockLine("Phone", intent.getStringExtra(EXTRA_CUSTOMER_PHONE) ?: "--")
                }

                R2wMockCard("Package") {
                    R2wMockLine("Package", packageName)
                    R2wMockLine("Provider", intent.getStringExtra(EXTRA_PROVIDER) ?: "--")
                    R2wMockLine("Country", intent.getStringExtra(EXTRA_COUNTRY) ?: "--")
                }

                R2wMockCard("Payment") {
                    R2wMockLine("Price", "$ $price", strong = true)
                    R2wMockLine("Tax", "$0.00")
                    R2wMockLine("Total", "$ $price", strong = true)
                }

                if (requiresSimIccid()) {
                    R2wMockCard("SIM ICCID") {
                        R2wMockTextField(simIccid, { simIccid = it }, "ICCID")
                    }
                }

                message?.let {
                    R2wMockCard("Status") {
                        R2wMockLine("Result", it, strong = true)
                    }
                }

                R2wMockPrimaryButton(if (loading) "Processing..." else "Confirm Purchase") {
                    submit()
                }

                R2wMockSecondaryButton("Cancel") {
                    finish()
                }
            }
        }
    }

    private fun submit() {
        val msg = "Purchase flow triggered (mock UI)"
        message = msg
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    }

    private fun requiresSimIccid(): Boolean {
        val provider = intent.getStringExtra(EXTRA_PROVIDER).orEmpty().lowercase()
        val name = intent.getStringExtra(EXTRA_NAME).orEmpty().lowercase()
        return provider.contains("tgt") && name.contains("sim")
    }

    companion object {
        private const val EXTRA_ID = "package.id"
        private const val EXTRA_PROVIDER = "package.provider"
        private const val EXTRA_NAME = "package.name"
        private const val EXTRA_COUNTRY = "package.country"
        private const val EXTRA_PRICE = "package.price"
        private const val EXTRA_CUSTOMER_FIRST_NAME = "customer.first_name"
        private const val EXTRA_CUSTOMER_LAST_NAME = "customer.last_name"
        private const val EXTRA_CUSTOMER_PHONE = "customer.phone"

        fun createIntent(context: android.content.Context, intent: android.content.Intent, first: String, last: String, phone: String, balance: String?) : android.content.Intent {
            val i = android.content.Intent(context, PurchaseReviewActivity::class.java)
            listOf(
                EXTRA_ID, EXTRA_PROVIDER, EXTRA_NAME, EXTRA_COUNTRY, EXTRA_PRICE
            ).forEach { i.putExtra(it, intent.getStringExtra(it)) }
            i.putExtra(EXTRA_CUSTOMER_FIRST_NAME, first)
            i.putExtra(EXTRA_CUSTOMER_LAST_NAME, last)
            i.putExtra(EXTRA_CUSTOMER_PHONE, phone)
            return i
        }
    }
}
