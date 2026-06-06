package im.angry.openeuicc.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.TextView
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

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_more)
        setSupportActionBar(requireViewById(R.id.toolbar))
        supportActionBar?.title = getString(R.string.r2w_more)

        scroll = requireViewById(R.id.more_scroll)
        bottomNav = requireViewById(R.id.more_bottom_nav)

        setupInsets()
        setupBottomNavigation()
        setupActions()
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
        requireViewById<MaterialButton>(R.id.more_profile).setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }
        requireViewById<MaterialButton>(R.id.more_esim_history).setOnClickListener {
            startActivity(Intent(this, PurchaseHistoryActivity::class.java))
        }
        requireViewById<MaterialButton>(R.id.more_openeuicc).setOnClickListener {
            openNativeOpenEuicc()
        }
        requireViewById<MaterialButton>(R.id.more_tgt_recharge).setOnClickListener {
            startActivity(Intent(this, TgtSimRechargeActivity::class.java))
        }
        requireViewById<MaterialButton>(R.id.more_orders).setOnClickListener {
            startActivity(Intent(this, PurchaseHistoryActivity::class.java))
        }
        requireViewById<MaterialButton>(R.id.more_reports).setOnClickListener {
            startActivity(Intent(this, ReportsActivity::class.java))
        }
        requireViewById<MaterialButton>(R.id.more_settings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        requireViewById<MaterialButton>(R.id.more_logout).setOnClickListener {
            logout()
        }
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
