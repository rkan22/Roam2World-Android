package im.angry.openeuicc.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import im.angry.openeuicc.auth.AuthTokenStore
import im.angry.openeuicc.common.R
import im.angry.openeuicc.util.activityToolbarInsetHandler
import im.angry.openeuicc.util.mainViewPaddingInsetHandler
import im.angry.openeuicc.util.setupRootViewSystemBarInsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MoreActivity : AppCompatActivity() {
    private val tokenStore by lazy { AuthTokenStore(this) }
    private lateinit var scroll: View
    private lateinit var bottomNav: BottomNavigationView
    private lateinit var profile: MaterialButton
    private lateinit var esimHistory: MaterialButton
    private lateinit var openEuicc: MaterialButton
    private lateinit var tgtRecharge: MaterialButton
    private lateinit var orders: MaterialButton
    private lateinit var reports: MaterialButton
    private lateinit var support: MaterialButton
    private lateinit var settings: MaterialButton
    private lateinit var logoutButton: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_more)
        setSupportActionBar(requireViewById(R.id.toolbar))
        supportActionBar?.title = getString(R.string.r2w_more)

        scroll = requireViewById(R.id.more_scroll)
        bottomNav = requireViewById(R.id.more_bottom_nav)
        profile = requireViewById(R.id.more_profile)
        esimHistory = requireViewById(R.id.more_esim_history)
        openEuicc = requireViewById(R.id.more_openeuicc)
        tgtRecharge = requireViewById(R.id.more_tgt_recharge)
        orders = requireViewById(R.id.more_orders)
        reports = requireViewById(R.id.more_reports)
        support = requireViewById(R.id.more_support)
        settings = requireViewById(R.id.more_settings)
        logoutButton = requireViewById(R.id.more_logout)

        setupInsets()
        setupBottomNavigation()
        setupActions()
        applyRoleVisibility()
    }

    override fun onResume() {
        super.onResume()
        bottomNav.selectedItemId = R.id.nav_more
    }

    private fun setupInsets() {
        setupRootViewSystemBarInsets(
            window.decorView.rootView,
            arrayOf(
                this::activityToolbarInsetHandler,
                mainViewPaddingInsetHandler(scroll),
                { insets ->
                    bottomNav.updatePadding(
                        insets.left,
                        bottomNav.paddingTop,
                        insets.right,
                        insets.bottom
                    )
                }
            ),
            consume = false
        )
    }

    private fun setupBottomNavigation() {
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_dashboard -> {
                    startActivity(Intent(this, DashboardActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
                    false
                }
                R.id.nav_packages -> {
                    startActivity(Intent(this, PackagesActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
                    false
                }
                R.id.nav_wallet -> {
                    startActivity(Intent(this, WalletActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
                    false
                }
                R.id.nav_esims -> {
                    startActivity(Intent(this, MobileEsimsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
                    false
                }
                R.id.nav_more -> true
                else -> false
            }
        }
        bottomNav.selectedItemId = R.id.nav_more
    }

    private fun setupActions() {
        profile.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }
        esimHistory.setOnClickListener {
            startActivity(Intent(this, PurchaseHistoryActivity::class.java))
        }
        openEuicc.setOnClickListener {
            openNativeOpenEuicc()
        }
        tgtRecharge.setOnClickListener {
            startActivity(Intent(this, TgtSimRechargeActivity::class.java))
        }
        orders.setOnClickListener {
            startActivity(Intent(this, PurchaseHistoryActivity::class.java))
        }
        reports.setOnClickListener {
            startActivity(Intent(this, ReportsActivity::class.java))
        }
        support.setOnClickListener {
            startActivity(Intent(this, SupportActivity::class.java))
        }
        settings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        logoutButton.setOnClickListener {
            logout()
        }
    }

    private fun applyRoleVisibility() {
        lifecycleScope.launch {
            val role = withContext(Dispatchers.IO) { tokenStore.getSession()?.role.orEmpty().lowercase() }
            when (role) {
                "admin" -> showAllBusinessItems()
                "reseller" -> showAllBusinessItems()
                "dealer" -> {
                    reports.visibility = View.GONE
                    openEuicc.visibility = View.VISIBLE
                    tgtRecharge.visibility = View.VISIBLE
                    esimHistory.visibility = View.VISIBLE
                    orders.visibility = View.VISIBLE
                    support.visibility = View.VISIBLE
                }
                else -> showAllBusinessItems()
            }
        }
    }

    private fun showAllBusinessItems() {
        esimHistory.visibility = View.VISIBLE
        openEuicc.visibility = View.VISIBLE
        tgtRecharge.visibility = View.VISIBLE
        orders.visibility = View.VISIBLE
        reports.visibility = View.VISIBLE
        support.visibility = View.VISIBLE
    }

    private fun openNativeOpenEuicc() {
        val target = targetActivityName(DashboardActivity.META_ESIM_ACTIVITY)
        if (target.isNullOrBlank()) return
        startActivity(Intent().setClassName(this, target))
    }

    private fun targetActivityName(key: String): String? {
        val appInfo = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
        return appInfo.metaData?.getString(key)
    }

    private fun logout() {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { tokenStore.clear() }
            startActivity(
                Intent(this@MoreActivity, LoginActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                }
            )
            finish()
        }
    }
}
