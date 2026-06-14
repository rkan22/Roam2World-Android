package im.angry.openeuicc.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import im.angry.openeuicc.auth.AuthSession
import im.angry.openeuicc.auth.AuthTokenStore
import im.angry.openeuicc.auth.JwtUtils
import im.angry.openeuicc.auth.MobileActivationDetails
import im.angry.openeuicc.auth.MobilePackagePurchaseRequest
import im.angry.openeuicc.auth.MobilePackagePurchaseResult
import im.angry.openeuicc.auth.Roam2WorldAuthApi
import im.angry.openeuicc.common.BuildConfig
import im.angry.openeuicc.common.R
import im.angry.openeuicc.util.activityToolbarInsetHandler
import im.angry.openeuicc.util.mainViewPaddingInsetHandler
import im.angry.openeuicc.util.setupRootViewSystemBarInsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

class PurchaseReviewActivity : AppCompatActivity() {
    private val tokenStore by lazy { AuthTokenStore(this) }
    private val authApi by lazy { Roam2WorldAuthApi(BuildConfig.ROAM2WORLD_API_BASE_URL) }

    private lateinit var confirmButton: MaterialButton
    private lateinit var cancelButton: MaterialButton
    private lateinit var progress: LinearProgressIndicator
    private lateinit var error: TextView
    private lateinit var simIccidLayout: TextInputLayout
    private lateinit var simIccidInput: TextInputEditText

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_purchase_review)
        setSupportActionBar(requireViewById(R.id.toolbar))
        supportActionBar?.apply {
            title = "Confirm Purchase"
            setDisplayHomeAsUpEnabled(true)
        }

        setupInsets()
        confirmButton = requireViewById(R.id.purchase_review_confirm)
        cancelButton = requireViewById(R.id.purchase_review_cancel)
        progress = requireViewById(R.id.purchase_review_progress)
        error = requireViewById(R.id.purchase_review_error)
        simIccidLayout = requireViewById(R.id.purchase_review_sim_iccid_layout)
        simIccidInput = requireViewById(R.id.purchase_review_sim_iccid)

        renderReview()
        confirmButton.setOnClickListener { confirmPurchase() }
        cancelButton.setOnClickListener { finish() }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }

    private fun setupInsets() {
        setupRootViewSystemBarInsets(
            window.decorView.rootView,
            arrayOf(
                this::activityToolbarInsetHandler,
                mainViewPaddingInsetHandler(requireViewById(R.id.purchase_review_scroll))
            ),
            consume = false
        )
    }

    private fun renderReview() {
        val price = priceAmount()
        val tax = BigDecimal.ZERO.setScale(2)
        val total = price.add(tax).setScale(2, RoundingMode.HALF_UP)
        val balance = decimalAmount(intent.getStringExtra(EXTRA_CURRENT_BALANCE))
        val after = balance?.subtract(total)?.setScale(2, RoundingMode.HALF_UP)

        requireViewById<TextView>(R.id.review_customer).text = "Customer: ${customerName()}"
        requireViewById<TextView>(R.id.review_phone).text = "Phone: ${intent.getStringExtra(EXTRA_CUSTOMER_PHONE).orEmpty()}"
        requireViewById<TextView>(R.id.review_package).text = "Package: ${PackageNameCleaner.clean(intent.getStringExtra(EXTRA_NAME))}"
        requireViewById<TextView>(R.id.review_provider).text = "Provider: ${displayProvider()}"
        requireViewById<TextView>(R.id.review_package_price).text = "Package Price: ${money(price)}"
        requireViewById<TextView>(R.id.review_tax).text = "Tax: ${money(tax)}"
        requireViewById<TextView>(R.id.review_total).text = "Total: ${money(total)}"
        requireViewById<TextView>(R.id.review_current_balance).text = "Current Balance: ${r2wMoney(intent.getStringExtra(EXTRA_CURRENT_BALANCE))}"
        requireViewById<TextView>(R.id.review_balance_after).text = "Balance After Purchase: ${after?.let { money(it) } ?: "--"}"
        renderSimIccidRequirement()
    }

    private fun renderSimIccidRequirement() {
        val required = requiresSimIccid()
        simIccidLayout.visibility = if (required) View.VISIBLE else View.GONE
        if (!required) {
            simIccidLayout.error = null
            simIccidInput.setText("")
        }
    }

    private fun requiresSimIccid(): Boolean {
        val id = intent.getStringExtra(EXTRA_ID).orEmpty()
        val provider = intent.getStringExtra(EXTRA_PROVIDER).orEmpty().lowercase()
        val name = intent.getStringExtra(EXTRA_NAME).orEmpty()
        val description = intent.getStringExtra(EXTRA_DESCRIPTION).orEmpty()
        val haystack = "$id $name $description".lowercase()
        return provider.contains("tgt") &&
            (
                haystack.contains("simcard】europe（41）") ||
                    haystack.contains("simcard]europe(41)")
            )
    }

    private fun simIccidOrNull(): String? =
        simIccidInput.text?.toString()?.trim()?.takeIf { it.isNotBlank() }

    private fun confirmPurchase() {
        lifecycleScope.launch {
            error.visibility = View.GONE
            setLoading(true)
            val session = activeSessionOrReturnToLogin()
            if (session == null) {
                setLoading(false)
                return@launch
            }

            if (requiresSimIccid() && simIccidOrNull() == null) {
                simIccidLayout.error = "ICCID is required for this SIMCARD Europe package"
                error.text = "Enter the ICCID printed on the SIM card to continue."
                error.visibility = View.VISIBLE
                setLoading(false)
                return@launch
            }
            simIccidLayout.error = null

            val price = intent.getStringExtra(EXTRA_PRICE) ?: "0"
            if (isDemoPackage()) {
                setLoading(false)
                startActivity(PurchaseConfirmationActivity.createIntent(this@PurchaseReviewActivity, demoPurchaseResult(price)))
                finish()
                return@launch
            }

            val purchase = runCatching {
                authApi.purchasePackage(
                    session,
                    MobilePackagePurchaseRequest(
                        packageId = intent.getStringExtra(EXTRA_ID),
                        provider = intent.getStringExtra(EXTRA_PROVIDER),
                        packageName = intent.getStringExtra(EXTRA_NAME) ?: "eSIM Package",
                        packageDescription = intent.getStringExtra(EXTRA_DESCRIPTION),
                        country = intent.getStringExtra(EXTRA_COUNTRY),
                        price = price,
                        role = intent.getStringExtra(EXTRA_ROLE),
                        customerFirstName = intent.getStringExtra(EXTRA_CUSTOMER_FIRST_NAME),
                        customerLastName = intent.getStringExtra(EXTRA_CUSTOMER_LAST_NAME),
                        customerPhone = intent.getStringExtra(EXTRA_CUSTOMER_PHONE),
                        simIccid = simIccidOrNull()
                    )
                )
            }

            setLoading(false)
            purchase
                .onSuccess {
                    startActivity(PurchaseConfirmationActivity.createIntent(this@PurchaseReviewActivity, it))
                    finish()
                }
                .onFailure {
                    error.text = it.message ?: getString(R.string.package_purchase_failed)
                    error.visibility = View.VISIBLE
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

    private fun setLoading(loading: Boolean) {
        confirmButton.isEnabled = !loading
        cancelButton.isEnabled = !loading
        progress.visibility = if (loading) View.VISIBLE else View.GONE
    }

    private fun priceAmount(): BigDecimal = decimalAmount(intent.getStringExtra(EXTRA_PRICE)) ?: BigDecimal.ZERO.setScale(2)

    private fun decimalAmount(value: String?): BigDecimal? {
        val normalized = value?.trim()?.replace(",", ".")?.replace(Regex("[^0-9.-]"), "")?.takeIf { it.isNotBlank() }
        return normalized?.toBigDecimalOrNull()?.setScale(2, RoundingMode.HALF_UP)
    }

    private fun money(value: BigDecimal): String = "$${value.setScale(2, RoundingMode.HALF_UP)}"

    private fun customerName(): String = listOfNotNull(
        intent.getStringExtra(EXTRA_CUSTOMER_FIRST_NAME),
        intent.getStringExtra(EXTRA_CUSTOMER_LAST_NAME)
    ).filter { it.isNotBlank() }.joinToString(" ").ifBlank { "Customer" }

    private fun displayProvider(): String {
        val provider = intent.getStringExtra(EXTRA_PROVIDER).orEmpty()
        return when {
            provider.contains("tgt", ignoreCase = true) -> "Orange"
            provider.isNotBlank() -> provider.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            else -> "Roam2World"
        }
    }

    private fun isDemoPackage(): Boolean = intent.getStringExtra(EXTRA_ID)?.startsWith("demo-") == true

    private fun demoPurchaseResult(price: String): MobilePackagePurchaseResult =
        MobilePackagePurchaseResult(
            orderId = "demo-${UUID.randomUUID()}",
            orderNumber = "DEMO-${System.currentTimeMillis().toString().takeLast(6)}",
            status = "demo_success",
            packageName = intent.getStringExtra(EXTRA_NAME) ?: "eSIM Package",
            price = price,
            balanceAfter = intent.getStringExtra(EXTRA_CURRENT_BALANCE),
            activation = MobileActivationDetails(
                lpaCode = null,
                smdpAddress = null,
                matchingId = null,
                confirmationCodeRequired = false,
                qrCode = null,
                qrCodeUrl = null,
                iccid = "DEMO-ICCID",
                esimId = intent.getStringExtra(EXTRA_ID)
            )
        )

    companion object {
        private const val EXTRA_ID = "package.id"
        private const val EXTRA_PROVIDER = "package.provider"
        private const val EXTRA_TYPE = "package.type"
        private const val EXTRA_NAME = "package.name"
        private const val EXTRA_COUNTRY = "package.country"
        private const val EXTRA_COUNTRY_CODE = "package.country_code"
        private const val EXTRA_PRICE = "package.price"
        private const val EXTRA_ROLE = "package.role"
        private const val EXTRA_VISIBILITY = "package.visibility"
        private const val EXTRA_DATA = "package.data"
        private const val EXTRA_VALIDITY = "package.validity"
        private const val EXTRA_NETWORK = "package.network"
        private const val EXTRA_COVERAGE = "package.coverage"
        private const val EXTRA_DESCRIPTION = "package.description"
        private const val EXTRA_CUSTOMER_FIRST_NAME = "customer.first_name"
        private const val EXTRA_CUSTOMER_LAST_NAME = "customer.last_name"
        private const val EXTRA_CUSTOMER_PHONE = "customer.phone"
        private const val EXTRA_CURRENT_BALANCE = "wallet.current_balance"

        fun createIntent(
            context: Context,
            packageIntent: Intent,
            customerFirstName: String,
            customerLastName: String,
            customerPhone: String,
            currentBalance: String?
        ): Intent = Intent(context, PurchaseReviewActivity::class.java).apply {
            listOf(
                EXTRA_ID, EXTRA_PROVIDER, EXTRA_TYPE, EXTRA_NAME, EXTRA_COUNTRY, EXTRA_COUNTRY_CODE,
                EXTRA_PRICE, EXTRA_ROLE, EXTRA_VISIBILITY, EXTRA_DATA, EXTRA_VALIDITY, EXTRA_NETWORK,
                EXTRA_COVERAGE, EXTRA_DESCRIPTION
            ).forEach { key -> putExtra(key, packageIntent.getStringExtra(key)) }
            putExtra(EXTRA_CUSTOMER_FIRST_NAME, customerFirstName)
            putExtra(EXTRA_CUSTOMER_LAST_NAME, customerLastName)
            putExtra(EXTRA_CUSTOMER_PHONE, customerPhone)
            putExtra(EXTRA_CURRENT_BALANCE, currentBalance)
        }
    }
}
