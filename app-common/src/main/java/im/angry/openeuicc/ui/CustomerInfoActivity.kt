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
import im.angry.openeuicc.auth.AuthTokenStore
import im.angry.openeuicc.auth.JwtUtils
import im.angry.openeuicc.auth.Roam2WorldAuthApi
import im.angry.openeuicc.common.BuildConfig
import im.angry.openeuicc.common.R
import im.angry.openeuicc.util.activityToolbarInsetHandler
import im.angry.openeuicc.util.mainViewPaddingInsetHandler
import im.angry.openeuicc.util.setupRootViewSystemBarInsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
        continueButton.setOnClickListener { validateAndOpenReview() }
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
            PackageNameCleaner.clean(intent.getStringExtra(EXTRA_NAME))
        requireViewById<TextView>(R.id.customer_package_meta).text = listOfNotNull(
            intent.getStringExtra(EXTRA_DATA),
            intent.getStringExtra(EXTRA_VALIDITY),
            intent.getStringExtra(EXTRA_COUNTRY)
        ).filter { it.isNotBlank() }.joinToString("  •  ")
        requireViewById<TextView>(R.id.customer_package_price).text =
            intent.getStringExtra(EXTRA_PRICE) ?: "0"
    }

    private fun validateAndOpenReview() {
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

        lifecycleScope.launch {
            setLoading(true)
            val currentBalance = runCatching {
                val session = withContext(Dispatchers.IO) { tokenStore.getSession() }
                if (session == null || JwtUtils.isExpired(session.accessToken)) null else authApi.wallet(session).currentBalance
            }.getOrNull()
            setLoading(false)
            startActivity(PurchaseReviewActivity.createIntent(this@CustomerInfoActivity, intent, first, last, phoneNumber, currentBalance))
        }
    }

    private fun setLoading(loading: Boolean) {
        continueButton.isEnabled = !loading
        progress.visibility = if (loading) View.VISIBLE else View.GONE
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
