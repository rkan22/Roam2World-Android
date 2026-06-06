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
import im.angry.openeuicc.auth.AuthSession
import im.angry.openeuicc.auth.AuthTokenStore
import im.angry.openeuicc.auth.JwtUtils
import im.angry.openeuicc.auth.MobileActivationDetails
import im.angry.openeuicc.auth.MobilePackage
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

class PackageDetailActivity : AppCompatActivity() {
    private val tokenStore by lazy { AuthTokenStore(this) }
    private val authApi by lazy { Roam2WorldAuthApi(BuildConfig.ROAM2WORLD_API_BASE_URL) }

    private lateinit var purchaseButton: MaterialButton
    private lateinit var progress: LinearProgressIndicator
    private lateinit var error: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_package_detail)
        setSupportActionBar(requireViewById(R.id.toolbar))
        supportActionBar?.apply {
            title = getString(R.string.package_detail_title)
            setDisplayHomeAsUpEnabled(true)
        }

        setupInsets()
        purchaseButton = requireViewById(R.id.package_purchase_button)
        progress = requireViewById(R.id.package_purchase_progress)
        error = requireViewById(R.id.package_purchase_error)
        purchaseButton.setOnClickListener {
            purchasePackage()
        }
        renderDetails()
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
                mainViewPaddingInsetHandler(requireViewById(R.id.package_detail_scroll))
            ),
            consume = false
        )
    }

    private fun renderDetails() {
        requireViewById<TextView>(R.id.package_detail_name).text = intent.getStringExtra(EXTRA_NAME)
            ?: getString(R.string.package_detail_title)
        requireViewById<TextView>(R.id.package_detail_country).text = listOfNotNull(
            intent.getStringExtra(EXTRA_COUNTRY),
            intent.getStringExtra(EXTRA_COUNTRY_CODE)?.takeIf { it.isNotBlank() }
        ).joinToString(" - ")
        requireViewById<TextView>(R.id.package_detail_price).text = intent.getStringExtra(EXTRA_PRICE)
            ?: "0"
        requireViewById<TextView>(R.id.package_detail_visibility).text =
            getString(
                R.string.package_detail_visibility_format,
                intent.getStringExtra(EXTRA_VISIBILITY) ?: ""
            )

        setOptionalText(R.id.package_detail_data, intent.getStringExtra(EXTRA_DATA), R.string.package_detail_data_format)
        setOptionalText(R.id.package_detail_validity, intent.getStringExtra(EXTRA_VALIDITY), R.string.package_detail_validity_format)
        setOptionalText(R.id.package_detail_network, intent.getStringExtra(EXTRA_NETWORK), R.string.package_detail_network_format)
        setOptionalText(R.id.package_detail_coverage, intent.getStringExtra(EXTRA_COVERAGE), R.string.package_detail_coverage_format)
        setOptionalText(R.id.package_detail_description, intent.getStringExtra(EXTRA_DESCRIPTION), R.string.package_detail_description_format)
    }

    private fun purchasePackage() {
        lifecycleScope.launch {
            error.visibility = View.GONE
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
                startActivity(PurchaseConfirmationActivity.createIntent(this@PackageDetailActivity, demoPurchaseResult(price)))
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
                        packageName = intent.getStringExtra(EXTRA_NAME)
                            ?: getString(R.string.package_detail_title),
                        packageDescription = intent.getStringExtra(EXTRA_DESCRIPTION),
                        country = intent.getStringExtra(EXTRA_COUNTRY),
                        price = price,
                        role = intent.getStringExtra(EXTRA_ROLE)
                    )
                )
            }

            setLoading(false)
            purchase
                .onSuccess {
                    startActivity(PurchaseConfirmationActivity.createIntent(this@PackageDetailActivity, it))
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
        purchaseButton.isEnabled = !loading
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

    private fun setOptionalText(viewId: Int, value: String?, formatResId: Int) {
        requireViewById<TextView>(viewId).apply {
            text = value?.let { getString(formatResId, it) }.orEmpty()
            visibility = if (value.isNullOrBlank()) View.GONE else View.VISIBLE
        }
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

        fun createIntent(context: Context, mobilePackage: MobilePackage, role: String?): Intent =
            Intent(context, PackageDetailActivity::class.java).apply {
                putExtra(EXTRA_ID, mobilePackage.id)
                putExtra(EXTRA_PROVIDER, mobilePackage.provider)
                putExtra(EXTRA_TYPE, mobilePackage.packageType)
                putExtra(EXTRA_NAME, mobilePackage.name)
                putExtra(EXTRA_COUNTRY, mobilePackage.country)
                putExtra(EXTRA_COUNTRY_CODE, mobilePackage.countryCode)
                putExtra(EXTRA_PRICE, mobilePackage.priceFor(role))
                putExtra(EXTRA_ROLE, role)
                putExtra(EXTRA_VISIBILITY, mobilePackage.visibilityLabel())
                putExtra(EXTRA_DATA, mobilePackage.dataAmount)
                putExtra(EXTRA_VALIDITY, mobilePackage.validity)
                putExtra(EXTRA_NETWORK, mobilePackage.network)
                putExtra(EXTRA_COVERAGE, mobilePackage.coverage)
                putExtra(EXTRA_DESCRIPTION, mobilePackage.description)
            }
    }
}
