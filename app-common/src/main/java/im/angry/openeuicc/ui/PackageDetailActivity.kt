package im.angry.openeuicc.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import im.angry.openeuicc.auth.MobilePackage
import im.angry.openeuicc.common.R
import im.angry.openeuicc.util.activityToolbarInsetHandler
import im.angry.openeuicc.util.mainViewPaddingInsetHandler
import im.angry.openeuicc.util.setupRootViewSystemBarInsets

class PackageDetailActivity : AppCompatActivity() {
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

    private fun setOptionalText(viewId: Int, value: String?, formatResId: Int) {
        requireViewById<TextView>(viewId).apply {
            text = value?.let { getString(formatResId, it) }.orEmpty()
            visibility = if (value.isNullOrBlank()) View.GONE else View.VISIBLE
        }
    }

    companion object {
        private const val EXTRA_NAME = "package.name"
        private const val EXTRA_COUNTRY = "package.country"
        private const val EXTRA_COUNTRY_CODE = "package.country_code"
        private const val EXTRA_PRICE = "package.price"
        private const val EXTRA_VISIBILITY = "package.visibility"
        private const val EXTRA_DATA = "package.data"
        private const val EXTRA_VALIDITY = "package.validity"
        private const val EXTRA_NETWORK = "package.network"
        private const val EXTRA_COVERAGE = "package.coverage"
        private const val EXTRA_DESCRIPTION = "package.description"

        fun createIntent(context: Context, mobilePackage: MobilePackage, role: String?): Intent =
            Intent(context, PackageDetailActivity::class.java).apply {
                putExtra(EXTRA_NAME, mobilePackage.name)
                putExtra(EXTRA_COUNTRY, mobilePackage.country)
                putExtra(EXTRA_COUNTRY_CODE, mobilePackage.countryCode)
                putExtra(EXTRA_PRICE, mobilePackage.priceFor(role))
                putExtra(EXTRA_VISIBILITY, mobilePackage.visibilityLabel())
                putExtra(EXTRA_DATA, mobilePackage.dataAmount)
                putExtra(EXTRA_VALIDITY, mobilePackage.validity)
                putExtra(EXTRA_NETWORK, mobilePackage.network)
                putExtra(EXTRA_COVERAGE, mobilePackage.coverage)
                putExtra(EXTRA_DESCRIPTION, mobilePackage.description)
            }
    }
}
