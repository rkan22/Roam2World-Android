package im.angry.openeuicc.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import im.angry.openeuicc.auth.AuthTokenStore
import im.angry.openeuicc.auth.Roam2WorldAuthApi
import im.angry.openeuicc.common.BuildConfig
import im.angry.openeuicc.common.R
import im.angry.openeuicc.util.activityToolbarInsetHandler
import im.angry.openeuicc.util.mainViewPaddingInsetHandler
import im.angry.openeuicc.util.setupRootViewSystemBarInsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MoreActivity : AppCompatActivity() {
    private val tokenStore by lazy { AuthTokenStore(this) }
    private val api by lazy { Roam2WorldAuthApi(BuildConfig.ROAM2WORLD_API_BASE_URL) }
    private lateinit var scroll: View
    private lateinit var bottomNav: BottomNavigationView
    private lateinit var profile: MaterialButton
    private lateinit var customers: MaterialButton
    private lateinit var esimHistory: MaterialButton
    private lateinit var openEuicc: MaterialButton
    private lateinit var tgtRecharge: MaterialButton
    private lateinit var tgtCheckGb: MaterialButton
    private lateinit var vodafoneRenewal: MaterialButton
    private lateinit var orders: MaterialButton
    private lateinit var notifications: MaterialButton
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
        customers = requireViewById(R.id.more_customers)
        esimHistory = requireViewById(R.id.more_esim_history)
        openEuicc = requireViewById(R.id.more_openeuicc)
        tgtRecharge = requireViewById(R.id.more_tgt_recharge)
        tgtCheckGb = requireViewById(R.id.more_tgt_check_gb)
        vodafoneRenewal = requireViewById(R.id.more_vodafone_renewal)
        orders = requireViewById(R.id.more_orders)
        notifications = requireViewById(R.id.more_notifications)
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
        loadNotificationBadge()
    }



    private fun loadNotificationBadge() {
        lifecycleScope.launch {
            val session = withContext(Dispatchers.IO) { tokenStore.getSession() }
            val unreadCount = if (session != null) {
                withContext(Dispatchers.IO) {
                    runCatching { api.mobileNotifications(session).unreadCount }.getOrDefault(0)
                }
            } else {
                0
            }

            notifications.text = if (unreadCount > 0) {
                "Notifications\nInbox ($unreadCount)"
            } else {
                "Notifications\nInbox"
            }
        }
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
        customers.setOnClickListener {
            startActivity(Intent(this, CustomersActivity::class.java))
        }
        esimHistory.setOnClickListener {
            startActivity(Intent(this, PurchaseHistoryActivity::class.java))
        }
        openEuicc.setOnClickListener {
            startActivity(Intent(this, OpenEuiccIntegrationActivity::class.java))
        }
        tgtRecharge.setOnClickListener {
            startActivity(Intent(this, TgtSimRechargeActivity::class.java))
        }
        tgtCheckGb.setOnClickListener {
            startActivity(Intent(this, TgtCheckGbActivity::class.java))
        }
        vodafoneRenewal.setOnClickListener {
            startActivity(Intent(this, VodafoneRenewalActivity::class.java))
        }
        orders.setOnClickListener {
            startActivity(Intent(this, PurchaseHistoryActivity::class.java))
        }
        notifications.setOnClickListener {
            startActivity(Intent(this, MobileNotificationsActivity::class.java))
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
                "dealer" -> showAllBusinessItems()
                else -> showAllBusinessItems()
            }
        }
    }

    private fun showAllBusinessItems() {
        customers.visibility = View.VISIBLE
        esimHistory.visibility = View.VISIBLE
        openEuicc.visibility = View.VISIBLE
        tgtRecharge.visibility = View.VISIBLE
        tgtCheckGb.visibility = View.VISIBLE
        vodafoneRenewal.visibility = View.VISIBLE
        orders.visibility = View.VISIBLE
        notifications.visibility = View.VISIBLE
        reports.visibility = View.VISIBLE
        support.visibility = View.VISIBLE
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
