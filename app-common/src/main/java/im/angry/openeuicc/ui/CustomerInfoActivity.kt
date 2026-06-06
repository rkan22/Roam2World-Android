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
import java.util.UUID

class CustomerInfoActivity : AppCompatActivity() {
    private val tokenStore by lazy { AuthTokenStore(this) }
    private val authApi by lazy { Roam2WorldAuthApi(BuildConfig.ROAM2WORLD_API_BASE_URL) }

    private lateinit var firstNameLayout: TextInputLayout
    private lateinit var lastNameLayout: TextInputLayout
    private lateinit var phoneLayout: TextInputLayout
    private lateinit var firstName: TextInputEditText
    private lateinit var lastName: TextInputEditText
    private lateinit var phone: TextInputEditText
    private lateinit var continueButton: MaterialButton
    private lateinit var progress: LinearProgressIndicator
    private lateinit var error: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_customer_info)
        setSupportActionBar(requireViewById(R.id.toolbar))
        supportActionBar?.apply {
            title = "Customer Info"
            setDisplayHomeAsUpEnabled(true)
        }

        setupInsets()
        firstNameLayout = requireViewById(R.id.customer_first_name_layout)
        lastNameLayout = requireViewById(R.id.customer_last_name_layout)
        phoneLayout = requireViewById(R.id.customer_phone_layout)
        firstName = requireViewById(R.id.customer_first_name)
        lastName = requireViewById(R.id.customer_last_name)
        phone = requireViewById(R.id.customer_phone)
        continueButton = requireViewById(R.id.customer_continue_button)
        progress = requireViewById(R.id.customer_info_progress)
        error = requireViewById(R.id.customer_info_error)

        renderPackageSummary()
        continueButton.setOnClickListener {
            validateAndPurchase()
        }
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
                mainViewPaddingInsetHandler(requireViewById(R.id.customer_info_scroll))
            ),
            consume = false
        )
    }

    private fun renderPackageSummary() {
        requireViewById<TextView>(R.id.customer_package_name).text =
            intent.getStringExtra(EXTRA_NAME) ?: getString(R.string.package_detail_title)
        requireViewById<TextView>(R.id.customer_package_meta).text = listOfNotNull(
            intent.getStringExtra(EXTRA_DATA),
            intent.getStringExtra(EXTRA_VALIDITY),
            intent.getStringExtra(EXTRA_COUNTRY)
        ).filter { it.isNotBlank() }.joinToString("  •  ")
        requireViewById<TextView>(R.id.customer_package_price).text =
            intent.getStringExtra(EXTRA_PRICE) ?: "0"
    }

    private fun validateAndPurchase() {
        firstNameLayout.error = null
        lastNameLayout.error = null
        phoneLayout.error = null
        error.visibility = View.GONE

        val first = firstName.text?.toString()?.trim().orEmpty()
        val last = lastName.text?.toString()?.trim().orEmpty()
        val phoneNumber = phone.text?.toString()?.trim().orEmpty()

        var valid = true
        if (first.isBlank()) {
            firstNameLayout.error = "First name is required"
            valid = false
        }
        if (last.isBlank()) {
            lastNameLayout.error = "Last name is required"
            valid = false
        }
        if (phoneNumber.isBlank()) {
            phoneLayout.error = "Phone number is required"
            valid = false
        }
        if (!valid) return

        purchasePackage(first, last, phoneNumber)
    }

    private fun purchasePackage(customerFirstName: String, customerLastName: String, customerPhone: String) {
        lifecycleScope.launch {
            setLoading(true)
            val session = activeSessionOrReturnToLogin()
            if (session == null) {
                setLoading(false)
                return@launch
            }

            val price = intent.getStringExtra(EXTRA_PRICE) ?: "0"
            val priceAmount = decimalAmount(price)
            if (priceAmount == null) {
                showPurchaseError(getString(R.string.package_purchase_price_unavailable))
                return@launch
            }

            if (isDemoPackage()) {
                setLoading(false)
                startActivity(PurchaseConfirmationActivity.createIntent(this@CustomerInfoActivity, demoPurchaseResult(price)))
                return@launch
            }

            val wallet = runCatching {
                authApi.wallet(session)
            }.getOrElse {
                showPurchaseError(it.message ?: getString(R.string.package_purchase_wallet_failed))
                return@launch
            }

            val balanceAmount = decimalAmount(wallet.currentBalance)
            if (balanceAmount == null || balanceAmount < priceAmount) {
                showPurchaseError(getString(R.string.package_purchase_insufficient_balance))
                return@launch
            }

            val purchase = runCatching {
                authApi.purchasePackage(
                    session,
                    MobilePackagePurchaseRequest(
                        packageId = intent.getStringExtra(EXTRA_ID),
                        provider = intent.getStringExtra(EXTRA_PROVIDER),
                        packageName = intent.getStringExtra(EXTRA_NAME) ?: getString(R.string.package_detail_title),
                        packageDescription = intent.getStringExtra(EXTRA_DESCRIPTION),
                        country = intent.getStringExtra(EXTRA_COUNTRY),
                        price = price,
                        role = intent.getStringExtra(EXTRA_ROLE),
                        customerFirstName = customerFirstName,
                        customerLastName = customerLastName,
                        customerPhone = customerPhone
                    )
                )
            }

            setLoading(false)
            purchase
                .onSuccess {
                    startActivity(PurchaseConfirmationActivity.createIntent(this@CustomerInfoActivity, it))
                }
                .onFailure {
                    error.text = it.message ?: getString(R.string.package_purchase_failed)
                    error.visibility = View.VISIBLE
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

    private fun showPurchaseError(message: String) {
        setLoading(false)
        error.text = message
        error.visibility = View.VISIBLE
    }

    private fun setLoading(loading: Boolean) {
        continueButton.isEnabled = !loading
        progress.visibility = if (loading) View.VISIBLE else View.GONE
    }

    private fun decimalAmount(value: String?): BigDecimal? {
        val normalized = value
            ?.trim()
            ?.replace(",", ".")
            ?.replace(Regex("[^0-9.-]"), "")
            ?.takeIf { it.isNotBlank() }
        return normalized?.toBigDecimalOrNull()
    }

    private fun isDemoPackage(): Boolean =
        intent.getStringExtra(EXTRA_ID)?.startsWith("demo-") == true

    private fun demoPurchaseResult(price: String): MobilePackagePurchaseResult =
        MobilePackagePurchaseResult(
            orderId = "demo-${UUID.randomUUID()}",
            orderNumber = "DEMO-${System.currentTimeMillis().toString().takeLast(6)}",
            status = "demo_success",
            packageName = intent.getStringExtra(EXTRA_NAME) ?: getString(R.string.package_detail_title),
            price = price,
            balanceAfter = null,
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

        fun createIntent(context: Context, packageIntent: Intent): Intent =
            Intent(context, CustomerInfoActivity::class.java).apply {
                listOf(
                    EXTRA_ID,
                    EXTRA_PROVIDER,
                    EXTRA_TYPE,
                    EXTRA_NAME,
                    EXTRA_COUNTRY,
                    EXTRA_COUNTRY_CODE,
                    EXTRA_PRICE,
                    EXTRA_ROLE,
                    EXTRA_VISIBILITY,
                    EXTRA_DATA,
                    EXTRA_VALIDITY,
                    EXTRA_NETWORK,
                    EXTRA_COVERAGE,
                    EXTRA_DESCRIPTION
                ).forEach { key ->
                    putExtra(key, packageIntent.getStringExtra(key))
                }
            }
    }
}
